/*
 * File: bus.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-03 08:00:07 am
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

import herd.common.core.{HpcInstrBus}
import herd.common.mem.mb4s.{OP => LSUUOP, AMO => LSUAMO}
import herd.core.aubrac.front.{FrontBus}
import herd.core.aubrac.back.csr.{UOP => CSRUOP, CsrBus}
import herd.core.aubrac.hfu.{CODE => HFUCODE, OP => HFUOP}
import herd.core.aubrac.common._


// ******************************
//           BACK BUS
// ******************************
class BackPortBus(debug: Boolean, nHart : Int, nAddrBit: Int, nInstrBit: Int) extends FrontBus(debug, nAddrBit, nInstrBit) {
  val hart = UInt(log2Ceil(nHart).W)
}

class StageBus(nHart: Int, nAddrBit: Int, nInstrBit: Int) extends Bundle {
  val valid = Bool()
  val hart = UInt(log2Ceil(nHart).W)
  val pc = UInt(nAddrBit.W)
  val instr = UInt(nInstrBit.W)
  val end = Bool()
  val exc_gen = Bool()
}

class BackBus(nAddrBit: Int, nDataBit: Int) extends Bundle {
  val state = UInt(STATE.NBIT.W)
  val pc = UInt(nAddrBit.W)
  val cause = UInt((nDataBit - 1).W)
  val info = UInt(nDataBit.W)
}

class RaiseBus(nAddrBit: Int, nDataBit: Int) extends Bundle {
  val valid = Bool()
  val pc = UInt(nAddrBit.W)
  val src = UInt(TRAPSRC.NBIT.W)
  val cause = UInt((nDataBit - 1).W)
  val info = UInt(nDataBit.W)
}

// ******************************
//       INFORMATIONS BUS
// ******************************
class InfoBus(nHart: Int, nAddrBit: Int, nInstrBit: Int) extends Bundle {
  val hart = UInt(log2Ceil(nHart).W)
  val pc = UInt(nAddrBit.W)
  val instr = UInt(nInstrBit.W)
  val end = Bool()
  val ser = Bool()
  val empty = Bool()
}

// ******************************
//          CONTROL BUS
// ******************************
// ------------------------------
//            INTERNAL
// ------------------------------
class IntCtrlBus(nBackPort: Int) extends Bundle {
  val unit = UInt(INTUNIT.NBIT.W)
  val port = UInt(log2Ceil(nBackPort).W)
  val uop = UInt(INTUOP.NBIT.W)
  val ssign = Vec(3, Bool())
  val ssize = Vec(3, UInt(INTSIZE.NBIT.W))
  val rsize = UInt(INTSIZE.NBIT.W)
}

class LsuCtrlBus extends Bundle {
  val use = Bool()
  val uop = UInt(LSUUOP.NBIT.W)
  val size = UInt(LSUSIZE.NBIT.W)
  val sign = UInt(LSUSIGN.NBIT.W)
  val amo = UInt(LSUAMO.NBIT.W)

  def ld: Bool = use & ((uop === LSUUOP.R) | (uop === LSUUOP.LR) | (uop === LSUUOP.SC) | (uop === LSUUOP.AMO))
  def st: Bool = use & ((uop === LSUUOP.W) | (uop === LSUUOP.SC) | (uop === LSUUOP.AMO))
  def sc: Bool = use & (uop === LSUUOP.SC)
  def a: Bool = use & ((uop === LSUUOP.AMO) | (uop === LSUUOP.SC))
}

class CsrCtrlBus extends Bundle {
  val read = Bool()
  val write = Bool()
  val uop = UInt(CSRUOP.NBIT.W)
}

class GprCtrlBus() extends Bundle {
  val en = Bool()
  val addr = UInt(5.W)
}

// ------------------------------
//            EXTERNAL
// ------------------------------
class ExtCtrlBus extends Bundle {
  val ext = UInt(EXT.NBIT.W)
  val code = UInt(8.W)
  val op1 = UInt(3.W)
  val op2 = UInt(3.W)
  val op3 = UInt(3.W)
  val rs1 = UInt(5.W)
  val rs2 = UInt(5.W)
  val rd = UInt(5.W)
}

// ******************************
//       STAGE CONTROL BUS
// ******************************
class ExCtrlBus(p: BackParams) extends Bundle {
  val info = new InfoBus(p.nHart, p.nAddrBit, p.nInstrBit)
  val trap = new TrapBus(p.nAddrBit, p.nDataBit)

  val int = new IntCtrlBus(p.nBackPort)
  val lsu = new LsuCtrlBus()
  val csr = new CsrCtrlBus()
  val gpr = new GprCtrlBus()

  val ext = new ExtCtrlBus()

  val hpc = new HpcInstrBus()
  
  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class MemCtrlBus(p: BackParams) extends Bundle {
  val info = new InfoBus(p.nHart, p.nAddrBit, p.nInstrBit)
  val trap = new TrapBus(p.nAddrBit, p.nDataBit)

  val lsu = new LsuCtrlBus()
  val csr = new CsrCtrlBus()
  val gpr = new GprCtrlBus()

  val ext = new ExtCtrlBus()

  val hpc = new HpcInstrBus()

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class WbCtrlBus(p: BackParams) extends Bundle {
  val info = new InfoBus(p.nHart, p.nAddrBit, p.nInstrBit)
  val trap = new TrapBus(p.nAddrBit, p.nDataBit)

  val lsu = new LsuCtrlBus()
  val csr = new CsrCtrlBus()
  val gpr = new GprCtrlBus()

  val ext = new ExtCtrlBus()

  val hpc = new HpcInstrBus()

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

class SloadCtrlBus(p: BackParams) extends Bundle {
  val info = new InfoBus(p.nHart, p.nAddrBit, p.nInstrBit)

  val lsu = new LsuCtrlBus()
  val gpr = UInt(5.W)

  val hpc = new HpcInstrBus()
  
  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit)) else None
}

// ******************************
//           DATA BUS
// ******************************
class DataSlctBus extends Bundle {
  val rs1 = UInt(5.W)
  val rs2 = UInt(5.W)
  val s1type = UInt(OP.NBIT.W)
  val s2type = UInt(OP.NBIT.W)
  val s3type = UInt(OP.NBIT.W)
  val imm1type = UInt(IMM.NBIT.W)
  val imm2type = UInt(IMM.NBIT.W)
}

class DataBus(nDataBit: Int) extends Bundle {
  val s1 = UInt(nDataBit.W)
  val s2 = UInt(nDataBit.W)
  val s3 = UInt(nDataBit.W)
  val rs1_link = Bool()
  val rd_link = Bool()
}

class ResultBus(nDataBit: Int) extends Bundle {
  val s1 = UInt(nDataBit.W)
  val s3 = UInt(nDataBit.W)
  val res = UInt(nDataBit.W)
}

class BypassBus(nHart: Int, nDataBit: Int) extends Bundle {
  val valid = Bool()
  val hart = UInt(log2Ceil(nHart).W)
  val ready = Bool()
  val addr = UInt(5.W)
  val data = UInt(nDataBit.W)
}

// ******************************
//             DEBUG
// ******************************
class BackDbgBus (p: BackParams) extends Bundle {
  val last = UInt(p.nAddrBit.W)
  val x = Vec(32, UInt(p.nDataBit.W))
  val csr = new CsrBus(p.nDataBit, p.useChamp)
}
