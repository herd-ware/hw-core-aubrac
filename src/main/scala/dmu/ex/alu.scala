/*
 * File: alu.scala                                                             *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:00:27 pm                                       *
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


class Alu(p: DmuParams) extends Module {
  val io = IO(new Bundle {
    val b_req = Flipped(new GenRVIO(p, new AluReqCtrlBus(), new AluReqDataBus(p)))
    val b_ack = new GenRVIO(p, UInt(0.W), new AluAckDataBus(p))
  })

  val w_field =  Wire(UInt(p.nDataBit.W))
  val w_res = Wire(UInt(p.nDataBit.W))
  val w_change = Wire(Bool())

  // ******************************
  //             I/Os
  // ******************************
  io.b_req.ready := true.B
  io.b_ack.valid := io.b_req.valid
  io.b_ack.data.get.dcres := io.b_req.data.get.s1
  io.b_ack.data.get.dcres.status.valid := io.b_req.data.get.s1.status.valid & ~w_change
  io.b_ack.data.get.res := w_res  

  // ******************************
  //          SELECT FIELD
  // ******************************
  w_field := DontCare
  when (io.b_req.valid | (io.b_req.ctrl.get.uop =/= ALUUOP.ADDR)) {
    switch (io.b_req.data.get.s3(6, 0)) {
      is (CONF.STATUS.U)   {
        w_field := io.b_req.data.get.s1.status.toUInt
        io.b_ack.data.get.dcres.status.fromUIntData(w_res)
      }
      is (CONF.ID.U)     {
        w_field := io.b_req.data.get.s1.id.toUInt
        io.b_ack.data.get.dcres.id.fromUInt(w_res)
      }
      is (CONF.ENTRY.U)  {
        w_field := io.b_req.data.get.s1.entry
        io.b_ack.data.get.dcres.entry := w_res
      }
      is (CONF.TABLE.U)  {
        w_field := io.b_req.data.get.s1.table
        io.b_ack.data.get.dcres.table := w_res
      }
      is (CONF.CAP.U)    {
        w_field := io.b_req.data.get.s1.cap.toUInt
        io.b_ack.data.get.dcres.cap.fromUInt(w_res)
      }
    }
  }

  // ******************************
  //            PROCESS
  // ******************************
  w_res := w_field
  w_change := false.B

  when (io.b_req.valid) {    
    switch(io.b_req.ctrl.get.uop) {
      is (ALUUOP.ADD)    {
        w_res := (w_field + io.b_req.data.get.s2)
        w_change := true.B
      }
      is (ALUUOP.SUB)    {
        w_res := (w_field - io.b_req.data.get.s2)
        w_change := true.B
      }
      is (ALUUOP.SET)  {
        w_res := (w_field | io.b_req.data.get.s2)
        w_change := true.B
      }
      is (ALUUOP.CLEAR)  {
        w_res := (w_field & ~io.b_req.data.get.s2)
        w_change := true.B
      }
      is (ALUUOP.IN1)    {
        w_res := w_field
        w_change := false.B
      }
      is (ALUUOP.IN2)    {
        w_res := io.b_req.data.get.s2
        w_change := true.B
      }
      is (ALUUOP.ADDR)    {
        w_res := (io.b_req.data.get.s2 + io.b_req.data.get.s3)
        w_change := false.B
      }
    }
  } 
}

object Alu extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new Alu(DmuConfigBase), args)
}
