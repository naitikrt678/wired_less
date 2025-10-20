package com.example.wiredlesscontroller

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.wiredlesscontroller.databinding.ActivityMainBinding
import com.example.wiredlesscontroller.inputlayer.ControllerInputHandler
import com.example.wiredlesscontroller.transportlayer.UdpTransport
import java.util.*
import kotlin.concurrent.timerTask
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.content.ComponentName
import android.content.Context
import android.util.Log

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var udpTransport: UdpTransport
    private lateinit var controllerHandler: ControllerInputHandler
    private var sendTimer: Timer? = null
    private var isConnected = false
    private var controllerDetected = false
    
    // Service connection
    private var controllerService: ControllerService? = null
    private var isServiceBound = false
    private val TAG = "MainActivity"
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                val binder = service as ControllerService.LocalBinder
                controllerService = binder.getService()
                isServiceBound = true
                Log.d(TAG, "Service connected")
                
                // If we're already connected, update the notification
                if (isConnected) {
                    controllerService?.updateNotificationText("Gamepad running: tap to open app")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onServiceConnected: ${e.message}", e)
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            isServiceBound = false
            controllerService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        udpTransport = UdpTransport()
        controllerHandler = ControllerInputHandler()

        setupUI()
        checkForController()
        
        // Set up the activity to capture all game controller events
        setupGameControllerCapture()
        
        // Bind to the service
        try {
            Intent(this, ControllerService::class.java).also { intent ->
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                Log.d(TAG, "Service bind initiated")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error binding service: ${e.message}", e)
        }
    }
    
    private fun setupGameControllerCapture() {
        // This will help ensure our app captures all game controller events
        // We'll set flags to indicate we want to handle all gamepad events
    }
    
    private fun setupUI() {
        binding.buttonConnect.setOnClickListener {
            connectToServer()
        }

        binding.buttonDisconnect.setOnClickListener {
            disconnectFromServer()
        }

        // Bluetooth button is disabled for now as per requirements
        binding.buttonBluetooth.setOnClickListener {
            // Placeholder for future Bluetooth implementation
        }
    }

    private fun connectToServer() {
        Log.d(TAG, "connectToServer called")
        val ip = binding.editTextIP.text.toString()
        val port = binding.editTextPort.text.toString().toIntOrNull() ?: 9999

        try {
            Log.d(TAG, "Attempting to connect to $ip:$port")
            if (udpTransport.connect(ip, port)) {
                isConnected = true
                updateUI(true)
                Log.d(TAG, "UDP connection successful")
                
                // Start the foreground service
                try {
                    Intent(this, ControllerService::class.java).also { intent ->
                        intent.putExtra("ip", ip)
                        intent.putExtra("port", port)
                        startService(intent)
                        Log.d(TAG, "Service start initiated")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting service: ${e.message}", e)
                    binding.textViewStatus.text = "Status: Service start error"
                    return
                }
                
                // Update notification text if service is bound
                if (isServiceBound) {
                    try {
                        controllerService?.updateNotificationText("Gamepad running: tap to open app")
                        Log.d(TAG, "Notification updated")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating notification: ${e.message}", e)
                    }
                } else {
                    Log.d(TAG, "Service not yet bound, notification will be updated when bound")
                }
                
                // Start sending input data
                startSendingData()
                Log.d(TAG, "Started sending data")
            } else {
                Log.e(TAG, "UDP connection failed")
                binding.textViewStatus.text = "Status: Connection failed"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in connectToServer: ${e.message}", e)
            binding.textViewStatus.text = "Status: Connection error"
        }
    }

    private fun disconnectFromServer() {
        Log.d(TAG, "disconnectFromServer called")
        stopSendingData()
        try {
            if (udpTransport.disconnect()) {
                Log.d(TAG, "UDP disconnect successful")
            } else {
                Log.w(TAG, "UDP disconnect reported failure")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting UDP: ${e.message}", e)
        }
        isConnected = false
        updateUI(false)
        
        // Stop the foreground service
        try {
            Intent(this, ControllerService::class.java).also { intent ->
                if (stopService(intent)) {
                    Log.d(TAG, "Service stop successful")
                } else {
                    Log.w(TAG, "Service stop reported failure")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service: ${e.message}", e)
        }
        
        // Update notification text if service is bound
        if (isServiceBound) {
            try {
                controllerService?.updateNotificationText("Gamepad disconnected")
                Log.d(TAG, "Notification updated to disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating notification: ${e.message}", e)
            }
        }
    }

    private fun startSendingData() {
        Log.d(TAG, "Starting data sending timer")
        sendTimer = Timer()
        sendTimer?.scheduleAtFixedRate(timerTask {
            if (isConnected && controllerHandler.shouldSendPacket()) {
                val packet = controllerHandler.getCurrentPacket()
                Log.d(TAG, "Sending packet: buttons=${packet.buttons}, LX=${packet.leftX}, LY=${packet.leftY}, RX=${packet.rightX}, RY=${packet.rightY}")
                val result = udpTransport.send(packet.toByteArray())
                Log.d(TAG, "Send result: $result")
            }
        }, 0, 10) // 100 Hz = 10 ms interval
    }

    private fun stopSendingData() {
        sendTimer?.cancel()
        sendTimer = null
    }

    private fun updateUI(connected: Boolean) {
        runOnUiThread {
            if (connected) {
                binding.buttonConnect.isEnabled = false
                binding.buttonDisconnect.isEnabled = true
                binding.textViewStatus.text = "Status: Connected to ${binding.editTextIP.text}:${binding.editTextPort.text}"
            } else {
                binding.buttonConnect.isEnabled = true
                binding.buttonDisconnect.isEnabled = false
                binding.textViewStatus.text = "Status: Disconnected"
            }
        }
    }

    private fun checkForController() {
        // Check for connected controllers periodically
        Timer().scheduleAtFixedRate(timerTask {
            val devices = InputDevice.getDeviceIds()
            var foundController = false
            
            for (deviceId in devices) {
                val device = InputDevice.getDevice(deviceId)
                if (device != null && isGameController(device)) {
                    foundController = true
                    break
                }
            }
            
            if (foundController != controllerDetected) {
                controllerDetected = foundController
                runOnUiThread {
                    binding.textViewControllerStatus.text = if (controllerDetected) {
                        "Controller: Detected"
                    } else {
                        "Controller: Not detected"
                    }
                }
            }
        }, 0, 1000) // Check every second
    }

    private fun isGameController(device: InputDevice): Boolean {
        val sources = device.sources
        return (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyDown called: keyCode=$keyCode, device=${event.device?.name}")
        // Check if this is a game controller event
        if (isGameController(event.device)) {
            Log.d(TAG, "Game controller event detected")
            // Handle the event in our app and prevent system from processing it
            val handled = controllerHandler.handleKeyEvent(event)
            Log.d(TAG, "KeyEvent handled: $handled")
            // Return true to indicate we've handled the event
            // This prevents the system from processing it as a navigation command
            // Special handling for HOME button - always consume it to prevent app exit
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                Log.d(TAG, "Consuming HOME button to prevent app exit")
                return true
            }
            return handled || super.onKeyDown(keyCode, event)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyUp called: keyCode=$keyCode, device=${event.device?.name}")
        // Check if this is a game controller event
        if (isGameController(event.device)) {
            Log.d(TAG, "Game controller event detected")
            // Handle the event in our app and prevent system from processing it
            val handled = controllerHandler.handleKeyEvent(event)
            Log.d(TAG, "KeyEvent handled: $handled")
            // Return true to indicate we've handled the event
            // This prevents the system from processing it as a navigation command
            // Special handling for HOME button - always consume it to prevent app exit
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                Log.d(TAG, "Consuming HOME button to prevent app exit")
                return true
            }
            return handled || super.onKeyUp(keyCode, event)
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        Log.d(TAG, "onGenericMotionEvent called: action=${event.action}, device=${event.device?.name}")
        // Check if this is a game controller event
        if (isGameController(event.device) && 
            event.action == MotionEvent.ACTION_MOVE) {
            Log.d(TAG, "Game controller motion event detected")
            // Handle the event in our app and prevent system from processing it
            val handled = controllerHandler.handleMotionEvent(event)
            Log.d(TAG, "MotionEvent handled: $handled")
            // Return true to indicate we've handled the event
            return handled || super.onGenericMotionEvent(event)
        }
        return super.onGenericMotionEvent(event)
    }
    
    // Add this method to capture all key events when a controller is connected
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.d(TAG, "dispatchKeyEvent called: keyCode=${event.keyCode}, action=${event.action}, device=${event.device?.name}")
        // If we have a game controller connected, capture all its events
        if (controllerDetected && isGameController(event.device)) {
            Log.d(TAG, "Dispatching game controller event")
            // Handle the event in our app
            when (event.action) {
                KeyEvent.ACTION_DOWN -> return onKeyDown(event.keyCode, event)
                KeyEvent.ACTION_UP -> return onKeyUp(event.keyCode, event)
            }
        }
        return super.dispatchKeyEvent(event)
    }
    
    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        super.onDestroy()
        stopSendingData()
        if (isConnected) {
            try {
                if (udpTransport.disconnect()) {
                    Log.d(TAG, "UDP disconnect successful in onDestroy")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting UDP in onDestroy: ${e.message}", e)
            }
        }
        
        // Unbind from the service
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
                Log.d(TAG, "Service unbound")
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding service: ${e.message}", e)
            }
            isServiceBound = false
        }
    }
}