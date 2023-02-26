/*
 * File: bus.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:13:05 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac

import chisel3._
import chisel3.util._

import herd.core.aubrac.common.{DfpBaseBus}
import herd.core.aubrac.front.{If2DfpBus}
import herd.core.aubrac.back.{IdDfpBus, ExDfpBus, MemDfpBus, MulDivDfpBus, WbDfpBus, GprDfpBus}
import herd.core.aubrac.back.csr.{CsrBus}


// ******************************
//             DEBUG
// ******************************
class PipelineDbgBus (p: PipelineParams) extends Bundle {
  val last = UInt(p.nAddrBit.W)
  val x = Vec(32, UInt(p.nDataBit.W))
  val csr = new CsrBus(p.nDataBit, p.useCeps)
}

class AubracDbgBus (p: AubracParams) extends Bundle {
  val last = UInt(p.nAddrBit.W)
  val x = Vec(32, UInt(p.nDataBit.W))
  val csr = new CsrBus(p.nDataBit, p.useCeps)
  val dc = if (p.useCeps) Some(Vec(p.nDomeCfg, Vec(6, UInt(p.nDataBit.W)))) else None
}

// ******************************
//         DATA FOOTPRINT
// ******************************
class PipelineDfpBus (p: PipelineParams) extends Bundle {
  val pc = UInt(p.nAddrBit.W)
  val if0 = UInt(p.nAddrBit.W)
  val if1 = if (p.useIf1Stage) Some(UInt(p.nAddrBit.W)) else None
  val if2 = if (p.useIf2Stage) Some(new If2DfpBus(p.nAddrBit, p.nFetchInstr, p.nInstrBit)) else None
  val if3 = Vec(p.nFetchBufferDepth, new DfpBaseBus(p.nAddrBit, p.nInstrBit))
  
  val id = new IdDfpBus(p)
  val ex = new ExDfpBus(p)
  val mem = if (p.useMemStage) Some(new MemDfpBus(p)) else None
  val wb = new WbDfpBus(p)
  val gpr = new GprDfpBus(p)
  
  val alu = if (p.nExStage > 1) Some(UInt(p.nDataBit.W)) else None
  val muldiv = if (p.useExtM) Some(new MulDivDfpBus(p.nDataBit, (p.nExStage > 2))) else None
}

class AubracDfpBus (p: AubracParams) extends Bundle {
  val pipe = new PipelineDfpBus(p)
}              