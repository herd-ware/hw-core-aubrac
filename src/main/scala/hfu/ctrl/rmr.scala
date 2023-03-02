/*
 * File: rmr.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 01:54:27 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.hfu

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import herd.common.gen._
import herd.common.field._
import herd.common.isa.champ._
import herd.common.tools.Counter
import herd.core.aubrac.common._


object RmrFSM extends ChiselEnum {
  val s0IDLE, s1SWFLUSH, s2SWLAUNCH, s3FRFLUSH, s4FREXE = Value
}

class Rmr (p: HfuParams) extends Module {
  import herd.core.aubrac.hfu.RmrFSM._

  require(((!p.useChampExtFr) || (p.nPart > 2)), "At least three parts are necesary to use fast recovery.")
  
  val io = IO(new Bundle {
    val b_req = Flipped(new RmrReqIO(p))

    val i_state = Input(new RegFileStateBus(p.nChampReg, p.pFieldStruct))

    val o_flush = Output(Bool())
    val b_field = Flipped(Vec(p.nField, new FieldIO(p.nAddrBit, p.nDataBit)))
    val b_hart = Flipped(new RsrcIO(p.nHart, p.nField, 1))
    val b_pexe = Flipped(new NRsrcIO(p.nHart, p.nField, p.nPart))
    val b_pall = Flipped(new NRsrcIO(p.nHart, p.nField, p.nPart))

    val o_br_field = Output(new BranchBus(p.nAddrBit))
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

  w_exe_flush := io.i_state.cur.hf.cap.secmie | io.b_req.hfres.cap.secmie
  if (p.useChampExtFr) {
    w_fr_sw := io.i_state.cur.hf.cap.feafr & io.i_state.cur.hf.inst.fr
    w_fr_flush := w_fr_sw & io.i_state.fr.hf.cap.secmie
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
            when (io.i_state.cur.hf.cap.secmie | io.i_state.fr.hf.cap.secmie) {
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
  //            FIELD
  // ******************************
  // Default field state registers
  val init_state = Wire(Vec(p.nField, new RmrFieldBus(p)))

  init_state := DontCare
  init_state(0).id := 0.U
  init_state(0).mie := true.B

  val r_state = RegInit(init_state)

  // Default field at boot
  val init_field = Wire(UInt(log2Ceil(p.nField).W))

  init_field := 0.U

  val r_exe_field = RegInit(init_field)
  val r_fr_state = RegInit(init_field)

  // Default resources multiplexing
  val r_mux = RegInit(VecInit(Seq.fill(2)(RMRMUX.EXE)))

  // ------------------------------
  //             STATE
  // ------------------------------
  val w_field_used = Wire(Vec(p.nField, Bool()))

  val w_field_exe_here = Wire(Bool())
  val w_field_exe_used = Wire(Vec(p.nField, Bool()))
  val w_field_exe_slct = Wire(UInt(log2Ceil(p.nField).W))

  // Field in Rmr registers ?
  w_field_exe_here := false.B
  w_field_exe_slct := 0.U

  for (f <- 0 until p.nField) {
    // Same security ? (mie)
    val w_exe_mie = Wire(Bool())
    val w_fr_mie = Wire(Bool())

    if (p.useChampExtMie) {
      w_exe_mie := (r_state(f).mie === io.i_state.cur.hf.cap.secmie)
      w_fr_mie := (r_state(f).mie === io.i_state.fr.hf.cap.secmie)
    } else {
      w_exe_mie := true.B
      w_fr_mie := true.B
    }

    // Same security ? (cst)
    val w_exe_cst = Wire(Bool())
    val w_fr_cst = Wire(Bool())

    if (p.useChampExtCst) {
      w_exe_cst := (r_state(f).cst === io.i_state.cur.hf.cap.seccst)
      w_fr_cst := (r_state(f).cst === io.i_state.fr.hf.cap.seccst)
    } else {
      w_exe_cst := true.B
      w_fr_cst := true.B
    }

    // Compare all
    w_field_used(f) := false.B

    when ((r_state(f).id === io.i_state.cur.hf.id.toUInt) & w_exe_mie & w_exe_cst) {
      w_field_used(f) := true.B
      w_field_exe_here := true.B
      w_field_exe_slct := f.U
    }

    if (p.useChampExtFr) {
      when (io.i_state.fr.valid & (r_state(f).id === io.i_state.fr.hf.id.toUInt) & w_fr_mie & w_fr_cst) {
        w_field_used(f) := true.B
      }
    }    
  }

  // ------------------------------
  //         FIELD SELECT
  // ------------------------------
  w_field_exe_used := w_field_used
  when (~w_field_exe_here) {
    w_field_exe_slct := PriorityEncoder(~w_field_exe_used.asUInt)
  }

  switch(r_fsm) {
    is (s2SWLAUNCH) {
      r_exe_field := w_field_exe_slct

      when(~w_field_exe_here) {
        r_state(w_field_exe_slct).id      := io.i_state.fr.hf.id.toUInt
        r_state(w_field_exe_slct).mie     := io.i_state.fr.hf.cap.secmie
        r_state(w_field_exe_slct).cst     := io.i_state.fr.hf.cap.seccst
        r_state(w_field_exe_slct).use(0)  := true.B
      }

      when (r_reg.fr_sw) {
        r_fr_state := r_exe_field

        r_state(r_fr_state).use(1)       := false.B
        r_state(r_exe_field).use(1)       := true.B
      }
    }

    is (s4FREXE) {
      r_fr_state := r_exe_field

      r_state(r_fr_state).use(1)          := false.B
    }
  }

  // ------------------------------
  //       FIELD MULTIPLEXING
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
  // Multiple fields
  if (p.useChampExtFr) {
    for (f <- 0 until p.nField) {
      io.b_field(f).valid := r_state(f).use.asUInt.orR 
      io.b_field(f).id := r_state(f).id
      io.b_field(f).entry := Mux(r_state(f).use(1), io.i_state.fr.hf.entry, io.i_state.cur.hf.entry)
      io.b_field(f).exe := r_state(f).use(0)
      io.b_field(f).flush := (r_state(f).use(0) & r_reg.cur_flush) | (r_state(f).use(1) & r_reg.fr_flush)
      io.b_field(f).tl := Mux(r_state(f).use(1), io.i_state.fr.hf.cap.featl, io.i_state.cur.hf.cap.featl)
      io.b_field(f).cbo := Mux(r_state(f).use(1), io.i_state.fr.hf.cap.feacbo, io.i_state.cur.hf.cap.feacbo)
      io.b_field(f).mie := r_state(f).mie
    }   

  /// One fields
  } else {
    io.b_field(0).valid := true.B
    io.b_field(0).id := io.i_state.cur.hf.id.toUInt
    io.b_field(0).entry := io.i_state.cur.hf.entry
    io.b_field(0).exe := true.B
    io.b_field(0).flush := r_reg.cur_flush
    io.b_field(0).tl := io.i_state.cur.hf.cap.featl
    io.b_field(0).cbo := io.i_state.cur.hf.cap.feacbo
    io.b_field(0).mie := io.i_state.cur.hf.cap.secmie
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
  io.b_hart.field := r_exe_field
  io.b_hart.port := 0.U

  // ------------------------------
  //     ONLY FOR EXECUTEDFIELD
  // ------------------------------
  // Parts for executed fields
  io.b_pexe.weight(0) := p.nPart.U
  for (f <- 1 until p.nField) {
    io.b_pexe.weight(f) := 0.U
  }
  
  for (pa <- 0 until p.nPart) {
    io.b_pexe.state(pa).valid := true.B
    io.b_pexe.state(pa).flush := r_exe_field
    io.b_pexe.state(pa).hart := 0.U
    io.b_pexe.state(pa).field := 0.U
    io.b_pexe.state(pa).port := 0.U
  }

  // ------------------------------
  //         ALL RESOURCES
  // ------------------------------
  if (p.useChampExtFr) {
    for (f <- 0 until p.nField) {
      io.b_pall.weight(f) := 0.U
    }

    when ((r_exe_field =/= r_fr_state) & (r_mux(0) === RMRMUX.FR) | (r_mux(1) === RMRMUX.FR)) {
      io.b_pall.weight(r_exe_field) := (p.nPart - 1).U
      io.b_pall.weight(r_fr_state) := 1.U
    }.otherwise {
      io.b_pall.weight(r_exe_field) := p.nPart.U
      io.b_pall.weight(r_fr_state) := 0.U
    }    

    for (pa <- 0 until p.nPart) {
      io.b_pall.state(pa).valid := true.B
      io.b_pall.state(pa).flush := r_reg.cur_flush
      io.b_pall.state(pa).hart := 0.U
      io.b_pall.state(pa).field := r_exe_field
      io.b_pall.state(pa).port := 0.U
    }

    for (pa <- 0 until 2) {
      when (r_mux(pa) === RMRMUX.FR) {
        io.b_pall.state(pa).valid := r_state(r_fr_state).use(1)
        io.b_pall.state(pa).flush := r_reg.fr_flush
        io.b_pall.state(pa).hart := 0.U
        io.b_pall.state(pa).field := r_fr_state
        io.b_pall.state(pa).port := 0.U
      }
    }
  } else {
    io.b_pall.weight(0) := p.nPart.U
    for (pa <- 0 until p.nPart) {
      io.b_pall.state(pa).valid := true.B
      io.b_pall.state(pa).flush := r_reg.cur_flush
      io.b_pall.state(pa).hart := 0.U
      io.b_pall.state(pa).field := r_exe_field
      io.b_pall.state(pa).port := 0.U
    }
  }

  // ******************************
  //             FLUSH
  // ******************************
  val w_exe_free = Wire(Vec(p.nPart, Bool()))
  val w_fr_free = Wire(Vec(2, Bool()))

  val m_cst = Module(new Counter(log2Ceil(p.nFieldFlushCycle + 1)))

  m_cst.io.i_limit := p.nFieldFlushCycle.U
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
    when ((r_reg.cur_flush & r_state(r_exe_field).cst) | (r_reg.fr_flush & r_state(r_fr_state).cst)) {
      r_reg.cur_free := m_cst.io.o_flag
      r_reg.fr_free := m_cst.io.o_flag
    }.otherwise {
      r_reg.cur_free := r_reg.cur_flush & w_exe_free.asUInt.andR & io.b_hart.free & io.b_field(0).free
      r_reg.fr_free := r_reg.fr_flush & w_fr_free.asUInt.andR
    }
  } else {
    r_reg.cur_free := r_reg.cur_flush & w_exe_free.asUInt.andR & io.b_hart.free & io.b_field(0).free
    r_reg.fr_free := r_reg.fr_flush & w_fr_free.asUInt.andR
  }

  // ------------------------------
  //             BOOT
  // ------------------------------
  io.o_br_field.valid := (r_fsm === s2SWLAUNCH)
  io.o_br_field.addr := r_reg.target

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    dontTouch(io.b_hart)
    dontTouch(io.b_field)
    dontTouch(io.b_pall)
    dontTouch(io.b_pexe)
  }  
}

object Rmr extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Rmr(HfuConfigBase), args)
}