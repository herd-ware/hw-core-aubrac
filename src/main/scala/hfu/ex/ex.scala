/*
 * File: ex.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:33:08 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.hfu

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.isa.champ._
import herd.core.aubrac.common._


class ExStage(p: HfuParams) extends Module {
  val io = IO(new Bundle {
    val i_flush = Input(Bool())

    val b_in = Flipped(new GenRVIO(p, new ExStageBus(p), new DataBus(p)))

    val i_exe = Input(new DomeCfgBus(p.pDomeCfg))
    val o_byp = Output(new BypassBus(p))

    val b_out = new GenRVIO(p, new CtrlStageBus(p), new ResultBus(p))
  })

  val w_lock = Wire(Bool())

  // ******************************
  //        EXECUTION UNITS
  // ******************************
  val w_hfres_lock = Wire(Bool())
  val w_end = Wire(Bool())
  val w_res = Wire(UInt(p.nDataBit.W))
  val w_hfres = Wire(new DomeCfgBus(p.pDomeCfg))
  val w_index = Wire(UInt(7.W))
  val w_check = Wire(Bool())

  w_hfres_lock := io.b_in.ctrl.get.info.lock
  w_end := false.B
  w_res := 0.U
  w_hfres := io.b_in.data.get.s1
  w_index := io.b_in.data.get.s3
  w_check := true.B

  // ------------------------------
  //             MOVE
  // ------------------------------
  when (io.b_in.ctrl.get.op.mv) {
    w_end := true.B
    w_res := 0.U
    w_hfres := io.b_in.data.get.s1
  }
  
  // ------------------------------
  //             ALU
  // ------------------------------
  val m_alu = Module(new Alu(p))

  m_alu.io.b_req.valid := io.b_in.valid & io.b_in.ctrl.get.op.alu_use
  m_alu.io.b_req.ctrl.get.uop := io.b_in.ctrl.get.op.alu_uop
  m_alu.io.b_req.data.get.s1 := io.b_in.data.get.s1
  m_alu.io.b_req.data.get.s2 := io.b_in.data.get.s2
  m_alu.io.b_req.data.get.s3 := io.b_in.data.get.s3

  m_alu.io.b_ack.ready := true.B

  when (io.b_in.ctrl.get.op.alu_use) {
    w_end := m_alu.io.b_ack.valid
    w_res := m_alu.io.b_ack.data.get.res
    when (~w_hfres_lock) {
      w_hfres := m_alu.io.b_ack.data.get.hfres 
    }
  }

  // ------------------------------
  //             BASE
  // ------------------------------
  val m_base = Module(new Base(p))

  m_base.io.i_use_old := io.b_in.data.get.s1.status.update
  m_base.io.i_exe := io.i_exe
  m_base.io.i_old := io.b_in.data.get.s1

  // ------------------------------
  //            CHECK
  // ------------------------------
  val m_check = Module(new Check(p))

  m_check.io.b_req.valid := io.b_in.valid & io.b_in.ctrl.get.op.check_use
  m_check.io.b_req.ctrl.get.uop := io.b_in.ctrl.get.op.check_uop
  m_check.io.b_req.ctrl.get.index := w_index
  m_check.io.b_req.data.get.base := m_base.io.o_base
  m_check.io.b_req.data.get.hfs := io.b_in.data.get.s1

  m_check.io.b_ack.ready := true.B

  when (io.b_in.ctrl.get.op.check_use) {
    w_end := m_check.io.b_ack.valid
    w_res := m_check.io.b_ack.data.get.res
    w_hfres := m_check.io.b_ack.data.get.hfres 
    w_check := m_check.io.b_ack.ctrl.get   
  }

  // ******************************
  //            OUTPUTS
  // ******************************
  // ------------------------------
  //           REGISTERS
  // ------------------------------
  val m_out = Module( new GenReg(p, new CtrlStageBus(p), new ResultBus(p), false, false, true))

  m_out.io.i_flush := io.i_flush

  w_lock := ~m_out.io.b_in.ready

  m_out.io.b_in.valid := io.b_in.valid & w_end & ~io.i_flush
  m_out.io.b_in.ctrl.get.info := io.b_in.ctrl.get.info
  m_out.io.b_in.ctrl.get.info.check := w_check
  m_out.io.b_in.ctrl.get.trap := io.b_in.ctrl.get.trap
  m_out.io.b_in.ctrl.get.lsu := io.b_in.ctrl.get.lsu
  m_out.io.b_in.ctrl.get.rf := io.b_in.ctrl.get.rf

  m_out.io.b_in.data.get.s2 := io.b_in.data.get.s2
  m_out.io.b_in.data.get.s3 := io.b_in.data.get.s3
  m_out.io.b_in.data.get.hfres := w_hfres
  m_out.io.b_in.data.get.res := w_res

  io.b_out <> m_out.io.b_out

  // ------------------------------
  //             BYPASS
  // ------------------------------
  io.o_byp.valid := io.b_in.valid & io.b_in.ctrl.get.rf.en
  io.o_byp.addr := io.b_in.ctrl.get.rf.addr
  io.o_byp.ready := ~io.b_in.ctrl.get.lsu.ld & w_end
  io.o_byp.data := w_hfres
  io.o_byp.full := io.b_in.ctrl.get.info.full | io.b_in.data.get.s1.status.valid | io.b_in.data.get.s1.status.update
  io.o_byp.index := w_index

  // ------------------------------
  //             READY
  // ------------------------------
  io.b_in.ready := io.i_flush | (~w_lock & w_end)
  
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
    m_out.io.b_in.ctrl.get.etd.get := io.b_in.ctrl.get.etd.get
  }
}

object ExStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new ExStage(HfuConfigBase), args)
}
