import sys
import struct
import socket
import threading
from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QLineEdit, QPushButton, QTextEdit, QGroupBox, QFormLayout, QCheckBox
)
from PyQt5.QtCore import Qt, pyqtSignal, QObject

# Replace vigemclient with vgamepad
try:
    import vgamepad
    from vgamepad import VX360Gamepad, XUSB_BUTTON
    VGAMEPAD_AVAILABLE = True
except ImportError:
    vgamepad = None
    VX360Gamepad = None
    XUSB_BUTTON = None
    VGAMEPAD_AVAILABLE = False
    print("vgamepad not available. Please install it with: pip install vgamepad")


def get_local_ip():
    """Get the local IP address of the machine"""
    try:
        # Create a socket and connect to a remote address to determine local IP
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        # Connect to a remote address (doesn't actually send data)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        # Fallback method
        try:
            return socket.gethostbyname(socket.gethostname())
        except Exception:
            return "127.0.0.1"


class InputData:
    def __init__(self):
        self.buttons = 0
        self.left_x = 0
        self.left_y = 0
        self.right_x = 0
        self.right_y = 0
        self.left_trigger = 0
        self.right_trigger = 0


class UdpReceiver(QObject):
    # Signal to communicate with the main thread
    data_received = pyqtSignal(InputData)
    client_disconnected = pyqtSignal(str)
    
    def __init__(self, port=9999):
        super().__init__()
        self.port = port
        self.socket = None
        self.running = False
        self._thread = None  # Use _thread to avoid conflict with QObject.thread
        self.client_address = None  # Initialize client_address attribute
        self.test_mode = False  # Test mode flag
        
    def start(self):
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.socket.bind(('', self.port))
            self.running = True
            
            # Start listening in a separate thread
            self._thread = threading.Thread(target=self.listen, daemon=True)
            self._thread.start()
            return True
        except Exception as e:
            print(f"Failed to start UDP receiver: {e}")
            return False
    
    def stop(self):
        self.running = False
        if self.socket:
            self.socket.close()
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=1.0)  # Wait for thread to finish
    
    def set_test_mode(self, enabled):
        self.test_mode = enabled
    
    def listen(self):
        while self.running:
            try:
                if self.socket is None:
                    continue
                data, addr = self.socket.recvfrom(16)  # Expecting 16-byte packets
                print(f"Received packet of size {len(data)} bytes from {addr}")
                if len(data) == 16:
                    # Parse the packet
                    input_data = InputData()
                    
                    # Unpack the data according to our protocol
                    # uint16 buttons, int16 left_x, int16 left_y, int16 right_x, int16 right_y,
                    # uint8 left_trigger, uint8 right_trigger, uint8 reserved[4]
                    # Format: <HhhhhBB4s (2+2+2+2+2+1+1+4 = 16 bytes)
                    unpacked = struct.unpack('<HhhhhBB4s', data)
                    
                    input_data.buttons = unpacked[0]
                    input_data.left_x = unpacked[1]
                    input_data.left_y = unpacked[2]
                    input_data.right_x = unpacked[3]
                    input_data.right_y = unpacked[4]
                    input_data.left_trigger = unpacked[5]
                    input_data.right_trigger = unpacked[6]
                    
                    # Diagnostic logging
                    print(f"Parsed packet: buttons={input_data.buttons:04x}, LX={input_data.left_x}, LY={input_data.left_y}, RX={input_data.right_x}, RY={input_data.right_y}, LT={input_data.left_trigger}, RT={input_data.right_trigger}")
                    
                    # Additional debugging for D-pad buttons
                    if input_data.buttons & 0x0001:  # DPAD_UP
                        print("DPAD_UP is pressed in received packet")
                    if input_data.buttons & 0x0002:  # DPAD_RIGHT
                        print("DPAD_RIGHT is pressed in received packet")
                    if input_data.buttons & 0x0004:  # DPAD_DOWN
                        print("DPAD_DOWN is pressed in received packet")
                    if input_data.buttons & 0x0008:  # DPAD_LEFT
                        print("DPAD_LEFT is pressed in received packet")
                    if input_data.buttons & 0x0040:  # HOME
                        print("HOME is pressed in received packet at timestamp {}".format(threading.current_thread().ident))
                    
                    # Emit signal to main thread
                    self.data_received.emit(input_data)
                    
                    # Track client address
                    if self.client_address != addr:
                        self.client_address = addr
                else:
                    print(f"Received malformed packet of size {len(data)}")
            except Exception as e:
                if self.running:  # Only print error if we're still supposed to be running
                    print(f"Error receiving data: {e}")
        
        if self.client_address:
            self.client_disconnected.emit(f"{self.client_address[0]}:{self.client_address[1]}")


class ControllerManager:
    def __init__(self):
        self.gamepad = None
        self.connected = False
        self.home_pressed = False  # Track HOME button state
        
    def connect(self):
        if not VGAMEPAD_AVAILABLE:
            print("vgamepad not available. Cannot create virtual controller.")
            return False
            
        try:
            # Create a virtual Xbox 360 gamepad using vgamepad
            if VX360Gamepad is not None:
                self.gamepad = VX360Gamepad()
                self.connected = True
                return True
            else:
                print("VX360Gamepad class is not available.")
                return False
        except Exception as e:
            print(f"Failed to create virtual Xbox 360 gamepad: {e}")
            self.connected = False
            return False
    
    def disconnect(self):
        try:
            if self.gamepad:
                # Send all-zero state update before disconnecting
                self.send_zero_state()
                # vgamepad doesn't require explicit cleanup in most cases
                self.gamepad = None
            self.connected = False
            self.home_pressed = False  # Reset HOME button state
        except Exception as e:
            print(f"Error disconnecting virtual controller: {e}")
    
    def send_zero_state(self):
        """Send an all-zero state update to clear the controller"""
        if self.gamepad and self.connected and XUSB_BUTTON is not None:
            try:
                # Release all buttons
                for button in [
                    XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP,
                    XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN,
                    XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT,
                    XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT,
                    XUSB_BUTTON.XUSB_GAMEPAD_START,
                    XUSB_BUTTON.XUSB_GAMEPAD_BACK,
                    XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB,
                    XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB,
                    XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER,
                    XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER,
                    XUSB_BUTTON.XUSB_GAMEPAD_GUIDE,
                    XUSB_BUTTON.XUSB_GAMEPAD_A,
                    XUSB_BUTTON.XUSB_GAMEPAD_B,
                    XUSB_BUTTON.XUSB_GAMEPAD_X,
                    XUSB_BUTTON.XUSB_GAMEPAD_Y
                ]:
                    self.gamepad.release_button(button=button)
                
                # Set all axes to zero
                self.gamepad.left_joystick(x_value=0, y_value=0)
                self.gamepad.right_joystick(x_value=0, y_value=0)
                self.gamepad.left_trigger(value=0)
                self.gamepad.right_trigger(value=0)
                
                # Update the gamepad state
                self.gamepad.update()
            except Exception as e:
                print(f"Error sending zero state: {e}")
    
    def update_input(self, input_data):
        if not self.connected or not self.gamepad or not VGAMEPAD_AVAILABLE or XUSB_BUTTON is None:
            return
            
        try:
            # Map buttons for Xbox 360 controller using correct XInput bit constants
            # D-pad mapping according to XInput specification
            if input_data.buttons & 0x0001:  # DPAD_UP
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_DPAD_UP)
                
            if input_data.buttons & 0x0002:  # DPAD_RIGHT
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_DPAD_RIGHT)
                
            if input_data.buttons & 0x0004:  # DPAD_DOWN
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_DPAD_DOWN)
                
            if input_data.buttons & 0x0008:  # DPAD_LEFT
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_DPAD_LEFT)
                
            if input_data.buttons & 0x0010:  # START
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_START)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_START)
                
            if input_data.buttons & 0x0020:  # BACK
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_BACK)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_BACK)
                
            # Handle HOME button (0x0040) - map to GUIDE button
            if input_data.buttons & 0x0040:  # HOME
                if not self.home_pressed:  # Only log on state change
                    print("HOME button pressed at timestamp {}".format(threading.current_thread().ident))
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_GUIDE)
                self.home_pressed = True
            else:
                if self.home_pressed:  # Only log on state change
                    print("HOME button released at timestamp {}".format(threading.current_thread().ident))
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_GUIDE)
                self.home_pressed = False
                
            if input_data.buttons & 0x0100:  # LEFT_SHOULDER
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_LEFT_SHOULDER)
                
            if input_data.buttons & 0x0200:  # RIGHT_SHOULDER
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_SHOULDER)
                
            if input_data.buttons & 0x0400:  # LEFT_THUMB
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_LEFT_THUMB)
                
            if input_data.buttons & 0x0800:  # RIGHT_THUMB
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_RIGHT_THUMB)
                
            if input_data.buttons & 0x1000:  # A
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_A)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_A)
                
            if input_data.buttons & 0x2000:  # B
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_B)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_B)
                
            if input_data.buttons & 0x4000:  # X
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_X)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_X)
                
            if input_data.buttons & 0x8000:  # Y
                self.gamepad.press_button(button=XUSB_BUTTON.XUSB_GAMEPAD_Y)
            else:
                self.gamepad.release_button(button=XUSB_BUTTON.XUSB_GAMEPAD_Y)
            
            # Set trigger values (0-255 range)
            self.gamepad.left_trigger(value=input_data.left_trigger)
            self.gamepad.right_trigger(value=input_data.right_trigger)
            
            # Set thumbstick values (-32768 to 32767 range)
            # Note: Y-axis is already inverted in the Android app
            self.gamepad.left_joystick(x_value=input_data.left_x, y_value=input_data.left_y)
            self.gamepad.right_joystick(x_value=input_data.right_x, y_value=input_data.right_y)
            
            # Update the gamepad state
            self.gamepad.update()
            
        except Exception as e:
            print(f"Error updating controller: {e}")


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("WiredLess Controller Bridge - Server")
        self.setGeometry(100, 100, 500, 500)
        
        self.udp_receiver = UdpReceiver()
        self.controller_manager = ControllerManager()
        
        # Connect signals
        self.udp_receiver.data_received.connect(self.on_data_received)
        self.udp_receiver.client_disconnected.connect(self.on_client_disconnected)
        
        self.init_ui()
        
    def init_ui(self):
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        
        main_layout = QVBoxLayout()
        central_widget.setLayout(main_layout)
        
        # Server controls group
        server_group = QGroupBox("Server Controls")
        server_layout = QFormLayout()
        
        # Get local IP address
        local_ip = get_local_ip()
        
        self.port_edit = QLineEdit("9999")
        self.start_button = QPushButton("Start Server")
        self.stop_button = QPushButton("Stop Server")
        self.stop_button.setEnabled(False)
        
        self.start_button.clicked.connect(self.start_server)
        self.stop_button.clicked.connect(self.stop_server)
        
        server_layout.addRow("Local IP Address:", QLabel(local_ip))
        server_layout.addRow("Port:", self.port_edit)
        server_layout.addRow(self.start_button, self.stop_button)
        
        server_group.setLayout(server_layout)
        main_layout.addWidget(server_group)
        
        # Test mode group
        test_group = QGroupBox("Test Mode")
        test_layout = QHBoxLayout()
        
        self.test_mode_checkbox = QCheckBox("Enable Test Mode")
        self.test_mode_checkbox.stateChanged.connect(self.toggle_test_mode)
        test_layout.addWidget(self.test_mode_checkbox)
        
        test_group.setLayout(test_layout)
        main_layout.addWidget(test_group)
        
        # Status group
        status_group = QGroupBox("Connection Status")
        status_layout = QVBoxLayout()
        
        self.status_label = QLabel("Status: Server stopped")
        self.client_label = QLabel("Client: None")
        self.controller_label = QLabel("Virtual Controller: Disconnected")
        self.hat_label = QLabel("Hat: (0,0)")
        self.dpad_label = QLabel("D-pad mask: 0x0000")
        self.home_label = QLabel("Home: Released")  # Add HOME state label
        
        # Add buttons to connect/disconnect virtual controller
        controller_buttons_layout = QHBoxLayout()
        self.connect_controller_button = QPushButton("Connect Virtual Controller")
        self.disconnect_controller_button = QPushButton("Disconnect Virtual Controller")
        self.disconnect_controller_button.setEnabled(False)
        
        self.connect_controller_button.clicked.connect(self.connect_virtual_controller)
        self.disconnect_controller_button.clicked.connect(self.disconnect_virtual_controller)
        
        controller_buttons_layout.addWidget(self.connect_controller_button)
        controller_buttons_layout.addWidget(self.disconnect_controller_button)
        
        status_layout.addWidget(self.status_label)
        status_layout.addWidget(self.client_label)
        status_layout.addWidget(self.controller_label)
        status_layout.addWidget(self.hat_label)
        status_layout.addWidget(self.dpad_label)
        status_layout.addWidget(self.home_label)  # Add HOME label to UI
        status_layout.addLayout(controller_buttons_layout)
        
        status_group.setLayout(status_layout)
        main_layout.addWidget(status_group)
        
        # Visualization group
        viz_group = QGroupBox("Input Visualization")
        viz_layout = QVBoxLayout()
        
        self.input_display = QTextEdit()
        self.input_display.setMaximumHeight(150)
        self.input_display.setReadOnly(True)
        
        viz_layout.addWidget(self.input_display)
        viz_group.setLayout(viz_layout)
        main_layout.addWidget(viz_group)
        
        # Log group
        log_group = QGroupBox("Logs")
        log_layout = QVBoxLayout()
        
        self.log_display = QTextEdit()
        self.log_display.setMaximumHeight(100)
        self.log_display.setReadOnly(True)
        
        log_layout.addWidget(self.log_display)
        log_group.setLayout(log_layout)
        main_layout.addWidget(log_group)
        
    def start_server(self):
        port = int(self.port_edit.text())
        self.udp_receiver.port = port
        
        if self.udp_receiver.start():
            self.status_label.setText(f"Status: Server running on port {port}")
            self.start_button.setEnabled(False)
            self.stop_button.setEnabled(True)
            self.log_message(f"Server started on port {port}")
        else:
            self.log_message("Failed to start server")
    
    def stop_server(self):
        self.udp_receiver.stop()
        self.status_label.setText("Status: Server stopped")
        self.start_button.setEnabled(True)
        self.stop_button.setEnabled(False)
        self.client_label.setText("Client: None")
        self.hat_label.setText("Hat: (0,0)")
        self.dpad_label.setText("D-pad mask: 0x0000")
        self.home_label.setText("Home: Released")  # Reset HOME label
        self.log_message("Server stopped")
    
    def toggle_test_mode(self, state):
        # State is an integer value, 2 for checked, 0 for unchecked
        enabled = state == 2
        self.udp_receiver.set_test_mode(enabled)
        self.log_message(f"Test mode {'enabled' if enabled else 'disabled'}")
    
    def connect_virtual_controller(self):
        if self.controller_manager.connect():
            self.controller_label.setText("Virtual Controller: Connected")
            self.connect_controller_button.setEnabled(False)
            self.disconnect_controller_button.setEnabled(True)
            self.log_message("Virtual controller connected")
        else:
            self.log_message("Failed to connect virtual controller")
    
    def disconnect_virtual_controller(self):
        self.controller_manager.disconnect()
        self.controller_label.setText("Virtual Controller: Disconnected")
        self.connect_controller_button.setEnabled(True)
        self.disconnect_controller_button.setEnabled(False)
        self.home_label.setText("Home: Released")  # Reset HOME label
        self.log_message("Virtual controller disconnected")
    
    def on_data_received(self, input_data):
        # Update the virtual controller if connected
        if self.controller_manager.connected:
            self.controller_manager.update_input(input_data)
        
        # Update visualization
        self.update_visualization(input_data)
    
    def on_client_disconnected(self, client_addr):
        self.client_label.setText(f"Client: {client_addr} (disconnected)")
        self.home_label.setText("Home: Released")  # Reset HOME label on disconnect
        self.log_message(f"Client {client_addr} disconnected")
    
    def update_visualization(self, input_data):
        # Create a text representation of the input data
        buttons_text = []
        if input_data.buttons & 0x0001: buttons_text.append("UP")
        if input_data.buttons & 0x0004: buttons_text.append("DOWN")
        if input_data.buttons & 0x0008: buttons_text.append("LEFT")
        if input_data.buttons & 0x0002: buttons_text.append("RIGHT")
        if input_data.buttons & 0x0010: buttons_text.append("START")
        if input_data.buttons & 0x0020: buttons_text.append("BACK")
        if input_data.buttons & 0x0040: buttons_text.append("HOME")  # Add HOME to visualization
        if input_data.buttons & 0x0100: buttons_text.append("LB")
        if input_data.buttons & 0x0200: buttons_text.append("RB")
        if input_data.buttons & 0x0400: buttons_text.append("LS")
        if input_data.buttons & 0x0800: buttons_text.append("RS")
        if input_data.buttons & 0x1000: buttons_text.append("A")
        if input_data.buttons & 0x2000: buttons_text.append("B")
        if input_data.buttons & 0x4000: buttons_text.append("X")
        if input_data.buttons & 0x8000: buttons_text.append("Y")
        
        buttons_str = ", ".join(buttons_text) if buttons_text else "None"
        
        display_text = f"""Buttons: {buttons_str} (0x{input_data.buttons:04x})
Left Stick: ({input_data.left_x}, {input_data.left_y})
Right Stick: ({input_data.right_x}, {input_data.right_y})
Triggers: Left={input_data.left_trigger}, Right={input_data.right_trigger}"""
        
        print(f"Updating visualization: {display_text}")
        
        self.input_display.setPlainText(display_text)
        
        # Also update client info and diagnostic labels
        if self.udp_receiver.client_address:
            client_addr = f"{self.udp_receiver.client_address[0]}:{self.udp_receiver.client_address[1]}"
            self.client_label.setText(f"Client: {client_addr}")
        
        # Update diagnostic labels
        # Extract hat information from buttons (this is a simplified approach)
        # In a real implementation, you might want to send hat values separately
        hat_x = 0
        hat_y = 0
        if input_data.buttons & 0x0001:  # UP
            hat_y = 1
        elif input_data.buttons & 0x0004:  # DOWN
            hat_y = -1
            
        if input_data.buttons & 0x0008:  # LEFT
            hat_x = -1
        elif input_data.buttons & 0x0002:  # RIGHT
            hat_x = 1
            
        self.hat_label.setText(f"Hat: ({hat_x},{hat_y})")
        self.dpad_label.setText(f"D-pad mask: 0x{input_data.buttons & 0x000F:04x}")
        
        # Update HOME button state in UI
        if input_data.buttons & 0x0040:  # HOME bit set
            self.home_label.setText("Home: Pressed")
        else:
            self.home_label.setText("Home: Released")
    
    def log_message(self, message):
        self.log_display.append(message)
    
    def closeEvent(self, a0):
        # Clean up resources
        self.udp_receiver.stop()
        self.controller_manager.disconnect()
        super().closeEvent(a0)

if __name__ == "__main__":
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec_())