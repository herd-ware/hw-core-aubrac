/*
 * File: table-csr.scala                                                       *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-08 09:36:49 am                                       *
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

import herd.common.isa.riscv.{INSTR => RISCV}
import herd.core.aubrac.back.csr.{UOP => CSRUOP}


// ************************************************************
//
//          DECODE TABLES TO EXTRACT CSR INFORMATION
//
// ************************************************************
object TABLECSR {
  val default: List[UInt] =
               List[UInt](  0.B,  0.B,    CSRUOP.X)
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                           CSR Wen ?
  //                               |         CSR Uop ?
  //                      CSR Ren ?|            |
  //                         |     |            |
  //                         |     |            |
    RISCV.CSRRW0  -> List(  0.B,  1.B,    CSRUOP.W),
    RISCV.CSRRW   -> List(  1.B,  1.B,    CSRUOP.W),
    RISCV.CSRRS0  -> List(  1.B,  0.B,    CSRUOP.S),
    RISCV.CSRRS   -> List(  1.B,  1.B,    CSRUOP.S),
    RISCV.CSRRC0  -> List(  1.B,  0.B,    CSRUOP.C),
    RISCV.CSRRC   -> List(  1.B,  1.B,    CSRUOP.C),
    RISCV.CSRRWI0 -> List(  0.B,  1.B,    CSRUOP.W),
    RISCV.CSRRWI  -> List(  1.B,  1.B,    CSRUOP.W),
    RISCV.CSRRSI0 -> List(  1.B,  0.B,    CSRUOP.S),
    RISCV.CSRRSI  -> List(  1.B,  1.B,    CSRUOP.S),
    RISCV.CSRRCI0 -> List(  1.B,  0.B,    CSRUOP.C),
    RISCV.CSRRCI  -> List(  1.B,  1.B,    CSRUOP.C))
}
