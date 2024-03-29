/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 06:07:08 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.front

import chisel3._
import chisel3.util._

import herd.core.aubrac.common._


// ******************************
//              BUS
// ******************************
class If0CtrlBus(debug: Boolean, nAddrBit: Int, nFetchInstr: Int) extends Bundle {
  val en = Vec(nFetchInstr, Bool())
  val abort = Bool()
  val pc = UInt(nAddrBit.W)

  val etd = if (debug) Some(new EtdBus(1, nAddrBit, 0)) else None
}

class If3CtrlBus(debug: Boolean, nAddrBit: Int, nFetchInstr: Int, nInstrBit: Int) extends Bundle {
  val en = Vec(nFetchInstr, Bool())
  val pc = UInt(nAddrBit.W)
  val instr = Vec(nFetchInstr, UInt(nInstrBit.W))

  val etd = if (debug) Some(Vec(nFetchInstr, new EtdBus(1, nAddrBit, nInstrBit))) else None
}

// ******************************
//         DATA FOOTPRINT
// ******************************
class FrontBus(debug: Boolean, nAddrBit: Int, nInstrBit: Int) extends Bundle {
  val pc = UInt(nAddrBit.W)
  val instr = UInt(nInstrBit.W)
  
  val etd = if (debug) Some(new EtdBus(1, nAddrBit, nInstrBit)) else None
}