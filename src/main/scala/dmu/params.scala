/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:56:19 pm                                       *
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
import herd.common.dome._
import herd.common.isa.champ._
import herd.common.mem.mb4s._


trait DmuParams extends GenParams  {
  def debug: Boolean
  def pcBoot: String
  def nHart: Int
  def nAddrBit: Int
  def nDataBit: Int
  def nDataByte: Int = (nDataBit / 8).toInt
  
  def useChamp: Boolean
  def useDome: Boolean = useChamp
  def multiDome: Boolean = false
  def useChampExtR: Boolean = false
  def useChampExtMie: Boolean
  def useChampExtFr: Boolean
  def useChampExtCst: Boolean
  def nChampTrapLvl: Int
  def nDome: Int = if (useChampExtFr) 2 else 1
  def nPart: Int
  def nDomeFlushCycle: Int
  def nDomeField: Int = 6
  def nDomeBit: Int = nDomeField * nDataBit

  def nDomeCfg: Int
  def nBypass: Int = 2

  def pDomeCfg: DomeCfgParams = new DomeCfgConfig (
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
    
    useDome = useChamp,
    nDome = nDome,
    multiDome = false
  )
}

case class DmuConfig (
  debug: Boolean,
  pcBoot: String,
  nHart: Int,
  nAddrBit: Int,
  nDataBit: Int,
  useChamp: Boolean,
  useChampExtMie: Boolean,
  useChampExtFr: Boolean,
  useChampExtCst: Boolean,
  nChampTrapLvl: Int,
  nPart: Int,
  nDomeFlushCycle: Int,

  nDomeCfg: Int
) extends DmuParams
