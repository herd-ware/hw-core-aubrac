/*
 * File: ceps.scala                                                            *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:10:22 pm                                       *
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

import herd.common.gen._
import herd.common.dome._
import herd.common.isa.count.{CsrBus => StatBus}
import herd.core.aubrac.common._
import herd.core.aubrac.dmu.{DmuReqCtrlBus, DmuReqDataBus, DmuCsrIO}
import herd.core.aubrac.dmu.{CODE => DMUCODE, OP => DMUOP}
import herd.io.core.clint.{ClintIO}

import herd.common.isa.base.{CBIE}
import herd.common.isa.ceps._
import herd.common.isa.ceps.CSR._
import herd.common.isa.count.CSR._
import herd.common.isa.custom.CSR._


class Ceps(p: CsrParams) extends Module {
  require(p.useCeps, "CEPS ISA support must be enable for this version of CSR.")

  val io = IO(new Bundle {
    val b_dome = Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit))
    val b_hart = Vec(p.nHart, new RsrcIO(p.nHart, p.nDome, p.nHart))

    val b_read = Vec(p.nHart, new CsrReadIO(p.nDataBit))
    val b_write = Vec(p.nHart, new CsrWriteIO(p.nDataBit))

    val i_trap = Input(Vec(p.nHart, new TrapBus(p.nAddrBit, p.nDataBit)))
    val o_ie = Output(Vec(p.nHart, UInt(p.nDataBit.W)))
    val b_trap = Vec(p.nHart, new GenRVIO(p, new DmuReqCtrlBus(p.debug, p.nAddrBit), new DmuReqDataBus(p.nDataBit)))
    val o_br_trap = Output(Vec(p.nHart, new BranchBus(p.nAddrBit)))

    val i_stat = Input(Vec(p.nHart, new StatBus()))
    val o_decoder = Output(Vec(p.nHart, new CsrDecoderBus()))
    val b_mem = Vec(p.nHart, new CsrMemIO())
    val b_dmu = Vec(p.nHart, Flipped(new DmuCsrIO(p.nAddrBit, p.nCepsTrapLvl)))
    val b_clint = Vec(p.nHart, Flipped(new ClintIO(p.nDataBit)))

    val o_dbg = if (p.debug) Some(Output(Vec(p.nHart, new CsrBus(p.nDataBit, true)))) else None
  })

  // ******************************
  //             INIT
  // ******************************
  val init_csr = Wire(Vec(p.nHart, new CsrBus(p.nDataBit, true)))
  init_csr := DontCare

  for (h <- 0 until p.nHart) {
    init_csr(h).ceps.get.cdc          := 0.U 
    init_csr(h).ceps.get.pdc          := 0.U 
    
    init_csr(h).ceps.get.hl0id        := h.U

    init_csr(h).ceps.get.tl0status    := DontCare
    init_csr(h).ceps.get.tl0tdc       := DontCare
    init_csr(h).ceps.get.tl0edeleg    := DontCare
    init_csr(h).ceps.get.tl0ideleg    := DontCare
    init_csr(h).ceps.get.tl0ie        := DontCare
    init_csr(h).ceps.get.tl0tvec      := DontCare

    init_csr(h).ceps.get.tl0scratch   := DontCare
    init_csr(h).ceps.get.tl0edc       := DontCare
    init_csr(h).ceps.get.tl0epc       := DontCare 
    init_csr(h).ceps.get.tl0cause     := DontCare
    init_csr(h).ceps.get.tl0tval      := DontCare
    init_csr(h).ceps.get.tl0ip        := DontCare

    init_csr(h).ceps.get.tl1status    := DontCare
    init_csr(h).ceps.get.tl1tdc       := DontCare
    init_csr(h).ceps.get.tl1edeleg    := DontCare
    init_csr(h).ceps.get.tl1ideleg    := DontCare
    init_csr(h).ceps.get.tl1ie        := DontCare
    init_csr(h).ceps.get.tl1tvec      := DontCare

    init_csr(h).ceps.get.tl1scratch   := DontCare
    init_csr(h).ceps.get.tl1edc       := DontCare
    init_csr(h).ceps.get.tl1epc       := DontCare
    init_csr(h).ceps.get.tl1cause     := DontCare
    init_csr(h).ceps.get.tl1tval      := DontCare 
    init_csr(h).ceps.get.tl1ip        := DontCare 

    init_csr(h).ceps.get.envcfg       := DontCare 

    init_csr(h).cnt.cycle             := 0.U
    init_csr(h).cnt.time              := 0.U
    init_csr(h).cnt.instret           := 0.U
    init_csr(h).cnt.alu               := 0.U
    init_csr(h).cnt.ld                := 0.U
    init_csr(h).cnt.st                := 0.U
    init_csr(h).cnt.br                := 0.U
    init_csr(h).cnt.mispred           := 0.U
    init_csr(h).cnt.l1imiss           := 0.U
    init_csr(h).cnt.l1dmiss           := 0.U
    init_csr(h).cnt.l2miss            := 0.U
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
      is (UOP.S) {w_wdata(h) := io.b_write(h).data | io.b_write(h).mask}
      is (UOP.C) {w_wdata(h) := (io.b_write(h).data ^ io.b_write(h).mask) & io.b_write(h).data}
    }
  }

  // ------------------------------
  //            REGISTER
  // ------------------------------
  for (h <- 0 until p.nHart) {
    when (io.b_write(h).valid) {   
      switch (io.b_write(h).addr) {
        is (ENVCFG.U)           {
          when (io.b_dome(io.b_hart(h).dome).cbo) {
            if (p.nDataBit > 32) {
              r_csr(h).ceps.get.envcfg := w_wdata(h)
            } else {
              r_csr(h).ceps.get.envcfg := Cat(r_csr(h).ceps.get.envcfg(63, 32), w_wdata(h))
            }            
          }
        }
      }

      if (p.nDataBit == 32) {
        switch (io.b_write(h).addr) {
          is (ENVCFGH.U)           {
            when (io.b_dome(io.b_hart(h).dome).cbo) {
              r_csr(h).ceps.get.envcfg := Cat(w_wdata(h), r_csr(h).ceps.get.envcfg(31, 0))
            }
          }
        }
      }

      if (p.nCepsTrapLvl > 0) {
        when (io.b_dome(io.b_hart(h).dome).tl(0)) {
          switch (io.b_write(h).addr) {
            is (TL0STATUS.U)    {r_csr(h).ceps.get.tl0status := w_wdata(h)}
            is (TL0TDC.U)       {r_csr(h).ceps.get.tl0tdc := w_wdata(h)}
            is (TL0EDELEG.U)    {r_csr(h).ceps.get.tl0edeleg := w_wdata(h)}
            is (TL0IDELEG.U)    {r_csr(h).ceps.get.tl0ideleg := w_wdata(h)}
            is (TL0IE.U)        {r_csr(h).ceps.get.tl0ie := w_wdata(h)}
            is (TL0TVEC.U)      {r_csr(h).ceps.get.tl0tvec := Cat(w_wdata(h)(p.nAddrBit - 1, 2), 0.U(2.W))}
            is (TL0SCRATCH.U)   {r_csr(h).ceps.get.tl0scratch := w_wdata(h)}
            is (TL0EDC.U)       {r_csr(h).ceps.get.tl0edc := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), w_wdata(h)(4, 0))}
            is (TL0EPC.U)       {r_csr(h).ceps.get.tl0epc := Cat(w_wdata(h)(p.nAddrBit - 1, 2), 0.U(2.W))
                                 r_csr(h).ceps.get.tl0edc := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), r_csr(h).ceps.get.tl0edc(4, 0))}                               
            is (TL0TVAL.U)      {r_csr(h).ceps.get.tl0tval := w_wdata(h)}
          }
        }  
      }      

      if (p.nCepsTrapLvl > 1) {
        when (io.b_dome(io.b_hart(h).dome).tl(1)) {
          switch (io.b_write(h).addr) {
            is (TL1STATUS.U)    {r_csr(h).ceps.get.tl1status := w_wdata(h)}
            is (TL1TDC.U)       {r_csr(h).ceps.get.tl1tdc := w_wdata(h)}
            is (TL1IE.U)        {r_csr(h).ceps.get.tl1ie := w_wdata(h)}
            is (TL1TVEC.U)      {r_csr(h).ceps.get.tl1tvec := Cat(w_wdata(h)(p.nAddrBit - 1, 2), 0.U(2.W))}
            is (TL1SCRATCH.U)   {r_csr(h).ceps.get.tl1scratch := w_wdata(h)}
            is (TL1EDC.U)       {r_csr(h).ceps.get.tl1edc := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), w_wdata(h)(4, 0))}
            is (TL1EPC.U)       {r_csr(h).ceps.get.tl1epc := Cat(w_wdata(h)(p.nAddrBit - 1, 2), 0.U(2.W))
                                 r_csr(h).ceps.get.tl1edc := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), r_csr(h).ceps.get.tl1edc(4, 0))}
            is (TL1TVAL.U)      {r_csr(h).ceps.get.tl1tval := w_wdata(h)}
          }
        }   
      }       
    }
  }

  // ******************************
  //             TRAP
  // ******************************
  // ------------------------------
  //          INFORMATIONS
  // ------------------------------
  val w_is_tl0handler = Wire(Vec(p.nHart, Bool()))
  val w_is_tl0deleg = Wire(Vec(p.nHart, Bool()))
  val w_is_tl1handler = Wire(Vec(p.nHart, Bool()))

  for (h <- 0 until p.nHart) {
    w_is_tl0handler(h) := io.b_dome(io.b_hart(h).dome).tl(0) & (r_csr(h).ceps.get.tl0tdc === io.b_dmu(h).cdc)
    if (p.nCepsTrapLvl > 1) w_is_tl1handler(h) := (r_csr(h).ceps.get.tl1tdc === io.b_dmu(h).cdc) else w_is_tl1handler(h) := false.B
    w_is_tl0deleg(h) := (io.i_trap(h).cause(p.nDataBit - 2, 5) === 0.U) & r_csr(h).ceps.get.tl0edeleg(io.i_trap(h).cause(4, 0)) & (r_csr(h).ceps.get.tl1tdc === io.b_dmu(h).cdc)
  }

  // ------------------------------
  //            UPDATE
  // ------------------------------
  for (h <- 0 until p.nHart) {
    when (io.i_trap(h).valid) {
      when ((io.i_trap(h).src === TRAPSRC.IRQ) | (io.i_trap(h).src === TRAPSRC.EXC)) {
        when (~w_is_tl0handler(h) & w_is_tl0deleg(h)) {
          r_csr(h).ceps.get.tl1edc := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), io.b_dmu(h).cdc)
          r_csr(h).ceps.get.tl1epc := io.i_trap(h).pc
          r_csr(h).ceps.get.tl1cause := Cat((io.i_trap(h).src === TRAPSRC.IRQ), io.i_trap(h).cause)
          r_csr(h).ceps.get.tl1tval := io.i_trap(h).info
        }.otherwise {
          r_csr(h).ceps.get.tl0edc := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), io.b_dmu(h).cdc)
          r_csr(h).ceps.get.tl0epc := io.i_trap(h).pc
          r_csr(h).ceps.get.tl0cause := Cat((io.i_trap(h).src === TRAPSRC.IRQ), io.i_trap(h).cause)
          r_csr(h).ceps.get.tl0tval := io.i_trap(h).info
        }
      } 
    } 

    if (p.nCepsTrapLvl > 0) {
      io.b_dmu(h).etl(0).sw := r_csr(h).ceps.get.tl0edc(p.nDataBit - 1)
      io.b_dmu(h).etl(0).dc := r_csr(h).ceps.get.tl0edc(4, 0)
      io.b_dmu(h).etl(0).pc := r_csr(h).ceps.get.tl0epc
    }

    if (p.nCepsTrapLvl > 0) {
      io.b_dmu(h).etl(1).sw := r_csr(h).ceps.get.tl1edc(p.nDataBit - 1)
      io.b_dmu(h).etl(1).dc := r_csr(h).ceps.get.tl1edc(4, 0)
      io.b_dmu(h).etl(1).pc := r_csr(h).ceps.get.tl1epc
    }
  }

  // ------------------------------
  //       ACTIVE TRAP LEVEL
  // ------------------------------
  val init_atl = Wire(Vec(p.nHart, Vec(p.nCepsTrapLvl, Bool())))

  for (h <- 0 until p.nHart) {
    for (tl <- 0 until p.nCepsTrapLvl) {
      init_atl(h)(tl) := false.B
    }
  }

  val r_atl = RegInit(init_atl)

  for (h <- 0 until p.nHart) {
    for (tl <- 0 until p.nCepsTrapLvl) {
      io.b_dmu(h).atl := r_atl(h)
      when (io.b_hart(h).flush) {
        r_atl(h)(tl) := false.B
      }
    }
  }

  // ------------------------------
  //        BRANCH & SWITCH
  // ------------------------------
  for (h <- 0 until p.nHart) {
    io.o_br_trap(h) := DontCare
    io.o_br_trap(h).valid := false.B

    io.b_trap(h) := DontCare
    io.b_trap(h).valid := false.B

    when (io.i_trap(h).valid) {
      when (w_is_tl0deleg(h) & w_is_tl1handler(h)) {
        io.o_br_trap(h).valid := true.B
        io.o_br_trap(h).addr := r_csr(h).ceps.get.tl1tvec
      }.elsewhen (w_is_tl0handler(h)) {
        io.o_br_trap(h).valid := true.B
        io.o_br_trap(h).addr := r_csr(h).ceps.get.tl0tvec          
      }.otherwise {
        io.b_trap(h).valid := true.B
        io.b_trap(h).ctrl.get.code := DMUCODE.TRAP
        io.b_trap(h).ctrl.get.op2 := DMUOP.IN
        io.b_trap(h).ctrl.get.op3 := DMUOP.X
        io.b_trap(h).ctrl.get.wb := false.B
        when (w_is_tl0deleg(h)) {
          r_atl(h)(1) := true.B
          io.b_trap(h).ctrl.get.dcs1 := r_csr(h).ceps.get.tl1tdc
          io.b_trap(h).data.get.s2 := r_csr(h).ceps.get.tl1tvec
        }.otherwise {
          r_atl(h)(0) := true.B
          io.b_trap(h).ctrl.get.dcs1 := r_csr(h).ceps.get.tl0tdc
          io.b_trap(h).data.get.s2 := r_csr(h).ceps.get.tl0tvec
        }
      }
    }
  }

  // ******************************
  //           INTERRUPT
  // ******************************
  // ------------------------------
  //            ENABLE
  // ------------------------------
  val w_ie = Wire(Vec(p.nHart, Vec(2, Vec(p.nDataBit, Bool()))))

  for (h <- 0 until p.nHart) {
    // Interrupt reset
    io.b_clint(h).ir := 0.U
    when (io.b_write(h).valid & (io.b_write(h).addr === TL0IP.U)) {
      io.b_clint(h).ir := ~w_wdata(h)
    }
    when (io.b_write(h).valid & (io.b_write(h).addr === TL1IP.U)) {
      io.b_clint(h).ir := ~w_wdata(h)
    }

    // Interrupt enable    
    for (b <- 0 until p.nDataBit) {
      w_ie(h)(0)(b) := r_csr(h).ceps.get.tl0status(0) & r_csr(h).ceps.get.tl0ie(b)
      w_ie(h)(1)(b) := r_csr(h).ceps.get.tl1status(1) & r_csr(h).ceps.get.tl1ie(b)     
    }

    io.b_clint(h).ie := w_ie(h)(0).asUInt | w_ie(h)(1).asUInt
    io.o_ie(h) := w_ie(h)(0).asUInt | w_ie(h)(1).asUInt
  }

  // ******************************
  //            READ
  // ******************************
  for (h <- 0 until p.nHart) {
    io.b_read(h).ready := ~(io.b_write(h).valid & (io.b_read(h).addr === io.b_write(h).addr))
    io.b_read(h).data := 0.U

    when (io.b_read(h).valid) {
      switch (io.b_read(h).addr) {
        is (CDC.U)            {io.b_read(h).data := io.b_dmu(h).cdc}
        is (PDC.U)            {io.b_read(h).data := io.b_dmu(h).pdc}

        is (HL0ID.U)          {io.b_read(h).data := r_csr(0).ceps.get.hl0id}
        is (ENVCFG.U)         {io.b_read(h).data := r_csr(h).ceps.get.envcfg((p.nDataBit - 1),0)}

        is (CYCLE.U)          {io.b_read(h).data := r_csr(0).cnt.cycle((p.nDataBit - 1),0)}
        is (TIME.U)           {io.b_read(h).data := r_csr(0).cnt.time((p.nDataBit - 1),0)}
        is (INSTRET.U)        {io.b_read(h).data := r_csr(h).cnt.instret((p.nDataBit - 1),0)}
        is (BR.U)             {io.b_read(h).data := r_csr(h).cnt.br((p.nDataBit - 1),0)}
        is (MISPRED.U)        {io.b_read(h).data := r_csr(h).cnt.mispred((p.nDataBit - 1),0)}
        is (L1IMISS.U)        {io.b_read(h).data := r_csr(h).cnt.l1imiss((p.nDataBit - 1),0)}
        is (L1DMISS.U)        {io.b_read(h).data := r_csr(h).cnt.l1dmiss((p.nDataBit - 1),0)}
        is (L2MISS.U)         {io.b_read(h).data := r_csr(h).cnt.l2miss((p.nDataBit - 1),0)}
      }

      if (p.nDataBit == 32) {
        switch (io.b_read(h).addr) {
          is (ENVCFG.U)     {io.b_read(h).data := r_csr(h).ceps.get.envcfg(63, 32)}

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

      if (p.nCepsTrapLvl > 0) {
        switch (io.b_read(h).addr) {
          is (TL0STATUS.U)      {io.b_read(h).data := r_csr(h).ceps.get.tl0status}
          is (TL0TDC.U)         {io.b_read(h).data := r_csr(h).ceps.get.tl0tdc}
          is (TL0EDELEG.U)      {io.b_read(h).data := r_csr(h).ceps.get.tl0edeleg}
          is (TL0IDELEG.U)      {io.b_read(h).data := r_csr(h).ceps.get.tl0ideleg}
          is (TL0IE.U)          {io.b_read(h).data := r_csr(h).ceps.get.tl0ie}
          is (TL0TVEC.U)        {io.b_read(h).data := r_csr(h).ceps.get.tl0tvec}

          is (TL0SCRATCH.U)     {io.b_read(h).data := r_csr(h).ceps.get.tl0scratch}
          is (TL0EDC.U)         {io.b_read(h).data := r_csr(h).ceps.get.tl0edc}
          is (TL0EPC.U)         {io.b_read(h).data := r_csr(h).ceps.get.tl0epc}
          is (TL0CAUSE.U)       {io.b_read(h).data := r_csr(h).ceps.get.tl0cause}
          is (TL0TVAL.U)        {io.b_read(h).data := r_csr(h).ceps.get.tl0tval}
          is (TL0IP.U)          {io.b_read(h).data := io.b_clint(h).ip}
        }
      }

      if (p.nCepsTrapLvl > 1) {
        switch (io.b_read(h).addr) {
          is (TL1STATUS.U)      {io.b_read(h).data := r_csr(h).ceps.get.tl1status}
          is (TL1TDC.U)         {io.b_read(h).data := r_csr(h).ceps.get.tl1tdc}
          is (TL1IE.U)          {io.b_read(h).data := r_csr(h).ceps.get.tl1ie}
          is (TL1TVEC.U)        {io.b_read(h).data := r_csr(h).ceps.get.tl1tvec}

          is (TL1SCRATCH.U)     {io.b_read(h).data := r_csr(h).ceps.get.tl1scratch}
          is (TL1EDC.U)         {io.b_read(h).data := r_csr(h).ceps.get.tl1edc}
          is (TL1EPC.U)         {io.b_read(h).data := r_csr(h).ceps.get.tl1epc}
          is (TL1CAUSE.U)       {io.b_read(h).data := r_csr(h).ceps.get.tl1cause}
          is (TL1TVAL.U)        {io.b_read(h).data := r_csr(h).ceps.get.tl1tval}
          is (TL1IP.U)          {io.b_read(h).data := io.b_clint(h).ip}
        }
      }
    }
  }

  // ******************************
  //            DECODER
  // ******************************
  for (h <- 0 until p.nHart) {
    when (io.b_dome(io.b_hart(h).dome).cbo) {
      io.o_decoder(h).cbie := CBIE.INV
      io.o_decoder(h).cbcfe := true.B
      io.o_decoder(h).cbze := true.B
    }.otherwise {
      io.o_decoder(h).cbie := r_csr(h).ceps.get.envcfg(5, 4)
      io.o_decoder(h).cbcfe := r_csr(h).ceps.get.envcfg(6)
      io.o_decoder(h).cbze := r_csr(h).ceps.get.envcfg(7)
    }
  }

  // ******************************
  //             DOME
  // ******************************
  for (h <- 0 until p.nHart) {
    io.b_hart(h).free := true.B
  }
  for (d <- 0 until p.nDome) {
    io.b_dome(d).free := true.B
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(io.o_decoder)
    
    for (h <- 0 until p.nHart) {
      io.o_dbg.get(h) := r_csr(h)

      io.o_dbg.get(h).base.cycle    := r_csr(0).cnt.cycle
      io.o_dbg.get(h).base.time     := r_csr(0).cnt.time
      io.o_dbg.get(h).base.instret  := r_csr(h).cnt.instret
      io.o_dbg.get(h).cnt.cycle     := r_csr(0).cnt.cycle
      io.o_dbg.get(h).cnt.time      := r_csr(0).cnt.time
      io.o_dbg.get(h).ceps.get.cdc  := io.b_dmu(h).cdc
      io.o_dbg.get(h).ceps.get.pdc  := io.b_dmu(h).pdc

      dontTouch(r_csr(h).cnt)
    }    

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    for (h <- 0 until p.nHart) {
      io.b_trap(h).ctrl.get.etd.get.pc := io.i_trap(h).pc
    }
  }
}

object Ceps extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Ceps(CsrConfigBase), args)
}