package simulator.device

import chisel3._
import bus._
import chisel3.util._
import difftest.UARTIO
import simulator.SimulatorConst
import utils._
import config.Config._

class DeviceTop[T <: Bundle](io_type: T = new AXI4) extends Module with SimulatorConst {
  val io = IO(new Bundle() {
    val in = Flipped(new AXI4)
    val uart = new UARTIO
  })

  val mem = Module(new Memory(io_type, memByte = memory_size, beatBytes))
  val uart = Module(new UART(io_type))
  val xbar = Module(new CrossBar1toN(io_type, deviceAddrSpace))

  xbar.io.in <> io.in

  uart.io.in <> xbar.io.out(0)
  mem.io.in <> xbar.io.out(1)

  io.uart <> uart.uartio
}

abstract class BaseDevice[T <: Bundle](io_type: T = new AXI4) extends Module {
  val io = IO(new Bundle() {
    val in = Flipped(io_type)
  })

  def HoldUnless(v: UInt, en: Bool) = Mux(en, v, RegEnable(next = v, enable = en))

  val raddr = Wire(UInt(PAddrBits.W))
  val rdata = Wire(UInt(XLEN.W))
  val waddr = Wire(UInt(PAddrBits.W))
  val wdata = Wire(UInt(XLEN.W))
  val wstrb = Wire(UInt((XLEN/8).W))
  val ren   = Wire(Bool())
  val wen   = Wire(Bool())

  val d_raddr = Wire(UInt(PAddrBits.W))
  val d_rdata = Wire(UInt(XLEN.W))

  d_raddr := DontCare
  d_rdata := DontCare

  io.in match {
    case in: DoubleSimpleBus =>

      raddr := in.imem.req.bits.addr
      d_raddr := in.dmem.req.bits.addr

      in.imem.resp.bits.data := rdata
      in.dmem.resp.bits.rdata := d_rdata

      // write event
      waddr := in.dmem.req.bits.addr
      wdata := in.dmem.req.bits.wdata
      wstrb := in.dmem.req.bits.strb
      ren := in.dmem.resp.fire
      wen := SimpleBusCmd.isWriteReq(cmd = in.dmem.req.bits.cmd)

    case axi4: AXI4 =>
      // read
      val c = Counter(256)
      val readBeatCnt = Counter(256)
      val len = HoldUnless(axi4.ar.bits.len, axi4.ar.fire)
      val burst = HoldUnless(axi4.ar.bits.burst, axi4.ar.fire)
      def WrapAddr(addr: UInt, len: UInt, size: UInt) = (addr & ~(len.asTypeOf(UInt(PAddrBits.W)) << size)).asUInt
      val wrapAddr = WrapAddr(axi4.ar.bits.addr, axi4.ar.bits.len, axi4.ar.bits.size)
      raddr := HoldUnless(wrapAddr, axi4.ar.fire)
      axi4.r.bits.last := (c.value === len)
      val ren = RegNext(axi4.ar.fire, init=false.B) || (axi4.r.fire && !axi4.r.bits.last)
      when (ren) {
        readBeatCnt.inc()
        when (burst === AXI4Parameters.BURST_WRAP && readBeatCnt.value === len) {
          readBeatCnt.value := 0.U
        }
      }
      when (axi4.r.fire()) {
        c.inc()
        when (axi4.r.bits.last) { c.value := 0.U }
      }
      when (axi4.ar.fire()) {
        readBeatCnt.value := (axi4.ar.bits.addr >> axi4.ar.bits.size) & axi4.ar.bits.len
        when (axi4.ar.bits.len =/= 0.U && axi4.ar.bits.burst === AXI4Parameters.BURST_WRAP) {
          assert(axi4.ar.bits.len === 1.U || axi4.ar.bits.len === 3.U ||
            axi4.ar.bits.len === 7.U || axi4.ar.bits.len === 15.U)
        }
      }
      axi4.r.bits.data := rdata
      // write
      val writeBeatCnt = Counter(256)
      waddr := HoldUnless(axi4.aw.bits.addr, axi4.aw.fire)
      wen := axi4.w.fire
      val fullMask = MaskExpand(axi4.w.bits.strb)
      wstrb := axi4.w.bits.strb
      def genWdata(originData: UInt) = (originData & ~fullMask) | (axi4.w.bits.data & fullMask)
      wdata := genWdata(axi4.w.bits.data)
      when (axi4.w.fire) {
        writeBeatCnt.inc()
        when (axi4.w.bits.last) {
          writeBeatCnt.value := 0.U
        }
      }
  }

}
