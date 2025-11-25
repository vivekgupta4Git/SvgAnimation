package com.ruviapps.animatedsvg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import com.ruviapps.animatedsvg.SvgRenderer.drawCompletedFills
import com.ruviapps.animatedsvg.SvgRenderer.drawPartialStrokes
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.launch
import java.io.File

private const val SUPPORTED_FILE_MESSAGE = "This tool only supports Svg"

@Composable
fun MainUi() {
    var svgDocument by remember { mutableStateOf<SvgDocument?>(null) }
    // maps - remember once per composition
    val stateMap = remember { mutableStateMapOf<String, Boolean>() } // checkbox states
    val animMap = remember { mutableStateMapOf<String, Animatable<Float, AnimationVector1D>>() } // per-node anim

// uiTree from your parser
    var uiTree by remember { mutableStateOf<UiSvgItem.UiGroup?>(null) }

// whenever uiTree changes register all nodes (fill maps and anims)
    LaunchedEffect(uiTree) {
        if (uiTree == null) return@LaunchedEffect

        val allIds = mutableSetOf<String>()
        fun register(node: UiSvgItem) {
            allIds += node.id
            stateMap.putIfAbsent(node.id, true) // default selected
            if (node is UiSvgItem.UiNode) animMap.putIfAbsent(node.id, Animatable(0f))
            if (node is UiSvgItem.UiGroup) node.children.forEach(::register)
        }
        register(uiTree!!)

        // cleanup removed ids
        stateMap.keys.toList().forEach { if (it !in allIds) stateMap.remove(it) }
        animMap.keys.toList().forEach { if (it !in allIds) animMap.remove(it) }
    }


    val scope = rememberCoroutineScope()
    var columnWeight by rememberSaveable { mutableStateOf(0.2f) }
    var filePath by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(12.dp))
            TextField(
                value = filePath, readOnly = true,
                onValueChange = {
                    filePath = it
                },
                modifier = Modifier.weight(0.8f),
                label = {
                    if (filePath.isEmpty()) {
                        Text(SUPPORTED_FILE_MESSAGE)
                    } else
                        Text("File Path")
                },
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = {
                scope.launch {
                    val imageFile = FileKit.openFilePicker()
                    filePath = imageFile?.absolutePath().orEmpty()
                    svgDocument = SvgParser.getSvgDocument(file = File(filePath))
                    uiTree = svgDocument?.toUiTree()
                }
            }, modifier = Modifier.weight(0.2f)) {
                Text("Open file")
            }
            Spacer(Modifier.width(12.dp))
        }
        Spacer(Modifier.height(12.dp))
        Slider(value = columnWeight, onValueChange = {
            columnWeight = it
        }, valueRange = 0.1f..0.9f)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(Modifier
                .weight(columnWeight)
                .fillMaxHeight()
                .border(1.dp, Color.Black)) {
                LazyColumn {
                    item {
                        if (uiTree != null) {
                            UiTree(uiTree!!, 0, stateMap,animMap)
                        }
                    }
                }
            }
           Column(
                modifier = Modifier.weight((1f - columnWeight).coerceIn(0.1f, 0.9f)).fillMaxHeight()
                    .border(1.dp, Color.Black)
            ) {
               SvgPreviewCanvas(
                   modifier = Modifier.fillMaxSize(),
                   uiTree = uiTree,
                   stateMap = stateMap,
                   animMap = animMap
               )

            }
        }
    }
}
@Composable
fun SvgPreviewCanvas(
    modifier: Modifier = Modifier,
    uiTree: UiSvgItem.UiGroup?,
    stateMap: Map<String, Boolean>,
    animMap: Map<String, Animatable<Float, AnimationVector1D>>
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (uiTree == null) return@Canvas

        // Recursively collect visible BuildPaths and their animated progress
        val visibleNodes = mutableListOf<Pair<List<BuildPath>, Float>>() // (buildPaths, progress)

        fun collect(node: UiSvgItem) {
            when (node) {
                is UiSvgItem.UiGroup -> node.children.forEach(::collect)
                is UiSvgItem.UiNode -> {
                    val checked = stateMap[node.id] ?: true
                    val anim = animMap[node.id]?.value ?: if (checked) 1f else 0f
                    // Only draw if > tiny epsilon or fully visible
                    if (anim > 0f + 1e-3f) {
                        visibleNodes += (node.builtPath to anim)
                    }
                }
            }
        }
        collect(uiTree)

        // draw each node individually using their progress.
        // We will draw strokes (partial) then fills (completed) per node.
        visibleNodes.forEach { (buildPaths, progress) ->
            // For each node, totalLengthNode = sum lengths of its BuildPaths
            val nodeTotalLength = buildPaths.sumOf { it.length.toDouble() }.toFloat()
            val targetLength = nodeTotalLength * progress

            // draw partial strokes for these buildPaths using your helper:
            drawIntoCanvas { canvas ->
                drawPartialStrokes(buildPaths, canvas, targetLength)
                drawCompletedFills(buildPaths, canvas, targetLength)
            }
        }
    }
}


@Composable
fun ElementUi(
    modifier: Modifier = Modifier,
    name: String,
    isSelected: Boolean = true,
    onSelection: (Boolean) -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = onSelection)
        Spacer(Modifier.width(8.dp))
        Text(text = name)
    }
}