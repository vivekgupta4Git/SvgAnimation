package com.ruviapps.animatedsvg

sealed class UiSvgItem {
    abstract val id: String
    abstract val parentId : String?
    data class UiGroup(
        override val id: String,
        val items: List<UiSvgItem>,
        override val parentId : String? = null
    ) : UiSvgItem()

    data class UiNode(
        override val id: String,
        val type: String,
        override val parentId : String? = null
    ) : UiSvgItem()

}
data class UiFlatItem(
    val item: UiSvgItem,
    val level: Int
)
fun UiSvgItem.forEachRecursively(action: (UiSvgItem) -> Unit) {
    action(this)
    if (this is UiSvgItem.UiGroup) {
        items.forEach { it.forEachRecursively(action) }
    }
}

fun List<UiSvgItem>.forEachRecursively(action: (UiSvgItem) -> Unit) {
    this.forEach { it.forEachRecursively(action) }
}

fun updateParentState(
    parentId: String,
    groupMap: MutableMap<String, Boolean>,
    itemMap: MutableMap<String, Boolean>
) {
    groupMap[parentId] ?: return

    // All child nodes checked?
    val allChecked = itemMap.filterKeys { it.startsWith(parentId) }.values.all { it }
    val noneChecked = itemMap.filterKeys { it.startsWith(parentId) }.values.none { it }

    groupMap[parentId] = when {
        allChecked -> true
        noneChecked -> false
        else -> groupMap[parentId] == true
    }
}

fun flatten(items: List<UiSvgItem>, level: Int = 0): List<UiFlatItem> {
    val result = mutableListOf<UiFlatItem>()
    for (i in items) {
        result += UiFlatItem(i, level)
        if (i is UiSvgItem.UiGroup) {
            result += flatten(i.items, level + 1)
        }
    }
    return result
}

fun SvgDocument.toUiModel(): List<UiSvgItem> {

    fun walk(group: Group): UiSvgItem.UiGroup {
        val items = mutableListOf<UiSvgItem>()

        for (child in group.children) {
            when (child) {

                is Group -> {
                    items += walk(child) // keep hierarchy
                }

                is PathElement -> items += UiSvgItem.UiNode(
                    id = child.id ?: "path",
                    type = "path"
                )

                is RectElement -> items += UiSvgItem.UiNode(
                    id = child.id ?: "rect",
                    type = "rect"
                )

                is CircleElement -> items += UiSvgItem.UiNode(
                    id = child.id ?: "circle",
                    type = "circle"
                )

                is PolygonElement -> items += UiSvgItem.UiNode(
                    id = child.id ?: "polygon",
                    type = "polygon"
                )

                is PolylineElement -> items += UiSvgItem.UiNode(
                    id = child.id ?: "polyline",
                    type = "polyline"
                )

                // add line, ellipse etc if needed
            }
        }

        return UiSvgItem.UiGroup(
            id = group.id ?: "group",
            items = items
        )
    }

    // return ALL top-level groups or nodes inside root
    return walk(root).items
}

