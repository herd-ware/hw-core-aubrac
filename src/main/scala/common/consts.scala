/*
 * File: consts.scala                                                          *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:00:11 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.common

import chisel3._
import chisel3.util._


// ******************************
//          TRAP SOURCE
// ******************************
object TRAPSRC {
  def NBIT  = 3  

  def IRQ   = 0.U(NBIT.W)
  def WFI   = 1.U(NBIT.W)
  def EXC   = 2.U(NBIT.W)

  def MRET  = 4.U(NBIT.W)
  def SRET  = 5.U(NBIT.W)
}