package com.ruviapps.animatedsvg


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import com.ruviapps.animatedsvg.SvgParser.Companion.buildPaths
import com.ruviapps.animatedsvg.SvgParser.Companion.getTotalLength
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathMeasure


private const val MIN_STROKE_WIDTH_PX = 0.75f
private const val COMPLETION_EPSILON = 1e-3f

object SvgRenderer {
    fun SvgNode.createStrokePaint(
        defaultStokeWidth: Float = MIN_STROKE_WIDTH_PX,
        defaultStrokeColor: Color = Color(0x171717),
    ): androidx.compose.ui.graphics.Paint {
        val actualStrokeColor = strokeColor ?: defaultStrokeColor
        return androidx.compose.ui.graphics.Paint().apply {
            isAntiAlias = true
            style = PaintingStyle.Stroke
            color = actualStrokeColor
            strokeWidth = strokeWidthPxAt1x ?: defaultStokeWidth
            alpha = strokeAlpha
            strokeCap = cap
            strokeJoin = join
        }
    }

    fun SvgNode.createFillPaint(defaultFillColor: Color = Color(0x171717)): androidx.compose.ui.graphics.Paint {
        val actualFillColor = fillColor ?: defaultFillColor
        return androidx.compose.ui.graphics.Paint().apply {
            isAntiAlias = true
            color = actualFillColor
            alpha = actualFillColor.alpha
            this.style = PaintingStyle.Fill
        }
    }


    fun drawCompletedFills(
        buildPaths : List<BuildPath>,
        canvas: Canvas,
        targetLength: Float
    ) {
        var accumulatedLength = 0f
        buildPaths.forEach { built ->
            val pathEnd = accumulatedLength + built.length
            val isCompleted = targetLength >= pathEnd - COMPLETION_EPSILON
            if (isCompleted) {
                val composePath = built.skiaPath.asComposePath()
                val paint = built.svgNode.createFillPaint()
                canvas.drawPath(composePath, paint)
            }
            accumulatedLength += built.length
        }
    }

    fun drawPartialStrokes(
        buildPaths: List<BuildPath>,
        canvas: Canvas,
        targetLength: Float,
    ) {
        var remainingLength = targetLength
        buildPaths.forEach { built ->
            if (remainingLength <= 0f) return@forEach
            //print("\n built path length: ${built.length} with targetLength = $targetLength and remainingLength = $remainingLength")
            val pathDrawLength = remainingLength.coerceAtMost(built.length)
            if (pathDrawLength > 0f) {
                val tracedPath = Path()
                val pathMeasure = PathMeasure().apply {
                    setPath(built.skiaPath, false)
                }
                var lengthLeftOnPath = pathDrawLength
                do {
                    val contourLength = pathMeasure.length
                    if (lengthLeftOnPath <= 0f) break
                    val segmentLength = lengthLeftOnPath.coerceAtMost(contourLength)
                    if (segmentLength > 0f) {
                        pathMeasure.getSegment(
                            0f,
                            segmentLength,
                            tracedPath,
                            true
                        )
                    }
                    lengthLeftOnPath -= segmentLength
                } while (pathMeasure.nextContour())
                val composePath = tracedPath.asComposePath()
                val strokePaint = built.svgNode.createStrokePaint()
                canvas.drawPath( composePath,strokePaint)
            }
            remainingLength -= pathDrawLength
        }
    }

    @Composable
    fun pathTrace(
        modifier: Modifier = Modifier,
        buildPaths : List<BuildPath>,
        totalLength: Float,
        progress: Float,
    ) {
        Canvas(modifier) {
            val clampedProgress = progress.coerceIn(0f, 1f)
            val targetLength = clampedProgress * totalLength

            drawIntoCanvas { canvas ->
                drawPartialStrokes(buildPaths, canvas, targetLength)
                drawCompletedFills(buildPaths, canvas, targetLength)
            }
        }
    }

    @Composable
    fun SvgDocument.pathTraceSvgDocument(
        modifier: Modifier = Modifier,
        speedMs: Int = 3800,
        pauseMs: Int = 1000,
        easing: Easing = LinearEasing,
    ) {
        val buildPaths = remember(this) {
            buildPaths()
        }
        val progress = remember { Animatable(0f) }
        LaunchedEffect(this@pathTraceSvgDocument, speedMs, pauseMs, easing) {
                progress.snapTo(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = speedMs,
                        easing = easing,
                        delayMillis = pauseMs
                    )
                )
        }
        pathTrace(
            totalLength = buildPaths.getTotalLength(),
            modifier = modifier.fillMaxSize().padding(4.dp).border(
                width = 2.dp,color = Color.Cyan
            ),
            progress = progress.value,
            buildPaths = buildPaths
        )
    }

}
