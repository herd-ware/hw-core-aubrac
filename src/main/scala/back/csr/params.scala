/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-27 05:30:34 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.back.csr

import chisel3._
import chisel3.util._
import scala.math._

import herd.common.gen._


trait CsrParams extends GenParams {
  def debug: Boolean
  def nHart: Int
  def nAddrBit: Int
  def nDataBit: Int

  def useChamp: Boolean
  def useDome: Boolean = useChamp
  def nDome: Int
  def multiDome: Boolean = false
  def nPart: Int
  def nChampTrapLvl: Int
}

case class CsrConfig (
  debug: Boolean,
  nHart: Int,
  nAddrBit: Int,
  nDataBit: Int,
  
  useChamp: Boolean,
  nDome: Int,
  nPart: Int,
  nChampTrapLvl: Int
) extends CsrParams