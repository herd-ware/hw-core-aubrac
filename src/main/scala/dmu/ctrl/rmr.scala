/*
 * File: rmr.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:55:35 pm                                       *
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

import herd.common.gen._
import herd.common.dome._
import herd.common.isa.champ._
import herd.common.tools.Counter
import herd.core.aubrac.common._


object RmrFSM extends ChiselEnum {
  val s0IDLE, s1SWFLUSH, s2SWLAUNCH, s3FRFLUSH, s4FREXE = Value
}

class Rmr (p: DmuParams) extends Module {
  import herd.core.aubrac.dmu.RmrFSM._

  require(((!p.useChampExtFr) || (p.nPart > 2)), "At least three parts are necesary to use fast recovery.")
  
  val io = IO(new Bundle {
    val b_req = Flipped(new RmrReqIO(p))

    val i_state = Input(new RegFileStateBus(p.nDomeCfg, p.pDomeCfg))

    val o_flush = Output(Bool())
    val b_dome = Flipped(Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit)))
    val b_hart = Flipped(new RsrcIO(p.nHart, p.nDome, 1))
    val b_pexe = Flipped(new NRsrcIO(p.nHart, p.nDome, p.nPart))
    val b_pall = Flipped(new NRsrcIO(p.nHart, p.nDome, p.nPart))

    val o_br_dome = Output(new BranchBus(p.nAddrBit))
  })

  val r_fsm = RegInit(s0IDLE)

  val init_reg = Wire(new RmrStateBus(p))

  init_reg.cur_flush := false.B
  init_reg.cur_free := false.B
  init_reg.fr_sw := false.B
  init_reg.fr_flush := false.B
  init_reg.fr_free := false.B
  init_reg.target := DontCare

  val r_reg = RegInit(init_reg)

  // ******************************
  //             FSM
  // ******************************
  val w_exe_flush = Wire(Bool())
  val w_fr_flush = Wire(Bool())
  val w_fr_sw = Wire(Bool())

  w_exe_flush := io.i_state.cur.dc.cap.secmie | io.b_req.dcres.cap.secmie
  if (p.useChampExtFr) {
    w_fr_sw := io.i_state.cur.dc.cap.feafr & io.i_state.cur.dc.inst.fr
    w_fr_flush := w_fr_sw & io.i_state.fr.dc.cap.secmie
  } else {
    w_fr_sw := false.B
    w_fr_flush := false.B
  }

  io.b_req.ready := (r_fsm === s0IDLE)

  switch(r_fsm) {
    is (s0IDLE) {  
      r_reg.cur_flush := false.B
      r_reg.fr_flush := false.B
      r_reg.target := io.b_req.target

      when (io.b_req.valid & (io.b_req.op === RMRUOP.SWITCH)) {
        when (w_exe_flush | w_fr_flush) {
          r_fsm := s1SWFLUSH
          r_reg.cur_flush := w_exe_flush
          if (p.useChampExtFr) {
            r_reg.fr_sw := w_fr_sw
            r_reg.fr_flush := w_fr_flush
          }
        }.otherwise {
          r_fsm := s2SWLAUNCH
        }
      }.otherwise {
        if (p.useChampExtFr) {
          when (io.b_req.valid & (io.b_req.op === RMRUOP.NOFR)) {
            when (io.i_state.cur.dc.cap.secmie | io.i_state.fr.dc.cap.secmie) {
              r_fsm := s3FRFLUSH
              r_reg.fr_flush := true.B
            }.otherwise {
              r_fsm := s4FREXE
            }
          }
        }
      }
    }

    is (s1SWFLUSH) {
      when ((~r_reg.cur_flush | r_reg.cur_free) & (~r_reg.fr_flush | r_reg.fr_free)) {
        r_fsm := s2SWLAUNCH
        r_reg.cur_flush := false.B
        if (p.useChampExtFr) {
          r_reg.fr_flush := false.B
        }
      }
    }

    is (s2SWLAUNCH) {
      r_fsm := s0IDLE
      if (p.useChampExtFr) {
        r_reg.fr_sw := false.B
      }
    }

    is (s3FRFLUSH) {
      when (~r_reg.fr_flush | r_reg.fr_free) {
        r_fsm := s4FREXE
        r_reg.cur_flush := false.B
        r_reg.fr_flush := false.B
      }
    }

    is (s4FREXE) {
      r_fsm := s0IDLE
    }
  }

  // ******************************
  //             DOME
  // ******************************
  // Default dome state registers
  val init_state = Wire(Vec(p.nDome, new RmrDomeBus(p)))

  init_state := DontCare
  init_state(0).id := 0.U
  init_state(0).mie := true.B

  val r_state = RegInit(init_state)

  // Default dome at boot
  val init_dome = Wire(UInt(log2Ceil(p.nDome).W))

  init_dome := 0.U

  val r_exe_dome = RegInit(init_dome)
  val r_fr_state = RegInit(init_dome)

  // Default resources multiplexing
  val r_mux = RegInit(VecInit(Seq.fill(2)(RMRMUX.EXE)))

  // ------------------------------
  //             STATE
  // ------------------------------
  val w_dome_used = Wire(Vec(p.nDome, Bool()))

  val w_dome_exe_here = Wire(Bool())
  val w_dome_exe_used = Wire(Vec(p.nDome, Bool()))
  val w_dome_exe_slct = Wire(UInt(log2Ceil(p.nDome).W))

  // Dome in Rmr registers ?
  w_dome_exe_here := false.B
  w_dome_exe_slct := 0.U

  for (d <- 0 until p.nDome) {
    // Same security ? (mie)
    val w_exe_mie = Wire(Bool())
    val w_fr_mie = Wire(Bool())

    if (p.useChampExtMie) {
      w_exe_mie := (r_state(d).mie === io.i_state.cur.dc.cap.secmie)
      w_fr_mie := (r_state(d).mie === io.i_state.fr.dc.cap.secmie)
    } else {
      w_exe_mie := true.B
      w_fr_mie := true.B
    }

    // Same security ? (cst)
    val w_exe_cst = Wire(Bool())
    val w_fr_cst = Wire(Bool())

    if (p.useChampExtCst) {
      w_exe_cst := (r_state(d).cst === io.i_state.cur.dc.cap.seccst)
      w_fr_cst := (r_state(d).cst === io.i_state.fr.dc.cap.seccst)
    } else {
      w_exe_cst := true.B
      w_fr_cst := true.B
    }

    // Compare all
    w_dome_used(d) := false.B

    when ((r_state(d).id === io.i_state.cur.dc.id.toUInt) & w_exe_mie & w_exe_cst) {
      w_dome_used(d) := true.B
      w_dome_exe_here := true.B
      w_dome_exe_slct := d.U
    }

    if (p.useChampExtFr) {
      when (io.i_state.fr.valid & (r_state(d).id === io.i_state.fr.dc.id.toUInt) & w_fr_mie & w_fr_cst) {
        w_dome_used(d) := true.B
      }
    }    
  }

  // ------------------------------
  //          DOME SELECT
  // ------------------------------
  w_dome_exe_used := w_dome_used
  when (~w_dome_exe_here) {
    w_dome_exe_slct := PriorityEncoder(~w_dome_exe_used.asUInt)
  }

  switch(r_fsm) {
    is (s2SWLAUNCH) {
      r_exe_dome := w_dome_exe_slct

      when(~w_dome_exe_here) {
        r_state(w_dome_exe_slct).id      := io.i_state.fr.dc.id.toUInt
        r_state(w_dome_exe_slct).mie     := io.i_state.fr.dc.cap.secmie
        r_state(w_dome_exe_slct).cst     := io.i_state.fr.dc.cap.seccst
        r_state(w_dome_exe_slct).use(0)  := true.B
      }

      when (r_reg.fr_sw) {
        r_fr_state := r_exe_dome

        r_state(r_fr_state).use(1)       := false.B
        r_state(r_exe_dome).use(1)       := true.B
      }
    }

    is (s4FREXE) {
      r_fr_state := r_exe_dome

      r_state(r_fr_state).use(1)          := false.B
    }
  }

  // ------------------------------
  //        DOME MULTIPLEXING
  // ------------------------------
  switch(r_fsm) {
    is (s2SWLAUNCH) {
      when (r_reg.fr_sw) {
        switch(r_mux(0)) {
          is (RMRMUX.EXE) { r_mux(0) := RMRMUX.FR}
          is (RMRMUX.FR)  { r_mux(0) := RMRMUX.EXE}
        }

        switch(r_mux(1)) {
          is (RMRMUX.EXE) {
            when (r_mux(1) =/= r_mux(0)) {
                            r_mux(1) := RMRMUX.FR
            }
          }
          is (RMRMUX.FR)  { r_mux(1) := RMRMUX.EXE}
        }
      }
    }

    is (s4FREXE) {
      r_mux(0) := RMRMUX.EXE
      r_mux(1) := RMRMUX.EXE
    }
  }

  // ------------------------------
  //             I/Os
  // ------------------------------
  // Multiple domes
  if (p.useChampExtFr) {
    for (d <- 0 until p.nDome) {
      io.b_dome(d).valid := r_state(d).use.asUInt.orR 
      io.b_dome(d).id := r_state(d).id
      io.b_dome(d).entry := Mux(r_state(d).use(1), io.i_state.fr.dc.entry, io.i_state.cur.dc.entry)
      io.b_dome(d).exe := r_state(d).use(0)
      io.b_dome(d).flush := (r_state(d).use(0) & r_reg.cur_flush) | (r_state(d).use(1) & r_reg.fr_flush)
      io.b_dome(d).tl := Mux(r_state(d).use(1), io.i_state.fr.dc.cap.featl, io.i_state.cur.dc.cap.featl)
      io.b_dome(d).cbo := Mux(r_state(d).use(1), io.i_state.fr.dc.cap.feacbo, io.i_state.cur.dc.cap.feacbo)
      io.b_dome(d).mie := r_state(d).mie
    }   

  /// One domes
  } else {
    io.b_dome(0).valid := true.B
    io.b_dome(0).id := io.i_state.cur.dc.id.toUInt
    io.b_dome(0).entry := io.i_state.cur.dc.entry
    io.b_dome(0).exe := true.B
    io.b_dome(0).flush := r_reg.cur_flush
    io.b_dome(0).tl := io.i_state.cur.dc.cap.featl
    io.b_dome(0).cbo := io.i_state.cur.dc.cap.feacbo
    io.b_dome(0).mie := io.i_state.cur.dc.cap.secmie
  }

  // ******************************
  //           RESOURCES
  // ******************************
  // ------------------------------
  //             HART
  // ------------------------------
  // Only executed
  io.b_hart.valid := true.B
  io.b_hart.flush := r_reg.cur_flush
  io.b_hart.hart := 0.U
  io.b_hart.dome := r_exe_dome
  io.b_hart.port := 0.U

  // ------------------------------
  //     ONLY FOR EXECUTED DOME
  // ------------------------------
  // Parts for executed domes
  io.b_pexe.weight(0) := p.nPart.U
  for (d <- 1 until p.nDome) {
    io.b_pexe.weight(d) := 0.U
  }
  
  for (pa <- 0 until p.nPart) {
    io.b_pexe.state(pa).valid := true.B
    io.b_pexe.state(pa).flush := r_exe_dome
    io.b_pexe.state(pa).hart := 0.U
    io.b_pexe.state(pa).dome := 0.U
    io.b_pexe.state(pa).port := 0.U
  }

  // ------------------------------
  //         ALL RESOURCES
  // ------------------------------
  if (p.useChampExtFr) {
    for (d <- 0 until p.nDome) {
      io.b_pall.weight(d) := 0.U
    }

    when ((r_exe_dome =/= r_fr_state) & (r_mux(0) === RMRMUX.FR) | (r_mux(1) === RMRMUX.FR)) {
      io.b_pall.weight(r_exe_dome) := (p.nPart - 1).U
      io.b_pall.weight(r_fr_state) := 1.U
    }.otherwise {
      io.b_pall.weight(r_exe_dome) := p.nPart.U
      io.b_pall.weight(r_fr_state) := 0.U
    }    

    for (pa <- 0 until p.nPart) {
      io.b_pall.state(pa).valid := true.B
      io.b_pall.state(pa).flush := r_reg.cur_flush
      io.b_pall.state(pa).hart := 0.U
      io.b_pall.state(pa).dome := r_exe_dome
      io.b_pall.state(pa).port := 0.U
    }

    for (pa <- 0 until 2) {
      when (r_mux(pa) === RMRMUX.FR) {
        io.b_pall.state(pa).valid := r_state(r_fr_state).use(1)
        io.b_pall.state(pa).flush := r_reg.fr_flush
        io.b_pall.state(pa).hart := 0.U
        io.b_pall.state(pa).dome := r_fr_state
        io.b_pall.state(pa).port := 0.U
      }
    }
  } else {
    io.b_pall.weight(0) := p.nPart.U
    for (pa <- 0 until p.nPart) {
      io.b_pall.state(pa).valid := true.B
      io.b_pall.state(pa).flush := r_reg.cur_flush
      io.b_pall.state(pa).hart := 0.U
      io.b_pall.state(pa).dome := r_exe_dome
      io.b_pall.state(pa).port := 0.U
    }
  }

  // ******************************
  //             FLUSH
  // ******************************
  val w_exe_free = Wire(Vec(p.nPart, Bool()))
  val w_fr_free = Wire(Vec(2, Bool()))

  val m_cst = Module(new Counter(log2Ceil(p.nDomeFlushCycle + 1)))

  m_cst.io.i_limit := p.nDomeFlushCycle.U
  m_cst.io.i_init := ~(r_reg.cur_flush & r_reg.fr_flush)
  m_cst.io.i_en := r_reg.cur_flush | r_reg.fr_flush

  io.o_flush := r_reg.cur_flush

  if (p.useChampExtFr) {
    for (pa <- 0 until 2) {
      w_exe_free(pa) := io.b_pexe.state(pa).free & ((r_mux(pa) =/= RMRMUX.EXE) | io.b_pall.state(pa).free)
      w_fr_free(pa) := ((r_mux(pa) =/= RMRMUX.FR) | io.b_pall.state(pa).free)
    }

    for (pa <- 2 until p.nPart) {
      w_exe_free(pa) := io.b_pexe.state(pa).free & io.b_pall.state(pa).free
    }

  } else {
    for (pa <- 0 until 2) {
      w_fr_free(pa) := true.B
    }

    for (pa <- 0 until p.nPart) {
      w_exe_free(pa) := io.b_pexe.state(pa).free & io.b_pall.state(pa).free
    }
  }

  if (p.useChampExtCst) {
    when ((r_reg.cur_flush & r_state(r_exe_dome).cst) | (r_reg.fr_flush & r_state(r_fr_state).cst)) {
      r_reg.cur_free := m_cst.io.o_flag
      r_reg.fr_free := m_cst.io.o_flag
    }.otherwise {
      r_reg.cur_free := r_reg.cur_flush & w_exe_free.asUInt.andR & io.b_hart.free & io.b_dome(0).free
      r_reg.fr_free := r_reg.fr_flush & w_fr_free.asUInt.andR
    }
  } else {
    r_reg.cur_free := r_reg.cur_flush & w_exe_free.asUInt.andR & io.b_hart.free & io.b_dome(0).free
    r_reg.fr_free := r_reg.fr_flush & w_fr_free.asUInt.andR
  }

  // ------------------------------
  //             BOOT
  // ------------------------------
  io.o_br_dome.valid := (r_fsm === s2SWLAUNCH)
  io.o_br_dome.addr := r_reg.target

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    dontTouch(io.b_hart)
    dontTouch(io.b_dome)
    dontTouch(io.b_pall)
    dontTouch(io.b_pexe)
  }  
}

object Rmr extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Rmr(DmuConfigBase), args)
}