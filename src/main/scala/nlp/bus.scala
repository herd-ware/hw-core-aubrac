/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:06:04 pm                                       *
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

import herd.core.aubrac.common._


// ******************************
//              BHT
// ******************************
class BhtReadIO(p: BhtParams) extends Bundle {
  val slct = Input(UInt(log2Ceil(p.nEntry).W))
  val taken = Output(Bool())
}

class BhtWriteIO(p: BhtParams) extends Bundle {
  val valid = Input(Bool())
  val slct = Input(UInt(log2Ceil(p.nEntry).W))
  val taken = Input(Bool())
}

// ******************************
//              BTB
// ******************************
class BtbDataBus(p: BtbParams) extends Bundle {
  val jmp = Bool()
  val call = Bool()
  val ret = Bool()
  val target = UInt(p.nTargetBit.W)
}

class BtbReadIO(p: BtbParams) extends Bundle {
  val valid = Input(Bool())
  val tag = Input(UInt(p.nTagBit.W))
  val ready = Output(Bool())
  val data = Output(new BtbDataBus(p))
}

class BtbWriteIO(p: BtbParams) extends Bundle {
  val valid = Input(Bool())
  val tag = Input(UInt(p.nTagBit.W))
  val data = Input(new BtbDataBus(p))
  val ready = Output(Bool())
}

// ******************************
//              RSB
// ******************************
class RsbReadIO(p: RsbParams) extends Bundle {
  val valid = Input(Bool())
  val target = Output(UInt(p.nTargetBit.W))
}

class RsbWriteIO(p: RsbParams) extends Bundle {
  val valid = Input(Bool())
  val target = Input(UInt(p.nTargetBit.W))
}

// ******************************
//             NLP
// ******************************
class BranchInfoBus(nAddrBit: Int) extends Bundle {
  val valid = Bool()
  val pc = UInt(nAddrBit.W)
  val br = Bool()
  val taken = Bool()
  val jmp = Bool()
  val call = Bool()
  val ret = Bool()
  val target = UInt(nAddrBit.W)
}

class NlpReadIO(nFetchInstr: Int, nAddrBit: Int) extends Bundle {
  val valid = Input(Vec(nFetchInstr, Bool()))
  val pc = Input(UInt(nAddrBit.W))
  val br_new = Output(Vec(nFetchInstr, new BranchBus(nAddrBit)))
}