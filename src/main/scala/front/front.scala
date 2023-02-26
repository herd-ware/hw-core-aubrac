/*
 * File: front.scala                                                           *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:03:47 pm                                       *
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
import herd.common.dome._
import herd.common.tools._
import herd.core.aubrac.nlp.{NlpReadIO}
import herd.core.aubrac.common._


class Front (p: FrontParams) extends Module {
  val io = IO(new Bundle {
    // Resource management bus
    val b_hart = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None

    // Front management buses
    val i_flush = Input(Bool())

    // Branch change and information buses
    val o_br_next = Output(new BranchBus(p.nAddrBit))
    val o_br_new = if (p.useFastJal) Some(Output(new BranchBus(p.nAddrBit))) else None
    val i_br_dome = if (p.useDome) Some(Input(new BranchBus(p.nAddrBit))) else None
    val i_br_new = Input(new BranchBus(p.nAddrBit))
    
    // Next line predictor
    val b_nlp = if (p.useNlp) Some(Flipped(new NlpReadIO(p.nFetchInstr, p.nAddrBit))) else None

    // Instruction memory bus
    val b_imem = new Mb4sIO(p.pL0IBus)

    // Output data buses
    val b_out = Vec(p.nBackPort, new GenRVIO(p, new FrontBus(p.debug, p.nAddrBit, p.nInstrBit), UInt(0.W)))

    // Debug bus
    val o_dfp = if (p.debug) Some(Output(new FrontDfpBus(p))) else None  
  })

  val w_flush = Wire(Bool())

  val m_pc = Module(new PcStage(p))
  val m_if0 = Module(new If0Stage(p))
  val m_if1 = Module(new If1Stage(p))
  val m_if2 = Module(new If2Stage(p))
  val m_if3 = Module(new If3Stage(p))  

  // ******************************
  //           NEW BRANCH
  // ******************************
  val w_br_new = Wire(new BranchBus(p.nAddrBit))

  if (p.useFastJal) {
    when (io.i_br_new.valid) {
      w_br_new := io.i_br_new
    }.elsewhen(m_if3.io.o_br_new.get.valid){
      w_br_new := m_if3.io.o_br_new.get
    }.otherwise {
      w_br_new := m_if2.io.o_br_dead
    }

    io.o_br_new.get := w_br_new
  } else {
    when (io.i_br_new.valid) {
      w_br_new := io.i_br_new
    }.otherwise {
      w_br_new := m_if2.io.o_br_dead
    }
  }

  if (p.useFastJal) {
    w_flush := m_if3.io.o_flush.get | io.i_flush
  } else {
    w_flush := io.i_flush
  }

  // ******************************
  //          NEXT BRANCH
  // ******************************
  val w_br_next = Wire(new BranchBus(p.nAddrBit))

  w_br_next := DontCare
  w_br_next.valid := false.B

  for (fi <- 0 until p.nFetchInstr) {
    when (m_pc.io.b_out.valid & m_pc.io.b_out.ctrl.get.en(p.nFetchInstr - 1 - fi) & ~m_pc.io.b_out.ctrl.get.abort) {
      w_br_next.valid := true.B
      w_br_next.addr := m_pc.io.b_out.ctrl.get.pc + ((p.nFetchInstr - 1 - fi) * p.nInstrByte).U
    }
  }

  for (fi <- 0 until p.nFetchInstr) {
    when (m_if0.io.b_out.valid & m_if0.io.b_out.ctrl.get.en(p.nFetchInstr - 1 - fi) & ~m_if0.io.b_out.ctrl.get.abort) {
      w_br_next.valid := true.B
      w_br_next.addr := m_if0.io.b_out.ctrl.get.pc + ((p.nFetchInstr - 1 - fi) * p.nInstrByte).U
    }
  }

  if (p.useIf1Stage) {
    for (fi <- 0 until p.nFetchInstr) {
      when (m_if1.io.b_out.valid & m_if1.io.b_out.ctrl.get.en(p.nFetchInstr - 1 - fi) & ~m_if1.io.b_out.ctrl.get.abort) {
        w_br_next.valid := true.B
        w_br_next.addr := m_if1.io.b_out.ctrl.get.pc + ((p.nFetchInstr - 1 - fi) * p.nInstrByte).U
      }
    }
  }

  // ******************************
  //        PROGRAM COUNTER
  // ******************************
  if (p.useDome) {    
    m_pc.io.b_hart.get <> io.b_hart.get
    m_pc.io.i_br_dome.get := io.i_br_dome.get
  }  
  m_pc.io.i_flush := w_flush | m_if2.io.o_dead
  m_pc.io.i_br_new := w_br_new
  if (p.useNlp) m_pc.io.b_nlp.get <> io.b_nlp.get

  // ******************************
  //      INSTRUCTION FETCH 0
  // ******************************
  if (p.useDome) m_if0.io.b_hart.get <> io.b_hart.get   
  m_if0.io.i_flush := w_flush | m_if2.io.o_dead
  m_if0.io.b_in <> m_pc.io.b_out
  m_if0.io.b_imem <> io.b_imem.req

  // ******************************
  //      INSTRUCTION FETCH 1
  // ******************************
  if (p.useDome) m_if1.io.b_hart.get <> io.b_hart.get
  m_if1.io.i_flush := w_flush | m_if2.io.o_dead
  m_if1.io.b_in <> m_if0.io.b_out

  // ******************************
  //      INSTRUCTION FETCH 2
  // ******************************
  if (p.useDome) m_if2.io.b_hart.get <> io.b_hart.get
  m_if2.io.i_flush := w_flush
  m_if2.io.b_in <> m_if1.io.b_out
  m_if2.io.b_imem.read <> io.b_imem.read
  m_if2.io.b_imem.write <> io.b_imem.write

  // ******************************
  //      INSTRUCTION FETCH 3
  // ******************************
  if (p.useDome) m_if3.io.b_hart.get <> io.b_hart.get
  m_if3.io.i_flush := io.i_flush
  m_if3.io.b_in <> m_if2.io.b_out
  if (p.useFastJal) m_if3.io.i_br_next.get := w_br_next
  
  // ******************************
  //           FETCH BUS
  // ******************************
  io.b_out <> m_if3.io.b_out

  io.o_br_next := DontCare
  io.o_br_next.valid := m_if3.io.b_out(0).valid
  io.o_br_next.addr := m_if3.io.b_out(0).ctrl.get.pc

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    io.b_hart.get.free := m_pc.io.b_hart.get.free & m_if0.io.b_hart.get.free & m_if1.io.b_hart.get.free & m_if2.io.b_hart.get.free & m_if3.io.b_hart.get.free
  }  

  // ******************************
  //            DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    io.o_dfp.get.pc := m_pc.io.o_dfp.get
    io.o_dfp.get.if0 := m_if0.io.o_dfp.get
    if (p.useIf1Stage) io.o_dfp.get.if1.get := m_if1.io.o_dfp.get
    if (p.useIf2Stage) io.o_dfp.get.if2.get := m_if2.io.o_dfp.get
    io.o_dfp.get.if3 := m_if3.io.o_dfp.get

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    
  }
}

object Front extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Front(FrontConfigBase), args)
}