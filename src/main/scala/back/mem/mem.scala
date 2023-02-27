/*
 * File: mem.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:37:24 pm                                       *
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
import herd.common.isa.base._
import herd.common.isa.priv.{EXC => PRIVEXC}
import herd.common.isa.champ.{EXC => CHAMPEXC}
import herd.core.aubrac.common._
import herd.core.aubrac.back.csr.{CsrReadIO}


class MemStage (p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_back = if (p.useDome) Some(new RsrcIO(p.nHart, p.nDome, 1)) else None

    val i_flush = Input(Bool())
    val o_flush = Output(Bool())
    
    val o_stop = Output(Bool())
    val o_stage = Output(new StageBus(p.nHart, p.nAddrBit, p.nInstrBit))

    val b_in = Flipped(new GenRVIO(p, new MemCtrlBus(p), new ResultBus(p.nDataBit)))

    val b_csr = Flipped(new CsrReadIO(p.nDataBit))
    val b_dmem = if (p.useMemStage && (p.nExStage == 1)) Some(new Mb4sReqIO(p.pL0DBus)) else None
    val o_byp = Output(new BypassBus(p.nHart, p.nDataBit))

    val b_out = new GenRVIO(p, new WbCtrlBus(p), new ResultBus(p.nDataBit))
    
    val o_dfp = if (p.debug) Some(Output(new MemDfpBus(p))) else None
  })

  val w_flush = Wire(Bool())
  val w_lock = Wire(Bool())

  // ******************************
  //        BACK PORT STATUS
  // ******************************
  val w_back_valid = Wire(Bool())
  val w_back_flush = Wire(Bool())

  if (p.useDome) {
    w_back_valid := io.b_back.get.valid & ~io.b_back.get.flush
    w_back_flush := io.b_back.get.flush | io.i_flush
  } else {
    w_back_valid := true.B
    w_back_flush := false.B
  }

  // ******************************
  //            CSR PORT
  // ******************************
  val w_csr_read = io.b_in.ctrl.get.csr.read & ~w_flush & w_back_valid
  val w_csr_wait = Mux(w_csr_read, ~io.b_csr.ready | w_lock, false.B)

  io.b_csr.valid := io.b_in.valid & w_csr_read
  io.b_csr.addr := io.b_in.data.get.s3(11,0)

  // ******************************
  //        MEMORY REQUEST
  // ******************************
  val w_wait_mem = Wire(Bool())

  if (p.useMemStage && (p.nExStage == 1)) {
    io.b_dmem.get.valid := io.b_in.valid & io.b_in.ctrl.get.lsu.use & ~w_lock & ~w_flush & w_back_valid
    if (p.useDome) io.b_dmem.get.dome.get := io.b_back.get.dome
    io.b_dmem.get.ctrl.hart := io.b_in.ctrl.get.info.hart
    io.b_dmem.get.ctrl.op := io.b_in.ctrl.get.lsu.uop
    if (p.useExtA) io.b_dmem.get.ctrl.amo.get := io.b_in.ctrl.get.lsu.amo
    io.b_dmem.get.ctrl.size := SIZE.B0.U
    io.b_dmem.get.ctrl.addr := io.b_in.data.get.res

    switch (io.b_in.ctrl.get.lsu.size) {
      is (LSUSIZE.B) {
        io.b_dmem.get.ctrl.size := SIZE.B1.U
      }
      is (LSUSIZE.H) {
        io.b_dmem.get.ctrl.size := SIZE.B2.U
      }
      is (LSUSIZE.W) {
        io.b_dmem.get.ctrl.size := SIZE.B4.U
      }
      is (LSUSIZE.D) {
        if (p.nDataBit >= 64) {
          io.b_dmem.get.ctrl.size := SIZE.B8.U
        }
      }
    }

    w_wait_mem := ~io.b_dmem.get.ready(0) & io.b_in.ctrl.get.lsu.use
  } else {
    w_wait_mem := false.B
  }

  // ******************************
  //           EXCEPTION
  // ******************************
  val w_exc_misalign = Wire(Bool())

  // Detect exception
  w_exc_misalign := false.B
  switch (io.b_in.ctrl.get.lsu.size) {
    is (LSUSIZE.H) {
      w_exc_misalign := (io.b_in.data.get.res(0) =/= 0.U)
    }
    is (LSUSIZE.W) {
      w_exc_misalign := (io.b_in.data.get.res(1, 0) =/= 0.U)
    }
    is (LSUSIZE.D) {
      w_exc_misalign := (io.b_in.data.get.res(2, 0) =/= 0.U)
    }
  }

  // ******************************
  //            FLUSH
  // ******************************
  val r_flush = RegInit(false.B)

  when (r_flush) {
    r_flush := ~io.i_flush
  }.otherwise {
    r_flush := io.b_in.valid & ~w_flush & ~w_wait_mem & ~w_lock & w_exc_misalign
  }

  w_flush := r_flush | w_back_flush

  if (p.useBranchReg) {
    io.o_flush := r_flush
  } else {
    io.o_flush := io.b_in.valid & ~w_wait_mem & ~w_lock & w_exc_misalign
  }

  // ******************************
  //            OUTPUTS
  // ******************************
  // ------------------------------
  //             BUS
  // ------------------------------
  val m_out = Module(new GenReg(p, new MemCtrlBus(p), new ResultBus(p.nDataBit), false, false, true))

  w_lock := ~m_out.io.b_in.ready

  m_out.io.i_flush := w_back_flush

  m_out.io.b_in.valid := io.b_in.valid & w_back_valid & ~w_flush & ~w_wait_mem & ~w_csr_wait
  m_out.io.b_in.ctrl.get.info := io.b_in.ctrl.get.info
  m_out.io.b_in.ctrl.get.trap := io.b_in.ctrl.get.trap
  m_out.io.b_in.ctrl.get.trap.valid := io.b_in.ctrl.get.trap.valid | (io.b_in.ctrl.get.lsu.use & w_exc_misalign)
  when (~io.b_in.ctrl.get.trap.valid & (io.b_in.ctrl.get.lsu.use & w_exc_misalign)) {
    m_out.io.b_in.ctrl.get.trap.src := TRAPSRC.EXC
    if (p.useChamp) {
      m_out.io.b_in.ctrl.get.trap.cause := Mux(io.b_in.ctrl.get.lsu.st, CHAMPEXC.SADDRMIS.U, CHAMPEXC.LADDRMIS.U)
    } else {
      m_out.io.b_in.ctrl.get.trap.cause := Mux(io.b_in.ctrl.get.lsu.st, PRIVEXC.SADDRMIS.U, PRIVEXC.LADDRMIS.U)
    }    
  }
  m_out.io.b_in.ctrl.get.lsu := io.b_in.ctrl.get.lsu
  m_out.io.b_in.ctrl.get.csr := io.b_in.ctrl.get.csr
  m_out.io.b_in.ctrl.get.gpr := io.b_in.ctrl.get.gpr
  m_out.io.b_in.ctrl.get.ext := io.b_in.ctrl.get.ext

  m_out.io.b_in.data.get.s1 := io.b_in.data.get.s1
  m_out.io.b_in.data.get.s3 := io.b_in.data.get.s3
  m_out.io.b_in.data.get.res := Mux(w_csr_read, io.b_csr.data, io.b_in.data.get.res)

  io.b_out <> m_out.io.b_out

  // ------------------------------
  //             LOCK
  // ------------------------------
  io.b_in.ready := w_flush | ~(w_wait_mem | w_csr_wait | w_lock)

  // ******************************
  //             BYPASS
  // ******************************
  io.o_byp.valid := io.b_in.valid & io.b_in.ctrl.get.gpr.en
  io.o_byp.ready := ~(io.b_in.ctrl.get.lsu.ld | (io.b_in.ctrl.get.ext.ext =/= EXT.NONE) | w_csr_wait)
  io.o_byp.hart := io.b_in.ctrl.get.info.hart
  io.o_byp.addr := io.b_in.ctrl.get.gpr.addr
  io.o_byp.data := Mux(w_csr_read, io.b_csr.data, io.b_in.data.get.res)

  // ******************************
  //             STAGE
  // ******************************
  if (p.useMemStage) io.o_stage.valid := io.b_in.valid else io.o_stage.valid := false.B  
  io.o_stage.hart := io.b_in.ctrl.get.info.hart
  io.o_stage.pc := io.b_in.ctrl.get.info.pc
  io.o_stage.instr := io.b_in.ctrl.get.info.instr
  io.o_stage.exc_gen := io.b_in.ctrl.get.trap.gen
  io.o_stage.end := io.b_in.valid & io.b_in.ctrl.get.info.end

  io.o_stop := io.b_in.valid & ~w_flush & w_exc_misalign 

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    io.b_back.get.free := ~m_out.io.o_val.valid
  } 

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    io.o_dfp.get.pc := m_out.io.o_val.ctrl.get.info.pc
    io.o_dfp.get.instr := m_out.io.o_val.ctrl.get.info.instr
    io.o_dfp.get.s1 := m_out.io.o_reg.data.get.s1
    io.o_dfp.get.s3 := m_out.io.o_reg.data.get.s3
    io.o_dfp.get.res := m_out.io.o_reg.data.get.res    
    
    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    m_out.io.b_in.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
    when (io.b_in.ctrl.get.lsu.use) {
      m_out.io.b_in.ctrl.get.etd.get.daddr := io.b_in.data.get.res
    }

    dontTouch(m_out.io.o_reg.ctrl.get.etd.get)
  }
}

object MemStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new MemStage(BackConfigBase), args)
}
