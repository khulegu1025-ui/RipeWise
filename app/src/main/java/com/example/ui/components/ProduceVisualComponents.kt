package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.AppLanguage
import com.example.model.RipenessStage
import com.example.model.SampleProduceCatalog
import com.example.ui.theme.BerryInk
import com.example.ui.theme.CrispLime
import com.example.ui.theme.DeepSage
import com.example.ui.theme.GoldenMango
import com.example.ui.theme.LemonCream
import com.example.ui.theme.LimeInk
import com.example.ui.theme.MangoInk
import com.example.ui.theme.MintDew
import com.example.ui.theme.PeachBlush
import com.example.ui.theme.SageWash
import com.example.ui.theme.TerracottaBerry
import java.io.File

@Composable
fun ProduceArtworkImage(
    localImagePath: String?,
    sampleKey: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val sampleDrawable = SampleProduceCatalog.drawableForKey(sampleKey)
    val localFile = localImagePath?.let { File(it) }?.takeIf { it.exists() }

    when {
        localFile != null -> {
            AsyncImage(
                model = localFile,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
        sampleDrawable != null -> {
            Image(
                painter = painterResource(id = sampleDrawable),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        }
        else -> {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Eco,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

data class RipenessVisualPalette(
    val container: Color,
    val content: Color,
    val accent: Color
)

fun ripenessPalette(stage: RipenessStage): RipenessVisualPalette {
    return when (stage) {
        RipenessStage.UNDERRIPE -> RipenessVisualPalette(
            container = MintDew,
            content = LimeInk,
            accent = CrispLime
        )
        RipenessStage.PERFECTLY_RIPE -> RipenessVisualPalette(
            container = SageWash,
            content = DeepSage,
            accent = DeepSage
        )
        RipenessStage.OVERRIPE -> RipenessVisualPalette(
            container = LemonCream,
            content = MangoInk,
            accent = GoldenMango
        )
        RipenessStage.DAMAGED -> RipenessVisualPalette(
            container = PeachBlush,
            content = BerryInk,
            accent = TerracottaBerry
        )
    }
}

@Composable
fun RipenessBadge(
    stage: RipenessStage,
    language: AppLanguage = AppLanguage.ENGLISH,
    modifier: Modifier = Modifier
) {
    val palette = ripenessPalette(stage)
    Surface(
        color = palette.container,
        contentColor = palette.content,
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
            )
            Text(
                text = stage.localizedLabel(language),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun RipenessArcGauge(
    score: Int,
    stage: RipenessStage,
    language: AppLanguage = AppLanguage.ENGLISH,
    size: Dp = 104.dp,
    modifier: Modifier = Modifier
) {
    val palette = ripenessPalette(stage)
    val animatedSweep by animateFloatAsState(
        targetValue = (score.coerceIn(0, 100) / 100f) * 240f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "ripeness_sweep"
    )
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 10.dp.toPx()
            val arcSize = Size(this.size.width, this.size.height)
            drawArc(
                color = trackColor,
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = Offset.Zero,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = palette.accent,
                startAngle = 150f,
                sweepAngle = animatedSweep,
                useCenter = false,
                topLeft = Offset.Zero,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score%",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (language == AppLanguage.MONGOLIAN) "БОЛОВСРОЛТ" else "RIPENESS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BotanicalScanningOverlay(
    label: String,
    completedCount: Int,
    totalCount: Int,
    previewBitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
    val lineFraction by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141E18)),
        contentAlignment = Alignment.Center
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.44f))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(250.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = pulseAlpha),
                    shape = RoundedCornerShape(28.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val y = this.size.height * lineFraction
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF9BE6B9),
                            Color.White,
                            Color(0xFF9BE6B9),
                            Color.Transparent
                        )
                    ),
                    start = Offset(16.dp.toPx(), y),
                    end = Offset(this.size.width - 16.dp.toPx(), y),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Surface(
            color = Color(0xFF19241D).copy(alpha = 0.92f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF8FD1AC),
                        strokeWidth = 2.2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                }
                if (totalCount > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0.08f, 1f) },
                        color = Color(0xFF8FD1AC),
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(5.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}
