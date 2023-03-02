/*
 * File: if2.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 06:04:10 pm                                       *
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


class If2Stage(p: FrontParams) extends Module {
  val io = IO(new Bundle {
    // Resource management bus
    val b_hart = if (p.useField) Some(new RsrcIO(1, p.nField, 1)) else None

    // Stage management buses
    val i_flush = Input(Bool())

    val o_dead = Output(Bool())
    val o_br_dead = Output(new BranchBus(p.nAddrBit))

    // Input data buses
    val b_in = Flipped(new GenRVIO(p, new If0CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr), UInt(0.W)))

    // Instruction memory acknowledgement bus
    val b_imem = new Mb4sAckIO(p.pL0IBus)

    // Output data buses
    val b_out = new GenRVIO(p, new If3CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr, p.nInstrBit), UInt(0.W)) 
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
  //          DEAD REQUEST
  // ******************************
  val r_dead = RegInit(0.U(4.W))
  val w_dead = Wire(Bool())

  w_dead := (r_dead === 15.U)
  when (io.b_in.valid & ~io.b_in.ctrl.get.abort & io.b_imem.read.valid & w_lock) {
    r_dead := r_dead + 1.U
  }.otherwise {
    r_dead := 0.U
  }

  io.o_dead := w_dead
  io.o_br_dead.valid := w_dead
  io.o_br_dead.addr := io.b_in.ctrl.get.pc + (PriorityEncoder(io.b_in.ctrl.get.en) << 2.U)

  // ******************************
  //     INSTRUCTION MEMORY ACK
  // ******************************
  io.b_imem.read.ready(0) := io.b_in.valid.asUInt.orR & (~w_lock | io.i_flush | io.b_in.ctrl.get.abort | w_dead)

  io.b_imem.write := DontCare
  io.b_imem.write.valid := false.B

  // ******************************
  //            REGISTER
  // ******************************
  val m_out = if (p.useIf2Stage) Some(Module(new GenReg(p, new If3CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr, p.nInstrBit), UInt(0.W), false, false, true))) else None

  io.b_in.ready := io.b_imem.read.valid & (~w_lock | io.i_flush | w_hart_flush | io.b_in.ctrl.get.abort | w_dead)

  if (p.useIf2Stage) {  
    w_lock := ~m_out.get.io.b_in.ready
  
    m_out.get.io.i_flush := w_hart_flush
  
    m_out.get.io.b_in.valid := io.b_in.valid & io.b_imem.read.valid & ~io.i_flush & w_hart_valid & ~w_hart_flush & ~io.b_in.ctrl.get.abort & ~w_dead & io.b_in.ctrl.get.en.asUInt.orR
    m_out.get.io.b_in.ctrl.get.en := io.b_in.ctrl.get.en
    m_out.get.io.b_in.ctrl.get.pc := io.b_in.ctrl.get.pc
    for (fi <- 0 until p.nFetchInstr) {
       m_out.get.io.b_in.ctrl.get.instr(fi) := io.b_imem.read.data(p.nInstrBit * (fi + 1) - 1, p.nInstrBit * fi)
    }
  } else {
    w_lock := ~io.b_out.ready
  }

  // ******************************
  //            OUTPUT
  // ******************************
  if (p.useIf2Stage) {
    m_out.get.io.b_out <> io.b_out
  } else {
    io.b_out.valid := io.b_in.valid & io.b_imem.read.valid & w_hart_valid & ~w_hart_flush & ~io.b_in.ctrl.get.abort & ~w_dead & io.b_in.ctrl.get.en.asUInt.orR
    io.b_out.ctrl.get.en := io.b_in.ctrl.get.en
    io.b_out.ctrl.get.pc := io.b_in.ctrl.get.pc
    for (fi <- 0 until p.nFetchInstr) {
       io.b_out.ctrl.get.instr(fi) := io.b_imem.read.data(p.nInstrBit * (fi + 1) - 1, p.nInstrBit * fi)
    }
  }

  // ******************************
  //            FIELD
  // ******************************  
  if (p.useField) {
    if (p.useIf2Stage) io.b_hart.get.free := ~m_out.get.io.b_out.valid else io.b_hart.get.free := true.B
  }

  // ******************************
  //             DEBUG 
  // ******************************   
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(r_dead)
    dontTouch(w_dead)
    dontTouch(io.o_dead)
    dontTouch(io.o_br_dead)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    if (p.useIf2Stage) {
      val w_dfp = Wire(new Bundle {
        val pc = UInt(p.nAddrBit.W)
        val instr = Vec(p.nFetchInstr, UInt(p.nInstrBit.W))
      })

      w_dfp.pc := m_out.get.io.o_val.ctrl.get.pc
      w_dfp.instr := m_out.get.io.o_val.ctrl.get.instr  

      dontTouch(w_dfp)
    }      

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    for (fi <- 0 until p.nFetchInstr) {
      if (p.useIf2Stage) {      
        m_out.get.io.b_in.ctrl.get.etd.get(fi) := DontCare
        m_out.get.io.b_in.ctrl.get.etd.get(fi).done := io.b_in.ctrl.get.etd.get.done
        m_out.get.io.b_in.ctrl.get.etd.get(fi).pc := io.b_in.ctrl.get.etd.get.pc + (fi * p.nInstrByte).U
        m_out.get.io.b_in.ctrl.get.etd.get(fi).instr := io.b_imem.read.data(p.nInstrBit * (fi + 1) - 1, p.nInstrBit * fi)
        m_out.get.io.b_in.ctrl.get.etd.get(fi).tstart := io.b_in.ctrl.get.etd.get.tstart      
      } else {
        io.b_out.ctrl.get.etd.get(fi) := io.b_in.ctrl.get.etd.get
        io.b_out.ctrl.get.etd.get(fi).pc := io.b_in.ctrl.get.etd.get.pc + (fi * p.nInstrByte).U
        io.b_out.ctrl.get.etd.get(fi).instr := io.b_imem.read.data(p.nInstrBit * (fi + 1) - 1, p.nInstrBit * fi)
      }
    }
  }   
}

object If2Stage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new If2Stage(FrontConfigBase), args)
}
