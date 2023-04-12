/*
 * File: back.scala                                                            *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-11 06:52:20 pm                                       *
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
import herd.common.field._
import herd.common.core.{HpcPipelineBus}
import herd.common.mem.mb4s._
import herd.common.mem.cbo._
import herd.core.aubrac.common._
import herd.core.aubrac.nlp.{BranchInfoBus}
import herd.core.aubrac.back.csr.{Csr, CsrMemIO}
import herd.core.aubrac.hfu.{Hfu, HfuIO}
import herd.io.core.clint.{ClintIO}


class Back (p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_field = if (p.useField) Some(Vec(p.nField, new FieldIO(p.nAddrBit, p.nDataBit))) else None
    val b_hart = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val i_flush = Input(Bool())
    val o_flush = Output(Bool())

    val b_in = Flipped(new GenRVIO(p, new BackPortBus(p.debug, p.nHart, p.nAddrBit, p.nInstrBit), UInt(0.W)))

    val i_br_next = Input(new BranchBus(p.nInstrBit))
    val o_br_new = Output(new BranchBus(p.nInstrBit))
    val o_br_info = Output(new BranchInfoBus(p.nInstrBit))

    val b_dmem = new Mb4sIO(p.pL0DBus)
    val b_cbo = if (p.useCbo) Some(new CboIO(p.nHart, p.useField, p.nField, p.nAddrBit)) else None
    val b_clint = Flipped(new ClintIO(p.nDataBit))
    
    val b_hfu = if (p.useChamp) Some(Flipped(new HfuIO(p, p.nAddrBit, p.nDataBit, p.nChampTrapLvl))) else None

    val o_hpc = Output(new HpcPipelineBus())
    val i_hpm = Input(Vec(32, UInt(64.W)))

    val o_dbg = if (p.debug) Some(Output(new BackDbgBus(p))) else None
    val o_etd = if (p.debug) Some(Output(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit))) else None
  })

  val m_id = Module(new IdStage(p))
  val m_ex = Module(new ExStage(p))
  val m_mem = Module(new MemStage(p))
  val m_wb = Module(new WbStage(p))

  val m_gpr = Module(new Gpr(p))
  val m_csr = Module(new Csr(p))
  val m_fsm = Module(new Fsm(p.nHart, p.useChamp, p.nInstrBit, p.nAddrBit, p.nDataBit))

  // ******************************
  //             FSM
  // ******************************  
  // ------------------------------
  //            EMPTY
  // ------------------------------
  val w_empty = Wire(Bool())
  
  w_empty := ~m_id.io.o_stage.valid & ~m_mem.io.o_stage.valid & ~m_wb.io.o_stage(0).valid & ~m_wb.io.o_stage(1).valid 
  for (e <- 0 until p.nExStage) {
    when (m_ex.io.o_stage(e).valid) {
      w_empty := false.B
    }
  }

  // ------------------------------
  //            MODULE
  // ------------------------------
  if (p.useField) m_fsm.io.b_hart.get <> io.b_hart.get
  m_fsm.io.i_stop := m_id.io.o_stop | m_mem.io.o_stop | m_wb.io.o_stop
  m_fsm.io.i_empty := w_empty
  m_fsm.io.i_br := m_ex.io.o_br_new.valid
  m_fsm.io.i_wb := m_wb.io.o_last
  m_fsm.io.i_raise := m_wb.io.o_raise
  m_fsm.io.b_clint <> io.b_clint 

  // ******************************
  //            ID STAGE
  // ******************************
  // ------------------------------
  //           END & PEND
  // ------------------------------
  val w_pend = Wire(Vec((p.nExStage + 3), Bool()))
  val w_end = Wire(Vec((p.nExStage + 2), Bool()))

  w_pend(0) := m_wb.io.o_stage(0).valid
  w_pend(1) := m_wb.io.o_stage(1).valid
  w_pend(2) := m_mem.io.o_stage.valid
  for (es <- 0 until p.nExStage) {
    w_pend(3 + es) := m_ex.io.o_stage(es).valid
  }

  w_end(0) := m_wb.io.o_stage(1).end
  w_end(1) := m_mem.io.o_stage.end
  for (es <- 0 until p.nExStage) {
    w_end(2 + es) := m_ex.io.o_stage(es).end
  }

  // ------------------------------
  //            MODULE
  // ------------------------------
  if (p.useField) m_id.io.b_back.get <> io.b_hart.get
  m_id.io.i_flush := m_fsm.io.o_trap.valid | io.i_flush | m_ex.io.o_flush | m_mem.io.o_flush
  m_id.io.b_in <> io.b_in
  m_id.io.b_in.valid := io.b_in.valid & ~m_fsm.io.o_lock
  io.b_in.ready := m_id.io.b_in.ready & ~m_fsm.io.o_lock
  m_id.io.i_csr := m_csr.io.o_decoder(0)
  m_id.io.i_pend := w_pend.asUInt.orR
  m_id.io.i_end := w_end.asUInt.orR
  
  // ******************************
  //         GPR AND BYPASS
  // ******************************
  m_gpr.io.b_read(0) <> m_id.io.b_rs(0)
  m_gpr.io.b_read(1) <> m_id.io.b_rs(1)
  m_gpr.io.b_write(0) <> m_wb.io.b_rd  
  m_gpr.io.i_byp(0) := m_wb.io.o_byp(0)
  m_gpr.io.i_byp(1) := m_wb.io.o_byp(1)
  if (p.useMemStage) {
    m_gpr.io.i_byp(2) := m_mem.io.o_byp
    for (e <- 0 until p.nExStage) {
      m_gpr.io.i_byp(3 + e) := m_ex.io.o_byp(e)
    }
  } else {
    m_gpr.io.i_byp(2) := m_ex.io.o_byp(0)
  }

  // ******************************
  //            EX STAGE
  // ******************************
  // ------------------------------
  //       NEXT CURRENT BRANCH
  // ------------------------------
  val w_br_next = Wire(new BranchBus(p.nInstrBit))

  w_br_next := io.i_br_next

  // ------------------------------
  //            MODULE
  // ------------------------------
  if (p.useField) m_ex.io.b_back.get <> io.b_hart.get
  if (p.useMemStage) {
    m_ex.io.i_flush := m_fsm.io.o_trap.valid | io.i_flush | m_mem.io.o_flush
  } else {
    m_ex.io.i_flush := m_fsm.io.o_trap.valid | io.i_flush
  }
  m_ex.io.b_in <> m_id.io.b_out
  m_ex.io.i_br_next := w_br_next
  if (p.useCbo) m_ex.io.b_cbo.get <> io.b_cbo.get  

  // ******************************
  //           MEM STAGE
  // ******************************
  if (p.useField) m_mem.io.b_back.get <> io.b_hart.get
  m_mem.io.i_flush := m_fsm.io.o_trap.valid | io.i_flush
  m_mem.io.b_in <> m_ex.io.b_out
  if (!p.useMemStage || (p.nExStage > 1)) {
    m_ex.io.b_dmem.get <> io.b_dmem.req
  } else {
    m_mem.io.b_dmem.get <> io.b_dmem.req
  }

  // ******************************
  //              CSR
  // ******************************
  if (p.useField) {
    m_csr.io.b_field.get <> io.b_field.get
    m_csr.io.b_hart.get(0) <> io.b_hart.get
  }
  m_csr.io.b_read(0) <> m_mem.io.b_csr
  m_csr.io.b_write(0) <> m_wb.io.b_csr

  m_csr.io.i_hpm(0) := io.i_hpm
  
  m_csr.io.b_clint(0) <> io.b_clint
  m_csr.io.i_trap(0) := m_fsm.io.o_trap

  // ******************************
  //            WB STAGE
  // ******************************
  if (p.useField) m_wb.io.b_back.get <> io.b_hart.get
  m_wb.io.i_flush := m_fsm.io.o_trap.valid | io.i_flush
  m_wb.io.b_in <> m_mem.io.b_out
  m_wb.io.b_dmem.read <> io.b_dmem.read
  m_wb.io.b_dmem.write <> io.b_dmem.write

  // ******************************
  //              HFU
  // ******************************
  if (p.useChamp) {
    io.b_hfu.get.ctrl.hfu_flush := m_fsm.io.o_trap.valid
    when (m_csr.io.b_trap.get(0).valid) {
      io.b_hfu.get.req <> m_csr.io.b_trap.get(0)
      m_ex.io.b_hfu.get.ready := false.B
    }.otherwise {
      io.b_hfu.get.req <> m_ex.io.b_hfu.get
      m_csr.io.b_trap.get(0).ready := false.B
    }
    m_wb.io.b_hfu.get <> io.b_hfu.get.ack
    m_csr.io.b_hfu.get(0) <> io.b_hfu.get.csr
  }

  // ******************************
  //            FIELD
  // ******************************
  if (p.useField) {
    io.b_hart.get.free := m_id.io.b_back.get.free & m_ex.io.b_back.get.free & m_mem.io.b_back.get.free & m_wb.io.b_back.get.free & m_fsm.io.b_hart.get.free
  }

  // ******************************
  //              I/O
  // ******************************
  io.o_hpc := m_wb.io.o_hpc
  io.o_hpc.srcdep(0) := m_id.io.o_hpc_srcdep

  // ------------------------------
  //             BRANCH
  // ------------------------------
  io.o_br_new := Mux(m_csr.io.o_br_trap(0).valid, m_csr.io.o_br_trap(0), m_ex.io.o_br_new)
  io.o_br_info := m_ex.io.o_br_info

  // ------------------------------
  //             FLUSH
  // ------------------------------
  io.o_flush := m_fsm.io.o_trap.valid | m_id.io.o_flush | m_ex.io.o_flush | m_mem.io.o_flush

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    val r_dbg_last = Reg(UInt(p.nAddrBit.W))

    when (m_wb.io.o_last.valid) {
      r_dbg_last := m_wb.io.o_last.pc
    }

    io.o_dbg.get.last := r_dbg_last
    io.o_dbg.get.x := m_gpr.io.o_dbg.get(0)
    io.o_dbg.get.csr := m_csr.io.o_dbg.get(0)

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    // Default
    val init_etd = Wire(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit))

    init_etd := DontCare
    init_etd.done := false.B

    val r_etd = RegInit(init_etd)
    val r_time = RegInit(0.U(64.W))
    
    // Registers
    r_etd.done := false.B
    when (m_wb.io.o_etd.get.done) {
      r_etd := m_wb.io.o_etd.get
      r_etd.tend := r_time + 1.U
    }

    // Time update
    r_time := r_time + 1.U

    // Output
    io.o_etd.get := r_etd
  }
}

object Back extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Back(BackConfigBase), args)
}
