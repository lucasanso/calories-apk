package com.calories.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calories.ui.format.Fmt
import com.calories.ui.theme.LocalNutrientColors
import com.calories.ui.theme.TabularNumbers

/** Anel de calorias: consumo em relação à meta, com o restante no centro. */
@Composable
fun CalorieRing(consumed: Double, goal: Double?, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val fraction = goal?.takeIf { it > 0 }?.let { (consumed / it).toFloat() } ?: 0f
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(450), label = "ring")
    val over = goal != null && consumed > goal
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val color = if (over) LocalNutrientColors.current.over else MaterialTheme.colorScheme.primary
    val description = if (goal == null) "Consumido ${Fmt.kcal(consumed)} kcal, sem meta definida"
    else "Consumido ${Fmt.kcal(consumed)} de ${Fmt.kcal(goal)} kcal"
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (goal == null) {
                Text(Fmt.kcal(consumed), style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers))
                Text("kcal consumidas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val remaining = goal - consumed
                Text(Fmt.kcal(kotlin.math.abs(remaining)), style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers), color = if (over) color else MaterialTheme.colorScheme.onSurface)
                Text(if (over) "kcal acima" else "kcal restantes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Barra de progresso de um nutriente; acima de 100% muda para a cor de aviso e mostra o percentual real. */
@Composable
fun MacroBar(label: String, consumedText: String, consumed: Double, goal: Double?, goalText: String?, color: Color, modifier: Modifier = Modifier) {
    val fraction = goal?.takeIf { it > 0 }?.let { consumed / it }
    val animated by animateFloatAsState((fraction ?: 0.0).toFloat().coerceIn(0f, 1f), tween(450), label = "bar")
    val over = fraction != null && fraction > 1.0
    val barColor = if (over) LocalNutrientColors.current.over else color
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).let { it }) { Canvas(Modifier.size(8.dp)) { drawCircle(color) } }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (goalText != null) "$consumedText / $goalText" else consumedText,
                style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers),
                color = if (over) barColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (fraction != null) {
                Spacer(Modifier.width(8.dp))
                Text(Fmt.percent(fraction), style = MaterialTheme.typography.labelMedium.merge(TabularNumbers), color = if (over) barColor else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                drawRect(barColor.copy(alpha = 0.18f))
                drawRect(barColor, size = Size(size.width * animated, size.height))
            }
        }
    }
}

@Composable
fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium.merge(TabularNumbers))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.padding(top = 1.dp).size(8.dp)) { drawCircle(color) }
}
