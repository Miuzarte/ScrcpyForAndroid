package io.github.miuzarte.scrcpyforandroid.scrcpy

import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Gamepad passthrough (UHID virtual gamepad).
 *
 * The scrcpy server has no dedicated gamepad control message; it forwards a
 * virtual HID gamepad over the UHID (create/input/destroy) messages. This mirrors
 * upstream scrcpy `--gamepad=uhid`: we capture the physical controller connected to
 * this (controller-side) device, keep a per-device state, and emit a 15-byte HID
 * input report whenever a button or axis changes.
 */
object GamepadHid {
    const val VENDOR_ID = 0x045e // Microsoft
    const val PRODUCT_ID = 0x028e // Xbox 360 pad
    const val NAME = "Microsoft X-Box 360 Pad"
    const val REPORT_SIZE = 15

    // Reserved for future keyboard/mouse UHID devices (matches upstream ids).
    const val FIRST_ID = 3

    // Low 16 bits are forwarded "as is" into the report; the 4 dpad direction
    // bits live above them and are translated into a single hat-switch byte.
    private const val BUTTONS_MASK = 0xFFFF
    const val BIT_DPAD_UP = 0x10000
    const val BIT_DPAD_DOWN = 0x20000
    const val BIT_DPAD_LEFT = 0x40000
    const val BIT_DPAD_RIGHT = 0x80000
    const val DPAD_BITS_MASK = BIT_DPAD_UP or BIT_DPAD_DOWN or BIT_DPAD_LEFT or BIT_DPAD_RIGHT

    /**
     * True when [device] is a game controller. Android reports gamepad input across
     * several sources (SOURCE_GAMEPAD for buttons, SOURCE_JOYSTICK for axes, and
     * even KEYCODE_DPAD_* key events), so we key off the INPUT DEVICE's capabilities
     * rather than any single event source.
     */
    fun isGameController(device: InputDevice?): Boolean {
        if (device == null) return false
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD) != 0 ||
                (sources and InputDevice.SOURCE_JOYSTICK) != 0
    }

    /**
     * Standard gamepad report descriptor (Xbox-style). 15-byte reports:
     *   [0..1] left stick X, [2..3] left stick Y (0..65535)
     *   [4..5] right stick X, [6..7] right stick Y
     *   [8..9] L2, [10..11] R2 (0..32767)
     *   [12..13] buttons (16-bit little-endian), [14] hat switch (0..8)
     */
    val reportDescriptor: ByteArray = intArrayOf(
        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x05, // Usage (Gamepad)
        0xA1, 0x01, // Collection (Application)
        0xA1, 0x00, // Collection (Physical)

        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x30, // Usage (X)   left stick x
        0x09, 0x31, // Usage (Y)   left stick y
        0x09, 0x33, // Usage (Rx)  right stick x
        0x09, 0x34, // Usage (Ry)  right stick y
        0x15, 0x00, // Logical Minimum (0)
        0x27, 0xFF, 0xFF, 0x00, 0x00, // Logical Maximum (65535)
        0x75, 0x10, // Report Size (16)
        0x95, 0x04, // Report Count (4)
        0x81, 0x02, // Input (Data, Variable, Absolute)

        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x32, // Usage (Z)
        0x09, 0x35, // Usage (Rz)
        0x15, 0x00, // Logical Minimum (0)
        0x26, 0xFF, 0x7F, // Logical Maximum (32767)
        0x75, 0x10, // Report Size (16)
        0x95, 0x02, // Report Count (2)
        0x81, 0x02, // Input (Data, Variable, Absolute)

        0x05, 0x09, // Usage Page (Buttons)
        0x19, 0x01, // Usage Minimum (1)
        0x29, 0x10, // Usage Maximum (16)
        0x15, 0x00, // Logical Minimum (0)
        0x25, 0x01, // Logical Maximum (1)
        0x95, 0x10, // Report Count (16)
        0x75, 0x01, // Report Size (1)
        0x81, 0x02, // Input (Data, Variable, Absolute)

        0x05, 0x01, // Usage Page (Generic Desktop)
        0x09, 0x39, // Usage (Hat switch)
        0x15, 0x01, // Logical Minimum (1)
        0x25, 0x08, // Logical Maximum (8)
        0x75, 0x04, // Report Size (4)
        0x95, 0x01, // Report Count (1)
        0x81, 0x42, // Input (Data, Variable, Null State)

        0xC0, // End Collection
        0xC0, // End Collection
    ).map { it.toByte() }.toByteArray()

    /** Per-device gamepad state. */
    data class Slot(
        var buttons: Int = 0,
        var axisLeftX: Int = 0x8000,
        var axisLeftY: Int = 0x8000,
        var axisRightX: Int = 0x8000,
        var axisRightY: Int = 0x8000,
        var axisLeftTrigger: Int = 0,
        var axisRightTrigger: Int = 0,
    )

    /** Android keycode -> report bit (or dpad direction bit). */
    fun buttonBit(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> 0x0001 // south
        KeyEvent.KEYCODE_BUTTON_B -> 0x0002 // east
        KeyEvent.KEYCODE_BUTTON_X -> 0x0008 // west
        KeyEvent.KEYCODE_BUTTON_Y -> 0x0010 // north
        KeyEvent.KEYCODE_BUTTON_L1 -> 0x0040 // left shoulder
        KeyEvent.KEYCODE_BUTTON_R1 -> 0x0080 // right shoulder
        KeyEvent.KEYCODE_BUTTON_SELECT -> 0x0400 // back
        KeyEvent.KEYCODE_BUTTON_START -> 0x0800 // start
        KeyEvent.KEYCODE_BUTTON_MODE -> 0x1000 // guide
        KeyEvent.KEYCODE_BUTTON_THUMBL -> 0x2000 // left stick
        KeyEvent.KEYCODE_BUTTON_THUMBR -> 0x4000 // right stick
        KeyEvent.KEYCODE_DPAD_UP -> BIT_DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> BIT_DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> BIT_DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> BIT_DPAD_RIGHT
        else -> null
    }

    /**
     * Hat-switch value for the current dpad directions:
     *      8 1 2
     *      7 0 3
     *      6 5 4
     */
    fun dpadValue(buttons: Int): Int {
        val up = buttons and BIT_DPAD_UP != 0
        val down = buttons and BIT_DPAD_DOWN != 0
        val left = buttons and BIT_DPAD_LEFT != 0
        val right = buttons and BIT_DPAD_RIGHT != 0
        return when {
            up && left -> 8
            up && right -> 2
            up -> 1
            down && left -> 6
            down && right -> 4
            down -> 5
            left -> 7
            right -> 3
            else -> 0
        }
    }

    fun buildReport(slot: Slot): ByteArray {
        val data = ByteArray(REPORT_SIZE)
        writeLe16(data, 0, slot.axisLeftX)
        writeLe16(data, 2, slot.axisLeftY)
        writeLe16(data, 4, slot.axisRightX)
        writeLe16(data, 6, slot.axisRightY)
        writeLe16(data, 8, slot.axisLeftTrigger)
        writeLe16(data, 10, slot.axisRightTrigger)
        writeLe16(data, 12, slot.buttons and BUTTONS_MASK)
        data[14] = dpadValue(slot.buttons).toByte()
        return data
    }

    /** Normalize a raw axis value into [0, outMax]. */
    fun rescale(value: Float, min: Float, max: Float, outMax: Int): Int {
        if (max <= min) return outMax / 2
        val normalized = ((value - min) / (max - min)).coerceIn(0f, 1f)
        return (normalized * outMax).roundToInt().coerceIn(0, outMax)
    }

    private fun writeLe16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }
}

/**
 * Captures gamepad key/motion events from the controller-side device and forwards
 * them as a UHID virtual gamepad. A UHID_CREATE is sent lazily on the first event
 * of a controller; UHID_INPUT on every state change; UHID_DESTROY on [destroy].
 */
class GamepadInputHandler(
    private val scope: CoroutineScope,
    private val onUhidCreate: suspend (
        id: Int,
        vendorId: Int,
        productId: Int,
        name: String,
        reportDesc: ByteArray,
    ) -> Unit,
    private val onUhidInput: suspend (id: Int, data: ByteArray) -> Unit,
    private val onUhidDestroy: suspend (id: Int) -> Unit,
    private val onDebugChanged: ((String) -> Unit)? = null,
) {
    private class DeviceState {
        val slot = GamepadHid.Slot()
        var created = false
    }

    private val states = HashMap<Int, DeviceState>()
    private val idByDevice = HashMap<Int, Int>()
    private var nextId = GamepadHid.FIRST_ID

    // Serializes UHID_CREATE + UHID_INPUT for all devices so an input can never be
    // written ahead of the create of a controller (which would make the server drop it).
    private val sendMutex = Mutex()

    /** Handle a gamepad button key event. Returns true if it was consumed. */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!GamepadHid.isGameController(InputDevice.getDevice(event.deviceId))) return false
        val bit = GamepadHid.buttonBit(event.keyCode) ?: return false
        val deviceId = event.deviceId
        val state = ensureDevice(deviceId) ?: return true
        when (event.action) {
            KeyEvent.ACTION_DOWN -> state.slot.buttons = state.slot.buttons or bit
            KeyEvent.ACTION_UP -> state.slot.buttons = state.slot.buttons and bit.inv()
            else -> return true
        }
        send(deviceId, state)
        return true
    }

    /** Handle a gamepad axis motion event. Returns true if it was consumed. */
    fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (!GamepadHid.isGameController(InputDevice.getDevice(event.deviceId))) return false
        val deviceId = event.deviceId
        val state = ensureDevice(deviceId) ?: return true
        val device = InputDevice.getDevice(deviceId)
        val slot = state.slot

        slot.axisLeftX = readAxis(device, event, MotionEvent.AXIS_X, 65535) ?: slot.axisLeftX
        slot.axisLeftY = readAxis(device, event, MotionEvent.AXIS_Y, 65535) ?: slot.axisLeftY
        slot.axisRightX = readAxis(device, event, MotionEvent.AXIS_RX, 65535)
            ?: readAxis(device, event, MotionEvent.AXIS_Z, 65535)
                    ?: slot.axisRightX
        slot.axisRightY = readAxis(device, event, MotionEvent.AXIS_RY, 65535)
            ?: readAxis(device, event, MotionEvent.AXIS_RZ, 65535)
                    ?: slot.axisRightY
        slot.axisLeftTrigger = readAxis(device, event, MotionEvent.AXIS_LTRIGGER, 32767)
            ?: slot.axisLeftTrigger
        slot.axisRightTrigger = readAxis(device, event, MotionEvent.AXIS_RTRIGGER, 32767)
            ?: slot.axisRightTrigger

        // D-pad: some controllers report it only as a hat axis (AXIS_HAT_X/Y),
        // while others (or the OS compatibility layer) report it as KEYCODE_DPAD_*
        // key events. Handle the hat axis here so the d-pad still works either way.
        val hatX = readRawAxis(device, event, MotionEvent.AXIS_HAT_X)
        val hatY = readRawAxis(device, event, MotionEvent.AXIS_HAT_Y)
        if (hatX != null || hatY != null) {
            val hx = hatX ?: 0f
            val hy = hatY ?: 0f
            // Recompute the 4 d-pad direction bits from the analog hat position.
            slot.buttons = slot.buttons and GamepadHid.DPAD_BITS_MASK.inv()
            if (hy < -0.5f) slot.buttons = slot.buttons or GamepadHid.BIT_DPAD_UP
            if (hy > 0.5f) slot.buttons = slot.buttons or GamepadHid.BIT_DPAD_DOWN
            if (hx < -0.5f) slot.buttons = slot.buttons or GamepadHid.BIT_DPAD_LEFT
            if (hx > 0.5f) slot.buttons = slot.buttons or GamepadHid.BIT_DPAD_RIGHT
        }

        send(deviceId, state)
        return true
    }

    /** Destroy all created UHID devices (call on session end / screen dispose). */
    fun destroy() {
        val ids = idByDevice.values.toList()
        idByDevice.clear()
        states.clear()
        for (id in ids) {
            scope.launch {
                try {
                    onUhidDestroy(id)
                } catch (t: Throwable) {
                    Log.w(TAG, "uhidDestroy failed", t)
                }
            }
        }
    }

    private fun ensureDevice(deviceId: Int): DeviceState? {
        var state = states[deviceId]
        if (state == null) {
            val id = nextId++
            if (id > 0xFFFF) {
                // Exhausted ids; drop new controllers rather than breaking the stream.
                return null
            }
            idByDevice[deviceId] = id
            state = DeviceState()
            states[deviceId] = state
        }
        return state
    }

    private fun send(deviceId: Int, state: DeviceState) {
        val id = idByDevice[deviceId] ?: return
        val report = GamepadHid.buildReport(state.slot)
        scope.launch {
            // Hold the lock across create+input so the create is always transmitted
            // before the first input of a controller (even under a burst of events).
            sendMutex.withLock {
                if (!state.created) {
                    try {
                        onUhidCreate(
                            id,
                            GamepadHid.VENDOR_ID,
                            GamepadHid.PRODUCT_ID,
                            GamepadHid.NAME,
                            GamepadHid.reportDescriptor,
                        )
                        state.created = true
                    } catch (t: Throwable) {
                        Log.w(TAG, "uhidCreate failed", t)
                        // Keep created=false so a later event retries the create.
                    }
                }
                if (state.created) {
                    try {
                        onUhidInput(id, report)
                    } catch (t: Throwable) {
                        Log.w(TAG, "uhidInput failed", t)
                    }
                }
            }
        }
        notifyDebug()
    }

    private fun notifyDebug() {
        val callback = onDebugChanged ?: return
        callback(buildDebug())
    }

    private fun buildDebug(): String {
        if (states.isEmpty()) return ""
        return states.entries
            .sortedBy { it.key }
            .joinToString("\n") { (deviceId, st) ->
                val id = idByDevice[deviceId] ?: return@joinToString ""
                val s = st.slot
                buildString {
                    append("GP#$id")
                    append(" btn=0x").append((s.buttons and 0xFFFF).toString(16).padStart(4, '0'))
                    append(" L=(").append(s.axisLeftX).append(',').append(s.axisLeftY).append(')')
                    append(" R=(").append(s.axisRightX).append(',').append(s.axisRightY).append(')')
                    append(" LT=").append(s.axisLeftTrigger)
                    append(" RT=").append(s.axisRightTrigger)
                    append(" DPAD=").append(GamepadHid.dpadValue(s.buttons))
                }
            }
    }

    private fun readAxis(
        device: InputDevice?,
        event: MotionEvent,
        axis: Int,
        outMax: Int,
    ): Int? {
        // Only consider axes the controller actually exposes (via its motion ranges),
        // so an absent axis falls through to the next fallback axis.
        val range = device?.let { runCatching { it.getMotionRange(axis) }.getOrNull() }
            ?: return null
        val value = event.getAxisValue(axis)
        val min = range.min
        val max = range.max
        return GamepadHid.rescale(value, min, max, outMax)
    }

    /** Raw (unscaled) axis value, or null when the device does not expose [axis]. */
    private fun readRawAxis(
        device: InputDevice?,
        event: MotionEvent,
        axis: Int,
    ): Float? {
        val present = device?.let { runCatching { it.getMotionRange(axis) }.isSuccess } ?: false
        return if (present) event.getAxisValue(axis) else null
    }

    private companion object {
        const val TAG = "GamepadInput"
    }
}
