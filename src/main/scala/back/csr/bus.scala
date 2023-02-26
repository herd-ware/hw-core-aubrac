/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:58:02 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.back.csr

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.isa.base.{CsrBus => BaseCsrBus}
import herd.common.isa.priv.{CsrBus => PrivCsrBus}
import herd.common.isa.ceps.{CsrBus => CepsCsrBus}
import herd.common.isa.count.{CsrBus => CountCsrBus}
import herd.common.isa.custom.{CsrBus => CustomCsrBus}
import herd.core.aubrac.common._


// ******************************
//           REGISTER
// ******************************
class CsrBus(nDataBit: Int, useCeps: Boolean) extends Bundle {
  val base = new BaseCsrBus()
  val cnt = new CountCsrBus()
  val priv = if (!useCeps) Some(new PrivCsrBus(nDataBit)) else None
  val ceps = if (useCeps) Some(new CepsCsrBus(nDataBit)) else None
}

// ******************************
//             PORT
// ******************************
class CsrReadIO(nDataBit: Int) extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(12.W))
  val ready = Output(Bool())
  val data  = Output(UInt(nDataBit.W))
}

class CsrWriteIO(nDataBit: Int) extends Bundle {
  val valid = Input(Bool())
  val addr  = Input(UInt(12.W))
  val uop   = Input(UInt(UOP.NBIT.W))
  val data  = Input(UInt(nDataBit.W))
  val mask  = Input(UInt(nDataBit.W))
}

class CsrIO(nDataBit: Int) extends Bundle {
  val read = new CsrReadIO(nDataBit)
  val write = new CsrWriteIO(nDataBit)
}

// ******************************
//           INTERFACE
// ******************************
class CsrDecoderBus extends Bundle {
  val cbie = UInt(2.W)
  val cbcfe = Bool()
  val cbze = Bool()
}

class CsrMemIO extends Bundle {
  val l1imiss = Input(UInt(8.W))
  val l1dmiss = Input(UInt(8.W))
  val l2miss  = Input(UInt(8.W))
}

// ******************************
//             DOME
// ******************************
class DomeForceBus(nDataBit: Int) extends Bundle {
  val valid   = Bool()
  val pc      = UInt(nDataBit.W)
  val dome    = UInt(nDataBit.W)
}