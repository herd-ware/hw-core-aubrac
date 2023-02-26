/*
 * File: configs.scala                                                         *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:58:13 pm                                       *
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


object CsrConfigBase extends CsrConfig (
  debug = false,
  nHart = 1,
  nAddrBit = 32,
  nDataBit = 32,
  
  useCeps = true,
  nDome = 1,
  nPart = 1,
  nCepsTrapLvl = 1
) 