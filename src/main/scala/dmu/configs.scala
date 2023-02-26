/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:03:26 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.dmu

import chisel3._
import chisel3.util._


object DmuConfigBase extends DmuConfig (
  debug = false,
  pcBoot = "40",
  nHart = 2,
  nAddrBit = 32,
  nDataBit = 32,
  
  useCeps = true,
  useCepsExtMie = true,
  useCepsExtFr = true,
  useCepsExtCst = true,
  nCepsTrapLvl = 2,
  nPart = 4,
  nDomeCfg = 4,
  nDomeFlushCycle = 10,
)
