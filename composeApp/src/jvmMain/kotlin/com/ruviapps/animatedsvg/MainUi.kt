package com.ruviapps.animatedsvg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FilterChip
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ruviapps.animatedsvg.SvgParser.Companion.buildPaths
import com.ruviapps.animatedsvg.SvgParser.Companion.getTotalLength
import com.ruviapps.animatedsvg.SvgParser.Companion.printSvgDocument
import com.ruviapps.animatedsvg.SvgRenderer.pathTraceSvgDocument
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.launch
import java.io.File

private const val SUPPORTED_FILE_MESSAGE = "This tool only supports Svg"

@Composable
fun MainUi() {
    val svgGroups = rememberSaveable { mutableStateListOf<UiSvgItem.UiGroup>() }
    val svgNodes = remember { mutableStateListOf<UiSvgItem.UiNode>() }
    val groupMap = remember { mutableStateMapOf<String, Boolean>() }
    val itemMap = remember { mutableStateMapOf<String, Boolean>() }
    var startShow by rememberSaveable { mutableStateOf(false) }
    var svgDocument by remember { mutableStateOf<SvgDocument?>(null) }
    LaunchedEffect(svgDocument) {
        if (svgDocument != null) {
            startShow = true
        }
    }
    LaunchedEffect(svgGroups.size) {
        svgGroups.forEach { g ->
            if (!groupMap.containsKey(g.id)) {
                groupMap[g.id] = true
            }
            g.items.forEach { item ->
                if (!itemMap.containsKey(item.id)) {
                    itemMap[item.id] = true
                }
            }
        }
        svgNodes.clear()
        val newNodes = svgGroups.map { it.items }.flatten()
        svgNodes.addAll(newNodes)
        // remove keys for groups/items that no longer exist
        val groupIds = svgGroups.map { it.id }.toSet()
        val itemIds = svgGroups.flatMap { it.items.map { it.id } }.toSet()
        groupMap.keys.toList().forEach { if (it !in groupIds) groupMap.remove(it) }
        itemMap.keys.toList().forEach { if (it !in itemIds) itemMap.remove(it) }
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
                    //SvgParser.printSvgDocument(filePath)
                    svgDocument = SvgParser.getSvgDocument(file = File(filePath))
                    svgGroups.clear()
                    svgGroups.addAll(svgDocument?.toUiModel() ?: emptyList())

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
        /*
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            Slider(value = columnWeight, onValueChange = {
                columnWeight = it
            }, valueRange = 0.1f..0.9f)
            repeat(15) {
                FilterUi(title = "Filter $it")
            }
        }*/
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(Modifier.weight(columnWeight).fillMaxHeight().border(1.dp, Color.Black)) {

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    svgGroups.forEach { group ->
                        // group header
                        item(key = "group-${group.id}") {
                            // read the state directly from the map
                            val isGroupChecked by remember { derivedStateOf { groupMap[group.id] == true } }

                            ElementUi(
                                name = group.id,
                                isSelected = isGroupChecked,
                                onSelection = { checked ->
                                    // set group state
                                    groupMap[group.id] = checked
                                    // cascade to children if you want them to follow group toggle
                                    group.items.forEach { child -> itemMap[child.id] = checked }
                                }
                            )
                        }
                    }

                    // group children
                    items(
                        items = svgNodes,
                        key = { it.id }
                    )
                    { node ->
                        // stable read
                        val isItemChecked by remember { derivedStateOf { itemMap[node.id] == true } }
                        val isGroupOpen by remember { derivedStateOf { groupMap[node.parentId] == true } }

                        ElementUi(
                            modifier = Modifier.padding(start = 20.dp),
                            name = node.id,
                            // visually only enabled when group is open; actual checked state comes from itemMap
                            isSelected = isItemChecked && isGroupOpen,
                            onSelection = { newChecked ->
                                if (isGroupOpen)
                                    itemMap[node.id] = newChecked
                                val childIds = svgGroups
                                    .find { it.id == node.id }
                                    ?.items
                                    ?.map { it.id }
                                    .orEmpty()

                                val allChecked = childIds.all { id -> itemMap[id] == true }
                                val noneChecked = childIds.none { id -> itemMap[id] == true }
                                // update group accordingly
                                if (node.parentId != null) {
                                    groupMap[node.parentId] = when {
                                        allChecked -> true
                                        noneChecked -> false
                                        else -> groupMap[node.parentId] == true
                                    }
                                }
                            }
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.weight((1f - columnWeight).coerceIn(0.1f, 0.9f)).fillMaxHeight()
                    .border(1.dp, Color.Black)
            ) {
                if(startShow){
                    svgDocument?.pathTraceSvgDocument()
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FilterUi(title: String, selected: Boolean = false, onSelection: (Boolean) -> Unit = {}) {
    FilterChip(
        modifier = Modifier.padding(8.dp),
        selected = selected,
        onClick = { onSelection(!selected) },
    ) {
        Text(title)
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