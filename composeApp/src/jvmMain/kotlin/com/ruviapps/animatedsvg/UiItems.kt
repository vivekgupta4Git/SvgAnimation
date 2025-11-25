package com.ruviapps.animatedsvg

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ruviapps.animatedsvg.SvgParser.Companion.collectPathsFromNode

sealed class UiSvgItem {
    abstract val id: String
    abstract val parentId: String?

    data class UiGroup(
        override val id: String,
        val children: List<UiSvgItem>,
        override val parentId: String? = null
    ) : UiSvgItem()

    data class UiNode(
        override val id: String,
        override val parentId: String? = null,
        val builtPath : List<BuildPath>
    ) : UiSvgItem()

}

fun SvgDocument.toUiTree(): UiSvgItem.UiGroup {

    fun walk(node: SvgNode, parentId: String?): UiSvgItem {
        return when (node) {

            is Group -> UiSvgItem.UiGroup(
                id = node.id ?: "group_${node.hashCode()}",
                parentId = parentId,
                children = node.children.map { child ->
                    walk(child, node.id)
                }
            )

            is PathElement -> asUiNode(node,"Path",parentId)

            is CircleElement -> asUiNode(node, "Circle",parentId)

            is RectElement -> asUiNode(node, "Rect",parentId)

            is PolylineElement -> asUiNode(node, "Polyline",parentId)

            is LineElement -> asUiNode(node, "Line",parentId)

            else -> asUiNode(node, "PathElement",parentId)
        }
    }

    return walk(root, null) as UiSvgItem.UiGroup

}
fun asUiNode(node: SvgNode, idPrefix: String, parentId: String?) =
    UiSvgItem.UiNode(
        id = node.id ?: "${idPrefix}_${node.hashCode()}",
        parentId = parentId,
        builtPath = mutableListOf<BuildPath>().apply {
            collectPathsFromNode(node, this)
        }
    )

@Composable
fun UiTree(
    item: UiSvgItem,
    level: Int,
    stateMap: MutableMap<String, Boolean>,
    animMap: MutableMap<String, Animatable<Float, AnimationVector1D>>
) {
    val isChecked = stateMap[item.id] ?: true

    // If this is a path/node, animate when the checkbox state changes
    if (item is UiSvgItem.UiNode) {
        val anim = animMap[item.id] ?: remember { Animatable(0f) } // defensive
        // LaunchedEffect keyed to the checkbox state so it runs when toggled
        LaunchedEffect(isChecked) {
            if (isChecked) {
                // animate in
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 700, easing = LinearEasing)
                )
            } else {
                // animate out
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 450, easing = LinearEasing)
                )
            }
        }
    }

    ElementUi(
        modifier = Modifier.padding(start = (level * 16).dp),
        name = item.id,
        isSelected = isChecked,
        onSelection = { selected ->
            // update checkbox state
            stateMap[item.id] = selected
            // if group, cascade to children (existing helper)
            if (item is UiSvgItem.UiGroup) markChildren(item, selected, stateMap)
        }
    )

    if (item is UiSvgItem.UiGroup) {
        item.children.forEach { child ->
            UiTree(child, level + 1, stateMap, animMap)
        }
    }
}


fun markChildren(group: UiSvgItem.UiGroup, selected: Boolean, state: MutableMap<String, Boolean>) {
    group.children.forEach { child ->
        state[child.id] = selected
        if (child is UiSvgItem.UiGroup) {
            markChildren(child, selected, state)
        }
    }
}

