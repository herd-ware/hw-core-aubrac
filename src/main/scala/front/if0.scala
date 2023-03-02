/*
 * File: if0.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:03:50 pm                                       *
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
import herd.common.mem.mb4s._
import herd.common.field._
import herd.core.aubrac.common._


class If0Stage(p: FrontParams) extends Module {
  val io = IO(new Bundle {
    // Resource management bus
    val b_hart = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    // Stage flush bus
    val i_flush = Input(Bool())

    // Input data buses
    val b_in = Flipped(new GenRVIO(p, new If0CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr), UInt(0.W))) 

    // Instruction memory request bus
    val b_imem = new Mb4sReqIO(p.pL0IBus)

    // Output data buses
    val b_out = new GenRVIO(p, new If0CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr), UInt(0.W))

    // Debug buses
    val o_dfp = if (p.debug) Some(Output(UInt(p.nAddrBit.W))) else None
  })

  val w_lock = Wire(Bool())

  // ******************************
  //          HART STATUS
  // ******************************
  val w_hart_valid = Wire(Bool())
  val w_hart_flush = Wire(Bool())

  if (p.useField) {
    w_hart_valid := io.b_hart.get.valid & ~io.b_hart.get.flush
    w_hart_flush := io.b_hart.get.flush
  } else {
    w_hart_valid := true.B
    w_hart_flush := false.B
  }

  // ******************************
  //      INSTRUCTION VALIDITY
  // ******************************
  val r_done = RegInit(false.B)

  if (p.useIMemSeq) {
    when (~r_done) {
      r_done := io.b_in.valid & ~io.b_in.ctrl.get.abort & io.b_imem.ready(0) & w_lock
    }.otherwise {
      r_done := w_lock
    }
  }

  // ******************************
  //   INSTRUCTION MEMORY REQUEST
  // ******************************
  if (p.useIMemSeq) {
    io.b_imem.valid := io.b_in.valid & ~io.b_in.ctrl.get.abort & ~r_done
  } else {
    io.b_imem.valid := w_hart_valid & io.b_in.valid & ~io.i_flush & ~w_hart_flush & ~w_lock
  }

  if (p.useField) {
    io.b_imem.field.get := io.b_hart.get.field
    io.b_imem.ctrl.hart := io.b_hart.get.hart
  } else {
    io.b_imem.ctrl.hart := 0.U
  }

  io.b_imem.ctrl.op := OP.R
  io.b_imem.ctrl.size := SIZE.toSize(p.nFetchInstr * p.nInstrByte).U
  io.b_imem.ctrl.addr := io.b_in.ctrl.get.pc

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
    if (p.useIMemSeq) {
      r_out.valid := io.b_in.valid & (io.b_imem.ready(0) | r_done)
    } else {
      r_out.valid := w_hart_valid & io.b_in.valid & io.b_imem.ready(0) & ~io.i_flush & ~w_hart_flush
    }    
    r_out.ctrl.get.en := io.b_in.ctrl.get.en
    r_out.ctrl.get.pc := io.b_in.ctrl.get.pc
  }

  when (w_lock & (io.i_flush | w_hart_flush)) {
    r_out.ctrl.get.abort := true.B
  }.elsewhen(~w_lock) {
    if (p.useIMemSeq) {
      r_out.ctrl.get.abort := io.b_in.ctrl.get.abort | ((io.i_flush | w_hart_flush) & (io.b_imem.ready(0) | r_done))
    } else {
      r_out.ctrl.get.abort := false.B
    }
  }

  // ------------------------------
  //            CONNECT
  // ------------------------------
  io.b_out.valid := r_out.valid
  io.b_out.ctrl.get := r_out.ctrl.get

  // ------------------------------
  //             LOCK
  // ------------------------------
  if (p.useIMemSeq) {
    io.b_in.ready := (~io.b_imem.ready(0) & (io.i_flush | w_hart_flush)) | ((r_done | io.b_imem.ready(0)) & ~w_lock)
  } else {
    io.b_in.ready := (io.i_flush | w_hart_flush) | (~w_lock & io.b_imem.ready(0))
  }

  // ******************************
  //            FIELD
  // ******************************
  if (p.useField) io.b_hart.get.free := ~r_out.valid

  // ******************************
  //            DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    io.o_dfp.get := r_out.ctrl.get.pc

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    when (~w_lock) {
      r_out.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
    }

    dontTouch(r_out.ctrl.get.etd.get)
  }
}

object If0Stage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new If0Stage(FrontConfigBase), args)
}
