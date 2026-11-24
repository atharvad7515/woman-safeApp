package com.womansafe.womanapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.womansafe.womanapp.ui.screens.LoginScreen
import com.womansafe.womanapp.ui.screens.MainScreen
import com.womansafe.womanapp.ui.theme.WomanAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Log.d("MainActivity", "onCreate started")

        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            WomanAppTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isLoggedIn by remember {
                        mutableStateOf(FirebaseAuth.getInstance().currentUser != null)
                    }

                    if (isLoggedIn) {
                        MainScreen(
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = { isDarkTheme = !isDarkTheme },
                            onLogout = { 
                                // Stop the background service before signing out
                                val serviceIntent = Intent(this@MainActivity, com.womansafe.womanapp.sensor.ShakeService::class.java)
                                stopService(serviceIntent)

                                FirebaseAuth.getInstance().signOut()
                                isLoggedIn = false 
                            }
                        )
                    } else {
                        LoginScreen(
                            isDarkTheme = isDarkTheme,
                            onThemeToggle = { isDarkTheme = !isDarkTheme },
                            onLoginSuccess = { isLoggedIn = true }
                        )
                    }
                }
            }
        }
    }
}
