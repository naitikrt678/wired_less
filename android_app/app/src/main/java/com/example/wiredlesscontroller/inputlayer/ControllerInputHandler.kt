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
    
    // Track trigger button states for devices that report triggers as buttons
    private var leftTriggerButtonState: Boolean = false  // buttons[8]
    private var rightTriggerButtonState: Boolean = false // buttons[9]
    
    // Axis mapping detection
    private var leftXAxisIndex: Int = MotionEvent.AXIS_X
    private var leftYAxisIndex: Int = MotionEvent.AXIS_Y
    private var rightXAxisIndex: Int = MotionEvent.AXIS_Z
    private var rightYAxisIndex: Int = MotionEvent.AXIS_RZ
    private var leftTriggerAxisIndex: Int = MotionEvent.AXIS_LTRIGGER
    private var rightTriggerAxisIndex: Int = MotionEvent.AXIS_RTRIGGER
    private var hasDetectedAxes: Boolean = false
    private var deviceName: String? = null
    
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
        
        // Trigger threshold for button-based triggers
        const val TRIGGER_THRESHOLD = 0.1f
    }
    
    fun handleKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "KeyEvent received: keyCode=${event.keyCode}, action=${event.action}, device=${event.device?.name}")
        
        // Handle trigger buttons (buttons 8 and 9) - tester.py indicates these are left/right triggers
        when (event.keyCode) {
            10008 -> { // This is button index 8 (left trigger button)
                Log.d(TAG, "Left trigger button (button 8) detected, action=${event.action}")
                leftTriggerButtonState = event.action == KeyEvent.ACTION_DOWN
                // Don't return here, let it fall through to normal button handling if needed
            }
            10009 -> { // This is button index 9 (right trigger button)
                Log.d(TAG, "Right trigger button (button 9) detected, action=${event.action}")
                rightTriggerButtonState = event.action == KeyEvent.ACTION_DOWN
                // Don't return here, let it fall through to normal button handling if needed
            }
        }
        
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
        
        // Detect axes on first frames for this device
        if (!hasDetectedAxes || deviceName != inputDevice.name) {
            detectAxes(inputDevice)
            hasDetectedAxes = true
            deviceName = inputDevice.name
        }
        
        // Process all axes using detected mappings
        val leftXAxis = event.getAxisValue(leftXAxisIndex)
        val leftYAxis = event.getAxisValue(leftYAxisIndex)
        val rightXAxis = event.getAxisValue(rightXAxisIndex)
        val rightYAxis = event.getAxisValue(rightYAxisIndex)
        
        // Try to get trigger values from detected trigger axes first
        var leftTrigger = event.getAxisValue(leftTriggerAxisIndex)
        var rightTrigger = event.getAxisValue(rightTriggerAxisIndex)
        
        // If analog triggers are not available or zero, try alternative axes
        if (leftTrigger == 0.0f && leftTriggerAxisIndex != MotionEvent.AXIS_LTRIGGER) {
            // Try other possible left trigger axes
            val lTriggerValue = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
            val brakeValue = event.getAxisValue(MotionEvent.AXIS_BRAKE)
            leftTrigger = Math.max(leftTrigger, Math.max(lTriggerValue, brakeValue))
        }
        
        if (rightTrigger == 0.0f && rightTriggerAxisIndex != MotionEvent.AXIS_RTRIGGER) {
            // Try other possible right trigger axes
            val rTriggerValue = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
            val gasValue = event.getAxisValue(MotionEvent.AXIS_GAS)
            rightTrigger = Math.max(rightTrigger, Math.max(rTriggerValue, gasValue))
        }
        
        // Check for D-pad axes (HAT)
        val hatXAxis = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatYAxis = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        
        // Log axis values
        Log.d(TAG, "Axis values - LX:$leftXAxis, LY:$leftYAxis, RX:$rightXAxis, RY:$rightYAxis, LT:$leftTrigger, RT:$rightTrigger, HAT_X:$hatXAxis, HAT_Y:$hatYAxis")
        Log.d(TAG, "Axis indices - LX:$leftXAxisIndex, LY:$leftYAxisIndex, RX:$rightXAxisIndex, RY:$rightYAxisIndex, LT:$leftTriggerAxisIndex, RT:$rightTriggerAxisIndex")
        Log.d(TAG, "Trigger button states - Left:$leftTriggerButtonState, Right:$rightTriggerButtonState")
        
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
        // If we have analog trigger values, use them
        // Otherwise, fall back to button states
        if (leftTrigger > 0.0f) {
            inputPacket.leftTrigger = (leftTrigger * 255).toInt().toByte()
        } else if (leftTriggerButtonState) {
            // If button state indicates pressed, set to full value
            inputPacket.leftTrigger = 255.toByte()
        } else {
            inputPacket.leftTrigger = 0.toByte()
        }
        
        if (rightTrigger > 0.0f) {
            inputPacket.rightTrigger = (rightTrigger * 255).toInt().toByte()
        } else if (rightTriggerButtonState) {
            // If button state indicates pressed, set to full value
            inputPacket.rightTrigger = 255.toByte()
        } else {
            inputPacket.rightTrigger = 0.toByte()
        }
        
        Log.d(TAG, "Final trigger values - Left:${inputPacket.leftTrigger}, Right:${inputPacket.rightTrigger}")
        
        return true
    }
    
    private fun detectAxes(device: InputDevice) {
        Log.d(TAG, "Detecting axes for device: ${device.name}")
        
        // Reset to defaults first
        leftXAxisIndex = MotionEvent.AXIS_X
        leftYAxisIndex = MotionEvent.AXIS_Y
        rightXAxisIndex = MotionEvent.AXIS_Z
        rightYAxisIndex = MotionEvent.AXIS_RZ
        leftTriggerAxisIndex = MotionEvent.AXIS_LTRIGGER
        rightTriggerAxisIndex = MotionEvent.AXIS_RTRIGGER
        
        // Get all motion ranges for the device
        val motionRanges = device.motionRanges
        Log.d(TAG, "Found ${motionRanges.size} motion ranges")
        
        // Check for axes that might be used for right joystick
        var hasRX = false
        var hasRY = false
        var hasZ = false
        var hasRZ = false
        
        for (range in motionRanges) {
            val axis = range.axis
            Log.d(TAG, "Checking axis $axis (min=${range.min}, max=${range.max}, flat=${range.flat})")
            
            when (axis) {
                MotionEvent.AXIS_RX -> hasRX = true
                MotionEvent.AXIS_RY -> hasRY = true
                MotionEvent.AXIS_Z -> hasZ = true
                MotionEvent.AXIS_RZ -> hasRZ = true
            }
        }
        
        // Prefer RX/RY for right joystick if available, otherwise use Z/RZ
        if (hasRX && hasRY) {
            rightXAxisIndex = MotionEvent.AXIS_RX
            rightYAxisIndex = MotionEvent.AXIS_RY
            Log.d(TAG, "Using RX/RY for right joystick")
        } else if (hasZ && hasRZ) {
            rightXAxisIndex = MotionEvent.AXIS_Z
            rightYAxisIndex = MotionEvent.AXIS_RZ
            Log.d(TAG, "Using Z/RZ for right joystick")
        }
        
        Log.d(TAG, "Final axis mapping - LX:$leftXAxisIndex, LY:$leftYAxisIndex, RX:$rightXAxisIndex, RY:$rightYAxisIndex, LT:$leftTriggerAxisIndex, RT:$rightTriggerAxisIndex")
    }
    
    // Reset axis detection
    fun resetAxisDetection() {
        hasDetectedAxes = false
        deviceName = null
    }
    
    fun getCurrentPacket(): InputPacket {
        Log.d(TAG, "Getting current packet: buttons=${inputPacket.buttons}, LX=${inputPacket.leftX}, LY=${inputPacket.leftY}, hat=($hatXState,$hatYState), LT=${inputPacket.leftTrigger}, RT=${inputPacket.rightTrigger}")
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
    
    // Reset trigger states
    fun resetTriggerStates() {
        leftTriggerButtonState = false
        rightTriggerButtonState = false
    }
}