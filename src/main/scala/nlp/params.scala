/*
 * File: params.scala                                                          *
 * Created Date: 2023-02-25 10:19:59 pm                                        *
 * Author: Mathieu Escouteloup                                                 *
 * -----                                                                       *
 * Last Modified: 2023-02-25 11:06:14 pm                                       *
 * Modified By: Mathieu Escouteloup                                            *
 * -----                                                                       *
 * License: See LICENSE.md                                                     *
 * Copyright (c) 2023 HerdWare                                                 *
 * -----                                                                       *
 * Description:                                                                *
 */


package herd.core.aubrac.nlp

import chisel3._
import chisel3.util._


// ******************************
//              BTB
// ******************************
trait BtbParams {
  def useDome: Boolean
  def nDome: Int

  def nReadPort: Int
  def nLine: Int
  def nTagBit: Int
  def nTargetBit: Int
}

case class BtbConfig (
  useDome: Boolean,
  nDome: Int,

  nReadPort: Int,
  nLine: Int,
  nTagBit: Int,
  nTargetBit: Int
) extends BtbParams

// ******************************
//              BHT
// ******************************
trait BhtParams {
  def useDome: Boolean
  def nDome: Int

  def nReadPort: Int
  def nBit: Int
  def nSet: Int
  def nSetEntry: Int
  def nSetBit: Int = nSetEntry * nBit
  def nEntry: Int = nSet * nSetEntry
}

case class BhtConfig (
  useDome: Boolean,
  nDome: Int,

  nReadPort: Int,
  nSet: Int,
  nSetEntry: Int,
  nBit: Int
) extends BhtParams

// ******************************
//              RSB
// ******************************
trait RsbParams {
  def useDome: Boolean
  def nDome: Int

  def useSpec: Boolean
  def nDepth: Int
  def nTargetBit: Int
}

case class RsbConfig (
  useDome: Boolean,
  nDome: Int,

  useSpec: Boolean,
  nDepth: Int,
  nTargetBit: Int
) extends RsbParams

// ******************************
//              NLP
// ******************************
trait NlpParams {
  def useDome: Boolean
  def nDome: Int

  def nAddrBit: Int
  def nInstrByte: Int
  def nFetchInstr: Int
  def nTagBit: Int = nAddrBit - log2Floor(nInstrByte)

  def nBtbLine: Int
  def pBtb: BtbParams = new BtbConfig(
    useDome = useDome,
    nDome = nDome,

    nReadPort = nFetchInstr,
    nLine = nBtbLine,
    nTagBit = nTagBit,
    nTargetBit = nTagBit
  )

  def nBhtSet: Int
  def nBhtSetEntry: Int
  def nBhtBit: Int
  def pBht: BhtParams = new BhtConfig(
    useDome = useDome,
    nDome = nDome,

    nReadPort = nFetchInstr,
    nSet = nBhtSet,
    nSetEntry = nBhtSetEntry,
    nBit = nBhtBit
  )

  def useRsbSpec: Boolean
  def nRsbDepth: Int
  def pRsb: RsbParams = new RsbConfig(
    useDome = useDome,
    nDome = nDome,

    useSpec = useRsbSpec,
    nDepth = nRsbDepth,
    nTargetBit = nTagBit
  )
}

case class NlpConfig ( 
  useDome: Boolean,
  nDome: Int,

  nAddrBit: Int,
  nInstrByte: Int,
  nFetchInstr: Int,

  nBtbLine: Int,

  nBhtSet: Int,
  nBhtSetEntry: Int,
  nBhtBit: Int,
  
  useRsbSpec: Boolean,
  nRsbDepth: Int
) extends NlpParams
