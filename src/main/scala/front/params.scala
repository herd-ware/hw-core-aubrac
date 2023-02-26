/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:04:04 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.front

import chisel3._
import chisel3.util._

import herd.common.gen._
import herd.common.mem.mb4s._


trait FrontParams extends GenParams {
  def debug: Boolean
  def pcBoot: String
  def nHart: Int
  
  def useDome: Boolean
  def nDome: Int
  def multiDome: Boolean = false
  def nPart: Int

  def nAddrBit: Int
  def nInstrByte: Int
  def nInstrBit: Int = nInstrByte * 8
  def nFetchInstr: Int
  def nFetchByte: Int = nFetchInstr * nInstrByte

  def pL0IBus: Mb4sParams = new Mb4sConfig (
    debug = debug,
    readOnly = true,
    nHart = nHart,
    nAddrBit = nAddrBit,
    useAmo = false,
    nDataByte = 4 * nFetchInstr,
    
    useDome = useDome,
    nDome = nDome,
    multiDome = false
  )

  def useIMemSeq: Boolean
  def useIf1Stage: Boolean
  def useIf2Stage: Boolean
  def nFetchBufferDepth: Int
  def useNlp: Boolean
  def useFastJal: Boolean

  def nBackPort: Int
}

case class FrontConfig (
  debug: Boolean,
  pcBoot: String,
  nHart: Int,
  useDome: Boolean,
  nDome: Int,
  nPart: Int,

  nAddrBit: Int,
  nInstrByte: Int,
  nFetchInstr: Int,

  useIMemSeq: Boolean,
  useIf1Stage: Boolean,
  useIf2Stage: Boolean,
  nFetchBufferDepth: Int,
  useNlp: Boolean,
  useFastJal: Boolean,

  nBackPort: Int
) extends FrontParams
