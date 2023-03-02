/*
 * File: bus.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:32:33 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.hfu

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.isa.champ._
import herd.core.aubrac.common._


// ******************************
//            HFU IO
// ******************************
class HfuReqCtrlBus(debug: Boolean, nAddrBit: Int) extends Bundle {
  val code = UInt(CODE.NBIT.W)
  val op1 = UInt(OP.NBIT.W)
  val op2 = UInt(OP.NBIT.W)
  val op3 = UInt(OP.NBIT.W)
  val hfs1 = UInt(5.W)
  val hfs2 = UInt(5.W)
  val wb = Bool()

  val etd = if (debug) Some(new EtdBus(1, nAddrBit, 0)) else None
}

class HfuReqDataBus(nDataBit: Int) extends Bundle {
  val s2 = UInt(nDataBit.W)
  val s3 = UInt(nDataBit.W)
}

class HfuReqIO(p: GenParams, nAddrBit: Int, nDataBit: Int) extends GenRVIO(p, new HfuReqCtrlBus(p.debug, nAddrBit), new HfuReqDataBus(nDataBit))

class HfuAckCtrlBus(nAddrBit: Int, nDataBit: Int) extends Bundle {
  val wb = Bool()
  val trap = new TrapBus(nAddrBit, nDataBit)
}

class HfuAckIO(p: GenParams, nAddrBit: Int, nDataBit: Int) extends GenRVIO(p, new HfuAckCtrlBus(nAddrBit, nDataBit), UInt(nDataBit.W))

class HfuTrapBus(nAddrBit: Int) extends Bundle {
  val sw = Bool()
  val hf = UInt(5.W)
  val pc = UInt(nAddrBit.W)
}

class HfuCsrIO (nAddrBit: Int, nChampTrapLvl: Int) extends Bundle {  
  val chf = Output(UInt(5.W))
  val phf = Output(UInt(5.W))
  val atl = Input(Vec(nChampTrapLvl, Bool()))
  val etl = Input(Vec(nChampTrapLvl, new HfuTrapBus(nAddrBit)))
}

class HfuCtrlIO(nAddrBit: Int) extends Bundle {
  val hfu_flush = Input(Bool())
  val hfu_free = Output(Bool())
  val pipe_flush = Output(Bool())
  val pipe_br = Output(new BranchBus(nAddrBit))
}

class HfuIO(p: GenParams, nAddrBit: Int, nDataBit: Int, nChampTrapLvl: Int) extends Bundle {
  val req = Flipped(new HfuReqIO(p, nAddrBit, nDataBit))
  val ack = new HfuAckIO(p, nAddrBit, nDataBit)
  val ctrl = new HfuCtrlIO(nAddrBit)
  val csr = new HfuCsrIO(nAddrBit, nChampTrapLvl)
}

// ******************************
//            REGFILE
// ******************************
class RegFileReadIO(p: HfuParams) extends Bundle {
  val addr = Input(UInt(log2Ceil(p.nChampReg).W))
  val full = Input(Bool())
  val index = Input(UInt(7.W))
  val ready = Output(Bool())
  val data = Output(new FieldStructBus(p.pFieldStruct))
}

class RegFileWriteIO (p: HfuParams) extends Bundle {
  val ready = Output(Bool())
  val valid = Input(Bool())
  val sw = Input(UInt(SWUOP.NBIT.W))
  val addr = Input(UInt(log2Ceil(p.nChampReg).W))
  val full = Input(Bool())
  val index = Input(UInt(7.W))
  val data = Input(new FieldStructBus(p.pFieldStruct))
}

class BypassBus (p: HfuParams) extends Bundle {
  val valid = Bool()
  val addr = UInt(log2Ceil(p.nChampReg).W)
  val ready = Bool()
  val data = new FieldStructBus(p.pFieldStruct)
  val full = Bool()
  val index = UInt(7.W)
}

// ******************************
//          CONTROL BUS
// ******************************
class InfoBus (p: HfuParams) extends Bundle {
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

class RegFileCtrlBus(p: HfuParams)  extends Bundle {
  val en = Bool()
  val addr = UInt(log2Ceil(p.nChampReg).W)
  val sw = UInt(SWUOP.NBIT.W)
}

// ******************************
//       STAGE CONTROL BUS
// ******************************
class ExStageBus (p: HfuParams) extends Bundle {
  val info = new InfoBus(p)
  val trap = new TrapBus(p.nAddrBit, p.nDataBit)
  val op = new OpCtrlBus()
  val lsu = new LsuCtrlBus()
  val rf = new RegFileCtrlBus(p)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, 0)) else None
}

class CtrlStageBus (p: HfuParams) extends Bundle {
  val info = new InfoBus(p)
  val trap = new TrapBus(p.nAddrBit, p.nDataBit)
  val lsu = new LsuCtrlBus()
  val rf = new RegFileCtrlBus(p)

  val etd = if (p.debug) Some(new EtdBus(p.nHart, p.nAddrBit, 0)) else None
}

// ******************************
//           DATA BUS
// ******************************
class DataBus(p: HfuParams) extends Bundle {
  val s1 = new FieldStructBus(p.pFieldStruct)
  val s2 = UInt(p.nDataBit.W)
  val s3 = UInt(p.nDataBit.W)
}

class ResultBus(p: HfuParams) extends Bundle {
  val s2 = UInt(p.nDataBit.W)
  val s3 = UInt(p.nDataBit.W)
  val hfres = new FieldStructBus(p.pFieldStruct)
  val res = UInt(p.nDataBit.W)
}

// ******************************
//              RMR
// ******************************
class RmrReqIO (p: HfuParams) extends Bundle {
  val ready = Input(Bool())
  val valid = Output(Bool())
  val op = Output(UInt(RMRUOP.NBIT.W))
  val hfres = Output(new FieldStructBus(p.pFieldStruct))
  val target = Output(UInt(p.nAddrBit.W))
}

class RmrStateBus (p: HfuParams) extends Bundle {
  val cur_flush = Bool()
  val cur_free = Bool()
  val fr_sw = Bool()
  val fr_flush = Bool()
  val fr_free = Bool()
  val target = UInt(p.nAddrBit.W)
}

class RmrFieldBus (p: HfuParams) extends Bundle {
  val id = UInt(p.nDataBit.W)
  val mie = Bool()
  val cst = Bool()
  val use = Vec(2, Bool())

  def footprint: UInt = {
    return Cat(mie,id)
  }
}