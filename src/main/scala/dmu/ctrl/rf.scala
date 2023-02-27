/*
 * File: rf.scala                                                              *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:57:50 pm                                       *
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

import herd.common.isa.champ._


class RegFile (p: DmuParams) extends Module {
  require((p.nDomeCfg <= CST.MAXCFG), "A maximum of " + CST.MAXCFG + " dome configurations is possible.")
  
  val io = IO(new Bundle {
    val b_read = Vec(2, new RegFileReadIO(p))
    val b_write = new RegFileWriteIO(p)

    val o_state = Output(new RegFileStateBus(p.nDomeCfg, p.pDomeCfg))
    val i_atl = Input(Vec(2, Bool()))

    val o_dbg = if (p.debug) Some(Output(Vec(p.nDomeCfg, Vec(6, UInt(p.nDataBit.W))))) else None
  })

  // ******************************
  //             INIT
  // ******************************
  val init_reg = Wire(new RegFileBus(p.nDomeCfg, p.pDomeCfg))

  init_reg.dc(0).status.valid     := true.B
  init_reg.dc(0).status.lock      := true.B
  init_reg.dc(0).status.update    := false.B
  init_reg.dc(0).status.size      := 0.B
  init_reg.dc(0).status.atl(0)    := false.B
  init_reg.dc(0).status.atl(1)    := false.B

  if (p.useChampExtR) {
    init_reg.dc(0).status.order   := (p.nDataBit / log2Ceil(p.nDataBit)).U
  } else {
    init_reg.dc(0).status.order   := 0.U
  }  

  init_reg.dc(0).id.fromUInt(0.U)

  init_reg.dc(0).entry            := BigInt(p.pcBoot, 16).U

  init_reg.dc(0).table            := Cat(Fill(p.nDataBit, 1.B))

  init_reg.dc(0).cap.secmie       := true.B
  init_reg.dc(0).cap.seccst       := true.B
  for (tl <- 0 until CST.MAXTL) {
    init_reg.dc(0).cap.featl(tl)  := (tl < p.pDomeCfg.nTrapLvl).B    
  }
  init_reg.dc(0).cap.feafr        := p.useChampExtFr.B
  init_reg.dc(0).cap.feacbo       := true.B

  init_reg.dc(0).inst.weight      := WEIGHT.FULL.U
  init_reg.dc(0).inst.muldiv      := true.B
  init_reg.dc(0).inst.fr          := true.B

  for (dc <- 1 until p.nDomeCfg) {
    init_reg.dc(dc).status        := DontCare
    init_reg.dc(dc).status.valid  := false.B
    init_reg.dc(dc).status.update := false.B
    init_reg.dc(dc).status.atl(0) := false.B
    init_reg.dc(dc).status.atl(1) := false.B
    init_reg.dc(dc).id            := DontCare
    init_reg.dc(dc).entry         := DontCare
    init_reg.dc(dc).table         := DontCare
    init_reg.dc(dc).cap           := DontCare
    init_reg.dc(dc).inst          := DontCare
  }

  init_reg.cdc := 0.U
  init_reg.pdc := 0.U
  init_reg.frv := false.B
  init_reg.frdc := 0.U

  val r_reg = RegInit(init_reg)

  // ******************************
  //              READ
  // ******************************
  for (r <- 0 until 2) {
    io.b_read(r).ready := true.B
    io.b_read(r).data := r_reg.dc(io.b_read(r).addr)
  }

  // ******************************
  //             WRITE
  // ******************************
  io.b_write.ready := true.B
  
  when (io.b_write.valid) {
    when (io.b_write.sw =/= SWUOP.X) {
      r_reg.cdc := io.b_write.addr
      r_reg.pdc := r_reg.cdc

      r_reg.dc(r_reg.cdc).status.valid := (io.b_write.sw === SWUOP.V) | (io.b_write.sw === SWUOP.L)
      r_reg.dc(r_reg.cdc).status.lock := (io.b_write.sw === SWUOP.L)
      r_reg.dc(r_reg.cdc).status.update := false.B
      if (p.nChampTrapLvl > 0) r_reg.dc(r_reg.cdc).status.atl(0) := io.i_atl(0)
      if (p.nChampTrapLvl > 1) r_reg.dc(r_reg.cdc).status.atl(1) := io.i_atl(1)

      r_reg.dc(io.b_write.addr).status.valid := true.B
      r_reg.dc(io.b_write.addr).status.lock := true.B
      r_reg.dc(io.b_write.addr).status.update := false.B
      r_reg.dc(io.b_write.addr).status.atl(0) := false.B
      r_reg.dc(io.b_write.addr).status.atl(1) := false.B


    }.elsewhen(io.b_write.addr =/= r_reg.cdc) {
      when (io.b_write.full) {
        r_reg.dc(io.b_write.addr) := io.b_write.data
      }.otherwise {
        switch (io.b_write.index) {
          is(CONF.STATUS.U) {r_reg.dc(io.b_write.addr).status := io.b_write.data.status}
          is(CONF.ID.U)     {r_reg.dc(io.b_write.addr).id     := io.b_write.data.id}
          is(CONF.ENTRY.U)  {r_reg.dc(io.b_write.addr).entry  := io.b_write.data.entry}
          is(CONF.TABLE.U)  {r_reg.dc(io.b_write.addr).table  := io.b_write.data.table}
          is(CONF.CAP.U)    {r_reg.dc(io.b_write.addr).cap    := io.b_write.data.cap}
          is(CONF.INST.U)   {r_reg.dc(io.b_write.addr).inst   := io.b_write.data.inst}
        }
      }
    }
  }

  // ******************************
  //              I/O
  // ******************************
  io.o_state.cur.valid  := true.B
  io.o_state.cur.addr   := r_reg.cdc
  io.o_state.cur.dc     := r_reg.dc(r_reg.cdc)

  io.o_state.prev.valid := true.B
  io.o_state.prev.addr  := r_reg.pdc
  io.o_state.prev.dc    := r_reg.dc(r_reg.pdc)

  io.o_state.fr.valid   := r_reg.frv
  io.o_state.fr.addr    := r_reg.frdc
  io.o_state.fr.dc      := r_reg.dc(r_reg.frdc)

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    for (dc <- 0 until p.nDomeCfg) {
      io.o_dbg.get(dc) := r_reg.dc(dc).toVec
    }
    
    dontTouch(io.o_state)
  }  
}

class Bypass(p: DmuParams) extends Module {
  val io = IO(new Bundle {
    val i_dcs = Input(Vec(2, new DomeCfgBus(p.pDomeCfg)))
    val b_dcs = Vec(2, new RegFileReadIO(p))

    val i_cdc = Input(UInt(log2Ceil(p.nDomeCfg).W))
    val i_byp = Input(Vec(p.nBypass, new BypassBus(p)))
  })

  // ******************************
  //       BYPASS CONNECTION
  // ******************************
  for (r <- 0 until 2) {
    io.b_dcs(r).ready := true.B
    io.b_dcs(r).data := io.i_dcs(r)

    for (b <- 0 until p.nBypass) {
      when (io.i_byp(b).valid & (io.i_byp(b).addr === io.b_dcs(r).addr) & (io.b_dcs(r).addr =/= io.i_cdc)) { 
        io.b_dcs(r).ready := io.i_byp(b).ready & ((io.b_dcs(r).full & io.i_byp(b).full) | (~io.b_dcs(r).full & (io.i_byp(b).full | (io.b_dcs(r).index =/= io.i_byp(b).index))))
        io.b_dcs(r).data := io.i_byp(b).data
      }
    }
  }  
}

object RegFile extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new RegFile(DmuConfigBase), args)
}

object Bypass extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Bypass(DmuConfigBase), args)
}