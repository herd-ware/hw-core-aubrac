/*
 * File: priv.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-28 10:38:51 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.back.csr

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.isa.riscv.{CBIE}
import herd.common.isa.priv._
import herd.common.isa.priv.CSR._
import herd.common.isa.count.CSR._
import herd.common.isa.count.{CsrBus => StatBus}
import herd.common.isa.custom.CSR._
import herd.core.aubrac.common._
import herd.io.core.clint.{ClintIO}


class Priv(p: CsrParams) extends Module {
  require(!p.useChamp, "CHAMP ISA support must not be enable for this version of CSR.")

  val io = IO(new Bundle {
    val b_read = Vec(p.nHart, new CsrReadIO(p.nDataBit))
    val b_write = Vec(p.nHart, new CsrWriteIO(p.nDataBit))

    val i_trap = Input(Vec(p.nHart, new TrapBus(p.nAddrBit, p.nDataBit)))
    val o_ie = Output(Vec(p.nHart, UInt(p.nDataBit.W)))
    val o_br_trap = Output(Vec(p.nHart, new BranchBus(p.nAddrBit)))

    val i_stat  = Input(Vec(p.nHart, new StatBus()))
    val o_decoder = Output(Vec(p.nHart, new CsrDecoderBus()))
    val b_mem = Vec(p.nHart, new CsrMemIO())
    val b_clint = Vec(p.nHart, Flipped(new ClintIO(p.nDataBit)))

    val o_dbg = if(p.debug) Some(Output(Vec(p.nHart, new CsrBus(p.nDataBit, false)))) else None
  })

  // ******************************
  //             INIT
  // ******************************
  val init_csr = Wire(Vec(p.nHart, new CsrBus(p.nDataBit, false)))

  for (h <- 0 until p.nHart) {
    init_csr(h).riscv             := DontCare

    init_csr(h).priv.get.mhartid  := h.U

    init_csr(h).priv.get.mstatus  := 0.U
    init_csr(h).priv.get.medeleg  := 0.U
    init_csr(h).priv.get.mideleg  := 0.U
    init_csr(h).priv.get.mie      := 0.U
    init_csr(h).priv.get.mtvec    := DontCare

    init_csr(h).priv.get.mscratch := DontCare
    init_csr(h).priv.get.mepc     := DontCare
    init_csr(h).priv.get.mcause   := DontCare
    init_csr(h).priv.get.mtval    := DontCare
    init_csr(h).priv.get.mip      := DontCare
    init_csr(h).priv.get.menvcfg  := DontCare

    init_csr(h).cnt.cycle         := 0.U
    init_csr(h).cnt.time          := 0.U
    init_csr(h).cnt.instret       := 0.U
    init_csr(h).cnt.alu           := 0.U
    init_csr(h).cnt.ld            := 0.U
    init_csr(h).cnt.st            := 0.U
    init_csr(h).cnt.br            := 0.U
    init_csr(h).cnt.mispred       := 0.U
    init_csr(h).cnt.l1imiss       := 0.U
    init_csr(h).cnt.l1dmiss       := 0.U
    init_csr(h).cnt.l2miss        := 0.U
  }

  val r_csr = RegInit(init_csr)

  // ******************************
  //            COUNTERS
  // ******************************
  for (h <- 0 until p.nHart) {
    r_csr(h).cnt.cycle    := r_csr(h).cnt.cycle + 1.U
    r_csr(h).cnt.time     := r_csr(h).cnt.time + 1.U
    r_csr(h).cnt.instret  := r_csr(h).cnt.instret + io.i_stat(h).instret
    r_csr(h).cnt.alu      := r_csr(h).cnt.alu + io.i_stat(h).alu
    r_csr(h).cnt.ld       := r_csr(h).cnt.ld + io.i_stat(h).ld
    r_csr(h).cnt.st       := r_csr(h).cnt.st + io.i_stat(h).st
    r_csr(h).cnt.br       := r_csr(h).cnt.br + io.i_stat(h).br
    r_csr(h).cnt.mispred  := r_csr(h).cnt.mispred + io.i_stat(h).mispred
    r_csr(h).cnt.l1imiss  := r_csr(h).cnt.l1imiss + io.b_mem(h).l1imiss
    r_csr(h).cnt.l1dmiss  := r_csr(h).cnt.l1dmiss + io.b_mem(h).l1dmiss
    r_csr(h).cnt.l2miss   := r_csr(h).cnt.l2miss + io.b_mem(h).l2miss
  }

  // ******************************
  //             WRITE
  // ******************************
  // ------------------------------
  //             DATA
  // ------------------------------
  val w_wdata = Wire(Vec(p.nHart, UInt(p.nDataBit.W)))

  for (h <- 0 until p.nHart) {
    w_wdata(h) := 0.U

    switch (io.b_write(h).uop) {
      is (UOP.W) {w_wdata(h) := io.b_write(h).mask}
      is (UOP.S) {w_wdata(h) := io.b_write(h).mask | io.b_write(h).data}
      is (UOP.C) {w_wdata(h) := (io.b_write(h).mask ^ io.b_write(h).data) & io.b_write(h).data}
    }
  }

  // ------------------------------
  //            REGISTER
  // ------------------------------
  for (h <- 0 until p.nHart) {
    when (io.b_write(h).valid) {
      switch(io.b_write(h).addr) {         
        is (MSTATUS.U)  {
          if (p.nDataBit == 64) {
            r_csr(h).priv.get.mstatus := w_wdata(h)
          } else {
            r_csr(h).priv.get.mstatus := Cat(r_csr(h).priv.get.mstatus(63, 32), w_wdata(h))
          }          
        }
        is (MSTATUSH.U)  {
          if (p.nDataBit == 32) {
            r_csr(h).priv.get.mstatus := Cat(w_wdata(h), r_csr(h).priv.get.mstatus(31, 0))
          }          
        }
        is (MIE.U)      {r_csr(h).priv.get.mie := w_wdata(h)}
        is (MTVEC.U)    {r_csr(h).priv.get.mtvec := Cat(w_wdata(h)(p.nDataBit - 1, 2), 0.U(2.W))}
        is (MSCRATCH.U) {r_csr(h).priv.get.mscratch := w_wdata(h)}
        is (MEPC.U)     {r_csr(h).priv.get.mepc := Cat(w_wdata(h)(p.nDataBit - 1, 2), 0.U(2.W))}
        is (MTVAL.U)    {r_csr(h).priv.get.mtval := w_wdata(h)}
      }     
    }
  }

  // ******************************
  //             TRAP
  // ******************************
  for (h <- 0 until p.nHart) {
    io.o_br_trap(h) := DontCare
    io.o_br_trap(h).valid := false.B

    when (io.i_trap(h).valid) {
      when ((io.i_trap(h).src === TRAPSRC.IRQ) | (io.i_trap(h).src === TRAPSRC.EXC)) {
        r_csr(h).priv.get.mepc := io.i_trap(h).pc
        r_csr(h).priv.get.mcause := Cat((io.i_trap(h).src === TRAPSRC.IRQ), io.i_trap(h).cause)
        r_csr(h).priv.get.mtval := io.i_trap(h).info

        io.o_br_trap(h).valid := true.B
        io.o_br_trap(h).addr := r_csr(h).priv.get.mtvec
      }.elsewhen(io.i_trap(h).src === TRAPSRC.MRET) {
        io.o_br_trap(h).valid := true.B
        io.o_br_trap(h).addr := r_csr(h).priv.get.mepc
      } 
    }
  }

  val w_ie = Wire(Vec(p.nHart, Vec(p.nDataBit, Bool())))

  for (h <- 0 until p.nHart) {
    // Interrupt reset
    io.b_clint(h).ir := 0.U
    when (io.b_write(h).valid & (io.b_write(h).addr === MIP.U)) {
      io.b_clint(h).ir := ~w_wdata(h)
    }

    // Interrupt enable
    for (b <- 0 until p.nDataBit) {
      w_ie(h)(b) := r_csr(h).priv.get.mstatus(3) & r_csr(h).priv.get.mie(b)    
    }
    io.b_clint(h).ie := w_ie(h).asUInt
    io.o_ie(h) := w_ie(h).asUInt
  }

  // ******************************
  //            READ
  // ******************************
  for (h <- 0 until p.nHart) {
    io.b_read(h).ready := ~(io.b_write(h).valid & (io.b_read(h).addr === io.b_write(h).addr))
    io.b_read(h).data := 0.U

    when (io.b_read(h).valid) {
      switch (io.b_read(h).addr) {
        is (MHARTID.U)    {io.b_read(h).data := r_csr(0).priv.get.mhartid}

        is (MSTATUS.U)    {io.b_read(h).data := r_csr(h).priv.get.mstatus((p.nDataBit - 1),0)}
        is (MTVEC.U)      {io.b_read(h).data := r_csr(h).priv.get.mtvec}
        is (MIE.U)        {io.b_read(h).data := r_csr(h).priv.get.mie}

        is (MSCRATCH.U)   {io.b_read(h).data := r_csr(h).priv.get.mscratch}
        is (MEPC.U)       {io.b_read(h).data := r_csr(h).priv.get.mepc}
        is (MCAUSE.U)     {io.b_read(h).data := r_csr(h).priv.get.mcause}
        is (MTVAL.U)      {io.b_read(h).data := r_csr(h).priv.get.mtval}
        is (MIP.U)        {io.b_read(h).data := io.b_clint(h).ip}

        is (CYCLE.U)      {io.b_read(h).data := r_csr(0).cnt.cycle((p.nDataBit - 1),0)}
        is (TIME.U)       {io.b_read(h).data := r_csr(0).cnt.time((p.nDataBit - 1),0)}
        is (INSTRET.U)    {io.b_read(h).data := r_csr(h).cnt.instret((p.nDataBit - 1),0)}
        is (BR.U)         {io.b_read(h).data := r_csr(h).cnt.br((p.nDataBit - 1),0)}
        is (MISPRED.U)    {io.b_read(h).data := r_csr(h).cnt.mispred((p.nDataBit - 1),0)}
        is (L1IMISS.U)    {io.b_read(h).data := r_csr(h).cnt.l1imiss((p.nDataBit - 1),0)}
        is (L1DMISS.U)    {io.b_read(h).data := r_csr(h).cnt.l1dmiss((p.nDataBit - 1),0)}
        is (L2MISS.U)     {io.b_read(h).data := r_csr(h).cnt.l2miss((p.nDataBit - 1),0)}
      }

      if (p.nDataBit == 32) {
        switch (io.b_read(h).addr) {
          is (MSTATUSH.U)   {io.b_read(h).data := r_csr(h).priv.get.mstatus(63,32)}

          is (CYCLEH.U)     {io.b_read(h).data := r_csr(0).cnt.cycle(63,32)}
          is (TIMEH.U)      {io.b_read(h).data := r_csr(0).cnt.time(63,32)}
          is (INSTRETH.U)   {io.b_read(h).data := r_csr(h).cnt.instret(63,32)}
          is (BRH.U)        {io.b_read(h).data := r_csr(h).cnt.br(63,32)}
          is (MISPREDH.U)   {io.b_read(h).data := r_csr(h).cnt.mispred(63,32)}
          is (L1IMISSH.U)   {io.b_read(h).data := r_csr(h).cnt.l1imiss(63,32)}
          is (L1DMISSH.U)   {io.b_read(h).data := r_csr(h).cnt.l1dmiss(63,32)}
          is (L2MISSH.U)    {io.b_read(h).data := r_csr(h).cnt.l2miss(63,32)}
        }
      }
    }
  }

  // ******************************
  //            DECODER
  // ******************************
  for (h <- 0 until p.nHart) {
    io.o_decoder(h).cbie := CBIE.INV
    io.o_decoder(h).cbcfe := true.B
    io.o_decoder(h).cbze := true.B    
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    for (h <- 0 until p.nHart) {
      io.o_dbg.get(h) := r_csr(h)
      
      io.o_dbg.get(h).riscv.cycle   := r_csr(0).cnt.cycle
      io.o_dbg.get(h).riscv.time    := r_csr(0).cnt.time
      io.o_dbg.get(h).riscv.instret := r_csr(h).cnt.instret
      io.o_dbg.get(h).cnt.cycle     := r_csr(0).cnt.cycle
      io.o_dbg.get(h).cnt.time      := r_csr(0).cnt.time

      dontTouch(r_csr(h).cnt)
    }
  }
}

object Priv extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Priv(CsrConfigBase), args)
}