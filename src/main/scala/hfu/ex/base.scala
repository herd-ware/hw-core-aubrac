/*
 * File: base.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:33:00 pm
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


class Base(p: HfuParams) extends Module {
  val io = IO(new Bundle {
    val i_use_old = Input(Bool())
    val i_exe = Input(new DomeCfgBus(p.pDomeCfg))
    val i_old = Input(new DomeCfgBus(p.pDomeCfg))
    val o_base = Output(new DomeCfgBus(p.pDomeCfg))
  }) 

  // ******************************
  //            DEFAULT
  // ******************************
  io.o_base := io.i_exe
  io.o_base.cap.fromUInt(~CAP.FEA.U(p.nDataBit.W) | io.i_exe.cap.toUInt)

  // ******************************
  //         USE DOME RANGE
  // ******************************
  if (p.useChampExtR) {
    when (io.i_use_old) {
      require(false, "TODO: Base for Dome R Extension must be implemented.")
    }
  // ******************************
  //            NO RANGE
  // ******************************
  } else {
    when (io.i_use_old) {
      io.o_base.status := io.i_old.status
      io.o_base.id := io.i_old.id
      io.o_base.table := (io.i_exe.table | io.i_old.table)
      io.o_base.cap.fromUInt(~CAP.FEA.U(p.nDataBit.W) | io.i_exe.cap.toUInt | io.i_old.cap.toUInt)
    }
  } 
}
object Base extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Base(HfuConfigBase), args)
}