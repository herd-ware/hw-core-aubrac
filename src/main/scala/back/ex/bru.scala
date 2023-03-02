/*
 * File: bru.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:58:33 pm                                       *
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

import herd.common.gen._
import herd.common.mem.cbo.{CboIO, CboBus, OP => CBOOP, SORT => CBOSORT, BLOCK => CBOBLOCK}
import herd.core.aubrac.common._
import herd.core.aubrac.nlp.{BranchInfoBus}
import herd.core.aubrac.back.INTUOP._


object BruFSM extends ChiselEnum {
  val s0IDLE, s1CBO, s2HINT = Value
}

class Bru (p: GenParams, nHart: Int, nAddrBit: Int, nDataBit: Int, useExtZifencei: Boolean, useExtZicbo: Boolean, isPipe: Boolean) extends Module {
  import herd.core.aubrac.back.BruFSM._

  def useCbo: Boolean = useExtZifencei || useExtZicbo
  
  val io = IO(new Bundle {
    val i_flush = Input(Bool())
    val o_free = Output(Bool())

    val b_port = new IntUnitIO(p, nHart, nAddrBit, nDataBit)

    val i_br_next = Input(new BranchBus(nAddrBit))
    val i_rs1_link = Input(Bool())
    val i_rd_link = Input(Bool())

    val o_br_info = Output(new BranchInfoBus(nAddrBit))
    val o_br_new = Output(new BranchBus(nAddrBit))
    val o_flush = Output(Bool())
    val b_cbo = if (useCbo) Some(new CboIO(nHart, p.useField, p.nField, nAddrBit)) else None
  })

  val init_cbo = Wire(new CboBus(nHart, p.useField, p.nField, nAddrBit))

  if (useExtZicbo) {
    init_cbo := DontCare
    init_cbo.valid := false.B
    init_cbo.ready := false.B
  } else {
    init_cbo := DontCare
    init_cbo.valid := false.B
    init_cbo.ready := false.B
    init_cbo.op := CBOOP.FLUSH
    init_cbo.sort := CBOSORT.E
    init_cbo.block := CBOBLOCK.FULL
  }

  val r_fsm = RegInit(s0IDLE)
  val r_cbo = RegInit(init_cbo)

  val w_done = Wire(Bool())
  val w_lock = Wire(Bool())

  // ******************************
  //             FSM
  // ******************************
  w_done := false.B
  
  switch(r_fsm) {
    is (s0IDLE) {
      w_done := true.B

      when (io.b_port.req.valid) {
        if (useExtZifencei) {
          switch (io.b_port.req.ctrl.get.uop) {
            is (FENCEI) {
              r_fsm := s1CBO
              w_done := false.B

              r_cbo.valid := true.B
              r_cbo.ready := false.B
              r_cbo.hart := io.b_port.req.ctrl.get.hart
              if (p.useField) r_cbo.field.get := io.b_port.req.ctrl.get.field.get
              r_cbo.op := CBOOP.FLUSH
              r_cbo.sort := CBOSORT.E
              r_cbo.block := CBOBLOCK.FULL
            }
          }
        } 
        if (useExtZicbo) {
          switch (io.b_port.req.ctrl.get.uop) {
            is (CLEAN, INVAL, FLUSH, ZERO) {
              r_fsm := s1CBO
              w_done := false.B

              r_cbo.valid := true.B
              r_cbo.ready := false.B
              r_cbo.hart := io.b_port.req.ctrl.get.hart
              if (p.useField) r_cbo.field.get := io.b_port.req.ctrl.get.field.get
              r_cbo.sort := CBOSORT.A
              r_cbo.block := CBOBLOCK.LINE
              r_cbo.addr := io.b_port.req.data.get.s1

              switch (io.b_port.req.ctrl.get.uop) {
                is (CLEAN)  {r_cbo.op := CBOOP.CLEAN}
                is (INVAL)  {r_cbo.op := CBOOP.INVAL}
                is (FLUSH)  {r_cbo.op := CBOOP.FLUSH}
                is (ZERO)   {r_cbo.op := CBOOP.ZERO}
              }
            }

            is (PFTCHE, PFTCHR, PFTCHW) {
              r_fsm := s2HINT
              w_done := false.B

              r_cbo.valid := true.B
              r_cbo.ready := false.B
              r_cbo.hart := io.b_port.req.ctrl.get.hart
              if (p.useField) r_cbo.field.get := io.b_port.req.ctrl.get.field.get
              r_cbo.op := CBOOP.PFTCH
              r_cbo.addr := io.b_port.req.data.get.s1

              switch (io.b_port.req.ctrl.get.uop) {
                is (PFTCHE) {r_cbo.sort := CBOSORT.E}
                is (PFTCHR) {r_cbo.sort := CBOSORT.R}
                is (PFTCHW) {r_cbo.sort := CBOSORT.W}
              }
            }
          }
        } 
      } 
    }

    is (s1CBO) {
      w_done := r_cbo.ready
      r_cbo.ready := io.b_cbo.get.ready
      when (r_cbo.ready & ~w_lock) {
        r_fsm := s0IDLE
        r_cbo.valid := false.B   
      }
    }

    is (s2HINT) {    
      w_done := true.B  
      when (~w_lock) {
        r_fsm := s0IDLE   
        r_cbo.valid := false.B      
      } 
    }
  }

  when (io.i_flush) {
    r_fsm := s0IDLE
  }

  // ******************************
  //            LOGIC
  // ******************************
  val w_sign = Wire(Bool())

  val w_br = Wire(Bool())
  val w_taken = Wire(Bool())
  val w_jmp = Wire(Bool())
  val w_call = Wire(Bool())
  val w_ret = Wire(Bool())
  val w_flush = Wire(Bool())

  // ------------------------------
  //            DEFAULT
  // ------------------------------  
  w_sign := io.b_port.req.ctrl.get.ssign(0) | io.b_port.req.ctrl.get.ssign(1)

  w_br := false.B
  w_taken := false.B
  w_jmp := false.B
  w_call := false.B
  w_ret := false.B
  w_flush := false.B

  switch (io.b_port.req.ctrl.get.uop) {
    // ------------------------------
    //             JUMP
    // ------------------------------
    is (JAL) {
      w_jmp := true.B
      w_call := io.i_rd_link
    }
    is (JALR) {
      w_jmp := true.B
      w_call := io.i_rd_link
      w_ret := io.i_rs1_link
    }

    // ------------------------------
    //            BRANCH
    // ------------------------------
    is (BEQ) {
      w_br := true.B
      w_taken := (io.b_port.req.data.get.s1 === io.b_port.req.data.get.s2)
    }
    is (BNE) {
      w_br := true.B
      w_taken := (io.b_port.req.data.get.s1 =/= io.b_port.req.data.get.s2)
    }
    is (BLT) {
      w_br := true.B
      when (w_sign) {
        w_taken := ((io.b_port.req.data.get.s1).asSInt < (io.b_port.req.data.get.s2).asSInt)
      }.otherwise {
        w_taken := (io.b_port.req.data.get.s1 < io.b_port.req.data.get.s2)
      }         
    }
    is (BGE) {
      w_br := true.B
      when (w_sign) {
        w_taken := ((io.b_port.req.data.get.s1).asSInt >= (io.b_port.req.data.get.s2).asSInt)
      }.otherwise {
        w_taken := (io.b_port.req.data.get.s1 >= io.b_port.req.data.get.s2)
      }         
    }

    // ------------------------------
    //            FENCE
    // ------------------------------
    is (FENCEI) {
      if (useExtZifencei) {
        w_flush := true.B
      }        
    }

    // ------------------------------
    //             CBO
    // ------------------------------
    is (INVAL) {
      w_flush := true.B
    }
    is (FLUSH) {
      w_flush := true.B      
    }
    is (ZERO) {
      w_flush := true.B      
    }
  }

  // ******************************
  //              CBO
  // ******************************
  if (useCbo) {
    io.b_cbo.get.valid := r_cbo.valid
    io.b_cbo.get.hart := r_cbo.hart
    if (p.useField) io.b_cbo.get.field.get := r_cbo.field.get 
    io.b_cbo.get.op := r_cbo.op
    io.b_cbo.get.sort := r_cbo.sort
    io.b_cbo.get.block := r_cbo.block  
    io.b_cbo.get.addr := r_cbo.addr 
  }

  // ******************************
  //            ADDRESS
  // ******************************
  val w_addr = Wire(UInt(nAddrBit.W))
  val w_redirect = Wire(Bool())

  when (w_jmp) {
    w_addr := io.b_port.req.data.get.s1 + io.b_port.req.data.get.s2
  }.elsewhen(w_br & w_taken) {
    w_addr := io.b_port.req.ctrl.get.pc + io.b_port.req.data.get.s3
  }.otherwise {
    w_addr := io.b_port.req.ctrl.get.pc + 4.U
  }

  w_redirect := io.b_port.req.valid & (~io.i_br_next.valid | (io.i_br_next.addr =/= w_addr))  

  // ******************************
  //           REGISTERS
  // ******************************
  // Default
  val init_ack = Wire(new GenVBus(p, UInt(0.W), UInt(nDataBit.W)))

  init_ack := DontCare
  init_ack.valid := false.B

  val init_br_new = Wire(new BranchBus(nAddrBit))

  init_br_new := DontCare
  init_br_new.valid := false.B

  val r_ack = RegInit(init_ack)
  val r_flush = RegInit(false.B)
  val r_br_new = RegInit(init_br_new)
  
  // Connect
  io.b_port.req.ready := ~w_lock & (r_fsm === s0IDLE)

  if (isPipe) {
    w_lock := r_ack.valid & ~io.b_port.ack.ready

    switch(r_fsm) {
      is (s0IDLE) {
        when (~w_lock | io.i_flush) {
          r_ack.valid := io.b_port.req.valid & w_done & ~io.i_flush
          r_ack.data.get := io.b_port.req.ctrl.get.pc + 4.U 
          r_flush := ((w_done & w_redirect) | w_flush) & ~io.i_flush
          r_br_new.valid := ((w_done & w_redirect) | w_flush) & ~io.i_flush
          r_br_new.addr := Cat(w_addr(nAddrBit - 1, 2), 0.U(2.W))
        }
      }

      is (s1CBO) {
        r_ack.valid := w_done & ~io.i_flush
        r_br_new.valid := w_done & ~io.i_flush & w_flush
      }

      is (s2HINT) {
        r_ack.valid := w_done & ~io.i_flush
      }
    }
    
    io.b_port.ack.valid := r_ack.valid
    io.b_port.ack.data.get := r_ack.data.get

    io.o_flush := r_flush
    io.o_br_new := r_br_new
  } else {
    w_lock := ~io.b_port.ack.ready
    
    io.b_port.ack.valid := (io.b_port.req.valid & w_done)
    io.b_port.ack.data.get := io.b_port.req.ctrl.get.pc + 4.U  

    io.o_flush := (w_done & w_redirect) | w_flush
    io.o_br_new.valid := (w_done & w_redirect) | w_flush
    io.o_br_new.addr := Cat(w_addr(nAddrBit - 1, 2), 0.U(2.W))
  }   

  // ******************************
  //         PREDICTOR INFOS
  // ******************************
  io.o_br_info.valid := io.b_port.req.valid & w_done & ~w_lock & (w_jmp | w_br)
  io.o_br_info.pc := io.b_port.req.ctrl.get.pc
  io.o_br_info.br := w_br
  io.o_br_info.taken := w_taken
  io.o_br_info.jmp := w_jmp
  io.o_br_info.call := w_call
  io.o_br_info.ret := w_ret
  io.o_br_info.target := w_addr   

  // ******************************
  //             FREE
  // ******************************
  if (isPipe) {
    io.o_free := (r_fsm === s0IDLE) & ~r_ack.valid
  } else {
    io.o_free := (r_fsm === s0IDLE)
  }
}

object Bru extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Bru(BackConfigBase, 2, 32, 32, true, true, true), args)
}
