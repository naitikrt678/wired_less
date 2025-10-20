package com.example.wiredlesscontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class ControllerService : Service() {
    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "ControllerServiceChannel"
        const val CHANNEL_NAME = "Controller Service"
        private const val TAG = "ControllerService"
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): ControllerService = this@ControllerService
    }
    
    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        try {
            createNotificationChannel()
            acquireWakeLock()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with startId: $startId")
        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}", e)
        }
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification channel: ${e.message}", e)
        }
    }
    
    private fun createNotification(): Notification {
        return try {
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WiredLess Controller")
                .setContentText("Gamepad running: tap to open app")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(createPendingIntent())
                .setOngoing(true)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification: ${e.message}", e)
            // Fallback notification
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WiredLess Controller")
                .setContentText("Service running")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
        }
    }
    
    private fun createPendingIntent(): android.app.PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return android.app.PendingIntent.getActivity(
            this,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or 
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0
        )
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiredLessController::ControllerService")
            wakeLock?.acquire(10*60*1000L /*10 minutes*/)
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring wake lock: ${e.message}", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock: ${e.message}", e)
        }
        wakeLock = null
    }
    
    fun updateNotificationText(text: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WiredLess Controller")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(createPendingIntent())
                .setOngoing(true)
                .build()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification: ${e.message}", e)
        }
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        try {
            releaseWakeLock()
            stopForeground(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
        }
        super.onDestroy()
    }
}