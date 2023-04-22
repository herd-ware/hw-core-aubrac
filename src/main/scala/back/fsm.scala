/*
 * File: fsm.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-21 09:58:41 am                                       *
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

import herd.common.field._
import herd.core.aubrac.common._
import herd.io.core.clint.{ClintIO}


class Fsm (nHart: Int, useChamp: Boolean, nInstrBit: Int, nAddrBit: Int, nDataBit: Int) extends Module {
  def useField: Boolean = useChamp
  
  val io = IO(new Bundle {
    val b_hart = if (useField) Some(new RsrcIO(nHart, 1, 1)) else None

    val i_stop = Input(Bool())
    val i_empty = Input(Bool())
    val i_br = Input(Bool())
    val i_wb = Input(new StageBus(nHart, nAddrBit, nInstrBit))
    val i_raise = Input(new RaiseBus(nAddrBit, nDataBit))
    val b_clint = Flipped(new ClintIO(nDataBit))

    val o_trap = Output(new TrapBus(nAddrBit, nDataBit))
    val o_lock = Output(Bool())
  })

  val init_reg = Wire(new BackBus(nAddrBit, nDataBit))

  init_reg := DontCare
  init_reg.state := STATE.RUN

  val r_reg = RegInit(init_reg)

  io.b_clint := DontCare
  
  // ******************************
  //          HART STATUS
  // ******************************
  val w_hart_valid = Wire(Bool())
  val w_hart_flush = Wire(Bool())

  if (useField) {
    w_hart_valid := io.b_hart.get.valid & ~io.b_hart.get.flush
    w_hart_flush := io.b_hart.get.flush
  } else {
    w_hart_valid := true.B
    w_hart_flush := false.B
  }

  // ******************************
  //             FSM
  // ******************************
  switch (r_reg.state) {
    // ------------------------------
    //             RUN
    // ------------------------------
    is (STATE.RUN) {
      when (io.b_clint.en) {
        r_reg.state := STATE.IRQ
        r_reg.pc := io.i_wb.pc
        r_reg.cause := io.b_clint.ecause
        r_reg.info := 0.U
      }.elsewhen (io.i_raise.valid) {
        r_reg.pc := io.i_raise.pc
        r_reg.cause := io.i_raise.cause
        r_reg.info := io.i_raise.info
        switch (io.i_raise.src) {
          is (TRAPSRC.WFI)   {
            r_reg.state := STATE.WFI
            r_reg.pc := io.i_raise.pc + 4.U
          }
          is (TRAPSRC.EXC)   {
            r_reg.state := STATE.EXC
          }
          is (TRAPSRC.MRET)  {
            r_reg.state := STATE.MRET
          }
        }
        if (!useChamp) {
          switch (io.i_raise.src) {
            is (TRAPSRC.MRET)  {
              r_reg.state := STATE.MRET
            }
          }
        }
      }.elsewhen (io.i_stop) {
        r_reg.state := STATE.STOP
      }
    }

    // ------------------------------
    //             STOP
    // ------------------------------
    is (STATE.STOP) {
      when (io.b_clint.en) {
        r_reg.state := STATE.IRQ
        r_reg.cause := io.b_clint.ecause
        r_reg.info := 0.U
      }.elsewhen (io.i_raise.valid) {
        r_reg.pc := io.i_raise.pc
        r_reg.cause := io.i_raise.cause
        r_reg.info := io.i_raise.info
        switch (io.i_raise.src) {
          is (TRAPSRC.WFI)   {
            r_reg.state := STATE.WFI
            r_reg.pc := io.i_raise.pc + 4.U
          }
          is (TRAPSRC.EXC)   {
            r_reg.state := STATE.EXC
          }
          is (TRAPSRC.MRET)  {
            r_reg.state := STATE.MRET
          }
        }
        if (!useChamp) {
          switch (io.i_raise.src) {
            is (TRAPSRC.MRET)  {
              r_reg.state := STATE.MRET
            }
          }
        }
      }.elsewhen(io.i_br) {
        r_reg.state := STATE.RUN
      }
    }

    // ------------------------------
    //             IRQ
    // ------------------------------
    is (STATE.IRQ) {
      when (io.i_empty) {
        r_reg.state := STATE.RUN
      }.elsewhen(io.i_wb.valid) {
        r_reg.pc := io.i_wb.pc
      }

      when (io.b_clint.en) {
        r_reg.cause := io.b_clint.ecause        
      }
    }

    // ------------------------------
    //              WFI
    // ------------------------------
    is (STATE.WFI) {
      when (io.b_clint.en) {
        r_reg.state := STATE.IRQ
        r_reg.cause := io.b_clint.ecause
        r_reg.info := 0.U      
      }
    }

    // ------------------------------
    //       EXCEPTION & RETURNS
    // ------------------------------
    is (STATE.EXC, STATE.MRET) {
      when (io.b_clint.en) {
        r_reg.state := STATE.IRQ
        r_reg.cause := io.b_clint.ecause
        r_reg.info := 0.U
      }.otherwise {
        r_reg.state := STATE.RUN
      }
    }
  }

  // ------------------------------
  //            FLUSH
  // ------------------------------
  when (~w_hart_valid) {
    r_reg.state := STATE.RUN
  }

  // ******************************
  //        CSR TRAP & LOCK
  // ******************************
  io.o_trap.gen := false.B
  io.o_trap.pc := r_reg.pc
  io.o_trap.cause := r_reg.cause
  io.o_trap.info := r_reg.info

  if (useChamp) {
    io.o_lock := (r_reg.state === STATE.STOP) | (r_reg.state === STATE.IRQ) | (r_reg.state === STATE.WFI)

    io.o_trap.valid :=  ((r_reg.state === STATE.IRQ) & io.i_empty) | (r_reg.state === STATE.EXC)
    io.o_trap.src := DontCare
    switch(r_reg.state) {
      is (STATE.IRQ)    {io.o_trap.src := TRAPSRC.IRQ}
      is (STATE.EXC)    {io.o_trap.src := TRAPSRC.EXC}
    }
  } else {
    io.o_lock := (r_reg.state === STATE.STOP) | (r_reg.state === STATE.IRQ) | (r_reg.state === STATE.WFI)

    io.o_trap.valid := ((r_reg.state === STATE.IRQ) & io.i_empty) | (r_reg.state === STATE.EXC) | (r_reg.state === STATE.MRET)
    io.o_trap.src := DontCare
    switch(r_reg.state) {
      is (STATE.IRQ)    {io.o_trap.src := TRAPSRC.IRQ}
      is (STATE.EXC)    {io.o_trap.src := TRAPSRC.EXC}
      is (STATE.MRET)   {io.o_trap.src := TRAPSRC.MRET}
    }
  }  
  
  // ******************************
  //            FIELD
  // ******************************
  if (useField) {
    io.b_hart.get.free := (r_reg.state === STATE.RUN)
  } 
}

object Fsm extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Fsm(1, true, 32, 32, 32), args)
}
