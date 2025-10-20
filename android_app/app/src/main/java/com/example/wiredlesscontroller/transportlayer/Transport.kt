package com.example.wiredlesscontroller.transportlayer

interface Transport {
    fun connect(ip: String, port: Int): Boolean
    fun send(data: ByteArray): Boolean
    fun disconnect(): Boolean
    fun isConnected(): Boolean
}