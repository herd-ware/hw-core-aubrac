/*
 * File: table-csr.scala                                                       *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:59:13 pm                                       *
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
import herd.core.aubrac.back.csr.{UOP => CSRUOP}


// ************************************************************
//
//          DECODE TABLES TO EXTRACT CSR INFORMATIONS
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
    BASE.CSRRW0   -> List(  0.B,  1.B,    CSRUOP.W),
    BASE.CSRRW    -> List(  1.B,  1.B,    CSRUOP.W),
    BASE.CSRRS0   -> List(  1.B,  0.B,    CSRUOP.S),
    BASE.CSRRS    -> List(  1.B,  1.B,    CSRUOP.S),
    BASE.CSRRC0   -> List(  1.B,  0.B,    CSRUOP.C),
    BASE.CSRRC    -> List(  1.B,  1.B,    CSRUOP.C),
    BASE.CSRRWI0  -> List(  0.B,  1.B,    CSRUOP.W),
    BASE.CSRRWI   -> List(  1.B,  1.B,    CSRUOP.W),
    BASE.CSRRSI0  -> List(  1.B,  0.B,    CSRUOP.S),
    BASE.CSRRSI   -> List(  1.B,  1.B,    CSRUOP.S),
    BASE.CSRRCI0  -> List(  1.B,  0.B,    CSRUOP.C),
    BASE.CSRRCI   -> List(  1.B,  1.B,    CSRUOP.C))
}
