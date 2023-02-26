/*
 * File: table-lsu.scala                                                       *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:59:23 pm                                       *
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
import herd.common.mem.mb4s.{OP => LSUUOP, AMO => LSUAMO}
import herd.core.aubrac.back.csr.{UOP => CSRUOP}


// ************************************************************
//
//        DECODE TABLES TO EXTRACT MEMORY INFORMATIONS
//
// ************************************************************
trait TABLELSU {
  //                                  Uop                        use sign ?      Amo op
  //                                   |            Size            |               |
  //                      use LSU ?    |              |             |               |
  //                         |         |              |             |               |
  val default: List[UInt] =
               List[UInt](  0.B,    LSUUOP.X,     LSUSIZE.X,    LSUSIGN.X,    LSUAMO.X    )
  val table: Array[(BitPat, List[UInt])]
}

object TABLELSU32I extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                                  Uop                        use sign ?      Amo op
  //                                   |            Size            |               |
  //                      use LSU ?    |              |             |               |
  //                         |         |              |             |               |
  BASE.LB     -> List(      1.B,    LSUUOP.R,     LSUSIZE.B,    LSUSIGN.S,    LSUAMO.X    ),
  BASE.LH     -> List(      1.B,    LSUUOP.R,     LSUSIZE.H,    LSUSIGN.S,    LSUAMO.X    ),
  BASE.LW     -> List(      1.B,    LSUUOP.R,     LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X    ),
  BASE.LBU    -> List(      1.B,    LSUUOP.R,     LSUSIZE.B,    LSUSIGN.U,    LSUAMO.X    ),
  BASE.LHU    -> List(      1.B,    LSUUOP.R,     LSUSIZE.H,    LSUSIGN.U,    LSUAMO.X    ),
  BASE.SB     -> List(      1.B,    LSUUOP.W,     LSUSIZE.B,    LSUSIGN.S,    LSUAMO.X    ),
  BASE.SH     -> List(      1.B,    LSUUOP.W,     LSUSIZE.H,    LSUSIGN.S,    LSUAMO.X    ),
  BASE.SW     -> List(      1.B,    LSUUOP.W,     LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X    ))
}

object TABLELSU64I extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                                  Uop                        use sign ?      Amo op
  //                                   |            Size            |               |
  //                      use LSU ?    |              |             |               |
  //                         |         |              |             |               |
  BASE.LWU    -> List(      1.B,    LSUUOP.R,     LSUSIZE.W,    LSUSIGN.U,    LSUAMO.X    ),
  BASE.LD     -> List(      1.B,    LSUUOP.R,     LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X    ),
  BASE.SD     -> List(      1.B,    LSUUOP.W,     LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X    ))
}

object TABLELSU32A extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                                  Uop                        use sign ?      Amo op
  //                                   |            Size            |               |
  //                      use LSU ?    |              |             |               |
  //                         |         |              |             |               |
  BASE.LRW      -> List(    1.B,    LSUUOP.LR,    LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X    ), 
  BASE.SCW      -> List(    1.B,    LSUUOP.SC,    LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X    ), 
  BASE.AMOSWAPW -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.SWAP ), 
  BASE.AMOADDW  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.ADD  ), 
  BASE.AMOXORW  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.XOR  ), 
  BASE.AMOANDW  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.AND  ), 
  BASE.AMOORW   -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.OR   ), 
  BASE.AMOMINW  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MIN  ), 
  BASE.AMOMAXW  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MAX  ), 
  BASE.AMOMINUW -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MINU ), 
  BASE.AMOMAXUW -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MAXU )) 
}

object TABLELSU64A extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                                  Uop                        use sign ?      Amo op
  //                                   |            Size            |               |
  //                      use LSU ?    |              |             |               |
  //                         |         |              |             |               |
  BASE.LRD      -> List(    1.B,    LSUUOP.LR,    LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X    ), 
  BASE.SCD      -> List(    1.B,    LSUUOP.SC,    LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X    ), 
  BASE.AMOSWAPD -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.SWAP ), 
  BASE.AMOADDD  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.ADD  ), 
  BASE.AMOXORD  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.XOR  ), 
  BASE.AMOANDD  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.AND  ), 
  BASE.AMOORD   -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.OR   ), 
  BASE.AMOMIND  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MIN  ), 
  BASE.AMOMAXD  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MAX  ), 
  BASE.AMOMINUD -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MINU ), 
  BASE.AMOMAXUD -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MAXU )) 
}
