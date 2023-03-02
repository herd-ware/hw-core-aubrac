/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:03:43 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.front

import chisel3._
import chisel3.util._


object FrontConfigBase extends FrontConfig (
  debug = true,
  pcBoot = "40",
  nHart = 1,
  
  useField = false,
  nField = 1,
  nPart = 1,

  nAddrBit = 32,
  nInstrByte = 4,
  nFetchInstr = 2,

  useIMemSeq = false,
  useIf1Stage = true,
  useIf2Stage = true,
  nFetchBufferDepth = 4,
  useNlp = true,
  useFastJal = true,

  nBackPort = 1
)
