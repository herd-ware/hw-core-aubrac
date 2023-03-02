/*
 * File: nlp.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:06:11 pm                                       *
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

import herd.common.tools._
import herd.common.field._


class Nlp (p: NlpParams) extends Module {
  val io = IO(new Bundle {
    val b_hart = if (p.useField) Some(new RsrcIO(1, p.nField, 1)) else None

    val i_mispred = Input(Bool())

    val b_read = new NlpReadIO(p.nFetchInstr, p.nAddrBit)
    val i_info = Input(new BranchInfoBus(p.nAddrBit))
  })

  val m_btb = Module(new Btb(p.pBtb))
  val m_bht = Module(new Bht(p.pBht))
  val m_rsb = Module(new Rsb(p.pRsb))

  // ******************************
  //      FORMAT TAG & TARGET
  // ******************************
  val w_rtag = Wire(Vec(p.nFetchInstr, UInt(p.nTagBit.W)))
  val w_wtag = Wire(UInt(p.nTagBit.W))
  val w_wtarget = Wire(UInt(p.nTagBit.W))

  for (fi <- 0 until p.nFetchInstr) {
    w_rtag(fi) := (io.b_read.pc >> log2Floor(p.nInstrByte).U) + fi.U
  }
  w_wtag := (io.i_info.pc >> log2Floor(p.nInstrByte).U)
  w_wtarget := (io.i_info.target >> log2Floor(p.nInstrByte).U)

  // ******************************
  //      BRANCH TARGET BUFFER
  // ******************************
  for (fi <- 0 until p.nFetchInstr) {
    m_btb.io.b_read(fi).valid := io.b_read.valid(fi)
    m_btb.io.b_read(fi).tag := w_rtag(fi)
  }
  m_btb.io.b_write.valid := io.i_info.valid
  m_btb.io.b_write.tag := w_wtag
  m_btb.io.b_write.data.jmp := io.i_info.jmp
  m_btb.io.b_write.data.call := io.i_info.call
  m_btb.io.b_write.data.ret := io.i_info.ret
  m_btb.io.b_write.data.target := w_wtarget

  // ******************************
  //     BRANCH HISTORY BUFFER
  // ******************************
  for (fi <- 0 until p.nFetchInstr) {
    m_bht.io.b_read(fi).slct := w_rtag(fi)
  }  
  m_bht.io.b_write.valid := io.i_info.valid & io.i_info.br
  m_bht.io.b_write.slct := w_wtag
  m_bht.io.b_write.taken := io.i_info.taken

  // ******************************
  //      RETURN ADDRESS STACK
  // ******************************
  m_rsb.io.i_restore := io.i_mispred

  // ------------------------------
  //             REAL
  // ------------------------------
  m_rsb.io.b_read(0).valid := io.i_info.valid & io.i_info.ret
  m_rsb.io.b_write(0).valid := io.i_info.valid & io.i_info.call
  m_rsb.io.b_write(0).target := ((io.i_info.pc >> log2Floor(p.nInstrByte).U) + 1.U)  

  // ------------------------------
  //          SPECULATIVE
  // ------------------------------
  m_rsb.io.b_read(1).valid := false.B
  m_rsb.io.b_write(1).valid := false.B
  m_rsb.io.b_write(1).target := DontCare

  for (fi <- p.nFetchInstr - 1 to 0 by -1) {
    when (io.b_read.valid(fi) & (m_btb.io.b_read(fi).data.call | m_btb.io.b_read(fi).data.ret)) {
      m_rsb.io.b_read(1).valid := m_btb.io.b_read(fi).data.ret
      m_rsb.io.b_write(1).valid := m_btb.io.b_read(fi).data.call
      m_rsb.io.b_write(1).target := ((io.b_read.pc >> log2Floor(p.nInstrByte).U) + (fi + 1).U)
    }
  }

  // ******************************
  //            RESULT
  // ******************************
  for (fi <- 0 until p.nFetchInstr) {
    io.b_read.br_new(fi).valid := m_btb.io.b_read(fi).ready & (m_btb.io.b_read(fi).data.jmp | m_bht.io.b_read(fi).taken)
    io.b_read.br_new(fi).addr := Mux(m_btb.io.b_read(fi).data.ret, m_rsb.io.b_read(1).target, m_btb.io.b_read(fi).data.target) << log2Floor(p.nInstrByte).U
  }

  // ******************************
  //            FIELD
  // ******************************
  if (p.useField) {
    m_btb.io.b_hart.get <> io.b_hart.get
    m_bht.io.b_hart.get <> io.b_hart.get
    m_rsb.io.b_hart.get <> io.b_hart.get

    io.b_hart.get.free := m_btb.io.b_hart.get.free & m_bht.io.b_hart.get.free & m_rsb.io.b_hart.get.free
  }
}


object Nlp extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Nlp(NlpConfigBase), args)
}
