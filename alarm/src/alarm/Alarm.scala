package alarm

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.com.uart._

object Led extends SpinalEnum {
  val on, off = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding")(
    on -> 0,
    off -> 1
  )

  def onIf(c: C, v: Bool) =
    when(v) {
      c := on
    } otherwise {
      c := off
    }
}

class SensorExtSig extends Bundle {
  val sig = in Bool ()
  val enable_sw = in Bool ()

  val relay = out Bool ()
  val disable_led, inv_led, exist_led, empty_led = out(Led())
}
class Sensor extends Component {
  val io = new Bundle {
    val ext = new SensorExtSig()
    val enable, condition = in Bool ()

    val result = out Bool ()
  }

  io.ext.relay := io.ext.enable_sw

  when(io.ext.enable_sw && io.enable) {
    io.ext.disable_led := Led.off
    Led.onIf(io.ext.inv_led, !io.condition)
    Led.onIf(io.ext.exist_led, io.ext.sig)
    Led.onIf(io.ext.empty_led, !io.ext.sig)

    io.result := io.ext.sig === io.condition
  } otherwise {
    io.ext.disable_led := Led.on
    io.ext.inv_led := Led.off
    io.ext.exist_led := Led.off
    io.ext.empty_led := Led.off

    io.result := True
  }
}

class IrExtSig extends Bundle {
  val uart = master(Uart())

  val enable_sw = in Bool ()

  val disable_led = out(Led())
}
class IrReceiver extends Component {
  val io = new Bundle {
    val ext = new IrExtSig()
    val code = master Stream (Bits(24 bits))
  }

  val uart = UartCtrl(
    config = UartCtrlInitConfig(
      baudrate = 9600,
      dataLength = 7,
      parity = UartParityType.NONE,
      stop = UartStopType.ONE
    ),
    readonly = true
  )
  uart.io.uart <> io.ext.uart

  val adapter =
    StreamWidthAdapter(
      uart.io.read.throwWhen(!io.ext.enable_sw),
      io.code,
      order = HIGHER_FIRST
    )

  Led.onIf(io.ext.disable_led, !io.ext.enable_sw)
}

object Beep extends SpinalEnum {
  val on, off = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding")(
    on -> 1,
    off -> 0
  )
}
class AlarmExtSig extends Bundle {
  val enable = in Bool ()

  val beep = out(Beep())
  val alarm_led, silent_led, disable_led = out(Led())
}
class Beep extends Component {
  val io = new Bundle {
    val ext = new AlarmExtSig()
    val input = in Bool ()
    val silent, disable = in Bool ()
  }

  when(io.disable || !io.ext.enable) {
    io.ext.disable_led := Led.on
    io.ext.alarm_led := Led.off
    io.ext.silent_led := Led.off
    io.ext.beep := Beep.off
  } otherwise {
    io.ext.disable_led := Led.off

    Led.onIf(io.ext.alarm_led, io.input)
    Led.onIf(io.ext.silent_led, io.silent)

    when(io.input && !io.silent) {
      io.ext.beep := Beep.on
    } otherwise {
      io.ext.beep := Beep.off
    }
  }
}

//Hardware definition
class Alarm extends Component {
  val io = new Bundle {
    val alarm = new AlarmExtSig()
    val ir = new IrExtSig()
    val outer_sensor = new SensorExtSig()
    val inner_sensor = new SensorExtSig()
  }

  val alarm_enable, alarm_silent = Reg(Bool()) init (True)
  val alarm = new Beep()
  alarm.io.ext <> io.alarm
  alarm.io.silent <> alarm_silent
  alarm.io.disable := !alarm_enable

  val outer_enable, inner_enable = Reg(Bool()) init (True)
  val outer_mode, inner_mode = Reg(Bool()) init (True)

  val outer = new Sensor()
  outer.io.ext <> io.outer_sensor
  outer.io.enable <> outer_enable
  outer.io.condition <> outer_mode

  val inner = new Sensor()
  inner.io.ext <> io.inner_sensor
  inner.io.enable <> inner_enable
  inner.io.condition <> inner_mode

  alarm.io.input := outer.io.result && inner.io.result

  val ir = new IrReceiver()
  ir.io.ext <> io.ir

  def keycode(code: Int) = B(0x00ff00 | code)
  val code = ir.io.code.toFlow

  when(code.valid) {
    switch(code.payload) {
      is(keycode(0x47)) { // silent
        alarm_silent := !alarm_silent
      }
      is(keycode(0x43)) { // inner enable
        inner_enable := !inner_enable
      }
      is(keycode(0x40)) { // outer enable
        outer_enable := !outer_enable
      }
      is(keycode(0x44)) { // alarm
        alarm_enable := !alarm_enable
      }
      is(keycode(0x46)) { // inner mode
        inner_mode := !inner_mode
      }
      is(keycode(0x45)) { // outer mode
        outer_mode := !outer_mode
      }
    }
  }
}

object AlarmVerilog {
  def main(args: Array[String]) {
    SpinalConfig(
      mode = Verilog,
      defaultClockDomainFrequency = FixedFrequency(25 MHz),
      defaultConfigForClockDomains = ClockDomainConfig(resetActiveLevel = LOW)
    ).generate(new Alarm)
  }
}
