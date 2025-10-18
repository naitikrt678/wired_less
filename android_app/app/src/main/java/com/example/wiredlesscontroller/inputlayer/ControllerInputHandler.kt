package com.example.wiredlesscontroller.inputlayer

import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.util.*

class ControllerInputHandler {
    private val inputPacket = InputPacket()
    private var lastSendTime: Long = 0
    private val sendInterval: Long = 10 // 100 Hz = 10 ms interval
    private val TAG = "ControllerInputHandler"
    
    // Track hat state for canonical D-pad handling
    private var hatXState: Int = 0
    private var hatYState: Int = 0
    private var hatPresent: Boolean = false
    
    // Button mapping constants - Updated to match XInput bit constants
    companion object {
        // XInput D-pad bit constants
        const val DPAD_UP = 0x0001
        const val DPAD_DOWN = 0x0004
        const val DPAD_LEFT = 0x0008
        const val DPAD_RIGHT = 0x0002
        
        const val BUTTON_A = 0x1000
        const val BUTTON_B = 0x2000
        const val BUTTON_X = 0x4000
        const val BUTTON_Y = 0x8000
        const val BUTTON_START = 0x0010
        const val BUTTON_SELECT = 0x0020
        const val BUTTON_L1 = 0x0100
        const val BUTTON_R1 = 0x0200
        const val BUTTON_L3 = 0x0400
        const val BUTTON_R3 = 0x0800
        // Home button mapping - using 0x0040 as specified
        const val BUTTON_HOME = 0x0040
    }
    
    fun handleKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "KeyEvent received: keyCode=${event.keyCode}, action=${event.action}, device=${event.device?.name}")
        
        val button = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> {
                Log.d(TAG, "A button detected")
                BUTTON_A
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                Log.d(TAG, "B button detected")
                BUTTON_B
            }
            KeyEvent.KEYCODE_BUTTON_X -> {
                Log.d(TAG, "X button detected")
                BUTTON_X
            }
            KeyEvent.KEYCODE_BUTTON_Y -> {
                Log.d(TAG, "Y button detected")
                BUTTON_Y
            }
            // Only handle D-pad via key events if hat is not present
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!hatPresent) {
                    Log.d(TAG, "DPAD_UP detected via key event")
                    DPAD_UP
                } else {
                    0 // Ignore if hat is present
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!hatPresent) {
                    Log.d(TAG, "DPAD_DOWN detected via key event")
                    DPAD_DOWN
                } else {
                    0 // Ignore if hat is present
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!hatPresent) {
                    Log.d(TAG, "DPAD_LEFT detected via key event")
                    DPAD_LEFT
                } else {
                    0 // Ignore if hat is present
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!hatPresent) {
                    Log.d(TAG, "DPAD_RIGHT detected via key event")
                    DPAD_RIGHT
                } else {
                    0 // Ignore if hat is present
                }
            }
            KeyEvent.KEYCODE_BUTTON_START -> {
                Log.d(TAG, "START button detected")
                BUTTON_START
            }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                Log.d(TAG, "SELECT button detected")
                BUTTON_SELECT
            }
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                Log.d(TAG, "L1 button detected")
                BUTTON_L1
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                Log.d(TAG, "R1 button detected")
                BUTTON_R1
            }
            KeyEvent.KEYCODE_BUTTON_THUMBL -> {
                Log.d(TAG, "L3 button detected")
                BUTTON_L3
            }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                Log.d(TAG, "R3 button detected")
                BUTTON_R3
            }
            // Handle home button - specifically button index 12
            KeyEvent.KEYCODE_HOME -> {
                Log.d(TAG, "HOME button (KEYCODE_HOME) detected at ${System.currentTimeMillis()}")
                BUTTON_HOME
            }
            KeyEvent.KEYCODE_BUTTON_MODE -> {
                Log.d(TAG, "HOME button (KEYCODE_BUTTON_MODE) detected at ${System.currentTimeMillis()}")
                BUTTON_HOME
            }
            else -> {
                Log.d(TAG, "Unknown key code: ${event.keyCode}")
                return false
            }
        }
        
        if (button != 0) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                inputPacket.buttons = (inputPacket.buttons.toInt() or button).toShort()
                Log.d(TAG, "Button pressed, new button state: ${inputPacket.buttons}")
            } else if (event.action == KeyEvent.ACTION_UP) {
                inputPacket.buttons = (inputPacket.buttons.toInt() and button.inv()).toShort()
                Log.d(TAG, "Button released, new button state: ${inputPacket.buttons}")
            }
        }
        
        return true
    }
    
    fun handleMotionEvent(event: MotionEvent): Boolean {
        val inputDevice = event.device
        if (inputDevice == null) return false
        
        // Log motion events for debugging
        Log.d(TAG, "MotionEvent received: action=${event.action}, device=${inputDevice.name}")
        
        // Process all axes
        val leftXAxis = event.getAxisValue(MotionEvent.AXIS_X)
        val leftYAxis = event.getAxisValue(MotionEvent.AXIS_Y)
        val rightXAxis = event.getAxisValue(MotionEvent.AXIS_Z)
        val rightYAxis = event.getAxisValue(MotionEvent.AXIS_RZ)
        val leftTrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        val rightTrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
        
        // Check for D-pad axes (HAT)
        val hatXAxis = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatYAxis = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        
        // Log axis values
        Log.d(TAG, "Axis values - LX:$leftXAxis, LY:$leftYAxis, RX:$rightXAxis, RY:$rightYAxis, LT:$leftTrigger, RT:$rightTrigger, HAT_X:$hatXAxis, HAT_Y:$hatYAxis")
        
        // Handle D-pad from HAT axes as canonical source
        handleDpadFromHat(hatXAxis, hatYAxis)
        
        // Normalize values to short range (-32768 to 32767)
        inputPacket.leftX = (leftXAxis * 32767).toInt().toShort()
        // Invert Y-axis (positive up, negative down)
        inputPacket.leftY = (-leftYAxis * 32767).toInt().toShort()
        inputPacket.rightX = (rightXAxis * 32767).toInt().toShort()
        // Invert Y-axis (positive up, negative down)
        inputPacket.rightY = (-rightYAxis * 32767).toInt().toShort()
        
        // Normalize triggers to byte range (0 to 255)
        inputPacket.leftTrigger = (leftTrigger * 255).toInt().toByte()
        inputPacket.rightTrigger = (rightTrigger * 255).toInt().toByte()
        
        return true
    }
    
    fun getCurrentPacket(): InputPacket {
        Log.d(TAG, "Getting current packet: buttons=${inputPacket.buttons}, LX=${inputPacket.leftX}, LY=${inputPacket.leftY}, hat=($hatXState,$hatYState)")
        return inputPacket.copy()
    }
    
    private fun handleDpadFromHat(hatX: Float, hatY: Float) {
        // Convert float hat values to integer -1/0/1
        val hatXInt = when {
            hatX < -0.5f -> -1
            hatX > 0.5f -> 1
            else -> 0
        }
        
        val hatYInt = when {
            hatY < -0.5f -> -1
            hatY > 0.5f -> 1
            else -> 0
        }
        
        // Check if hat is present (non-zero values detected)
        if (hatXInt != 0 || hatYInt != 0) {
            hatPresent = true
        }
        
        // Update hat state
        hatXState = hatXInt
        hatYState = hatYInt
        
        // Clear D-pad bits from buttons
        inputPacket.buttons = (inputPacket.buttons.toInt() and (DPAD_UP or DPAD_DOWN or DPAD_LEFT or DPAD_RIGHT).inv()).toShort()
        
        // Map hat values to D-pad bits according to XInput constants
        // Corrected mapping: positive Y is down, negative Y is up
        var dpadBits = 0
        when (hatYInt) {
            1 -> dpadBits = dpadBits or DPAD_DOWN  // Positive Y is down
            -1 -> dpadBits = dpadBits or DPAD_UP   // Negative Y is up
        }
        when (hatXInt) {
            -1 -> dpadBits = dpadBits or DPAD_LEFT
            1 -> dpadBits = dpadBits or DPAD_RIGHT
        }
        
        // Set D-pad bits in buttons
        inputPacket.buttons = (inputPacket.buttons.toInt() or dpadBits).toShort()
        
        Log.d(TAG, "Hat state: ($hatXInt, $hatYInt), D-pad bits: 0x${dpadBits.toString(16)}")
    }
    
    fun shouldSendPacket(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSendTime >= sendInterval) {
            lastSendTime = currentTime
            return true
        }
        return false
    }
    
    // Reset hat state (for fallback detection)
    fun resetHatState() {
        hatPresent = false
        hatXState = 0
        hatYState = 0
    }
}