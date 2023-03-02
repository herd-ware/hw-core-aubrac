/*
 * File: check.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:33:05 pm
 * Modified By: Mathieu Escouteloup
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.hfu

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.isa.champ._


class Check(p: HfuParams) extends Module {
  def nRange: Int = p.pFieldStruct.nRange

  val io = IO(new Bundle {
    val b_req = Flipped(new GenRVIO(p, new CheckReqCtrlBus(), new CheckReqDataBus(p)))
    val b_ack = new GenRVIO(p, Bool(), new CheckAckDataBus(p))
  }) 

  val w_base = io.b_req.data.get.base
  val w_hfs = io.b_req.data.get.hfs

  val w_id_check = Wire(Bool())
  val w_order_check = Wire(Bool())
  val w_table_check = Wire(Bool())
  val w_cap_check = Wire(Bool())

  // ******************************
  //            RESULTS
  // ******************************
  // Valid conf
  val w_valid = Wire(Vec(4, Bool()))
  w_valid(0) := w_id_check
  w_valid(1) := w_order_check
  w_valid(2) := w_table_check
  w_valid(3) := w_cap_check

  // Clear conf
  val w_clear = Wire(Vec(3, Bool()))
  w_clear(0) := w_id_check
  w_clear(1) := w_order_check
  w_clear(2) := w_table_check

  // Modify conf
  val w_mod = Wire(Vec(4, Bool()))
  w_mod(0) := (io.b_req.ctrl.get.index =/= CONF.ID.U)
  w_mod(1) := (io.b_req.ctrl.get.index =/= CONF.STATUS.U)  
  w_mod(2) := w_table_check
  w_mod(3) := w_cap_check

  // Owned by base
  val w_own = Wire(Vec(3, Bool()))
  w_own(0) := w_id_check
  w_own(1) := (w_base.cap.featl(0) & w_hfs.status.atl(0))
  w_own(2) := (w_base.cap.featl(1) & w_hfs.status.atl(1))

  // ******************************
  //          OPERATIONS
  // ******************************
  io.b_req.ready := true.B
  io.b_ack.valid := true.B
  io.b_ack.ctrl.get := true.B
  io.b_ack.data.get.hfres := w_hfs
  io.b_ack.data.get.res := 0.U

  switch(io.b_req.ctrl.get.uop) {
    is (CHECKUOP.V) {
      io.b_ack.data.get.hfres.status.valid := w_hfs.status.valid | w_hfs.status.update | w_valid.asUInt.andR
      io.b_ack.data.get.hfres.status.update := false.B
      io.b_ack.data.get.res := ~(w_hfs.status.valid | w_hfs.status.update | w_valid.asUInt.andR)
      io.b_ack.ctrl.get := (w_hfs.status.valid | w_hfs.status.update | w_valid.asUInt.andR)
    }
    is (CHECKUOP.J) {
      io.b_ack.data.get.hfres.status.valid := (w_hfs.status.valid | w_hfs.status.update | w_valid.asUInt.andR) & w_own.asUInt.orR
      io.b_ack.data.get.hfres.status.update := false.B
      io.b_ack.data.get.res := ~(w_hfs.status.valid | w_hfs.status.update | w_valid.asUInt.andR) | ~w_own.asUInt.orR
      io.b_ack.ctrl.get := (w_hfs.status.valid | w_hfs.status.update | w_valid.asUInt.andR) & w_own.asUInt.orR
    }
    is (CHECKUOP.U) {
      io.b_ack.data.get.hfres.status.valid := w_hfs.status.valid 
      io.b_ack.data.get.hfres.status.update := ~w_hfs.status.valid & (w_hfs.status.update | w_valid.asUInt.andR)
      io.b_ack.data.get.res := w_hfs.status.valid | ~(w_hfs.status.update | w_valid.asUInt.andR)
      io.b_ack.ctrl.get := ~w_hfs.status.valid & (w_hfs.status.update | w_valid.asUInt.andR)
    }
    is (CHECKUOP.L) {
      io.b_ack.data.get.hfres.status.valid := w_hfs.status.valid | w_hfs.status.update | w_valid.asUInt.andR
      io.b_ack.data.get.hfres.status.update := false.B
      io.b_ack.data.get.hfres.status.lock := w_hfs.status.valid | w_hfs.status.update | w_valid.asUInt.andR
      io.b_ack.data.get.res := ~(w_hfs.status.valid | w_hfs.status.update | w_valid.asUInt.andR)
      io.b_ack.ctrl.get := (w_hfs.status.valid | w_hfs.status.update | w_valid.asUInt.andR)
    }
    is (CHECKUOP.C) {
      io.b_ack.data.get.hfres.status.valid := (w_hfs.status.valid & w_hfs.status.lock) & ~w_clear.asUInt.andR
      io.b_ack.data.get.hfres.status.update := false.B
      io.b_ack.data.get.hfres.status.lock := (w_hfs.status.valid & w_hfs.status.lock) & ~w_clear.asUInt.andR
      io.b_ack.data.get.res := w_hfs.status.valid & w_hfs.status.lock & ~w_clear.asUInt.andR
      io.b_ack.ctrl.get := w_hfs.status.valid & w_hfs.status.lock & w_clear.asUInt.andR
    }
    is (CHECKUOP.M) {
      io.b_ack.data.get.hfres.status.update := w_hfs.status.update & w_mod.asUInt.andR
      io.b_ack.data.get.res := ~w_mod.asUInt.andR
    }
    is (CHECKUOP.R) {
      io.b_ack.data.get.hfres.status.valid := w_hfs.status.valid
      io.b_ack.data.get.hfres.status.update := false.B
      io.b_ack.data.get.res := ~w_hfs.status.valid
      io.b_ack.ctrl.get := w_hfs.status.valid
    }
  }  

  // ******************************
  //            COMPARE
  // ******************************
  // ------------------------------
  //              ID
  // ------------------------------
  val w_cmp_id_part = Wire(Vec(nRange, Bool()))
  val w_cmp_id_order = Wire(Vec(nRange + 1, Bool()))

  w_cmp_id_order(nRange) := true.B
  if (p.useChampExtR) {
    for (o <- 0 until nRange) {
      w_cmp_id_part(o) := (w_hfs.id.part(o) === w_base.id.part(o))
      w_cmp_id_order(o) := w_cmp_id_order(o + 1) & w_cmp_id_part(o)
    }
  } else {
    w_cmp_id_part(0) := (w_hfs.id.part(0) === w_base.id.part(0))
    w_cmp_id_order(0) := w_cmp_id_part(0)
    for (o <- 1 until nRange) {
      w_cmp_id_part(o) := true.B
      w_cmp_id_order(o) := true.B
    }
  }  

  // ------------------------------
  //             TABLE
  // ------------------------------  
  val w_hfs_table_full = w_hfs.table.andR
  val w_hfs_table_empty = ~w_hfs.table.orR

  val w_base_table_full = w_base.table.andR
  val w_base_table_empty = ~w_base.table.orR

  val w_hfs_table_cur_in = Wire(Vec(p.nDataBit, Bool()))
  val w_hfs_table_only_sub = Wire(Bool())
  
  //  Verify if base range is alone in confs range (here 0 is true)
  for (b <- 0 until p.nDataBit) {
    w_hfs_table_cur_in(b) := (b.U === w_base.id.toPart(w_hfs.status.order)) ^ w_hfs.table(b)
  }
  w_hfs_table_only_sub := ~w_hfs_table_cur_in.asUInt.orR

  // ******************************
  //             CHECK
  // ******************************
  // ------------------------------
  //              ID
  // ------------------------------
  //  Check if confs configuration's ID is managed by base conf
  if (p.useChampExtR) {
    w_id_check := w_cmp_id_order(w_base.status.order + 1.U) & w_base.table(w_hfs.id.toPart(w_base.status.order))
  } else {
    w_id_check := w_base.table(w_hfs.id.part(0))
  }

  // ------------------------------
  //             ORDER
  // ------------------------------
  if (p.useChampExtR) {
    when (w_base.status.order >= w_hfs.status.order)  {
      w_order_check := w_id_check
    }.elsewhen (w_hfs.status.order === (w_base.status.order + 1.U)) {
      w_order_check := w_cmp_id_order(w_hfs.status.order + 1.U) & w_hfs_table_only_sub & w_base_table_full
    }.otherwise {
      w_order_check := false.B
    }
  } else {
    w_order_check := true.B
  }

  // ------------------------------
  //             TABLE
  // ------------------------------
  if (p.useChampExtR) {
    when (w_base.status.order > w_hfs.status.order)  {
      w_table_check := w_id_check
    }.elsewhen (w_hfs.status.order === w_base.status.order) {
      w_table_check := ~(w_hfs.table & ~w_base.table).orR
    }.elsewhen (w_hfs.status.order === (w_base.status.order + 1.U)) {
      w_table_check := w_hfs_table_only_sub
    }.otherwise {
      w_table_check := w_hfs_table_empty
    }
  } else {
    w_table_check := ~(w_hfs.table & ~w_base.table).orR
  }

  // ------------------------------
  //          CAPABILITIES
  // ------------------------------
  w_cap_check := ~(w_hfs.cap.toUInt & ~w_base.cap.toUInt).orR
}

object Check extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Check(HfuConfigBase), args)
}
