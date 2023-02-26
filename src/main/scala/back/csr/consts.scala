/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:58:17 pm                                       *
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


// ******************************
//            MICRO-OP
// ******************************
object UOP {
  val NBIT  = 2

  val X     = 0.U(NBIT.W)
  val W     = 1.U(NBIT.W)
  val S     = 2.U(NBIT.W)
  val C     = 3.U(NBIT.W)
}

object TRAP {
  val NBIT  = 2

  val IRQ   = 0.U(NBIT.W)
  val EXC   = 1.U(NBIT.W)
  
  val MRET  = 2.U(NBIT.W)
  val SRET  = 3.U(NBIT.W)
}