package com.aaforge.shesafe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.parser.ParsedAgentTurn
import com.aaforge.shesafe.searchtool.ScrapedWebSource
import com.aaforge.shesafe.ui.renderers.PerplexityFaviconAnim
import com.aaforge.shesafe.ui.renderers.XmlMessageRenderer
import com.aaforge.shesafe.ui.theme.ObsidianColors

data class UiChatMessage(
    val isUser: Boolean,
    val text: String = "",
    val agentTurn: ParsedAgentTurn? = null,
    val sources: List<ScrapedWebSource> = emptyList()
)

@Composable
fun ChatScreen(
    messages: List<UiChatMessage>,
    isStreaming: Boolean,
    onSendMessage: (String) -> Unit,
    onStartVoiceCall: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().background(ObsidianColors.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(ObsidianColors.SurfaceCard).border(1.dp, ObsidianColors.SurfaceBorder).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SheSafe@School", color = ObsidianColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Button(
                onClick = onStartVoiceCall,
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianColors.PillBackground)
            ) {
                Text("Voice Call", color = ObsidianColors.AccentViolet, fontSize = 12.sp)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages) { msg ->
                if (msg.isUser) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianColors.SurfaceCard)
                                .border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Text(msg.text, color = ObsidianColors.TextPrimary, fontSize = 14.5.sp)
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (msg.sources.isNotEmpty()) {
                            PerplexityFaviconAnim(sources = msg.sources, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianColors.SurfaceCard)
                                .border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            msg.agentTurn?.let { XmlMessageRenderer(turn = it) }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(ObsidianColors.SurfaceCard).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Describe privately...", color = ObsidianColors.TextSecondary) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ObsidianColors.Background,
                    unfocusedContainerColor = ObsidianColors.Background,
                    focusedTextColor = ObsidianColors.TextPrimary,
                    unfocusedTextColor = ObsidianColors.TextPrimary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (input.isNotBlank() && !isStreaming) {
                        onSendMessage(input.trim())
                        input = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianColors.AccentViolet)
            ) {
                Text("Send", color = ObsidianColors.Background, fontWeight = FontWeight.Bold)
            }
        }
    }
}
