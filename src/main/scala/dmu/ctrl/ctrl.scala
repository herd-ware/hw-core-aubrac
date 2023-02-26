/*
 * File: ctrl.scala                                                            *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:00:16 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.dmu

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import scala.math._

import herd.common.gen._
import herd.common.isa.ceps._
import herd.common.tools.Counter
import herd.common.mem.mb4s._
import herd.common.mem.mb4s.{OP => MEMOP}
import herd.core.aubrac.common._


object CtrlStageFSM extends ChiselEnum {
  val s0NEW, s1MEM = Value
}

class CtrlStage(p: DmuParams) extends Module {
  import herd.core.aubrac.dmu.CtrlStageFSM._

  // ******************************
  //              I/O
  // ******************************
  val io = IO(new Bundle {
    val i_flush = Input(Bool())
    
    val b_in = Flipped(new GenRVIO(p, new CtrlStageBus(p), new ResultBus(p)))

    val b_dmem = new Mb4sIO(p.pL0DBus)
    val i_state = Input(new RegFileStateBus(p.nDomeCfg, p.pDomeCfg))
    val o_byp = Output(new BypassBus(p))

    val b_rf = Flipped(new RegFileWriteIO(p))
    val b_rmr = new RmrReqIO(p)
    val b_ack = new DmuAckIO(p, p.nAddrBit, p.nDataBit)
  })

  val r_fsm = RegInit(s0NEW)
  val r_mem_req = RegInit(false.B)

  val w_trap = Wire(new TrapBus(p.nAddrBit, p.nDataBit))

  val w_valid = Wire(Bool())
  val w_is_mem = Wire(Bool())
  val w_is_switch = Wire(Bool())
  val w_check = Wire(Bool())

  val w_wait_fsm = Wire(Bool())
  val w_wait_ack = Wire(Bool())
  val w_wait_mem = Wire(Bool())
  val w_wait_mreq = Wire(Bool())
  val w_wait_mack = Wire(Bool())
  val w_wait_rmr = Wire(Bool())

  // ******************************
  //         CONTROL & DATA
  // ******************************
  val w_full = Wire(Bool())
  val w_field = Wire(UInt(7.W))
  val w_dcres = Wire(new DomeCfgBus(p.pDomeCfg))
  val w_dcres_lock = Wire(Bool())
  val w_res = Wire(UInt(p.nDataBit.W))

  w_full := io.b_in.ctrl.get.info.full
  w_field := io.b_in.data.get.s3
  w_dcres := io.b_in.data.get.dcres
  w_dcres_lock := io.b_in.ctrl.get.info.lock
  w_res := Mux(w_is_switch, ~io.b_in.data.get.dcres.status.valid, io.b_in.data.get.res)

  // ******************************
  //            COUNTER
  // ******************************
  // Count memory requests
  val m_creq = Module(new Counter(3))
  // Count memory acknowledgements
  val m_cack = Module(new Counter(3))

  m_creq.io.i_limit := 6.U
  m_creq.io.i_init := true.B
  m_creq.io.i_en := false.B

  m_cack.io.i_limit := 6.U
  m_cack.io.i_init := true.B
  m_cack.io.i_en := false.B

  switch (r_fsm) {
    is (s0NEW) {
      when (w_is_mem & ~w_wait_ack & ~w_wait_rmr & ~w_wait_mreq & ~w_trap.valid) {
        m_creq.io.i_init := false.B
        m_creq.io.i_en := true.B
      }
    }

    is (s1MEM) {
      m_creq.io.i_init := false.B
      m_creq.io.i_en := ~w_wait_mreq & ~r_mem_req
      m_cack.io.i_init := false.B
      m_cack.io.i_en := ~w_wait_mack
    }
  }

  // ******************************
  //             FSM
  // ******************************
  w_valid := io.b_in.valid & ~io.i_flush
  w_is_mem := w_valid & ((io.b_in.ctrl.get.lsu.ld & ~w_dcres_lock) | io.b_in.ctrl.get.lsu.st)
  w_is_switch := w_valid & io.b_in.ctrl.get.rf.sw
  w_check := io.b_in.ctrl.get.info.check

  r_fsm := s0NEW
  w_wait_fsm := false.B

  switch (r_fsm) {
    is (s0NEW) {
      when (w_is_mem & ~w_wait_ack & ~w_wait_rmr & ~w_wait_mreq & ~w_trap.valid) {
        r_fsm := s1MEM
        w_wait_fsm := true.B
      }
    }

    is (s1MEM) {
      when (m_cack.io.o_flag & ~w_wait_mack) {
        r_fsm := s0NEW
        w_wait_fsm := false.B        
      }.otherwise {
        r_fsm := s1MEM       
        w_wait_fsm := true.B 
      }
    }
  }

  // ******************************
  //            MEMORY
  // ******************************
  // ------------------------------
  //            STATUS
  // ------------------------------
  when (r_mem_req) {
    r_mem_req := ~(m_cack.io.o_flag & ~w_wait_mack)
  }.otherwise {
    r_mem_req := (r_fsm === s1MEM) & m_creq.io.o_flag & ~w_wait_mreq
  }

  w_wait_mreq := w_valid & ((io.b_in.ctrl.get.lsu.ld & ~w_dcres_lock) | io.b_in.ctrl.get.lsu.st) & ~io.b_dmem.req.ready(0) & ~r_mem_req
  w_wait_mack := w_valid & ((io.b_in.ctrl.get.lsu.ld & ~w_dcres_lock & ~io.b_dmem.read.valid) | (io.b_in.ctrl.get.lsu.st & ~io.b_dmem.write.ready(0)))
  w_wait_mem := w_valid & (io.b_in.ctrl.get.lsu.ld & ~w_dcres_lock & ~io.b_dmem.read.valid) & (~m_cack.io.o_flag | ~w_wait_mack)

//  w_wait_mreq := w_valid & (io.b_in.ctrl.get.lsu.ld | io.b_in.ctrl.get.lsu.st) & ~r_mem_req
//  w_wait_mack := w_valid & (~m_creq.io.o_flag | ~((io.b_in.ctrl.get.lsu.ld & io.b_dmem.read.valid) | (io.b_in.ctrl.get.lsu.st & io.b_dmem.write.ready(0))))

  // ------------------------------
  //             REQ
  // ------------------------------
  io.b_dmem.req.valid := ~r_mem_req & w_is_mem & ~w_wait_ack & ~w_wait_rmr & ~w_trap.valid
  io.b_dmem.req.dome.get := 0.U  
  io.b_dmem.req.ctrl.hart := 0.U
  io.b_dmem.req.ctrl.op := Mux(io.b_in.ctrl.get.lsu.st, MEMOP.W, MEMOP.R)
  io.b_dmem.req.ctrl.size := SIZE.B4.U
  io.b_dmem.req.ctrl.addr := Cat(io.b_in.data.get.res(31, 5), (m_creq.io.o_val << 2.U)(4, 0))

  // ------------------------------
  //             ACK
  // ------------------------------
  io.b_dmem.read.ready(0) := io.b_in.ctrl.get.lsu.ld & (r_fsm === s1MEM)

  io.b_dmem.write.valid := io.b_in.ctrl.get.lsu.st & (r_fsm === s1MEM)
  io.b_dmem.write.dome.get := 0.U
  io.b_dmem.write.data := DontCare

  // ------------------------------
  //            FORMAT
  // ------------------------------
  when (r_fsm === s1MEM) {
    w_full := false.B
  }

  switch (m_cack.io.o_val) {
    is (0.U) {
      io.b_dmem.write.data := io.b_in.data.get.dcres.status.toUInt
      when (io.b_in.ctrl.get.lsu.ld) {
        w_field := CONF.STATUS.U
        w_dcres.status.fromUInt(io.b_dmem.read.data)
        w_dcres.status.valid := false.B
        w_dcres.status.lock := false.B
        w_dcres.status.update := false.B
      }
    }
    is (1.U) {
      io.b_dmem.write.data := io.b_in.data.get.dcres.id.toUInt
      when (io.b_in.ctrl.get.lsu.ld) {
        w_field := CONF.ID.U
        w_dcres.id.fromUInt(io.b_dmem.read.data)
      }      
    }
    is (2.U) {
      io.b_dmem.write.data := io.b_in.data.get.dcres.entry
      when (io.b_in.ctrl.get.lsu.ld) {
        w_field := CONF.ENTRY.U
        w_dcres.entry := io.b_dmem.read.data
      }
    }
    is (3.U) {
      io.b_dmem.write.data := io.b_in.data.get.dcres.table
      when (io.b_in.ctrl.get.lsu.ld) {
        w_field := CONF.TABLE.U
        w_dcres.table := io.b_dmem.read.data
      }
    }
    is (4.U) {
      io.b_dmem.write.data := io.b_in.data.get.dcres.cap.toUInt
      when (io.b_in.ctrl.get.lsu.ld) {
        w_field := CONF.CAP.U
        w_dcres.cap.fromUInt(io.b_dmem.read.data)
      }
    }
    is (5.U) {
      io.b_dmem.write.data := io.b_in.data.get.dcres.inst.toUInt
      when (io.b_in.ctrl.get.lsu.ld) {
        w_field := CONF.INST.U
        w_dcres.inst.fromUInt(io.b_dmem.read.data)
      }
    }
  } 

  // ******************************
  //             TRAP
  // ******************************
  w_trap.valid := false.B
  w_trap.gen := DontCare
  w_trap.pc := DontCare
  w_trap.src := TRAPSRC.EXC
  w_trap.cause := 0.U
  w_trap.info := 0.U

  when (w_valid & (r_fsm === s0NEW)) {
    when ((io.b_in.ctrl.get.lsu.ld | io.b_in.ctrl.get.lsu.st) & (io.b_in.data.get.res(4, 0) =/= 0.U(5.W))) {
      w_trap.valid := true.B
      w_trap.info := io.b_in.data.get.res
      when (io.b_in.ctrl.get.lsu.ld) {
        w_trap.cause := EXC.DLADDRMIS.U
      }.otherwise {
        w_trap.cause := EXC.DSADDRMIS.U
      }
    }
  }

  // ******************************
  //            REGFILE
  // ******************************   
  io.b_rf.sw := io.b_in.ctrl.get.rf.sw
  io.b_rf.addr := io.b_in.ctrl.get.rf.addr
  io.b_rf.full := w_full
  io.b_rf.field := w_field
  io.b_rf.data := w_dcres

  io.b_rf.valid := false.B
  switch(r_fsm) {
    is (s0NEW) {
      io.b_rf.valid := w_valid & io.b_in.ctrl.get.rf.en & w_check & ~w_is_mem & ~w_wait_ack & ~w_wait_rmr & ~w_trap.valid
    }

    is (s1MEM) {
      io.b_rf.valid := io.b_in.ctrl.get.lsu.ld & ~w_wait_mack
    }
  }

  // ******************************
  //              RMR
  // ****************************** 
  w_wait_rmr := (r_fsm === s0NEW) & w_valid & ~io.b_rmr.ready

  io.b_rmr.valid := (r_fsm === s0NEW) & w_valid & w_check & ~w_wait_ack & (w_is_switch | (io.b_in.ctrl.get.rf.en & (io.b_rf.addr === io.i_state.fr.addr) & ~w_dcres_lock))
  io.b_rmr.op := Mux(w_is_switch, RMRUOP.SWITCH, RMRUOP.NOFR)
  io.b_rmr.dcres := w_dcres

  when (io.b_in.ctrl.get.info.jump) {
    io.b_rmr.target := io.b_in.data.get.s2
  }.otherwise {
    io.b_rmr.target := w_dcres.entry
  }

  // ******************************
  //              ACK
  // ******************************
  w_wait_ack := (r_fsm === s0NEW) & w_valid & ~io.b_ack.ready & ~io.b_in.ctrl.get.info.trap

  io.b_ack.valid := w_valid & (r_fsm === s0NEW) & ~io.b_in.ctrl.get.info.trap
  io.b_ack.ctrl.get.wb := io.b_in.ctrl.get.info.wb
  io.b_ack.ctrl.get.trap := w_trap
  io.b_ack.data.get := w_res

  // ******************************
  //            OUTPUTS
  // ******************************
  // ------------------------------
  //             LOCK
  // ------------------------------
  io.b_in.ready := io.i_flush | (~w_wait_fsm & ~w_wait_ack & ~w_wait_mem & ~w_wait_rmr)

  // ------------------------------
  //             BYPASS
  // ------------------------------
  io.o_byp.valid := io.b_in.valid & io.b_in.ctrl.get.rf.en
  io.o_byp.ready := ~io.b_in.ctrl.get.lsu.ld
  io.o_byp.addr := io.b_in.ctrl.get.rf.addr
  io.o_byp.full := w_full
  io.o_byp.field := w_field
  io.o_byp.data := w_dcres  
  
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

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    dontTouch(io.b_in.ctrl.get.etd.get)
  }
}

object CtrlStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new CtrlStage(DmuConfigBase), args)
}
