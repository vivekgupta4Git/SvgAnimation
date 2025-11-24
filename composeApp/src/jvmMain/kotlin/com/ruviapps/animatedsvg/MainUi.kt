package com.ruviapps.animatedsvg

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    var svgDocument by remember { mutableStateOf<SvgDocument?>(null) }

    val groupMap = remember { mutableStateMapOf<String, Boolean>() }
    val itemMap = remember { mutableStateMapOf<String, Boolean>() }

// flatten groups + nodes recursively
    val flatList by remember {
        derivedStateOf {
            flatten(svgGroups)
        }
    }


    LaunchedEffect(svgGroups) {

        val allGroupIds = mutableSetOf<String>()
        val allItemIds = mutableSetOf<String>()

        // Traverse all groups and items
        svgGroups.forEachRecursively { item ->
            when (item) {
                is UiSvgItem.UiGroup -> {
                    allGroupIds.add(item.id)
                    groupMap.putIfAbsent(item.id, true) // default = checked
                }

                is UiSvgItem.UiNode -> {
                    allItemIds.add(item.id)
                    itemMap.putIfAbsent(item.id, true) // default = checked
                }
            }
        }

        // Remove IDs no longer present
        groupMap.keys.toList().forEach { id ->
            if (id !in allGroupIds) groupMap.remove(id)
        }
        itemMap.keys.toList().forEach { id ->
            if (id !in allItemIds) itemMap.remove(id)
        }
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
                    // get raw ui model (can be UiGroup or UiNode at top-level)
                    val rawUi = svgDocument?.toUiModel().orEmpty()

                    // Build a list of top-level UiGroup:
                    // - keep UiGroup as-is
                    // - wrap top-level UiNode into a synthetic group "Root" so UI always has groups
                    val topGroups = mutableListOf<UiSvgItem.UiGroup>()
                    rawUi.forEach { item ->
                        when (item) {
                            is UiSvgItem.UiGroup -> topGroups.add(item)
                            is UiSvgItem.UiNode -> {
                                // wrap singleton nodes into a synthetic group so they appear in the tree
                                topGroups.add(
                                    UiSvgItem.UiGroup(
                                        id = "root_${item.id}",
                                        items = listOf(item)
                                    )
                                )
                            }
                        }
                    }

                    // replace state list (do this on main thread - we're already in coroutine on main)
                    svgGroups.clear()
                    svgGroups.addAll(topGroups)

                    // PREFILL selection maps immediately (default = true)
                    // ensures UI draws in selected state on first composition
                    topGroups.forEachRecursively { uiItem ->
                        when (uiItem) {
                            is UiSvgItem.UiGroup -> groupMap[uiItem.id] = true
                            is UiSvgItem.UiNode -> itemMap[uiItem.id] = true
                        }
                    }

                    // cleanup removed keys (in case previous file had other ids)
                    val allGroupIds = mutableSetOf<String>()
                    val allItemIds = mutableSetOf<String>()
                    topGroups.forEachRecursively { uiItem ->
                        when (uiItem) {
                            is UiSvgItem.UiGroup -> allGroupIds.add(uiItem.id)
                            is UiSvgItem.UiNode -> allItemIds.add(uiItem.id)
                        }
                    }
                    groupMap.keys.toList().forEach { if (it !in allGroupIds) groupMap.remove(it) }
                    itemMap.keys.toList().forEach { if (it !in allItemIds) itemMap.remove(it) }


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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                ) {
                    items(flatList, key = { it.item.id }) { flat ->

                        val item = flat.item
                        val level = flat.level

                        when (item) {
                            is UiSvgItem.UiGroup -> {
                                val checked = groupMap[item.id] == true

                                ElementUi(
                                    modifier = Modifier.padding(start = (level * 20).dp),
                                    name = item.id,
                                    isSelected = checked,
                                    onSelection = { newChecked ->
                                        groupMap[item.id] = newChecked
                                        // Cascade to all children
                                        item.items.forEachRecursively { child ->
                                            itemMap[child.id] = newChecked
                                            if (child is UiSvgItem.UiGroup)
                                                groupMap[child.id] = newChecked
                                        }
                                    }
                                )
                            }

                            is UiSvgItem.UiNode -> {
                                val checked = itemMap[item.id] == true

                                ElementUi(
                                    modifier = Modifier.padding(start = (level * 20).dp),
                                    name = item.id,
                                    isSelected = checked,
                                    onSelection = { newChecked ->
                                        if (groupMap[item.id] == true)
                                            itemMap[item.id] = newChecked
                                        // Update parent group state
                                        item.parentId?.let { parent ->
                                            updateParentState(parent, groupMap, itemMap)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

            }
            Column(
                modifier = Modifier.weight((1f - columnWeight).coerceIn(0.1f, 0.9f)).fillMaxHeight()
                    .border(1.dp, Color.Black)
            ) {
                svgDocument?.pathTraceSvgDocument()
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