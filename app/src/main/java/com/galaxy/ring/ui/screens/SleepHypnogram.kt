package com.galaxy.ring.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.galaxy.ring.data.SleepRecordParser
import com.galaxy.ring.data.SleepSegment
import com.galaxy.ring.data.SleepStage

private fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.DEEP -> Color(0xFF3F51B5)
    SleepStage.LIGHT -> Color(0xFF64B5F6)
    SleepStage.REM -> Color(0xFFAB47BC)
    SleepStage.AWAKE -> Color(0xFFFFB74D)
}

private fun stageRow(stage: SleepStage): Int = when (stage) {
    SleepStage.AWAKE -> 0
    SleepStage.REM -> 1
    SleepStage.LIGHT -> 2
    SleepStage.DEEP -> 3
}

/**
 * Hypnogram built ONLY from real SleepSegment ranges.
 * X-axis is sessionStart..sessionEnd. Tapping the chart (or a list row) shows
 * e.g. "Deep sleep  23:48 – 00:05  (17 min)".
 * Pass SleepSessionEntity.toSegments() (or SleepRecordParser.segmentsFromStages(session.stages)).
 */
@Composable
fun SleepHypnogram(segments: List<SleepSegment>, modifier: Modifier = Modifier) {
    if (segments.isEmpty()) {
        Text(
            text = "No sleep stage detail from the ring for this night.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
        return
    }

    val sessionStart = segments.first().start
    val sessionEnd = segments.last().end
    val totalMs = (sessionEnd - sessionStart).coerceAtLeast(1L)
    var selected by remember(segments) { mutableStateOf<SleepSegment?>(null) }

    val msByStage = SleepStage.values().associateWith { st ->
        segments.filter { it.stage == st }.sumOf { it.end - it.start }
    }
    val asleepMs = totalMs - (msByStage[SleepStage.AWAKE] ?: 0L)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "${SleepRecordParser.formatClock(sessionStart)} – ${SleepRecordParser.formatClock(sessionEnd)}  ·  ${SleepRecordParser.formatDuration(totalMs / 60_000L)}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(segments) {
                    detectTapGestures { offset ->
                        val t = sessionStart + ((offset.x / size.width.toFloat()) * totalMs).toLong()
                        selected = segments.firstOrNull { t >= it.start && t < it.end }
                            ?: segments.lastOrNull { t >= it.start }
                    }
                }
        ) {
            val rows = 4
            val rowH = size.height / rows
            for (r in 0..rows) {
                drawLine(gridColor, Offset(0f, r * rowH), Offset(size.width, r * rowH), strokeWidth = 1f)
            }
            for (seg in segments) {
                val x = ((seg.start - sessionStart).toFloat() / totalMs) * size.width
                val w = ((seg.end - seg.start).toFloat() / totalMs) * size.width
                val y = stageRow(seg.stage) * rowH
                val alpha = if (selected == null || selected == seg) 1f else 0.45f
                drawRect(
                    color = stageColor(seg.stage).copy(alpha = alpha),
                    topLeft = Offset(x, y + rowH * 0.15f),
                    size = Size(w.coerceAtLeast(1.5f), rowH * 0.7f)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(SleepRecordParser.formatClock(sessionStart), style = MaterialTheme.typography.labelSmall)
            if (SleepRecordParser.crossesLocalMidnight(sessionStart, sessionEnd)) {
                Text("Mid-Night", style = MaterialTheme.typography.labelSmall)
            }
            Text(SleepRecordParser.formatClock(sessionEnd), style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = selected?.let { SleepRecordParser.formatSegment(it) } ?: "Tap the chart for stage details",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "In bed ${SleepRecordParser.formatDuration(totalMs / 60_000L)}  ·  Asleep ${SleepRecordParser.formatDuration(asleepMs / 60_000L)}  ·  Efficiency ${(asleepMs * 100L / totalMs)}%",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(6.dp))

        // Stage % bars = sum of segment durations over time in bed.
        for (st in listOf(SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP, SleepStage.AWAKE)) {
            val ms = msByStage[st] ?: 0L
            val frac = (ms.toFloat() / totalMs).coerceIn(0f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SleepRecordParser.stageLabel(st),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(96.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(trackColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .background(stageColor(st))
                    )
                }
                Text(
                    text = "${(frac * 100).toInt()}%  ${SleepRecordParser.formatDuration(ms / 60_000L)}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(88.dp).padding(start = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Stage ranges", style = MaterialTheme.typography.titleSmall)
        for (seg in segments) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selected = seg }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(stageColor(seg.stage))
                )
                Text(
                    text = SleepRecordParser.formatSegment(seg),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
