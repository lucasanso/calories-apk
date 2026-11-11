package com.calories.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calories.ui.components.Dot
import com.calories.ui.format.Fmt
import com.calories.ui.theme.LocalNutrientColors
import kotlin.math.max

data class BarDatum(val label: String, val value: Double?, val goal: Double? = null)

/**
 * Barras de calorias por dia com a meta tracejada. Dias sem registro ficam sem barra (não zero).
 * O gráfico tem descrição textual para leitores de tela.
 */
@Composable
fun CalorieBarChart(data: List<BarDatum>, modifier: Modifier = Modifier, height: Dp = 160.dp, description: String) {
    val primary = MaterialTheme.colorScheme.primary
    val over = LocalNutrientColors.current.over
    val grid = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = max(data.maxOfOrNull { max(it.value ?: 0.0, it.goal ?: 0.0) } ?: 0.0, 1.0) * 1.1
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val slot = size.width / data.size.coerceAtLeast(1)
            val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            data.forEachIndexed { i, d ->
                val cx = slot * i + slot / 2
                d.value?.let { v ->
                    val h = (v / maxValue * size.height).toFloat()
                    val color = if (d.goal != null && v > d.goal * 1.1) over else primary
                    drawRoundRect(color, Offset(cx - barWidth / 2, size.height - h), Size(barWidth, h), CornerRadius(4.dp.toPx()))
                }
                d.goal?.let { g ->
                    val y = size.height - (g / maxValue * size.height).toFloat()
                    drawLine(goalColor, Offset(cx - slot / 2, y), Offset(cx + slot / 2, y), strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        AxisLabels(data.map { it.label })
    }
}

/** Rótulos do eixo X centrados em cada barra; podem ultrapassar a largura da barra (no mês, só alguns dias têm rótulo). */
@Composable
private fun AxisLabels(labels: List<String>) {
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Box(Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
    }
}

data class MacroDatum(val label: String, val proteinG: Double, val carbohydrateG: Double, val fatG: Double)

/** Distribuição da energia dos macronutrientes por dia (convenção 4/4/9 kcal/g), em 100%. */
@Composable
fun MacroShareChart(data: List<MacroDatum>, modifier: Modifier = Modifier, height: Dp = 120.dp) {
    val c = LocalNutrientColors.current
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val slot = size.width / data.size.coerceAtLeast(1)
            val barWidth = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            data.forEachIndexed { i, d ->
                val x = slot * i + slot / 2 - barWidth / 2
                val p = d.proteinG * 4; val cb = d.carbohydrateG * 4; val f = d.fatG * 9
                val total = p + cb + f
                // Dia sem registro: sem barra (como no gráfico de calorias).
                if (total > 0) {
                    var y = size.height
                    listOf(p to c.protein, cb to c.carbohydrate, f to c.fat).forEach { (v, color) ->
                        val h = (v / total * size.height).toFloat()
                        drawRect(color, Offset(x, y - h), Size(barWidth, h))
                        y -= h
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        AxisLabels(data.map { it.label })
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Proteínas" to c.protein, "Carboidratos" to c.carbohydrate, "Gorduras" to c.fat).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) { Dot(color); Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

/** Linha de peso com média móvel opcional e rótulos de mínimo/máximo. */
@Composable
fun WeightLineChart(values: List<Double>, average: List<Double>?, modifier: Modifier = Modifier, height: Dp = 180.dp, description: String) {
    val line = MaterialTheme.colorScheme.primary
    val avgColor = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    if (values.isEmpty()) return
    val min = (values + average.orEmpty()).min()
    val maxV = (values + average.orEmpty()).max()
    val pad = max((maxV - min) * 0.15, 0.5)
    val lo = min - pad
    val hi = maxV + pad
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.fillMaxWidth()) {
            Text("${Fmt.number(hi, 1)} kg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            fun x(i: Int) = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
            fun y(v: Double) = (size.height * (1 - (v - lo) / (hi - lo))).toFloat()
            listOf(0f, size.height / 2, size.height).forEach { gy -> drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f) }
            average?.takeIf { it.size > 1 }?.let { avg ->
                val p = Path().apply { avg.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
                drawPath(p, avgColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            }
            if (values.size > 1) {
                val p = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
                drawPath(p, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            values.forEachIndexed { i, v -> drawCircle(line, 3.5.dp.toPx(), Offset(x(i), y(v))) }
        }
        Text("${Fmt.number(lo, 1)} kg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (average != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { Dot(line); Spacer(Modifier.width(4.dp)); Text("Peso", style = MaterialTheme.typography.labelSmall) }
                Row(verticalAlignment = Alignment.CenterVertically) { Dot(avgColor); Spacer(Modifier.width(4.dp)); Text("Média de 7 registros", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

