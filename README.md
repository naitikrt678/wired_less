# WiredLess Controller Bridge

A system that allows a wired DirectInput (HID) controller connected to an Android phone to act as a wireless XInput controller on a Windows PC using Wi-Fi.

## Architecture Overview

The system consists of three main layers:
1. **InputLayer**: Captures DirectInput/HID input events on Android
2. **TransportLayer**: Handles communication (Wi-Fi UDP currently, Bluetooth-ready)
3. **OutputLayer**: Receives data and injects it via ViGEm on the PC

## Setup Instructions

### Prerequisites
1. Install ViGEmBus driver on Windows PC from [ViGEm Downloads](https://github.com/ViGEm/ViGEmBus/releases)
2. Ensure both Android device and Windows PC are on the same Wi-Fi network

### Windows Application Setup 
1. Install Python 3.7 or higher
2. Install required packages:
   ```
   pip install -r requirements.txt
   ```
   Or run `install_requirements.bat` on Windows
3. Run the server application:
   ```
   python main.py
   ```
   Or run `run_server.bat` on Windows

## Usage Instructions

1. Run the PC app and start the server
2. Note the IP address displayed in the PC app
3. Open Android app and enter the PC's IP address and port (default 9999)
4. Press Connect in the Android app
5. Connect your controller to the Android device 
6. Verify that input reflects in both the PC UI and XInput tester

## Background Operation

The Android app can function in the background and while the device is asleep:

- **Background Service**: The app uses a foreground service to continue running when the app is not in the foreground
- **Wake Lock**: The app acquires a partial wake lock to keep the CPU running when the screen is off
- **Floating Notification**: When the gamepad and connection are active, a persistent notification is shown saying "Gamepad running: tap to open app"
- **Automatic Reconnection**: The app will automatically detect controller disconnects and reconnections

To enable background operation:
1. Connect your controller to the Android device
2. Connect to the PC server using the app
3. Press the home button or turn off the screen - the app will continue to function
4. The floating notification will remain visible, allowing you to quickly return to the app

## Controller Input Handling

The app properly captures all controller input, including the home button:

- **Input Capture**: All controller events are captured by the app and prevented from being processed as system navigation commands
- **Home Button Support**: The home button on controllers is now properly captured and sent to the PC as the GUIDE button
- **Full Button Mapping**: All standard controller buttons are supported (A, B, X, Y, D-pad, Start, Back, Home, LB, RB, LS, RS, triggers, and joysticks)
- **Button index 12 is Home. App consumes and forwards it as HOME bit (0x0040). App will not exit on HOME press; if device OEM blocks interception you may need to use vendor button codes.**

## Packet Format

Data is transmitted as 16-byte packets:
- `uint16 buttons_bitmask` (2 bytes)
- `int16 left_x` (2 bytes)
- `int16 left_y` (2 bytes)
- `int16 right_x` (2 bytes)
- `int16 right_y` (2 bytes)
- `uint8 left_trigger` (1 byte)
- `uint8 right_trigger` (1 byte)
- `uint8 reserved[4]` (4 bytes)

Transmission rate: 100 Hz

## Modular Architecture

### Transport Layer Interface
The system uses a modular transport layer that currently implements UDP but is designed for Bluetooth extension:

```kotlin
interface Transport {
    fun connect(ip: String, port: Int): Boolean
    fun send(data: ByteArray): Boolean
    fun disconnect(): Boolean
    fun isConnected(): Boolean
}
```

To add Bluetooth support, implement this interface in a `BluetoothTransport` class.

### Input Layer
The Android app captures input using Android's `InputDevice`, `KeyEvent`, and `MotionEvent` APIs at 100 Hz.

### Output Layer
The Windows app receives packets and translates them to XInput-compatible data using vgamepad with VX360Gamepad.

## Future Enhancements

Bluetooth integration can be added by:
1. Implementing a `BluetoothTransport` class that implements the Transport interface
2. Enabling the Bluetooth mode toggle in the Android UI
3. Multiple Controller Support, multiple phones, one pc server. 
4. Xbox and PS controller support ie controllers that dont have a dedicated "Android Mode"

## Troubleshooting

- If the controller is not detected, ensure it's properly connected via OTG adapter
- If connection fails, verify both devices are on the same network
- If input is not responding, check that the ViGEmBus driver is properly installed and vgamepad is working correctly
- For latency issues, ensure both devices have good Wi-Fi connectivity
- If the app exits when pressing the home button on your controller, ensure you're using the latest version with proper input capture
- **If the app crashes when connecting**: 
  1. Ensure the IP address is correct and the PC server is running
  2. Check that both devices are on the same Wi-Fi network
  3. Verify firewall settings on the PC are not blocking UDP port 9999
  4. Make sure the ViGEmBus driver is properly installed on the PC
  5. Try restarting both the Android app and PC server
  6. Check Android device logs for specific error messages

  ## NOTE
  The current build only supports controllers that have a dedicated "Android" mode. Xbox controllers straight up won't work and PS controllers will show wrong inputs.
