package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

@Composable
fun PatternCanvas(
    modifier: Modifier = Modifier,
    onPatternComplete: (List<Int>) -> Unit
) {
    var selectedDots by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentTouchPoint by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val dotPositions = remember(canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) {
            emptyList()
        } else {
            val grid = mutableListOf<Offset>()
            val colSpacing = canvasSize.width / 4f
            val rowSpacing = canvasSize.height / 4f
            for (row in 1..3) {
                for (col in 1..3) {
                    grid.add(Offset(col * colSpacing, row * rowSpacing))
                }
            }
            grid
        }
    }
    
    val dotRadius = 24f
    val hitRadius = 80f
    
    val patternColor = MaterialTheme.colorScheme.primary
    val strokeColor = MaterialTheme.colorScheme.secondary
    val lineStroke = 14f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .onSizeChanged { size ->
                canvasSize = size
            }
            .pointerInput(dotPositions) {
                if (dotPositions.isEmpty()) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        selectedDots = emptyList()
                        currentTouchPoint = offset
                        
                        dotPositions.forEachIndexed { index, dot ->
                            if ((offset - dot).getDistance() < hitRadius) {
                                selectedDots = listOf(index)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newPoint = change.position
                        currentTouchPoint = newPoint
                        
                        dotPositions.forEachIndexed { index, dot ->
                            if ((newPoint - dot).getDistance() < hitRadius) {
                                if (!selectedDots.contains(index)) {
                                    selectedDots = selectedDots + index
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (selectedDots.size >= 3) {
                            onPatternComplete(selectedDots)
                        }
                        selectedDots = emptyList()
                        currentTouchPoint = null
                    },
                    onDragCancel = {
                        selectedDots = emptyList()
                        currentTouchPoint = null
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw connecting lines between selected dots
            if (selectedDots.isNotEmpty() && dotPositions.isNotEmpty()) {
                for (i in 0 until selectedDots.size - 1) {
                    val p1 = dotPositions[selectedDots[i]]
                    val p2 = dotPositions[selectedDots[i + 1]]
                    drawLine(
                        color = strokeColor,
                        start = p1,
                        end = p2,
                        strokeWidth = lineStroke,
                        cap = StrokeCap.Round
                    )
                }
                
                // Draw active visual feedback line to the current touch point
                currentTouchPoint?.let { touch ->
                    val lastDot = dotPositions[selectedDots.last()]
                    drawLine(
                        color = strokeColor.copy(alpha = 0.6f),
                        start = lastDot,
                        end = touch,
                        strokeWidth = lineStroke,
                        cap = StrokeCap.Round
                    )
                }
            }
            
            // Draw grid dots
            dotPositions.forEachIndexed { index, position ->
                val isSelected = selectedDots.contains(index)
                drawCircle(
                    color = if (isSelected) patternColor else patternColor.copy(alpha = 0.25f),
                    radius = if (isSelected) dotRadius * 1.5f else dotRadius,
                    center = position
                )
                if (isSelected) {
                    drawCircle(
                        color = patternColor.copy(alpha = 0.15f),
                        radius = dotRadius * 3f,
                        center = position
                    )
                }
            }
        }
    }
}
