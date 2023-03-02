/*
 * File: if1.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:03:53 pm                                       *
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

import herd.common.gen._
import herd.common.field._
import herd.core.aubrac.common._


class If1Stage(p: FrontParams) extends Module {
  val io = IO(new Bundle {
    // Resource management bus
    val b_hart = if (p.useField) Some(new RsrcIO(1, p.nField, 1)) else None

    // Stage management buses
    val i_flush = Input(Bool())

    // Input data buses
    val b_in = Flipped(new GenRVIO(p, new If0CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr), UInt(0.W))) 

    // Output data buses
    val b_out = new GenRVIO(p, new If0CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr), UInt(0.W)) 
    
    // Debug bus
    val o_dfp = if (p.debug && p.useIf1Stage) Some(Output(UInt(p.nAddrBit.W))) else None
  })

  val w_lock = Wire(Bool())

  // ******************************
  //          HART STATUS
  // ******************************
  val w_hart_flush = Wire(Bool())

  if (p.useField) {
    w_hart_flush := io.b_hart.get.flush
  } else {
    w_hart_flush := false.B
  }

  // ******************************
  //            OUTPUT
  // ******************************
  val init_out = Wire(new GenVBus(p, new If0CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr), UInt(0.W)))

  init_out := DontCare
  init_out.valid := false.B

  val r_out = RegInit(init_out)

  // ------------------------------
  //           REGISTER
  // ------------------------------
  w_lock := ~io.b_out.ready & r_out.valid

  when (~w_lock) {
    r_out.valid := io.b_in.valid
    r_out.ctrl.get := io.b_in.ctrl.get
  }

  when (~io.b_out.ready & r_out.valid & (io.i_flush | w_hart_flush)) {
    r_out.ctrl.get.abort := true.B
  }.elsewhen (io.b_out.ready) {
    r_out.ctrl.get.abort := io.b_in.ctrl.get.abort | ((io.i_flush | w_hart_flush) & io.b_in.valid)
  }

  // ------------------------------
  //            CONNECT
  // ------------------------------
  if (p.useIf1Stage) {
    io.b_out.valid := r_out.valid
    io.b_out.ctrl.get := r_out.ctrl.get

    io.b_in.ready := ~r_out.valid | io.b_out.ready
  } else {
    io.b_out.valid := io.b_in.valid
    io.b_out.ctrl.get := io.b_in.ctrl.get

    io.b_in.ready := io.b_out.ready
  }

  // ******************************
  //            FIELD
  // ******************************
  if (p.useField) {
    if (p.useIf1Stage) {
      io.b_hart.get.free := ~r_out.valid & ~r_out.ctrl.get.abort
    } else {
      io.b_hart.get.free := true.B
    }
  }

  // ******************************
  //            DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    if (p.useIf1Stage) {
      io.o_dfp.get := r_out.ctrl.get.pc
    }

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    if (p.useIf1Stage) {
      when (~w_lock) {
        r_out.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
      }

      dontTouch(r_out.ctrl.get.etd.get)

    } else {
      io.b_out.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
    }
  }
}

object If1Stage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new If1Stage(FrontConfigBase), args)
}
