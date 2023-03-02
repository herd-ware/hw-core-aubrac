/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-02 05:58:36 pm                                       *
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
import scala.math._

import herd.common.mem.mb4s._
import herd.core.aubrac.back.csr.{CsrParams}


trait DecoderParams{
  def nHart: Int
  def nBackPort: Int
  def nAddrBit: Int
  def nDataBit: Int
  def nInstrBit: Int = 32

  def useChamp: Boolean
  def useExtM: Boolean
  def useExtA: Boolean
  def useExtB: Boolean
  def useExtZifencei: Boolean
  def useExtZicbo: Boolean
}

case class DecoderConfig (
  nHart: Int,
  nBackPort: Int,
  nAddrBit: Int,
  nDataBit: Int,

  useChamp: Boolean,
  useExtM: Boolean,
  useExtA: Boolean,
  useExtB: Boolean,
  useExtZifencei: Boolean,
  useExtZicbo: Boolean
) extends DecoderParams

trait GprParams{
  def debug: Boolean
  def nHart: Int
  def nDataBit: Int

  def nGprReadPhy: Int
  def nGprWritePhy: Int
  def nGprReadLog: Int
  def nGprWriteLog: Int
  def nGprBypass: Int
}

case class GprConfig (
  debug: Boolean,
  nHart: Int,
  nDataBit: Int,

  nGprReadPhy: Int,
  nGprWritePhy: Int,
  nGprReadLog: Int,
  nGprWriteLog: Int,
  nGprBypass: Int
) extends GprParams

trait BackParams extends GprParams 
                  with DecoderParams
                  with CsrParams {
  def debug: Boolean
  def pcBoot: String
  def nHart: Int = 1
  def nAddrBit: Int
  def nDataBit: Int
  def nDataByte: Int = (nDataBit / 8).toInt

  def useChamp: Boolean
  def nField: Int
  def nPart: Int
  def nChampTrapLvl: Int

  def nBackPort: Int = 1
  def useExtM: Boolean
  def useExtA: Boolean
  def useExtB: Boolean
  def useExtZifencei: Boolean
  def useExtZicbo: Boolean
  def useCbo: Boolean = useExtZifencei || useExtZicbo
  def nExStage: Int 
  def useMemStage: Boolean
  def useWbData: Boolean = true
  def useBranchReg: Boolean
  def nGprReadPhy: Int = 2
  def nGprWritePhy: Int = 1
  def nGprReadLog: Int = 2
  def nGprWriteLog: Int = 1
  def nGprBypass: Int = {
    var nbyp: Int = 2
    if (useWbData) {
      nbyp = nbyp + 1
    }
    if (useMemStage) {
      nbyp = nbyp + nExStage
    }
    return nbyp
  }

  def pL0DBus: Mb4sParams = new Mb4sConfig (
    debug = debug,
    readOnly = false,
    nHart = 1,
    nAddrBit = nAddrBit,
    useAmo = useExtA,
    nDataByte = nDataByte,
    
    useField = useChamp,
    nField = nField,
    multiField = false
  )
}

case class BackConfig (
  debug: Boolean,
  pcBoot: String,
  nAddrBit: Int,
  nDataBit: Int,
  
  useChamp: Boolean,
  nField: Int,
  nPart: Int,
  nChampTrapLvl: Int,

  useExtM: Boolean,
  useExtA: Boolean,
  useExtB: Boolean,
  useExtZifencei: Boolean,
  useExtZicbo: Boolean,
  nExStage: Int,
  useMemStage: Boolean,
  useBranchReg: Boolean,
) extends BackParams
