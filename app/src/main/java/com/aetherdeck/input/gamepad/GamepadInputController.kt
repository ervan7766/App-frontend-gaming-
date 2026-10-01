package com.aetherdeck.input.gamepad

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.aetherdeck.core.logging.AetherLogger
import com.aetherdeck.core.logging.LogCategory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

@Serializable
enum class ControllerAction(val label: String, val defaultButtonHint: String) {
    NAV_UP("Navigate Up", "D-Pad Up / L-Stick"),
    NAV_DOWN("Navigate Down", "D-Pad Down / L-Stick"),
    NAV_LEFT("Navigate Left", "D-Pad Left / L-Stick"),
    NAV_RIGHT("Navigate Right", "D-Pad Right / L-Stick"),
    CONFIRM("Confirm / Select", "A"),
    BACK("Back", "B"),
    CONTEXT_MENU("Context Action", "X"),
    FAVORITE("Toggle Favorite", "Y"),
    PREV_SECTION("Previous Section", "L1"),
    NEXT_SECTION("Next Section", "R1"),
    FAST_SCROLL_UP("Fast Scroll Up", "L2"),
    FAST_SCROLL_DOWN("Fast Scroll Down", "R2"),
    MAIN_MENU("Main Menu / Settings", "START"),
    QUICK_MENU("Quick Menu / Search", "SELECT")
}

data class LiveControllerTelemetry(
    val detectedControllerName: String = "No Hardware Controller Connected",
    val isConnected: Boolean = false,
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val l2Trigger: Float = 0f,
    val r2Trigger: Float = 0f,
    val dpadUp: Boolean = false,
    val dpadDown: Boolean = false,
    val dpadLeft: Boolean = false,
    val dpadRight: Boolean = false,
    val buttonA: Boolean = false,
    val buttonB: Boolean = false,
    val buttonX: Boolean = false,
    val buttonY: Boolean = false,
    val buttonL1: Boolean = false,
    val buttonR1: Boolean = false,
    val buttonL2: Boolean = false,
    val buttonR2: Boolean = false,
    val buttonStart: Boolean = false,
    val buttonSelect: Boolean = false,
    val lastKeyCode: Int? = null,
    val lastKeyName: String = "None",
    val recentEvents: List<String> = emptyList()
)

class GamepadInputController {
    private val json = Json { ignoreUnknownKeys = true }

    private val _telemetry = MutableStateFlow(LiveControllerTelemetry())
    val telemetry: StateFlow<LiveControllerTelemetry> = _telemetry.asStateFlow()

    private val _actions = MutableSharedFlow<ControllerAction>(extraBufferCapacity = 16)
    val actions: SharedFlow<ControllerAction> = _actions.asSharedFlow()

    // Remapping capture state
    private val _remappingTarget = MutableStateFlow<ControllerAction?>(null)
    val remappingTarget: StateFlow<ControllerAction?> = _remappingTarget.asStateFlow()

    private var customBindings: MutableMap<Int, ControllerAction> = defaultBindings().toMutableMap()

    // Debounce & anti-double-input tracking between Stick and D-Pad
    private var lastDirectionalTimestampMs: Long = 0L
    private var lastDirectionalAction: ControllerAction? = null

    fun defaultBindings(): Map<Int, ControllerAction> = mapOf(
        KeyEvent.KEYCODE_DPAD_UP to ControllerAction.NAV_UP,
        KeyEvent.KEYCODE_DPAD_DOWN to ControllerAction.NAV_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to ControllerAction.NAV_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to ControllerAction.NAV_RIGHT,
        KeyEvent.KEYCODE_BUTTON_A to ControllerAction.CONFIRM,
        KeyEvent.KEYCODE_DPAD_CENTER to ControllerAction.CONFIRM,
        KeyEvent.KEYCODE_ENTER to ControllerAction.CONFIRM,
        KeyEvent.KEYCODE_BUTTON_B to ControllerAction.BACK,
        KeyEvent.KEYCODE_BUTTON_X to ControllerAction.CONTEXT_MENU,
        KeyEvent.KEYCODE_BUTTON_Y to ControllerAction.FAVORITE,
        KeyEvent.KEYCODE_BUTTON_L1 to ControllerAction.PREV_SECTION,
        KeyEvent.KEYCODE_BUTTON_R1 to ControllerAction.NEXT_SECTION,
        KeyEvent.KEYCODE_BUTTON_L2 to ControllerAction.FAST_SCROLL_UP,
        KeyEvent.KEYCODE_BUTTON_R2 to ControllerAction.FAST_SCROLL_DOWN,
        KeyEvent.KEYCODE_BUTTON_START to ControllerAction.MAIN_MENU,
        KeyEvent.KEYCODE_BUTTON_SELECT to ControllerAction.QUICK_MENU,
        KeyEvent.KEYCODE_BUTTON_MODE to ControllerAction.QUICK_MENU
    )

    fun refreshConnectedControllers() {
        try {
            val deviceIds = InputDevice.getDeviceIds()
            var foundName: String? = null
            for (id in deviceIds) {
                val dev = InputDevice.getDevice(id) ?: continue
                val sources = dev.sources
                val isGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                if (isGamepad && !dev.isVirtual) {
                    foundName = dev.name
                    if (dev.name.contains("Kishi", ignoreCase = true) ||
                        dev.name.contains("Xbox", ignoreCase = true) ||
                        dev.name.contains("Wireless Controller", ignoreCase = true)
                    ) {
                        break
                    }
                }
            }
            _telemetry.value = _telemetry.value.copy(
                detectedControllerName = foundName ?: "No Physical Gamepad Detected (Touch Ready)",
                isConnected = foundName != null
            )
        } catch (_: Throwable) {
            // Safe on Robolectric JVM
        }
    }

    fun loadBindingsFromJson(rawJson: String) {
        if (rawJson.isBlank()) {
            customBindings = defaultBindings().toMutableMap()
            return
        }
        runCatching {
            val parsed = json.decodeFromString<Map<Int, ControllerAction>>(rawJson)
            customBindings = defaultBindings().toMutableMap().apply { putAll(parsed) }
        }
    }

    fun exportBindingsToJson(): String {
        return json.encodeToString(customBindings.toMap())
    }

    fun resetBindingsToDefault(): String {
        customBindings = defaultBindings().toMutableMap()
        return exportBindingsToJson()
    }

    fun startRemapping(action: ControllerAction?) {
        _remappingTarget.value = action
    }

    fun handleKeyEvent(
        event: KeyEvent,
        swapAB: Boolean,
        swapXY: Boolean,
        debounceMs: Long = 110L
    ): Boolean {
        val keyCode = event.keyCode
        val isDown = event.action == KeyEvent.ACTION_DOWN
        val keyLabel = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

        // Update live telemetry state for Input Tester
        updateButtonTelemetry(keyCode, isDown, keyLabel)

        // If user is currently remapping a button in Button Remapper
        val targetRemap = _remappingTarget.value
        if (targetRemap != null && isDown) {
            customBindings[keyCode] = targetRemap
            _remappingTarget.value = null
            AetherLogger.info(LogCategory.INPUT, "Remapped $keyLabel -> ${targetRemap.name}")
            return true
        }

        if (!isDown) return false

        val effectiveKeyCode = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> if (swapAB) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_B -> if (swapAB) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_X -> if (swapXY) KeyEvent.KEYCODE_BUTTON_Y else KeyEvent.KEYCODE_BUTTON_X
            KeyEvent.KEYCODE_BUTTON_Y -> if (swapXY) KeyEvent.KEYCODE_BUTTON_X else KeyEvent.KEYCODE_BUTTON_Y
            else -> keyCode
        }

        val action = customBindings[effectiveKeyCode] ?: return false

        // Prevent double directional input between analog stick and D-Pad
        if (action in setOf(
                ControllerAction.NAV_UP,
                ControllerAction.NAV_DOWN,
                ControllerAction.NAV_LEFT,
                ControllerAction.NAV_RIGHT
            )
        ) {
            val now = System.currentTimeMillis()
            if (action == lastDirectionalAction && (now - lastDirectionalTimestampMs) < debounceMs) {
                return true
            }
            lastDirectionalTimestampMs = now
            lastDirectionalAction = action
        }

        _actions.tryEmit(action)
        return action !in setOf(
            ControllerAction.NAV_UP,
            ControllerAction.NAV_DOWN,
            ControllerAction.NAV_LEFT,
            ControllerAction.NAV_RIGHT,
            ControllerAction.CONFIRM
        )
    }

    fun handleMotionEvent(
        event: MotionEvent,
        deadzone: Float,
        debounceMs: Long = 160L
    ): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return false
        }

        val lx = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_X), deadzone)
        val ly = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Y), deadzone)
        val rx = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_Z), deadzone)
        val ry = applyDeadzone(event.getAxisValue(MotionEvent.AXIS_RZ), deadzone)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val l2 = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val r2 = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))

        _telemetry.value = _telemetry.value.copy(
            leftStickX = lx,
            leftStickY = ly,
            rightStickX = rx,
            rightStickY = ry,
            l2Trigger = l2,
            r2Trigger = r2,
            dpadLeft = hatX < -0.5f,
            dpadRight = hatX > 0.5f,
            dpadUp = hatY < -0.5f,
            dpadDown = hatY > 0.5f,
            buttonL2 = l2 > 0.5f,
            buttonR2 = r2 > 0.5f
        )

        val dirAction = when {
            ly < -0.6f || hatY < -0.5f -> ControllerAction.NAV_UP
            ly > 0.6f || hatY > 0.5f -> ControllerAction.NAV_DOWN
            lx < -0.6f || hatX < -0.5f -> ControllerAction.NAV_LEFT
            lx > 0.6f || hatX > 0.5f -> ControllerAction.NAV_RIGHT
            else -> null
        }

        if (dirAction != null) {
            val now = System.currentTimeMillis()
            if (dirAction != lastDirectionalAction || (now - lastDirectionalTimestampMs) >= debounceMs) {
                lastDirectionalTimestampMs = now
                lastDirectionalAction = dirAction
                _actions.tryEmit(dirAction)
            }
        }
        return false
    }

    private fun applyDeadzone(value: Float, deadzone: Float): Float {
        return if (abs(value) < deadzone) 0f else value
    }

    private fun updateButtonTelemetry(keyCode: Int, isDown: Boolean, keyLabel: String) {
        val current = _telemetry.value
        val updatedEvents = if (isDown) {
            (listOf("DOWN: $keyLabel ($keyCode)") + current.recentEvents).take(12)
        } else {
            current.recentEvents
        }
        _telemetry.value = current.copy(
            lastKeyCode = keyCode,
            lastKeyName = keyLabel,
            recentEvents = updatedEvents,
            dpadUp = if (keyCode == KeyEvent.KEYCODE_DPAD_UP) isDown else current.dpadUp,
            dpadDown = if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) isDown else current.dpadDown,
            dpadLeft = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) isDown else current.dpadLeft,
            dpadRight = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) isDown else current.dpadRight,
            buttonA = if (keyCode == KeyEvent.KEYCODE_BUTTON_A) isDown else current.buttonA,
            buttonB = if (keyCode == KeyEvent.KEYCODE_BUTTON_B) isDown else current.buttonB,
            buttonX = if (keyCode == KeyEvent.KEYCODE_BUTTON_X) isDown else current.buttonX,
            buttonY = if (keyCode == KeyEvent.KEYCODE_BUTTON_Y) isDown else current.buttonY,
            buttonL1 = if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) isDown else current.buttonL1,
            buttonR1 = if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) isDown else current.buttonR1,
            buttonL2 = if (keyCode == KeyEvent.KEYCODE_BUTTON_L2) isDown else current.buttonL2,
            buttonR2 = if (keyCode == KeyEvent.KEYCODE_BUTTON_R2) isDown else current.buttonR2,
            buttonStart = if (keyCode == KeyEvent.KEYCODE_BUTTON_START) isDown else current.buttonStart,
            buttonSelect = if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) isDown else current.buttonSelect
        )
    }
}
