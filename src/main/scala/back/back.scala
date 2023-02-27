/*
 * File: back.scala                                                            *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:34:48 pm                                       *
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
import herd.common.dome._
import herd.common.mem.mb4s._
import herd.common.mem.cbo._
import herd.core.aubrac.common._
import herd.core.aubrac.nlp.{BranchInfoBus}
import herd.core.aubrac.back.csr.{Csr, CsrMemIO}
import herd.core.aubrac.dmu.{Dmu, DmuIO}
import herd.io.core.clint.{ClintIO}


class Back (p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_dome = if (p.useDome) Some(Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit))) else None
    val b_hart = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None

    val i_flush = Input(Bool())
    val o_flush = Output(Bool())

    val b_in = Flipped(new GenRVIO(p, new BackPortBus(p.debug, p.nHart, p.nAddrBit, p.nInstrBit), UInt(0.W)))

    val i_br_next = Input(new BranchBus(p.nInstrBit))
    val o_br_new = Output(new BranchBus(p.nInstrBit))
    val o_br_info = Output(new BranchInfoBus(p.nInstrBit))

    val b_dmem = new Mb4sIO(p.pL0DBus)
    val b_cbo = if (p.useCbo) Some(new CboIO(p.nHart, p.useDome, p.nDome, p.nAddrBit)) else None
    val b_dmu = if (p.useChamp) Some(Flipped(new DmuIO(p, p.nAddrBit, p.nDataBit, p.nChampTrapLvl))) else None
    val b_clint = Flipped(new ClintIO(p.nDataBit))

    val b_csr_mem = new CsrMemIO()

    val o_dbg = if (p.debug) Some(Output(new BackDbgBus(p))) else None
    val o_dfp = if (p.debug) Some(Output(new BackDfpBus(p))) else None
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
  if (p.useDome) m_fsm.io.b_hart.get <> io.b_hart.get
  m_fsm.io.i_stop := m_id.io.o_stop | m_mem.io.o_stop | m_wb.io.o_stop
  m_fsm.io.i_empty := w_empty
  m_fsm.io.i_br := m_ex.io.o_br_new.valid
  m_fsm.io.i_wb := m_wb.io.o_stage(1)
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
  if (p.useDome) m_id.io.b_back.get <> io.b_hart.get
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
  if (p.useDome) m_ex.io.b_back.get <> io.b_hart.get
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
  if (p.useDome) m_mem.io.b_back.get <> io.b_hart.get
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
  if (p.useDome) {
    m_csr.io.b_dome.get <> io.b_dome.get
    m_csr.io.b_hart.get(0) <> io.b_hart.get
  }
  m_csr.io.b_read(0) <> m_mem.io.b_csr
  m_csr.io.b_write(0) <> m_wb.io.b_csr

  m_csr.io.i_stat(0) := 0.U.asTypeOf(m_csr.io.i_stat(0))
  m_csr.io.i_stat(0).br := m_ex.io.o_br
  m_csr.io.i_stat(0).mispred := m_ex.io.o_mispred
  m_csr.io.i_stat(0).instret := m_wb.io.o_instret
  m_csr.io.b_mem(0) <> io.b_csr_mem
  m_csr.io.b_clint(0) <> io.b_clint
  m_csr.io.i_trap(0) := m_fsm.io.o_trap

  // ******************************
  //            WB STAGE
  // ******************************
  if (p.useDome) m_wb.io.b_back.get <> io.b_hart.get
  m_wb.io.i_flush := m_fsm.io.o_trap.valid | io.i_flush
  m_wb.io.b_in <> m_mem.io.b_out
  m_wb.io.b_dmem.read <> io.b_dmem.read
  m_wb.io.b_dmem.write <> io.b_dmem.write

  // ******************************
  //              DMU
  // ******************************
  if (p.useChamp) {
    io.b_dmu.get.ctrl.dmu_flush := m_fsm.io.o_trap.valid
    when (m_csr.io.b_trap.get(0).valid) {
      io.b_dmu.get.req <> m_csr.io.b_trap.get(0)
      m_ex.io.b_dmu.get.ready := false.B
    }.otherwise {
      io.b_dmu.get.req <> m_ex.io.b_dmu.get
      m_csr.io.b_trap.get(0).ready := false.B
    }
    m_wb.io.b_dmu.get <> io.b_dmu.get.ack
    m_csr.io.b_dmu.get(0) <> io.b_dmu.get.csr
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    io.b_hart.get.free := m_id.io.b_back.get.free & m_ex.io.b_back.get.free & m_mem.io.b_back.get.free & m_wb.io.b_back.get.free & m_fsm.io.b_hart.get.free
  }

  // ******************************
  //              I/O
  // ******************************
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
      r_dbg_last := m_wb.io.o_last.addr
    }

    io.o_dbg.get.last := r_dbg_last
    io.o_dbg.get.x := m_gpr.io.o_dbg.get(0)
    io.o_dbg.get.csr := m_csr.io.o_dbg.get(0)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    io.o_dfp.get.id := m_id.io.o_dfp.get
    if (p.useMemStage) {
      io.o_dfp.get.ex := m_ex.io.o_dfp_ex.get       
      io.o_dfp.get.mem.get := m_mem.io.o_dfp.get
    } else {
      io.o_dfp.get.ex.pc(0) := m_mem.io.o_dfp.get.pc
      io.o_dfp.get.ex.instr(0) := m_mem.io.o_dfp.get.instr
      io.o_dfp.get.ex.s1(0) := m_mem.io.o_dfp.get.s1
      io.o_dfp.get.ex.s3(0) := m_mem.io.o_dfp.get.s3
      io.o_dfp.get.ex.res(0) := m_mem.io.o_dfp.get.res
    }
    io.o_dfp.get.wb := m_wb.io.o_dfp.get
    io.o_dfp.get.gpr := m_gpr.io.o_dfp.get

    if (p.nExStage > 1) io.o_dfp.get.alu.get := m_ex.io.o_dfp_alu.get   
    if (p.useExtM) io.o_dfp.get.muldiv.get := m_ex.io.o_dfp_muldiv.get

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
