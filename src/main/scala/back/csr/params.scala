/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 10:58:24 pm                                       *
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

  def useCeps: Boolean
  def useDome: Boolean = useCeps
  def nDome: Int
  def multiDome: Boolean = false
  def nPart: Int
  def nCepsTrapLvl: Int
}

case class CsrConfig (
  debug: Boolean,
  nHart: Int,
  nAddrBit: Int,
  nDataBit: Int,
  
  useCeps: Boolean,
  nDome: Int,
  nPart: Int,
  nCepsTrapLvl: Int
) extends CsrParams