package com.womansafe.womanapp.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private var globalMediaPlayer: MediaPlayer? = null
private var currentPlayingId = mutableStateOf<String?>(null)
private var isPaused = mutableStateOf(false)

@Composable
fun HistoryScreen() {
    val auth = FirebaseAuth.getInstance()
    val uid = auth.currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    
    var recordings by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        db.collection("user").document(uid).collection("recordings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    recordings = snapshot.documents.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data
                    }
                }
                isLoading = false
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            globalMediaPlayer?.release()
            globalMediaPlayer = null
            currentPlayingId.value = null
            isPaused.value = false
        }
    }

    // Unified Gradient Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A2F1F), Color.Black)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Evidence Vault",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00C853))
                }
            } else if (recordings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No emergency recordings found.", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(recordings) { recording ->
                        RecordingItem(recording, uid) { recordingId, fileName ->
                            if (currentPlayingId.value == recordingId) {
                                globalMediaPlayer?.stop()
                                currentPlayingId.value = null
                                isPaused.value = false
                            }
                            db.collection("user").document(uid).collection("recordings").document(recordingId).delete()
                            FirebaseStorage.getInstance().reference.child("recordings/$uid/$fileName").delete()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingItem(recording: Map<String, Any>, uid: String, onDelete: (String, String) -> Unit) {
    val timestamp = recording["timestamp"] as? Long ?: 0L
    val url = recording["url"] as? String ?: ""
    val id = recording["id"] as? String ?: ""
    val fileName = recording["fileName"] as? String ?: ""
    val isCurrent = currentPlayingId.value == id
    val date = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
    var progress by remember { mutableStateOf(0f) }
    
    LaunchedEffect(isCurrent, isPaused.value) {
        if (isCurrent && !isPaused.value) {
            while (globalMediaPlayer?.isPlaying == true) {
                progress = (globalMediaPlayer?.currentPosition?.toFloat() ?: 0f) / (globalMediaPlayer?.duration?.toFloat() ?: 1f)
                delay(200)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(16.dp),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00C853)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Emergency Audio", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(date, color = Color.Gray, fontSize = 12.sp)
                }

                IconButton(
                    onClick = {
                        if (isCurrent) {
                            if (isPaused.value) { globalMediaPlayer?.start(); isPaused.value = false }
                            else { globalMediaPlayer?.pause(); isPaused.value = true }
                        } else {
                            globalMediaPlayer?.stop(); globalMediaPlayer?.release()
                            globalMediaPlayer = MediaPlayer().apply {
                                setDataSource(url); prepareAsync()
                                setOnPreparedListener { it.start(); currentPlayingId.value = id; isPaused.value = false }
                                setOnCompletionListener { currentPlayingId.value = null; isPaused.value = false; progress = 0f }
                            }
                        }
                    },
                    modifier = Modifier.background(if (isCurrent) Color(0x2200C853) else Color.Transparent, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isCurrent && !isPaused.value) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isCurrent) Color(0xFF00C853) else Color.White
                    )
                }

                IconButton(onClick = { onDelete(id, fileName) }) {
                    Icon(Icons.Default.Delete, null, tint = Color.DarkGray)
                }
            }
            
            if (isCurrent) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(CircleShape),
                    color = Color(0xFF00C853),
                    trackColor = Color(0xFF333333)
                )
            }
        }
    }
}
