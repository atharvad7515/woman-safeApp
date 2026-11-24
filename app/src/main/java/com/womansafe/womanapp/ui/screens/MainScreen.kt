@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.womansafe.womanapp.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.womansafe.womanapp.sensor.ShakeService

import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.womansafe.womanapp.R

@Composable
fun MainScreen(isDarkTheme: Boolean, onThemeToggle: () -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid ?: "dev_user"
    val prefs = remember { context.getSharedPreferences("woman_safe_prefs", Context.MODE_PRIVATE) }
    
    var currentScreen by remember { mutableStateOf("home") }
    var alertStatus by remember { mutableStateOf(false) }

    // --- PERMISSION HANDLER ---
    val permissionsToRequest = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            context.startService(Intent(context, ShakeService::class.java).apply { action = "START_LISTENING" })
        } else {
            Toast.makeText(context, "Permissions required for voice and SOS!", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val needsRequest = permissionsToRequest.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needsRequest) {
            permissionLauncher.launch(permissionsToRequest)
        } else {
            // Service might already be running, but ensure it's listening
            context.startService(Intent(context, ShakeService::class.java).apply { action = "START_LISTENING" })
        }
    }
    // ---------------------------
    
    var firestoreContacts by remember(uid) { mutableStateOf<List<String>>(emptyList()) }
    val localContacts = remember(uid) { 
        mutableStateListOf<String>().apply { 
            addAll(prefs.getStringSet("contacts_$uid", emptySet()) ?: emptySet())
        } 
    }
    val emergencyContacts = (firestoreContacts + localContacts).distinct()

    LaunchedEffect(uid) {
        db.collection("user").document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                alertStatus = snapshot.getBoolean("alertstatus") ?: false
                @Suppress("UNCHECKED_CAST")
                firestoreContacts = snapshot.get("emergency contact") as? List<String> ?: emptyList()
            }
        }
    }

    val backgroundGradient = if (isDarkTheme) {
        Brush.verticalGradient(listOf(Color(0xFF0A2F1F), Color.Black))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE8F5E9), Color.White))
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GUARDIAN", fontWeight = FontWeight.ExtraBold, color = if (isDarkTheme) Color.White else Color.Black) },
                actions = {
                    IconButton(onClick = onThemeToggle) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.Refresh else Icons.Default.Settings,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkTheme) Color.White else Color.Black
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = if (isDarkTheme) Color.White else Color.Black)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D0D0D)) {
                val navItems = listOf(
                    Triple("home", Icons.Default.Home, "Home"),
                    Triple("directory", Icons.AutoMirrored.Filled.List, "Guide"),
                    Triple("history", Icons.Default.Refresh, "Vault"),
                    Triple("profile", Icons.Default.Person, "Profile")
                )
                navItems.forEach { (route, icon, label) ->
                    NavigationBarItem(
                        icon = { Icon(icon, null) },
                        label = { Text(label, fontSize = 10.sp) },
                        selected = currentScreen == route,
                        onClick = { currentScreen = route },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF00C853),
                            selectedTextColor = Color(0xFF00C853),
                            unselectedIconColor = Color.Gray,
                            indicatorColor = Color(0x1100C853)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A2F1F), Color.Black)))
            .padding(padding)
        ) {
            when (currentScreen) {
                "home" -> HomeScreenUI(
                    uid = uid,
                    alertStatus = alertStatus,
                    emergencyContacts = emergencyContacts,
                    onAddContact = { num ->
                        localContacts.add(num)
                        prefs.edit { putStringSet("contacts_$uid", localContacts.toSet()) }
                        db.collection("user").document(uid).set(mapOf("emergency contact" to FieldValue.arrayUnion(num)), SetOptions.merge())
                    },
                    onDeleteContact = { num ->
                        localContacts.remove(num)
                        prefs.edit { putStringSet("contacts_$uid", localContacts.toSet()) }
                        db.collection("user").document(uid).update("emergency contact", FieldValue.arrayRemove(num))
                    },
                    onToggleAlert = {
                        val intent = Intent(context, ShakeService::class.java).apply {
                            action = if (alertStatus) "STOP_SOS" else "TRIGGER_SOS"
                            putStringArrayListExtra("contacts", ArrayList(emergencyContacts))
                        }
                        context.startService(intent)
                    }
                )
                "directory" -> DirectoryScreenUI()
                "history" -> HistoryScreen()
                "profile" -> ProfileScreen(onBack = { currentScreen = "home" }, onLogout = onLogout)
            }
        }
    }
}

@Composable
fun HomeScreenUI(
    uid: String,
    alertStatus: Boolean,
    emergencyContacts: List<String>,
    onAddContact: (String) -> Unit,
    onDeleteContact: (String) -> Unit,
    onToggleAlert: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("woman_safe_prefs", Context.MODE_PRIVATE) }
    val db = FirebaseFirestore.getInstance()
    var newContact by remember { mutableStateOf("") }
    var customTrigger by remember { mutableStateOf(prefs.getString("trigger_$uid", "ok ok ok") ?: "ok ok ok") }

    fun getContactName(phoneNumber: String, context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return phoneNumber
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return phoneNumber
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Status Badge
        Surface(color = if (alertStatus) Color(0x33FF1744) else Color(0x3300C853), shape = RoundedCornerShape(20.dp)) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (alertStatus) Color.Red else Color.Green))
                Spacer(Modifier.width(8.dp))
                Text(if (alertStatus) "EMERGENCY ACTIVE" else "SHIELD ACTIVE", color = if (alertStatus) Color.Red else Color.Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        // SOS Button
        Box(
            modifier = Modifier.size(200.dp).border(2.dp, if (alertStatus) Color.Red else Color(0xFF00C853), CircleShape).padding(12.dp)
                .clip(CircleShape).background(if (alertStatus) Color.Red else Color(0xFF1B5E20)).padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onToggleAlert, modifier = Modifier.fillMaxSize()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (alertStatus) Icons.Default.Warning else Icons.Default.Notifications, null, modifier = Modifier.size(45.dp), tint = Color.White)
                    Text(if (alertStatus) "STOP" else "SOS", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Restored Trigger Keyword Card
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Secret Voice Keyword", color = Color(0xFF00C853), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = customTrigger,
                        onValueChange = { customTrigger = it.lowercase() },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = Color.White, focusedTextColor = Color.White, unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00C853))
                    )
                    Button(
                        onClick = {
                            prefs.edit().putString("trigger_$uid", customTrigger).apply()
                            db.collection("user").document(uid).update("voice_trigger", customTrigger)
                            context.startService(Intent(context, ShakeService::class.java).apply { action = "UPDATE_TRIGGER"; putExtra("trigger", customTrigger) })
                            Toast.makeText(context, "Keyword Updated", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                        modifier = Modifier.padding(start = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Set", color = Color.Black) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Contacts Card
        Card(modifier = Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Emergency Contacts", color = Color.White, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newContact, onValueChange = { newContact = it }, modifier = Modifier.weight(1f), placeholder = { Text("Number", color = Color.Gray) }, colors = OutlinedTextFieldDefaults.colors(unfocusedTextColor = Color.White, focusedTextColor = Color.White, unfocusedBorderColor = Color.DarkGray, focusedBorderColor = Color(0xFF00C853)))
                    IconButton(onClick = { if(newContact.isNotBlank()){ onAddContact(newContact); newContact="" } }, modifier = Modifier.padding(start = 4.dp).background(Color(0xFF00C853), RoundedCornerShape(8.dp))) { Icon(Icons.Default.Add, null, tint = Color.Black) }
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(emergencyContacts) { contact ->
                        val displayName = remember(contact) { getContactName(contact, context) }
                        ListItem(
                            headlineContent = { Text(displayName, color = Color.White) },
                            supportingContent = if (displayName != contact) { { Text(contact, color = Color.Gray, fontSize = 12.sp) } } else null,
                            trailingContent = { IconButton(onClick = { onDeleteContact(contact) }) { Icon(Icons.Default.Delete, null, tint = Color.Gray) } },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DirectoryScreenUI() {
    val context = LocalContext.current
    val numbers = listOf(
        "Police" to "100",
        "Ambulance" to "102",
        "Fire Brigade" to "101",
        "Women Helpline" to "1091",
        "Child Helpline" to "1098"
    )

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Safety Guide", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("Quick-dial official emergency services", color = Color.Gray, fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyColumn {
            items(numbers) { (label, number) ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(number, color = Color(0xFF00C853), fontWeight = FontWeight.Medium)
                        }
                        IconButton(
                            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) },
                            modifier = Modifier.background(Color(0xFF00C853), CircleShape)
                        ) {
                            Icon(Icons.Default.Call, null, tint = Color.Black)
                        }
                    }
                }
            }
        }
    }
}


