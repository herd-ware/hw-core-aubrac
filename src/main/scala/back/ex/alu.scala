/*
 * File: alu.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:58:30 pm                                       *
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

import herd.common.gen._


class Alu (p: GenParams, nDataBit: Int, isPipe: Boolean, useExt: Boolean) extends Module {
  import herd.core.aubrac.back.INTUOP._

  val io = IO(new Bundle {
    val i_flush = Input(Bool())
    val o_free = Output(Bool())

    val b_port = new IntUnitIO(p, 1, 0, nDataBit)

    val o_byp = Output(UInt(nDataBit.W))    
    val o_add = Output(UInt(nDataBit.W))  

    val o_dfp = if (p.debug && isPipe) Some(Output(UInt(nDataBit.W))) else None  
  })  

  val w_lock = Wire(Bool())

  // ******************************
  //           OPERANDS
  // ******************************
  val w_uop = Wire(UInt(NBIT.W))
  val w_sign = Wire(Bool())
  val w_s1 = Wire(UInt(nDataBit.W))
  val w_s2 = Wire(UInt(nDataBit.W))
  val w_amount = Wire(UInt(log2Ceil(nDataBit).W))

  if (nDataBit >= 64) {
    when (io.b_port.req.ctrl.get.rsize === INTSIZE.W) {
      w_amount := io.b_port.req.data.get.s2(4,0).asUInt
    }.otherwise {
      w_amount := io.b_port.req.data.get.s2(5,0).asUInt
    }
  } else {
    w_amount := io.b_port.req.data.get.s2(4,0).asUInt
  }  

  w_sign := io.b_port.req.ctrl.get.ssign(0) | io.b_port.req.ctrl.get.ssign(1)
  w_uop := io.b_port.req.ctrl.get.uop
  w_s1 := io.b_port.req.data.get.s1
  w_s2 := io.b_port.req.data.get.s2

  if (useExt) {
    switch (io.b_port.req.ctrl.get.uop) {
      is (SH1ADD) {
        w_uop := ADD
        w_s1 := (io.b_port.req.data.get.s1 << 1.U)
      }
      is (SH2ADD) {
        w_uop := ADD
        w_s1 := (io.b_port.req.data.get.s1 << 2.U)
      }
      is (SH3ADD) {
        w_uop := ADD
        w_s1 := (io.b_port.req.data.get.s1 << 3.U)
      }
      is (CLZ) {
        if (nDataBit >= 64) {
          when (io.b_port.req.ctrl.get.rsize === INTSIZE.W) {
            w_s1 := Cat(Fill(32, 1.B), Reverse(io.b_port.req.data.get.s1(31, 0)))   
          } 
        }
      }
      is (CTZ) {
        if (nDataBit >= 64) {
          when (io.b_port.req.ctrl.get.rsize === INTSIZE.W) {
            w_s1 := Cat(Fill(32, 1.B), io.b_port.req.data.get.s1(31, 0))
          }
        }
      }
      is (CPOP) {
        if (nDataBit >= 64) {
          when (io.b_port.req.ctrl.get.rsize === INTSIZE.W) {
            w_s1 := Cat(Fill(32, 0.B), io.b_port.req.data.get.s1(31, 0))
          }
        }
      }
      is (ANDN) {
        w_uop := AND
        w_s2 := ~io.b_port.req.data.get.s2
      }
      is (ORN) {
        w_uop := OR
        w_s2 := ~io.b_port.req.data.get.s2
      }
    }
  }  

  // ******************************
  //            LOGIC
  // ******************************
  // Processing
  val w_add = Wire(UInt(nDataBit.W))
  val w_res = Wire(UInt(nDataBit.W))

  w_add := io.b_port.req.data.get.s1 + w_s2  
  w_res := 0.U
  
  switch (w_uop) {
    is (ADD) {
      w_res := w_add
    }
    is (SUB) {
      w_res := w_s1 - w_s2
    }
    is (SLT) {
      when (w_sign) {
        w_res := (w_s1).asSInt < (w_s2).asSInt
      }.otherwise {
        w_res := w_s1 < w_s2
      }      
    }
    is (OR) {
      w_res := w_s1 | w_s2
    }
    is (AND) {
      w_res := w_s1 & w_s2
    }
    is (XOR) {
      w_res := w_s1 ^ w_s2
    }
    is (SHR) {
      when (w_sign) {
        w_res := ((w_s1).asSInt >> w_amount).asUInt
      }.otherwise {
        w_res := w_s1 >> w_amount
      }      
    }
    is (SHL) {
      w_res := w_s1 << w_amount      
    }
  }

  if (useExt) {
    switch (w_uop) {
      is (XNOR) {
        w_res := ~(w_s1 ^ w_s2)
      }
      is (CLZ, CTZ) {
        w_res := Mux(~w_s1.orR, PriorityEncoder(Reverse(w_s1)), nDataBit.U)                
      }
      is (CPOP) {
        w_res := PopCount(w_s1)               
      }
      is (MAX) {
        when (w_sign) {
          w_res := Mux(((w_s1).asSInt > (w_s2).asSInt), w_s1, w_s2)
        }.otherwise {
          w_res := Mux((w_s1 > w_s2), w_s1, w_s2)
        }      
      }
      is (MIN) {
        when (w_sign) {
          w_res := Mux(((w_s1).asSInt < (w_s2).asSInt), w_s1, w_s2)
        }.otherwise {
          w_res := Mux((w_s1 < w_s2), w_s1, w_s2)
        }      
      }
      is (EXTB) {
        when (w_sign) {
          w_res := Cat(Fill(nDataBit - 8, w_s1(7)), w_s1(7, 0))
        }.otherwise {
          w_res := Cat(Fill(nDataBit - 8, 0.B), w_s1(7, 0))
        }      
      }
      is (EXTH) {
        when (w_sign) {
          w_res := Cat(Fill(nDataBit - 16, w_s1(15)), w_s1(15, 0))
        }.otherwise {
          w_res := Cat(Fill(nDataBit - 16, 0.B), w_s1(15, 0))
        }      
      }
      is (ROL) {
        w_res := (Cat(w_s1, w_s1) << w_amount)(nDataBit * 2 - 1, nDataBit)
        if (nDataBit >= 64) {
          when (io.b_port.req.ctrl.get.rsize === INTSIZE.W) {
            w_res := (Cat(w_s1(31, 0), w_s1(31, 0)) << w_amount)(63, 32)
          }
        }
      }
      is (ROR) {
        w_res := (Cat(w_s1, w_s1) >> w_amount)
        if (nDataBit >= 64) {
          when (io.b_port.req.ctrl.get.rsize === INTSIZE.W) {
            w_res := (Cat(w_s1(31, 0), w_s1(31, 0)) >> w_amount)
          }
        }
      }
      is (ORCB) {
        val w_byte = Wire(Vec((nDataBit / 8), UInt(8.W)))

        for (b <- 0 until (nDataBit / 8)) {
          when (w_s1((b + 1) * 8 - 1, b * 8).asUInt.orR) {
            w_byte(b) := Cat(Fill(8, 1.B))
          }.otherwise {
            w_byte(b) := 0.U
          }          
        }

        w_res := w_byte.asUInt
      }
      is (REV8) {
        val w_byte = Wire(Vec((nDataBit / 8), UInt(8.W)))

        for (b <- 0 until (nDataBit / 8)) {
          w_byte(b) := w_s1(nDataBit - (8 * b) - 1, nDataBit - (8 * (b + 1)))       
        }

        w_res := w_byte.asUInt
      }
      is (BCLR) {
        val w_bit = Wire(Vec(nDataBit, Bool()))
        
        for (b <- 0 until nDataBit) {
          w_bit(b) := (b.U =/= w_amount) & w_s1(b)
        }

        w_res := w_bit.asUInt
      }
      is (BEXT) {
        w_res := w_s1(w_amount)
      }
      is (BINV) {
        val w_bit = Wire(Vec(nDataBit, Bool()))
        
        for (b <- 0 until nDataBit) {
          w_bit(b) := (b.U =/= w_amount) & w_s1(b) | ((b.U === w_amount) & ~w_s1(b))
        }

        w_res := w_bit.asUInt
      }
      is (BSET) {
        val w_bit = Wire(Vec(nDataBit, Bool()))
        
        for (b <- 0 until nDataBit) {
          w_bit(b) := (b.U =/= w_amount) & w_s1(b) | (b.U === w_amount)
        }

        w_res := w_bit.asUInt
      }
    }
  }
  
  // ******************************
  //      FINAL RESULT & BYPASS
  // ******************************
  // Format final result
  val w_fin = Wire(UInt(nDataBit.W))

  if (nDataBit >= 64) {
    when (io.b_port.req.ctrl.get.rsize === INTSIZE.W) {
      w_fin := Mux(w_sign, Cat(Fill(32, w_res(31)), w_res(31, 0)), Cat(Fill(32, 0.B), w_res(31, 0)))
    }.otherwise {
      w_fin := w_res
    }    
  } else {
    w_fin := w_res
  }

  // Bypass result  
  io.o_byp := w_fin
  io.o_add := w_add

  // ******************************
  //           REGISTERS
  // ******************************
  val m_ack = Module(new GenReg(p, UInt(0.W), UInt(nDataBit.W), false, false, true))

  m_ack.io := DontCare
  m_ack.io.i_flush := io.i_flush
  
  io.b_port.req.ready := ~w_lock

  if (isPipe) {
    w_lock := ~m_ack.io.b_in.ready

    m_ack.io.b_in.valid := io.b_port.req.valid
    m_ack.io.b_in.data.get := w_fin

    io.b_port.ack <> m_ack.io.b_out
  } else {
    w_lock := ~io.b_port.ack.ready
    
    io.b_port.ack.valid := io.b_port.req.valid
    io.b_port.ack.data.get := w_fin
  } 

  // ******************************
  //             FREE
  // ******************************
  if (isPipe) {
    io.o_free := ~m_ack.io.o_val.valid
  } else {
    io.o_free := true.B
  }  

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    
    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    if (isPipe) {
      io.o_dfp.get := m_ack.io.o_reg.data.get    
    }
  }
}

object Alu extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Alu(GenConfigBase, 64, true, true), args)
}
