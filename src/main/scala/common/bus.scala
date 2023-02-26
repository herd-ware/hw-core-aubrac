/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:00:07 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.common

import chisel3._
import chisel3.util._

import herd.common.gen._


// ******************************
//            BRANCH
// ******************************
class BranchBus(nAddrBit: Int) extends Bundle {
  val valid = Bool()
  val addr = UInt(nAddrBit.W)
}

// ******************************
//             TRAP
// ******************************
class TrapBus(nAddrBit: Int, nDataBit: Int) extends Bundle {
  val gen = Bool()
  val valid = Bool()
  val pc = UInt(nAddrBit.W)
  val src = UInt(TRAPSRC.NBIT.W)
  val cause = UInt((nDataBit - 1).W)
  val info = UInt(nDataBit.W)
}

// ******************************
//   EXECUTION TRACKER TO DEBUG
// ******************************
class EtdBus(nHart: Int, nAddrBit: Int, nInstrBit: Int) extends Bundle {
  val done = Bool()
  val hart = UInt(nHart.W)
  val pc = UInt(nAddrBit.W)
  val instr = UInt(nInstrBit.W)
  val tstart = UInt(64.W)
  val tend = UInt(64.W)
  val daddr = UInt(nAddrBit.W)
}

// ******************************
//         DATA FOOTPRINT
// ******************************
class DfpBaseBus (nAddrBit: Int, nInstrBit: Int) extends Bundle {
  val pc = UInt(nAddrBit.W)
  val instr = UInt(nInstrBit.W)
}
