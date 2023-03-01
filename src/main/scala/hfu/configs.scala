/*
 * File: configs.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:33:18 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.hfu

import chisel3._
import chisel3.util._


object HfuConfigBase extends HfuConfig (
  debug = false,
  pcBoot = "40",
  nHart = 2,
  nAddrBit = 32,
  nDataBit = 32,
  
  useChamp = true,
  nChampReg = 4,
  useChampExtMie = true,
  useChampExtFr = true,
  useChampExtCst = true,
  nChampTrapLvl = 2,
  nPart = 4,
  nDomeFlushCycle = 10
)
