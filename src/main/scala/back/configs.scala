/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:34:57 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.back

import chisel3._


object DecoderConfigBase extends DecoderConfig (
  nHart = 1,
  nBackPort = 1,
  nAddrBit = 32,
  nDataBit = 32,

  useChamp = false,
  useExtM = true,
  useExtA = false,
  useExtB = false,
  useExtZifencei = true,
  useExtZicbo = false
)

object GprConfigBase extends GprConfig (
  debug = true,
  nHart = 1,
  nDataBit = 32,

  nGprReadPhy = 2,
  nGprWritePhy = 1,
  nGprReadLog = 2,
  nGprWriteLog = 1,
  nGprBypass = 3
)

object BackConfigBase extends BackConfig (
  debug = true,
  pcBoot = "40",
  nAddrBit = 32,
  nDataBit = 32,
  
  useChamp = true,
  nField = 1,
  nPart = 1,
  nChampTrapLvl = 1,

  useExtM = true,
  useExtA = false,
  useExtB = false,
  useExtZifencei = true,
  useExtZicbo = true,
  nExStage = 3,
  useMemStage = true,
  useBranchReg = true
)