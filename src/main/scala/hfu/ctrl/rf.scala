/*
 * File: rf.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:32:48 pm
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

import herd.common.isa.champ._


class RegFile (p: HfuParams) extends Module {
  require((p.nChampReg <= CST.MAXCFG), "A maximum of " + CST.MAXCFG + " dome configurations is possible.")
  
  val io = IO(new Bundle {
    val b_read = Vec(2, new RegFileReadIO(p))
    val b_write = new RegFileWriteIO(p)

    val o_state = Output(new RegFileStateBus(p.nChampReg, p.pDomeCfg))
    val i_atl = Input(Vec(2, Bool()))

    val o_dbg = if (p.debug) Some(Output(Vec(p.nChampReg, Vec(6, UInt(p.nDataBit.W))))) else None
  })

  // ******************************
  //             INIT
  // ******************************
  val init_reg = Wire(new RegFileBus(p.nChampReg, p.pDomeCfg))

  init_reg.hf(0).status.valid     := true.B
  init_reg.hf(0).status.lock      := true.B
  init_reg.hf(0).status.update    := false.B
  init_reg.hf(0).status.size      := 0.B
  init_reg.hf(0).status.atl(0)    := false.B
  init_reg.hf(0).status.atl(1)    := false.B

  if (p.useChampExtR) {
    init_reg.hf(0).status.order   := (p.nDataBit / log2Ceil(p.nDataBit)).U
  } else {
    init_reg.hf(0).status.order   := 0.U
  }  

  init_reg.hf(0).id.fromUInt(0.U)

  init_reg.hf(0).entry            := BigInt(p.pcBoot, 16).U

  init_reg.hf(0).table            := Cat(Fill(p.nDataBit, 1.B))

  init_reg.hf(0).cap.secmie       := true.B
  init_reg.hf(0).cap.seccst       := true.B
  for (tl <- 0 until CST.MAXTL) {
    init_reg.hf(0).cap.featl(tl)  := (tl < p.pDomeCfg.nTrapLvl).B    
  }
  init_reg.hf(0).cap.feafr        := p.useChampExtFr.B
  init_reg.hf(0).cap.feacbo       := true.B

  init_reg.hf(0).inst.weight      := WEIGHT.FULL.U
  init_reg.hf(0).inst.muldiv      := true.B
  init_reg.hf(0).inst.fr          := true.B

  for (hf <- 1 until p.nChampReg) {
    init_reg.hf(hf).status        := DontCare
    init_reg.hf(hf).status.valid  := false.B
    init_reg.hf(hf).status.update := false.B
    init_reg.hf(hf).status.atl(0) := false.B
    init_reg.hf(hf).status.atl(1) := false.B
    init_reg.hf(hf).id            := DontCare
    init_reg.hf(hf).entry         := DontCare
    init_reg.hf(hf).table         := DontCare
    init_reg.hf(hf).cap           := DontCare
    init_reg.hf(hf).inst          := DontCare
  }

  init_reg.chf := 0.U
  init_reg.phf := 0.U
  init_reg.frv := false.B
  init_reg.frhf := 0.U

  val r_reg = RegInit(init_reg)

  // ******************************
  //              READ
  // ******************************
  for (r <- 0 until 2) {
    io.b_read(r).ready := true.B
    io.b_read(r).data := r_reg.hf(io.b_read(r).addr)
  }

  // ******************************
  //             WRITE
  // ******************************
  io.b_write.ready := true.B
  
  when (io.b_write.valid) {
    when (io.b_write.sw =/= SWUOP.X) {
      r_reg.chf := io.b_write.addr
      r_reg.phf := r_reg.chf

      r_reg.hf(r_reg.chf).status.valid := (io.b_write.sw === SWUOP.V) | (io.b_write.sw === SWUOP.L)
      r_reg.hf(r_reg.chf).status.lock := (io.b_write.sw === SWUOP.L)
      r_reg.hf(r_reg.chf).status.update := false.B
      if (p.nChampTrapLvl > 0) r_reg.hf(r_reg.chf).status.atl(0) := io.i_atl(0)
      if (p.nChampTrapLvl > 1) r_reg.hf(r_reg.chf).status.atl(1) := io.i_atl(1)

      r_reg.hf(io.b_write.addr).status.valid := true.B
      r_reg.hf(io.b_write.addr).status.lock := true.B
      r_reg.hf(io.b_write.addr).status.update := false.B
      r_reg.hf(io.b_write.addr).status.atl(0) := false.B
      r_reg.hf(io.b_write.addr).status.atl(1) := false.B


    }.elsewhen(io.b_write.addr =/= r_reg.chf) {
      when (io.b_write.full) {
        r_reg.hf(io.b_write.addr) := io.b_write.data
      }.otherwise {
        switch (io.b_write.index) {
          is(CONF.STATUS.U) {r_reg.hf(io.b_write.addr).status := io.b_write.data.status}
          is(CONF.ID.U)     {r_reg.hf(io.b_write.addr).id     := io.b_write.data.id}
          is(CONF.ENTRY.U)  {r_reg.hf(io.b_write.addr).entry  := io.b_write.data.entry}
          is(CONF.TABLE.U)  {r_reg.hf(io.b_write.addr).table  := io.b_write.data.table}
          is(CONF.CAP.U)    {r_reg.hf(io.b_write.addr).cap    := io.b_write.data.cap}
          is(CONF.INST.U)   {r_reg.hf(io.b_write.addr).inst   := io.b_write.data.inst}
        }
      }
    }
  }

  // ******************************
  //              I/O
  // ******************************
  io.o_state.cur.valid  := true.B
  io.o_state.cur.addr   := r_reg.chf
  io.o_state.cur.hf     := r_reg.hf(r_reg.chf)

  io.o_state.prev.valid := true.B
  io.o_state.prev.addr  := r_reg.phf
  io.o_state.prev.hf    := r_reg.hf(r_reg.phf)

  io.o_state.fr.valid   := r_reg.frv
  io.o_state.fr.addr    := r_reg.frhf
  io.o_state.fr.hf      := r_reg.hf(r_reg.frhf)

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    for (hf <- 0 until p.nChampReg) {
      io.o_dbg.get(hf) := r_reg.hf(hf).toVec
    }
    
    dontTouch(io.o_state)
  }  
}

class Bypass(p: HfuParams) extends Module {
  val io = IO(new Bundle {
    val i_hfs = Input(Vec(2, new DomeCfgBus(p.pDomeCfg)))
    val b_hfs = Vec(2, new RegFileReadIO(p))

    val i_chf = Input(UInt(log2Ceil(p.nChampReg).W))
    val i_byp = Input(Vec(p.nBypass, new BypassBus(p)))
  })

  // ******************************
  //       BYPASS CONNECTION
  // ******************************
  for (r <- 0 until 2) {
    io.b_hfs(r).ready := true.B
    io.b_hfs(r).data := io.i_hfs(r)

    for (b <- 0 until p.nBypass) {
      when (io.i_byp(b).valid & (io.i_byp(b).addr === io.b_hfs(r).addr) & (io.b_hfs(r).addr =/= io.i_chf)) { 
        io.b_hfs(r).ready := io.i_byp(b).ready & ((io.b_hfs(r).full & io.i_byp(b).full) | (~io.b_hfs(r).full & (io.i_byp(b).full | (io.b_hfs(r).index =/= io.i_byp(b).index))))
        io.b_hfs(r).data := io.i_byp(b).data
      }
    }
  }  
}

object RegFile extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new RegFile(HfuConfigBase), args)
}

object Bypass extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Bypass(HfuConfigBase), args)
}