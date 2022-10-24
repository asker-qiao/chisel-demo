package soc.cpu

import chisel3._
import chisel3.util._

object RVZicsr {
  def CSRRW     = BitPat("b?????????????????001?????1110011")
  def CSRRS     = BitPat("b?????????????????010?????1110011")
  def CSRRC     = BitPat("b?????????????????011?????1110011")

  def CSRRWI    = BitPat("b?????????????????101?????1110011")
  def CSRRSI    = BitPat("b?????????????????110?????1110011")
  def CSRRCI    = BitPat("b?????????????????111?????1110011")

  val table = Array(
    CSRRW  -> List(InstrType.i, SrcType.no, SrcType.no, FuType.alu, ALUOpType.ADD, MemType.N, MemOpType.no, WBCtrl.CSR_W)
    // TODO:

  )
}
