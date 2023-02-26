/*
 * File: bht.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:05:56 pm                                       *
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

import herd.common.dome._


class Bht(p: BhtParams) extends Module {
  require((p.nSet > 1), "Each BHT must have more than one set.")
  require((p.nSetEntry > 1), "Each BHT set must have more than one entry.")
  require(isPow2(p.nEntry), "BHT must have 2^n entries.")
  
  val io = IO(new Bundle {
    val b_hart = if (p.useDome) Some(new RsrcIO(1, p.nDome, 1)) else None

    val b_read = Vec(p.nReadPort, new BhtReadIO(p))
    val b_write = new BhtWriteIO(p)
  })

  val r_valid = RegInit(VecInit(Seq.fill(p.nSet)(false.B)))
  val r_cnt = Reg(Vec(p.nSet, UInt(p.nSetBit.W)))

  // ******************************
  //             FLUSH
  // ******************************
  val w_flush = Wire(Bool())

  if (p.useDome) w_flush := io.b_hart.get.flush else w_flush := false.B

  // ******************************
  //              READ
  // ******************************
  val w_rset = Wire(Vec(p.nReadPort, UInt(log2Ceil(p.nSet).W)))
  val w_rentry = Wire(Vec(p.nReadPort, UInt(log2Ceil(p.nSetEntry).W)))
  val w_rcnt = Wire(Vec(p.nReadPort, Vec(p.nSetEntry, UInt(p.nBit.W))))

  for (rp <- 0 until p.nReadPort) {
    w_rset(rp) := io.b_read(rp).slct(log2Ceil(p.nEntry) - 1, log2Ceil(p.nSetEntry))
    w_rentry(rp) := io.b_read(rp).slct(log2Ceil(p.nSetEntry) - 1, 0)

    for (se <- 0 until p.nSetEntry) {
      w_rcnt(rp)(se) := r_cnt(w_rset(rp))((se + 1) * p.nBit - 1, se * p.nBit)
    }     

    io.b_read(rp).taken := Mux(r_valid(w_rset(rp)), w_rcnt(rp)(w_rentry(rp))(p.nBit - 1), 0.B)
  }

  // ******************************
  //             WRITE
  // ******************************
  // ------------------------------
  //             SELECT
  // ------------------------------
  val w_wset = Wire(UInt(log2Ceil(p.nSet).W))
  val w_wentry = Wire(UInt(log2Ceil(p.nSetEntry).W))

  w_wset := io.b_write.slct(log2Ceil(p.nEntry) - 1, log2Ceil(p.nSetEntry))
  w_wentry := io.b_write.slct(log2Ceil(p.nSetEntry) - 1, 0)

  // ------------------------------
  //           OLD VALUE
  // ------------------------------
  val w_wcnt_old = Wire(Vec(p.nSetEntry, UInt(p.nBit.W)))

  for (se <- 0 until p.nSetEntry) {
    when (r_valid(w_wset)) {
      w_wcnt_old(se) := r_cnt(w_wset)((se + 1) * p.nBit - 1, se * p.nBit)
    }.otherwise {
      w_wcnt_old(se) := 0.U      
    }
  }

  // ------------------------------
  //           NEW VALUE
  // ------------------------------
  val w_wcnt_new = Wire(Vec(p.nSetEntry, UInt(p.nBit.W)))

  w_wcnt_new := w_wcnt_old

  when (io.b_write.taken & ~w_wcnt_old(w_wentry).andR) {
    w_wcnt_new(w_wentry) := w_wcnt_old(w_wentry) + 1.U
  }.elsewhen (~io.b_write.taken & w_wcnt_old(w_wentry).orR) {
    w_wcnt_new(w_wentry) := w_wcnt_old(w_wentry) - 1.U
  }

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  when (w_flush) {
    for (s <- 0 until p.nSet) {
      r_valid(s) := 0.U
    }
  }.elsewhen (io.b_write.valid) {
    r_valid(w_wset) := true.B
    r_cnt(w_wset) := w_wcnt_new.asUInt
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    io.b_hart.get.free := ~r_valid.asUInt.orR
  }  
}

object Bht extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Bht(BhtConfigBase), args)
}
