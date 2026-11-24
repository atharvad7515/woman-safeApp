package com.womansafe.womanapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyDirectory(onBack: () -> Unit) {
    val context = LocalContext.current
    val emergencyNumbers = listOf(
        EmergencyService("Police", "100", Color(0xFF2196F3)),
        EmergencyService("Ambulance", "102", Color(0xFF4CAF50)),
        EmergencyService("Fire Brigade", "101", Color(0xFFF44336)),
        EmergencyService("Women Helpline", "1091", Color(0xFFE91E63)),
        EmergencyService("Child Helpline", "1098", Color(0xFFFF9800))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety Directory", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF121212))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(emergencyNumbers.size) { index ->
                val service = emergencyNumbers[index]
                ServiceCard(service, context)
            }
        }
    }
}

data class EmergencyService(val name: String, val number: String, val color: Color)

@Composable
fun ServiceCard(service: EmergencyService, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${service.number}"))
            context.startActivity(intent)
        }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = service.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = service.number,
                    fontSize = 16.sp,
                    color = service.color
                )
            }
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call",
                tint = service.color,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
