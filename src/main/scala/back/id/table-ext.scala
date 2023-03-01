/*
 * File: table-ext.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:30:48 pm
 * Modified By: Mathieu Escouteloup
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
import herd.common.isa.champ.{INSTR => CHAMP}
import herd.core.aubrac.hfu.{CODE => HFUCODE, OP => HFUOP}

trait TABLEEXT
{
  //                          Ext unit       Code             S1 ?          S2 ?          S3 ?
  //                             |             |               |             |             |
  val default: List[UInt] =
               List[UInt](    EXT.NONE,       0.U,            0.U,          0.U,          0.U)
  val table: Array[(BitPat, List[UInt])]
}

object TABLEEXT32I extends TABLEEXT {
  val table : Array[(BitPat, List[UInt])] =    
              Array[(BitPat, List[UInt])](
  //                          Ext unit       Code             S1 ?          S2 ?          S3 ?
  //                             |             |               |             |             |
    RISCV.ADD       -> List(  EXT.NONE,       0.U,            0.U,          0.U,          0.U))
}

object TABLEEXTHFU extends TABLEEXT {
  val table : Array[(BitPat, List[UInt])] =    
              Array[(BitPat, List[UInt])](
  //                          Ext unit       Code             S1 ?          S2 ?          S3 ?
  //                             |             |               |             |             |
    CHAMP.ADD       -> List(  EXT.HFU,  HFUCODE.ADD,      HFUOP.VALUE,  HFUOP.IN,     HFUOP.IN  ),
    CHAMP.SUB       -> List(  EXT.HFU,  HFUCODE.SUB,      HFUOP.VALUE,  HFUOP.IN,     HFUOP.IN  ),
    CHAMP.SET       -> List(  EXT.HFU,  HFUCODE.SET,      HFUOP.VALUE,  HFUOP.IN,     HFUOP.IN  ),
    CHAMP.CLEAR     -> List(  EXT.HFU,  HFUCODE.CLEAR,    HFUOP.VALUE,  HFUOP.IN,     HFUOP.IN  ),
    CHAMP.MVCX      -> List(  EXT.HFU,  HFUCODE.MVCX,     HFUOP.VALUE,  HFUOP.IN,     HFUOP.IN  ),
    CHAMP.MVXC      -> List(  EXT.HFU,  HFUCODE.MVXC,     HFUOP.VALUE,  HFUOP.X,      HFUOP.X   ),
    CHAMP.MV        -> List(  EXT.HFU,  HFUCODE.MV,       HFUOP.CONF,   HFUOP.CONF,   HFUOP.X   ),
    CHAMP.LOAD      -> List(  EXT.HFU,  HFUCODE.LOAD,     HFUOP.CONF,   HFUOP.IN,     HFUOP.IN  ),
    CHAMP.STORE     -> List(  EXT.HFU,  HFUCODE.STORE,    HFUOP.CONF,   HFUOP.IN,     HFUOP.IN  ),
    CHAMP.SWITCHV   -> List(  EXT.HFU,  HFUCODE.SWITCHV,  HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.SWITCHL   -> List(  EXT.HFU,  HFUCODE.SWITCHL,  HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.SWITCHC   -> List(  EXT.HFU,  HFUCODE.SWITCHC,  HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.SWITCHJV  -> List(  EXT.HFU,  HFUCODE.SWITCHJV, HFUOP.CONF,   HFUOP.IN,     HFUOP.X   ),
    CHAMP.SWITCHJL  -> List(  EXT.HFU,  HFUCODE.SWITCHJL, HFUOP.CONF,   HFUOP.IN,     HFUOP.X   ),
    CHAMP.SWITCHJC  -> List(  EXT.HFU,  HFUCODE.SWITCHJC, HFUOP.CONF,   HFUOP.IN,     HFUOP.X   ),
    CHAMP.CHECKV    -> List(  EXT.HFU,  HFUCODE.CHECKV,   HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.CHECKU    -> List(  EXT.HFU,  HFUCODE.CHECKU,   HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.CHECKL    -> List(  EXT.HFU,  HFUCODE.CHECKL,   HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.CHECKC    -> List(  EXT.HFU,  HFUCODE.CHECKC,   HFUOP.CONF,   HFUOP.X,      HFUOP.X   ),
    CHAMP.SWITCHRL0 -> List(  EXT.HFU,  HFUCODE.RETL0,    HFUOP.CONF,   HFUOP.TL0EPC, HFUOP.X   ),
    CHAMP.SWITCHRL1 -> List(  EXT.HFU,  HFUCODE.RETL1,    HFUOP.CONF,   HFUOP.TL1EPC, HFUOP.X   ))
}
