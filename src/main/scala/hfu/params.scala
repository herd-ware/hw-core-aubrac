/*
 * File: params.scala
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-03-01 12:33:27 pm
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
import herd.common.field._
import herd.common.isa.champ._
import herd.common.mem.mb4s._


trait HfuParams extends GenParams  {
  def debug: Boolean
  def pcBoot: String
  def nHart: Int
  def nAddrBit: Int
  def nDataBit: Int
  def nDataByte: Int = (nDataBit / 8).toInt
  
  def useChamp: Boolean
  def nChampReg: Int
  def useChampExtR: Boolean = false
  def useChampExtMie: Boolean
  def useChampExtFr: Boolean
  def useChampExtCst: Boolean
  def nChampTrapLvl: Int

  def useField: Boolean = useChamp
  def multiField: Boolean = false
  def nField: Int = if (useChampExtFr) 2 else 1
  def nPart: Int
  def nFieldFlushCycle: Int
  def nFieldIndex: Int = 6
  def nFieldBit: Int = nFieldIndex * nDataBit

  def nBypass: Int = 2

  def pFieldStruct: FieldStructParams = new FieldStructConfig (
    nDataBit = nDataBit,
    nTrapLvl = nChampTrapLvl,
    useRange = useChampExtR,
    useFr = useChampExtFr
  )

  def pL0DBus: Mb4sParams = new Mb4sConfig (
    debug = debug,
    readOnly = false,
    nHart = 1,
    nAddrBit = nAddrBit,
    useAmo = false,
    nDataByte = nDataByte,
    
    useField = useChamp,
    nField = nField,
    multiField = false
  )
}

case class HfuConfig (
  debug: Boolean,
  pcBoot: String,
  nHart: Int,
  nAddrBit: Int,
  nDataBit: Int,
  useChamp: Boolean,
  nChampReg: Int,
  useChampExtMie: Boolean,
  useChampExtFr: Boolean,
  useChampExtCst: Boolean,
  nChampTrapLvl: Int,
  
  nPart: Int,
  nFieldFlushCycle: Int
) extends HfuParams
