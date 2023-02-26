/*
 * File: rr.scala                                                              *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:03:13 pm                                       *
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

import herd.common.gen._
import herd.common.isa.ceps._
import herd.core.aubrac.common._


class SlctSource(nDataBit: Int) extends Module {
  val io = IO(new Bundle {
    val i_src_type = Input(UInt(OP.NBIT.W))

    val i_in = Input(UInt(nDataBit.W))
    val i_tl0epc = Input(UInt(nDataBit.W))
    val i_tl1epc = Input(UInt(nDataBit.W))
//    val i_dc = Input(new DomeCfgBus(pDomeCfg))

    val o_val = Output(UInt(nDataBit.W))
  })

  io.o_val := 0.U
  switch (io.i_src_type) {
    is (OP.ZERO)    {io.o_val := 0.U}
    is (OP.IN)      {io.o_val := io.i_in}
    is (OP.TL0EPC)  {io.o_val := io.i_tl0epc}
    is (OP.TL1EPC)  {io.o_val := io.i_tl1epc}
//    is (OP.CONF)  {io.o_val := io.i_dc}
//    is (OP.FIELD) {io.o_val := io.i_dc}
  }
}

class RrStage(p: DmuParams) extends Module {
  val io = IO(new Bundle {
    val i_flush = Input(Bool())

    val b_req = Flipped(new DmuReqIO(p, p.nAddrBit, p.nDataBit))

    val i_state = Input(new RegFileStateBus(p.nDomeCfg, p.pDomeCfg))
    val i_etl = Input(Vec(p.nCepsTrapLvl, new DmuTrapBus(p.nAddrBit)))
    val b_dcs = Vec(2, Flipped(new RegFileReadIO(p)))

    val b_out = new GenRVIO(p, new ExStageBus(p), new DataBus(p))
  })

  val w_lock = Wire(Bool())

  val w_dcs1 = Wire(UInt(5.W))
  val w_dcs2 = Wire(UInt(5.W))
  val w_check_uop = Wire(UInt(CHECKUOP.NBIT.W))

  w_dcs1 := io.b_req.ctrl.get.dcs1
  w_dcs2 := io.b_req.ctrl.get.dcs2

  // ******************************
  //            DECODER
  // ******************************
  val w_is_full = (io.b_req.ctrl.get.op1 === OP.CONF)
  val w_is_mv = (io.b_req.ctrl.get.code === CODE.MV)
  val w_is_trap = (io.b_req.ctrl.get.code === CODE.TRAP)
  val w_is_ret = Wire(Bool())
  val w_decoder_op  = ListLookup(io.b_req.ctrl.get.code, TABLEOP.default, TABLEOP.table)
  val w_decoder_ctrl = ListLookup(io.b_req.ctrl.get.code, TABLECTRL.default, TABLECTRL.table)

  w_check_uop := w_decoder_op(2) 

  // ******************************
  //            RETURN
  // ******************************
  w_is_ret := false.B
  switch (io.b_req.ctrl.get.code) {
    is (CODE.RETL0) {
      w_is_ret := true.B
      if (p.nCepsTrapLvl > 0) {
        w_dcs1 := io.i_etl(0).dc
        when (io.i_etl(0).sw) {
          w_check_uop := CHECKUOP.J
        }.otherwise {
          w_check_uop := CHECKUOP.R
        }
      }      
    }
    
    is (CODE.RETL1) {
      w_is_ret := true.B
      if (p.nCepsTrapLvl > 1) {
        w_dcs1 := io.i_etl(1).dc
        when (io.i_etl(1).sw) {
          w_check_uop := CHECKUOP.J
        }.otherwise {
          w_check_uop := CHECKUOP.R
        }
      }      
    }
  }

  // ******************************
  //         CONFR & BYPASS
  // ******************************
  val w_wait_dcs = Wire(Bool())

  io.b_dcs(0).addr := w_dcs1
  io.b_dcs(0).full := (io.b_req.ctrl.get.op1 === OP.CONF)
  io.b_dcs(0).field := io.b_req.data.get.s3(6,0)

  io.b_dcs(1).addr := w_dcs2
  io.b_dcs(1).full := (io.b_req.ctrl.get.op2 === OP.CONF)
  io.b_dcs(1).field := io.b_req.data.get.s3(6,0)

  w_wait_dcs := ~io.b_dcs(0).ready | (w_is_mv & ~io.b_dcs(1).ready)

  // ******************************
  //             SOURCE
  // ******************************
  // ------------------------------
  //               S2
  // ------------------------------
  val m_slct_s2 = Module(new SlctSource(p.nDataBit))

  m_slct_s2.io.i_src_type := io.b_req.ctrl.get.op2
  m_slct_s2.io.i_in := io.b_req.data.get.s2
  if (p.nCepsTrapLvl > 0) m_slct_s2.io.i_tl0epc := io.i_etl(0).pc else m_slct_s2.io.i_tl0epc := DontCare
  if (p.nCepsTrapLvl > 0) m_slct_s2.io.i_tl1epc := io.i_etl(1).pc else m_slct_s2.io.i_tl1epc := DontCare
//  m_slct_s2.io.i_dc := io.b_dcs(1).data

  // ------------------------------
  //               S3
  // ------------------------------
  val m_slct_s3 = Module(new SlctSource(p.nDataBit))

  m_slct_s3.io.i_src_type := io.b_req.ctrl.get.op3
  m_slct_s3.io.i_in := io.b_req.data.get.s3
  if (p.nCepsTrapLvl > 0) m_slct_s3.io.i_tl0epc := io.i_etl(0).pc else m_slct_s3.io.i_tl0epc := DontCare
  if (p.nCepsTrapLvl > 0) m_slct_s3.io.i_tl1epc := io.i_etl(1).pc else m_slct_s3.io.i_tl1epc := DontCare
//  m_slct_s3.io.i_dc := DontCare

  // ------------------------------
  //             CONFD
  // ------------------------------
  val w_dcres_lock = Wire(Bool())
  val w_dcres_up = Wire(Bool())

  w_dcres_lock := io.b_dcs(0).data.status.valid & io.b_dcs(0).data.status.lock
  w_dcres_up := io.b_dcs(0).data.status.update

  // ******************************
  //            OUTPUTS
  // ******************************
  // ------------------------------
  //           REGISTERS
  // ------------------------------
  val m_out = Module( new GenReg(p, new ExStageBus(p), new DataBus(p), false, false, true))

  m_out.io.i_flush := io.i_flush & ~w_is_trap

  w_lock := ~m_out.io.b_in.ready

  m_out.io.b_in.valid := io.b_req.valid & w_decoder_op(0) & ~w_wait_dcs

  m_out.io.b_in.ctrl.get.info.cur := (w_dcs1(log2Ceil(p.nDomeCfg) - 1, 0) === io.i_state.cur.addr)
  m_out.io.b_in.ctrl.get.info.trap := w_is_trap 
  m_out.io.b_in.ctrl.get.info.ret := w_is_ret 
  m_out.io.b_in.ctrl.get.info.jump := w_decoder_ctrl(4) 
  m_out.io.b_in.ctrl.get.info.full := w_is_full | io.b_dcs(0).data.status.valid | io.b_dcs(0).data.status.update
  m_out.io.b_in.ctrl.get.info.wb := io.b_req.ctrl.get.wb
  m_out.io.b_in.ctrl.get.info.check := true.B
  m_out.io.b_in.ctrl.get.info.lock := w_dcres_lock

  m_out.io.b_in.ctrl.get.trap := DontCare
  m_out.io.b_in.ctrl.get.trap.gen := false.B
  m_out.io.b_in.ctrl.get.trap.valid := false.B

  m_out.io.b_in.ctrl.get.op.mv := w_is_mv
  m_out.io.b_in.ctrl.get.op.check_use := w_decoder_op(1) | (w_dcres_up & (w_check_uop === CHECKUOP.M))
  m_out.io.b_in.ctrl.get.op.check_uop := w_check_uop  
  m_out.io.b_in.ctrl.get.op.alu_use := w_decoder_op(3)
  m_out.io.b_in.ctrl.get.op.alu_uop := w_decoder_op(4)

  m_out.io.b_in.ctrl.get.lsu.ld := w_decoder_ctrl(1)
  m_out.io.b_in.ctrl.get.lsu.st := w_decoder_ctrl(2)

  m_out.io.b_in.ctrl.get.rf.en := w_decoder_ctrl(0)
  m_out.io.b_in.ctrl.get.rf.addr := w_dcs1
  m_out.io.b_in.ctrl.get.rf.sw := w_decoder_ctrl(3)

  m_out.io.b_in.data.get.s1 := Mux(w_is_mv & ~w_dcres_lock, io.b_dcs(1).data, io.b_dcs(0).data)
  m_out.io.b_in.data.get.s2 := m_slct_s2.io.o_val
  m_out.io.b_in.data.get.s3 := m_slct_s3.io.o_val

  // ------------------------------
  //            CONNECT
  // ------------------------------
  io.b_out <> m_out.io.b_out
  m_out.io.b_out.ready := io.b_out.ready | w_is_trap
  
  io.b_req.ready := ~w_lock & ~w_wait_dcs

  // ******************************
  //            DEBUG
  // ******************************
  if (p.debug) {
    // ------------------------------
    //            SIGNALS
    // ------------------------------
    dontTouch(io.b_req)

    // ------------------------------
    //         DATA FOOTPRINT
    // ------------------------------

    // ------------------------------
    //       EXECUTION TRACKER
    // ------------------------------
    m_out.io.b_in.ctrl.get.etd.get := io.b_req.ctrl.get.etd.get
  }
}

object RrStage extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new RrStage(DmuConfigBase), args)
}
