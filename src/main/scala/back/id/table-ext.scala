/*
 * File: table-ext.scala                                                       *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:59:16 pm                                       *
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
import herd.common.isa.ceps.{INSTR => CEPS}
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
    CEPS.ADD        -> List(  EXT.DMU,  DMUCODE.ADD,      DMUOP.FIELD,  DMUOP.IN,     DMUOP.IN  ),
    CEPS.SUB        -> List(  EXT.DMU,  DMUCODE.SUB,      DMUOP.FIELD,  DMUOP.IN,     DMUOP.IN  ),
    CEPS.SET        -> List(  EXT.DMU,  DMUCODE.SET,      DMUOP.FIELD,  DMUOP.IN,     DMUOP.IN  ),
    CEPS.CLEAR      -> List(  EXT.DMU,  DMUCODE.CLEAR,    DMUOP.FIELD,  DMUOP.IN,     DMUOP.IN  ),
    CEPS.MVCX       -> List(  EXT.DMU,  DMUCODE.MVCX,     DMUOP.FIELD,  DMUOP.IN,     DMUOP.IN  ),
    CEPS.MVXC       -> List(  EXT.DMU,  DMUCODE.MVXC,     DMUOP.FIELD,  DMUOP.X,      DMUOP.X   ),
    CEPS.MV         -> List(  EXT.DMU,  DMUCODE.MV,       DMUOP.CONF,   DMUOP.CONF,   DMUOP.X   ),
    CEPS.LOAD       -> List(  EXT.DMU,  DMUCODE.LOAD,     DMUOP.CONF,   DMUOP.IN,     DMUOP.IN  ),
    CEPS.STORE      -> List(  EXT.DMU,  DMUCODE.STORE,    DMUOP.CONF,   DMUOP.IN,     DMUOP.IN  ),
    CEPS.SWITCHV    -> List(  EXT.DMU,  DMUCODE.SWITCHV,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.SWITCHL    -> List(  EXT.DMU,  DMUCODE.SWITCHL,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.SWITCHC    -> List(  EXT.DMU,  DMUCODE.SWITCHC,  DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.SWITCHJV   -> List(  EXT.DMU,  DMUCODE.SWITCHJV, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CEPS.SWITCHJL   -> List(  EXT.DMU,  DMUCODE.SWITCHJL, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CEPS.SWITCHJC   -> List(  EXT.DMU,  DMUCODE.SWITCHJC, DMUOP.CONF,   DMUOP.IN,     DMUOP.X   ),
    CEPS.CHECKV     -> List(  EXT.DMU,  DMUCODE.CHECKV,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.CHECKU     -> List(  EXT.DMU,  DMUCODE.CHECKU,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.CHECKL     -> List(  EXT.DMU,  DMUCODE.CHECKL,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.CHECKC     -> List(  EXT.DMU,  DMUCODE.CHECKC,   DMUOP.CONF,   DMUOP.X,      DMUOP.X   ),
    CEPS.SWITCHRL0  -> List(  EXT.DMU,  DMUCODE.RETL0,    DMUOP.CONF,   DMUOP.TL0EPC, DMUOP.X   ),
    CEPS.SWITCHRL1  -> List(  EXT.DMU,  DMUCODE.RETL1,    DMUOP.CONF,   DMUOP.TL1EPC, DMUOP.X   ))
}
