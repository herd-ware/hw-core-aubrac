/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:58:27 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.dmu

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.isa.champ._
import herd.core.aubrac.common._


// ******************************
//            DMU IO
// ******************************
class DmuReqCtrlBus(debug: Boolean, nAddrBit: Int) extends Bundle {
  val code = UInt(CODE.NBIT.W)
  val op1 = UInt(OP.NBIT.W)
  val op2 = UInt(OP.NBIT.W)
  val op3 = UInt(OP.NBIT.W)
  val dcs1 = UInt(5.W)
  val dcs2 = UInt(5.W)
  val wb = Bool()

  val etd = if (debug) Some(new EtdBus(1, nAddrBit, 0)) else None
}

class DmuReqDataBus(nDataBit: Int) extends Bundle {
  val s2 = UInt(nDataBit.W)
  val s3 = UInt(nDataBit.W)
}

class DmuReqIO(p: GenParams, nAddrBit: Int, nDataBit: Int) extends GenRVIO(p, new DmuReqCtrlBus(p.debug, nAddrBit), new DmuReqDataBus(nDataBit))

class DmuAckCtrlBus(nAddrBit: Int, nDataBit: Int) extends Bundle {
  val wb = Bool()
  val trap = new TrapBus(nAddrBit, nDataBit)
}

class DmuAckIO(p: GenParams, nAddrBit: Int, nDataBit: Int) extends GenRVIO(p, new DmuAckCtrlBus(nAddrBit, nDataBit), UInt(nDataBit.W))

class DmuTrapBus(nAddrBit: Int) extends Bundle {
  val sw = Bool()
  val dc = UInt(5.W)
  val pc = UInt(nAddrBit.W)
}

class DmuCsrIO (nAddrBit: Int, nChampTrapLvl: Int) extends Bundle {  
  val cdc = Output(UInt(5.W))
  val pdc = Output(UInt(5.W))
  val atl = Input(Vec(nChampTrapLvl, Bool()))
  val etl = Input(Vec(nChampTrapLvl, new DmuTrapBus(nAddrBit)))
}

class DmuCtrlIO(nAddrBit: Int) extends Bundle {
  val dmu_flush = Input(Bool())
  val dmu_free = Output(Bool())
  val pipe_flush = Output(Bool())
  val pipe_br = Output(new BranchBus(nAddrBit))
}

class DmuIO(p: GenParams, nAddrBit: Int, nDataBit: Int, nChampTrapLvl: Int) extends Bundle {
  val req = Flipped(new DmuReqIO(p, nAddrBit, nDataBit))
  val ack = new DmuAckIO(p, nAddrBit, nDataBit)
  val ctrl = new DmuCtrlIO(nAddrBit)
  val csr = new DmuCsrIO(nAddrBit, nChampTrapLvl)
}

// ******************************
//            REGFILE
// ******************************
class RegFileReadIO(p: DmuParams) extends Bundle {
  val addr = Input(UInt(log2Ceil(p.nDomeCfg).W))
  val full = Input(Bool())
  val index = Input(UInt(7.W))
  val ready = Output(Bool())
  val data = Output(new DomeCfgBus(p.pDomeCfg))
}

class RegFileWriteIO (p: DmuParams) extends Bundle {
  val ready = Output(Bool())
  val valid = Input(Bool())
  val sw = Input(UInt(SWUOP.NBIT.W))
  val addr = Input(UInt(log2Ceil(p.nDomeCfg).W))
  val full = Input(Bool())
  val index = Input(UInt(7.W))
  val data = Input(new DomeCfgBus(p.pDomeCfg))
}

class BypassBus (p: DmuParams) extends Bundle {
  val valid = Bool()
  val addr = UInt(log2Ceil(p.nDomeCfg).W)
  val ready = Bool()
  val data = new DomeCfgBus(p.pDomeCfg)
  val full = Bool()
  val index = UInt(7.W)
}

// ******************************
//          CONTROL BUS
// ******************************
class InfoBus (p: DmuParams) extends Bundle {
  val cur = Bool()
  val full = Bool()
  val lock = Bool()
  val check = Bool()
  val wb = Bool()
  
  val trap = Bool()
  val ret = Bool()
  val jump = Bool()
}

class OpCtrlBus extends Bundle {
  val mv = Bool()
  val check_use = Bool()
  val check_uop = UInt(CHECKUOP.NBIT.W)
  val alu_use = Bool()
  val alu_uop = UInt(ALUUOP.NBIT.W)
}

class LsuCtrlBus extends Bundle {
  val ld = Bool()
  val st = Bool()
}

class RegFileCtrlBus(p: DmuParams)  extends Bundle {
  val en = Bool()
  val addr = UInt(log2Ceil(p.nDomeCfg).W)
  val sw = UInt(SWUOP.NBIT.W)
}

// ******************************
//       STAGE CONTROL BUS
// ******************************
class ExStageBus (p: DmuParams) extends Bundle {
  val info = new InfoBus(p)
  val trap = new TrapBus(p.nAddrBit, p.nDataBit)
  val op = new OpCtrlBus()
  val lsu = new LsuCtrlBus()
  val rf = new RegFileCtrlBus(p)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, 0)) else None
}

class CtrlStageBus (p: DmuParams) extends Bundle {
  val info = new InfoBus(p)
  val trap = new TrapBus(p.nAddrBit, p.nDataBit)
  val lsu = new LsuCtrlBus()
  val rf = new RegFileCtrlBus(p)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, 0)) else None
}

// ******************************
//           DATA BUS
// ******************************
class DataBus(p: DmuParams) extends Bundle {
  val s1 = new DomeCfgBus(p.pDomeCfg)
  val s2 = UInt(p.nDataBit.W)
  val s3 = UInt(p.nDataBit.W)
}

class ResultBus(p: DmuParams) extends Bundle {
  val s2 = UInt(p.nDataBit.W)
  val s3 = UInt(p.nDataBit.W)
  val dcres = new DomeCfgBus(p.pDomeCfg)
  val res = UInt(p.nDataBit.W)
}

// ******************************
//              RMR
// ******************************
class RmrReqIO (p: DmuParams) extends Bundle {
  val ready = Input(Bool())
  val valid = Output(Bool())
  val op = Output(UInt(RMRUOP.NBIT.W))
  val dcres = Output(new DomeCfgBus(p.pDomeCfg))
  val target = Output(UInt(p.nAddrBit.W))
}

class RmrStateBus (p: DmuParams) extends Bundle {
  val cur_flush = Bool()
  val cur_free = Bool()
  val fr_sw = Bool()
  val fr_flush = Bool()
  val fr_free = Bool()
  val target = UInt(p.nAddrBit.W)
}

class RmrDomeBus (p: DmuParams) extends Bundle {
  val id = UInt(p.nDataBit.W)
  val mie = Bool()
  val cst = Bool()
  val use = Vec(2, Bool())

  def footprint: UInt = {
    return Cat(mie,id)
  }
}