/*
 * File: table-int.scala                                                       *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:59:19 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.back

import chisel3._
import chisel3.util._

import herd.common.isa.base.{INSTR => BASE}
import herd.common.isa.priv.{INSTR => PRIV}
import herd.common.isa.ceps.{INSTR => CEPS}


// ************************************************************
//
//     DECODE TABLES TO EXTRACT GLOBAL AND EX INFORMATIONS
//
// ************************************************************
trait TABLEINT
{  
    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
  val default: List[UInt] =
               List[UInt](      0.B,  0.B,  0.B,    0.B,  0.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X)
  val table: Array[(BitPat, List[UInt])]
}

object TABLEINT32I extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.LUI          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ADD,     1.B,  0.B,  0.B,  OP.IMM1,  OP.IMM2,  OP.X,     IMM.isU,  IMM.is0),
    BASE.AUIPC        -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ADD,     1.B,  0.B,  0.B,  OP.IMM1,  OP.PC,    OP.X,     IMM.isU,  IMM.X),
    BASE.JAL          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.BRU,      INTUOP.JAL,     0.B,  1.B,  0.B,  OP.PC,    OP.IMM1,  OP.X,     IMM.isJ,  IMM.X),
    BASE.JALR         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.BRU,      INTUOP.JALR,    0.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.BEQ          -> List(  1.B,  0.B,  0.B,    0.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.BEQ,     1.B,  1.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
    BASE.BNE          -> List(  1.B,  0.B,  0.B,    0.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.BNE,     1.B,  1.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
    BASE.BLT          -> List(  1.B,  0.B,  0.B,    0.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.BLT,     1.B,  1.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
    BASE.BGE          -> List(  1.B,  0.B,  0.B,    0.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.BGE,     1.B,  1.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
    BASE.BLTU         -> List(  1.B,  0.B,  0.B,    0.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.BLT,     0.B,  0.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
    BASE.BGEU         -> List(  1.B,  0.B,  0.B,    0.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.BGE,     0.B,  0.B,  1.B,  OP.XREG,  OP.XREG,  OP.IMM1,  IMM.isB,  IMM.X),
    BASE.LB           -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.LH           -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.LW           -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.LBU          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.LHU          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.SB           -> List(  1.B,  0.B,  0.B,    0.B,  0.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.isS,  IMM.X),
    BASE.SH           -> List(  1.B,  0.B,  0.B,    0.B,  0.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.isS,  IMM.X),
    BASE.SW           -> List(  1.B,  0.B,  0.B,    0.B,  0.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.isS,  IMM.X),
    BASE.ADDI         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ADD,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.SLTI         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SLT,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.SLTIU        -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SLT,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.XORI         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.XOR,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.ORI          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.OR,      1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.ANDI         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.AND,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.SLLI         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHL,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.SRLI         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHR,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.SRAI         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.ADD          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ADD,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SUB          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SUB,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SLL          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHL,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SLT          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SLT,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SLTU         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SLT,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.XOR          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.XOR,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SRL          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHR,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SRA          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.OR           -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.OR,      1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.AND          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.AND,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),

    BASE.FENCETSO     -> List(  1.B,  1.B,  1.B,    0.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.FENCE,   0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.PAUSE        -> List(  1.B,  1.B,  1.B,    0.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.FENCE,   0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.FENCE        -> List(  1.B,  1.B,  1.B,    0.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.FENCE,   0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X))
}

object TABLEINT64I extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.LWU          -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,   OP.IMM1,  OP.X,    IMM.isI,  IMM.X),
    BASE.LD           -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,   OP.IMM1,  OP.X,    IMM.isI,  IMM.X),
    BASE.SD           -> List(  1.B,  0.B,  0.B,    0.B,  0.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  1.B,  0.B,  OP.XREG,   OP.IMM1,  OP.XREG, IMM.isS,  IMM.X),
    BASE.ADDIW        -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ADD,     1.B,  1.B,  0.B,  OP.XREG,   OP.IMM1,  OP.X,    IMM.isI,  IMM.X),
    BASE.SLLIW        -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHL,     1.B,  1.B,  0.B,  OP.XREG,   OP.IMM1,  OP.X,    IMM.isI,  IMM.X),
    BASE.SRLIW        -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,   OP.IMM1,  OP.X,    IMM.isI,  IMM.X),
    BASE.SRAIW        -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,   OP.IMM1,  OP.X,    IMM.isI,  IMM.X),
    BASE.ADDW         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ADD,     1.B,  1.B,  0.B,  OP.XREG,   OP.XREG,  OP.X,    IMM.X,    IMM.X),
    BASE.SUBW         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SUB,     1.B,  1.B,  0.B,  OP.XREG,   OP.XREG,  OP.X,    IMM.X,    IMM.X),
    BASE.SLLW         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHL,     1.B,  1.B,  0.B,  OP.XREG,   OP.XREG,  OP.X,    IMM.X,    IMM.X),
    BASE.SRLW         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,   OP.XREG,  OP.X,    IMM.X,    IMM.X),
    BASE.SRAW         -> List(  1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHR,     1.B,  1.B,  0.B,  OP.XREG,   OP.XREG,  OP.X,    IMM.X,    IMM.X))
}

object TABLEINTCSR extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.CSRRW0       -> List(  1.B,  0.B,  1.B,    0.B,  0.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
    BASE.CSRRW        -> List(  1.B,  0.B,  1.B,    0.B,  1.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
    BASE.CSRRS0       -> List(  1.B,  0.B,  1.B,    0.B,  1.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
    BASE.CSRRS        -> List(  1.B,  0.B,  1.B,    0.B,  1.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
    BASE.CSRRC0       -> List(  1.B,  0.B,  1.B,    0.B,  1.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
    BASE.CSRRC        -> List(  1.B,  0.B,  1.B,    0.B,  1.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.IMM2,  IMM.X,    IMM.isI),
    BASE.CSRRWI0      -> List(  1.B,  0.B,  1.B,    0.B,  0.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI),
    BASE.CSRRWI       -> List(  1.B,  0.B,  1.B,    0.B,  1.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI),
    BASE.CSRRSI0      -> List(  1.B,  0.B,  1.B,    0.B,  1.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI),
    BASE.CSRRSI       -> List(  1.B,  0.B,  1.B,    0.B,  1.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI),
    BASE.CSRRCI0      -> List(  1.B,  0.B,  1.B,    0.B,  1.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI),
    BASE.CSRRCI       -> List(  1.B,  0.B,  1.B,    0.B,  1.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.IMM1,  OP.X,     OP.IMM2,  IMM.isC,  IMM.isI))
} 

object TABLEINT32M extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.MUL        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.MUL,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.MULH       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.MULH,    1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.MULHU      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.MULH,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.MULHSU     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.MULH,    1.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.DIV        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.DIV,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.DIVU       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.DIV,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.REM        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.REM,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.REMU       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.REM,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X))
}

object TABLEINT64M extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.MULW       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.MUL,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.DIVW       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.DIV,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.DIVUW      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.DIV,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.REMW       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.REM,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.REMUW      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.REM,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X))
}

object TABLEINT32A extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.LRW        -> List(    1.B,  0.B,  1.B,    1.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.is0,  IMM.X),
    BASE.SCW        -> List(    1.B,  0.B,  1.B,    1.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOSWAPW   -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOADDW    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOXORW    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOANDW    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOORW     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOMINW    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOMAXW    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOMINUW   -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOMAXUW   -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X))
}

object TABLEINT64A extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.LRW        -> List(    1.B,  0.B,  1.B,    1.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.is0,  IMM.X),
    BASE.SCW        -> List(    1.B,  0.B,  1.B,    1.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOSWAPW   -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOADDW    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOXORW    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOANDW    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOORW     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOMINW    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOMAXW    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOMINUW   -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X),
    BASE.AMOMAXUW   -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    1.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.XREG,  IMM.is0,  IMM.X))
}

object TABLEINT32B extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.SH1ADD     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SH1ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SH2ADD     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SH2ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SH3ADD     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SH3ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.ANDN       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ANDN,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.ORN        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ORN,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.XNOR       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.XNOR,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.CLZ        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.CLZ,     0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.CTZ        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.CTZ,     0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.CPOP       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.CPOP,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.MAX        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.MAX,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.MAXU       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.MAX,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.MIN        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.MIN,     1.B,  1.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.MINU       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.MIN,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SEXTB      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.EXTB,    1.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.SEXTH      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.EXTH,    1.B,  1.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.ZEXTH32    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.EXTH,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.ROL        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ROL,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.ROR        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ROR,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.RORI       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ROR,     0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.ORCB       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ORCB,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.REV832     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.REV8,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.CLMUL      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.CLMUL,   0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.CLMULH     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.CLMULH,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.CLMULR     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.MULDIV,   INTUOP.CLMULR,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.BCLR       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.BCLR,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.BCLRI      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.BCLR,    0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.BEXT       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.BEXT,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.BEXTI      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.BEXT,    0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.BINV       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.BINV,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.BINVI      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.BINV,    0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.BSET       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.BSET,    0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.BSETI      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.BSET,    0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X))
}

object TABLEINT64B extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.ADDUW      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ADD,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SH1ADDUW   -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SH1ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SH2ADDUW   -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SH2ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SH3ADDUW   -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SH3ADD,  0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.SLLIUW     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.SHL,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.CLZW       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.CLZ,     0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.CTZW       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.CTZ,     0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.CPOPW      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.CPOP,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.ZEXTH64    -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.EXTH,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    BASE.ROLW       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ROL,     0.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.RORW       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ROR,     1.B,  0.B,  0.B,  OP.XREG,  OP.XREG,  OP.X,     IMM.X,    IMM.X),
    BASE.RORIW      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.ROR,     1.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X),
    BASE.REV864     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.ALU,      INTUOP.REV8,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X))
}

object TABLEINTZIFENCEI extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.FENCEI     -> List(    1.B,  0.B,  1.B,    1.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.FENCEI,  0.B,  0.B,  0.B,  OP.XREG,  OP.IMM1,  OP.X,     IMM.isI,  IMM.X))
}

object TABLEINTZICBO extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    BASE.CBOCLEAN   -> List(    1.B,  0.B,  1.B,    1.B,  0.B,    1.B,  INTUNIT.BRU,      INTUOP.CLEAN,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
    BASE.CBOINVAL   -> List(    1.B,  0.B,  1.B,    1.B,  0.B,    1.B,  INTUNIT.BRU,      INTUOP.INVAL,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
    BASE.CBOFLUSH   -> List(    1.B,  0.B,  1.B,    1.B,  0.B,    1.B,  INTUNIT.BRU,      INTUOP.FLUSH,   0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
    BASE.CBOZERO    -> List(    1.B,  0.B,  1.B,    1.B,  0.B,    1.B,  INTUNIT.BRU,      INTUOP.ZERO,    0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
    BASE.PREFETCHI  -> List(    1.B,  0.B,  0.B,    1.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.PFTCHE,  0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
    BASE.PREFETCHR  -> List(    1.B,  0.B,  0.B,    1.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.PFTCHR,  0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X),
    BASE.PREFETCHW  -> List(    1.B,  0.B,  0.B,    1.B,  0.B,    0.B,  INTUNIT.BRU,      INTUOP.PFTCHW,  0.B,  0.B,  0.B,  OP.XREG,  OP.X,      OP.X,    IMM.X,    IMM.X))
}

object TABLEINTPRIV extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    PRIV.MRET       -> List(    1.B,  1.B,  1.B,    1.B,  0.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X),
    PRIV.WFI        -> List(    1.B,  1.B,  1.B,    1.B,  0.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.XREG,  OP.X,     OP.X,     IMM.X,    IMM.X))
}

object TABLEINTCEPS extends TABLEINT {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                        is Valid ?         wait Empty ?               Int Unit ?                   S1 Sign            S1 Type ?                    Imm1 Type ?
    //                           |   is End ?        |                         |                           |   S2 Sign        |       S2 Type ?              |     Imm2 Type ?
    //                           |     | is Serial ? |          Gen Exc ?      |             Int Uop ?     |     |   S3 Sign  |         |       S3 Type ?    |         |
    //                           |     |     |       |   WB ?      |           |                |          |     |     |      |         |         |          |         |
    //                           |     |     |       |     |       |           |                |          |     |     |      |         |         |          |         |
    CEPS.ADD        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
    CEPS.SUB        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
    CEPS.SET        -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
    CEPS.CLEAR      -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
    CEPS.MVCX       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
    CEPS.MVXC       -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isR,  IMM.X),
    CEPS.MV         -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    CEPS.LOAD       -> List(    1.B,  0.B,  0.B,    0.B,  0.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isS,  IMM.X),
    CEPS.STORE      -> List(    1.B,  0.B,  0.B,    0.B,  0.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.IMM1,  IMM.isS,  IMM.X),
    CEPS.SWITCHV    -> List(    1.B,  1.B,  1.B,    1.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    CEPS.SWITCHL    -> List(    1.B,  1.B,  1.B,    1.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    CEPS.SWITCHC    -> List(    1.B,  1.B,  1.B,    1.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    CEPS.SWITCHJV   -> List(    1.B,  1.B,  1.B,    1.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.X,     IMM.X,    IMM.X),
    CEPS.SWITCHJL   -> List(    1.B,  1.B,  1.B,    1.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.X,     IMM.X,    IMM.X),
    CEPS.SWITCHJC   -> List(    1.B,  1.B,  1.B,    1.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.XREG,  OP.X,     IMM.X,    IMM.X),
    CEPS.CHECKV     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    CEPS.CHECKU     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    CEPS.CHECKL     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    CEPS.CHECKC     -> List(    1.B,  0.B,  0.B,    0.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    CEPS.SWITCHRL0  -> List(    1.B,  1.B,  1.B,    1.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    CEPS.SWITCHRL1  -> List(    1.B,  1.B,  1.B,    1.B,  1.B,    0.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X),
    CEPS.WFI        -> List(    1.B,  1.B,  1.B,    0.B,  0.B,    1.B,  INTUNIT.X,        INTUOP.X,       0.B,  0.B,  0.B,  OP.X,     OP.X,     OP.X,     IMM.X,    IMM.X))   
}

