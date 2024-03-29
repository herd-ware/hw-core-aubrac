/*
 * File: priv.scala                                                            *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-21 10:15:26 am                                       *
 * Modified By: Mathieu Escouteloup                                            *
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
import herd.common.isa.riscv.CSR._
import herd.common.isa.priv._
import herd.common.isa.priv.CSR._
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

    val i_hpm = Input(Vec(p.nHart, Vec(32, UInt(64.W))))

    val o_decoder = Output(Vec(p.nHart, new CsrDecoderBus()))
    val b_clint = Vec(p.nHart, Flipped(new ClintIO(p.nDataBit)))

    val o_dbg = if(p.debug) Some(Output(Vec(p.nHart, new CsrDbgBus(p.nDataBit, false, 0)))) else None
  })

  // ******************************
  //             INIT
  // ******************************
  val init_csr = Wire(Vec(p.nHart, new CsrBus(p.nDataBit, false, 0)))

  for (h <- 0 until p.nHart) {
    init_csr(h).riscv                   := DontCare

    init_csr(h).priv.get.mvendorid      := 0.U
    init_csr(h).priv.get.marchid        := 0.U
    init_csr(h).priv.get.mimpid         := 0.U
    init_csr(h).priv.get.mhartid        := h.U

    init_csr(h).priv.get.cp             := PRIV.M
    init_csr(h).priv.get.xstatus.mie    := 0.B
    init_csr(h).priv.get.xstatus.mpp    := 0.B
    init_csr(h).priv.get.xstatus.mpie   := 0.U
    init_csr(h).priv.get.xisa.a         := p.useExtA.B
    init_csr(h).priv.get.xisa.b         := p.useExtB.B
    init_csr(h).priv.get.xisa.i         := true.B
    init_csr(h).priv.get.xisa.m         := p.useExtM.B
    if (p.nDataBit == 64) {
      init_csr(h).priv.get.xisa.mxl     := 2.U 
    } else {
      init_csr(h).priv.get.xisa.mxl     := 1.U 
    }
    init_csr(h).priv.get.medeleg        := 0.U.asTypeOf(init_csr(h).priv.get.medeleg)
    init_csr(h).priv.get.mideleg        := 0.U.asTypeOf(init_csr(h).priv.get.mideleg)
    init_csr(h).priv.get.mie.msie       := 0.B
    init_csr(h).priv.get.mie.mtie       := 0.B
    init_csr(h).priv.get.mie.meie       := 0.B
    init_csr(h).priv.get.mtvec          := DontCare

    init_csr(h).priv.get.mscratch       := DontCare
    init_csr(h).priv.get.mepc           := DontCare
    init_csr(h).priv.get.mcause         := DontCare
    init_csr(h).priv.get.mtval          := DontCare
    init_csr(h).priv.get.mip            := DontCare
    init_csr(h).priv.get.menvcfg.fiom   := DontCare
    init_csr(h).priv.get.menvcfg.cbie   := CBIE.INV
    init_csr(h).priv.get.menvcfg.cbcfe  := true.B
    init_csr(h).priv.get.menvcfg.cbze   := true.B
  }

  val r_csr = RegInit(init_csr)

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
        is (MSTATUS.U) {
          r_csr(h).priv.get.xstatus.mie := w_wdata(h)(3)
          r_csr(h).priv.get.xstatus.mpp := w_wdata(h)(8)        
        }
//        is (MSTATUSH.U)  {
//          if (p.nDataBit == 32) {
//            
//          }          
//        }
        is (MIE.U) {
          r_csr(h).priv.get.mie.msie := w_wdata(h)(0)
          r_csr(h).priv.get.mie.mtie := w_wdata(h)(4)
          r_csr(h).priv.get.mie.meie := w_wdata(h)(8)
        }
        is (MTVEC.U) {
          r_csr(h).priv.get.mtvec.mode  := 0.U
          r_csr(h).priv.get.mtvec.base  := w_wdata(h)(p.nDataBit - 1, 2)
        }
        is (MSCRATCH.U) {
          r_csr(h).priv.get.mscratch := w_wdata(h)
        }
        is (MEPC.U) {
          r_csr(h).priv.get.mepc := Cat(w_wdata(h)(p.nDataBit - 1, 2), 0.U(2.W))
        }
        is (MTVAL.U) {
          r_csr(h).priv.get.mtval := w_wdata(h)
        }
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
        r_csr(h).priv.get.mcause.code := io.i_trap(h).cause
        r_csr(h).priv.get.mcause.irq := (io.i_trap(h).src === TRAPSRC.IRQ)
        r_csr(h).priv.get.mtval := io.i_trap(h).info

        io.o_br_trap(h).valid := true.B
        io.o_br_trap(h).addr := r_csr(h).priv.get.mtvec.addr
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
      w_ie(h)(b) := r_csr(h).priv.get.xstatus.mie & r_csr(h).priv.get.mie.toUInt(b)    
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
        is (CYCLE.U)          {io.b_read(h).data := io.i_hpm(h)(0)((p.nDataBit - 1),0)}
        is (TIME.U)           {io.b_read(h).data := io.i_hpm(h)(1)((p.nDataBit - 1),0)}
        is (INSTRET.U)        {io.b_read(h).data := io.i_hpm(h)(2)((p.nDataBit - 1),0)}
        is (HPMCOUNTER3.U)    {io.b_read(h).data := io.i_hpm(h)(3)((p.nDataBit - 1),0)}
        is (HPMCOUNTER4.U)    {io.b_read(h).data := io.i_hpm(h)(4)((p.nDataBit - 1),0)}
        is (HPMCOUNTER5.U)    {io.b_read(h).data := io.i_hpm(h)(5)((p.nDataBit - 1),0)}
        is (HPMCOUNTER6.U)    {io.b_read(h).data := io.i_hpm(h)(6)((p.nDataBit - 1),0)}
        is (HPMCOUNTER7.U)    {io.b_read(h).data := io.i_hpm(h)(7)((p.nDataBit - 1),0)}
        is (HPMCOUNTER8.U)    {io.b_read(h).data := io.i_hpm(h)(8)((p.nDataBit - 1),0)}
        is (HPMCOUNTER9.U)    {io.b_read(h).data := io.i_hpm(h)(9)((p.nDataBit - 1),0)}
        is (HPMCOUNTER10.U)   {io.b_read(h).data := io.i_hpm(h)(10)((p.nDataBit - 1),0)}
        is (HPMCOUNTER11.U)   {io.b_read(h).data := io.i_hpm(h)(11)((p.nDataBit - 1),0)}
        is (HPMCOUNTER12.U)   {io.b_read(h).data := io.i_hpm(h)(12)((p.nDataBit - 1),0)}
        is (HPMCOUNTER13.U)   {io.b_read(h).data := io.i_hpm(h)(13)((p.nDataBit - 1),0)}
        is (HPMCOUNTER14.U)   {io.b_read(h).data := io.i_hpm(h)(14)((p.nDataBit - 1),0)}
        is (HPMCOUNTER15.U)   {io.b_read(h).data := io.i_hpm(h)(15)((p.nDataBit - 1),0)}
        is (HPMCOUNTER16.U)   {io.b_read(h).data := io.i_hpm(h)(16)((p.nDataBit - 1),0)}
        is (HPMCOUNTER17.U)   {io.b_read(h).data := io.i_hpm(h)(17)((p.nDataBit - 1),0)}
        is (HPMCOUNTER18.U)   {io.b_read(h).data := io.i_hpm(h)(18)((p.nDataBit - 1),0)}
        is (HPMCOUNTER19.U)   {io.b_read(h).data := io.i_hpm(h)(19)((p.nDataBit - 1),0)}
        is (HPMCOUNTER20.U)   {io.b_read(h).data := io.i_hpm(h)(20)((p.nDataBit - 1),0)}
        is (HPMCOUNTER21.U)   {io.b_read(h).data := io.i_hpm(h)(21)((p.nDataBit - 1),0)}
        is (HPMCOUNTER22.U)   {io.b_read(h).data := io.i_hpm(h)(22)((p.nDataBit - 1),0)}
        is (HPMCOUNTER23.U)   {io.b_read(h).data := io.i_hpm(h)(23)((p.nDataBit - 1),0)}
        is (HPMCOUNTER24.U)   {io.b_read(h).data := io.i_hpm(h)(24)((p.nDataBit - 1),0)}
        is (HPMCOUNTER25.U)   {io.b_read(h).data := io.i_hpm(h)(25)((p.nDataBit - 1),0)}
        is (HPMCOUNTER26.U)   {io.b_read(h).data := io.i_hpm(h)(26)((p.nDataBit - 1),0)}
        is (HPMCOUNTER27.U)   {io.b_read(h).data := io.i_hpm(h)(27)((p.nDataBit - 1),0)}
        is (HPMCOUNTER28.U)   {io.b_read(h).data := io.i_hpm(h)(28)((p.nDataBit - 1),0)}
        is (HPMCOUNTER29.U)   {io.b_read(h).data := io.i_hpm(h)(29)((p.nDataBit - 1),0)}
        is (HPMCOUNTER30.U)   {io.b_read(h).data := io.i_hpm(h)(30)((p.nDataBit - 1),0)}
        is (HPMCOUNTER31.U)   {io.b_read(h).data := io.i_hpm(h)(31)((p.nDataBit - 1),0)}

        is (MVENDORID.U)      {io.b_read(h).data := r_csr(0).priv.get.mvendorid}
        is (MARCHID.U)        {io.b_read(h).data := r_csr(0).priv.get.marchid}
        is (MIMPID.U)         {io.b_read(h).data := r_csr(0).priv.get.mimpid}
        is (MHARTID.U)        {io.b_read(h).data := r_csr(0).priv.get.mhartid}

        is (MSTATUS.U)        {io.b_read(h).data := r_csr(h).priv.get.xstatus.m}
        is (MISA.U)           {io.b_read(h).data := r_csr(h).priv.get.xisa.toUInt}
        is (MTVEC.U)          {io.b_read(h).data := r_csr(h).priv.get.mtvec.toUInt}
        is (MIE.U)            {io.b_read(h).data := r_csr(h).priv.get.mie.toUInt}

        is (MSCRATCH.U)       {io.b_read(h).data := r_csr(h).priv.get.mscratch}
        is (MEPC.U)           {io.b_read(h).data := r_csr(h).priv.get.mepc}
        is (MCAUSE.U)         {io.b_read(h).data := r_csr(h).priv.get.mcause.toUInt}
        is (MTVAL.U)          {io.b_read(h).data := r_csr(h).priv.get.mtval}
        is (MIP.U)            {io.b_read(h).data := io.b_clint(h).ip.asUInt}
      }

      if (p.nDataBit == 32) {
        switch (io.b_read(h).addr) {
          is (MSTATUSH.U)       {io.b_read(h).data := 0.U}

          is (CYCLEH.U)         {io.b_read(h).data := io.i_hpm(h)(0)(63,32)}
          is (TIMEH.U)          {io.b_read(h).data := io.i_hpm(h)(1)(63,32)}
          is (INSTRETH.U)       {io.b_read(h).data := io.i_hpm(h)(2)(63,32)}
          is (HPMCOUNTER3H.U)   {io.b_read(h).data := io.i_hpm(h)(3)(63,32)}
          is (HPMCOUNTER4H.U)   {io.b_read(h).data := io.i_hpm(h)(4)(63,32)}
          is (HPMCOUNTER5H.U)   {io.b_read(h).data := io.i_hpm(h)(5)(63,32)}
          is (HPMCOUNTER6H.U)   {io.b_read(h).data := io.i_hpm(h)(6)(63,32)}
          is (HPMCOUNTER7H.U)   {io.b_read(h).data := io.i_hpm(h)(7)(63,32)}
          is (HPMCOUNTER8H.U)   {io.b_read(h).data := io.i_hpm(h)(8)(63,32)}
          is (HPMCOUNTER9H.U)   {io.b_read(h).data := io.i_hpm(h)(9)(63,32)}
          is (HPMCOUNTER10H.U)  {io.b_read(h).data := io.i_hpm(h)(10)(63,32)}
          is (HPMCOUNTER11H.U)  {io.b_read(h).data := io.i_hpm(h)(11)(63,32)}
          is (HPMCOUNTER12H.U)  {io.b_read(h).data := io.i_hpm(h)(12)(63,32)}
          is (HPMCOUNTER13H.U)  {io.b_read(h).data := io.i_hpm(h)(13)(63,32)}
          is (HPMCOUNTER14H.U)  {io.b_read(h).data := io.i_hpm(h)(14)(63,32)}
          is (HPMCOUNTER15H.U)  {io.b_read(h).data := io.i_hpm(h)(15)(63,32)}
          is (HPMCOUNTER16H.U)  {io.b_read(h).data := io.i_hpm(h)(16)(63,32)}
          is (HPMCOUNTER17H.U)  {io.b_read(h).data := io.i_hpm(h)(17)(63,32)}
          is (HPMCOUNTER18H.U)  {io.b_read(h).data := io.i_hpm(h)(18)(63,32)}
          is (HPMCOUNTER19H.U)  {io.b_read(h).data := io.i_hpm(h)(19)(63,32)}
          is (HPMCOUNTER20H.U)  {io.b_read(h).data := io.i_hpm(h)(20)(63,32)}
          is (HPMCOUNTER21H.U)  {io.b_read(h).data := io.i_hpm(h)(21)(63,32)}
          is (HPMCOUNTER22H.U)  {io.b_read(h).data := io.i_hpm(h)(22)(63,32)}
          is (HPMCOUNTER23H.U)  {io.b_read(h).data := io.i_hpm(h)(23)(63,32)}
          is (HPMCOUNTER24H.U)  {io.b_read(h).data := io.i_hpm(h)(24)(63,32)}
          is (HPMCOUNTER25H.U)  {io.b_read(h).data := io.i_hpm(h)(25)(63,32)}
          is (HPMCOUNTER26H.U)  {io.b_read(h).data := io.i_hpm(h)(26)(63,32)}
          is (HPMCOUNTER27H.U)  {io.b_read(h).data := io.i_hpm(h)(27)(63,32)}
          is (HPMCOUNTER28H.U)  {io.b_read(h).data := io.i_hpm(h)(28)(63,32)}
          is (HPMCOUNTER29H.U)  {io.b_read(h).data := io.i_hpm(h)(29)(63,32)}
          is (HPMCOUNTER30H.U)  {io.b_read(h).data := io.i_hpm(h)(30)(63,32)}
          is (HPMCOUNTER31H.U)  {io.b_read(h).data := io.i_hpm(h)(31)(63,32)}
        }
      }
    }
  }

  // ******************************
  //            DECODER
  // ******************************
  for (h <- 0 until p.nHart) {
    io.o_decoder(h).cbie := r_csr(h).priv.get.menvcfg.cbie
    io.o_decoder(h).cbcfe := r_csr(h).priv.get.menvcfg.cbcfe
    io.o_decoder(h).cbze := r_csr(h).priv.get.menvcfg.cbze  
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    for (h <- 0 until p.nHart) {      
      io.o_dbg.get(h).riscv.cycle         := io.i_hpm(h)(0) 
      io.o_dbg.get(h).riscv.time          := io.i_hpm(h)(1) 
      io.o_dbg.get(h).riscv.instret       := io.i_hpm(h)(2) 
      io.o_dbg.get(h).riscv.hpmcounter3   := io.i_hpm(h)(3)
      io.o_dbg.get(h).riscv.hpmcounter4   := io.i_hpm(h)(4)
      io.o_dbg.get(h).riscv.hpmcounter5   := io.i_hpm(h)(5)
      io.o_dbg.get(h).riscv.hpmcounter6   := io.i_hpm(h)(6)
      io.o_dbg.get(h).riscv.hpmcounter7   := io.i_hpm(h)(7)
      io.o_dbg.get(h).riscv.hpmcounter8   := io.i_hpm(h)(8)
      io.o_dbg.get(h).riscv.hpmcounter9   := io.i_hpm(h)(9)
      io.o_dbg.get(h).riscv.hpmcounter10  := io.i_hpm(h)(10)
      io.o_dbg.get(h).riscv.hpmcounter11  := io.i_hpm(h)(11)
      io.o_dbg.get(h).riscv.hpmcounter12  := io.i_hpm(h)(12)
      io.o_dbg.get(h).riscv.hpmcounter13  := io.i_hpm(h)(13)
      io.o_dbg.get(h).riscv.hpmcounter14  := io.i_hpm(h)(14)
      io.o_dbg.get(h).riscv.hpmcounter15  := io.i_hpm(h)(15)
      io.o_dbg.get(h).riscv.hpmcounter16  := io.i_hpm(h)(16)
      io.o_dbg.get(h).riscv.hpmcounter17  := io.i_hpm(h)(17)
      io.o_dbg.get(h).riscv.hpmcounter18  := io.i_hpm(h)(18)
      io.o_dbg.get(h).riscv.hpmcounter19  := io.i_hpm(h)(19)
      io.o_dbg.get(h).riscv.hpmcounter20  := io.i_hpm(h)(20)
      io.o_dbg.get(h).riscv.hpmcounter21  := io.i_hpm(h)(21)
      io.o_dbg.get(h).riscv.hpmcounter22  := io.i_hpm(h)(22)
      io.o_dbg.get(h).riscv.hpmcounter23  := io.i_hpm(h)(23)
      io.o_dbg.get(h).riscv.hpmcounter24  := io.i_hpm(h)(24)
      io.o_dbg.get(h).riscv.hpmcounter25  := io.i_hpm(h)(25)
      io.o_dbg.get(h).riscv.hpmcounter26  := io.i_hpm(h)(26)
      io.o_dbg.get(h).riscv.hpmcounter27  := io.i_hpm(h)(27)
      io.o_dbg.get(h).riscv.hpmcounter28  := io.i_hpm(h)(28)
      io.o_dbg.get(h).riscv.hpmcounter29  := io.i_hpm(h)(29)
      io.o_dbg.get(h).riscv.hpmcounter30  := io.i_hpm(h)(30)
      io.o_dbg.get(h).riscv.hpmcounter31  := io.i_hpm(h)(31)

      io.o_dbg.get(h).priv.get.mvendorid  := r_csr(h).priv.get.mvendorid
      io.o_dbg.get(h).priv.get.marchid    := r_csr(h).priv.get.marchid
      io.o_dbg.get(h).priv.get.mimpid     := r_csr(h).priv.get.mimpid
      io.o_dbg.get(h).priv.get.mhartid    := r_csr(h).priv.get.mhartid

      io.o_dbg.get(h).priv.get.cp         := r_csr(h).priv.get.cp
      io.o_dbg.get(h).priv.get.mstatus    := r_csr(h).priv.get.xstatus.m
      io.o_dbg.get(h).priv.get.misa       := r_csr(h).priv.get.xisa.toUInt
      io.o_dbg.get(h).priv.get.medeleg    := r_csr(h).priv.get.medeleg.asUInt()
      io.o_dbg.get(h).priv.get.mideleg    := r_csr(h).priv.get.mideleg.asUInt()
      io.o_dbg.get(h).priv.get.mie        := r_csr(h).priv.get.mie.toUInt
      io.o_dbg.get(h).priv.get.mtvec      := r_csr(h).priv.get.mtvec.toUInt

      io.o_dbg.get(h).priv.get.mscratch   := r_csr(h).priv.get.mscratch
      io.o_dbg.get(h).priv.get.mepc       := r_csr(h).priv.get.mepc
      io.o_dbg.get(h).priv.get.mcause     := r_csr(h).priv.get.mcause.toUInt
      io.o_dbg.get(h).priv.get.mtval      := r_csr(h).priv.get.mtval
      io.o_dbg.get(h).priv.get.mip        := io.b_clint(h).ip

      io.o_dbg.get(h).priv.get.menvcfg    := r_csr(h).priv.get.menvcfg.toUInt
    }
  }
}

object Priv extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Priv(CsrConfigBase), args)
}