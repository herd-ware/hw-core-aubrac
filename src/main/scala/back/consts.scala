/*
 * File: consts.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:31:22 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.back

import chisel3._


// ******************************
//            PIPELINE
// ******************************
object STATE {
  def NBIT  = 3  

  def RUN   = 0.U(NBIT.W)
  def STOP  = 1.U(NBIT.W)
  def IRQ   = 2.U(NBIT.W)
  def WFI   = 3.U(NBIT.W)
  def EXC   = 4.U(NBIT.W)

  def MRET  = 5.U(NBIT.W)
  def SRET  = 6.U(NBIT.W)
}

// ******************************
//            OPERAND
// ******************************
object OP {
  def NBIT    = 3
  def X       = 0.U(NBIT.W)
  
  def PC      = 1.U(NBIT.W)
  def INSTR   = 2.U(NBIT.W)
  def IMM1    = 3.U(NBIT.W)
  def IMM2    = 4.U(NBIT.W)
  def XREG    = 5.U(NBIT.W)
}

// ******************************
//            IMMEDIATE
// ******************************
object IMM {
  def NBIT  = 4
  def X     = 0.U(NBIT.W)

  def is0   = 0.U(NBIT.W)
  def isR   = 1.U(NBIT.W)
  def isI   = 2.U(NBIT.W)
  def isS   = 3.U(NBIT.W)
  def isB   = 4.U(NBIT.W)
  def isU   = 5.U(NBIT.W)
  def isJ   = 6.U(NBIT.W)
  def isC   = 7.U(NBIT.W)
  def isV   = 8.U(NBIT.W)
  def isZ   = 9.U(NBIT.W)
}

// ******************************
//            INTEGER 
// ******************************
object INTUNIT {
  def NBIT    = 3

  def X       = 0.U(NBIT.W)
  def ALU     = 1.U(NBIT.W)
  def BRU     = 2.U(NBIT.W)
  def MULDIV  = 3.U(NBIT.W)
}

object INTUOP {
  def NBIT    = 5
  def X       = 0.U(NBIT.W)

  // ------------------------------
  //              BRU
  // ------------------------------
  def JAL     = 2.U(NBIT.W)
  def JALR    = 3.U(NBIT.W)

  def FENCE   = 4.U(NBIT.W)
  def FENCEI  = 5.U(NBIT.W)

  def BEQ     = 8.U(NBIT.W)
  def BNE     = 9.U(NBIT.W)
  def BLT     = 10.U(NBIT.W)
  def BGE     = 11.U(NBIT.W)

  def CLEAN   = 16.U(NBIT.W)
  def INVAL   = 17.U(NBIT.W)
  def FLUSH   = 18.U(NBIT.W)
  def ZERO    = 19.U(NBIT.W)
  def PFTCHE  = 20.U(NBIT.W)
  def PFTCHR  = 21.U(NBIT.W)
  def PFTCHW  = 22.U(NBIT.W)

  // ------------------------------
  //              ALU
  // ------------------------------
  def ADD     = 1.U(NBIT.W)
  def SUB     = 2.U(NBIT.W)
  def SLT     = 3.U(NBIT.W)
  def OR      = 4.U(NBIT.W)
  def AND     = 5.U(NBIT.W)
  def XOR     = 6.U(NBIT.W)
  def SHR     = 7.U(NBIT.W)
  def SHL     = 8.U(NBIT.W)

  def SH1ADD  = 9.U(NBIT.W)
  def SH2ADD  = 10.U(NBIT.W)
  def SH3ADD  = 11.U(NBIT.W)
  def ORN     = 12.U(NBIT.W)
  def ANDN    = 13.U(NBIT.W)
  def XNOR    = 14.U(NBIT.W)
  def CLZ     = 15.U(NBIT.W)
  def CTZ     = 16.U(NBIT.W)
  def CPOP    = 17.U(NBIT.W)
  def MAX     = 18.U(NBIT.W)
  def MIN     = 19.U(NBIT.W)
  def EXTB    = 20.U(NBIT.W)
  def EXTH    = 21.U(NBIT.W)
  def ROL     = 22.U(NBIT.W)
  def ROR     = 23.U(NBIT.W)
  def ORCB    = 24.U(NBIT.W)
  def REV8    = 25.U(NBIT.W)
  
  def BCLR    = 26.U(NBIT.W)
  def BEXT    = 27.U(NBIT.W)
  def BINV    = 28.U(NBIT.W)
  def BSET    = 29.U(NBIT.W)

  // ------------------------------
  //            MULDIV
  // ------------------------------
  def MUL     = 1.U(NBIT.W)
  def MULH    = 2.U(NBIT.W)
  def DIV     = 8.U(NBIT.W)
  def REM     = 10.U(NBIT.W)

  def CLMUL   = 4.U(NBIT.W)
  def CLMULH  = 5.U(NBIT.W)
  def CLMULR  = 6.U(NBIT.W)
}

object INTSIZE {
  def NBIT  = 1

  def X     = 0.U(NBIT.W)
  def W     = 1.U(NBIT.W)
}

// ******************************
//         LOAD-STORE UNIT
// ******************************
object LSUSIGN {
  val NBIT  = 1
  val X     = 0.U(NBIT.W)

  val U     = 0.U(NBIT.W)
  val S     = 1.U(NBIT.W)
}

object LSUSIZE {
  val NBIT  = 2
  val X     = 0.U(NBIT.W)

  val B     = 0.U(NBIT.W)
  val H     = 1.U(NBIT.W)
  val W     = 2.U(NBIT.W)
  val D     = 3.U(NBIT.W)
}

// ******************************
//            EXTERNAL
// ******************************
object EXT {
  def NBIT  = 2
  def X     = 0.U(NBIT.W)

  def NONE  = 0.U(NBIT.W)
  def HFU   = 1.U(NBIT.W)
}