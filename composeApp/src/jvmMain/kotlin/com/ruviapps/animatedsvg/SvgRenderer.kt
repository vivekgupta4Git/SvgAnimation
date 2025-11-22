package com.ruviapps.animatedsvg


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.withSaveLayer
import com.ruviapps.animatedsvg.SvgParser.Companion.buildPaths
import com.ruviapps.animatedsvg.SvgParser.Companion.getTotalLength
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathMeasure
import javax.swing.Spring.scale
import kotlin.math.min

/*
private const val MIN_STROKE_WIDTH_PX = 0.75f
private const val COMPLETION_EPSILON = 1e-3f

object SvgRenderer {
    fun SvgDocument.computeBaseMatrix(drawScope: DrawScope): Matrix {
        val scaleX = drawScope.size.width / viewBox.width
        val scaleY = drawScope.size.height / viewBox.height
        val scale = min(scaleX, scaleY)
        val translateX = (drawScope.size.width - viewBox.width * scale) / 2
        val translateY = (drawScope.size.height - viewBox.height * scale) / 2
        return Matrix().apply {
            scale(scale, scale)
            translate(translateX, translateY)
        }
    }
    fun SvgDocument.computeSkiaMatrix(drawScope: DrawScope): Matrix33 {
        val scaleX = drawScope.size.width / viewBox.width
        val scaleY = drawScope.size.height / viewBox.height
        val scale = min(scaleX, scaleY)

        val translateX = (drawScope.size.width - viewBox.width * scale) / 2f
        val translateY = (drawScope.size.height - viewBox.height * scale) / 2f

        // Skia 3x3 matrix: row-major order
        return Matrix33(
            scale, 0f, translateX,
            0f, scale, translateY,
            0f, 0f, 1f
        )
    }


    /**
     * Converts a Skia Path to a Compose Path and applies transformation matrix.
     */
    fun Path.toComposePath(document: SvgDocument, scope: DrawScope): androidx.compose.ui.graphics.Path {
        val baseMatrix = document.computeBaseMatrix(scope)
        return this.asComposePath().apply {
            transform(baseMatrix)
        }
    }

    fun SvgNode.createStrokePaint(
        defaultStokeWidth: Float = MIN_STROKE_WIDTH_PX,
        defaultStrokeColor: Color = Color(0x171717),
    ): Paint {
        val actualStrokeColor = strokeColor?.toArgb() ?: defaultStrokeColor.toArgb()
        return Paint().apply {
            isAntiAlias = true
            mode = PaintMode.STROKE
            color = actualStrokeColor
            strokeWidth = strokeWidthPxAt1x ?: defaultStokeWidth
            alpha = strokeAlpha.toInt()
            strokeCap = when(cap){
                StrokeCap.Butt -> PaintStrokeCap.BUTT
                StrokeCap.Round -> PaintStrokeCap.ROUND
                StrokeCap.Square -> PaintStrokeCap.SQUARE
                else -> {
                    PaintStrokeCap.ROUND
                }
            }
            strokeJoin = when(join){
                StrokeJoin.Round -> PaintStrokeJoin.ROUND
                StrokeJoin.Bevel -> PaintStrokeJoin.BEVEL
                else -> PaintStrokeJoin.MITER
            }
        }
    }

    fun SvgNode.createFillPaint(defaultFillColor: Color = Color(0x171717)): Paint {
        val actualFillColor = fillColor ?: defaultFillColor
        return Paint().apply {
            isAntiAlias = true
            mode = PaintMode.FILL
            color = actualFillColor.toArgb()
            alpha = fillAlpha.toInt()
        }
    }


    fun SvgDocument.drawCompletedFills(
        buildPaths : List<BuildPath>,
        drawScope: DrawScope,
        canvas: Canvas,
        targetLength: Float
    ) {
        var accumulatedLength = 0f
        buildPaths.forEach { built ->
            val pathEnd = accumulatedLength + built.length
            val isCompleted = targetLength >= pathEnd - COMPLETION_EPSILON
            if (isCompleted) {
                //val filPath = built.skiaPath.toComposePath(this,drawScope)
                val paint = built.svgNode.createFillPaint()
                canvas.nativeCanvas.drawPath(built.skiaPath.transform(computeSkiaMatrix(drawScope)),paint)
                //canvas.drawPath(filPath, paint)
            }
            accumulatedLength += built.length
        }
    }

    fun SvgDocument.drawPartialStrokes(
        buildPaths : List<BuildPath>,
        drawScope: DrawScope,
        canvas: Canvas,
        targetLength: Float,
    ) {
        var remainingLength = targetLength
        buildPaths.forEach { built ->
            if (remainingLength <= 0f) return@forEach
            val pathDrawLength = remainingLength.coerceAtLeast(built.length)
            if (pathDrawLength > 0f) {
                val tracedPath = Path()
                val pathMeasure = PathMeasure().apply {
                    setPath(tracedPath, false)
                }
                var lengthLeftOnPath = pathMeasure.length
                do {
                    val contourLength = pathMeasure.length
                    if (lengthLeftOnPath > 0f) break
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
                //val composePath = tracedPath.toComposePath(this, drawScope)
                val strokePaint = built.svgNode.createStrokePaint()
                canvas.nativeCanvas.drawPath(tracedPath.transform(computeSkiaMatrix(drawScope)),strokePaint)
            }
            remainingLength -= pathDrawLength
        }
    }

    @Composable
    fun SvgDocument.pathTrace(
        modifier: Modifier = Modifier,
        buildPaths : List<BuildPath>,
        totalLength: Float,
        progress: Float,
    ) {
        Canvas(modifier) {
            val clampedProgress = progress.coerceIn(0f, 1f)
            val targetLength = clampedProgress * totalLength

            drawIntoCanvas { canvas ->
                drawCompletedFills(buildPaths,this, canvas, targetLength)
                drawPartialStrokes(buildPaths,this, canvas, targetLength)
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
            modifier = modifier.fillMaxSize(),
            progress = progress.value,
            buildPaths = buildPaths
        )
    }

}

 */

private const val MIN_STROKE_WIDTH_PX = 0.75f
private const val COMPLETION_EPSILON = 1e-3f

object SvgRenderer {

    /** Computes Skia 3x3 transform matrix for scaling + centering the SVG */
    fun SvgDocument.computeSkiaMatrix(drawScope: DrawScope): Matrix33 {
        val scaleX = drawScope.size.width / viewBox.width
        val scaleY = drawScope.size.height / viewBox.height
        val scale = min(scaleX, scaleY)
        val translateX = (drawScope.size.width - viewBox.width * scale) / 2f
        val translateY = (drawScope.size.height - viewBox.height * scale) / 2f

        return Matrix33(
            scale, 0f, translateX,
            0f, scale, translateY,
            0f, 0f, 1f
        )
    }

    /** Stroke paint from SvgNode */
    fun SvgNode.createStrokePaint(defaultWidth: Float = MIN_STROKE_WIDTH_PX, defaultColor: Color = Color(0x171717)): Paint {
        val colorInt = strokeColor?.toArgb() ?: defaultColor.toArgb()
        return Paint().apply {
            isAntiAlias = true
            mode = PaintMode.STROKE
            color = colorInt
            strokeWidth =  strokeWidthPxAt1x ?: defaultWidth
            alpha = (strokeAlpha * 255).toInt().coerceIn(0, 255)
            strokeCap = when (cap) {
                StrokeCap.Butt -> PaintStrokeCap.BUTT
                StrokeCap.Round -> PaintStrokeCap.ROUND
                StrokeCap.Square -> PaintStrokeCap.SQUARE
                else -> PaintStrokeCap.ROUND
            }
            strokeJoin = when (join) {
                StrokeJoin.Round -> PaintStrokeJoin.ROUND
                StrokeJoin.Bevel -> PaintStrokeJoin.BEVEL
                StrokeJoin.Miter -> PaintStrokeJoin.MITER
                else -> PaintStrokeJoin.MITER
            }
            blendMode = BlendMode.SRC_OVER
        }
    }

    /** Fill paint from SvgNode */
    fun SvgNode.createFillPaint(defaultColor: Color = Color(0x171717)): Paint {
        val colorInt = fillColor?.toArgb() ?: defaultColor.toArgb()
        return Paint().apply {
            isAntiAlias = true
            mode = PaintMode.FILL
            color = colorInt
            alpha = (fillAlpha * 255).toInt().coerceIn(0, 255)
            //blendMode = BlendMode.SRC_OVER
        }
    }

    fun SvgDocument.drawCompletedFills(buildPaths: List<BuildPath>, drawScope: DrawScope, canvas: Canvas, targetLength: Float) {
        var accumulatedLength = 0f
        val matrix = computeSkiaMatrix(drawScope)
        buildPaths.forEach { built ->
            val pathEnd = accumulatedLength + built.length
            if (targetLength >= pathEnd - COMPLETION_EPSILON) {
                val fillPaint = built.svgNode.createFillPaint()
                val transformedPath = built.skiaPath.transform(matrix)
                canvas.nativeCanvas.drawPath(transformedPath, fillPaint)
            }
            accumulatedLength += built.length
        }
    }

    fun SvgDocument.computeBaseMatrix(drawScope: DrawScope): Matrix {
        val scaleX = drawScope.size.width / viewBox.width
        val scaleY = drawScope.size.height / viewBox.height
        val scale = min(scaleX, scaleY)
        val translateX = (drawScope.size.width - viewBox.width * scale) / 2
        val translateY = (drawScope.size.height - viewBox.height * scale) / 2
        return Matrix().apply {
            scale(scale, scale)
            translate(translateX, translateY)
        }
    }
    fun Path.toComposePath(document: SvgDocument, scope: DrawScope): androidx.compose.ui.graphics.Path {
        val baseMatrix = document.computeBaseMatrix(scope)
        return this.asComposePath().apply {
            transform(baseMatrix)
        }
    }
    fun SvgNode.createFillPaintCompose(defaultFillColor: Color = Color(0x171717)): androidx.compose.ui.graphics.Paint {
        val actualFillColor = fillColor ?: defaultFillColor
        return androidx.compose.ui.graphics.Paint().apply {
            isAntiAlias = true
            style = PaintingStyle.Fill
            color = actualFillColor
            alpha = fillAlpha
        }
    }

 /*   fun SvgDocument.drawCompletedFills(
        buildPaths : List<BuildPath>,
        drawScope: DrawScope,
        canvas: Canvas,
        targetLength: Float
    ) {

        var accumulatedLength = 0f
        buildPaths.forEach { built ->
            val pathEnd = accumulatedLength + built.length
            val isCompleted = targetLength >= pathEnd - COMPLETION_EPSILON
            if (isCompleted) {
                val filPath = built.skiaPath.toComposePath(this,drawScope)
                val paint = built.svgNode.createFillPaintCompose()
               // canvas.nativeCanvas.drawPath(built.skiaPath.transform(computeSkiaMatrix(drawScope)),paint)
                canvas.drawPath(filPath, paint)

            }
            accumulatedLength += built.length
        }
    }*/

    fun SvgDocument.drawPartialStrokes(
        buildPaths : List<BuildPath>,
        drawScope: DrawScope,
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
                //val composePath = tracedPath.toComposePath(this, drawScope)
                val strokePaint = built.svgNode.createStrokePaint()
                canvas.nativeCanvas.drawPath(tracedPath.transform(computeSkiaMatrix(drawScope)),strokePaint)
            }
            remainingLength -= pathDrawLength
        }
    }

    /** Main Compose canvas function for progressive path animation */
    @Composable
    fun SvgDocument.pathTrace(modifier: Modifier = Modifier, buildPaths: List<BuildPath>, totalLength: Float, progress: Float) {
        Canvas(modifier) {
            val clampedProgress = progress.coerceIn(0f, 1f)
            val targetLength = clampedProgress * totalLength
            drawIntoCanvas { canvas ->
                //canvas.nativeCanvas.saveLayer(null,null)
                 drawPartialStrokes(buildPaths, this, canvas, targetLength)
                drawCompletedFills(buildPaths, this, canvas, targetLength)
                //canvas.nativeCanvas.restore()
            }
        }
    }

    /** Animated Compose call for path trace */
    @Composable
    fun SvgDocument.pathTraceSvgDocument(modifier: Modifier = Modifier, speedMs: Int = 10000, pauseMs: Int = 3000, easing: Easing = LinearEasing) {
        val buildPaths = remember(this) { buildPaths() }
        val totalLength = buildPaths.getTotalLength()
        val progress = remember { Animatable(0f) }

        LaunchedEffect(this, speedMs, pauseMs, easing) {
            while (true) {
                progress.snapTo(0f)
                progress.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = speedMs, easing = easing))
                kotlinx.coroutines.delay(pauseMs.toLong())
            }
        }

        pathTrace(modifier = modifier.fillMaxSize(), buildPaths = buildPaths, totalLength = totalLength, progress = progress.value)
    }
}
