/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-04-12 09:40:50 am                                       *
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

import herd.common.mem.mb4s._
import herd.core.aubrac.nlp._
import herd.core.aubrac.front._
import herd.core.aubrac.back._
import herd.core.aubrac.hfu._
import herd.mem.hay.{HayParams}
import herd.io.core._


// ******************************
//       PIPELINE PARAMETERS
// ******************************
trait PipelineParams extends NlpParams
                      with FrontParams
                      with BackParams {
  
  // ------------------------------
  //            GLOBAL
  // ------------------------------
  def debug: Boolean
  def pcBoot: String
  def nInstrByte: Int = 4 
  override def nInstrBit: Int = nInstrByte * 8
  def nAddrBit: Int
  def nDataBit: Int 

  // ------------------------------
  //             CHAMP
  // ------------------------------
  def useChamp: Boolean
  override def multiField: Boolean = false
  def nField: Int
  def nPart: Int
  def nChampTrapLvl: Int

  // ------------------------------
  //           FRONT END
  // ------------------------------
  def nFetchInstr: Int
  def useIMemSeq: Boolean
  def useIf1Stage: Boolean
  def useIf2Stage: Boolean = false
  def nFetchBufferDepth: Int  
  def useFastJal: Boolean = false

  // ------------------------------
  //       NEXT-LINE PREDICTOR
  // ------------------------------
  def useNlp: Boolean
  def nBtbLine: Int
  def nBhtSet: Int
  def nBhtSetEntry: Int
  def nBhtBit: Int
  def useRsbSpec: Boolean
  def nRsbDepth: Int

  // ------------------------------
  //           BACK END
  // ------------------------------
  def useExtM: Boolean
  def useExtA: Boolean
  def useExtB: Boolean
  def useExtZifencei: Boolean
  def useExtZicbo: Boolean
  def nExStage: Int 
  def useMemStage: Boolean
  def useBranchReg: Boolean
}

// ******************************
//        PIPELINE CONFIG
// ******************************
case class PipelineConfig (
  debug: Boolean,
  pcBoot: String,
  nAddrBit: Int,
  nDataBit: Int, 

  useChamp: Boolean,
  nField: Int,
  nPart: Int,
  nChampTrapLvl: Int,

  nFetchInstr: Int,
  useIMemSeq: Boolean,
  useIf1Stage: Boolean,
  nFetchBufferDepth: Int,

  useNlp: Boolean,
  nBtbLine: Int,
  nBhtSet: Int,
  nBhtSetEntry: Int,
  nBhtBit: Int,
  useRsbSpec: Boolean,
  nRsbDepth: Int,

  useExtM: Boolean,
  useExtA: Boolean,
  useExtB: Boolean,
  useExtZifencei: Boolean,
  useExtZicbo: Boolean,
  nExStage: Int,
  useMemStage: Boolean,
  useBranchReg: Boolean
) extends PipelineParams

// ******************************
//         L1 PARAMETERS 
// ******************************
trait L1Params extends HayParams {
  // ------------------------------
  //       GLOBAL PARAMETERS
  // ------------------------------
  def debug: Boolean
  def nCbo: Int

  // ------------------------------
  //   PREVIOUS MEMORY PARAMETERS
  // ------------------------------
  def pPrevBus: Array[Mb4sParams]

  // ------------------------------
  //        FIELD PARAMETERS
  // ------------------------------
  def multiField: Boolean = true
  def nPart: Int

  // ------------------------------
  //     CONTROLLER PARAMETERS
  // ------------------------------
  def nNextDataByte: Int
  def useReqReg: Boolean
  def useAccReg: Boolean
  def useAckReg: Boolean
  def nWriteFifoDepth: Int = 2
  def nNextFifoDepth: Int = 2
  def nNextLatency: Int

  // ------------------------------
  //     PREFETCHER PARAMETERS
  // ------------------------------
  def usePftch: Boolean
  def nPftchEntry: Int
  def nPftchEntryAcc: Int
  def nPftchMemRead: Int
  def nPftchMemWrite: Int

  // ------------------------------
  //   PHYSICAL MEMORY PARAMETERS
  // ------------------------------
  def nMem: Int
  def nMemReadPort: Int
  def nMemWritePort: Int

  // ------------------------------
  //        CACHE PARAMETERS
  // ------------------------------ 
  def slctPolicy: String
  def nSet: Int
  def nLine: Int
  def nData: Int
}

// ******************************
//           L1 CONFIG 
// ******************************
case class L1Config (
  pPrevBus: Array[Mb4sParams],

  debug: Boolean,
  nCbo: Int,

  nPart: Int,

  useReqReg: Boolean,
  useAccReg: Boolean,
  useAckReg: Boolean,
  nNextDataByte: Int,
  nNextLatency: Int,

  usePftch: Boolean,
  nPftchEntry: Int,
  nPftchEntryAcc: Int,
  nPftchMemRead: Int,
  nPftchMemWrite: Int,
  
  nMem: Int,
  nMemReadPort: Int,
  nMemWritePort: Int,

  slctPolicy: String,
  nSet: Int,
  nLine: Int,
  nData: Int
) extends L1Params

// ******************************
//         L2 PARAMETERS 
// ******************************
trait L2Params extends HayParams {
  // ------------------------------
  //       GLOBAL PARAMETERS
  // ------------------------------
  def debug: Boolean
  def nCbo: Int

  // ------------------------------
  //   PREVIOUS MEMORY PARAMETERS
  // ------------------------------
  def pPrevBus: Array[Mb4sParams]

  // ------------------------------
  //        FIELD PARAMETERS
  // ------------------------------
  def multiField: Boolean = true
  def nPart: Int

  // ------------------------------
  //     CONTROLLER PARAMETERS
  // ------------------------------
  def nNextDataByte: Int
  def useReqReg: Boolean
  def useAccReg: Boolean
  def useAckReg: Boolean
  def nWriteFifoDepth: Int
  def nNextFifoDepth: Int
  def nNextLatency: Int

  // ------------------------------
  //     PREFETCHER PARAMETERS
  // ------------------------------
  def usePftch: Boolean
  def nPftchEntry: Int
  def nPftchEntryAcc: Int
  def nPftchMemRead: Int
  def nPftchMemWrite: Int

  // ------------------------------
  //   PHYSICAL MEMORY PARAMETERS
  // ------------------------------
  def nMem: Int
  def nMemReadPort: Int
  def nMemWritePort: Int

  // ------------------------------
  //        CACHE PARAMETERS
  // ------------------------------ 
  def slctPolicy: String
  def nSet: Int
  def nLine: Int
  def nData: Int
}

case class L2Config (
  pPrevBus: Array[Mb4sParams],

  debug: Boolean,
  nCbo: Int,

  nPart: Int,

  nNextDataByte: Int,
  useReqReg: Boolean,
  useAccReg: Boolean,
  useAckReg: Boolean,
  nWriteFifoDepth: Int,
  nNextFifoDepth: Int,
  nNextLatency: Int,

  usePftch: Boolean,
  nPftchEntry: Int,
  nPftchEntryAcc: Int,
  nPftchMemRead: Int,
  nPftchMemWrite: Int,
  
  nMem: Int,
  nMemReadPort: Int,
  nMemWritePort: Int,

  slctPolicy: String,
  nSet: Int,
  nLine: Int,
  nData: Int
) extends L2Params

// ******************************
//     AUBRAC CORE PARAMETERS 
// ******************************
trait AubracParams extends PipelineParams {  
  // ------------------------------
  //            GLOBAL
  // ------------------------------
  def debug: Boolean
  def pcBoot: String
  def nAddrBit: Int
  def nDataBit: Int 

  // ------------------------------
  //            CHAMP
  // ------------------------------
  def useChamp: Boolean
  def nChampReg: Int
  def useChampExtMie: Boolean
  def useChampExtFr: Boolean
  def useChampExtCst: Boolean
  def nChampTrapLvl: Int
  
  def nField: Int = {
    var nfield: Int = 1
    if (useChampExtFr) {
      nfield = nfield + 1
    }
    return nfield
  }
  override def multiField: Boolean = false
  def nPart: Int
  def nFieldFlushCycle: Int

  def pHfu: HfuParams = new HfuConfig (
    debug = debug,
    pcBoot = pcBoot,
    nHart = nHart,
    nAddrBit = nAddrBit,
    nDataBit = nDataBit,

    useChamp = useChamp,
    nChampReg = nChampReg,
    useChampExtMie = useChampExtMie,
    useChampExtFr = useChampExtFr,
    useChampExtCst = useChampExtCst,
    nChampTrapLvl = nChampTrapLvl,
    
    nPart = nPart,
    nFieldFlushCycle = nFieldFlushCycle
  )
    
  // ------------------------------
  //           FRONT END
  // ------------------------------
  def nFetchInstr: Int
  def useIMemSeq: Boolean
  def useIf1Stage: Boolean
  def nFetchBufferDepth: Int  

  // ------------------------------
  //       NEXT-LINE PREDICTOR
  // ------------------------------
  def useNlp: Boolean
  def nBtbLine: Int
  def nBhtSet: Int
  def nBhtSetEntry: Int
  def nBhtBit: Int
  def useRsbSpec: Boolean
  def nRsbDepth: Int

  // ------------------------------
  //           BACK END
  // ------------------------------
  def useExtM: Boolean
  def useExtA: Boolean
  def useExtB: Boolean
  def useExtZifencei: Boolean
  def useExtZicbo: Boolean
  def nExStage: Int 
  def useMemStage: Boolean
  def useBranchReg: Boolean
  def nCbo: Int = if (useCbo) 1 else 0

  // ------------------------------
  //           L1I CACHE
  // ------------------------------
  def useL1I: Boolean
  def nL1INextDataByte: Int
  def nL1INextLatency: Int

  def useL1IPftch: Boolean
  def nL1IPftchEntry: Int
  def nL1IPftchEntryAcc: Int
  def nL1IPftchMemRead: Int
  def nL1IPftchMemWrite: Int

  def nL1IMem: Int
  def nL1IMemReadPort: Int
  def nL1IMemWritePort: Int

  def slctL1IPolicy: String
  def nL1ISet: Int
  def nL1ILine: Int
  def nL1IData: Int

  // ------------------------------
  //           L1D CACHE
  // ------------------------------
  def useL1D: Boolean
  def nL1DNextDataByte: Int
  def nL1DNextLatency: Int

  def useL1DPftch: Boolean
  def nL1DPftchEntry: Int
  def nL1DPftchEntryAcc: Int
  def nL1DPftchMemRead: Int
  def nL1DPftchMemWrite: Int

  def nL1DMem: Int
  def nL1DMemReadPort: Int
  def nL1DMemWritePort: Int

  def slctL1DPolicy: String
  def nL1DSet: Int
  def nL1DLine: Int
  def nL1DData: Int

  // ------------------------------
  //           L2 CACHE
  // ------------------------------
  def useL2: Boolean
  def nL2NextDataByte: Int
  def useL2ReqReg: Boolean
  def useL2AccReg: Boolean
  def useL2AckReg: Boolean
  def nL2WriteFifoDepth: Int
  def nL2NextFifoDepth: Int
  def nL2PrevLatency: Int = {
    var latency: Int = 1
    if (useL2ReqReg) latency = latency + 1
    if (useL2AccReg) latency = latency + 1
    return latency
  }
  def nL2NextLatency: Int

  def useL2Pftch: Boolean
  def nL2PftchEntry: Int
  def nL2PftchEntryAcc: Int
  def nL2PftchMemRead: Int
  def nL2PftchMemWrite: Int

  def nL2Mem: Int
  def nL2MemReadPort: Int
  def nL2MemWritePort: Int

  def slctL2Policy: String
  def nL2Set: Int
  def nL2Line: Int
  def nL2Data: Int

  // ------------------------------
  //              I/Os
  // ------------------------------
  def nIOAddrBase: String
  def nScratch: Int
  def nCTimer: Int
  def isHpmAct: Array[String]
  def hasHpmMap: Array[String]

  def nUnCacheBase: String
  def nUnCacheByte: String

  // ------------------------------
  //            MEMORY
  // ------------------------------
  // ..............................
  //              L0
  // ..............................
  def pL0DArray: Array[Mb4sParams] = {
    var a: Array[Mb4sParams] = Array(pL0DBus)
    if (useChamp) a = a :+ pHfu.pL0DBus
    a
  }
  def pL0DCrossBus: Mb4sParams = MB4S.node(pL0DArray, false)
  
  def pUnCache: Mb4sMemParams = new Mb4sMemConfig (
    pPort = Array(pL0DCrossBus),
  
    nAddrBase = nUnCacheBase,
    nByte = nUnCacheByte
  )

  def pIO: IOCoreParams = new IOCoreConfig (
    pPort           = Array(pL0DCrossBus)   ,

    debug           = debug           ,
    nHart           = nHart           ,
    nAddrBit        = nAddrBit        ,
    nAddrBase       = nIOAddrBase     ,

    nChampTrapLvl    = nChampTrapLvl  ,

    useReqReg       = false           ,
    nScratch        = nScratch        ,
    nCTimer         = nCTimer         ,
    isHpmAct        = isHpmAct        ,
    hasHpmMap       = hasHpmMap
  )

  def pL0DCross: Mb4sCrossbarParams = new Mb4sCrossbarConfig (
    pMaster = pL0DArray,
    useMem = true,
    pMem = if (useL2 || useL1D){
      Array(pIO, pUnCache)
    } else {
      Array(pIO)
    },
    nDefault = 1,
    nBus = 0,
    
    debug = debug,  
    multiField = false,
    nDepth = 2,
    useDirect = false
  )

  // ..............................
  //              L1
  // ..............................
  def pL1I: L1Params = new L1Config (
    pPrevBus = Array(pL0IBus),

    debug = debug,
    nCbo = nCbo,

    nPart = nPart,

    useReqReg = useIf1Stage,
    useAccReg = false,
    useAckReg = false,
    nNextDataByte = nL1INextDataByte,
    nNextLatency = {
      if (useL2) {
        nL2PrevLatency
      } else {
        nL1INextLatency
      }
    },

    usePftch = useL1IPftch,
    nPftchEntry = nL1IPftchEntry,
    nPftchEntryAcc = nL1IPftchEntryAcc,
    nPftchMemRead = nL1IPftchMemRead,
    nPftchMemWrite = nL1IPftchMemWrite,

    nMem = nL1IMem,
    nMemReadPort = nL1IMemReadPort,
    nMemWritePort = nL1IMemWritePort,

    slctPolicy = slctL1IPolicy,
    nSet = nL1ISet,
    nLine = nL1ILine,
    nData = nL1IData
  )

  def pL1D: L1Params = new L1Config (
    pPrevBus = Array(pL0DCrossBus),

    debug = debug,
    nCbo = nCbo,

    nPart = nPart,

    useReqReg = (nExStage > 1),
    useAccReg = (nExStage > 2),
    useAckReg = useExtA,
    nNextDataByte = nL1DNextDataByte,
    nNextLatency = {
      if (useL2) {
        nL2PrevLatency
      } else {
        nL1INextLatency
      }
    },

    usePftch = useL1DPftch,
    nPftchEntry = nL1DPftchEntry,
    nPftchEntryAcc = nL1DPftchEntryAcc,
    nPftchMemRead = nL1DPftchMemRead,
    nPftchMemWrite = nL1DPftchMemWrite,

    nMem = nL1DMem,
    nMemReadPort = nL1DMemReadPort,
    nMemWritePort = nL1DMemWritePort,

    slctPolicy = slctL1DPolicy,
    nSet = nL1DSet,
    nLine = nL1DLine,
    nData = nL1DData
  )

  def pL1Array: Array[Mb4sParams] = {
    var a: Array[Mb4sParams] = Array()
    if (useL1I) {
      a = a :+ pL1I.pNextBus
    } else {
      a = a :+ pL0IBus
    }
    if (useL1D) {
      a = a :+ pL1D.pNextBus
    } else {
      a = a :+ pL0DCrossBus
    }
    a
  }
  def pL1Bus: Mb4sParams = MB4S.node(pL1Array, true)

  // ..............................
  //              L2
  // ..............................
  def pL2: L2Params = new L2Config (
    pPrevBus = pL1Array,

    debug = debug,
    nCbo = nCbo,

    nPart = nPart,

    nNextDataByte = nL2NextDataByte,
    useReqReg = useL2ReqReg,
    useAccReg = useL2AccReg,
    useAckReg = useL2AckReg || (useExtA && !useL1D),
    nWriteFifoDepth = nL2WriteFifoDepth,
    nNextFifoDepth = nL2NextFifoDepth,
    nNextLatency = nL2NextLatency,

    usePftch = useL2Pftch,
    nPftchEntry = nL2PftchEntry,
    nPftchEntryAcc = nL2PftchEntryAcc,
    nPftchMemRead = nL2PftchMemRead,
    nPftchMemWrite = nL2PftchMemWrite,

    nMem = nL2Mem,
    nMemReadPort = nL2MemReadPort,
    nMemWritePort = nL2MemWritePort,

    slctPolicy = slctL2Policy,
    nSet = nL2Set,
    nLine = nL2Line,
    nData = nL2Data
  )

  def pLLArray: Array[Mb4sParams] = Array(pL0DCrossBus, pL2.pNextBus)
  def pLLDArray: Array[Mb4sParams] = {
    var a: Array[Mb4sParams] = Array(pL0DCrossBus)
    if (useL1D) a = a :+ pL1D.pNextBus
    a
  }

  def pLLBus: Mb4sParams = MB4S.node(pLLArray, true)
  def pLLIBus: Mb4sParams = {
    if (useL1I) {
      pL1I.pNextBus
    } else {
      pL0IBus
    }
  }
  def pLLDBus: Mb4sParams = MB4S.node(pLLDArray, true)
  def pLLCross: Mb4sCrossbarParams = new Mb4sCrossbarConfig (
    pMaster = if (useL2) pLLArray else pLLDArray,
    useMem = false,
    pMem = Array(),
    nDefault = 0,
    nBus = 1,
    
    debug = debug,  
    multiField = true,
    nDepth = 4,
    useDirect = false
  )
}

// ******************************
//         AUBRAC CONFIG 
// ******************************
case class AubracConfig (
  // ------------------------------
  //            GLOBAL
  // ------------------------------
  debug: Boolean,
  pcBoot: String,
  nAddrBit: Int,
  nDataBit: Int, 

  // ------------------------------
  //             CHAMP
  // ------------------------------
  useChamp: Boolean,
  nChampReg: Int,
  useChampExtMie: Boolean,
  useChampExtFr: Boolean,
  useChampExtCst: Boolean,
  nChampTrapLvl: Int,
  
  nPart: Int,
  nFieldFlushCycle: Int,

  // ------------------------------
  //           FRONT END
  // ------------------------------
  nFetchInstr: Int,
  useIMemSeq: Boolean,
  useIf1Stage: Boolean,
  nFetchBufferDepth: Int,  

  // ------------------------------
  //       NEXT-LINE PREDICTOR
  // ------------------------------
  useNlp: Boolean,
  nBtbLine: Int,
  nBhtSet: Int,
  nBhtSetEntry: Int,
  nBhtBit: Int,
  useRsbSpec: Boolean,
  nRsbDepth: Int,

  // ------------------------------
  //           BACK END
  // ------------------------------
  useExtM: Boolean,
  useExtA: Boolean,
  useExtB: Boolean,
  useExtZifencei: Boolean,
  useExtZicbo: Boolean,
  nExStage: Int,
  useMemStage: Boolean,
  useBranchReg: Boolean,

  // ------------------------------
  //              I/Os
  // ------------------------------
  nIOAddrBase: String,
  nScratch: Int,
  nCTimer: Int,
  isHpmAct: Array[String],
  hasHpmMap: Array[String],

  nUnCacheBase: String,
  nUnCacheByte: String,

  // ------------------------------
  //           L1I CACHE
  // ------------------------------
  useL1I: Boolean,
  nL1INextDataByte: Int,
  nL1INextLatency: Int,

  useL1IPftch: Boolean,
  nL1IPftchEntry: Int,
  nL1IPftchEntryAcc: Int,
  nL1IPftchMemRead: Int,
  nL1IPftchMemWrite: Int,

  nL1IMem: Int,
  nL1IMemReadPort: Int,
  nL1IMemWritePort: Int,

  slctL1IPolicy: String,
  nL1ISet: Int,
  nL1ILine: Int,
  nL1IData: Int,

  // ------------------------------
  //           L1D CACHE
  // ------------------------------
  useL1D: Boolean,
  nL1DNextDataByte: Int,
  nL1DNextLatency: Int,

  useL1DPftch: Boolean,
  nL1DPftchEntry: Int,
  nL1DPftchEntryAcc: Int,
  nL1DPftchMemRead: Int,
  nL1DPftchMemWrite: Int,

  nL1DMem: Int,
  nL1DMemReadPort: Int,
  nL1DMemWritePort: Int,

  slctL1DPolicy: String,
  nL1DSet: Int,
  nL1DLine: Int,
  nL1DData: Int,

  // ------------------------------
  //           L2 CACHE
  // ------------------------------
  useL2: Boolean,
  nL2NextDataByte: Int,
  useL2ReqReg: Boolean,
  useL2AccReg: Boolean,
  useL2AckReg: Boolean,
  nL2WriteFifoDepth: Int,
  nL2NextFifoDepth: Int,
  nL2NextLatency: Int,

  useL2Pftch: Boolean,
  nL2PftchEntry: Int,
  nL2PftchEntryAcc: Int,
  nL2PftchMemRead: Int,
  nL2PftchMemWrite: Int,

  nL2Mem: Int,
  nL2MemReadPort: Int,
  nL2MemWritePort: Int,

  slctL2Policy: String,
  nL2Set: Int,
  nL2Line: Int,
  nL2Data: Int
) extends AubracParams