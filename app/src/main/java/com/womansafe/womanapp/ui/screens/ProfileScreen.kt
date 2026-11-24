package com.womansafe.womanapp.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val user = auth.currentUser
    val uid = user?.uid ?: ""
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("woman_safe_prefs", Context.MODE_PRIVATE) }

    var userName by remember { mutableStateOf(user?.displayName ?: "Guest User") }
    var userEmail by remember { mutableStateOf(user?.email ?: "guest@anonymous.com") }
    var firestoreContacts by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(uid) {
        db.collection("user").document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                userName = snapshot.getString("name") ?: user?.displayName ?: "User"
                userEmail = snapshot.getString("email") ?: user?.email ?: ""
                @Suppress("UNCHECKED_CAST")
                firestoreContacts = snapshot.get("emergency contact") as? List<String> ?: emptyList()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A2F1F), Color.Black)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Profile Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 32.dp)
            )

            // Profile Image with Glow
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .border(2.dp, Color(0xFF00C853), CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                if (user?.photoUrl != null) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(60.dp), tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(userName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(userEmail, fontSize = 14.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(32.dp))

            // Logout Button
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FF1744), contentColor = Color.Red)
            ) {
                Text("Sign Out Account", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Contact List Card
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Synced Contacts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyColumn {
                        items(firestoreContacts) { contact ->
                            ListItem(
                                headlineContent = { Text(contact, color = Color.White) },
                                trailingContent = {
                                    IconButton(onClick = {
                                        db.collection("user").document(uid).update("emergency contact", FieldValue.arrayRemove(contact))
                                    }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.DarkGray)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}
