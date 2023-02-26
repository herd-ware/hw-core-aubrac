/*
 * File: rsb.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:06:18 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.nlp

import chisel3._
import chisel3.util._

import herd.common.dome._


class RsbReg(p: RsbParams) extends Bundle {
  val target = Vec(p.nDepth, UInt(p.nTargetBit.W))
  val pt = UInt(log2Ceil(p.nDepth + 1).W)
  val wmin = Bool()
  val wmax = Bool()
}

class Rsb(p: RsbParams) extends Module {
  val io = IO(new Bundle {
    val b_hart = if (p.useDome) Some(new RsrcIO(1, p.nDome, 1)) else None

    val i_restore = Input(Bool())

    val b_read = Vec(2, new RsbReadIO(p))
    val b_write = Vec(2, new RsbWriteIO(p))
  })

  // ******************************
  //             FLUSH
  // ******************************
  val w_flush = Wire(Bool())

  if (p.useDome) w_flush := io.b_hart.get.flush else w_flush := false.B

  // ******************************
  //             STACK
  // ******************************
  val init_rsb = Wire(new RsbReg(p))
  init_rsb.target := DontCare
  init_rsb.pt := 0.U
  init_rsb.wmin := false.B
  init_rsb.wmax := false.B

  // 0: real version
  // 1+: speculative version
  val r_rsb = RegInit(VecInit(Seq.fill(2)(init_rsb)))

  val w_rsb_real = Wire(new RsbReg(p))
  w_rsb_real := r_rsb(0)

  // ******************************
  //            LOGIC
  // ******************************
  val w_pt = Wire(Vec(2, UInt(log2Ceil(p.nDepth + 1).W)))

  for (po <- 0 until 2) {
    when (w_flush) {
      w_pt(po) := 0.U
    }.elsewhen (io.b_read(po).valid & ~io.b_write(po).valid) {
      when (r_rsb(po).pt === 0.U) {
        w_pt(po) := (p.nDepth - 1).U
      }.otherwise {
        w_pt(po) := r_rsb(po).pt - 1.U
      }      
    }.elsewhen (~io.b_read(po).valid & io.b_write(po).valid) {
      when (r_rsb(po).pt === (p.nDepth - 1).U) {
        w_pt(po) := 0.U
      }.otherwise {
        w_pt(po) := r_rsb(po).pt + 1.U
      }    
    }.otherwise {
      w_pt(po) := r_rsb(po).pt
    }
  }


  // ******************************
  //             READ
  // ******************************
  for (po <- 0 until 2) {
    when (~r_rsb(po).wmin | (~r_rsb(po).wmax & (r_rsb(po).pt === 0.U))) {
      io.b_read(po).target := 0.U
    }.otherwise {
      if (p.useSpec) {
        when(r_rsb(po).pt === 0.U) {
          io.b_read(po).target := r_rsb(po).target((p.nDepth - 1).U)
        }.otherwise {
          io.b_read(po).target := r_rsb(po).target(r_rsb(po).pt - 1.U)
        }        
      } else {
        when(r_rsb(po).pt === 0.U) {
          io.b_read(po).target := r_rsb(0).target((p.nDepth - 1).U)
        }.otherwise {
          io.b_read(po).target := r_rsb(0).target(r_rsb(po).pt - 1.U)
        }  
      }      
    }
  }

  // ******************************
  //             WRITE
  // ******************************
  for (po <- 0 until 2) {
    when (io.b_write(po).valid) {
      if (p.useSpec && (po != 0)) {
        r_rsb(po).target(r_rsb(po).pt) := io.b_write(po).target
      } else {
        w_rsb_real.target(r_rsb(po).pt) := io.b_write(po).target
      }
    }
  }

  // ******************************
  //           REGISTER
  // ******************************
  // ------------------------------
  //             REAL
  // ------------------------------
  w_rsb_real.pt := w_pt(0)

  when (w_flush) {
    w_rsb_real.wmin := false.B
    w_rsb_real.wmax := false.B
  }.elsewhen(io.b_write(0).valid) {
    w_rsb_real.wmin := true.B
    when(r_rsb(0).pt === (p.nDepth - 1).U) {
      w_rsb_real.wmax := true.B
    }
  }

  r_rsb(0) := w_rsb_real

  // ------------------------------
  //             SPEC
  // ------------------------------
  for (po <- 1 until 2) {
    r_rsb(po).pt := w_pt(po)

    when (w_flush) {
      r_rsb(po).wmin := false.B
      r_rsb(po).wmax := false.B
    }.elsewhen(io.b_write(po).valid) {
      r_rsb(po).wmin := true.B
      when(r_rsb(po).pt === (p.nDepth - 1).U) {
        r_rsb(po).wmax := true.B
      }
    }

    when (io.i_restore) {
      r_rsb(po) := w_rsb_real
    }
  }

  // ******************************
  //             DOME
  // ******************************
  if (p.useDome) {
    val w_free = Wire(Vec(2, Bool()))
    for (po <- 0 until 2) {
      w_free(po) := (r_rsb(po).pt === 0.U) & ~r_rsb(po).wmin & ~r_rsb(po).wmax
    }
    io.b_hart.get.free := w_free.asUInt.andR
  }
}

object Rsb extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Rsb(RsbConfigBase), args)
}
