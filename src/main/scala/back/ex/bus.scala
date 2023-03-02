/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:58:37 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.back

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.core.aubrac.common._


// ******************************
//       INTEGER UNIT BUS
// ******************************
class IntUnitCtrlBus(nHart: Int, useField: Boolean, nField: Int, nAddrBit: Int) extends Bundle {
  val hart = UInt(log2Ceil(nHart).W)
  val field = if (useField) Some(UInt(log2Ceil(nField).W)) else None
  val uop = UInt(INTUOP.NBIT.W)
  val pc = UInt(nAddrBit.W)
  val ssign = Vec(3, Bool())
  val ssize = Vec(3, UInt(INTSIZE.NBIT.W))
  val rsize = UInt(INTSIZE.NBIT.W)
}

class IntUnitDataBus(nDataBit: Int) extends Bundle {
  val s1 = UInt(nDataBit.W)
  val s2 = UInt(nDataBit.W)
  val s3 = UInt(nDataBit.W)
}

class IntUnitIO(p: GenParams, nHart: Int, nAddrBit: Int, nDataBit: Int) extends Bundle {
  val req = Flipped(new GenRVIO(p, new IntUnitCtrlBus(nHart, p.useField, p.nField, nAddrBit), new IntUnitDataBus(nDataBit)))
  val ack = new GenRVIO(p, UInt(0.W), UInt(nDataBit.W))
}

// ******************************
//            MULDIV
// ******************************
class MulDivSrcCtrlBus extends Bundle {
  val lquo_hrem = Bool()
  val rev = Bool()
  val rsize = UInt(INTSIZE.NBIT.W)
}

class MulDivSrcDataBus (nDataBit: Int) extends Bundle {
  val s1_sign = Bool()
  val s2_sign = Bool()
  val us1 = UInt(nDataBit.W)
  val us2 = UInt(nDataBit.W)
}

class MulDivTmpDataBus (nDataBit: Int) extends Bundle {
  val ulquo = UInt(nDataBit.W)
  val uhrem = UInt(nDataBit.W)
}

// ******************************
//         DATA FOOTPRINT
// ******************************
class MulDivDfpBus (nDataBit: Int, isPipe: Boolean) extends Bundle {
  val us1 = UInt(nDataBit.W)
  val us2 = UInt(nDataBit.W)
  val ulquo = UInt(nDataBit.W)
  val uhrem = UInt(nDataBit.W)
  val res = if (isPipe) Some(UInt(nDataBit.W)) else None
}
