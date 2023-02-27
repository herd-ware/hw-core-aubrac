/*
 * File: table-ext.scala                                                       *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 06:26:46 pm                                       *
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
import herd.common.isa.champ.{INSTR => CHAMP}
import herd.core.aubrac.dmu.{CODE => DMUCODE, OP => DMUOP}

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
    BASE.ADD        -> List(  EXT.NONE,       0.U,            0.U,          0.U,          0.U))
}

object TABLEEXTDMU extends TABLEEXT {
  val table : Array[(BitPat, List[UInt])] =    
              Array[(BitPat, List[UInt])](
  //                          Ext unit       Code             S1 ?          S2 ?          S3 ?
  //                             |             |               |             |             |
    CHAMP.ADD       -> List(  EXT.DMU,  DMUCODE.ADD,      DMUOP.VALUE,  DMUOP.IN,     DMUOP.IN  ),
    CHAMP.SUB       -> List(  EXT.DMU,  DMUCODE.SUB,      DMUOP.VALUE,  DMUOP.IN,     DMUOP.IN  ),
    CHAMP.SET       -> List(  EXT.DMU,  DMUCODE.SET,      DMUOP.VALUE,  DMUOP.IN,     DMUOP.IN  ),
    CHAMP.CLEAR     -> List(  EXT.DMU,  DMUCODE.CLEAR,    DMUOP.VALUE,  DMUOP.IN,     DMUOP.IN  ),
    CHAMP.MVCX      -> List(  EXT.DMU,  DMUCODE.MVCX,     DMUOP.VALUE,  DMUOP.IN,     DMUOP.IN  ),
    CHAMP.MVXC      -> List(  EXT.DMU,  DMUCODE.MVXC,     DMUOP.VALUE,  DMUOP.X,      DMUOP.X   ),
    CHAMP.MV        -> List(  EXT.DMU,  DMUCODE.MV,       DMUOP.CONF,   DMUOP.CONF,   DMUOP.X   ),
    CHAMP.LOAD      -> List(  EXT.DMU,  DMUCODE.LOAD,     DMUOP.CONF,   DMUOP.IN,     DMUOP.IN  ),
    CHAMP.STORE     -> List(  EXT.DMU,  DMUCODE.STORE,    DMUOP.CONF,   DMUOP.IN,     DMUOP.IN  ),
    CHAMP.SWITCHV   -> List(  EXT.DMU,  DMUCODE.SWITCHV,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.SWITCHL   -> List(  EXT.DMU,  DMUCODE.SWITCHL,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.SWITCHC   -> List(  EXT.DMU,  DMUCODE.SWITCHC,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.SWITCHJV  -> List(  EXT.DMU,  DMUCODE.SWITCHJV, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CHAMP.SWITCHJL  -> List(  EXT.DMU,  DMUCODE.SWITCHJL, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CHAMP.SWITCHJC  -> List(  EXT.DMU,  DMUCODE.SWITCHJC, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CHAMP.CHECKV    -> List(  EXT.DMU,  DMUCODE.CHECKV,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.CHECKU    -> List(  EXT.DMU,  DMUCODE.CHECKU,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.CHECKL    -> List(  EXT.DMU,  DMUCODE.CHECKL,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.CHECKC    -> List(  EXT.DMU,  DMUCODE.CHECKC,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CHAMP.SWITCHRL0 -> List(  EXT.DMU,  DMUCODE.RETL0,    DMUOP.CONF,   DMUOP.TL0EPC, DMUOP.X   ),
    CHAMP.SWITCHRL1 -> List(  EXT.DMU,  DMUCODE.RETL1,    DMUOP.CONF,   DMUOP.TL1EPC, DMUOP.X   ))
}
