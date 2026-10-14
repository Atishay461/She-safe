package com.aaforge.shesafe.ui.renderers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaforge.shesafe.searchtool.GoogleFaviconFetcher
import com.aaforge.shesafe.searchtool.ScrapedWebSource
import com.aaforge.shesafe.ui.theme.ObsidianColors

@Composable
fun PerplexityFaviconAnim(
    sources: List<ScrapedWebSource>,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty()) return

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ObsidianColors.PillBackground)
            .border(1.dp, ObsidianColors.SurfaceBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                sources.take(4).forEachIndexed { index, source ->
                    val cachedBmp = GoogleFaviconFetcher.getCached(source.faviconUrl)
                    AnimatedVisibility(
                        visible = true,
                        enter = scaleIn(tween(durationMillis = 300, delayMillis = index * 80)) +
                                fadeIn(tween(durationMillis = 300, delayMillis = index * 80))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(ObsidianColors.SurfaceCard)
                                .border(1.5.dp, ObsidianColors.Background, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (cachedBmp != null) {
                                Image(
                                    bitmap = cachedBmp,
                                    contentDescription = source.domain,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${sources.size} verified sources reviewed",
                color = ObsidianColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
