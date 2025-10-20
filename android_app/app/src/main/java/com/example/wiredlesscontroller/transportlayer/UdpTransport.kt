package com.example.wiredlesscontroller.transportlayer

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException

class UdpTransport : Transport {
    private var socket: DatagramSocket? = null
    private var serverAddress: InetAddress? = null
    private var serverPort: Int = 0
    private var connected = false
    private val TAG = "UdpTransport"

    override fun connect(ip: String, port: Int): Boolean {
        return try {
            Log.d(TAG, "Attempting to connect to $ip:$port")
            serverAddress = InetAddress.getByName(ip)
            serverPort = port
            socket = DatagramSocket()
            connected = true
            Log.d(TAG, "Successfully connected to $ip:$port")
            true
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host: $ip", e)
            connected = false
            false
        } catch (e: SocketException) {
            Log.e(TAG, "Socket exception: ${e.message}", e)
            connected = false
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            connected = false
            false
        }
    }

    override fun send(data: ByteArray): Boolean {
        if (!connected || serverAddress == null || socket == null) {
            Log.w(TAG, "Not connected or socket is null")
            return false
        }

        return try {
            Log.d(TAG, "Sending packet of size ${data.size} bytes")
            val packet = DatagramPacket(data, data.size, serverAddress, serverPort)
            socket?.send(packet)
            Log.d(TAG, "Packet sent successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data: ${e.message}", e)
            false
        }
    }

    override fun disconnect(): Boolean {
        return try {
            Log.d(TAG, "Disconnecting")
            socket?.close()
            socket = null
            serverAddress = null
            connected = false
            Log.d(TAG, "Disconnected")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect: ${e.message}", e)
            false
        }
    }

    override fun isConnected(): Boolean {
        val isSocketOpen = socket?.isClosed == false
        Log.d(TAG, "isConnected check: connected=$connected, socketOpen=$isSocketOpen")
        return connected && isSocketOpen
    }
}