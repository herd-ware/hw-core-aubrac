/*
 * File: champ.scala                                                           *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 06:44:05 pm                                       *
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
import herd.common.field._
import herd.common.isa.hpc.{HpcPipelineBus, HpcMemoryBus}
import herd.core.aubrac.common._
import herd.core.aubrac.hfu.{HfuReqCtrlBus, HfuReqDataBus, HfuCsrIO}
import herd.core.aubrac.hfu.{CODE => HFUCODE, OP => HFUOP}
import herd.io.core.clint.{ClintIO}

import herd.common.isa.riscv.{CBIE}
import herd.common.isa.champ._
import herd.common.isa.champ.CSR._
import herd.common.isa.hpc.CSR._
import herd.common.isa.custom.CSR._


class Champ(p: CsrParams) extends Module {
  require(p.useChamp, "CHAMPS ISA support must be enable for this version of CSR.")

  val io = IO(new Bundle {
    val b_field = Vec(p.nField, new FieldIO(p.nAddrBit, p.nDataBit))
    val b_hart = Vec(p.nHart, new RsrcIO(p.nHart, p.nField, p.nHart))

    val b_read = Vec(p.nHart, new CsrReadIO(p.nDataBit))
    val b_write = Vec(p.nHart, new CsrWriteIO(p.nDataBit))

    val i_trap = Input(Vec(p.nHart, new TrapBus(p.nAddrBit, p.nDataBit)))
    val o_ie = Output(Vec(p.nHart, UInt(p.nDataBit.W)))
    val b_trap = Vec(p.nHart, new GenRVIO(p, new HfuReqCtrlBus(p.debug, p.nAddrBit), new HfuReqDataBus(p.nDataBit)))
    val o_br_trap = Output(Vec(p.nHart, new BranchBus(p.nAddrBit)))

    val i_hpc_pipe = Input(Vec(p.nHart, new HpcPipelineBus()))
    val i_hpc_mem = Input(Vec(p.nHart, new HpcMemoryBus()))

    val o_decoder = Output(Vec(p.nHart, new CsrDecoderBus()))
    val b_hfu = Vec(p.nHart, Flipped(new HfuCsrIO(p.nAddrBit, p.nChampTrapLvl)))
    val b_clint = Vec(p.nHart, Flipped(new ClintIO(p.nDataBit)))

    val o_dbg = if (p.debug) Some(Output(Vec(p.nHart, new CsrBus(p.nDataBit, true)))) else None
  })

  // ******************************
  //             INIT
  // ******************************
  val init_csr = Wire(Vec(p.nHart, new CsrBus(p.nDataBit, true)))
  init_csr := DontCare

  for (h <- 0 until p.nHart) {
    init_csr(h).champ.get.chf         := 0.U 
    init_csr(h).champ.get.phf         := 0.U 
    
    init_csr(h).champ.get.hl0id       := h.U

    init_csr(h).champ.get.tl0status   := DontCare
    init_csr(h).champ.get.tl0thf      := DontCare
    init_csr(h).champ.get.tl0edeleg   := DontCare
    init_csr(h).champ.get.tl0ideleg   := DontCare
    init_csr(h).champ.get.tl0ie       := DontCare
    init_csr(h).champ.get.tl0tvec     := DontCare

    init_csr(h).champ.get.tl0scratch  := DontCare
    init_csr(h).champ.get.tl0ehf      := DontCare
    init_csr(h).champ.get.tl0epc      := DontCare 
    init_csr(h).champ.get.tl0cause    := DontCare
    init_csr(h).champ.get.tl0tval     := DontCare
    init_csr(h).champ.get.tl0ip       := DontCare

    init_csr(h).champ.get.tl1status   := DontCare
    init_csr(h).champ.get.tl1thf      := DontCare
    init_csr(h).champ.get.tl1edeleg   := DontCare
    init_csr(h).champ.get.tl1ideleg   := DontCare
    init_csr(h).champ.get.tl1ie       := DontCare
    init_csr(h).champ.get.tl1tvec     := DontCare

    init_csr(h).champ.get.tl1scratch  := DontCare
    init_csr(h).champ.get.tl1ehf      := DontCare
    init_csr(h).champ.get.tl1epc      := DontCare
    init_csr(h).champ.get.tl1cause    := DontCare
    init_csr(h).champ.get.tl1tval     := DontCare 
    init_csr(h).champ.get.tl1ip       := DontCare 

    init_csr(h).champ.get.envcfg      := DontCare 

    init_csr(h).hpc.cycle             := 0.U
    init_csr(h).hpc.time              := 0.U
    init_csr(h).hpc.instret           := 0.U
    init_csr(h).hpc.alu               := 0.U
    init_csr(h).hpc.ld                := 0.U
    init_csr(h).hpc.st                := 0.U
    init_csr(h).hpc.br                := 0.U
    init_csr(h).hpc.mispred           := 0.U
    init_csr(h).hpc.l1imiss           := 0.U
    init_csr(h).hpc.l1dmiss           := 0.U
    init_csr(h).hpc.l2miss            := 0.U
  }

  val r_csr = RegInit(init_csr)

  // ******************************
  //             HPC
  // ******************************
  for (h <- 0 until p.nHart) {
    r_csr(h).hpc.cycle    := r_csr(h).hpc.cycle + 1.U
    r_csr(h).hpc.time     := r_csr(h).hpc.time + 1.U
    r_csr(h).hpc.instret  := r_csr(h).hpc.instret + io.i_hpc_pipe(h).instret
    r_csr(h).hpc.alu      := r_csr(h).hpc.alu + io.i_hpc_pipe(h).alu
    r_csr(h).hpc.ld       := r_csr(h).hpc.ld + io.i_hpc_pipe(h).ld
    r_csr(h).hpc.st       := r_csr(h).hpc.st + io.i_hpc_pipe(h).st
    r_csr(h).hpc.br       := r_csr(h).hpc.br + io.i_hpc_pipe(h).br
    r_csr(h).hpc.mispred  := r_csr(h).hpc.mispred + io.i_hpc_pipe(h).mispred
    r_csr(h).hpc.l1imiss  := r_csr(h).hpc.l1imiss + io.i_hpc_mem(h).l1imiss
    r_csr(h).hpc.l1dmiss  := r_csr(h).hpc.l1dmiss + io.i_hpc_mem(h).l1dmiss
    r_csr(h).hpc.l2miss   := r_csr(h).hpc.l2miss + io.i_hpc_mem(h).l2miss
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
          when (io.b_field(io.b_hart(h).field).cbo) {
            if (p.nDataBit > 32) {
              r_csr(h).champ.get.envcfg := w_wdata(h)
            } else {
              r_csr(h).champ.get.envcfg := Cat(r_csr(h).champ.get.envcfg(63, 32), w_wdata(h))
            }            
          }
        }
      }

      if (p.nDataBit == 32) {
        switch (io.b_write(h).addr) {
          is (ENVCFGH.U)           {
            when (io.b_field(io.b_hart(h).field).cbo) {
              r_csr(h).champ.get.envcfg := Cat(w_wdata(h), r_csr(h).champ.get.envcfg(31, 0))
            }
          }
        }
      }

      if (p.nChampTrapLvl > 0) {
        when (io.b_field(io.b_hart(h).field).tl(0)) {
          switch (io.b_write(h).addr) {
            is (TL0STATUS.U)    {r_csr(h).champ.get.tl0status := w_wdata(h)}
            is (TL0THF.U)       {r_csr(h).champ.get.tl0thf := w_wdata(h)}
            is (TL0EDELEG.U)    {r_csr(h).champ.get.tl0edeleg := w_wdata(h)}
            is (TL0IDELEG.U)    {r_csr(h).champ.get.tl0ideleg := w_wdata(h)}
            is (TL0IE.U)        {r_csr(h).champ.get.tl0ie := w_wdata(h)}
            is (TL0TVEC.U)      {r_csr(h).champ.get.tl0tvec := Cat(w_wdata(h)(p.nAddrBit - 1, 2), 0.U(2.W))}
            is (TL0SCRATCH.U)   {r_csr(h).champ.get.tl0scratch := w_wdata(h)}
            is (TL0EHF.U)       {r_csr(h).champ.get.tl0ehf := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), w_wdata(h)(4, 0))}
            is (TL0EPC.U)       {r_csr(h).champ.get.tl0epc := Cat(w_wdata(h)(p.nAddrBit - 1, 2), 0.U(2.W))
                                 r_csr(h).champ.get.tl0ehf := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), r_csr(h).champ.get.tl0ehf(4, 0))}                               
            is (TL0TVAL.U)      {r_csr(h).champ.get.tl0tval := w_wdata(h)}
          }
        }  
      }      

      if (p.nChampTrapLvl > 1) {
        when (io.b_field(io.b_hart(h).field).tl(1)) {
          switch (io.b_write(h).addr) {
            is (TL1STATUS.U)    {r_csr(h).champ.get.tl1status := w_wdata(h)}
            is (TL1THF.U)       {r_csr(h).champ.get.tl1thf := w_wdata(h)}
            is (TL1IE.U)        {r_csr(h).champ.get.tl1ie := w_wdata(h)}
            is (TL1TVEC.U)      {r_csr(h).champ.get.tl1tvec := Cat(w_wdata(h)(p.nAddrBit - 1, 2), 0.U(2.W))}
            is (TL1SCRATCH.U)   {r_csr(h).champ.get.tl1scratch := w_wdata(h)}
            is (TL1EHF.U)       {r_csr(h).champ.get.tl1ehf := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), w_wdata(h)(4, 0))}
            is (TL1EPC.U)       {r_csr(h).champ.get.tl1epc := Cat(w_wdata(h)(p.nAddrBit - 1, 2), 0.U(2.W))
                                 r_csr(h).champ.get.tl1ehf := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), r_csr(h).champ.get.tl1ehf(4, 0))}
            is (TL1TVAL.U)      {r_csr(h).champ.get.tl1tval := w_wdata(h)}
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
    w_is_tl0handler(h) := io.b_field(io.b_hart(h).field).tl(0) & (r_csr(h).champ.get.tl0thf === io.b_hfu(h).chf)
    if (p.nChampTrapLvl > 1) w_is_tl1handler(h) := (r_csr(h).champ.get.tl1thf === io.b_hfu(h).chf) else w_is_tl1handler(h) := false.B
    w_is_tl0deleg(h) := (io.i_trap(h).cause(p.nDataBit - 2, 5) === 0.U) & r_csr(h).champ.get.tl0edeleg(io.i_trap(h).cause(4, 0)) & (r_csr(h).champ.get.tl1thf === io.b_hfu(h).chf)
  }

  // ------------------------------
  //            UPDATE
  // ------------------------------
  for (h <- 0 until p.nHart) {
    when (io.i_trap(h).valid) {
      when ((io.i_trap(h).src === TRAPSRC.IRQ) | (io.i_trap(h).src === TRAPSRC.EXC)) {
        when (~w_is_tl0handler(h) & w_is_tl0deleg(h)) {
          r_csr(h).champ.get.tl1ehf := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), io.b_hfu(h).chf)
          r_csr(h).champ.get.tl1epc := io.i_trap(h).pc
          r_csr(h).champ.get.tl1cause := Cat((io.i_trap(h).src === TRAPSRC.IRQ), io.i_trap(h).cause)
          r_csr(h).champ.get.tl1tval := io.i_trap(h).info
        }.otherwise {
          r_csr(h).champ.get.tl0ehf := Cat(0.U(1.W), 0.U((p.nDataBit - 6).W), io.b_hfu(h).chf)
          r_csr(h).champ.get.tl0epc := io.i_trap(h).pc
          r_csr(h).champ.get.tl0cause := Cat((io.i_trap(h).src === TRAPSRC.IRQ), io.i_trap(h).cause)
          r_csr(h).champ.get.tl0tval := io.i_trap(h).info
        }
      } 
    } 

    if (p.nChampTrapLvl > 0) {
      io.b_hfu(h).etl(0).sw := r_csr(h).champ.get.tl0ehf(p.nDataBit - 1)
      io.b_hfu(h).etl(0).hf := r_csr(h).champ.get.tl0ehf(4, 0)
      io.b_hfu(h).etl(0).pc := r_csr(h).champ.get.tl0epc
    }

    if (p.nChampTrapLvl > 0) {
      io.b_hfu(h).etl(1).sw := r_csr(h).champ.get.tl1ehf(p.nDataBit - 1)
      io.b_hfu(h).etl(1).hf := r_csr(h).champ.get.tl1ehf(4, 0)
      io.b_hfu(h).etl(1).pc := r_csr(h).champ.get.tl1epc
    }
  }

  // ------------------------------
  //       ACTIVE TRAP LEVEL
  // ------------------------------
  val init_atl = Wire(Vec(p.nHart, Vec(p.nChampTrapLvl, Bool())))

  for (h <- 0 until p.nHart) {
    for (tl <- 0 until p.nChampTrapLvl) {
      init_atl(h)(tl) := false.B
    }
  }

  val r_atl = RegInit(init_atl)

  for (h <- 0 until p.nHart) {
    for (tl <- 0 until p.nChampTrapLvl) {
      io.b_hfu(h).atl := r_atl(h)
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
        io.o_br_trap(h).addr := r_csr(h).champ.get.tl1tvec
      }.elsewhen (w_is_tl0handler(h)) {
        io.o_br_trap(h).valid := true.B
        io.o_br_trap(h).addr := r_csr(h).champ.get.tl0tvec          
      }.otherwise {
        io.b_trap(h).valid := true.B
        io.b_trap(h).ctrl.get.code := HFUCODE.TRAP
        io.b_trap(h).ctrl.get.op2 := HFUOP.IN
        io.b_trap(h).ctrl.get.op3 := HFUOP.X
        io.b_trap(h).ctrl.get.wb := false.B
        when (w_is_tl0deleg(h)) {
          r_atl(h)(1) := true.B
          io.b_trap(h).ctrl.get.hfs1 := r_csr(h).champ.get.tl1thf
          io.b_trap(h).data.get.s2 := r_csr(h).champ.get.tl1tvec
        }.otherwise {
          r_atl(h)(0) := true.B
          io.b_trap(h).ctrl.get.hfs1 := r_csr(h).champ.get.tl0thf
          io.b_trap(h).data.get.s2 := r_csr(h).champ.get.tl0tvec
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
      w_ie(h)(0)(b) := r_csr(h).champ.get.tl0status(0) & r_csr(h).champ.get.tl0ie(b)
      w_ie(h)(1)(b) := r_csr(h).champ.get.tl1status(1) & r_csr(h).champ.get.tl1ie(b)     
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
        is (CHF.U)            {io.b_read(h).data := io.b_hfu(h).chf}
        is (PHF.U)            {io.b_read(h).data := io.b_hfu(h).phf}

        is (HL0ID.U)          {io.b_read(h).data := r_csr(0).champ.get.hl0id}
        is (ENVCFG.U)         {io.b_read(h).data := r_csr(h).champ.get.envcfg((p.nDataBit - 1),0)}

        is (CYCLE.U)          {io.b_read(h).data := r_csr(0).hpc.cycle((p.nDataBit - 1),0)}
        is (TIME.U)           {io.b_read(h).data := r_csr(0).hpc.time((p.nDataBit - 1),0)}
        is (INSTRET.U)        {io.b_read(h).data := r_csr(h).hpc.instret((p.nDataBit - 1),0)}
        is (BR.U)             {io.b_read(h).data := r_csr(h).hpc.br((p.nDataBit - 1),0)}
        is (MISPRED.U)        {io.b_read(h).data := r_csr(h).hpc.mispred((p.nDataBit - 1),0)}
        is (L1IMISS.U)        {io.b_read(h).data := r_csr(h).hpc.l1imiss((p.nDataBit - 1),0)}
        is (L1DMISS.U)        {io.b_read(h).data := r_csr(h).hpc.l1dmiss((p.nDataBit - 1),0)}
        is (L2MISS.U)         {io.b_read(h).data := r_csr(h).hpc.l2miss((p.nDataBit - 1),0)}
      }

      if (p.nDataBit == 32) {
        switch (io.b_read(h).addr) {
          is (ENVCFG.U)     {io.b_read(h).data := r_csr(h).champ.get.envcfg(63, 32)}

          is (CYCLEH.U)     {io.b_read(h).data := r_csr(0).hpc.cycle(63,32)}
          is (TIMEH.U)      {io.b_read(h).data := r_csr(0).hpc.time(63,32)}
          is (INSTRETH.U)   {io.b_read(h).data := r_csr(h).hpc.instret(63,32)}
          is (BRH.U)        {io.b_read(h).data := r_csr(h).hpc.br(63,32)}
          is (MISPREDH.U)   {io.b_read(h).data := r_csr(h).hpc.mispred(63,32)}
          is (L1IMISSH.U)   {io.b_read(h).data := r_csr(h).hpc.l1imiss(63,32)}
          is (L1DMISSH.U)   {io.b_read(h).data := r_csr(h).hpc.l1dmiss(63,32)}
          is (L2MISSH.U)    {io.b_read(h).data := r_csr(h).hpc.l2miss(63,32)}
        }
      }

      if (p.nChampTrapLvl > 0) {
        switch (io.b_read(h).addr) {
          is (TL0STATUS.U)      {io.b_read(h).data := r_csr(h).champ.get.tl0status}
          is (TL0THF.U)         {io.b_read(h).data := r_csr(h).champ.get.tl0thf}
          is (TL0EDELEG.U)      {io.b_read(h).data := r_csr(h).champ.get.tl0edeleg}
          is (TL0IDELEG.U)      {io.b_read(h).data := r_csr(h).champ.get.tl0ideleg}
          is (TL0IE.U)          {io.b_read(h).data := r_csr(h).champ.get.tl0ie}
          is (TL0TVEC.U)        {io.b_read(h).data := r_csr(h).champ.get.tl0tvec}

          is (TL0SCRATCH.U)     {io.b_read(h).data := r_csr(h).champ.get.tl0scratch}
          is (TL0EHF.U)         {io.b_read(h).data := r_csr(h).champ.get.tl0ehf}
          is (TL0EPC.U)         {io.b_read(h).data := r_csr(h).champ.get.tl0epc}
          is (TL0CAUSE.U)       {io.b_read(h).data := r_csr(h).champ.get.tl0cause}
          is (TL0TVAL.U)        {io.b_read(h).data := r_csr(h).champ.get.tl0tval}
          is (TL0IP.U)          {io.b_read(h).data := io.b_clint(h).ip}
        }
      }

      if (p.nChampTrapLvl > 1) {
        switch (io.b_read(h).addr) {
          is (TL1STATUS.U)      {io.b_read(h).data := r_csr(h).champ.get.tl1status}
          is (TL1THF.U)         {io.b_read(h).data := r_csr(h).champ.get.tl1thf}
          is (TL1IE.U)          {io.b_read(h).data := r_csr(h).champ.get.tl1ie}
          is (TL1TVEC.U)        {io.b_read(h).data := r_csr(h).champ.get.tl1tvec}

          is (TL1SCRATCH.U)     {io.b_read(h).data := r_csr(h).champ.get.tl1scratch}
          is (TL1EHF.U)         {io.b_read(h).data := r_csr(h).champ.get.tl1ehf}
          is (TL1EPC.U)         {io.b_read(h).data := r_csr(h).champ.get.tl1epc}
          is (TL1CAUSE.U)       {io.b_read(h).data := r_csr(h).champ.get.tl1cause}
          is (TL1TVAL.U)        {io.b_read(h).data := r_csr(h).champ.get.tl1tval}
          is (TL1IP.U)          {io.b_read(h).data := io.b_clint(h).ip}
        }
      }
    }
  }

  // ******************************
  //            DECODER
  // ******************************
  for (h <- 0 until p.nHart) {
    when (io.b_field(io.b_hart(h).field).cbo) {
      io.o_decoder(h).cbie := CBIE.INV
      io.o_decoder(h).cbcfe := true.B
      io.o_decoder(h).cbze := true.B
    }.otherwise {
      io.o_decoder(h).cbie := r_csr(h).champ.get.envcfg(5, 4)
      io.o_decoder(h).cbcfe := r_csr(h).champ.get.envcfg(6)
      io.o_decoder(h).cbze := r_csr(h).champ.get.envcfg(7)
    }
  }

  // ******************************
  //             FIELD
  // ******************************
  for (h <- 0 until p.nHart) {
    io.b_hart(h).free := true.B
  }
  for (f <- 0 until p.nField) {
    io.b_field(f).free := true.B
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

      io.o_dbg.get(h).riscv.cycle   := r_csr(0).hpc.cycle
      io.o_dbg.get(h).riscv.time    := r_csr(0).hpc.time
      io.o_dbg.get(h).riscv.instret := r_csr(h).hpc.instret
      io.o_dbg.get(h).hpc.cycle     := r_csr(0).hpc.cycle
      io.o_dbg.get(h).hpc.time      := r_csr(0).hpc.time
      io.o_dbg.get(h).champ.get.chf := io.b_hfu(h).chf
      io.o_dbg.get(h).champ.get.phf := io.b_hfu(h).phf

      dontTouch(r_csr(h).hpc)
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

object Champ extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Champ(CsrConfigBase), args)
}