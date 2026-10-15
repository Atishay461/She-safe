package com.aaforge.shesafe.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.data.StoredChatSession
import com.aaforge.shesafe.parser.ParsedAgentTurn
import com.aaforge.shesafe.searchtool.ScrapedWebSource
import com.aaforge.shesafe.ui.renderers.PerplexityFaviconAnim
import com.aaforge.shesafe.ui.renderers.UpArrowSendButton
import com.aaforge.shesafe.ui.renderers.XmlMessageRenderer
import com.aaforge.shesafe.ui.theme.ObsidianColors
import kotlinx.coroutines.launch

data class UiChatMessage(
    val isUser: Boolean,
    val text: String = "",
    val rawXml: String = "",
    val agentTurn: ParsedAgentTurn? = null,
    val sources: List<ScrapedWebSource> = emptyList()
)

@Composable
fun ChatScreen(
    messages: List<UiChatMessage>,
    savedSessions: List<StoredChatSession>,
    activeSessionId: String,
    isStreaming: Boolean,
    onSendMessage: (String) -> Unit,
    onSelectSession: (StoredChatSession) -> Unit,
    onNewSession: () -> Unit,
    onStartVoiceCall: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = ObsidianColors.SurfaceCard) {
                Column(modifier = Modifier.fillMaxHeight().width(270.dp).padding(16.dp)) {
                    Text("Safe Sessions", color = ObsidianColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(ObsidianColors.PillBackground).border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(10.dp))
                            .clickable { onNewSession(); scope.launch { drawerState.close() } }.padding(12.dp)
                    ) {
                        Text("+ New Private Chat", color = ObsidianColors.AccentViolet, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(savedSessions) { session ->
                            val isSelected = session.sessionId == activeSessionId
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ObsidianColors.PillBackground else Color.Transparent)
                                    .clickable { onSelectSession(session); scope.launch { drawerState.close() } }.padding(10.dp)
                            ) {
                                Text(session.chatName, color = ObsidianColors.TextPrimary, fontSize = 13.5.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(ObsidianColors.Background)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)) {
                    Text("☰", color = ObsidianColors.BrandBrownish, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterStart).clickable { scope.launch { drawerState.open() } })
                    Text("she safe", color = ObsidianColors.BrandBrownish, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
                    Text("Voice", color = ObsidianColors.BrandBrownish, fontSize = 12.5.sp, modifier = Modifier.align(Alignment.CenterEnd).clickable { onStartVoiceCall() })
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    this@Column.AnimatedVisibility(
                        visible = messages.isEmpty() && !isInputFocused && input.isEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(
                            text = "Share whatever you want!",
                            style = TextStyle(
                                brush = Brush.linearGradient(listOf(ObsidianColors.AccentViolet, ObsidianColors.StatusGreen, ObsidianColors.StatusAmber)),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(messages) { msg ->
                            if (msg.isUser) {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                    Box(modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(ObsidianColors.SurfaceCard).border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Text(msg.text, color = ObsidianColors.TextPrimary, fontSize = 14.5.sp)
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (msg.sources.isNotEmpty()) PerplexityFaviconAnim(sources = msg.sources, modifier = Modifier.padding(bottom = 6.dp))
                                    Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(ObsidianColors.SurfaceCard).border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(16.dp)).padding(14.dp)) {
                                        msg.agentTurn?.let { XmlMessageRenderer(turn = it) }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(32.dp)).background(ObsidianColors.SurfaceCard)
                        .border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(32.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (input.isEmpty()) Text("Share with no hesitation!", color = ObsidianColors.TextSecondary, fontSize = 14.5.sp)
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = TextStyle(color = ObsidianColors.TextPrimary, fontSize = 14.5.sp),
                            cursorBrush = SolidColor(ObsidianColors.PureWhite),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { isInputFocused = it.isFocused }
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    UpArrowSendButton(enabled = input.isNotBlank() && !isStreaming, onClick = { onSendMessage(input.trim()); input = "" })
                }
            }
        }
    }
}
