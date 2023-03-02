/*
 * File: wb.scala                                                              *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 12:22:17 pm                                       *
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
import herd.common.mem.mb4s._
import herd.common.isa.riscv._
import herd.common.isa.priv.{EXC => PRIVEXC}
import herd.common.isa.champ.{EXC => CHAMPEXC}
import herd.core.aubrac.common._
import herd.core.aubrac.back.csr.{CsrWriteIO}
import herd.core.aubrac.hfu.{HfuAckIO}


class WbStage (p: BackParams) extends Module {
  val io = IO(new Bundle {
    val b_back = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val i_flush = Input(Bool())

    val o_stop = Output(Bool())
    val o_stage = Output(Vec(2, new StageBus(p.nHart, p.nAddrBit, p.nInstrBit)))
    val o_raise = Output(new RaiseBus(p.nAddrBit, p.nDataBit))

    val b_in = Flipped(new GenRVIO(p, new MemCtrlBus(p), new ResultBus(p.nDataBit)))

    // External units
    val b_hfu = if (p.useChamp) Some(Flipped(new HfuAckIO(p, p.nAddrBit, p.nDataBit))) else None

    val b_dmem = new Mb4sAckIO(p.pL0DBus)
    val b_csr = Flipped(new CsrWriteIO(p.nDataBit))
    val b_rd = Flipped(new GprWriteIO(p))
    val o_byp = Output(Vec(2, new BypassBus(p.nHart, p.nDataBit)))

    val o_instret = Output(Bool())

    val o_last = Output(new BranchBus(p.nAddrBit))
    val o_dfp = if (p.debug) Some(Output(new WbDfpBus(p))) else None
    val o_etd = if (p.debug) Some(Output(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit))) else None
  })

  val m_rdmem = if (p.useExtA) Some(Module(new Mb4sDataSReg(p.pL0DBus))) else None
  val r_wdmem = RegInit(true.B)

  val w_lock = Wire(Bool())

  val w_wait_dmem = Wire(Bool())
  val w_wait_hfu = Wire(Bool())
  val w_wait_sload = Wire(Bool())
  
  val w_is_sload = Wire(Bool())
  val w_sload_av = Wire(Bool())

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
    w_back_flush := false.B
  }

  // ******************************
  //          LOCK & MISS
  // ******************************
  if (p.useExtA) {
    w_wait_dmem := io.b_in.valid & ((io.b_in.ctrl.get.lsu.ld & ~m_rdmem.get.io.b_sout.valid) | (io.b_in.ctrl.get.lsu.st & r_wdmem & ~io.b_dmem.write.ready(0)))
  } else {
    w_wait_dmem := io.b_in.valid & ((io.b_in.ctrl.get.lsu.ld & ~io.b_dmem.read.valid) | (io.b_in.ctrl.get.lsu.st & ~io.b_dmem.write.ready(0)))
  }
  
  if (p.useChamp){
    w_wait_hfu := io.b_in.valid & (io.b_in.ctrl.get.ext.ext === EXT.HFU) & ~io.b_hfu.get.valid
  } else {
    w_wait_hfu := false.B
  }

  w_lock := ~w_back_flush & (w_wait_dmem | w_wait_hfu)

  // ******************************
  //            MEMORY
  // ******************************
  // ------------------------------
  //           SIZE LOAD
  // ------------------------------
  // Init register
  val m_sload = Module(new GenReg(p, new SloadCtrlBus(p), UInt(p.nDataBit.W), false, false, true))

  m_sload.io.i_flush := w_back_flush

  m_sload.io.b_in.valid := io.b_in.valid & w_is_sload & ~w_wait_dmem
  m_sload.io.b_in.ctrl.get.info := io.b_in.ctrl.get.info
  m_sload.io.b_in.ctrl.get.gpr := io.b_in.ctrl.get.gpr.addr
  m_sload.io.b_in.ctrl.get.lsu := io.b_in.ctrl.get.lsu
  if (p.useExtA) {
    m_sload.io.b_in.data.get := m_rdmem.get.io.b_sout.data.get
  } else {
    m_sload.io.b_in.data.get := io.b_dmem.read.data
  }  

  if (p.nDataBit == 64) {
    w_is_sload := io.b_in.ctrl.get.lsu.ld & (io.b_in.ctrl.get.lsu.size =/= LSUSIZE.D)
  } else {
    w_is_sload := io.b_in.ctrl.get.lsu.ld & (io.b_in.ctrl.get.lsu.size =/= LSUSIZE.W)
  }  
  w_sload_av := m_sload.io.b_out.valid
  w_wait_sload := m_sload.io.b_out.valid & ~w_is_sload

  m_sload.io.b_out.ready := true.B

  // ------------------------------
  //             PORT
  // ------------------------------
  if (p.useExtA) {
    m_rdmem.get.io.b_port <> io.b_dmem.read
    m_rdmem.get.io.b_sout.ready := io.b_in.valid & ((io.b_in.ctrl.get.lsu.ld & ~w_sload_av) | w_is_sload) & (~io.b_in.ctrl.get.lsu.a | ~r_wdmem | io.b_dmem.write.ready(0))
  } else {
    io.b_dmem.read.ready(0) := io.b_in.valid & ((io.b_in.ctrl.get.lsu.ld & ~w_sload_av) | w_is_sload)
  }  
  
  if (p.useExtA) {
    io.b_dmem.write.valid := io.b_in.valid & r_wdmem & io.b_in.ctrl.get.lsu.st & ~w_sload_av
  } else {
    io.b_dmem.write.valid := io.b_in.valid & io.b_in.ctrl.get.lsu.st & ~w_sload_av
  }
  if (p.useField) io.b_dmem.write.field.get := io.b_back.get.field
  io.b_dmem.write.data := io.b_in.data.get.s3

  if (p.useExtA) {
    when (io.b_in.valid & io.b_in.ctrl.get.lsu.a) {
      when (r_wdmem) {
        r_wdmem := ~io.b_dmem.write.ready(0) | m_rdmem.get.io.b_sout.valid
      }.otherwise {
        r_wdmem := m_rdmem.get.io.b_sout.valid
      }
    }
  }

  // ******************************
  //            RESULT
  // ******************************
  val w_res = Wire(UInt(p.nDataBit.W))

  // ------------------------------
  //            DEFAULT
  // ------------------------------
  w_res := io.b_in.data.get.res

  // ------------------------------
  //              HFU
  // ------------------------------
  if (p.useChamp) {
    io.b_hfu.get.ready := io.b_in.valid & (io.b_in.ctrl.get.ext.ext === EXT.HFU) & ~w_wait_sload
    when (io.b_in.ctrl.get.ext.ext === EXT.HFU) {
      w_res := io.b_hfu.get.data.get
    }
  }

  // ------------------------------
  //             LOAD
  // ------------------------------
  when (w_sload_av) {
    switch(m_sload.io.b_out.ctrl.get.lsu.size) {
      is (LSUSIZE.B) {
        when (m_sload.io.b_out.ctrl.get.lsu.sign === LSUSIGN.S) {
          w_res := Cat(Fill(p.nDataBit - 8, m_sload.io.b_out.data.get(7)), m_sload.io.b_out.data.get(7,0))
        }.otherwise {
          w_res := Cat(Fill(p.nDataBit - 8, 0.B), m_sload.io.b_out.data.get(7,0))
        }
      }
      is (LSUSIZE.H) {
        when (m_sload.io.b_out.ctrl.get.lsu.sign === LSUSIGN.S) {
          w_res := Cat(Fill(p.nDataBit - 16, m_sload.io.b_out.data.get(15)), m_sload.io.b_out.data.get(15,0))
        }.otherwise {
          w_res := Cat(Fill(p.nDataBit - 16, 0.B), m_sload.io.b_out.data.get(15,0))
        }
      }
      is (LSUSIZE.W) {
        if (p.nDataBit >= 64) {
          when (m_sload.io.b_out.ctrl.get.lsu.sign === LSUSIGN.S) {
            w_res := Cat(Fill(p.nDataBit - 32, m_sload.io.b_out.data.get(31)), m_sload.io.b_out.data.get(31,0))
          }.otherwise {
            w_res := Cat(Fill(p.nDataBit - 32, 0.B), m_sload.io.b_out.data.get(31,0))
          }
        }
      }
    }
  }.elsewhen(io.b_in.ctrl.get.lsu.ld) {
    if (p.useExtA) {
      w_res := m_rdmem.get.io.b_sout.data.get
    } else {
      w_res := io.b_dmem.read.data
    }    
  }

  // ******************************
  //              CSR
  // ******************************
  // ------------------------------
  //             WRITE
  // ------------------------------
  io.b_csr.valid := io.b_in.valid & io.b_in.ctrl.get.csr.write & ~w_wait_sload
  io.b_csr.addr := io.b_in.data.get.s3(11,0)
  io.b_csr.uop := io.b_in.ctrl.get.csr.uop
  io.b_csr.data := io.b_in.data.get.res
  io.b_csr.mask := io.b_in.data.get.s1

  // ------------------------------
  //           INFORMATION
  // ------------------------------
  io.o_instret := ((io.b_in.valid & ~w_is_sload) | w_sload_av) & ~w_lock

  // ******************************
  //             BYPASS
  // ******************************
  io.o_byp(0).valid := w_sload_av
  io.o_byp(0).hart := m_sload.io.b_out.ctrl.get.info.hart
  io.o_byp(0).ready := true.B
  io.o_byp(0).addr := m_sload.io.b_out.ctrl.get.gpr
  io.o_byp(0).data := w_res

  io.o_byp(1).valid := io.b_in.valid & io.b_in.ctrl.get.gpr.en
  io.o_byp(1).hart := io.b_in.ctrl.get.info.hart
  io.o_byp(1).ready := ~w_lock & ~io.b_in.ctrl.get.lsu.ld & ~w_sload_av & ~(io.b_in.ctrl.get.ext.ext === EXT.HFU)
  io.o_byp(1).addr := io.b_in.ctrl.get.gpr.addr
  io.o_byp(1).data := io.b_in.data.get.res


  // ******************************
  //           GPR WRITE
  // ******************************
  io.b_rd.valid := ((io.b_in.valid & io.b_in.ctrl.get.gpr.en & ~w_lock & ~w_is_sload) | w_sload_av) & ~w_back_flush
  io.b_rd.hart := io.b_in.ctrl.get.info.hart
  io.b_rd.addr := Mux(w_sload_av, m_sload.io.b_out.ctrl.get.gpr, io.b_in.ctrl.get.gpr.addr)
  io.b_rd.data := w_res

  // ******************************
  //             TRAP
  // ******************************
  val w_trap = Wire(Bool())
  val w_exc_unit = Wire(Bool())

  w_trap := io.b_in.ctrl.get.trap.valid | w_exc_unit
  
  // ------------------------------
  //         EXTERNAL UNIT
  // ------------------------------
  w_exc_unit := false.B

  // ------------------------------
  //             RAISE
  // ------------------------------
  io.o_raise.valid := io.b_in.valid & ~io.i_flush & ~w_lock & w_trap
  io.o_raise.pc := io.b_in.ctrl.get.info.pc
  io.o_raise.src := io.b_in.ctrl.get.trap.src
  io.o_raise.cause := io.b_in.ctrl.get.trap.cause
  io.o_raise.info := DontCare
  if (p.useChamp) {
    switch (io.b_in.ctrl.get.trap.cause) {
      is (CHAMPEXC.IINSTR.U)   {io.o_raise.info := io.b_in.ctrl.get.info.instr}
    }
  } else {
    switch (io.b_in.ctrl.get.trap.cause) {
      is (PRIVEXC.IINSTR.U)   {io.o_raise.info := io.b_in.ctrl.get.info.instr}
    }
  }

  // ******************************
  //             BACK
  // ******************************
  io.o_stage(0).valid := m_sload.io.b_out.valid
  io.o_stage(0).hart := m_sload.io.b_out.ctrl.get.info.hart
  io.o_stage(0).pc := m_sload.io.b_out.ctrl.get.info.pc
  io.o_stage(0).instr := m_sload.io.b_out.ctrl.get.info.instr
  io.o_stage(0).exc_gen := false.B
  io.o_stage(0).end := false.B

  io.o_stage(1).valid := io.b_in.valid
  io.o_stage(1).hart := io.b_in.ctrl.get.info.hart
  io.o_stage(1).pc := io.b_in.ctrl.get.info.pc
  io.o_stage(1).instr := io.b_in.ctrl.get.info.instr
  io.o_stage(1).exc_gen := io.b_in.ctrl.get.trap.gen
  io.o_stage(1).end := io.b_in.valid & io.b_in.ctrl.get.info.end

  io.o_stop := io.b_in.valid & ~io.i_flush & ~w_lock & w_exc_unit

  // ******************************
  //            OUTPUTS
  // ******************************
  io.b_in.ready := ~w_lock & (~w_sload_av | w_is_sload)

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    io.b_back.get.free := ~m_sload.io.o_val.valid
  } 

  // ******************************
  //             LAST
  // ******************************
  io.o_last.valid := (io.b_in.valid & ~w_lock & ~w_is_sload) | w_sload_av
  io.o_last.addr := Mux(w_sload_av, m_sload.io.b_out.ctrl.get.info.pc, io.b_in.ctrl.get.info.pc)

  // ******************************
  //            DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(io.b_in)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    if (p.useExtA) io.o_dfp.get.rback.get := DontCare

    io.o_dfp.get.pc := m_sload.io.o_reg.ctrl.get.info.pc
    io.o_dfp.get.instr := m_sload.io.o_reg.ctrl.get.info.instr
    io.o_dfp.get.res := m_sload.io.o_reg.data.get

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    m_sload.io.b_in.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get

    when (m_sload.io.b_out.valid) {
      io.o_etd.get := m_sload.io.b_out.ctrl.get.etd.get
    }.otherwise {
      io.o_etd.get := io.b_in.ctrl.get.etd.get
      io.o_etd.get.done := io.b_in.valid & ~w_lock
    }
  }
}

object WbStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new WbStage(BackConfigBase), args)
}
