/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:06:08 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.nlp

import chisel3._
import chisel3.util._

       
// ******************************
//              BTB
// ******************************   
object BtbConfigBase extends BtbConfig (
  useDome = true,
  nDome = 2,

  nReadPort = 2,
  nLine = 8,
  nTagBit = 30,
  nTargetBit = 30
) 

// ******************************
//              BHT
// ******************************
object BhtConfigBase extends BhtConfig (
  useDome = true,
  nDome = 2,

  nReadPort = 2,
  nSet = 2,
  nSetEntry = 4,
  nBit = 2
)

// ******************************
//              RSB
// ******************************
object RsbConfigBase extends RsbConfig (
  useDome = true,
  nDome = 2,

  useSpec = true,
  nDepth = 16,
  nTargetBit = 30
)

// ******************************
//              NLP
// ******************************
object NlpConfigBase extends NlpConfig (
  useDome = true,
  nDome = 2,

  nAddrBit = 32,
  nInstrByte = 4,
  nFetchInstr = 2,

  nBtbLine = 32,
  
  nBhtSet = 2,
  nBhtSetEntry = 2,
  nBhtBit = 2,

  useRsbSpec = true,
  nRsbDepth = 32
) 
