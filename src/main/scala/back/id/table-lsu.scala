/*
 * File: table-lsu.scala                                                       *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-08 09:37:00 am                                       *
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
import herd.common.mem.mb4s.{OP => LSUUOP, AMO => LSUAMO}
import herd.core.aubrac.back.csr.{UOP => CSRUOP}


// ************************************************************
//
//        DECODE TABLES TO EXTRACT MEMORY INFORMATION
//
// ************************************************************
trait TABLELSU {
  //                                    Uop                        use sign ?      Amo op
  //                                     |            Size            |               |
  //                        use LSU ?    |              |             |               |
  //                           |         |              |             |               |
  val default: List[UInt] =
               List[UInt](    0.B,    LSUUOP.X,     LSUSIZE.X,    LSUSIGN.X,    LSUAMO.X    )
  val table: Array[(BitPat, List[UInt])]
}

object TABLELSU32I extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                                    Uop                        use sign ?      Amo op
  //                                     |            Size            |               |
  //                        use LSU ?    |              |             |               |
  //                           |         |              |             |               |
  RISCV.LB        -> List(    1.B,    LSUUOP.R,     LSUSIZE.B,    LSUSIGN.S,    LSUAMO.X    ),
  RISCV.LH        -> List(    1.B,    LSUUOP.R,     LSUSIZE.H,    LSUSIGN.S,    LSUAMO.X    ),
  RISCV.LW        -> List(    1.B,    LSUUOP.R,     LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X    ),
  RISCV.LBU       -> List(    1.B,    LSUUOP.R,     LSUSIZE.B,    LSUSIGN.U,    LSUAMO.X    ),
  RISCV.LHU       -> List(    1.B,    LSUUOP.R,     LSUSIZE.H,    LSUSIGN.U,    LSUAMO.X    ),
  RISCV.SB        -> List(    1.B,    LSUUOP.W,     LSUSIZE.B,    LSUSIGN.S,    LSUAMO.X    ),
  RISCV.SH        -> List(    1.B,    LSUUOP.W,     LSUSIZE.H,    LSUSIGN.S,    LSUAMO.X    ),
  RISCV.SW        -> List(    1.B,    LSUUOP.W,     LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X    ))
}

object TABLELSU64I extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                                    Uop                        use sign ?      Amo op
  //                                     |            Size            |               |
  //                        use LSU ?    |              |             |               |
  //                           |         |              |             |               |
  RISCV.LWU       -> List(    1.B,    LSUUOP.R,     LSUSIZE.W,    LSUSIGN.U,    LSUAMO.X    ),
  RISCV.LD        -> List(    1.B,    LSUUOP.R,     LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X    ),
  RISCV.SD        -> List(    1.B,    LSUUOP.W,     LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X    ))
}

object TABLELSU32A extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                                    Uop                        use sign ?      Amo op
  //                                     |            Size            |               |
  //                        use LSU ?    |              |             |               |
  //                           |         |              |             |               |
  RISCV.LRW       -> List(    1.B,    LSUUOP.LR,    LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X    ), 
  RISCV.SCW       -> List(    1.B,    LSUUOP.SC,    LSUSIZE.W,    LSUSIGN.S,    LSUAMO.X    ), 
  RISCV.AMOSWAPW  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.SWAP ), 
  RISCV.AMOADDW   -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.ADD  ), 
  RISCV.AMOXORW   -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.XOR  ), 
  RISCV.AMOANDW   -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.AND  ), 
  RISCV.AMOORW    -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.OR   ), 
  RISCV.AMOMINW   -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MIN  ), 
  RISCV.AMOMAXW   -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MAX  ), 
  RISCV.AMOMINUW  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MINU ), 
  RISCV.AMOMAXUW  -> List(    1.B,    LSUUOP.AMO,   LSUSIZE.W,    LSUSIGN.S,    LSUAMO.MAXU )) 
}

object TABLELSU64A extends TABLELSU {
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

  //                                    Uop                        use sign ?      Amo op
  //                                     |            Size            |               |
  //                        use LSU ?    |              |             |               |
  //                           |         |              |             |               |
  RISCV.LRD      -> List(     1.B,    LSUUOP.LR,    LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X    ), 
  RISCV.SCD      -> List(     1.B,    LSUUOP.SC,    LSUSIZE.D,    LSUSIGN.S,    LSUAMO.X    ), 
  RISCV.AMOSWAPD -> List(     1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.SWAP ), 
  RISCV.AMOADDD  -> List(     1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.ADD  ), 
  RISCV.AMOXORD  -> List(     1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.XOR  ), 
  RISCV.AMOANDD  -> List(     1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.AND  ), 
  RISCV.AMOORD   -> List(     1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.OR   ), 
  RISCV.AMOMIND  -> List(     1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MIN  ), 
  RISCV.AMOMAXD  -> List(     1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MAX  ), 
  RISCV.AMOMINUD -> List(     1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MINU ), 
  RISCV.AMOMAXUD -> List(     1.B,    LSUUOP.AMO,   LSUSIZE.D,    LSUSIGN.S,    LSUAMO.MAXU )) 
}
