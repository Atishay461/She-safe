package com.aaforge.shesafe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.ui.theme.ObsidianColors

@Composable
fun VoiceCallScreen(amplitude: Float, onEndCall: () -> Unit) {
    val orbScale = 1f + (amplitude * 0.8f)
    Column(
        modifier = Modifier.fillMaxSize().background(ObsidianColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SheSafe Live Voice", color = ObsidianColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Confidential Audio Channel", color = ObsidianColors.TextSecondary, fontSize = 13.sp)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
            Box(modifier = Modifier.size(140.dp).scale(orbScale).clip(CircleShape).background(ObsidianColors.AccentViolet.copy(alpha = 0.25f)))
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(ObsidianColors.AccentViolet).border(2.dp, ObsidianColors.TextPrimary, CircleShape))
        }
        IconButton(
            onClick = onEndCall,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(ObsidianColors.StatusRed)
        ) {
            Text("End", color = ObsidianColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
