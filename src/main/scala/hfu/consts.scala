/*
 * File: consts.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:27:46 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.hfu

import chisel3._


// ******************************
//           DECODING
// ******************************
object CODE {
  def NBIT      = 5

  def X         = 0.U(NBIT.W)
  def ADD       = 1.U(NBIT.W)
  def SUB       = 2.U(NBIT.W)
  def SET       = 3.U(NBIT.W)
  def CLEAR     = 4.U(NBIT.W)
  def MVCX      = 5.U(NBIT.W)
  def MVXC      = 6.U(NBIT.W)
  def MV        = 7.U(NBIT.W)
  def LOAD      = 8.U(NBIT.W)
  def STORE     = 9.U(NBIT.W)
  def SWITCHV   = 10.U(NBIT.W)
  def SWITCHL   = 11.U(NBIT.W)
  def SWITCHC   = 12.U(NBIT.W)  
  def SWITCHJV  = 13.U(NBIT.W)
  def SWITCHJL  = 14.U(NBIT.W)
  def SWITCHJC  = 15.U(NBIT.W) 
  def TRAP      = 16.U(NBIT.W)
  def CHECKV    = 17.U(NBIT.W)
  def CHECKU    = 18.U(NBIT.W)
  def CHECKL    = 19.U(NBIT.W)
  def CHECKC    = 20.U(NBIT.W)
  def RETL0     = 21.U(NBIT.W)
  def RETL1     = 22.U(NBIT.W)
}

object OP {
  def NBIT    = 3
  def X       = 0.U(NBIT.W)

  def ZERO    = 0.U(NBIT.W)
  def IN      = 1.U(NBIT.W)
  def CONF    = 2.U(NBIT.W)
  def VALUE   = 3.U(NBIT.W)
  def TL0EPC  = 4.U(NBIT.W)
  def TL1EPC  = 5.U(NBIT.W)
}

// ******************************
//           MICRO-OP
// ******************************
object ALUUOP {
  def NBIT  = 3
  def X     = 0.U(NBIT.W)

  def ADD   = 1.U(NBIT.W)
  def SUB   = 2.U(NBIT.W)
  def SET   = 3.U(NBIT.W)
  def CLEAR = 4.U(NBIT.W)
  def IN1   = 5.U(NBIT.W)
  def IN2   = 6.U(NBIT.W)
  def ADDR  = 7.U(NBIT.W)
}

object CHECKUOP {
  def NBIT  = 3
  def X     = 0.U(NBIT.W)

  def V     = 0.U(NBIT.W)
  def U     = 1.U(NBIT.W)
  def L     = 2.U(NBIT.W)
  def C     = 3.U(NBIT.W)
  def M     = 4.U(NBIT.W)
  def J     = 5.U(NBIT.W)
  def R     = 6.U(NBIT.W)
}

object SWUOP {
  def NBIT  = 2

  def X     = 0.U(NBIT.W)
  def C     = 1.U(NBIT.W)
  def V     = 2.U(NBIT.W)
  def L     = 3.U(NBIT.W)
}

object RMRUOP {
  def NBIT    = 1
  def X       = 0.U(NBIT.W)

  def SWITCH  = 0.U(NBIT.W)
  def NOFR    = 1.U(NBIT.W)
}

object RMRMUX {
  def NBIT  = 1
  def X     = 0.U(NBIT.W)

  def EXE   = 0.U(NBIT.W)
  def FR    = 1.U(NBIT.W)
}