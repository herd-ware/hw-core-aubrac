/*
 * File: csr.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:30:44 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.back.csr

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.gen._
import herd.common.dome._
import herd.common.isa.count.{CsrBus => StatBus}
import herd.core.aubrac.common._
import herd.core.aubrac.dmu.{DmuReqCtrlBus, DmuReqDataBus, DmuCsrIO}
import herd.io.core.clint.{ClintIO}


class Csr(p: CsrParams) extends Module {
  val io = IO(new Bundle {
    val b_dome = if (p.useDome) Some(Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit))) else None
    val b_hart = if (p.useDome) Some(Vec(p.nHart, new RsrcIO(p.nHart, p.nDome, p.nHart))) else None

    val b_read = Vec(p.nHart, new CsrReadIO(p.nDataBit))
    val b_write = Vec(p.nHart, new CsrWriteIO(p.nDataBit))

    val i_trap = Input(Vec(p.nHart, new TrapBus(p.nAddrBit, p.nDataBit)))
    val o_ie = Output(Vec(p.nHart, UInt(p.nDataBit.W)))
    val b_trap = if (p.useChamp) Some(Vec(p.nHart, new GenRVIO(p, new DmuReqCtrlBus(p.debug, p.nAddrBit), new DmuReqDataBus(p.nDataBit)))) else None
    val o_br_trap = Output(Vec(p.nHart, new BranchBus(p.nAddrBit)))

    val i_stat  = Input(Vec(p.nHart, new StatBus()))
    val o_decoder = Output(Vec(p.nHart, new CsrDecoderBus()))
    val b_mem = Vec(p.nHart, new CsrMemIO())
    val b_dmu = if (p.useChamp) Some(Vec(p.nHart, Flipped(new DmuCsrIO(p.nAddrBit, p.nChampTrapLvl)))) else None
    val b_clint = Vec(p.nHart, Flipped(new ClintIO(p.nDataBit)))

    val o_dbg = if (p.debug) Some(Output(Vec(p.nHart, new CsrBus(p.nDataBit, p.useChamp)))) else None
  })

  if (p.useChamp) {
    val m_csr = Module(new Champ(p))

    m_csr.io.b_dome <> io.b_dome.get
    m_csr.io.b_hart <> io.b_hart.get

    m_csr.io.b_read <> io.b_read
    m_csr.io.b_write <> io.b_write

    m_csr.io.i_trap := io.i_trap
    io.o_ie := m_csr.io.o_ie
    io.o_br_trap := m_csr.io.o_br_trap
    io.b_trap.get <> m_csr.io.b_trap

    m_csr.io.i_stat := io.i_stat
    io.o_decoder := m_csr.io.o_decoder
    m_csr.io.b_mem <> io.b_mem
    m_csr.io.b_dmu <> io.b_dmu.get
    m_csr.io.b_clint <> io.b_clint

    if (p.debug) io.o_dbg.get := m_csr.io.o_dbg.get 
  } else {
    val m_csr = Module(new Priv(p))

    m_csr.io.b_read <> io.b_read
    m_csr.io.b_write <> io.b_write

    m_csr.io.i_trap := io.i_trap
    io.o_ie := m_csr.io.o_ie
    io.o_br_trap := m_csr.io.o_br_trap

    m_csr.io.i_stat := io.i_stat
    io.o_decoder := m_csr.io.o_decoder
    m_csr.io.b_mem <> io.b_mem
    m_csr.io.b_clint <> io.b_clint
    
    if (p.debug) io.o_dbg.get := m_csr.io.o_dbg.get 
  }
}

object Csr extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Csr(CsrConfigBase), args)
}