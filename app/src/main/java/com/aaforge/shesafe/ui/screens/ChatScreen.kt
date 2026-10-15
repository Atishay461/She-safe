package com.aaforge.shesafe.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.R
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
    onStopStreaming: () -> Unit,
    onSelectSession: (StoredChatSession) -> Unit,
    onNewSession: () -> Unit,
    onStartVoiceCall: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    fun dismissPromptFocus() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        isInputFocused = false
    }

    BackHandler(enabled = isInputFocused) {
        dismissPromptFocus()
    }

    var wasKeyboardOpen by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) {
            wasKeyboardOpen = true
        } else if (wasKeyboardOpen) {
            wasKeyboardOpen = false
            dismissPromptFocus()
        }
    }

    val showCenterBranding = messages.isEmpty() && !isInputFocused && input.isEmpty()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = ObsidianColors.DeepObsidianCanvas) {
                Column(modifier = Modifier.fillMaxHeight().width(280.dp).background(ObsidianColors.DeepObsidianCanvas).padding(18.dp)) {
                    Text("MELLA ARCHIVE", color = ObsidianColors.BrandBronze, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(ObsidianColors.SurfaceElevated).border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(8.dp))
                            .clickable { dismissPromptFocus(); onNewSession(); scope.launch { drawerState.close() } }.padding(vertical = 12.dp, horizontal = 14.dp)
                    ) {
                        Text("+  New Confidential Session", color = ObsidianColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(savedSessions) { session ->
                            val isSelected = session.sessionId == activeSessionId
                            Box(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) ObsidianColors.SurfaceElevated else Color.Transparent)
                                    .border(1.dp, if (isSelected) ObsidianColors.SurfaceBorder else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { dismissPromptFocus(); onSelectSession(session); scope.launch { drawerState.close() } }.padding(11.dp)
                            ) {
                                Text(session.chatName, color = if (isSelected) ObsidianColors.TextPrimary else ObsidianColors.TextSecondary, fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().background(ObsidianColors.Background)) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp, start = 20.dp, end = 20.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { dismissPromptFocus() }) }
            ) {
                Text("≡", color = ObsidianColors.BrandBronze, fontSize = 22.sp, modifier = Modifier.align(Alignment.CenterStart).clickable { dismissPromptFocus(); scope.launch { drawerState.open() } })
                Text("mella", color = ObsidianColors.BrandBronze, fontSize = 17.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, letterSpacing = 3.sp, modifier = Modifier.align(Alignment.Center))
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(6.dp)).background(ObsidianColors.SurfaceCard)
                        .border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(6.dp)).clickable { dismissPromptFocus(); onStartVoiceCall() }.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("LIVE AUDIO", color = ObsidianColors.BrandCopper, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onTap = { dismissPromptFocus() }) }
            ) {
                this@Column.AnimatedVisibility(
                    visible = showCenterBranding,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp).offset(y = 10.dp)) {
                        Image(painter = painterResource(id = R.drawable.logo2), contentDescription = "Mella Logo", modifier = Modifier.size(120.dp), contentScale = ContentScale.Fit)
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "Share whatever you want!",
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                brush = Brush.linearGradient(listOf(ObsidianColors.BrandCopper, ObsidianColors.AccentViolet, ObsidianColors.StatusGreen)),
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Encrypted edge channel · Zero server retention", color = ObsidianColors.TextSecondary, fontSize = 11.5.sp, letterSpacing = 0.6.sp)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    items(messages) { msg ->
                        if (msg.isUser) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                Box(modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp)).background(ObsidianColors.SurfaceElevated).border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(18.dp)).padding(horizontal = 15.dp, vertical = 10.dp)) {
                                    Text(msg.text, color = ObsidianColors.TextPrimary, fontSize = 14.5.sp, lineHeight = 21.sp)
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                if (msg.sources.isNotEmpty()) PerplexityFaviconAnim(sources = msg.sources, modifier = Modifier.padding(bottom = 8.dp))
                                msg.agentTurn?.let { XmlMessageRenderer(turn = it) }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(32.dp)).background(ObsidianColors.SurfaceCard)
                    .border(1.dp, if (isInputFocused) ObsidianColors.BrandBronze else ObsidianColors.SurfaceBorder, RoundedCornerShape(32.dp))
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (input.isEmpty()) Text("Share with no hesitation!", color = ObsidianColors.TextSecondary, fontSize = 14.sp)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(color = ObsidianColors.TextPrimary, fontSize = 14.5.sp),
                        cursorBrush = SolidColor(ObsidianColors.BrandCopper),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { isInputFocused = it.isFocused }
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                UpArrowSendButton(
                    canSend = input.isNotBlank(),
                    isStreaming = isStreaming,
                    onSendClick = {
                        onSendMessage(input.trim())
                        input = ""
                        dismissPromptFocus()
                    },
                    onStopClick = onStopStreaming
                )
            }
        }
    }
}
