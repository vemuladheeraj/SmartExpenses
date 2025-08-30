package com.dheeraj.smartexpenses

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen(
                onSplashComplete = {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            )
        }
    }
}

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    // Animation states
    var logoVisible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var subtitleVisible by remember { mutableStateOf(false) }
    var madeInIndiaVisible by remember { mutableStateOf(false) }
    var loadingVisible by remember { mutableStateOf(false) }
    
    // Logo animation
    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.5f,
        animationSpec = tween(1000, easing = EaseOutBounce),
        label = "logo_scale"
    )
    
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(1000),
        label = "logo_alpha"
    )

    // Gradient background
    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2196F3), // Blue
            Color(0xFF4CAF50)  // Green
        )
    )

    LaunchedEffect(Unit) {
        // Start animations sequence
        delay(500)
        logoVisible = true
        
        delay(800)
        titleVisible = true
        
        delay(600)
        subtitleVisible = true
        
        delay(600)
        madeInIndiaVisible = true
        
        delay(400)
        loadingVisible = true
        
        // Wait for loading animation
        delay(2500)
        
        // Navigate to main activity
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Card(
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    // Simplified wallet icon
                    Card(
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "₹",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App Title
            if (titleVisible) {
                Text(
                    text = "SmartExpenses",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle
            if (subtitleVisible) {
                Text(
                    text = "Your Personal Expenses App",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Made in India
            if (madeInIndiaVisible) {
                Text(
                    text = "Built in India, for India 🇮🇳",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Loading indicator
            if (loadingVisible) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
