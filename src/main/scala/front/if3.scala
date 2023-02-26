/*
 * File: if3.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:04:00 pm                                       *
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
import herd.common.tools._
import herd.common.dome._
import herd.common.isa.base.{INSTR => BASE}
import herd.core.aubrac.common._


class If3Stage(p: FrontParams) extends Module {
  val io = IO(new Bundle {
    // Resource management bus
    val b_hart = if (p.useDome) Some(new RsrcIO(1, p.nDome, 1)) else None

    // Stage management buses
    val i_flush = Input(Bool())
    val o_flush = if (p.useFastJal) Some(Output(Bool())) else None

    // Input data buses
    val b_in = Flipped(new GenRVIO(p, new If3CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr, p.nInstrBit), UInt(0.W)))

    // Branch
    val i_br_next = if (p.useFastJal) Some(Input(new BranchBus(p.nAddrBit))) else None
    val o_br_new = if (p.useFastJal) Some(Output(new BranchBus(p.nAddrBit))) else None

    // Output data buses
    val b_out = Vec(p.nBackPort, new GenRVIO(p, new FrontBus(p.debug, p.nAddrBit, p.nInstrBit), UInt(0.W)))
  
    // Debug bus
    val o_dfp = if (p.debug) Some(Output(Vec(p.nFetchBufferDepth, new DfpBaseBus(p.nAddrBit, p.nInstrBit)))) else None
  })

  val w_lock = Wire(Bool())
  
  // ******************************
  //          HART STATUS
  // ******************************
  val w_hart_valid = Wire(Bool())
  val w_hart_flush = Wire(Bool())

  if (p.useDome) {
    w_hart_valid := io.b_hart.get.valid & ~io.b_hart.get.flush
    w_hart_flush := io.b_hart.get.flush | io.i_flush
  } else {
    w_hart_valid := true.B
    w_hart_flush := io.i_flush
  }

  // ******************************
  //              PC
  // ******************************
  val w_pc = Wire(Vec(p.nFetchInstr, UInt(32.W)))

  for (fi <- 0 until p.nFetchInstr) {
    w_pc(fi) := io.b_in.ctrl.get.pc + (fi * p.nInstrByte).U
  }

  // ******************************
  //      JUMP AND LINK (JAL)
  // ******************************
  val w_jal_flush = Wire(Vec(p.nFetchInstr, Bool()))

  // ------------------------------
  //             FAST
  // ------------------------------
  if (p.useFastJal) {
    // Decode
    val w_is_jal = Wire(Vec(p.nFetchInstr, Bool()))

    for (fi <- 0 until p.nFetchInstr) {
      w_is_jal(fi) := (io.b_in.ctrl.get.instr(fi) === BASE.JAL)
    }

    // Current informations
    val w_jal_valid = Wire(Vec(p.nFetchInstr, Bool()))
    val w_jal_inc = Wire(Vec(p.nFetchInstr, UInt(32.W)))
    val w_jal_addr = Wire(Vec(p.nFetchInstr, UInt(32.W))) 

    for (fi <- 0 until p.nFetchInstr) {
      w_jal_valid(fi) := io.b_in.valid & io.b_in.ctrl.get.en(fi) & w_is_jal(fi)
      w_jal_inc(fi) := Cat(Fill(12, io.b_in.ctrl.get.instr(fi)(31)), io.b_in.ctrl.get.instr(fi)(19,12), io.b_in.ctrl.get.instr(fi)(20), io.b_in.ctrl.get.instr(fi)(30,21), 0.U(1.W))
      w_jal_addr(fi) := w_pc(fi) + w_jal_inc(fi)
    }

    // Next branch
    val w_br_next = Wire(Vec(p.nFetchInstr, new BranchBus(p.nAddrBit)))

    w_br_next(p.nFetchInstr - 1) := io.i_br_next.get
    for (fi <- 1 until p.nFetchInstr) {
      w_br_next(p.nFetchInstr - 1 - fi).valid := io.b_in.ctrl.get.en(p.nFetchInstr - fi) | w_br_next(p.nFetchInstr - fi).valid
      w_br_next(p.nFetchInstr - 1 - fi).addr := Mux(io.b_in.ctrl.get.en(p.nFetchInstr - fi), w_pc(p.nFetchInstr - fi), w_br_next(p.nFetchInstr - fi).addr)
    }

    // Compare & redirect
    val w_jal_false = Wire(Vec(p.nFetchInstr, Bool()))

    w_jal_false(0) := w_jal_valid(0) & (~w_br_next(0).valid | (w_br_next(0).addr =/= w_jal_addr(0)))
    w_jal_flush(0) := false.B
    for (fi <- 1 until p.nFetchInstr) {
      w_jal_false(fi) := w_jal_valid(fi) & (~w_br_next(fi).valid | (w_br_next(fi).addr =/= w_jal_addr(fi)))
      w_jal_flush(fi) := w_jal_flush(fi - 1) | w_jal_false(fi - 1)
    }

    // New branch
    val w_br_new = Wire(new BranchBus(p.nAddrBit)) 

    w_br_new.valid := w_jal_false.asUInt.orR
    w_br_new.addr := w_jal_addr(PriorityEncoder(w_jal_false.asUInt))

    io.o_flush.get := w_jal_false.asUInt.orR
    io.o_br_new.get := w_br_new  

  // ------------------------------
  //            NORMAL
  // ------------------------------
  } else {
    for (fi <- 0 until p.nFetchInstr) {
      w_jal_flush(fi) := false.B    
    }
  }

  // ******************************
  //             READY
  // ******************************
  io.b_in.ready := (~w_lock | w_hart_flush | io.i_flush)

  // ******************************
  //          FETCH BUFFER
  // ******************************
  if (p.nFetchBufferDepth > 1 || p.nFetchInstr > 1 || p.nBackPort > 1) {
    val m_buf = Module(new GenFifo(p, new FrontBus(p.debug, p.nAddrBit, p.nInstrBit), UInt(0.W), 4, p.nFetchBufferDepth, p.nFetchInstr, p.nBackPort))

    // ------------------------------
    //             WRITE 
    // ------------------------------
    m_buf.io.i_flush := w_hart_flush | io.i_flush
    
    w_lock := ~m_buf.io.b_in(p.nFetchInstr - 1).ready
    for (fi <- 0 until p.nFetchInstr) {
      m_buf.io.b_in(fi).valid := io.b_in.valid & w_hart_valid & ~w_hart_flush & io.b_in.ctrl.get.en(fi) & ~w_jal_flush(fi)
      m_buf.io.b_in(fi).ctrl.get.pc := w_pc(fi)
      m_buf.io.b_in(fi).ctrl.get.instr := io.b_in.ctrl.get.instr(fi)
    }

    // ------------------------------
    //             READ 
    // ------------------------------
    for (bp <- 0 until p.nBackPort) {
      //m_buf.io.b_out(bp) <> io.b_out(bp)
      m_buf.io.b_out(bp).ready := io.b_out(bp).ready 
      io.b_out(bp).valid := m_buf.io.o_val(bp).valid
      io.b_out(bp).ctrl.get := m_buf.io.o_val(bp).ctrl.get
    }

    // ------------------------------
    //             DOME 
    // ------------------------------    
    if (p.useDome) {
      io.b_hart.get.free := ~m_buf.io.b_out(0).valid
    }

    // ------------------------------
    //             DEBUG 
    // ------------------------------
    if (p.debug) {
      // Data Footprint
      for (fb <- 0 until p.nFetchBufferDepth) {
        io.o_dfp.get(fb).pc := m_buf.io.o_val(fb).ctrl.get.pc
        io.o_dfp.get(fb).instr := m_buf.io.o_val(fb).ctrl.get.instr
      } 

      // Time Tracker
      for (fi <- 0 until p.nFetchInstr) {
        m_buf.io.b_in(fi).ctrl.get.etd.get := io.b_in.ctrl.get.etd.get(fi)
      }

      for (bp <- 0 until p.nBackPort) {
        dontTouch(m_buf.io.b_out(bp).ctrl.get.etd.get)
      }
    }   

  // ******************************
  //            REGISTER
  // ******************************
  } else {
    val m_buf = Module(new GenReg(p, new FrontBus(p.debug, p.nAddrBit, p.nInstrBit), UInt(0.W), false, false, true))

    // ------------------------------
    //             WRITE 
    // ------------------------------
    m_buf.io.i_flush := w_hart_flush
    
    w_lock := ~m_buf.io.b_in.ready

    m_buf.io.b_in.valid := io.b_in.valid& w_hart_valid & ~w_hart_flush
    m_buf.io.b_in.ctrl.get.pc := io.b_in.ctrl.get.pc
    m_buf.io.b_in.ctrl.get.instr := io.b_in.ctrl.get.instr(0)

    // ------------------------------
    //             READ 
    // ------------------------------
    io.b_out(0) <> m_buf.io.b_out

    // ------------------------------
    //             DOME 
    // ------------------------------    
    if (p.useDome) {
      io.b_hart.get.free := ~m_buf.io.b_out.valid
    }

    // ------------------------------
    //             DEBUG 
    // ------------------------------   
    if (p.debug) {
      // Data Footprint
      io.o_dfp.get(0).pc := m_buf.io.b_out.ctrl.get.pc
      io.o_dfp.get(0).instr := m_buf.io.b_out.ctrl.get.instr

      // Time Tracker
      m_buf.io.b_in.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get(0)

      dontTouch(m_buf.io.b_out.ctrl.get.etd.get)
    }
  }
}

object If3Stage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new If3Stage(FrontConfigBase), args)
}


