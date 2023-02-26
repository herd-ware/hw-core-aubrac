/*
 * File: gpr.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:59:33 pm                                       *
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

import herd.common.isa.base._


class GprRMux (p: GprParams) extends Module {
  val io = IO(new Bundle {
    val b_log = Vec(p.nGprReadLog, new GprReadIO(p))
    val i_byp = Input(Vec(p.nGprBypass, new BypassBus(p.nHart, p.nDataBit)))
    val b_phy = Flipped(Vec(p.nGprReadPhy, new GprReadIO(p)))
  })

  // ******************************
  //        READ PHYSICAL GPR
  // ******************************
  // ------------------------------
  //   DIRECT CONNECT (LOG == PHY)
  // ------------------------------
  if (p.nGprReadLog == p.nGprReadPhy) {
    io.b_log <> io.b_phy
    
  // ------------------------------
  //        MUX (LOG > PHY)
  // ------------------------------
  } else {
    // TODO: Smart read (no different port used for the same physical source)
    val w_av = Wire(Vec(p.nGprReadLog, Vec(p.nGprReadPhy, Bool())))
    val w_done = Wire(Vec(p.nGprReadLog, Bool()))
    val w_slct = Wire(Vec(p.nGprReadLog, UInt(log2Ceil(p.nGprReadPhy).W)))

    // Default
    for (rl <- 0 until p.nGprReadLog) {
      io.b_log(rl).ready := false.B
      io.b_log(rl).data := DontCare
      w_done(rl) := false.B
      w_slct(rl) := 0.U
    }

    for (rp <- 0 until p.nGprReadPhy) {
      w_av(0)(rp) := true.B
      io.b_phy(rp) := DontCare
      io.b_phy(rp).valid := false.B      
    }

    // Select
    when (io.b_log(0).valid) {
      w_done(0) := true.B
      w_slct(0) := 0.U
    }

    for (rl <- 1 until p.nGprReadLog) {
      w_av(rl) := w_av(rl - 1)

      when (w_done(rl - 1)) {
        w_av(rl)(w_slct(rl -1)) := false.B
      }

      w_done(rl) := io.b_log(rl).valid & w_av(rl).asUInt.orR
      w_slct(rl) := PriorityEncoder(w_av(rl).asUInt)
    }

    for (rl <- 0 until p.nGprReadLog) {
      when (w_done(rl)) {
        io.b_phy(w_slct(rl)) <> io.b_log(rl)
      }
    }
  }

  // ******************************
  //            BYPASS
  // ******************************
  for (rl <- 0 until p.nGprReadLog) {
    for (b <- 0 until p.nGprBypass) {
      when (io.i_byp(b).valid & (io.i_byp(b).addr === io.b_log(rl).addr) & (io.b_log(rl).addr =/= REG.X0.U) & (io.b_log(rl).hart === io.i_byp(b).hart)) {
        io.b_log(rl).ready := io.i_byp(b).ready
        io.b_log(rl).data := io.i_byp(b).data
      }
    }
  }
}

class GprWMux(p: GprParams) extends Module {
  val io = IO(new Bundle {
    val b_log = Vec(p.nGprWriteLog, new GprWriteIO(p))
    val b_phy = Flipped(Vec(p.nGprWritePhy, new GprWriteIO(p)))
  })

  // ******************************
  //   DIRECT CONNECT (LOG == PHY)
  // ******************************
  if (p.nGprWriteLog == p.nGprWritePhy) {
    io.b_log <> io.b_phy

  // ******************************
  //        MUX (LOG > PHY)
  // ******************************
  } else {
    val w_av = Wire(Vec(p.nGprWriteLog, Vec(p.nGprWritePhy, Bool())))
    val w_done = Wire(Vec(p.nGprWriteLog, Bool()))
    val w_slct = Wire(Vec(p.nGprWriteLog, UInt(log2Ceil(p.nGprWritePhy).W)))

    // Default
    for (wl <- 0 until p.nGprWriteLog) {
      io.b_log(wl).ready := false.B
      w_done(wl) := false.B
      w_slct(wl) := 0.U
    }

    for (wp <- 0 until p.nGprWritePhy) {
      w_av(0)(wp) := true.B
      io.b_phy(wp).valid := false.B
      io.b_phy(wp).addr := DontCare
      io.b_phy(wp).data := DontCare
    }

    // Select
    when (io.b_log(0).valid) {
      w_done(0) := true.B
      w_slct(0) := 0.U
    }

    for (wl <- 1 until p.nGprWriteLog) {
      w_av(wl) := w_av(wl - 1)

      when (w_done(wl - 1)) {
        w_av(wl)(w_slct(wl -1)) := false.B
      }

      w_done(wl) := io.b_log(wl).valid & w_av(wl).asUInt.orR
      w_slct(wl) := PriorityEncoder(w_av(wl).asUInt)
    }

    for (wl <- 0 until p.nGprWriteLog) {
      when (w_done(wl)) {
        io.b_phy(w_slct(wl)) <> io.b_log(wl)
      }
    }
  }
}

class Gpr(p : GprParams) extends Module {
  val io = IO(new Bundle {
    val b_read = Vec(p.nGprReadLog, new GprReadIO(p))
    val b_write = Vec(p.nGprWriteLog, new GprWriteIO(p))
    val i_byp = Input(Vec(p.nGprBypass, new BypassBus(p.nHart, p.nDataBit)))

    val o_dbg = if (p.debug) Some(Output(Vec(p.nHart, Vec(32, UInt(p.nDataBit.W))))) else None
    val o_dfp = if (p.debug) Some(Output(new GprDfpBus(p))) else None
  
  })

  val r_gpr = Reg(Vec(p.nHart, Vec(32, UInt(p.nDataBit.W))))
  
  // ******************************
  //             I/Os
  // ******************************
  val m_rmux = Module(new GprRMux(p))
  val m_wmux = Module(new GprWMux(p))

  m_rmux.io.b_log <> io.b_read
  m_rmux.io.i_byp := io.i_byp
  m_wmux.io.b_log <> io.b_write

  // ******************************
  //              READ
  // ******************************
  for (r <- 0 until p.nGprReadPhy) {
    m_rmux.io.b_phy(r).ready := true.B
    m_rmux.io.b_phy(r).data := 0.U
    when (m_rmux.io.b_phy(r).addr =/= REG.X0.U) {
      m_rmux.io.b_phy(r).data := r_gpr(m_rmux.io.b_phy(r).hart)(m_rmux.io.b_phy(r).addr)
    }
  }

  // ******************************
  //             WRITE
  // ******************************
  for (w <- 0 until p.nGprWritePhy) {
    m_wmux.io.b_phy(w).ready := true.B
    when(m_wmux.io.b_phy(w).valid & m_wmux.io.b_phy(w).addr =/= REG.X0.U) {
      r_gpr(m_wmux.io.b_phy(w).hart)(m_wmux.io.b_phy(w).addr) := m_wmux.io.b_phy(w).data
    }
  }

  // ******************************
  //           FIXED X0
  // ******************************
  for (h <- 0 until p.nHart) {
    r_gpr(h)(0) := 0.U
  }

  // ******************************
  //            DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    for (h <- 0 until p.nHart) {
      for (i <- 0 until 32) {
        io.o_dbg.get(h)(i) := r_gpr(h)(i)
      }
    }

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    for (r <- 0 until p.nGprReadPhy) {
      io.o_dfp.get.wire(r) := m_rmux.io.b_phy(r).data
    }
    for (h <- 0 until p.nHart) {
      for (i <- 0 until 32) {
        io.o_dfp.get.gpr(h)(i) := r_gpr(h)(i)
      }
    }

  }
}

object Gpr extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Gpr(GprConfigBase), args)
}