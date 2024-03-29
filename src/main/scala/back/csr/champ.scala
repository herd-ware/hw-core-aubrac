/*
 * File: champ.scala                                                           *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-21 10:26:52 am                                       *
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
import herd.core.aubrac.common._
import herd.core.aubrac.hfu.{HfuReqCtrlBus, HfuReqDataBus, HfuCsrIO}
import herd.core.aubrac.hfu.{CODE => HFUCODE, OP => HFUOP}
import herd.io.core.clint.{ClintIO}

import herd.common.isa.riscv.{CBIE}
import herd.common.isa.riscv.CSR._
import herd.common.isa.champ._
import herd.common.isa.champ.CSR._
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

    val i_hpm = Input(Vec(p.nHart, Vec(32, UInt(64.W))))

    val o_decoder = Output(Vec(p.nHart, new CsrDecoderBus()))
    val b_hfu = Vec(p.nHart, Flipped(new HfuCsrIO(p.nAddrBit, p.nChampTrapLvl)))
    val b_clint = Vec(p.nHart, Flipped(new ClintIO(p.nDataBit)))

    val o_dbg = if (p.debug) Some(Output(Vec(p.nHart, new CsrDbgBus(p.nDataBit, true, p.nChampTrapLvl)))) else None
  })

  // ******************************
  //             INIT
  // ******************************
  val init_csr = Wire(Vec(p.nHart, new CsrBus(p.nDataBit, true, p.nChampTrapLvl)))
  init_csr := DontCare

  for (h <- 0 until p.nHart) {
    init_csr(h).champ.get.chf               := 0.U 
    init_csr(h).champ.get.phf               := 0.U 
    
    init_csr(h).champ.get.hlxvendorid       := 0.U
    init_csr(h).champ.get.hlxarchid         := 0.U
    init_csr(h).champ.get.hlximpid          := 0.U
    init_csr(h).champ.get.hlxid             := h.U

    init_csr(h).champ.get.tlxstatus.ie      := 0.U.asTypeOf(init_csr(h).champ.get.tlxstatus.ie)
    init_csr(h).champ.get.tlxstatus.pie     := 0.U.asTypeOf(init_csr(h).champ.get.tlxstatus.pie)
    init_csr(h).champ.get.tlxisa.a          := p.useExtA.B
    init_csr(h).champ.get.tlxisa.b          := p.useExtB.B
    init_csr(h).champ.get.tlxisa.i          := true.B
    init_csr(h).champ.get.tlxisa.m          := p.useExtM.B
    if (p.nDataBit == 64) {
      init_csr(h).champ.get.tlxisa.mxl      := 2.U 
    } else {
      init_csr(h).champ.get.tlxisa.mxl      := 1.U 
    }

    for (tl <- 0 until p.nChampTrapLvl) {
      init_csr(h).champ.get.tlxthf(tl)      := DontCare
      init_csr(h).champ.get.tlxedeleg(tl)   := 0.U.asTypeOf(init_csr(h).champ.get.tlxedeleg(tl))
      init_csr(h).champ.get.tlxideleg(tl)   := 0.U.asTypeOf(init_csr(h).champ.get.tlxedeleg(tl))
      init_csr(h).champ.get.tlxie(tl).sie   := 0.U.asTypeOf(init_csr(h).champ.get.tlxie(tl).sie)
      init_csr(h).champ.get.tlxie(tl).tie   := 0.U.asTypeOf(init_csr(h).champ.get.tlxie(tl).tie)
      init_csr(h).champ.get.tlxie(tl).eie   := 0.U.asTypeOf(init_csr(h).champ.get.tlxie(tl).eie)
      init_csr(h).champ.get.tlxtvec(tl)     := DontCare

      init_csr(h).champ.get.tlxscratch(tl)  := DontCare
      init_csr(h).champ.get.tlxehf(tl).hf   := DontCare
      init_csr(h).champ.get.tlxehf(tl).hw   := false.B
      init_csr(h).champ.get.tlxepc(tl)      := DontCare 
      init_csr(h).champ.get.tlxcause(tl)    := DontCare
      init_csr(h).champ.get.tlxtval(tl)     := DontCare
      init_csr(h).champ.get.tlxip(tl)       := DontCare
    }

    init_csr(h).champ.get.envcfg.fiom      := DontCare
    init_csr(h).champ.get.envcfg.cbie      := CBIE.INV
    init_csr(h).champ.get.envcfg.cbcfe     := true.B
    init_csr(h).champ.get.envcfg.cbze      := true.B
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
            r_csr(h).champ.get.envcfg.cbie := w_wdata(h)(5, 4)
            r_csr(h).champ.get.envcfg.cbcfe := w_wdata(h)(6)
            r_csr(h).champ.get.envcfg.cbze := w_wdata(h)(7)          
          }
        }
      }

//      if (p.nDataBit == 32) {
//        switch (io.b_write(h).addr) {
//          is (ENVCFGH.U)           {
//            when (io.b_field(io.b_hart(h).field).cbo) {
//              r_csr(h).champ.get.envcfg := Cat(w_wdata(h), r_csr(h).champ.get.envcfg(31, 0))
//            }
//          }
//        }
//      }

      if (p.nChampTrapLvl > 0) {
        when (io.b_field(io.b_hart(h).field).tl(0)) {
          switch (io.b_write(h).addr) {
            is (TL0STATUS.U) {
              for (tl <- 0 until p.nChampTrapLvl) {
                r_csr(h).champ.get.tlxstatus.ie(tl) := w_wdata(h)(0 + tl)
              }              
            }

            is (TL0THF.U) {
              r_csr(h).champ.get.tlxthf(0) := w_wdata(h)(4, 0)
            }
            is (TL0EDELEG.U) {
              if (p.nChampTrapLvl > 1) {
                r_csr(h).champ.get.tlxedeleg(0) := w_wdata(h).asBools
              }
            }
            is (TL0IDELEG.U) {
              if (p.nChampTrapLvl > 1) {
                r_csr(h).champ.get.tlxideleg(0) := w_wdata(h).asBools
              }
            }
            is (TL0IE.U) {
              for (tl <- 0 until p.nChampTrapLvl) {
                r_csr(h).champ.get.tlxie(0).sie(tl) := w_wdata(h)(0 + tl)
                r_csr(h).champ.get.tlxie(0).tie(tl) := w_wdata(h)(4 + tl)
                r_csr(h).champ.get.tlxie(0).eie(tl) := w_wdata(h)(8 + tl)
              }              
            }
            is (TL0TVEC.U) {
              r_csr(h).champ.get.tlxtvec(0).mode := 0.U
              r_csr(h).champ.get.tlxtvec(0).base := w_wdata(h)(p.nAddrBit - 1, 2)
            }
            is (TL0SCRATCH.U) {
              r_csr(h).champ.get.tlxscratch(0) := w_wdata(h)
            }
            is (TL0EHF.U) {
              r_csr(h).champ.get.tlxehf(0).hf := w_wdata(h)(4, 0)
              r_csr(h).champ.get.tlxehf(0).hw := 0.B
            }
            is (TL0EPC.U) {
              r_csr(h).champ.get.tlxepc(0) := Cat(w_wdata(h)(p.nAddrBit - 1, 2), 0.U(2.W))
              r_csr(h).champ.get.tlxehf(0).hw := 0.B
            }                               
            is (TL0TVAL.U) {
              r_csr(h).champ.get.tlxtval(0) := w_wdata(h)
            }
          }
        }  
      }      

      if (p.nChampTrapLvl > 1) {
        when (io.b_field(io.b_hart(h).field).tl(1)) {
          switch (io.b_write(h).addr) {
            is (TL1STATUS.U) {
              for (tl <- 1 until p.nChampTrapLvl) {
                r_csr(h).champ.get.tlxstatus.ie(tl) := w_wdata(h)(0 + tl)
              }         
            }

            is (TL1THF.U) {
              r_csr(h).champ.get.tlxthf(1) := w_wdata(h)(4, 0)
            }
            is (TL1EDELEG.U) {
              if (p.nChampTrapLvl > 2) {
                r_csr(h).champ.get.tlxedeleg(1) := w_wdata(h).asBools
              }
            }
            is (TL1IDELEG.U) {
              if (p.nChampTrapLvl > 2) {
                r_csr(h).champ.get.tlxideleg(1) := w_wdata(h).asBools
              }
            }
            is (TL1IE.U) {
              for (tl <- 1 until p.nChampTrapLvl) {
                r_csr(h).champ.get.tlxie(1).sie(tl) := w_wdata(h)(0 + tl)
                r_csr(h).champ.get.tlxie(1).tie(tl) := w_wdata(h)(4 + tl)
                r_csr(h).champ.get.tlxie(1).eie(tl) := w_wdata(h)(8 + tl)
              }              
            }
            is (TL1TVEC.U) {
              r_csr(h).champ.get.tlxtvec(1).mode := 0.U
              r_csr(h).champ.get.tlxtvec(1).base := w_wdata(h)(p.nAddrBit - 1, 2)
            }
            is (TL1SCRATCH.U) {
              r_csr(h).champ.get.tlxscratch(1) := w_wdata(h)
            }
            is (TL1EHF.U) {
              r_csr(h).champ.get.tlxehf(1).hf := w_wdata(h)(4, 0)
              r_csr(h).champ.get.tlxehf(1).hw := 0.B
            }
            is (TL1EPC.U) {
              r_csr(h).champ.get.tlxepc(1) := Cat(w_wdata(h)(p.nAddrBit - 1, 2), 0.U(2.W))
              r_csr(h).champ.get.tlxehf(1).hw := 0.B
            }                               
            is (TL1TVAL.U) {
              r_csr(h).champ.get.tlxtval(1) := w_wdata(h)
            }
          }
        }  
      }      
    }
  }

  // ******************************
  //             TRAP
  // ******************************
  // ------------------------------
  //          INFORMATION
  // ------------------------------
  val w_is_tlxhandler = Wire(Vec(p.nHart, Vec(p.nChampTrapLvl, Bool())))
  val w_is_tlxedeleg = Wire(Vec(p.nHart, Vec(p.nChampTrapLvl, Bool())))
  val w_is_tlxideleg = Wire(Vec(p.nHart, Vec(p.nChampTrapLvl, Bool())))
  val w_is_tlxdeleg = Wire(Vec(p.nHart, Vec(p.nChampTrapLvl, Bool())))

  for (h <- 0 until p.nHart) {
    for (tl <- 0 until p.nChampTrapLvl) {
      w_is_tlxhandler(h)(tl) := io.b_field(io.b_hart(h).field).tl(tl) & (r_csr(h).champ.get.tlxthf(tl) === io.b_hfu(h).chf)
      w_is_tlxedeleg(h)(tl) := (io.i_trap(h).src =/= TRAPSRC.IRQ) & r_csr(h).champ.get.tlxedeleg(tl)(io.i_trap(h).cause(log2Ceil(p.nDataBit) - 1, 0)) 
      w_is_tlxideleg(h)(tl) := (io.i_trap(h).src === TRAPSRC.IRQ) & r_csr(h).champ.get.tlxideleg(tl)(io.i_trap(h).cause(log2Ceil(p.nDataBit) - 1, 0))
      if ((tl + 1) < (p.nChampTrapLvl)) {
        if (tl > 0) {
          w_is_tlxdeleg(h)(tl) := (io.i_trap(h).cause(p.nDataBit - 2, log2Ceil(p.nDataBit)) === 0.U) & (w_is_tlxedeleg(h)(tl) | w_is_tlxideleg(h)(tl)) & w_is_tlxdeleg(h)(tl - 1)
        } else {
          w_is_tlxdeleg(h)(tl) := (io.i_trap(h).cause(p.nDataBit - 2, log2Ceil(p.nDataBit)) === 0.U) & (w_is_tlxedeleg(h)(tl) | w_is_tlxideleg(h)(tl))
        }        
      } else {
        w_is_tlxdeleg(h)(tl) := false.B
      }      
    }
  }

  // ------------------------------
  //            UPDATE
  // ------------------------------
  for (h <- 0 until p.nHart) {
    for (tl <- 0 until p.nChampTrapLvl) {
      when (io.i_trap(h).valid) {
        when ((io.i_trap(h).src === TRAPSRC.IRQ) | (io.i_trap(h).src === TRAPSRC.EXC)) {
          when (
            if (tl > 0) {
              ~w_is_tlxdeleg(h)(tl) & w_is_tlxdeleg(h)(tl - 1)
            } else {
              ~w_is_tlxdeleg(h)(tl)
            }
          ) {
            r_csr(h).champ.get.tlxehf(tl).hf := io.b_hfu(h).chf
            r_csr(h).champ.get.tlxehf(tl).hw := 1.B
            r_csr(h).champ.get.tlxepc(tl) := io.i_trap(h).pc
            r_csr(h).champ.get.tlxcause(tl).irq := (io.i_trap(h).src === TRAPSRC.IRQ)
            r_csr(h).champ.get.tlxcause(tl).code := io.i_trap(h).cause(p.nDataBit - 2, 0)
            r_csr(h).champ.get.tlxtval(tl) := io.i_trap(h).info
          }
        } 
      } 

      io.b_hfu(h).etl(tl).sw := r_csr(h).champ.get.tlxehf(tl).hw
      io.b_hfu(h).etl(tl).hf := r_csr(h).champ.get.tlxehf(tl).hf
      io.b_hfu(h).etl(tl).pc := r_csr(h).champ.get.tlxepc(tl)
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

    for (tl <- 0 until p.nChampTrapLvl) {
      when (io.i_trap(h).valid) {
        when (
          if (tl > 0) {
            ~w_is_tlxdeleg(h)(tl) & w_is_tlxdeleg(h)(tl - 1)
          } else {
            ~w_is_tlxdeleg(h)(tl)
          }
        ) {
          when (w_is_tlxhandler(h)(tl)) {
            io.o_br_trap(h).valid := true.B
            io.o_br_trap(h).addr := r_csr(h).champ.get.tlxtvec(tl).addr
          }.otherwise {
            io.b_trap(h).valid := true.B
            io.b_trap(h).ctrl.get.code := HFUCODE.TRAP
            io.b_trap(h).ctrl.get.op2 := HFUOP.IN
            io.b_trap(h).ctrl.get.op3 := HFUOP.X
            io.b_trap(h).ctrl.get.wb := false.B
            io.b_trap(h).ctrl.get.hfs1 := r_csr(h).champ.get.tlxthf(tl)
            io.b_trap(h).data.get.s2 := r_csr(h).champ.get.tlxtvec(tl).addr
          }
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
  val w_ie = Wire(Vec(p.nHart, Vec(4, Vec(p.nDataBit, Bool()))))

  w_ie := 0.U.asTypeOf(w_ie)
  for (h <- 0 until p.nHart) {
    // Interrupt reset
    io.b_clint(h).ir := 0.U

    when (io.b_write(h).valid & (io.b_write(h).addr === TL0IP.U) & io.b_field(io.b_hart(h).field).tl(0)) {
      io.b_clint(h).ir := ~(w_wdata(h) & r_csr(h).champ.get.tlxip(0).mask(0))
    }

    when (io.b_write(h).valid & (io.b_write(h).addr === TL1IP.U) & io.b_field(io.b_hart(h).field).tl(1)) {
      io.b_clint(h).ir := ~(w_wdata(h) & r_csr(h).champ.get.tlxip(1).mask(1))
    }

    // Interrupt enable    
    for (tl <- 0 until p.nChampTrapLvl) {
      for (b <- 0 until p.nDataBit) {
        w_ie(h)(tl)(b) := r_csr(h).champ.get.tlxstatus.ie(tl) & (r_csr(h).champ.get.tlxie(tl).toUInt)(b) 
      }
    }

    io.b_clint(h).ie := w_ie(h)(0).asUInt | w_ie(h)(1).asUInt | w_ie(h)(2).asUInt | w_ie(h)(3).asUInt
    io.o_ie(h) := w_ie(h)(0).asUInt | w_ie(h)(1).asUInt | w_ie(h)(2).asUInt | w_ie(h)(3).asUInt
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

        is (HL0ID.U)          {io.b_read(h).data := r_csr(0).champ.get.hlxid}
        is (ENVCFG.U)         {io.b_read(h).data := r_csr(h).champ.get.envcfg.toUInt}

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
      }

      if (p.nDataBit == 32) {
        switch (io.b_read(h).addr) {
          is (ENVCFG.U)         {io.b_read(h).data := 0.U}
          
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

      if (p.nChampTrapLvl > 0) {
        switch (io.b_read(h).addr) {
          is (TL0STATUS.U)      {io.b_read(h).data := r_csr(h).champ.get.tlxstatus.status(0)}
          is (TL0THF.U)         {io.b_read(h).data := r_csr(h).champ.get.tlxthf(0)}
          is (TL0EDELEG.U)      {io.b_read(h).data := r_csr(h).champ.get.tlxedeleg(0).asUInt}
          is (TL0IDELEG.U)      {io.b_read(h).data := r_csr(h).champ.get.tlxideleg(0).asUInt}
          is (TL0IE.U)          {io.b_read(h).data := r_csr(h).champ.get.tlxie(0).toUInt}
          is (TL0TVEC.U)        {io.b_read(h).data := r_csr(h).champ.get.tlxtvec(0).toUInt}

          is (TL0SCRATCH.U)     {io.b_read(h).data := r_csr(h).champ.get.tlxscratch(0)}
          is (TL0EHF.U)         {io.b_read(h).data := r_csr(h).champ.get.tlxehf(0).toUInt}
          is (TL0EPC.U)         {io.b_read(h).data := r_csr(h).champ.get.tlxepc(0)}
          is (TL0CAUSE.U)       {io.b_read(h).data := r_csr(h).champ.get.tlxcause(0).toUInt}
          is (TL0TVAL.U)        {io.b_read(h).data := r_csr(h).champ.get.tlxtval(0)}
          is (TL0IP.U)          {io.b_read(h).data := io.b_clint(h).ip}
        }
      }

      if (p.nChampTrapLvl > 1) {
        switch (io.b_read(h).addr) {
          is (TL1STATUS.U)      {io.b_read(h).data := r_csr(h).champ.get.tlxstatus.status(1)}
          is (TL1THF.U)         {io.b_read(h).data := r_csr(h).champ.get.tlxthf(1)}
          is (TL1EDELEG.U)      {io.b_read(h).data := r_csr(h).champ.get.tlxedeleg(1).asUInt}
          is (TL1IDELEG.U)      {io.b_read(h).data := r_csr(h).champ.get.tlxideleg(1).asUInt}
          is (TL1IE.U)          {io.b_read(h).data := r_csr(h).champ.get.tlxie(1).toUInt}
          is (TL1TVEC.U)        {io.b_read(h).data := r_csr(h).champ.get.tlxtvec(1).toUInt}

          is (TL1SCRATCH.U)     {io.b_read(h).data := r_csr(h).champ.get.tlxscratch(1)}
          is (TL1EHF.U)         {io.b_read(h).data := r_csr(h).champ.get.tlxehf(1).toUInt}
          is (TL1EPC.U)         {io.b_read(h).data := r_csr(h).champ.get.tlxepc(1)}
          is (TL1CAUSE.U)       {io.b_read(h).data := r_csr(h).champ.get.tlxcause(1).toUInt}
          is (TL1TVAL.U)        {io.b_read(h).data := r_csr(h).champ.get.tlxtval(1)}
          is (TL1IP.U)          {io.b_read(h).data := io.b_clint(h).ip}
        }
      }
    }
  }

  // ******************************
  //            DECODER
  // ******************************
  for (h <- 0 until p.nHart) {
    io.o_decoder(h).cbie := r_csr(h).champ.get.envcfg.cbie
    io.o_decoder(h).cbcfe := r_csr(h).champ.get.envcfg.cbcfe
    io.o_decoder(h).cbze := r_csr(h).champ.get.envcfg.cbze   
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
      io.o_dbg.get(h).riscv.cycle := io.i_hpm(h)(0) 
      io.o_dbg.get(h).riscv.time := io.i_hpm(h)(1) 
      io.o_dbg.get(h).riscv.instret := io.i_hpm(h)(2) 
      io.o_dbg.get(h).riscv.hpmcounter3 := io.i_hpm(h)(3)
      io.o_dbg.get(h).riscv.hpmcounter4 := io.i_hpm(h)(4)
      io.o_dbg.get(h).riscv.hpmcounter5 := io.i_hpm(h)(5)
      io.o_dbg.get(h).riscv.hpmcounter6 := io.i_hpm(h)(6)
      io.o_dbg.get(h).riscv.hpmcounter7 := io.i_hpm(h)(7)
      io.o_dbg.get(h).riscv.hpmcounter8 := io.i_hpm(h)(8)
      io.o_dbg.get(h).riscv.hpmcounter9 := io.i_hpm(h)(9)
      io.o_dbg.get(h).riscv.hpmcounter10 := io.i_hpm(h)(10)
      io.o_dbg.get(h).riscv.hpmcounter11 := io.i_hpm(h)(11)
      io.o_dbg.get(h).riscv.hpmcounter12 := io.i_hpm(h)(12)
      io.o_dbg.get(h).riscv.hpmcounter13 := io.i_hpm(h)(13)
      io.o_dbg.get(h).riscv.hpmcounter14 := io.i_hpm(h)(14)
      io.o_dbg.get(h).riscv.hpmcounter15 := io.i_hpm(h)(15)
      io.o_dbg.get(h).riscv.hpmcounter16 := io.i_hpm(h)(16)
      io.o_dbg.get(h).riscv.hpmcounter17 := io.i_hpm(h)(17)
      io.o_dbg.get(h).riscv.hpmcounter18 := io.i_hpm(h)(18)
      io.o_dbg.get(h).riscv.hpmcounter19 := io.i_hpm(h)(19)
      io.o_dbg.get(h).riscv.hpmcounter20 := io.i_hpm(h)(20)
      io.o_dbg.get(h).riscv.hpmcounter21 := io.i_hpm(h)(21)
      io.o_dbg.get(h).riscv.hpmcounter22 := io.i_hpm(h)(22)
      io.o_dbg.get(h).riscv.hpmcounter23 := io.i_hpm(h)(23)
      io.o_dbg.get(h).riscv.hpmcounter24 := io.i_hpm(h)(24)
      io.o_dbg.get(h).riscv.hpmcounter25 := io.i_hpm(h)(25)
      io.o_dbg.get(h).riscv.hpmcounter26 := io.i_hpm(h)(26)
      io.o_dbg.get(h).riscv.hpmcounter27 := io.i_hpm(h)(27)
      io.o_dbg.get(h).riscv.hpmcounter28 := io.i_hpm(h)(28)
      io.o_dbg.get(h).riscv.hpmcounter29 := io.i_hpm(h)(29)
      io.o_dbg.get(h).riscv.hpmcounter30 := io.i_hpm(h)(30)
      io.o_dbg.get(h).riscv.hpmcounter31 := io.i_hpm(h)(31)

      io.o_dbg.get(h).champ.get.chf := io.b_hfu(h).chf
      io.o_dbg.get(h).champ.get.phf := io.b_hfu(h).phf

      io.o_dbg.get(h).champ.get.hlxid := r_csr(h).champ.get.hlxid

      for (tl <- 0 until p.nChampTrapLvl) {
        io.o_dbg.get(h).champ.get.tlxstatus(tl) := r_csr(h).champ.get.tlxstatus.status(tl)
        io.o_dbg.get(h).champ.get.tlxisa(tl) := r_csr(h).champ.get.tlxisa.toUInt

        io.o_dbg.get(h).champ.get.tlxthf(tl) := r_csr(h).champ.get.tlxthf(tl)
        io.o_dbg.get(h).champ.get.tlxedeleg(tl) := r_csr(h).champ.get.tlxedeleg(tl).asUInt
        io.o_dbg.get(h).champ.get.tlxideleg(tl) := r_csr(h).champ.get.tlxideleg(tl).asUInt
        io.o_dbg.get(h).champ.get.tlxie(tl) := r_csr(h).champ.get.tlxie(tl).toUInt
        io.o_dbg.get(h).champ.get.tlxtvec(tl) := r_csr(h).champ.get.tlxtvec(tl).toUInt

        io.o_dbg.get(h).champ.get.tlxscratch(tl) := r_csr(h).champ.get.tlxscratch(tl)
        io.o_dbg.get(h).champ.get.tlxehf(tl) := r_csr(h).champ.get.tlxehf(tl).toUInt
        io.o_dbg.get(h).champ.get.tlxepc(tl) := r_csr(h).champ.get.tlxepc(tl)
        io.o_dbg.get(h).champ.get.tlxcause(tl) := r_csr(h).champ.get.tlxcause(tl).toUInt
        io.o_dbg.get(h).champ.get.tlxtval(tl) := r_csr(h).champ.get.tlxtval(tl)
        io.o_dbg.get(h).champ.get.tlxip(tl) := r_csr(h).champ.get.tlxip(tl).toUInt
      }

      io.o_dbg.get(h).champ.get.envcfg := r_csr(h).champ.get.envcfg.toUInt
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