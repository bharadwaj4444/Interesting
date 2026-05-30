package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun PerformanceChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF00FFCC),
    title: String = "REAL-TIME LOG",
    unit: String = "ms"
) {
    val history = data.takeLast(30)
    val maxVal = (history.maxOrNull() ?: 100f).coerceAtLeast(10f)
    val minVal = (history.minOrNull() ?: 0f).coerceAtMost(maxVal - 5)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = "CURR: ${history.lastOrNull()?.roundToInt() ?: 0}$unit",
                style = MaterialTheme.typography.labelSmall,
                color = lineColor,
                fontSize = 10.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFEF7FF))
                .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(bottom = 4.dp, top = 8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Draw Grid Lines
                val gridLines = 4
                for (i in 0..gridLines) {
                    val y = height * (i.toFloat() / gridLines)
                    drawLine(
                        color = Color(0xFFEADDFF),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        pathEffect = null,
                        strokeWidth = 1f
                    )
                }

                if (history.size >= 2) {
                    val path = Path()
                    val dx = width / (history.size - 1)
                    val valueRange = maxVal - minVal

                    history.forEachIndexed { index, value ->
                        // Calculate normalized Y coordinate
                        val normY = (value - minVal) / valueRange
                        val y = height - (normY * height)
                        val x = index * dx

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    // Stroke the path with neon lineColor
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Draw subtle grid point lines or gradient under the curve if desired
                }
            }
        }
    }
}
