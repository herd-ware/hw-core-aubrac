/*
 * File: muldiv.scala                                                          *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 05:53:05 pm                                       *
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
import chisel3.experimental.ChiselEnum
import scala.math._

import herd.common.gen._
import herd.core.aubrac.back.INTUOP._


object TABLEMULT
{
  val default: List[Bool] =
               List[Bool](    0.B,  0.B,    0.B,    0.B)
  val table : Array[(BitPat, List[Bool])] =
              Array[(BitPat, List[Bool])](

//                                   High/Rem (~Low/Quo) ?
//                       is Mul (~Div) ?     |    Reverse
//                             |   Carry ?   |       |
//                             |     |       |       |
    BitPat(MUL)     ->  List( 1.B,  1.B,    0.B,    0.B),
    BitPat(MULH)    ->  List( 1.B,  1.B,    1.B,    0.B),
    BitPat(DIV)     ->  List( 0.B,  1.B,    0.B,    0.B),
    BitPat(REM)     ->  List( 0.B,  1.B,    1.B,    0.B),
    BitPat(CLMUL)   ->  List( 1.B,  0.B,    0.B,    0.B),
    BitPat(CLMULH)  ->  List( 1.B,  0.B,    1.B,    0.B),
    BitPat(CLMULR)  ->  List( 1.B,  0.B,    1.B,    1.B))
}

class MulShiftOp(nDataBit: Int, nOpLevel: Int, useAdd: Boolean) extends Module {
  def nRound: Int = nDataBit / pow(2, nOpLevel).toInt

  val io = IO(new Bundle {
    val i_s1 = Input(UInt(nDataBit.W))
    val i_s2 = Input(UInt(nDataBit.W))
    val i_round = Input(UInt(log2Ceil(nRound).W))

    val o_res = Output(UInt((nDataBit * 2).W))
  })

  val w_op = Wire(Vec(pow(2, nOpLevel + 1).toInt - 1, UInt((nDataBit * 2).W)))

  // Format inputs
  for (na <- 0 until pow(2, nOpLevel).toInt) {
    val w_bit = (io.i_round << nOpLevel.U) + na.U

    w_op(na) := Mux(io.i_s2(w_bit), (io.i_s1 << w_bit), 0.U)
  }

  var opoff: Int = 0

  // Operation levels
  for (al <- nOpLevel to 1 by -1) {
    for (na <- 0 until pow(2, al - 1).toInt) {    
      if (useAdd) {
        w_op(opoff + pow(2, al).toInt + na) := w_op(opoff + na * 2) + w_op(opoff + na * 2 + 1)
      } else {
        w_op(opoff + pow(2, al).toInt + na) := w_op(opoff + na * 2) ^ w_op(opoff + na * 2 + 1)
      }      
    }

    opoff = opoff + pow(2, al).toInt
  }

  // Result = last w_op
  io.o_res := w_op(pow(2, nOpLevel + 1).toInt - 2)
}

class DivShiftSub(nDataBit: Int) extends Module {
  val io = IO(new Bundle {
    val i_s1 = Input(UInt(nDataBit.W))
    val i_s2 = Input(UInt(nDataBit.W))
    val i_round = Input(UInt(log2Ceil(nDataBit).W))
    val i_quo = Input(UInt(nDataBit.W))
    val i_rem = Input(UInt(nDataBit.W))

    val o_quo = Output(UInt(nDataBit.W))
    val o_rem = Output(UInt(nDataBit.W))
  })

  val w_old_rem = Wire(UInt(nDataBit.W))
  val w_old_quo = Wire(UInt(nDataBit.W))
  
  w_old_rem := io.i_rem << 1.U
  w_old_quo := io.i_quo << 1.U

  // ******************************
  //         NEW DIVIDEND
  // ******************************  
  val w_div = Wire(Vec(nDataBit, Bool()))

  w_div := w_old_rem.asBools
  w_div(0) := io.i_s1((nDataBit - 1).U - io.i_round)

  // ******************************
  //   NEW QUOTIENT AND REMAINDER
  // ******************************
  val w_quo = Wire(Vec(nDataBit, Bool()))
  val w_rem = Wire(UInt(nDataBit.W))

  w_quo := w_old_quo.asBools

  when (w_div.asUInt >= io.i_s2) {
    w_quo(0) := 1.B
    w_rem := w_div.asUInt - io.i_s2
  }.otherwise {
    w_quo(0) := 0.B
    w_rem := w_div.asUInt
  }

  io.o_quo := w_quo.asUInt
  io.o_rem := w_rem
}

object MulDivFSM extends ChiselEnum {
  val s0IDLE, s1MADD, s2DIV, s3MXOR = Value
}

class MulDiv (p: GenParams, nDataBit: Int, isPipe: Boolean, useExt: Boolean, nMulLevel: Int) extends Module {
  import herd.core.aubrac.back.MulDivFSM._

  require((nMulLevel < log2Ceil(nDataBit)), "MulDiv unit must have less adder levels.")

  val io = IO(new Bundle {
    val i_flush = Input(Bool())
    val o_free = Output(Bool())

    val b_port = new IntUnitIO(p, 1, 0, nDataBit)
  })

  val w_end = Wire(Bool())
  val w_lock = Wire(Bool())

  // ******************************
  //          DECODING UOP
  // ******************************
  val w_dec = ListLookup(io.b_port.req.ctrl.get.uop, TABLEMULT.default, TABLEMULT.table)

  // ******************************
  //              FSM
  // ******************************
  val r_fsm = RegInit(s0IDLE)
  val r_round = Reg(UInt(log2Ceil(nDataBit).W))

  when (r_fsm === s1MADD) {
    when (io.i_flush | (w_end & ~w_lock)) {
      r_fsm := s0IDLE
    }
  }.elsewhen (r_fsm === s2DIV) {
    when (io.i_flush | (w_end & ~w_lock)) {
      r_fsm := s0IDLE
    }
  }.elsewhen (r_fsm === s3MXOR) {
    if (useExt) {
      when (io.i_flush | (w_end & ~w_lock)) {
        r_fsm := s0IDLE
      }
    } else {
      r_fsm := s0IDLE
    }
  }.otherwise {
    when (io.b_port.req.valid & ~io.i_flush) {
      if (useExt) {
        when (w_dec(0) & w_dec(1)) {
          r_fsm := s1MADD
        }.elsewhen(w_dec(0)) {
          r_fsm := s3MXOR
        }.otherwise {
          r_fsm := s2DIV
        }
      } else {
        when (w_dec(0)) {
          r_fsm := s1MADD
        }.otherwise {
          r_fsm := s2DIV
        }
      }
    }.otherwise {
      r_fsm := s0IDLE
    }
  }

  io.b_port.req.ready := (r_fsm === s0IDLE)

  // ******************************
  //        UNSIGN (S TO U)  
  // ******************************
  val w_us1_in = Wire(UInt(nDataBit.W))
  val w_us2_in = Wire(UInt(nDataBit.W))
  val w_s1_sign = Wire(Bool())
  val w_s2_sign = Wire(Bool())

  w_us1_in := Mux(io.b_port.req.ctrl.get.ssign(0) & io.b_port.req.data.get.s1(nDataBit - 1), ~(io.b_port.req.data.get.s1 - 1.U), io.b_port.req.data.get.s1)
  w_us2_in := Mux(io.b_port.req.ctrl.get.ssign(1) & io.b_port.req.data.get.s2(nDataBit - 1), ~(io.b_port.req.data.get.s2 - 1.U), io.b_port.req.data.get.s2)
  if (useExt) {
    when (w_dec(3)) {
      w_us1_in := ~io.b_port.req.data.get.s1
      w_us2_in := ~io.b_port.req.data.get.s2
    }
  }
  w_s1_sign := io.b_port.req.ctrl.get.ssign(0) & io.b_port.req.data.get.s1(nDataBit - 1)
  w_s2_sign := io.b_port.req.ctrl.get.ssign(1) & io.b_port.req.data.get.s2(nDataBit - 1)

  // ******************************
  //             STATE
  // ******************************
  val m_src = Module(new GenReg(p, new MulDivSrcCtrlBus(), new MulDivSrcDataBus(nDataBit), false, false, false))

  m_src.io.i_flush := false.B
  m_src.io.b_out.ready := w_end & ~w_lock
  m_src.io.b_in.valid := false.B
  m_src.io.b_in.ctrl.get := DontCare
  m_src.io.b_in.data.get := DontCare

  val w_us1_bset = Wire(UInt(log2Ceil(nDataBit).W))
  val w_us2_rest = Wire(Vec(nDataBit, Bool()))

  val w_nround = Wire(UInt(log2Ceil(nDataBit).W))
  val w_src_zero = Wire(Bool())
  val w_mul_nomore = Wire(Bool())
  val w_div_zero = Wire(Bool())
  val w_div_over = Wire(Bool())
  val w_div_start = Wire(UInt(log2Ceil(nDataBit).W))

  // ------------------------------
  //           REGISTERS
  // ------------------------------
  when ((r_fsm === s1MADD) | (r_fsm === s2DIV) | (r_fsm === s3MXOR)) {
    when (io.i_flush) {
      r_round := 0.U
    }.elsewhen(w_end) {
      when (~w_lock) {
        r_round := 0.U
      }
    }.otherwise {
      r_round := w_nround
    }
  }.otherwise {
    when (io.b_port.req.valid & ~io.i_flush) {
      r_round := 1.U

      m_src.io.b_in.valid := true.B
      m_src.io.b_in.ctrl.get.lquo_hrem := w_dec(2)
      m_src.io.b_in.ctrl.get.rev := w_dec(3)
      m_src.io.b_in.ctrl.get.rsize := io.b_port.req.ctrl.get.rsize
      m_src.io.b_in.data.get.us1 := w_us1_in
      m_src.io.b_in.data.get.us2 := w_us2_in
      m_src.io.b_in.data.get.s1_sign := w_s1_sign
      m_src.io.b_in.data.get.s2_sign := w_s2_sign
    }.otherwise {
      r_round := 0.U
    }
  }

  // ------------------------------
  //             END
  // ------------------------------
  when ((r_fsm === s1MADD) | (r_fsm === s3MXOR)) {
    w_end := (r_round === ((nDataBit / pow(2, nMulLevel).toInt) - 1).U) | w_src_zero | w_mul_nomore
  }.elsewhen (r_fsm === s2DIV) {
    w_end := (r_round === (nDataBit - 1).U) | w_src_zero | w_div_zero
  }.otherwise {
    w_end := false.B
  }

  // ------------------------------
  //          NEXT ROUND
  // ------------------------------
  when ((r_fsm === s2DIV) & (r_round < (w_div_start))) {
    w_nround := w_div_start
  }.otherwise {
    w_nround := r_round + 1.U
  }  

  // ------------------------------
  //         SPECIFIC CASES
  // ------------------------------
  // Accelerated result
  w_us1_bset := PriorityEncoder(Reverse(m_src.io.o_val.data.get.us1))
  for (b <- 0 until nDataBit) {
    w_us2_rest(b) := (b.U >= ((r_round + 1.U) << nMulLevel.U)) & m_src.io.o_val.data.get.us2(b)
  }
  w_div_start := w_us1_bset

  // Instantaneous result
  w_src_zero := (m_src.io.o_val.data.get.us1 === 0.U) | (m_src.io.o_val.data.get.us2 === 0.U)
  w_mul_nomore := (w_us2_rest.asUInt === 0.U)
  w_div_zero := (m_src.io.o_val.data.get.us2 === 0.U)  
  w_div_over := (m_src.io.o_val.data.get.s1_sign ^ m_src.io.o_val.data.get.s2_sign) & (m_src.io.o_val.data.get.us1 === Cat(1.B, Fill(nDataBit - 1, 0.B))) & (m_src.io.o_val.data.get.us2 === Fill(nDataBit, 1.B))
  
  // ******************************
  //             DATA
  // ******************************
  val m_tmp = Module(new GenReg(p, UInt(0.W), new MulDivTmpDataBus(nDataBit), false, true, false))

  m_tmp.io.i_flush := false.B
  m_tmp.io.b_out.ready := w_end & ~w_lock
  m_tmp.io.b_in.valid := false.B
  m_tmp.io.b_in.data.get := DontCare
  m_tmp.io.i_up.get.data.get := m_tmp.io.b_out.data.get

  val w_tmp_acc = Wire(UInt((nDataBit * 2).W))
  
  // ------------------------------
  //             UNITS
  // ------------------------------
  val m_mulsa = Module(new MulShiftOp(nDataBit, nMulLevel, true))

  m_mulsa.io.i_round := r_round
  m_mulsa.io.i_s1 := Mux((r_fsm === s1MADD), m_src.io.o_val.data.get.us1, w_us1_in)
  m_mulsa.io.i_s2 := Mux((r_fsm === s1MADD), m_src.io.o_val.data.get.us2, w_us2_in)

  val m_divss = Module(new DivShiftSub(nDataBit))

  m_divss.io.i_round := r_round
  m_divss.io.i_s1 := Mux((r_fsm === s2DIV), m_src.io.o_val.data.get.us1, w_us1_in)
  m_divss.io.i_s2 := Mux((r_fsm === s2DIV), m_src.io.o_val.data.get.us2, w_us2_in)
  m_divss.io.i_quo := Mux((r_fsm === s2DIV), m_tmp.io.o_val.data.get.ulquo, 0.U)
  m_divss.io.i_rem := Mux((r_fsm === s2DIV), m_tmp.io.o_val.data.get.uhrem, 0.U)

  val m_mulsx = if (useExt) Some(Module(new MulShiftOp(nDataBit, nMulLevel, true))) else None

  if (useExt) {
    m_mulsx.get.io.i_round := r_round
    m_mulsx.get.io.i_s1 := Mux((r_fsm === s3MXOR), m_src.io.o_val.data.get.us1, w_us1_in)
    m_mulsx.get.io.i_s2 := Mux((r_fsm === s3MXOR), m_src.io.o_val.data.get.us2, w_us2_in)
  }

  // ------------------------------
  //        TEMPORARY VALUES
  // ------------------------------
  when (r_fsm === s1MADD) {
    w_tmp_acc := Cat(m_tmp.io.o_val.data.get.uhrem, m_tmp.io.o_val.data.get.ulquo) + m_mulsa.io.o_res
  }.elsewhen(r_fsm === s3MXOR) {
    if (useExt) {
      w_tmp_acc := Cat(m_tmp.io.o_val.data.get.uhrem, m_tmp.io.o_val.data.get.ulquo) ^ m_mulsx.get.io.o_res
    } else {
      w_tmp_acc := DontCare
    }    
  }.otherwise {
    w_tmp_acc := m_mulsa.io.o_res
  }  

  when ((r_fsm === s1MADD) | (r_fsm === s3MXOR)) {
    when (~w_end) {
      m_tmp.io.i_up.get.data.get.ulquo := w_tmp_acc(nDataBit - 1, 0)
      m_tmp.io.i_up.get.data.get.uhrem := w_tmp_acc(nDataBit * 2 - 1, nDataBit)
    }
  }.elsewhen (r_fsm === s2DIV) {
    when (~w_end) {
      m_tmp.io.i_up.get.data.get.ulquo := m_divss.io.o_quo
      m_tmp.io.i_up.get.data.get.uhrem := m_divss.io.o_rem
    }
  }.otherwise {
    when (io.b_port.req.valid & ~io.i_flush) {
      m_tmp.io.b_in.valid := true.B
      when (w_dec(0)) {
        m_tmp.io.b_in.data.get.ulquo := w_tmp_acc(nDataBit - 1, 0)
        m_tmp.io.b_in.data.get.uhrem := w_tmp_acc(nDataBit * 2 - 1, nDataBit)
      }.otherwise {
        m_tmp.io.b_in.data.get.ulquo := m_divss.io.o_quo
        m_tmp.io.b_in.data.get.uhrem := m_divss.io.o_rem
      }
    }
  }

  // ******************************
  //             RESULT
  // ******************************
  val w_fin = Wire(UInt(nDataBit.W))

  // ------------------------------
  //            RESIZE
  // ------------------------------
  val w_uacc = Wire(UInt((nDataBit * 2).W))
  val w_uquo = Wire(UInt(nDataBit.W))
  val w_urem = Wire(UInt(nDataBit.W))

  if (nDataBit >= 64) {
    when (m_src.io.o_val.ctrl.get.rsize === INTSIZE.W) {
      w_uacc := w_tmp_acc(31, 0)
      w_uquo := m_divss.io.o_quo(31, 0)
      w_urem := m_divss.io.o_rem(31, 0)
    }.otherwise {
      w_uacc := w_tmp_acc
      w_uquo := m_divss.io.o_quo
      w_urem := m_divss.io.o_rem
    }
  } else {
    w_uacc := w_tmp_acc
    w_uquo := m_divss.io.o_quo
    w_urem := m_divss.io.o_rem
  }

  // ------------------------------
  //         SIGN (U TO S)
  // ------------------------------
  val w_acc = Wire(UInt((nDataBit * 2).W))
  val w_quo = Wire(UInt(nDataBit.W))
  val w_rem = Wire(UInt(nDataBit.W))

  when (w_src_zero) {
    w_acc := 0.U
  }.otherwise {
    w_acc := Mux(m_src.io.o_val.data.get.s1_sign ^ m_src.io.o_val.data.get.s2_sign, (~w_uacc) + 1.U, w_uacc)
  }

  when (w_div_zero) {
    w_quo := Fill(nDataBit, 1.B)
    w_rem := Mux(m_src.io.o_val.data.get.s1_sign, (~m_src.io.o_val.data.get.us1) + 1.U, m_src.io.o_val.data.get.us1)
  }.elsewhen (w_div_over) {
    w_quo := (~m_src.io.o_val.data.get.us1) + 1.U
    w_rem := 0.U
  }.elsewhen (w_src_zero) {
    w_quo := 0.U
    w_rem := 0.U
  }.otherwise {
    w_quo := Mux(m_src.io.o_val.data.get.s1_sign ^ m_src.io.o_val.data.get.s2_sign, (~w_uquo) + 1.U, w_uquo)
    w_rem := Mux(m_src.io.o_val.data.get.s1_sign, (~w_urem) + 1.U, w_urem)
  }

  // ------------------------------
  //            SELECT
  // ------------------------------
  w_fin := DontCare
  when (r_fsm === s1MADD) {
    when (m_src.io.o_val.ctrl.get.lquo_hrem) {
      w_fin := w_acc(nDataBit * 2 - 1, nDataBit)
    }.otherwise {
      w_fin := w_acc(nDataBit - 1, 0)
    }
  }.elsewhen (r_fsm === s2DIV) {
    when (m_src.io.o_val.ctrl.get.lquo_hrem) {
      w_fin := w_rem
    }.otherwise {
      w_fin := w_quo       
    }
  }.elsewhen (r_fsm === s3MXOR) {
    if (useExt) {
      when (m_src.io.o_val.ctrl.get.lquo_hrem) {
        when (m_src.io.o_val.ctrl.get.rev) {
          w_fin := ~w_acc(nDataBit * 2 - 2, nDataBit - 1)
        }.otherwise {
          w_fin := w_acc(nDataBit * 2 - 1, nDataBit)
        }        
      }.otherwise {
        w_fin := w_acc(nDataBit - 1, 0)
      }
    }
  }

  // ******************************
  //            OUTPUTS
  // ******************************
  val m_ack = Module(new GenReg(p, UInt(0.W), UInt(nDataBit.W), false, false, true))

  m_ack.io := DontCare
  m_ack.io.i_flush := io.i_flush

  // ------------------------------
  //            REGISTER
  // ------------------------------
  if (isPipe) {
    io.o_free := (r_fsm === s0IDLE) & ~m_ack.io.o_val.valid

    w_lock := ~m_ack.io.b_in.ready

    m_ack.io.b_in.valid := w_end
    m_ack.io.b_in.data.get := w_fin

    m_ack.io.b_out <> io.b_port.ack
  
  // ------------------------------
  //             DIRECT
  // ------------------------------
  } else {
    io.o_free := (r_fsm === s0IDLE)

    w_lock := ~io.b_port.ack.ready

    io.b_port.ack.valid := w_end
    io.b_port.ack.data.get := w_fin
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {    
    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    val w_dfp = Wire(new Bundle {
      val us1 = UInt(nDataBit.W)
      val us2 = UInt(nDataBit.W)
      val ulquo = UInt(nDataBit.W)
      val uhrem = UInt(nDataBit.W)
      val res = if (isPipe) Some(UInt(nDataBit.W)) else None
    })

    w_dfp.us1 := m_src.io.o_reg.data.get.us1
    w_dfp.us2 := m_src.io.o_reg.data.get.us2
    w_dfp.ulquo := m_tmp.io.o_reg.data.get.ulquo
    w_dfp.uhrem := m_tmp.io.o_reg.data.get.uhrem
    if (isPipe) w_dfp.res.get := m_ack.io.o_reg.data.get

    dontTouch(w_dfp)
  }
}

object MulShiftAdd extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new MulShiftOp(32, 3, true), args)
}

object MulShiftXor extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new MulShiftOp(32, 3, false), args)
}

object DivShiftSub extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new DivShiftSub(32), args)
}

object MulDiv extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new MulDiv(GenConfigBase, 64, true, true, 4), args)
}
