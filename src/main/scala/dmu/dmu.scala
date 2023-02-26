/*
 * File: dmu.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:03:33 pm                                       *
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

import herd.common.gen._
import herd.common.dome._
import herd.common.mem.mb4s._
import herd.common.isa.ceps._
import herd.core.aubrac.common._


class Dmu (p: DmuParams) extends Module {
  val io = IO(new Bundle {
    val b_port = new DmuIO(p, p.nAddrBit, p.nDataBit, p.nCepsTrapLvl)

    val b_dome = Flipped(Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit)))
    val b_hart = Flipped(new RsrcIO(p.nHart, p.nDome, 1))
    val b_pexe = Flipped(new NRsrcIO(p.nHart, p.nDome, p.nPart))
    val b_pall = Flipped(new NRsrcIO(p.nHart, p.nDome, p.nPart))

    val b_dmem = new Mb4sIO(p.pL0DBus)

    val o_state = Output(new RegFileStateBus(p.nDomeCfg, p.pDomeCfg))

    val o_dbg = if (p.debug) Some(Output(Vec(p.nDomeCfg, Vec(6, UInt(p.nDataBit.W))))) else None
  })

  val m_rr = Module(new RrStage(p))
  val m_ex = Module(new ExStage(p))
  val m_ctrl = Module(new CtrlStage(p))

  val m_byp = Module(new Bypass(p))
  val m_rf = Module(new RegFile(p))
  val m_rmr = Module(new Rmr(p))

  // ******************************
  //              RR
  // ******************************
  m_rr.io.i_flush := m_rmr.io.o_flush | io.b_port.ctrl.dmu_flush
  m_rr.io.b_req <> io.b_port.req
  m_rr.io.i_state := m_rf.io.o_state
  m_rr.io.i_etl := io.b_port.csr.etl

  // ******************************
  //              OP
  // ******************************
  m_ex.io.i_flush := m_rmr.io.o_flush | io.b_port.ctrl.dmu_flush
  m_ex.io.b_in <> m_rr.io.b_out
  m_ex.io.i_exe := m_rf.io.o_state.cur.dc

  // ******************************
  //             CTRL
  // ******************************
  m_ctrl.io.i_flush := m_rmr.io.o_flush | io.b_port.ctrl.dmu_flush
  m_ctrl.io.b_in <> m_ex.io.b_out
  m_ctrl.io.b_dmem <> io.b_dmem
  m_ctrl.io.i_state := m_rf.io.o_state
  m_ctrl.io.b_ack <> io.b_port.ack

  // ******************************
  //          RF & BYPASS
  // ******************************
  m_rf.io.b_read(0) <> m_rr.io.b_dcs(0)
  m_rf.io.b_read(1) <> m_rr.io.b_dcs(1)
  m_rf.io.b_write <> m_ctrl.io.b_rf
  m_rf.io.i_atl := io.b_port.csr.atl

  m_byp.io.b_dcs(0) <> m_rr.io.b_dcs(0)
  m_byp.io.i_dcs(0) := m_rf.io.b_read(0).data
  m_byp.io.b_dcs(1) <> m_rr.io.b_dcs(1)
  m_byp.io.i_dcs(1) := m_rf.io.b_read(1).data
  m_byp.io.i_cdc := m_rf.io.o_state.cur.addr
  m_byp.io.i_byp(0) := m_ctrl.io.o_byp
  m_byp.io.i_byp(1) := m_ex.io.o_byp

  // ******************************
  //              RMR
  // ******************************
  m_rmr.io.b_req <> m_ctrl.io.b_rmr
  m_rmr.io.i_state := m_rf.io.o_state

  io.b_dome <> m_rmr.io.b_dome
  io.b_hart <> m_rmr.io.b_hart
  io.b_pexe <> m_rmr.io.b_pexe
  io.b_pall <> m_rmr.io.b_pall

  // ******************************
  //              I/O
  // ******************************
  // CSRs
  io.b_port.csr.cdc := m_rf.io.o_state.cur.addr
  io.b_port.csr.pdc := m_rf.io.o_state.prev.addr

  // Others
  io.o_state := m_rf.io.o_state
  io.b_port.ctrl.dmu_free := false.B
  io.b_port.ctrl.pipe_flush := m_rmr.io.o_flush
  io.b_port.ctrl.pipe_br := m_rmr.io.o_br_dome

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    io.o_dbg.get := m_rf.io.o_dbg.get
  }
}

object Dmu extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Dmu(DmuConfigBase), args)
}
