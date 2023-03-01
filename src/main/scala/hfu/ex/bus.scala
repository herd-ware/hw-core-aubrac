/*
 * File: bus.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:33:03 pm
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

import herd.common.isa.champ._


// ******************************
//              ALU
// ******************************
class AluReqCtrlBus extends Bundle {
  val uop = UInt(ALUUOP.NBIT.W)
}

class AluReqDataBus(p: HfuParams) extends Bundle {
  val s1 = Input(new DomeCfgBus(p.pDomeCfg))
  val s2 = Input(UInt(p.nDataBit.W))
  val s3 = Input(UInt(p.nDataBit.W))
}

class AluAckDataBus(p: HfuParams) extends Bundle {
  val hfres = new DomeCfgBus(p.pDomeCfg)
  val res = UInt(p.nDataBit.W)
}

// ******************************
//            CHECK
// ******************************
class CheckReqCtrlBus extends Bundle {
  val uop = UInt(CHECKUOP.NBIT.W)
  val index = UInt(7.W)
}

class CheckReqDataBus(p: HfuParams) extends Bundle {
  val base = new DomeCfgBus(p.pDomeCfg)
  val hfs = new DomeCfgBus(p.pDomeCfg)
}

class CheckAckDataBus(p: HfuParams) extends Bundle {
  val hfres = new DomeCfgBus(p.pDomeCfg)
  val res = UInt(p.nDataBit.W)
}