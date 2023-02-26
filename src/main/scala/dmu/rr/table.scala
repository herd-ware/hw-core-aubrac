/*
 * File: table.scala                                                           *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:03:18 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.dmu

import chisel3._
import chisel3.util._


object TABLEOP
{
    //                                   V    Check ?,  Check Uop,     Alu ?,      Alu Uop    
    //                                   |       |           |           |            |
  val default: List[UInt] = List[UInt]( 0.B,    0.B,    CHECKUOP.X,     0.B,    ALUUOP.X)
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                                   V    Check ?,  Check Uop,     Alu ?,      Alu Uop    
    //                                   |       |           |           |            |
    BitPat(CODE.ADD)      ->  List(     1.B,    0.B,    CHECKUOP.M,     1.B,    ALUUOP.ADD), 
    BitPat(CODE.SUB)      ->  List(     1.B,    0.B,    CHECKUOP.M,     1.B,    ALUUOP.SUB), 
    BitPat(CODE.SET)      ->  List(     1.B,    0.B,    CHECKUOP.M,     1.B,    ALUUOP.SET), 
    BitPat(CODE.CLEAR)    ->  List(     1.B,    0.B,    CHECKUOP.M,     1.B,    ALUUOP.CLEAR),  
    BitPat(CODE.MVCX)     ->  List(     1.B,    0.B,    CHECKUOP.M,     1.B,    ALUUOP.IN2), 
    BitPat(CODE.MVXC)     ->  List(     1.B,    0.B,    CHECKUOP.X,     1.B,    ALUUOP.IN1), 
    BitPat(CODE.MV)       ->  List(     1.B,    0.B,    CHECKUOP.X,     0.B,    ALUUOP.X),    
    BitPat(CODE.LOAD)     ->  List(     1.B,    0.B,    CHECKUOP.X,     1.B,    ALUUOP.ADDR),   
    BitPat(CODE.STORE)    ->  List(     1.B,    0.B,    CHECKUOP.X,     1.B,    ALUUOP.ADDR),   
    BitPat(CODE.SWITCHV)  ->  List(     1.B,    1.B,    CHECKUOP.V,     0.B,    ALUUOP.X),
    BitPat(CODE.SWITCHL)  ->  List(     1.B,    1.B,    CHECKUOP.V,     0.B,    ALUUOP.X),
    BitPat(CODE.SWITCHC)  ->  List(     1.B,    1.B,    CHECKUOP.V,     0.B,    ALUUOP.X),
    BitPat(CODE.SWITCHJV) ->  List(     1.B,    1.B,    CHECKUOP.J,     0.B,    ALUUOP.X),
    BitPat(CODE.SWITCHJL) ->  List(     1.B,    1.B,    CHECKUOP.J,     0.B,    ALUUOP.X),
    BitPat(CODE.SWITCHJC) ->  List(     1.B,    1.B,    CHECKUOP.J,     0.B,    ALUUOP.X),
    BitPat(CODE.CHECKV)   ->  List(     1.B,    1.B,    CHECKUOP.V,     0.B,    ALUUOP.X), 
    BitPat(CODE.CHECKU)   ->  List(     1.B,    1.B,    CHECKUOP.U,     0.B,    ALUUOP.X), 
    BitPat(CODE.CHECKL)   ->  List(     1.B,    1.B,    CHECKUOP.L,     0.B,    ALUUOP.X), 
    BitPat(CODE.CHECKC)   ->  List(     1.B,    1.B,    CHECKUOP.C,     0.B,    ALUUOP.X),
    BitPat(CODE.TRAP)     ->  List(     1.B,    1.B,    CHECKUOP.V,     0.B,    ALUUOP.X),
    BitPat(CODE.RETL0)    ->  List(     1.B,    1.B,    CHECKUOP.R,     0.B,    ALUUOP.X),
    BitPat(CODE.RETL1)    ->  List(     1.B,    1.B,    CHECKUOP.R,     0.B,    ALUUOP.X))
}

object TABLECTRL
{
    //                                 RF ?,       Load,   Store,   Switch     Jump
    //                                   |           |       |         |         |
  val default: List[UInt] = List[UInt]( 0.B,        0.B,    0.B,    SWUOP.X,    0.B)
  val table : Array[(BitPat, List[UInt])] =
              Array[(BitPat, List[UInt])](

    //                                 RF ?,       Load,   Store,   Switch     Jump
    //                                   |           |       |         |         |
    BitPat(CODE.ADD)      ->  List(     1.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.SUB)      ->  List(     1.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.SET)      ->  List(     1.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.CLEAR)    ->  List(     1.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.MVCX)     ->  List(     1.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.MVXC)     ->  List(     0.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.MV)       ->  List(     1.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.LOAD)     ->  List(     1.B,        1.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.STORE)    ->  List(     0.B,        0.B,    1.B,    SWUOP.X,    0.B),     
    BitPat(CODE.SWITCHV)  ->  List(     1.B,        0.B,    0.B,    SWUOP.V,    0.B),
    BitPat(CODE.SWITCHL)  ->  List(     1.B,        0.B,    0.B,    SWUOP.L,    0.B),
    BitPat(CODE.SWITCHC)  ->  List(     1.B,        0.B,    0.B,    SWUOP.C,    0.B),   
    BitPat(CODE.SWITCHJV) ->  List(     1.B,        0.B,    0.B,    SWUOP.V,    1.B),
    BitPat(CODE.SWITCHJL) ->  List(     1.B,        0.B,    0.B,    SWUOP.L,    1.B),
    BitPat(CODE.SWITCHJC) ->  List(     1.B,        0.B,    0.B,    SWUOP.C,    1.B),
    BitPat(CODE.CHECKV)   ->  List(     1.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.CHECKU)   ->  List(     1.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.CHECKL)   ->  List(     1.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.CHECKC)   ->  List(     1.B,        0.B,    0.B,    SWUOP.X,    0.B),
    BitPat(CODE.TRAP)     ->  List(     1.B,        0.B,    0.B,    SWUOP.L,    1.B),
    BitPat(CODE.RETL0)    ->  List(     1.B,        0.B,    0.B,    SWUOP.L,    1.B),
    BitPat(CODE.RETL1)    ->  List(     1.B,        0.B,    0.B,    SWUOP.L,    1.B))
}