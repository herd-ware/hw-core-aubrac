/*
 * File: pc.scala                                                              *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 01:18:01 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.front

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.field._
import herd.common.isa.champ._
import herd.core.aubrac.common._
import herd.core.aubrac.nlp.{NlpReadIO}


class PcStage(p: FrontParams) extends Module {
  require(isPow2(p.nInstrByte), "Instruction must have a 2^n size.")

  val io = IO(new Bundle {
    // Resource state bus
    val b_hart = if (p.useField) Some(new RsrcIO(1, p.nField, 1)) else None

    // Stage flush bus
    val i_flush = Input(Bool())

    // Branch change and information buses
    val i_br_field = if (p.useField) Some(Input(new BranchBus(p.nAddrBit))) else None
    val i_br_new = Input(new BranchBus(p.nAddrBit))

    // Next line predictor
    val b_nlp = if (p.useNlp) Some(Flipped(new NlpReadIO(p.nFetchInstr, p.nAddrBit))) else None

    // Output data buses
    val b_out = new GenRVIO(p, new If0CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr), UInt(0.W)) 

    // Debug buses
    val o_dfp = if (p.debug) Some(Output(UInt(p.nAddrBit.W))) else None
  })

  val w_lock = Wire(Bool())

  // ******************************
  //          HART STATUS
  // ******************************
  val w_hart_valid = Wire(Bool())
  val w_hart_flush = Wire(Bool())

  if (p.useField) {
    w_hart_valid := io.b_hart.get.valid
    w_hart_flush := io.b_hart.get.flush
  } else {
    w_hart_valid := true.B
    w_hart_flush := false.B
  }

  // ******************************
  //           NEXT PC
  // ******************************
  val r_pc_next = RegInit((BigInt(p.pcBoot, 16) + p.nFetchByte).U(p.nAddrBit.W))

  val w_pc = Wire(UInt(p.nAddrBit.W))
  val w_pc_next = Wire(UInt(p.nAddrBit.W))

  w_pc := r_pc_next

  when (~w_lock) {
    r_pc_next := w_pc_next
  }

  when (io.i_br_new.valid) {
    w_pc := io.i_br_new.addr
    if (p.useIMemSeq) {
      when (w_lock) {
        r_pc_next := io.i_br_new.addr
      }
    }
  }
  if (p.useField) {
    when (io.i_br_field.get.valid) {
      w_pc := io.i_br_field.get.addr
      if (p.useIMemSeq) {
        when (w_lock) {
          r_pc_next := io.i_br_field.get.addr
        }
      }
    }
  }

  // ******************************
  //          FETCH PACKET
  // ******************************
  val w_pack_valid = Wire(Vec(p.nFetchInstr, Bool()))
  val w_pack_pc = Wire(UInt(p.nAddrBit.W))

  w_pack_pc := Cat(w_pc(p.nAddrBit - 1, log2Ceil(p.nFetchByte)), 0.U((log2Ceil(p.nFetchByte)).W))

  if (p.nFetchInstr > 1) {
    val w_offset = w_pc(log2Ceil(p.nFetchByte) - 1, log2Ceil(p.nInstrByte))

    for (fi <- 0 until p.nFetchInstr) {
      w_pack_valid(fi) := w_hart_valid & (fi.U >= w_offset)
    }
  } else {
    w_pack_valid(0) := w_hart_valid
  }

  // ******************************
  //              NLP
  // ******************************
  val w_nlp_valid = Wire(Vec(p.nFetchInstr, Bool()))

  w_pc_next := w_pack_pc + p.nFetchByte.U

  if (p.useNlp) {
    io.b_nlp.get.valid := w_pack_valid
    io.b_nlp.get.pc := w_pack_pc

    w_nlp_valid := w_pack_valid

    for (fi0 <- (p.nFetchInstr - 1) to 0 by -1) {
      when (w_pack_valid(fi0) & io.b_nlp.get.br_new(fi0).valid) {
        w_pc_next := io.b_nlp.get.br_new(fi0).addr
        for (fi1 <- (fi0 + 1) until p.nFetchInstr) {
          w_nlp_valid(fi1) := false.B
        }
      }
    }
  } else {
    w_nlp_valid := w_pack_valid    
  }  
  
  // ******************************
  //           REGISTERS
  // ******************************
  // ------------------------------
  //             INIT
  // ------------------------------
  val init_pc_boot = Wire(UInt(p.nAddrBit.W))
  val init_out = Wire(new GenVBus(p, new If0CtrlBus(p.debug, p.nAddrBit, p.nFetchInstr), UInt(0.W)))

  init_pc_boot := BigInt(p.pcBoot, 16).U

  if (p.nFetchInstr > 1) {
    val w_offset = init_pc_boot(log2Ceil(p.nFetchByte) - 1, log2Ceil(p.nInstrByte))
    
    for (fi <- 0 until p.nFetchInstr) {
      init_out.ctrl.get.en(fi) := (fi.U >= w_offset)
    }
  } else {
    init_out.ctrl.get.en(0) := true.B
  }

  init_out.valid := true.B
  init_out.ctrl.get.pc := Cat(init_pc_boot(p.nAddrBit - 1, log2Ceil(p.nFetchByte)), 0.U(log2Ceil(p.nFetchByte).W))
  init_out.ctrl.get.abort := false.B

  // ------------------------------
  //            UPDATE
  // ------------------------------
  val r_out = RegInit(init_out)

  when (~w_lock) {
    r_out.valid := w_nlp_valid.asUInt.orR & w_hart_valid & ~w_hart_flush
    r_out.ctrl.get.en := w_nlp_valid
    r_out.ctrl.get.pc := w_pack_pc
  }

  if (p.useIMemSeq) {
    w_lock := r_out.valid & ~io.b_out.ready    

    when (~io.b_out.ready & (io.i_flush | w_hart_flush)) {
      r_out.ctrl.get.abort := true.B
    }.elsewhen (io.b_out.ready) {
      r_out.ctrl.get.abort := false.B
    }
  } else {
    if (p.useField) {
      w_lock := r_out.valid & ~io.b_out.ready & ~io.i_br_new.valid & ~io.i_br_field.get.valid
    } else {
      w_lock := r_out.valid & ~io.b_out.ready & ~io.i_br_new.valid
    }
  }

  // ******************************
  //            OUTPUTS
  // ******************************
  io.b_out.valid    := r_out.valid
  io.b_out.ctrl.get := r_out.ctrl.get

  // ******************************
  //             FIELD
  // ******************************
  if (p.useField) io.b_hart.get.free := true.B

  // ******************************
  //            DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(w_pack_valid)
    dontTouch(w_nlp_valid)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    io.o_dfp.get := r_out.ctrl.get.pc

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    val r_time = RegInit(0.U(32.W))

    r_time := r_time + 1.U
    
    // Init
    init_out.ctrl.get.etd.get := DontCare
    init_out.ctrl.get.etd.get.done := false.B
    init_out.ctrl.get.etd.get.pc := init_out.ctrl.get.pc
    init_out.ctrl.get.etd.get.tstart := 0.U

    // Registers
    when (~w_lock) {
      r_out.ctrl.get.etd.get.pc := w_pack_pc
      r_out.ctrl.get.etd.get.tstart := r_time + 1.U
    }
    
    dontTouch(r_out.ctrl.get.etd.get)
  }
}

object PcStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new PcStage(FrontConfigBase), args)
}
