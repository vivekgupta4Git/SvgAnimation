package com.ruviapps.animatedsvg

sealed class UiSvgItem {
    data class UiGroup(
        val id: String,
        val items: List<UiNode>,
        val parentId : String? = null
    ) : UiSvgItem()

    data class UiNode(
        val id: String,
        val type: String,
        val parentId : String? = null
    ) : UiSvgItem()
}

fun SvgDocument.toUiModel(): List<UiSvgItem.UiGroup> {
    fun walk(group: Group): UiSvgItem.UiGroup {
        val uiNodes = mutableListOf<UiSvgItem.UiNode>()

        for (child in group.children) {
            when (child) {

                is Group -> {
                    // nested groups become full UiGroups
                    // you may flatten or keep hierarchy
                    // here: keep as group-within-group
                    val nested = walk(child)
                    uiNodes += UiSvgItem.UiNode(
                        id = nested.id,
                        type = "group(${nested.items.size})",
                        parentId = group.id
                    )
                }

                is PathElement -> uiNodes += UiSvgItem.UiNode(
                    id = child.id ?: "path(unknown)",
                    type = "path",
                    parentId =  group.id
                )

                is RectElement -> uiNodes += UiSvgItem.UiNode(
                    id = child.id ?: "rect(unknown)",
                    type = "rect",
                    parentId = group.id
                )

                is CircleElement -> uiNodes += UiSvgItem.UiNode(
                    id = child.id ?: "circle(unknown)",
                    type = "circle",
                    parentId = group.id
                )
            }
        }

        return UiSvgItem.UiGroup(
            id = group.id ?: "group(unnamed)",
            items = uiNodes
        )
    }

    return listOf(walk(root))
}
