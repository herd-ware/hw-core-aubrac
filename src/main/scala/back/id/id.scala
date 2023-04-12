/*
 * File: id.scala                                                              *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-11 12:19:12 pm                                       *
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
import herd.core.aubrac.common._
import herd.core.aubrac.back.csr.{CsrDecoderBus}


class SlctSource(nInstrBit: Int, nDataBit: Int, withGpr: Boolean) extends Module {
  val io = IO(new Bundle {
    val i_src_type = Input(UInt(OP.NBIT.W))
    val i_rs = if (withGpr) Some(Input(UInt(nDataBit.W))) else None
    val i_imm1 = Input(UInt(nDataBit.W))
    val i_imm2 = Input(UInt(nDataBit.W))
    val i_pc = Input(UInt(nDataBit.W))
    val i_instr = Input(UInt(nInstrBit.W))

    val o_val = Output(UInt(nDataBit.W))
  })

  io.o_val := 0.U
  switch (io.i_src_type) {
    is (OP.XREG) {
      if (withGpr) {
        io.o_val := io.i_rs.get
      } else {
        io.o_val := 0.U
      }
    }
    is (OP.IMM1)  {io.o_val := io.i_imm1}
    is (OP.IMM2)  {io.o_val := io.i_imm2}
    is (OP.PC)    {io.o_val := io.i_pc}
    is (OP.INSTR) {io.o_val := io.i_instr}
  }
}

class SlctImm(nInstrBit: Int, nDataBit: Int) extends Module {
  val io = IO(new Bundle {
    val i_instr = Input(UInt(nInstrBit.W))
    val i_imm_type = Input(UInt(IMM.NBIT.W))
    val o_val = Output(UInt(nDataBit.W))
  })

  io.o_val := 0.U
  switch (io.i_imm_type) {
    is (IMM.is0) {io.o_val := 0.U}
    is (IMM.isR) {io.o_val := Cat(Fill(nDataBit - 6,  io.i_instr(31)),  io.i_instr(30,25))}
    is (IMM.isI) {io.o_val := Cat(Fill(nDataBit - 11, io.i_instr(31)),  io.i_instr(30,20))}
    is (IMM.isS) {io.o_val := Cat(Fill(nDataBit - 11, io.i_instr(31)),  io.i_instr(30,25),  io.i_instr(11,7))}
    is (IMM.isB) {io.o_val := Cat(Fill(nDataBit - 12, io.i_instr(31)),  io.i_instr(7),      io.i_instr(30,25),  io.i_instr(11,8), 0.U(1.W))}
    is (IMM.isU) {io.o_val := Cat(Fill(nDataBit - 31, io.i_instr(31)),  io.i_instr(30,12),  0.U(12.W))}
    is (IMM.isJ) {io.o_val := Cat(Fill(nDataBit - 20, io.i_instr(31)),  io.i_instr(19,12),  io.i_instr(20),     io.i_instr(30,21), 0.U(1.W))}
    is (IMM.isC) {io.o_val := Cat(Fill(nDataBit - 5,  0.B),             io.i_instr(19,15))}
    is (IMM.isV) {io.o_val := Cat(Fill(nDataBit - 5,  io.i_instr(19)),  io.i_instr(19,15))}
    is (IMM.isZ) {io.o_val := Cat(Fill(nDataBit - 10, 0.B),             io.i_instr(29,20))}
  }
}

class SlctSize(nDataBit: Int) extends Module {
  val io = IO(new Bundle {
    val i_val = Input(UInt(nDataBit.W))
    val i_size = Input(UInt(INTSIZE.NBIT.W))
    val i_sign = Input(Bool())
    val o_val = Output(UInt(nDataBit.W))
  })

  io.o_val := io.i_val
  switch (io.i_size) {
    is (INTSIZE.W) {
      if (nDataBit >= 64) {
        when (io.i_sign) {
          io.o_val := Cat(Fill(nDataBit - 32, io.i_val(31)),  io.i_val(31, 0))
        }.otherwise {
          io.o_val := Cat(Fill(nDataBit - 32, 0.B),           io.i_val(31, 0))
        }
      }
    }
  }
}

class IdStage(p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_back = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None
    
    val i_flush = Input(Bool())
    val o_flush = Output(Bool()) 

    val b_in = Flipped(new GenRVIO(p, new BackPortBus(p.debug, p.nHart, p.nAddrBit, p.nInstrBit), UInt(0.W)))
    
    val b_rs = Vec(2, Flipped(new GprReadIO(p)))

    val i_csr = Input(new CsrDecoderBus()) 
    val i_end = Input(Bool())
    val i_pend = Input(Bool())
    val o_stop = Output(Bool())
    val o_stage = Output(new StageBus(p.nHart, p.nAddrBit, p.nInstrBit))  

    val o_hpc_srcdep = Output(Bool())

    val b_out = new GenRVIO(p, new ExCtrlBus(p), new DataBus(p.nDataBit))
  })
  
  val w_lock = Wire(Bool())
  val w_wait = Wire(Bool())
  val w_flush = Wire(Bool())

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
  //            DECODER
  // ******************************
  val m_decoder = Module(new Decoder(p))
  m_decoder.io.i_instr := io.b_in.ctrl.get.instr
  m_decoder.io.i_csr := io.i_csr

  // ******************************
  //              GPR
  // ******************************
  io.b_rs(0).valid := true.B
  io.b_rs(0).hart := io.b_in.ctrl.get.hart
  io.b_rs(0).addr := m_decoder.io.o_data.rs1
  io.b_rs(1).valid := true.B
  io.b_rs(1).hart := io.b_in.ctrl.get.hart
  io.b_rs(1).addr := m_decoder.io.o_data.rs2

  // ******************************
  //             IMM
  // ******************************
  val m_imm1 = Module(new SlctImm(p.nInstrBit, p.nDataBit))
  m_imm1.io.i_instr := io.b_in.ctrl.get.instr
  m_imm1.io.i_imm_type := m_decoder.io.o_data.imm1type

  val m_imm2 = Module(new SlctImm(p.nInstrBit, p.nDataBit))
  m_imm2.io.i_instr := io.b_in.ctrl.get.instr
  m_imm2.io.i_imm_type := m_decoder.io.o_data.imm2type

  // ******************************
  //             SOURCE
  // ******************************
  // ------------------------------
  //               S1
  // ------------------------------
  val m_s1_src = Module(new SlctSource(p.nInstrBit, p.nDataBit, true))
  m_s1_src.io.i_src_type := m_decoder.io.o_data.s1type
  m_s1_src.io.i_rs.get := io.b_rs(0).data
  m_s1_src.io.i_imm1 := m_imm1.io.o_val
  m_s1_src.io.i_imm2 := m_imm2.io.o_val
  m_s1_src.io.i_pc := io.b_in.ctrl.get.pc
  m_s1_src.io.i_instr := io.b_in.ctrl.get.instr

  val m_s1_size = Module(new SlctSize(p.nDataBit))
  m_s1_size.io.i_val := m_s1_src.io.o_val
  m_s1_size.io.i_size := m_decoder.io.o_int.ssize(0)
  m_s1_size.io.i_sign := m_decoder.io.o_int.ssign(0)

  // ------------------------------
  //               S2
  // ------------------------------
  val m_s2_src = Module(new SlctSource(p.nInstrBit, p.nDataBit, true))
  m_s2_src.io.i_src_type := m_decoder.io.o_data.s2type
  m_s2_src.io.i_rs.get := io.b_rs(1).data
  m_s2_src.io.i_imm1 := m_imm1.io.o_val
  m_s2_src.io.i_imm2 := m_imm2.io.o_val
  m_s2_src.io.i_pc := io.b_in.ctrl.get.pc
  m_s2_src.io.i_instr := io.b_in.ctrl.get.instr

  val m_s2_size = Module(new SlctSize(p.nDataBit))
  m_s2_size.io.i_val := m_s2_src.io.o_val
  m_s2_size.io.i_size := m_decoder.io.o_int.ssize(1)
  m_s2_size.io.i_sign := m_decoder.io.o_int.ssign(1)

  // ------------------------------
  //               S3
  // ------------------------------
  val m_s3_src = Module(new SlctSource(p.nInstrBit, p.nDataBit, true))
  m_s3_src.io.i_src_type := m_decoder.io.o_data.s3type
  m_s3_src.io.i_rs.get := io.b_rs(1).data
  m_s3_src.io.i_imm1 := m_imm1.io.o_val
  m_s3_src.io.i_imm2 := m_imm2.io.o_val
  m_s3_src.io.i_pc := io.b_in.ctrl.get.pc
  m_s3_src.io.i_instr := io.b_in.ctrl.get.instr

  val m_s3_size = Module(new SlctSize(p.nDataBit))
  m_s3_size.io.i_val := m_s3_src.io.o_val
  m_s3_size.io.i_size := m_decoder.io.o_int.ssize(2)
  m_s3_size.io.i_sign := m_decoder.io.o_int.ssign(2)

  // ******************************
  //          DEPENDENCIES
  // ******************************
  val w_wait_rs = Wire(Bool())
  
  when (io.b_in.valid & m_decoder.io.o_data.s1type === OP.XREG & ~io.b_rs(0).ready) {w_wait_rs := true.B}
  .elsewhen (io.b_in.valid & m_decoder.io.o_data.s2type === OP.XREG & ~io.b_rs(1).ready) {w_wait_rs := true.B}
  .elsewhen (io.b_in.valid & m_decoder.io.o_data.s3type === OP.XREG & ~io.b_rs(1).ready) {w_wait_rs := true.B}
  .otherwise {w_wait_rs := false.B}

  // ******************************
  //       WAIT EMPTY PIPELINE
  // ******************************
  val w_wait_empty = Wire(Bool())
  w_wait_empty := io.b_in.valid & m_decoder.io.o_info.empty & io.i_pend

  // ******************************
  //            FLUSH
  // ******************************
  val r_flush = RegInit(false.B)

  when (r_flush) {
    r_flush := ~w_back_flush
  }.otherwise {
    r_flush := io.b_in.valid & ~w_flush & ~w_wait & ~w_lock & m_decoder.io.o_trap.valid
  }
  
  w_flush := r_flush | w_back_flush

  if (p.useBranchReg) {
    io.o_flush := r_flush
  } else {
    io.o_flush := io.b_in.valid & ~w_flush & ~w_wait & ~w_lock & m_decoder.io.o_trap.valid
  }
  
  // ******************************
  //              HPC
  // ******************************
  io.o_hpc_srcdep := io.b_in.valid & w_wait_rs

  // ******************************
  //            OUTPUTS
  // ******************************
  // ------------------------------
  //              BUS
  // ------------------------------
  val m_out = Module(new GenReg(p, new ExCtrlBus(p), new DataBus(p.nDataBit), false, false, true))

  w_wait := io.i_end | w_wait_rs | w_wait_empty
  w_lock := ~m_out.io.b_in.ready

  if (p.useField) {
    m_out.io.i_flush := io.b_back.get.flush
  } else {
    m_out.io.i_flush := false.B
  }  

  m_out.io.b_in.valid := io.b_in.valid & w_back_valid & ~w_flush & ~w_wait

  m_out.io.b_in.ctrl.get.info := m_decoder.io.o_info
  m_out.io.b_in.ctrl.get.info.pc := io.b_in.ctrl.get.pc
  m_out.io.b_in.ctrl.get.trap := m_decoder.io.o_trap

  m_out.io.b_in.ctrl.get.int := m_decoder.io.o_int
  if (p.useExtB) {
    switch (m_decoder.io.o_int.unit) {
      is (INTUNIT.BALU)   {m_out.io.b_in.ctrl.get.int.unit := INTUNIT.ALU}
      is (INTUNIT.CLMUL)  {m_out.io.b_in.ctrl.get.int.unit := INTUNIT.MULDIV}
    }
  }
  m_out.io.b_in.ctrl.get.lsu := m_decoder.io.o_lsu
  m_out.io.b_in.ctrl.get.csr := m_decoder.io.o_csr
  m_out.io.b_in.ctrl.get.gpr := m_decoder.io.o_gpr

  m_out.io.b_in.ctrl.get.ext := m_decoder.io.o_ext

  m_out.io.b_in.ctrl.get.hpc.instret := false.B
  m_out.io.b_in.ctrl.get.hpc.alu := (m_decoder.io.o_int.unit === INTUNIT.ALU)
  m_out.io.b_in.ctrl.get.hpc.ld := m_decoder.io.o_lsu.ld
  m_out.io.b_in.ctrl.get.hpc.st := m_decoder.io.o_lsu.st
  m_out.io.b_in.ctrl.get.hpc.bru := (m_decoder.io.o_int.unit === INTUNIT.BRU)
  m_out.io.b_in.ctrl.get.hpc.mispred := false.B
  m_out.io.b_in.ctrl.get.hpc.rdcycle := m_decoder.io.o_csr.read & (m_imm2.io.o_val(11, 0) === CSR.CYCLE.U) 
  m_out.io.b_in.ctrl.get.hpc.jal := (m_decoder.io.o_int.unit === INTUNIT.BRU) & (m_decoder.io.o_int.uop === INTUOP.JAL)
  m_out.io.b_in.ctrl.get.hpc.jalr := (m_decoder.io.o_int.unit === INTUNIT.BRU) & (m_decoder.io.o_int.uop === INTUOP.JALR)
  m_out.io.b_in.ctrl.get.hpc.cflush := (m_decoder.io.o_int.unit === INTUNIT.BRU) & (m_decoder.io.o_int.uop === INTUOP.FLUSH)
  m_out.io.b_in.ctrl.get.hpc.call := (m_decoder.io.o_int.unit === INTUNIT.BRU) & (m_decoder.io.o_int.uop === INTUOP.JALR) & m_decoder.io.o_int.call
  m_out.io.b_in.ctrl.get.hpc.ret := (m_decoder.io.o_int.unit === INTUNIT.BRU) & (m_decoder.io.o_int.uop === INTUOP.JALR) & m_decoder.io.o_int.ret

  m_out.io.b_in.data.get.s1 := m_s1_size.io.o_val
  m_out.io.b_in.data.get.s2 := m_s2_size.io.o_val
  m_out.io.b_in.data.get.s3 := m_s3_size.io.o_val  

  io.b_out <> m_out.io.b_out

  // ------------------------------
  //             LOCK
  // ------------------------------
  io.b_in.ready := w_flush | ~(w_wait | w_lock)

  // ******************************
  //             STAGE
  // ******************************  
  io.o_stop := io.b_in.valid & ~w_lock & ~w_flush & m_decoder.io.o_trap.valid 

  io.o_stage.valid := io.b_in.valid
  io.o_stage.hart := io.b_in.ctrl.get.hart
  io.o_stage.pc := io.b_in.ctrl.get.pc
  io.o_stage.instr := io.b_in.ctrl.get.instr
  io.o_stage.exc_gen := m_decoder.io.o_trap.gen
  io.o_stage.end := io.b_in.valid & m_decoder.io.o_info.end

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_back.get.free := ~m_out.io.o_val.valid
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
    val w_dfp = Wire(new Bundle {
      val pc = UInt(p.nAddrBit.W)
      val instr = UInt(p.nInstrBit.W)
      val wire = Vec(2, UInt(p.nDataBit.W))

      val s1 = UInt(p.nDataBit.W)
      val s2 = UInt(p.nDataBit.W)
      val s3 = UInt(p.nDataBit.W)
    })

    w_dfp.wire(0) := io.b_rs(0).data
    w_dfp.wire(1) := io.b_rs(1).data
    w_dfp.pc := m_out.io.o_reg.ctrl.get.info.pc
    w_dfp.instr := m_out.io.o_reg.ctrl.get.info.instr
    w_dfp.s1 := m_out.io.o_reg.data.get.s1
    w_dfp.s2 := m_out.io.o_reg.data.get.s2
    w_dfp.s3 := m_out.io.o_reg.data.get.s3 

    dontTouch(w_dfp)

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    m_out.io.b_in.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get

    dontTouch(m_out.io.o_reg.ctrl.get.etd.get)
  }
}

object IdStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new IdStage(BackConfigBase), args)
}
