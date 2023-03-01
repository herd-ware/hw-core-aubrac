/*
 * File: decoder.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:30:59 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.back

import chisel3._
import chisel3.util._

import herd.common.isa.riscv.{INSTR => RISCV, CBIE}
import herd.common.isa.priv.{INSTR => PRIV, EXC => PRIVEXC}
import herd.common.isa.champ.{INSTR => CHAMP, EXC => CHAMPEXC}
import herd.core.aubrac.common._
import herd.core.aubrac.back.csr.{CsrDecoderBus}


class Decoder(p: DecoderParams) extends Module {
  val io = IO(new Bundle {
    val i_instr = Input(UInt(p.nInstrBit.W))

    val i_csr = Input(new CsrDecoderBus())

    val o_info = Output(new InfoBus(p.nHart, p.nAddrBit, p.nInstrBit))
    val o_trap = Output(new TrapBus(p.nAddrBit, p.nDataBit))

    val o_int = Output(new IntCtrlBus(p.nBackPort))
    val o_lsu = Output(new LsuCtrlBus())
    val o_csr = Output(new CsrCtrlBus())
    val o_gpr = Output(new GprCtrlBus())

    val o_ext = Output(new ExtCtrlBus())

    val o_data = Output(new DataSlctBus())
  })

  // ******************************
  //         DECODER LOGIC
  // ******************************
  // Integer table
  var t_int = TABLEINT32I.table
                          t_int ++= TABLEINTCSR.table
  if (p.nDataBit >= 64)   t_int ++= TABLEINT64I.table
  if (p.useExtM) {
                          t_int ++= TABLEINT32M.table
    if (p.nDataBit >= 64) t_int ++= TABLEINT64M.table
  }      
  if (p.useExtA) {
                          t_int ++= TABLEINT32A.table
    if (p.nDataBit >= 64) t_int ++= TABLEINT64A.table
  }         
  if (p.useExtB) {
                          t_int ++= TABLEINT32B.table
    if (p.nDataBit >= 64) t_int ++= TABLEINT64B.table
  } 
  if (p.useExtZifencei)   t_int ++= TABLEINTZIFENCEI.table
  if (p.useExtZicbo)      t_int ++= TABLEINTZICBO.table
  if (p.useChamp)         t_int ++= TABLEINTCHAMP.table
  if (!p.useChamp)        t_int ++= TABLEINTPRIV.table

  // LSU table
  var t_lsu = TABLELSU32I.table
  if (p.nDataBit >= 64)   t_lsu ++= TABLELSU64I.table    
  if (p.useExtA) {
                          t_lsu ++= TABLELSU32A.table
    if (p.nDataBit >= 64) t_lsu ++= TABLELSU64A.table
  }  

  // CSR table
  var t_csr = TABLECSR.table

  // 64 bit table
  var t_64b = TABLE64BI.table
  if (p.useExtM)          t_64b ++= TABLE64BM.table
  if (p.useExtA)          t_64b ++= TABLE64BA.table  

  // External table
  var t_ext = TABLEEXT32I.table
  if (p.useChamp)         t_ext ++= TABLEEXTHFU.table

  // Decoded signals
  val w_dec_int = ListLookup(io.i_instr, TABLEINT32I.default, t_int)
  val w_dec_lsu = ListLookup(io.i_instr, TABLELSU32I.default, t_lsu)
  val w_dec_csr = ListLookup(io.i_instr, TABLECSR.default,    t_csr)
  val w_dec_64b = ListLookup(io.i_instr, TABLE64BI.default,   t_64b)
  val w_dec_ext = ListLookup(io.i_instr, TABLEEXT32I.default, t_ext)

  // ******************************
  //            INFO BUS
  // ******************************
  io.o_info.hart := 0.U
  io.o_info.pc := 0.U
  io.o_info.instr := io.i_instr
  io.o_info.end := w_dec_int(1)
  io.o_info.ser := w_dec_int(2)
  io.o_info.empty := w_dec_int(3)

  // ******************************
  //            TRAP BUS
  // ******************************
  io.o_trap.gen := w_dec_int(5)
  io.o_trap.valid := ~w_dec_int(0)
  io.o_trap.pc := DontCare
  io.o_trap.src := TRAPSRC.EXC  
  io.o_trap.info := DontCare

  if (p.useChamp) {
    io.o_trap.cause := CHAMPEXC.IINSTR.U
    when (io.i_instr === CHAMP.WFI) {
      io.o_trap.valid := true.B
      io.o_trap.src := TRAPSRC.WFI
    }
  } else {
    io.o_trap.cause := PRIVEXC.IINSTR.U
    when (io.i_instr === PRIV.WFI) {
      io.o_trap.valid := true.B
      io.o_trap.src := TRAPSRC.WFI
    }.elsewhen(io.i_instr === PRIV.MRET) {
      io.o_trap.valid := true.B
      io.o_trap.src := TRAPSRC.MRET
    }
  }

  if (p.useExtZicbo) {
    when ((io.i_instr === RISCV.CBOINVAL) & (io.i_csr.cbie === CBIE.ILL)) {
      io.o_trap.valid := true.B
    }
    when ((io.i_instr === RISCV.CBOCLEAN) & (~io.i_csr.cbcfe)) {
      io.o_trap.valid := true.B
    }
    when ((io.i_instr === RISCV.CBOFLUSH) & (~io.i_csr.cbcfe)) {
      io.o_trap.valid := true.B
    }
    when ((io.i_instr === RISCV.CBOZERO) & (~io.i_csr.cbze)) {
      io.o_trap.valid := true.B
    }
  }

  // ******************************
  //            EX BUS
  // ******************************
  io.o_int.unit := w_dec_int(6)
  io.o_int.port := 0.U
  io.o_int.uop := w_dec_int(7)
  io.o_int.ssign(0) := w_dec_int(8)
  io.o_int.ssign(1) := w_dec_int(9)
  io.o_int.ssign(2) := w_dec_int(10)

  if (p.nDataBit >= 64) {
    io.o_int.ssize(0) := w_dec_64b(0)
    io.o_int.ssize(1) := w_dec_64b(1)
    io.o_int.ssize(2) := w_dec_64b(2)
    io.o_int.rsize := w_dec_64b(3)
  } else {
    io.o_int.ssize(0) := INTSIZE.X
    io.o_int.ssize(1) := INTSIZE.X
    io.o_int.ssize(2) := INTSIZE.X
    io.o_int.rsize := INTSIZE.X
  }

  if (p.useExtZicbo) {
    when ((io.i_instr === RISCV.CBOINVAL) & (io.i_csr.cbie === CBIE.FLUSH)) {
      io.o_int.uop := INTUOP.FLUSH
    }
  }

  // ******************************
  //            LSU BUS
  // ******************************
  io.o_lsu.use := w_dec_lsu(0)
  io.o_lsu.uop := w_dec_lsu(1)
  io.o_lsu.size := w_dec_lsu(2)
  io.o_lsu.sign := w_dec_lsu(3)
  io.o_lsu.amo := w_dec_lsu(4)

  // ******************************
  //            CSR BUS
  // ******************************
  io.o_csr.read := w_dec_csr(0)
  io.o_csr.write := w_dec_csr(1)
  io.o_csr.uop := w_dec_csr(2)

  // ******************************
  //            WB BUS
  // ******************************
  io.o_gpr.en := w_dec_int(4)
  io.o_gpr.addr := io.i_instr(11,7)

  // ******************************
  //            EXTERNAL
  // ******************************
  // ------------------------------
  //             COMMON
  // ------------------------------
  io.o_ext.ext := w_dec_ext(0)
  io.o_ext.code := w_dec_ext(1)
  io.o_ext.op1 := w_dec_ext(2)
  io.o_ext.op2 := w_dec_ext(3)
  io.o_ext.op3 := w_dec_ext(4)
  io.o_ext.rs1 := io.i_instr(19,15)
  io.o_ext.rs2 := io.i_instr(24,20)
  io.o_ext.rd := io.i_instr(11, 7)

  // ******************************
  //            DATA BUS
  // ******************************
  io.o_data.rs1 := io.i_instr(19,15)
  io.o_data.rs2 := io.i_instr(24,20)
  io.o_data.s1type := w_dec_int(11)
  io.o_data.s2type := w_dec_int(12)
  io.o_data.s3type := w_dec_int(13)
  io.o_data.imm1type := w_dec_int(14)
  io.o_data.imm2type := w_dec_int(15)
}

object Decoder extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Decoder(DecoderConfigBase), args)
}