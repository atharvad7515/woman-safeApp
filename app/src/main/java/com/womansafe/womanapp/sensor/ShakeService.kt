package com.womansafe.womanapp.sensor

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.PowerManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.womansafe.womanapp.R
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import kotlin.math.sqrt

class ShakeService : Service(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Threshold: 2.3g (22.5 m/s^2) as requested
    private val SHAKE_THRESHOLD = 22.5f 
    private val SHAKE_WINDOW_MS = 10000 
    private var lastHitTime: Long = 0
    private var lastSOSTime: Long = 0
    private var lastMagnitude: Float = 0f
    private var hitCount: Int = 0
    private var voiceTriggerCount: Int = 0
    @Volatile private var isEmergencyActive = false
    private var activeRecorder: MediaRecorder? = null
    private var isListening = false
    private var voskDetector: VoskVoiceDetector? = null
    private var cachedContacts: List<String> = emptyList()
    private var currentTriggerWord: String = "ok ok ok"
    private var currentUid: String? = null

    override fun onCreate() {
        super.onCreate()
        reloadUserSettings()

        // Acquire WakeLock to keep CPU running when screen is off
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WomanSafe:ShakeWakeLock")
        // Use a 24-hour timeout to ensure it doesn't expire while the app is backgrounded
        wakeLock?.acquire(24 * 60 * 60 * 1000L /*24 hours*/)

        fetchEmergencyContacts()
        
        // Re-enable Snatch Detection
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        
        startForegroundService()
        
        // Start Voice Detection
        startVoiceTriggerListening()
    }

    private fun fetchEmergencyContacts() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            cachedContacts = emptyList()
            return
        }
        
        // Load from local preferences first as a fast backup
        val prefs = getSharedPreferences("woman_safe_prefs", Context.MODE_PRIVATE)
        val localSet = prefs.getStringSet("contacts_$uid", emptySet())
        if (!localSet.isNullOrEmpty()) {
            cachedContacts = localSet.toList()
            Log.d("ShakeService", "Loaded ${cachedContacts.size} contacts from local prefs")
        }

        FirebaseFirestore.getInstance().collection("user").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val remoteContacts = snapshot.get("emergency contact") as? List<String> ?: emptyList()
                    if (remoteContacts.isNotEmpty()) {
                        // Ensure we only use the current user's contacts
                        cachedContacts = remoteContacts
                        Log.d("ShakeService", "Updated ${cachedContacts.size} contacts from Firestore")
                    }
                }
            }
    }

    private fun startVoiceTriggerListening() {
        if (voskDetector != null) {
            voskDetector?.resume()
            return
        }
        
        Log.d("ShakeService", "Initializing Vosk Voice Detector with: $currentTriggerWord")
        voskDetector = VoskVoiceDetector(this, currentTriggerWord) {
            handleEmergency()
        }
    }

    private fun startForegroundService() {
        val channelId = "shake_service_channel"
        val channel = NotificationChannel(
            channelId, "Safety Monitor", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Safety System Active")
            .setContentText("Monitoring for hard shake and voice triggers...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        // Required for Android 14+ (API 34) to maintain background mic/location access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(1, notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // BLOCK all sensor processing if an emergency is already active
        if (isEmergencyActive) return

        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val magnitude = sqrt(x * x + y * y + z * z)
            val currentTime = System.currentTimeMillis()
            
            // Calculate Jerk (Change in magnitude)
            val jerk = Math.abs(magnitude - lastMagnitude)

            // 1. PHONE DROP FILTER (Free-fall detection)
            // Near-zero gravity indicates a fall. Reset everything to avoid trigger on impact.
            if (magnitude < 3.0f) {
                hitCount = 0
                Log.d("ShakeDebug", "📱 Phone Drop/Fall detected. Ignoring impact.")
                lastMagnitude = magnitude
                return
            }

            // 2. RESET HITS if movements are too far apart (more than 1.5s)
            if (currentTime - lastHitTime > 1500) {
                hitCount = 0
            }

            // 3. HARD SHAKE / SNATCH DETECTION
            // High magnitude (force) + High jerk (suddenness)
            // Motorola G82 accelerometer is sensitive, so we use a high threshold (32.0f)
            if (magnitude > 32.0f && jerk > 12.0f) {
                hitCount++
                lastHitTime = currentTime
                Log.d("ShakeDebug", "⚠️ Hard Shake/Snatch! Hit: $hitCount (Mag: $magnitude)")
                
                // Require 3 hard hits to confirm it's not an accident
                if (hitCount >= 3) {
                    Log.d("ShakeService", "🚨 SOS TRIGGERED BY HARD SHAKE/SNATCH")
                    hitCount = 0 
                    handleEmergency()
                }
            }

            lastMagnitude = magnitude
        }
    }

    @Synchronized
    private fun handleEmergency(manualContacts: List<String>? = null) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        
        // --- NEW: BLOCK IF SIGNED OUT ---
        if (uid == null) {
            Log.w("ShakeService", "Emergency trigger ignored: No user signed in")
            // Stop the service if we detect we're logged out
            stopSelf()
            return
        }

        val currentTime = System.currentTimeMillis()
        
        // Manual triggers (manualContacts != null) should ALWAYS be allowed to override 
        // the cooldown, but still respect the active recording state to prevent file corruption.
        val isManual = manualContacts != null
        
        if (isEmergencyActive) {
            Log.d("ShakeService", "Emergency trigger ignored: Recording in progress")
            return
        }

        if (!isManual && (currentTime - lastSOSTime < 10000)) {
            Log.d("ShakeService", "Automatic trigger ignored: Cooldown (10s) in progress")
            return
        }
        
        isEmergencyActive = true
        lastSOSTime = currentTime
        Log.d("ShakeService", "Emergency Triggered! Source: ${if (isManual) "Button" else "Sensor/Voice"}")

        // Temporarily stop voice listening to free up Microphone for MediaRecorder
        voskDetector?.stop()

        val db = FirebaseFirestore.getInstance()

        db.collection("user").document(uid).update("alertstatus", true)

        // 1. Send IMMEDIATE SMS
        sendImmediateSOS(manualContacts)

        // 2. Start recording locally (ensure only one 15s file)
        startRecordingLocally {
            // After recording finishes, resume listening
            startVoiceTriggerListening()
        }
    }

    private fun sendImmediateSOS(manualContacts: List<String>? = null) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Use manual contacts from intent if provided, otherwise use cached ones
        val contacts = manualContacts ?: cachedContacts
        
        if (contacts.isEmpty()) {
            Log.w("ShakeService", "No contacts available to send SOS")
            return
        }

        try {
            // Request high accuracy location for the emergency
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnCompleteListener { task ->
                    val location = if (task.isSuccessful) task.result else null
                    // If getCurrentLocation fails, try lastLocation as fallback
                    if (location == null) {
                        fusedLocationClient.lastLocation.addOnCompleteListener { lastTask ->
                            val lastLocation = if (lastTask.isSuccessful) lastTask.result else null
                            broadcastSOS(contacts, lastLocation)
                        }
                    } else {
                        broadcastSOS(contacts, location)
                    }
                }
        } catch (e: SecurityException) {
            Log.e("ShakeService", "Location permissions missing")
            broadcastSOS(contacts, null)
        }
    }

    private fun broadcastSOS(contacts: List<String>, location: android.location.Location?) {
        val baseMessage = "⚠️ Emergency! I am in danger."
        val locationUrl = if (location != null) {
            "\nMy location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "\nLocation: (Unable to pinpoint, check last known location)"
        }
        
        val finalMessage = baseMessage + locationUrl
        
        contacts.distinct().forEach { contact ->
            sendDirectSMS(contact, finalMessage)
        }
    }

    private fun startRecordingLocally(onComplete: () -> Unit = {}) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: "unknown_user"
        
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        activeRecorder = recorder

        // USER-SPECIFIC LOCAL DIRECTORY
        val recordingsDir = File(filesDir, "recordings/$uid")
        if (!recordingsDir.exists()) recordingsDir.mkdirs()

        val audioFile = File(recordingsDir, "emergency_audio_${System.currentTimeMillis()}.mp4")
        
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            Log.d("ShakeService", "🔴 EMERGENCY RECORDING STARTED (10s)")

            // Stop after 10 seconds and reset emergency state
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    recorder.stop()
                    recorder.release()
                    if (activeRecorder == recorder) activeRecorder = null
                    
                    // Reset Firestore status back to Standby
                    if (uid != null) {
                        FirebaseFirestore.getInstance().collection("user").document(uid).update("alertstatus", false)
                    }

                    isEmergencyActive = false
                    hitCount = 0 // Reset shake hits
                    lastSOSTime = System.currentTimeMillis()
                    
                    // --- NEW: Upload to Firebase ---
                    if (uid != null) {
                        uploadRecordingToFirebase(audioFile, uid)
                    }
                    
                    Log.d("ShakeService", "✅ Recording saved. System READY for next trigger.")
                } catch (e: Exception) {
                    isEmergencyActive = false
                    Log.e("ShakeService", "Recorder stop failed", e)
                } finally {
                    onComplete()
                }
            }, 10000)

        } catch (e: Exception) {
            isEmergencyActive = false
            Log.e("ShakeService", "Audio recording failed", e)
            onComplete()
        }
    }

    private fun uploadRecordingToFirebase(file: File, uid: String) {
        val storageRef = FirebaseStorage.getInstance().reference
            .child("recordings/$uid/${file.name}")
        
        val fileUri = Uri.fromFile(file)
        storageRef.putFile(fileUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { url ->
                    // Save metadata to Firestore
                    val recordingData = mapOf(
                        "url" to url.toString(),
                        "timestamp" to System.currentTimeMillis(),
                        "fileName" to file.name
                    )
                    FirebaseFirestore.getInstance()
                        .collection("user").document(uid)
                        .collection("recordings")
                        .add(recordingData)
                    
                    Log.d("ShakeService", "☁️ Upload success! URL: $url")
                }
            }
            .addOnFailureListener {
                Log.e("ShakeService", "❌ Upload failed: ${it.message}")
            }
    }

    private fun sendDirectSMS(phoneNumber: String, message: String) {
        if (phoneNumber.isBlank()) return
        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            Log.d("ShakeService", "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e("ShakeService", "Failed to send SMS to $phoneNumber", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    private fun reloadUserSettings() {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        currentUid = uid
        
        if (uid != null) {
            val prefs = getSharedPreferences("woman_safe_prefs", Context.MODE_PRIVATE)
            currentTriggerWord = prefs.getString("trigger_$uid", "ok ok ok") ?: "ok ok ok"
            
            // Re-fetch contacts for the new user
            fetchEmergencyContacts()
            
            // Update the voice detector with the user's specific word
            voskDetector?.updateTriggerWord(currentTriggerWord)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "TRIGGER_SOS" -> {
                val contacts = intent.getStringArrayListExtra("contacts")
                handleEmergency(contacts)
            }
            "START_LISTENING" -> {
                startVoiceTriggerListening()
            }
            "UPDATE_TRIGGER" -> {
                val newTrigger = intent.getStringExtra("trigger") ?: "ok ok ok"
                currentTriggerWord = newTrigger
                voskDetector?.updateTriggerWord(newTrigger)
            }
            "USER_SWITCHED" -> {
                reloadUserSettings()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        
        // Release WakeLock
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        try {
            activeRecorder?.stop()
            activeRecorder?.release()
        } catch (e: Exception) {}
        activeRecorder = null
        voskDetector?.stop()
        voskDetector = null
        sensorManager?.unregisterListener(this)
    }
}
