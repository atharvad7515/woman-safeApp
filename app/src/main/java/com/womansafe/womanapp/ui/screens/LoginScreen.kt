package com.womansafe.womanapp.ui.screens

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.womansafe.womanapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(isDarkTheme: Boolean, onThemeToggle: () -> Unit, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    val logoBitmap = remember {
        try {
            val inputStream = context.assets.open("logoreal.png")
            BitmapFactory.decodeStream(inputStream).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    // Premium Background Gradient
    val backgroundGradient = if (isDarkTheme) {
        Brush.verticalGradient(colors = listOf(Color(0xFF0A2F1F), Color(0xFF121212), Color.Black))
    } else {
        Brush.verticalGradient(colors = listOf(Color(0xFFE8F5E9), Color(0xFFF1F8E9), Color.White))
    }

    val textColor = if (isDarkTheme) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        // Theme Toggle Button
        IconButton(
            onClick = onThemeToggle,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Toggle Theme",
                tint = if (isDarkTheme) Color.White else Color.DarkGray
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- LOGO SECTION ---
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isDarkTheme) Color(0x1100C853) else Color(0x11004D40))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap,
                        contentDescription = "Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "Logo Fallback",
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Unspecified
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- APP NAME & MOTTO ---
            Text(
                text = "GUARDIAN SAFE",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                letterSpacing = 2.sp
            )
            
            Text(
                text = "Your 24/7 Personal Safety Companion",
                fontSize = 14.sp,
                color = if (isDarkTheme) Color.Gray else Color.DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(60.dp))

            // --- SOCIAL LOGIN BUTTON ---
            Button(
                onClick = {
                    scope.launch { signInWithGoogle(context, auth, db, onLoginSuccess) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Google-like Icon stand-in (or use a real resource if you have one)
                    Text(
                        "G ", 
                        fontWeight = FontWeight.Bold, 
                        color = Color(0xFF4285F4),
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Sign in with Google",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- GUEST BUTTON (GLASSMORPHISM STYLE) ---
            OutlinedButton(
                onClick = {
                    scope.launch { signInAnonymously(context, auth, db, onLoginSuccess) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(1.dp, Color.Gray, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(
                    text = "Continue as Guest",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                text = "By continuing, you agree to our Safety Terms",
                fontSize = 11.sp,
                color = Color.DarkGray
            )
        }
    }
}

// Keep existing logic for signInAnonymously, initializeUserInFirestore, and signInWithGoogle...
private suspend fun signInAnonymously(
    context: Context,
    auth: FirebaseAuth,
    db: FirebaseFirestore,
    onLoginSuccess: () -> Unit
) {
    try {
        val authResult = auth.signInAnonymously().await()
        val user = authResult.user
        if (user != null) {
            withContext(Dispatchers.Main) {
                onLoginSuccess()
            }
            initializeUserInFirestore(user.uid, "Guest User", "guest@anonymous.com", db)
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Guest Login Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private suspend fun initializeUserInFirestore(
    uid: String,
    name: String,
    email: String,
    db: FirebaseFirestore
) {
    try {
        val userDoc = db.collection("user").document(uid)
        val document = userDoc.get().await()
        
        if (!document.exists()) {
            val userData = hashMapOf(
                "uid" to uid,
                "email" to email,
                "name" to name,
                "alertstatus" to false,
                "emergency contact" to listOf<String>(),
                "lastlocation" to GeoPoint(0.0, 0.0)
            )
            userDoc.set(userData).await()
        }
    } catch (e: Exception) {
        Log.e("Firestore", "Background init failed: ${e.message}")
    }
}

private suspend fun signInWithGoogle(
    context: Context,
    auth: FirebaseAuth,
    db: FirebaseFirestore,
    onLoginSuccess: () -> Unit
) {
    val credentialManager = CredentialManager.create(context)
    val serverClientId = "499749590373-a7nj097ivmggn65lnp2663pakkfmjgj1.apps.googleusercontent.com"

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(serverClientId)
        .setAutoSelectEnabled(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    try {
        credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        val result = credentialManager.getCredential(context, request)
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
        val googleIdToken = googleIdTokenCredential.idToken
        
        if (googleIdToken.isNotEmpty()) {
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user
            
            if (user != null) {
                withContext(Dispatchers.Main) {
                    onLoginSuccess()
                }
                initializeUserInFirestore(user.uid, user.displayName ?: "User", user.email ?: "", db)
            }
        }
    } catch (e: Exception) {
        Log.e("LoginScreen", "Google Sign-in failed", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Login Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
