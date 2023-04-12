/*
 * File: pipeline.scala                                                        *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-12 10:02:07 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac

import chisel3._
import chisel3.util._

import herd.common.field._
import herd.common.core.{HpcPipelineBus}
import herd.common.mem.mb4s._
import herd.common.mem.cbo._
import herd.core.aubrac.nlp._
import herd.core.aubrac.front._
import herd.core.aubrac.back._
import herd.core.aubrac.back.csr.{CsrMemIO}
import herd.core.aubrac.hfu._
import herd.core.aubrac.common._
import herd.io.core.clint.{ClintIO}


class Pipeline (p: PipelineParams) extends Module {
  val io = IO(new Bundle {
    val b_field = if (p.useField) Some(Vec(p.nField, new FieldIO(p.nAddrBit, p.nDataBit))) else None
    val b_hart = if (p.useField) Some(new RsrcIO(p.nHart, p.nField, 1)) else None

    val b_imem = new Mb4sIO(p.pL0IBus)

    val b_dmem = new Mb4sIO(p.pL0DBus)
    val b_cbo = if (p.useCbo) Some(new CboIO(p.nHart, p.useField, p.nField, p.nAddrBit)) else None
    val b_hfu = if (p.useChamp) Some(Flipped(new HfuIO(p, p.nAddrBit, p.nDataBit, p.nChampTrapLvl))) else None
    val b_clint = Flipped(new ClintIO(p.nDataBit))

    val o_hpc = Output(new HpcPipelineBus())
    val i_hpm = Input(Vec(32, UInt(64.W)))

    val o_dbg = if (p.debug) Some(Output(new PipelineDbgBus(p))) else None
    val o_etd = if (p.debug) Some(Output(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit))) else None
  })

  val m_nlp = if (p.useNlp) Some(Module(new Nlp(p))) else None
  val m_front = Module(new Front(p))
  val m_back = Module(new Back(p))

  // ******************************
  //          FRONT & NLP
  // ******************************
  if (p.useNlp) {    
    if (p.useField) m_nlp.get.io.b_hart.get <> io.b_hart.get
    m_nlp.get.io.i_mispred := m_back.io.o_br_new.valid
    m_nlp.get.io.b_read <> m_front.io.b_nlp.get
    m_nlp.get.io.i_info := m_back.io.o_br_info
  }
  
  if (p.useField) {
    m_front.io.b_hart.get <> io.b_hart.get
    m_front.io.i_flush := m_back.io.o_flush | io.b_hfu.get.ctrl.pipe_flush
    m_front.io.i_br_field.get := io.b_hfu.get.ctrl.pipe_br
  } else {
    m_front.io.i_flush := m_back.io.o_flush
  }
  m_front.io.i_br_new := m_back.io.o_br_new
  m_front.io.b_imem <> io.b_imem
  m_front.io.b_out(0).ready := m_back.io.b_in.ready

  // ******************************
  //             BACK
  // ******************************  
  if (p.useField) {
    m_back.io.b_field.get <> io.b_field.get
    m_back.io.b_hart.get <> io.b_hart.get
    m_back.io.i_flush := io.b_hfu.get.ctrl.pipe_flush
  } else {
    m_back.io.i_flush := false.B
  }
  m_back.io.i_br_next := m_front.io.o_br_next
  
  m_back.io.b_in.valid := m_front.io.b_out(0).valid
  m_back.io.b_in.ctrl.get.hart := 0.U
  m_back.io.b_in.ctrl.get.pc := m_front.io.b_out(0).ctrl.get.pc
  m_back.io.b_in.ctrl.get.instr := m_front.io.b_out(0).ctrl.get.instr

  m_back.io.b_dmem <> io.b_dmem  
  if (p.useCbo) m_back.io.b_cbo.get <> io.b_cbo.get
  if (p.useChamp) m_back.io.b_hfu.get <> io.b_hfu.get

  io.o_hpc := m_back.io.o_hpc
  m_back.io.i_hpm := io.i_hpm
  m_back.io.b_clint <> io.b_clint

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) {
    if (p.useNlp) {
      io.b_hart.get.free := m_front.io.b_hart.get.free & m_back.io.b_hart.get.free & m_nlp.get.io.b_hart.get.free
    } else {
      io.b_hart.get.free := m_front.io.b_hart.get.free & m_back.io.b_hart.get.free
    }     
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    io.o_dbg.get.last := m_back.io.o_dbg.get.last
    io.o_dbg.get.x := m_back.io.o_dbg.get.x
    io.o_dbg.get.csr := m_back.io.o_dbg.get.csr

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    m_back.io.b_in.ctrl.get.etd.get := m_front.io.b_out(0).ctrl.get.etd.get

    io.o_etd.get := m_back.io.o_etd.get
  } 
}

object Pipeline extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Pipeline(PipelineConfigBase), args)
}
