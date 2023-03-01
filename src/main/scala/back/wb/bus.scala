/*
 * File: bus.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-28 10:37:23 pm
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

import herd.common.gen._
import herd.common.isa.riscv._


class GprReadIO(p: GprParams) extends Bundle {
  val valid = Input(Bool())
  val hart = Input(UInt(log2Ceil(p.nHart).W))
  val addr = Input(UInt(5.W))
  val ready = Output(Bool())
  val data = Output(UInt(p.nDataBit.W))
}

class GprWriteIO(p: GprParams) extends Bundle {
  val valid = Input(Bool())
  val hart = Input(UInt(log2Ceil(p.nHart).W))
  val addr = Input(UInt(5.W))
  val data = Input(UInt(p.nDataBit.W))
  val ready = Output(Bool())
}