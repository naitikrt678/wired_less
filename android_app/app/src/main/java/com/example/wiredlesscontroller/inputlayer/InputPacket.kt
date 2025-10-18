package com.example.wiredlesscontroller.inputlayer

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class InputPacket(
    var buttons: Short = 0,
    var leftX: Short = 0,
    var leftY: Short = 0,
    var rightX: Short = 0,
    var rightY: Short = 0,
    var leftTrigger: Byte = 0,
    var rightTrigger: Byte = 0
) {
    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(PACKET_SIZE)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.putShort(buttons)
        buffer.putShort(leftX)
        buffer.putShort(leftY)
        buffer.putShort(rightX)
        buffer.putShort(rightY)
        buffer.put(leftTrigger)
        buffer.put(rightTrigger)
        buffer.put(ByteArray(4)) // Reserved bytes (4 bytes to make total 16)
        
        return buffer.array()
    }
    
    companion object {
        const val PACKET_SIZE = 16 // 2+2+2+2+2+1+1+4 = 16 bytes
    }
}