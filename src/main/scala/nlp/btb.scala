/*
 * File: btb.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:06:00 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.nlp

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.dome._
import herd.common.mem.replace._


class Btb (p: BtbParams) extends Module {
  val io = IO(new Bundle {
    val b_hart = if (p.useDome) Some(new RsrcIO(1, p.nDome, 1)) else None

    val b_read = Vec(p.nReadPort, new BtbReadIO(p))
    val b_write = new BtbWriteIO(p)
  })

  val r_valid = RegInit(VecInit(Seq.fill(p.nLine)(false.B)))
  val r_tag = Reg(Vec(p.nLine, UInt(p.nTagBit.W)))
  val r_data = Reg(Vec(p.nLine, new BtbDataBus(p)))

  // ******************************
  //             FLUSH
  // ******************************
  val w_flush = Wire(Bool())

  if (p.useDome) w_flush := io.b_hart.get.flush else w_flush := false.B

  // ******************************
  //             READ
  // ******************************
  val w_read_here = Wire(Vec(p.nReadPort, Bool()))
  val w_read_line = Wire(Vec(p.nReadPort, UInt(log2Ceil(p.nLine).W)))

  for (r <- 0 until p.nReadPort) {
    w_read_here(r) := false.B
    w_read_line(r) := 0.U
    io.b_read(r).data := r_data(0)

    for (l <- 0 until p.nLine) {
      when (r_valid(l) & (io.b_read(r).tag === r_tag(l))) {
        w_read_here(r) := true.B
        w_read_line(r) := l.U
        io.b_read(r).data := r_data(l)
      }
    }

    io.b_read(r).ready := w_read_here(r)
  }

  // ******************************
  //             WRITE
  // ******************************
  // ------------------------------
  //       CHECK DATA PRESENCE
  // ------------------------------
  val w_write_here = Wire(Bool())
  val w_write_line = Wire(UInt(log2Ceil(p.nLine).W))

  w_write_here := false.B
  w_write_line := 0.U

  for (l <- 0 until p.nLine) {
    when (r_valid(l) & (io.b_write.tag === r_tag(l))) {
      w_write_here := true.B
      w_write_line := l.U
    }
  }

  // ------------------------------
  //         REPLACE POLICY
  // ------------------------------
  val m_pol = Module(new BitPLruPolicy(false, 1, p.nReadPort, p.nLine))

  val w_rep_done = Wire(Bool())
  val w_rep_line = Wire(UInt(log2Ceil(p.nLine).W))
  val w_pol_free = Wire(Vec(p.nLine, Bool()))

  for (l <- 0 until p.nLine) {
    m_pol.io.b_line(l).av := true.B
    m_pol.io.b_line(l).flush := w_flush
  }

  for (r <- 0 until p.nReadPort) {
    m_pol.io.b_acc(r).valid := io.b_read(r).valid & w_read_here(r)
    m_pol.io.b_acc(r).line := w_read_line(r)
  }

  m_pol.io.b_rep.valid := io.b_write.valid & ~w_write_here
  m_pol.io.b_rep.fixed := DontCare

  for (l <- 0 until p.nLine) {
    w_pol_free(l) := m_pol.io.b_line(l).free
  }  

  w_rep_done := m_pol.io.b_rep.done
  w_rep_line := m_pol.io.b_rep.line

  // ------------------------------
  //            OUTPUTS
  // ------------------------------
  io.b_write.ready := w_write_here | w_rep_done

  // ******************************
  //         REGISTER UPDATE
  // ******************************
  for (l <- 0 until p.nLine) {
    when (w_flush) {
      r_valid(l) := false.B
    }.elsewhen (io.b_write.valid & w_write_here) {
      when (l.U === w_write_line) {
        r_data(l) := io.b_write.data
      }
    }.elsewhen (io.b_write.valid & w_rep_done) {
      when (l.U === w_rep_line) {
        r_valid(l) := true.B
        r_tag(l) := io.b_write.tag
        r_data(l) := io.b_write.data
      }
    }
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    io.b_hart.get.free := ~r_valid.asUInt.orR & w_pol_free.asUInt.orR
  }
}

object Btb extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Btb(BtbConfigBase), args)
}
