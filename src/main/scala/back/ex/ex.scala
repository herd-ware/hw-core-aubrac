/*
 * File: ex.scala                                                              *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-03 12:53:54 pm                                       *
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
import herd.common.isa.riscv._
import herd.common.isa.priv.{EXC => PRIVEXC}
import herd.common.isa.champ.{EXC => CHAMPEXC}
import herd.common.mem.mb4s._
import herd.common.mem.cbo._
import herd.core.aubrac.common._
import herd.core.aubrac.nlp.{BranchInfoBus}
import herd.core.aubrac.hfu.{HfuReqCtrlBus,HfuReqDataBus}


class ExStage (p: BackParams) extends Module {
  require ((p.nExStage >= 1) && (p.nExStage <= 3), "Only 1 to 3 EX stages are possible.")

  val io = IO(new Bundle {    
    val b_back = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None
    
    val i_flush = Input(Bool())
    val o_flush = Output(Bool())
    
    val o_stop = Output(Bool())
    val o_stage = Output(Vec(p.nExStage, new StageBus(p.nHart, p.nAddrBit, p.nInstrBit)))

    val b_in = Flipped(new GenRVIO(p, new ExCtrlBus(p), new DataBus(p.nDataBit)))

    val i_br_next = Input(new BranchBus(p.nAddrBit))

    val b_dmem = if (!p.useMemStage || (p.nExStage > 1)) Some(new Mb4sReqIO(p.pL0DBus)) else None
    val b_cbo = if (p.useCbo) Some(new CboIO(p.nHart, p.useField, p.nField, p.nAddrBit)) else None
    val b_hfu = if (p.useChamp) Some(new GenRVIO(p, new HfuReqCtrlBus(p.debug, p.nAddrBit), new HfuReqDataBus(p.nDataBit))) else None
    
    val o_byp = Output(Vec(p.nExStage, new BypassBus(p.nHart, p.nDataBit)))
    val o_br_new = Output(new BranchBus(p.nAddrBit))
    val o_br_info = Output(new BranchInfoBus(p.nAddrBit))
    val o_mispred = Output(UInt(p.nDataBit.W))

    val b_out = new GenRVIO(p, new MemCtrlBus(p), new ResultBus(p.nDataBit))
  })

  // ******************************
  //        BACK PORT STATUS
  // ******************************
  val w_back_valid = Wire(Bool())
  val w_back_flush = Wire(Bool())

  if (p.useField) {
    w_back_valid := io.b_back.get.valid & ~io.b_back.get.flush
    w_back_flush := io.b_back.get.flush | io.i_flush
  } else {
    w_back_valid := true.B
    w_back_flush := io.i_flush
  }

  // ******************************
  //  STAGE REGISTERS AND SIGNALS
  // ******************************
  // ------------------------------
  //              EX0
  // ------------------------------
  val w_ex0 = Wire(new GenVBus(p, new IntUnitCtrlBus(p.nHart, p.useField, p.nField, p.nAddrBit), new IntUnitDataBus(p.nDataBit)))
  
  val w_ex0_flush = Wire(Bool())
  val w_ex0_wait = Wire(Bool())
  val w_ex0_unit_wait = Wire(Bool())
  val w_ex0_mem_wait = Wire(Bool())
  val w_ex0_lock = Wire(Bool())
  val w_ex0_trap = Wire(new TrapBus(p.nAddrBit, p.nDataBit))

  val r_ex0_flush = RegInit(false.B)
  
  val m_ex0 = Module(new GenReg(p, new ExCtrlBus(p), new ResultBus(p.nDataBit), false, false, true))

  // ------------------------------
  //              EX1
  // ------------------------------
  val w_ex1 = Wire(new GenVBus(p, new ExCtrlBus(p), new ResultBus(p.nDataBit)))

  val w_ex1_flush = Wire(Bool())
  val w_ex1_wait = Wire(Bool())
  val w_ex1_unit_wait = Wire(Bool())
  val w_ex1_lock = Wire(Bool())

  // Flush register with branches
  val r_pipe_flush = RegInit(false.B)
  
  val w_br_new = Wire(new BranchBus(p.nAddrBit))
  val w_pipe_flush = Wire(Bool())

  val m_ex1 = Module(new GenReg(p, new ExCtrlBus(p), new ResultBus(p.nDataBit), false, false, true))
  
  // ------------------------------
  //              EX2
  // ------------------------------
  val w_ex2 = Wire(new GenVBus(p, new ExCtrlBus(p), new ResultBus(p.nDataBit)))

  val w_ex2_flush = Wire(Bool())
  val w_ex2_wait = Wire(Bool())
  val w_ex2_unit_wait = Wire(Bool())
  val w_ex2_lock = Wire(Bool())

  val m_out = Module(new GenReg(p, new MemCtrlBus(p), new ResultBus(p.nDataBit), false, false, true))

  // ******************************
  //              EX0
  // ******************************
  // ------------------------------
  //         UNIT: DEFAULT
  // ------------------------------
  w_ex0_unit_wait := false.B

  w_ex0.valid := io.b_in.valid & w_back_valid & ~w_ex0_flush
  w_ex0.ctrl.get.hart := io.b_in.ctrl.get.info.hart
  if (p.useField) w_ex0.ctrl.get.field.get := io.b_back.get.field
  w_ex0.ctrl.get.uop := io.b_in.ctrl.get.int.uop
  w_ex0.ctrl.get.pc := io.b_in.ctrl.get.info.pc
  w_ex0.ctrl.get.ssign := io.b_in.ctrl.get.int.ssign
  w_ex0.ctrl.get.ssize := io.b_in.ctrl.get.int.ssize
  w_ex0.ctrl.get.rsize := io.b_in.ctrl.get.int.rsize
  w_ex0.data.get.s1 := io.b_in.data.get.s1
  w_ex0.data.get.s2 := io.b_in.data.get.s2
  w_ex0.data.get.s3 := io.b_in.data.get.s3  

  // ------------------------------
  //           UNIT: ALU
  // ------------------------------
  val m_alu = Module(new Alu(p, p.nDataBit, (p.nExStage > 1), p.useExtB))

  m_alu.io.i_flush := w_back_flush
  m_alu.io.b_port.req.valid := false.B
  m_alu.io.b_port.req.ctrl.get := w_ex0.ctrl.get
  m_alu.io.b_port.req.data.get := w_ex0.data.get

  if (p.nExStage > 1) {
    when (io.b_in.ctrl.get.int.unit === INTUNIT.ALU) {
      w_ex0_unit_wait := ~m_alu.io.b_port.req.ready
    }
    m_alu.io.b_port.req.valid := w_ex0.valid & (io.b_in.ctrl.get.int.unit === INTUNIT.ALU) & ~w_ex0_lock & ~w_ex0_mem_wait
  } else {
    w_ex0_unit_wait := false.B
    m_alu.io.b_port.req.valid := w_ex0.valid & (io.b_in.ctrl.get.int.unit === INTUNIT.ALU)
  }

  // ------------------------------
  //          UNIT: BRU
  // ------------------------------
  val m_bru = Module(new Bru(p, 1, p.nAddrBit, p.nDataBit, p.useExtZifencei, p.useExtZicbo, (p.nExStage > 1)))

  m_bru.io.i_flush := w_back_flush
  m_bru.io.b_port.req.valid := false.B
  m_bru.io.b_port.req.ctrl.get := w_ex0.ctrl.get
  m_bru.io.b_port.req.data.get := w_ex0.data.get
  m_bru.io.i_br_next := io.i_br_next
  m_bru.io.i_call := io.b_in.ctrl.get.int.call  
  m_bru.io.i_ret := io.b_in.ctrl.get.int.ret

  if (p.useCbo) m_bru.io.b_cbo.get <> io.b_cbo.get

  if (p.nExStage > 1) {
    when(io.b_in.ctrl.get.int.unit === INTUNIT.BRU) {
      w_ex0_unit_wait := ~m_bru.io.b_port.req.ready
    }
    m_bru.io.b_port.req.valid := w_ex0.valid & (io.b_in.ctrl.get.int.unit === INTUNIT.BRU) & ~w_ex0_lock
  } else {
    w_ex0_unit_wait := false.B
    m_bru.io.b_port.req.valid := w_ex0.valid & (io.b_in.ctrl.get.int.unit === INTUNIT.BRU)
  }  

  // ------------------------------
  //         UNIT: MULDIV
  // ------------------------------
  val m_muldiv = if (p.useExtM) Some(Module(new MulDiv(p, p.nDataBit, (p.nExStage > 1), p.useExtB, 3))) else None

  if (p.useExtM) {
    m_muldiv.get.io.i_flush := false.B
    m_muldiv.get.io.b_port.req.valid := false.B
    m_muldiv.get.io.b_port.req.ctrl.get := w_ex0.ctrl.get
    m_muldiv.get.io.b_port.req.data.get := w_ex0.data.get

    if (p.nExStage > 1) {
      when (io.b_in.ctrl.get.int.unit === INTUNIT.MULDIV) {
        w_ex0_unit_wait := ~m_muldiv.get.io.b_port.req.ready 
      }      
      m_muldiv.get.io.b_port.req.valid := w_ex0.valid & (io.b_in.ctrl.get.int.unit === INTUNIT.MULDIV) & ~w_ex0_lock
    } else {
      w_ex0_unit_wait := false.B
      m_muldiv.get.io.b_port.req.valid := w_ex0.valid & (io.b_in.ctrl.get.int.unit === INTUNIT.MULDIV)
    }  
  }

  // ------------------------------
  //           UNIT: HFU
  // ------------------------------
  if (p.useChamp) {
    when (io.b_in.ctrl.get.ext.ext === EXT.HFU) {
      w_ex0_unit_wait := ~io.b_hfu.get.ready
    }

    io.b_hfu.get.valid := w_ex0.valid & (io.b_in.ctrl.get.ext.ext === EXT.HFU) & ~w_ex0_lock & ~w_ex0_flush & ~w_pipe_flush
    io.b_hfu.get.ctrl.get.code := io.b_in.ctrl.get.ext.code
    io.b_hfu.get.ctrl.get.op1 := io.b_in.ctrl.get.ext.op1
    io.b_hfu.get.ctrl.get.op2 := io.b_in.ctrl.get.ext.op2
    io.b_hfu.get.ctrl.get.op3 := io.b_in.ctrl.get.ext.op3
    io.b_hfu.get.ctrl.get.hfs1 := io.b_in.ctrl.get.ext.rs1
    io.b_hfu.get.ctrl.get.hfs2 := io.b_in.ctrl.get.ext.rs2
    io.b_hfu.get.ctrl.get.wb := io.b_in.ctrl.get.gpr.en

    io.b_hfu.get.data.get.s2 := w_ex0.data.get.s2
    io.b_hfu.get.data.get.s3 := w_ex0.data.get.s3
  }

  // ------------------------------
  //              LSU
  // ------------------------------
  w_ex0_trap := io.b_in.ctrl.get.trap

  if (!p.useMemStage || (p.nExStage > 1)) {
    // Misalign exception
    when (~io.b_in.ctrl.get.trap.valid) {      
      w_ex0_trap.src := TRAPSRC.EXC
      if (p.useChamp) {
        w_ex0_trap.cause := Mux(io.b_in.ctrl.get.lsu.st, CHAMPEXC.SADDRMIS.U, CHAMPEXC.LADDRMIS.U)
      } else {
        w_ex0_trap.cause := Mux(io.b_in.ctrl.get.lsu.st, PRIVEXC.SADDRMIS.U, PRIVEXC.LADDRMIS.U)
      }      

      switch (io.b_in.ctrl.get.lsu.size) {
        is (LSUSIZE.H) {
          w_ex0_trap.valid := (m_alu.io.o_add(0) =/= 0.U)
        }
        is (LSUSIZE.W) {
          w_ex0_trap.valid := (m_alu.io.o_add(1, 0) =/= 0.U)
        }
        is (LSUSIZE.D) {
          w_ex0_trap.valid := (m_alu.io.o_add(2, 0) =/= 0.U)
        }
      }
    }

    // Multiple ExStage
    if (p.nExStage > 1) {
      val m_dmem = Module(new GenReg(p, new Mb4sReqBus(p.pL0DBus), UInt(0.W), false, false, true))

      m_dmem.io.i_flush := io.i_flush

      w_ex0_mem_wait := ~m_dmem.io.b_in.ready & io.b_in.ctrl.get.lsu.use

      m_dmem.io.b_in.valid := io.b_in.valid & io.b_in.ctrl.get.lsu.use & ~w_ex0_trap.valid & ~w_ex0_lock & ~w_ex0_flush & ~w_pipe_flush
      m_dmem.io.b_in.ctrl.get.hart := 0.U
      m_dmem.io.b_in.ctrl.get.op := io.b_in.ctrl.get.lsu.uop
      m_dmem.io.b_in.ctrl.get.addr := m_alu.io.o_add
      m_dmem.io.b_in.ctrl.get.size := SIZE.B0.U

      switch (io.b_in.ctrl.get.lsu.size) {
        is (LSUSIZE.B) {
          m_dmem.io.b_in.ctrl.get.size := SIZE.B1.U
        }
        is (LSUSIZE.H) {
          m_dmem.io.b_in.ctrl.get.size := SIZE.B2.U
        }
        is (LSUSIZE.W) {
          m_dmem.io.b_in.ctrl.get.size := SIZE.B4.U
        }
        is (LSUSIZE.D) {
          if (p.nDataBit >= 64) {
            m_dmem.io.b_in.ctrl.get.size := SIZE.B8.U
          }
        }
      }

      m_dmem.io.b_out.ready := io.b_dmem.get.ready(0)
      io.b_dmem.get.valid := m_dmem.io.b_out.valid
      if (p.useField) io.b_dmem.get.field.get := io.b_back.get.field
      io.b_dmem.get.ctrl := m_dmem.io.b_out.ctrl.get
      
    // No MemStage
    } else {
      w_ex0_mem_wait := ~io.b_dmem.get.ready(0) & io.b_in.ctrl.get.lsu.use

      io.b_dmem.get.valid := io.b_in.valid & io.b_in.ctrl.get.lsu.use & ~w_ex0_trap.valid & ~w_ex0_lock & ~w_ex0_flush & ~w_pipe_flush
      if (p.useField) io.b_dmem.get.field.get := io.b_back.get.field
      io.b_dmem.get.ctrl.hart := 0.U
      io.b_dmem.get.ctrl.op := io.b_in.ctrl.get.lsu.uop
      io.b_dmem.get.ctrl.addr := m_alu.io.o_add
      io.b_dmem.get.ctrl.size := SIZE.B0.U

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
    }
  } else {
    w_ex0_mem_wait := false.B
  }

  // ------------------------------
  //             FLUSH
  // ------------------------------
  when (r_ex0_flush) {
    r_ex0_flush := ~w_back_flush
  }.otherwise {
    r_ex0_flush := io.b_in.valid & ~w_back_flush & ~w_ex0_flush & ~w_ex0_wait & ~w_ex0_lock & w_ex0_trap.valid
  }

  if (p.useBranchReg) {
    w_ex0_flush := r_pipe_flush | w_back_flush
  } else {
    if (p.nExStage > 1) {
      w_ex0_flush := w_pipe_flush | w_back_flush
    } else {
      w_ex0_flush := w_back_flush
    }
  }  

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  // Wait next stages  
  if (p.nExStage > 1) {
    w_ex0_lock := ~m_ex0.io.b_in.ready
  } else {
    w_ex0_lock := w_ex2_lock
  }

  // Wait
  w_ex0_wait := w_ex0_mem_wait | w_ex0_unit_wait | w_ex0_lock  
  
  // Update registers
  if (p.nExStage > 1) {
    m_ex0.io.i_flush := w_back_flush

    m_ex0.io.b_in.valid := io.b_in.valid & ~(w_ex0_mem_wait | w_ex0_unit_wait | w_ex0_flush)
    m_ex0.io.b_in.ctrl.get := io.b_in.ctrl.get

    m_ex0.io.b_in.data.get.s1 := io.b_in.data.get.s1
    m_ex0.io.b_in.data.get.s3 := io.b_in.data.get.s3
    m_ex0.io.b_in.data.get.res := DontCare

    w_ex1_unit_wait := (m_ex0.io.b_out.ctrl.get.int.unit === INTUNIT.ALU) | (m_ex0.io.b_out.ctrl.get.int.unit === INTUNIT.BRU)

    if (p.nExStage > 2) {
      m_ex0.io.b_out.ready := ~w_ex1_wait | w_ex1_flush
    } else {
      m_ex0.io.b_out.ready := (~w_ex1_wait & ~w_ex2_wait) | w_ex1_flush
    }
    w_ex1.valid := m_ex0.io.b_out.valid & w_back_valid
    w_ex1.ctrl.get := m_ex0.io.b_out.ctrl.get
    w_ex1.data.get := m_ex0.io.b_out.data.get

    io.b_in.ready := w_ex0_flush | ~w_ex0_wait
  } else {
    m_ex0.io.i_flush := false.B
    m_ex0.io.b_in := DontCare
    m_ex0.io.b_in.valid := false.B
    m_ex0.io.b_out.ready := false.B

    w_ex1_unit_wait := (io.b_in.ctrl.get.int.unit === INTUNIT.ALU) | (io.b_in.ctrl.get.int.unit === INTUNIT.BRU)

    w_ex1.valid := io.b_in.valid & w_back_valid & ~(w_ex0_mem_wait | w_ex0_unit_wait | w_ex0_flush)
    w_ex1.ctrl.get := io.b_in.ctrl.get

    w_ex1.data.get.s1 := io.b_in.data.get.s1
    w_ex1.data.get.s3 := io.b_in.data.get.s3
    w_ex1.data.get.res := DontCare

    io.b_in.ready := w_ex0_flush | (~w_ex0_wait & ~w_ex1_wait & ~w_ex2_wait)
  }

  // ******************************
  //              EX1
  // ******************************  
  // ------------------------------
  //          NEW BRANCH
  // ------------------------------
  w_br_new := DontCare
  w_br_new.valid := false.B
  w_pipe_flush := false.B

  // ------------------------------
  //          UNIT: ALU
  // ------------------------------
  m_alu.io.b_port.ack.ready := w_ex1_flush

  when (w_ex1.ctrl.get.int.unit === INTUNIT.ALU) {
    if (p.nExStage > 2) {
      m_alu.io.b_port.ack.ready := w_ex1.valid & ~w_ex1_lock
    } else {
      m_alu.io.b_port.ack.ready := w_ex1.valid & ~w_ex1_lock & ~w_ex2_wait
    }

    w_ex1_unit_wait := ~m_alu.io.b_port.ack.valid
    w_ex1.data.get.res := m_alu.io.b_port.ack.data.get
  }

  // ------------------------------
  //          UNIT: BRU
  // ------------------------------
  // CSR information
  io.o_mispred := 0.U

  m_bru.io.b_port.ack.ready := w_ex1_flush

  when (w_ex1.ctrl.get.int.unit === INTUNIT.BRU) {
    w_ex1_unit_wait := ~m_bru.io.b_port.ack.valid

    if (p.nExStage > 2) {
      m_bru.io.b_port.ack.ready := w_ex1.valid & ~w_ex1_lock

      w_br_new.valid := m_bru.io.b_port.ack.valid & m_bru.io.o_br_new.valid & ~w_ex1_flush & ~w_ex1_lock
      w_pipe_flush := m_bru.io.b_port.ack.valid & m_bru.io.o_flush & ~w_ex1_flush & ~w_ex1_lock
    } else {
      m_bru.io.b_port.ack.ready := w_ex1.valid & ~w_ex1_lock & ~w_ex2_wait
      
      w_br_new.valid := m_bru.io.b_port.ack.valid & m_bru.io.o_br_new.valid & ~w_ex1_flush & ~w_ex2_lock
      w_pipe_flush := m_bru.io.b_port.ack.valid & m_bru.io.o_flush & ~w_ex1_flush & ~w_ex2_lock
    }

    w_ex1_unit_wait := ~m_bru.io.b_port.ack.valid
    w_ex1.data.get.res := m_bru.io.b_port.ack.data.get
    w_br_new.addr := m_bru.io.o_br_new.addr

    w_ex1.ctrl.get.hpc.mispred := m_bru.io.b_port.ack.valid & m_bru.io.o_br_new.valid
  }

  // ------------------------------
  //         UNIT: MULDIV
  // ------------------------------
  if (p.useExtM) {
    m_muldiv.get.io.i_flush := w_ex1_flush 
  }

  // ------------------------------
  //             BRANCH
  // ------------------------------
  // New
  val init_br_new = Wire(new BranchBus(p.nAddrBit))

  init_br_new.valid := false.B
  init_br_new.addr := DontCare  

  val r_br_new = RegInit(init_br_new)

  // Connect
  if (p.useBranchReg) {
    r_br_new := w_br_new

    io.o_br_new := r_br_new
  } else {
    io.o_br_new := w_br_new
  }  

  // Info
  val init_br_info = Wire( new BranchInfoBus(p.nAddrBit))

  init_br_info := DontCare
  init_br_info.valid := false.B  

  val r_br_info = RegInit(init_br_info)

  if (p.useBranchReg) {
    r_br_info := m_bru.io.o_br_info
    io.o_br_info := r_br_info
  } else {
    io.o_br_info := m_bru.io.o_br_info
  }

  // ------------------------------
  //             FLUSH
  // ------------------------------
  if (p.useBranchReg) {
    r_pipe_flush := w_pipe_flush

    io.o_flush := r_pipe_flush
  } else {
    io.o_flush := w_pipe_flush
  } 

  w_ex1_flush := r_pipe_flush | w_back_flush

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  // Wait next stages
  if (p.nExStage > 2) {
    w_ex1_lock := ~m_ex1.io.b_in.ready
  } else {
    w_ex1_lock := w_ex2_lock
  }

  // Wait
  w_ex1_wait := ~w_ex1_flush & (w_ex1_unit_wait | w_ex1_lock)

  // Update registers
  if (p.nExStage > 2) {
    m_ex1.io.i_flush := w_back_flush

    m_ex1.io.b_in.valid := w_ex1.valid & ~(w_ex1_unit_wait | w_ex1_flush)
    m_ex1.io.b_in.ctrl.get := w_ex1.ctrl.get

    m_ex1.io.b_in.data.get.s1 := w_ex1.data.get.s1
    m_ex1.io.b_in.data.get.s3 := w_ex1.data.get.s3
    m_ex1.io.b_in.data.get.res := w_ex1.data.get.res

    w_ex2_unit_wait := (m_ex1.io.b_out.ctrl.get.int.unit === INTUNIT.MULDIV)
    
    m_ex1.io.b_out.ready := ~w_ex2_wait | w_ex2_flush

    w_ex2.valid := m_ex1.io.b_out.valid & w_back_valid
    w_ex2.ctrl.get := m_ex1.io.b_out.ctrl.get
    w_ex2.data.get := m_ex1.io.b_out.data.get
  } else {
    m_ex1.io.i_flush := false.B
    m_ex1.io.b_in := DontCare
    m_ex1.io.b_in.valid := false.B
    m_ex1.io.b_out.ready := false.B

    w_ex2_unit_wait := (m_ex1.io.b_out.ctrl.get.int.unit === INTUNIT.MULDIV)

    w_ex2.valid := w_ex1.valid & ~(w_ex1_unit_wait | w_ex1_flush)
    w_ex2.ctrl.get := w_ex1.ctrl.get
    w_ex2.data.get := w_ex1.data.get
  }

  // ******************************
  //              EX2
  // ******************************
  // ------------------------------
  //         UNIT: MULDIV
  // ------------------------------
  m_muldiv.get.io.b_port.ack.ready := w_ex2_flush

  if (p.useExtM) {
    when (w_ex2.ctrl.get.int.unit === INTUNIT.MULDIV) {
      m_muldiv.get.io.b_port.ack.ready := w_ex2.valid & ~w_ex2_lock

      w_ex2_unit_wait := ~m_muldiv.get.io.b_port.ack.valid
      w_ex2.data.get.res := m_muldiv.get.io.b_port.ack.data.get
    }
  }

  // ------------------------------
  //             FLUSH
  // ------------------------------
  w_ex2_flush := w_back_flush

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  // Wait next stages
  if (p.useMemStage) {
    w_ex2_lock := ~m_out.io.b_in.ready
  } else {
    w_ex2_lock := ~io.b_out.ready
  }

  // Wait
  w_ex2_wait := ~w_ex2_flush & (w_ex2_unit_wait | w_ex2_lock)

  // Update register
  if (p.useMemStage) {
    m_out.io.i_flush := w_back_flush

    m_out.io.b_in.valid := w_ex2.valid & ~(w_ex2_unit_wait | w_ex2_flush)
    m_out.io.b_in.ctrl.get := w_ex2.ctrl.get

    m_out.io.b_in.data.get.s1 := w_ex2.data.get.s1
    m_out.io.b_in.data.get.s3 := w_ex2.data.get.s3
    m_out.io.b_in.data.get.res := w_ex2.data.get.res
    
    m_out.io.b_out <> io.b_out
  } else {
    m_out.io.i_flush := false.B
    m_out.io.b_in := DontCare
    m_out.io.b_in.valid := false.B
    m_out.io.b_out.ready := false.B

    io.b_out.valid := w_ex2.valid & ~(w_ex2_unit_wait | w_ex2_flush)
    io.b_out.ctrl.get := w_ex2.ctrl.get
    io.b_out.data.get := w_ex2.data.get
  }

  // ******************************
  //             BYPASS
  // ******************************
  io.o_byp(0).valid := w_ex2.valid & w_ex2.ctrl.get.gpr.en
  io.o_byp(0).hart := w_ex2.ctrl.get.info.hart
  io.o_byp(0).ready := ~(w_ex2.ctrl.get.lsu.ld | (w_ex2.ctrl.get.ext.ext =/= EXT.NONE) | w_ex2.ctrl.get.csr.read | w_ex2_unit_wait)
  io.o_byp(0).addr := w_ex2.ctrl.get.gpr.addr
  io.o_byp(0).data := w_ex2.data.get.res

  if (p.nExStage > 2) {
    io.o_byp(1).valid := w_ex1.valid & w_ex1.ctrl.get.gpr.en & ~w_ex1_unit_wait
    io.o_byp(1).hart := w_ex1.ctrl.get.info.hart
    io.o_byp(1).ready := ~(w_ex1.ctrl.get.lsu.ld | (w_ex1.ctrl.get.ext.ext =/= EXT.NONE) | w_ex1.ctrl.get.csr.read | w_ex1_unit_wait | (io.b_in.ctrl.get.int.unit === INTUNIT.MULDIV))
    io.o_byp(1).addr := w_ex1.ctrl.get.gpr.addr
    io.o_byp(1).data := w_ex1.data.get.res

    io.o_byp(2).valid := io.b_in.valid & io.b_in.ctrl.get.gpr.en
    io.o_byp(2).hart := io.b_in.ctrl.get.info.hart
    io.o_byp(2).ready := (io.b_in.ctrl.get.int.unit === INTUNIT.ALU) & ~io.b_in.ctrl.get.lsu.ld
    io.o_byp(2).addr := io.b_in.ctrl.get.gpr.addr
    io.o_byp(2).data := m_alu.io.o_byp
  } else if (p.nExStage > 1) {
    io.o_byp(1).valid := io.b_in.valid & io.b_in.ctrl.get.gpr.en
    io.o_byp(1).hart := io.b_in.ctrl.get.info.hart
    io.o_byp(1).ready := (io.b_in.ctrl.get.int.unit === INTUNIT.ALU) & ~io.b_in.ctrl.get.lsu.ld
    io.o_byp(1).addr := io.b_in.ctrl.get.gpr.addr
    io.o_byp(1).data := m_alu.io.o_byp
  }

  // ******************************
  //             STAGE
  // ******************************
  io.o_stage(0).valid := io.b_in.valid
  io.o_stage(0).hart := io.b_in.ctrl.get.info.hart
  io.o_stage(0).pc := io.b_in.ctrl.get.info.pc
  io.o_stage(0).instr := io.b_in.ctrl.get.info.instr
  io.o_stage(0).exc_gen := io.b_in.ctrl.get.trap.gen
  io.o_stage(0).end := io.b_in.valid & io.b_in.ctrl.get.info.end

  if (p.nExStage > 1) {
    io.o_stage(1).valid := m_ex0.io.b_out.valid
    io.o_stage(1).hart := m_ex0.io.b_out.ctrl.get.info.hart
    io.o_stage(1).pc := m_ex0.io.b_out.ctrl.get.info.pc
    io.o_stage(1).instr := m_ex0.io.b_out.ctrl.get.info.instr
    io.o_stage(1).exc_gen := m_ex0.io.b_out.ctrl.get.trap.gen
    io.o_stage(1).end := m_ex0.io.b_out.valid & m_ex0.io.b_out.ctrl.get.info.end
  }

  if (p.nExStage > 2) {
    io.o_stage(2).valid := m_ex1.io.b_out.valid
    io.o_stage(2).hart := m_ex1.io.b_out.ctrl.get.info.hart
    io.o_stage(2).pc := m_ex1.io.b_out.ctrl.get.info.pc
    io.o_stage(2).instr := m_ex1.io.b_out.ctrl.get.info.instr
    io.o_stage(2).exc_gen := m_ex1.io.b_out.ctrl.get.trap.gen
    io.o_stage(2).end := m_ex1.io.b_out.valid & m_ex1.io.b_out.ctrl.get.info.end
  }

  io.o_stop := io.b_in.valid & ~w_ex0_lock & ~w_ex0_flush & w_ex0_trap.valid

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    val w_free_ex0 = Wire(Bool())
    val w_free_ex1 = Wire(Bool())
    val w_free_out = Wire(Bool())
    val w_free_alu = Wire(Bool())
    val w_free_bru = Wire(Bool())
    val w_free_muldiv = Wire(Bool())

    if (p.useExtM) {
      w_free_muldiv := m_muldiv.get.io.o_free
    } else {
      w_free_muldiv := true.B
    }
    if (p.useMemStage) {
      w_free_out := ~m_out.io.o_val.valid
      if (p.nExStage > 1) {
        w_free_ex0 := ~m_ex0.io.o_val.valid
        w_free_alu := m_alu.io.o_free
        w_free_bru := m_bru.io.o_free
        if (p.nExStage > 2) {
          w_free_ex1 := ~m_ex1.io.o_val.valid
        } else {
          w_free_ex1 := true.B
        }
      } else {
        w_free_ex0 := true.B
        w_free_ex1 := true.B
        w_free_alu := true.B
        w_free_bru := true.B
      }
    } else {
      w_free_ex0 := true.B
      w_free_ex1 := true.B
      w_free_out := true.B
      w_free_alu := true.B
      w_free_bru := true.B
    }

    io.b_back.get.free := w_free_ex0 & w_free_ex1 & w_free_out & w_free_alu & w_free_bru & w_free_muldiv
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    val w_dfp = Wire(Vec(p.nExStage, new Bundle {
      val pc = UInt(p.nAddrBit.W)
      val instr = UInt(p.nInstrBit.W)

      val s1 = UInt(p.nDataBit.W)
      val s3 = UInt(p.nDataBit.W)
      val res = UInt(p.nDataBit.W)
    }))

    if (p.useMemStage) {    
      w_dfp(p.nExStage - 1).pc := m_out.io.o_reg.ctrl.get.info.pc
      w_dfp(p.nExStage - 1).instr := m_out.io.o_reg.ctrl.get.info.instr
      w_dfp(p.nExStage - 1).s1 := m_out.io.o_reg.data.get.s1
      w_dfp(p.nExStage - 1).s3 := m_out.io.o_reg.data.get.s3
      w_dfp(p.nExStage - 1).res := m_out.io.o_reg.data.get.res
            

      if (p.nExStage > 1) {    
        w_dfp(0).pc := m_ex0.io.o_reg.ctrl.get.info.pc
        w_dfp(0).instr := m_ex0.io.o_reg.ctrl.get.info.instr
        w_dfp(0).s1 := m_ex0.io.o_reg.data.get.s1
        w_dfp(0).s3 := m_ex0.io.o_reg.data.get.s3
        w_dfp(0).res := m_ex0.io.o_reg.data.get.res             
      }

      if (p.nExStage > 2) {    
        w_dfp(1).pc := m_ex1.io.o_reg.ctrl.get.info.pc
        w_dfp(1).instr := m_ex1.io.o_reg.ctrl.get.info.instr
        w_dfp(1).s1 := m_ex1.io.o_reg.data.get.s1
        w_dfp(1).s3 := m_ex1.io.o_reg.data.get.s3
        w_dfp(1).res := m_ex1.io.o_reg.data.get.res        
      }
    }
    
    dontTouch(w_dfp)

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    if (p.useChamp) io.b_hfu.get.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get

    dontTouch(m_out.io.o_reg.ctrl.get.etd.get)
  }
}

object ExStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new ExStage(BackConfigBase), args)
}
