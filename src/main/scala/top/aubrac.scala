/*
 * File: aubrac.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:34:19 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac

import chisel3._
import chisel3.util._

import herd.common.mem.mb4s._
import herd.common.mem.cbo._
import herd.common.dome._
import herd.core.aubrac.common._
import herd.core.aubrac.hfu._
import herd.io.core._
import herd.mem.hay._


class Aubrac (p: AubracParams) extends Module {
  val io = IO(new Bundle {
    val b_dome = if (p.useDome) Some(Flipped(Vec(p.nDome, new DomeIO(p.nAddrBit, p.nDataBit)))) else None
    val b_pall = if (p.useDome) Some(Flipped(new NRsrcIO(p.nHart, p.nDome, p.nPart))) else None

    val b_imem = if (!p.useL2) Some(new Mb4sIO(p.pLLIBus)) else None
    val b_dmem = if (!p.useL2) Some(new Mb4sIO(p.pLLDBus)) else None
    val b_mem = if (p.useL2) Some(new Mb4sIO(p.pLLDBus)) else None

    val i_irq_lei = if (p.useChamp) Some(Input(Vec(p.nChampTrapLvl, Bool()))) else None
    val i_irq_lsi = if (p.useChamp) Some(Input(Vec(p.nChampTrapLvl, Bool()))) else None
    val i_irq_mei = if (!p.useChamp) Some(Input(Bool())) else None
    val i_irq_msi = if (!p.useChamp) Some(Input(Bool())) else None

    val o_dbg = if (p.debug) Some(Output(new AubracDbgBus(p))) else None
    val o_dfp = if (p.debug) Some(Output(new AubracDfpBus(p))) else None
    val o_etd = if (p.debug) Some(Output(new EtdBus(p.nHart, p.nAddrBit, p.nInstrBit))) else None
  })

  // ******************************
  //            MODULES
  // ******************************
  val m_pipe = Module(new Pipeline(p))
  val m_hfu = if (p.useChamp) Some(Module(new Hfu(p.pHfu))) else None
  val m_pall = if (p.useDome) Some(Module(new StaticSlct(p.nDome, p.nPart, 1))) else None
  val m_io = Module(new IOCore(p.pIO))
  val m_l0dcross = Module(new Mb4sCrossbar(p.pL0DCross))
  val m_l1i = if (p.useL1I) Some(Module(new Hay(p.pL1I))) else None
  val m_l1d = if (p.useL1D) Some(Module(new Hay(p.pL1D))) else None
  val m_l2 = if (p.useL2) Some(Module(new Hay(p.pL2))) else None
  val m_llcross = if (p.useL1D || p.useL2) Some(Module(new Mb4sCrossbar(p.pLLCross))) else None
  
  // ******************************
  //           PIPELINE
  // ******************************
  if (p.useDome) {
    m_pipe.io.b_dome.get <> m_hfu.get.io.b_dome 
    m_pipe.io.b_hart.get <> m_hfu.get.io.b_hart
  }

  m_pipe.io.b_clint <> m_io.io.b_clint

  // ******************************
  //           DOME SELECT
  // ******************************
  if (p.useDome) { 
    for (d <- 0 until p.nDome) {
      m_pall.get.io.i_weight(d) := m_hfu.get.io.b_pall.weight(d)
    }

    if (p.useL1I && p.pL1I.multiDome) {
      m_l1i.get.io.i_slct_prev.get := m_pall.get.io.o_slct
      if (p.useL2) m_l2.get.io.i_slct_prev.get := m_l1i.get.io.o_slct_next.get
    }

    if (p.useL1D && p.pL1D.multiDome) {
      m_l1d.get.io.i_slct_prev.get := m_pall.get.io.o_slct
      if (p.useL2) m_l2.get.io.i_slct_prev.get := m_l1d.get.io.o_slct_next.get
    }

    if (p.useL2 && p.pL2.multiDome && (!p.useL1I || !p.pL1I.multiDome) && (!p.useL1D || !p.pL1D.multiDome)) {
      m_l2.get.io.i_slct_prev.get := m_pall.get.io.o_slct
    }
  }

  // ******************************
  //             MEMORY
  // ******************************
  // ------------------------------
  //              L1I
  // ------------------------------
  val w_l1i_cbo = Wire(Vec(2, Bool()))  

  w_l1i_cbo(0) := false.B
  w_l1i_cbo(1) := true.B

  if (p.useL1I) {
    m_pipe.io.b_csr_mem.l1imiss := m_l1i.get.io.o_miss(0)

    if (p.useDome) {
      m_l1i.get.io.b_dome.get <> m_hfu.get.io.b_dome
      m_l1i.get.io.b_part.get <> m_hfu.get.io.b_pall
    }
    if (p.useCbo) {
      w_l1i_cbo(0) := m_pipe.io.b_cbo.get.instr
      w_l1i_cbo(1) := ~w_l1i_cbo(0) | m_l1i.get.io.b_cbo(0).ready
      m_l1i.get.io.b_cbo(0) <> m_pipe.io.b_cbo.get
      m_l1i.get.io.b_cbo(0).valid := m_pipe.io.b_cbo.get.valid & w_l1i_cbo(0)
    }

    if (p.useDome) m_l1i.get.io.i_slct_prev.get := m_pall.get.io.o_slct
    m_l1i.get.io.b_prev(0) <> m_pipe.io.b_imem
    if (!p.useL2) m_l1i.get.io.b_next <> io.b_imem.get
  } else {
    m_pipe.io.b_csr_mem.l1imiss := 0.U
    
    m_pipe.io.b_imem <> io.b_imem.get
  }

  // ------------------------------
  //              L0D
  // ------------------------------
  if (p.useDome) m_l0dcross.io.b_dome.get <> m_hfu.get.io.b_dome
  m_l0dcross.io.b_m(0) <> m_pipe.io.b_dmem
  if (p.useChamp) m_l0dcross.io.b_m(1) <> m_hfu.get.io.b_dmem
  m_l0dcross.io.b_s(0) <> m_io.io.b_port 
  if (!p.useL1D && !p.useL2) m_l0dcross.io.b_s(1) <> io.b_dmem.get   

  // ------------------------------
  //              L1D
  // ------------------------------
  val w_l1d_cbo = Wire(Vec(2, Bool())) 

  w_l1d_cbo(0) := false.B
  w_l1d_cbo(1) := true.B

  if (p.useL1D) {
    m_pipe.io.b_csr_mem.l1dmiss := m_l1d.get.io.o_miss(0)

    if (p.useDome) {
      m_l1d.get.io.b_dome.get <> m_hfu.get.io.b_dome
      m_l1d.get.io.b_part.get <> m_hfu.get.io.b_pall
    }    
    if (p.useCbo) {
      w_l1d_cbo(0) := m_pipe.io.b_cbo.get.data
      w_l1d_cbo(1) := ~w_l1d_cbo(0) | m_l1d.get.io.b_cbo(0).ready
      m_l1d.get.io.b_cbo(0) <> m_pipe.io.b_cbo.get
      m_l1d.get.io.b_cbo(0).valid := m_pipe.io.b_cbo.get.valid & w_l1d_cbo(0)
    }

    if (p.useDome) m_l1d.get.io.i_slct_prev.get := m_pall.get.io.o_slct
    m_l1d.get.io.b_prev(0) <> m_l0dcross.io.b_s(2)
    if (!p.useL2) {
      if (p.useDome) {
        m_llcross.get.io.b_dome.get <> m_hfu.get.io.b_dome
        m_llcross.get.io.i_slct_req.get := m_l1d.get.io.o_slct_next.get
        m_llcross.get.io.i_slct_read.get := m_l1d.get.io.o_slct_prev.get
        m_llcross.get.io.i_slct_write.get := m_l1d.get.io.o_slct_prev.get
      }
      m_llcross.get.io.b_m(0) <> m_l0dcross.io.b_s(1)
      m_llcross.get.io.b_m(1) <> m_l1d.get.io.b_next
      m_llcross.get.io.b_s(0) <> io.b_dmem.get
    }     
  } else {
    m_pipe.io.b_csr_mem.l1dmiss := 0.U    
  }

  // ------------------------------
  //              L2
  // ------------------------------
  val w_l2_cbo = Wire(Vec(2, Bool())) 

  w_l2_cbo(0) := false.B
  w_l2_cbo(1) := true.B

  if (p.useL2) {
    m_pipe.io.b_csr_mem.l2miss := m_l2.get.io.o_miss(0)

    if (p.useDome) {
      m_l2.get.io.b_dome.get <> m_hfu.get.io.b_dome
      for (d <- 0 until p.nDome) {
        if (p.useL1I && p.useL1D) {
          m_l2.get.io.b_dome.get(d).flush := m_hfu.get.io.b_dome(d).flush & m_l1i.get.io.b_dome.get(d).free & m_l1d.get.io.b_dome.get(d).free
        } else if (p.useL1I) {        
          m_l2.get.io.b_dome.get(d).flush := m_hfu.get.io.b_dome(d).flush & m_l1i.get.io.b_dome.get(d).free
        } else if (p.useL1D) {        
          m_l2.get.io.b_dome.get(d).flush := m_hfu.get.io.b_dome(d).flush & m_l1d.get.io.b_dome.get(d).free
        }
      }

      m_l2.get.io.b_part.get <> m_hfu.get.io.b_pall
      for (pa <- 0 until p.nPart) {
        if (p.useL1I && p.useL1D) {
          m_l2.get.io.b_part.get.state(pa).flush :=  m_hfu.get.io.b_pall.state(pa).flush & m_l1i.get.io.b_part.get.state(pa).free & m_l1d.get.io.b_part.get.state(pa).free
        } else if (p.useL1I) {        
          m_l2.get.io.b_part.get.state(pa).flush :=  m_hfu.get.io.b_pall.state(pa).flush & m_l1i.get.io.b_part.get.state(pa).free
        } else if (p.useL1D) {        
          m_l2.get.io.b_part.get.state(pa).flush :=  m_hfu.get.io.b_pall.state(pa).flush & m_l1d.get.io.b_part.get.state(pa).free
        }
      }
    }

    if (p.useCbo) {
      if (p.useL1D) {
        w_l2_cbo(0) := m_pipe.io.b_cbo.get.any & ~m_pipe.io.b_cbo.get.zero
      } else {
        w_l2_cbo(0) := m_pipe.io.b_cbo.get.any
      }      
      w_l2_cbo(1) := ~w_l2_cbo(0) | m_l2.get.io.b_cbo(0).ready
      m_l2.get.io.b_cbo(0) <> m_pipe.io.b_cbo.get
      m_l2.get.io.b_cbo(0).valid := m_pipe.io.b_cbo.get.valid & w_l2_cbo(0) & ((w_l1i_cbo(1) & w_l1d_cbo(1)) | m_pipe.io.b_cbo.get.hint)
    }

    if (p.useDome) {
      if (p.useL1D) {
        m_l2.get.io.i_slct_prev.get := m_l1d.get.io.o_slct_next.get
      } else if (p.useL1I) {
        m_l2.get.io.i_slct_prev.get := m_l1i.get.io.o_slct_next.get
      } else {
        m_l2.get.io.i_slct_prev.get := m_pall.get.io.o_slct
      }
    }
   
    if (p.useL1I) {
      m_l2.get.io.b_prev(0) <> m_l1i.get.io.b_next
    } else {
      m_l2.get.io.b_prev(0) <> m_pipe.io.b_imem
    }
    if (p.useL1I) {
      m_l2.get.io.b_prev(1) <> m_l1d.get.io.b_next
    } else {
      m_l2.get.io.b_prev(1) <> m_l0dcross.io.b_s(2)
    }

    if (p.useDome) {
      m_llcross.get.io.b_dome.get <> m_hfu.get.io.b_dome
      m_llcross.get.io.i_slct_req.get := m_l2.get.io.o_slct_next.get
      m_llcross.get.io.i_slct_read.get := m_l2.get.io.o_slct_prev.get
      m_llcross.get.io.i_slct_write.get := m_l2.get.io.o_slct_prev.get
    }
    m_llcross.get.io.b_m(0) <> m_l0dcross.io.b_s(1)
    m_llcross.get.io.b_m(1) <> m_l2.get.io.b_next
    m_llcross.get.io.b_s(0) <> io.b_mem.get
  } else {
    m_pipe.io.b_csr_mem.l2miss := 0.U
  }
  
  // ******************************
  //              CBO
  // ******************************
  if (p.useCbo) {
    m_pipe.io.b_cbo.get.ready := w_l1i_cbo(1) & w_l1d_cbo(1) & w_l2_cbo(1)
  } 

  // ******************************
  //             CLINT
  // ******************************
  if (p.useChamp) {
    m_io.io.b_dome.get <> m_hfu.get.io.b_dome

    m_io.io.i_irq_lei.get := io.i_irq_lei.get 
    m_io.io.i_irq_lsi.get := io.i_irq_lsi.get 
  } else {
    m_io.io.i_irq_mei.get := io.i_irq_mei.get
    m_io.io.i_irq_msi.get := io.i_irq_msi.get
  }

  // ******************************
  //              HFU
  // ******************************
  if (p.useChamp) {
    // ------------------------------
    //             PORT
    // ------------------------------
    m_hfu.get.io.b_port <> m_pipe.io.b_hfu.get    

    // ------------------------------
    //           DOME STATE
    // ------------------------------
    val w_dome_free = Wire(Vec(p.nDome, Vec(7, Bool())))

    for (d <- 0 until p.nDome) {
      for (l <- 0 until 7) {
        w_dome_free(d)(l) := true.B
      }
    }

    io.b_dome.get <> m_hfu.get.io.b_dome

    for (d <- 0 until p.nDome) {
      w_dome_free(d)(0) := m_pipe.io.b_dome.get(d).free
      w_dome_free(d)(1) := m_l0dcross.io.b_dome.get(d).free
      w_dome_free(d)(2) := m_io.io.b_dome.get(d).free
      if (p.useL1I) w_dome_free(d)(3) := m_l1i.get.io.b_dome.get(d).free
      if (p.useL1D) w_dome_free(d)(4) := m_l1d.get.io.b_dome.get(d).free
      if (p.useL2) w_dome_free(d)(5) := m_l2.get.io.b_dome.get(d).free
      w_dome_free(d)(6) := io.b_dome.get(d).free

      m_hfu.get.io.b_dome(d).free := w_dome_free(d).asUInt.andR
    }

    // ------------------------------
    //           HART STATE
    // ------------------------------

    // ------------------------------
    //    EXECUTED DOME PART STATE
    // ------------------------------
    for (pa <- 0 until p.nPart) {
      m_hfu.get.io.b_pexe.state(pa).free := true.B
    }

    // ------------------------------
    //        ALL PART STATE
    // ------------------------------
    val w_pall_free = Wire(Vec(p.nPart, Vec(4, Bool())))

    for (pa <- 0 until p.nPart) {
      for (l <- 0 until 4) {
        w_pall_free(pa)(l) := true.B
      }
    }

    for (pa <- 0 until p.nPart) {
      io.b_pall.get.state(pa) <> m_hfu.get.io.b_pall.state(pa)
    }
    io.b_pall.get.weight := DontCare

    for (pa <- 0 until p.nPart) {
      if (p.useL1I) w_pall_free(pa)(0) := m_l1i.get.io.b_part.get.state(pa).free
      if (p.useL1D) w_pall_free(pa)(1) := m_l1d.get.io.b_part.get.state(pa).free
      if (p.useL2) w_pall_free(pa)(2) := m_l2.get.io.b_part.get.state(pa).free
      w_pall_free(pa)(3) := io.b_pall.get.state(pa).free

      m_hfu.get.io.b_pall.state(pa).free := w_pall_free(pa).asUInt.andR
    }
  }

  // ******************************
  //             DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    io.o_dbg.get.last := m_pipe.io.o_dbg.get.last
    io.o_dbg.get.x := m_pipe.io.o_dbg.get.x
    io.o_dbg.get.csr := m_pipe.io.o_dbg.get.csr
    if (p.useChamp) io.o_dbg.get.hf.get := m_hfu.get.io.o_dbg.get

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------
    io.o_dfp.get.pipe := m_pipe.io.o_dfp.get

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    io.o_etd.get := m_pipe.io.o_etd.get
  } 
}

object Aubrac extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Aubrac(AubracConfigBase), args)
}
