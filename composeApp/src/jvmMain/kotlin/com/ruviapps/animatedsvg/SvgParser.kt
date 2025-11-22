package com.ruviapps.animatedsvg


import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.PathMeasure
import java.io.File
import java.io.FileReader
import javax.xml.namespace.QName
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.events.StartElement


sealed class SvgNode(open val id: String?) {
    var parent: Group? = null
    var fillColor: Color? = null
    var strokeColor: Color? = null
    var strokeWidthPxAt1x: Float? = null
    var fillAlpha: Float = 1f
    var strokeAlpha: Float = 1f
    var cap: StrokeCap = StrokeCap.Round
    var join: StrokeJoin = StrokeJoin.Round
    var fillTypeEvenOdd: Boolean = false
}

data class SvgDocument(
    val width: String?,
    val height: String?,
    val viewBox: ViewBox,
    val root: Group
)

data class Group(
    override val id: String? = null,
    val children: MutableList<SvgNode> = mutableListOf(),
    val transforms: MutableList<String> = mutableListOf()
) : SvgNode(id)

data class PathElement(
    override val id: String? = null,
    val d: String,
    val attrs: Map<String, String>,
) : SvgNode(id)

data class RectElement(
    override val id: String? = null,
    val attrs: Map<String, String>,
) : SvgNode(id)

data class CircleElement(
    override val id: String? = null,
    val attrs: Map<String, String>
) : SvgNode(id)

data class ViewBox(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float
)

data class BuildPath(
    val skiaPath : Path,
    val svgNode: SvgNode,
    val length : Float,
)

// You can add more element types (Line, Ellipse, Polygon, Text...)

// --- Parser ---
class SvgParser(private val namespaceAware: Boolean = true) {

    companion object {
        private const val DEFAULT_VIEW_BOX_WIDTH = 24f
        private const val DEFAULT_VIEW_BOX_HEIGHT = 24f
        fun getSvgDocument(file: File): SvgDocument {
            val parser = SvgParser()
            val doc = parser.parseSvg(file)
            return doc
        }

        fun SvgDocument.buildPaths(): List<BuildPath> {
            val pathList = mutableListOf<BuildPath>()
            for (c in root.children) {
                when (c) {
                    is PathElement -> {
                        val path = Path.makeFromSVGString(c.d).apply {
                            fillMode = if (c.fillTypeEvenOdd) PathFillMode.EVEN_ODD else PathFillMode.WINDING
                        }

                        val pathMeasure = PathMeasure()
                        pathMeasure.setPath(path, false)
                        var totalLength = 0f
                        do {
                            totalLength += pathMeasure.length
                        } while (pathMeasure.nextContour())
                        pathList.add(BuildPath(
                            skiaPath = path,
                            svgNode = c,
                            length = totalLength
                        ))
                    }

                    else -> {
                        continue
                    }
                }
            }
            print(" Created Path List size = ${pathList.size}")
            return pathList
        }

        fun List<BuildPath>.getTotalLength(): Float {
            return  map { it.length }.sum()
        }

        fun SvgDocument.printSvgDocument() {
            println("SVG width=$width} height=${height} viewBox=${viewBox}")
            printGroup(root)
        }

        fun printGroup(g: Group, depth: Int = 0) {
            val pad = "  ".repeat(depth)
            println("${pad}Group id=${g.id} transforms=${g.transforms}")
            for (c in g.children) {
                when (c) {
                    is Group -> printGroup(c, depth + 1)
                    is PathElement -> println("$pad  Path id=${c.id} d='${c.d.take(60)}' attrs=${c.attrs.keys} fillColor=${c.fillColor}")
                    is RectElement -> println("$pad  Rect id=${c.id} attrs=${c.attrs}")
                    is CircleElement -> println("$pad  Circle id=${c.id} attrs=${c.attrs}")
                }
            }
        }
    }

    fun parseSvg(file: File): SvgDocument {
        val factory = XMLInputFactory.newInstance()
        // make namespace-aware so element names include namespace if present
        try {
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, namespaceAware)
        } catch (_: Exception) { /* ignore if not supported */
        }

        val reader = factory.createXMLEventReader(FileReader(file))

        // stack for current group hierarchy
        val groupStack = ArrayDeque<Group>()
        // temporary root if file has multiple top-level nodes
        val topRoot = Group(id = null)
        groupStack.addFirst(topRoot)

        var svgWidth: String? = null
        var svgHeight: String? = null
        var svgViewBox: ViewBox? = null
        var groupCount = 0
        var nodeCount = 0
        while (reader.hasNext()) {
            val event = reader.nextEvent()

            when (event.eventType) {
                XMLStreamConstants.START_ELEMENT -> {
                    val se = event.asStartElement()
                    when (val localName = se.name.localPart) {
                        "svg" -> {
                            svgWidth = getAttrValue(se, "width")
                            svgHeight = getAttrValue(se, "height")
                            val svgViewBoxSting = getAttrValue(se, "viewBox")
                            svgViewBox = parseViewBox(svgViewBoxSting)
                            // push svg root as a group
                            val id = getAttrValue(se, "id") ?: "Group_${++groupCount}"
                            val svgGroup = Group(id = id)
                            groupStack.first().children.add(svgGroup)
                            svgGroup.parent = groupStack.first()
                            groupStack.addFirst(svgGroup)
                        }

                        "g" -> {
                            val id = getAttrValue(se, "id") ?: "Group_${++groupCount}"
                            val g = Group(id = id)
                            getAttrValue(se, "transform")?.let { g.transforms.add(it) }
                            // style can also contain transforms in rare cases — handled by user
                            addToCurrentGroup(groupStack, g)
                            groupStack.addFirst(g)
                        }

                        "path" -> {
                            val d = getAttrValue(se, "d") ?: ""
                            val attrs = attributesMap(se)
                            val id = getAttrValue(se, "id") ?: "Node_${++nodeCount}"
                            val p = PathElement(id = id, d = d, attrs = attrs)
                            applySvgAttributes(se, p)
                            addToCurrentGroup(groupStack, p)
                        }

                        "rect" -> {
                            val attrs = attributesMap(se)
                            val id = getAttrValue(se, "id") ?: "Node_${++nodeCount}"
                            val r = RectElement(id = id, attrs = attrs)
                            applySvgAttributes(se, r)
                            addToCurrentGroup(groupStack, r)
                        }

                        "circle" -> {
                            val attrs = attributesMap(se)
                            val id = getAttrValue(se, "id") ?: "Node_${++nodeCount}"
                            val c = CircleElement(id = id, attrs = attrs)
                            applySvgAttributes(se, c)
                            addToCurrentGroup(groupStack, c)
                        }

                        // Add more element handlers here (line, ellipse, polyline, polygon, text...)

                        else -> {
                            // Unhandled element — if it may contain children, push a group to preserve tree
                            if (mayHaveChildren(localName)) {
                                val id = getAttrValue(se, "id") ?: "Node_${++nodeCount}"
                                val generic = Group(id = id)
                                addToCurrentGroup(groupStack, generic)
                                groupStack.addFirst(generic)
                            } else {
                                // leaf element — store as a generic group with attributes if desired
                                val attrs = attributesMap(se)
                                val id = getAttrValue(se, "id") ?: "Node_${++nodeCount}"
                                val leaf = RectElement(id = id, attrs = attrs)
                                addToCurrentGroup(groupStack, leaf)
                            }
                        }
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    val ee = event.asEndElement()
                    val name = ee.name.localPart
                    // if end of a group-like element, pop the stack
                    when (name) {
                        "g", "svg" -> {
                            if (groupStack.isNotEmpty()) groupStack.removeFirst()
                        }
                        // other end tags ignored; leaf nodes were not pushed
                    }
                }

                XMLStreamConstants.CHARACTERS -> {
                    val ch = event.asCharacters()
                    if (!ch.isWhiteSpace) {
                        // typically you only get meaningful text inside <text> elements
                        // For now we ignore or could attach text nodes to current group
                        val txt = ch.data.trim()
                        if (txt.isNotEmpty()) {
                            // attach as a path-less text node? For now, we just print
                            println("SVG TEXT found: $txt")
                        }
                    }
                }

                else -> { /* ignore other events */
                }
            }
        }

        // The real root is the first child of topRoot (usually the svg group); if none found, use topRoot
        val realRoot = topRoot.children.firstOrNull() as? Group ?: topRoot

        return SvgDocument(
            width = svgWidth, height = svgHeight, viewBox = svgViewBox
                ?: ViewBox(0f, 0f, DEFAULT_VIEW_BOX_WIDTH, DEFAULT_VIEW_BOX_HEIGHT), root = realRoot
        )
    }

    // --- helpers ---
    private fun applySvgAttributes(se: StartElement, node: SvgNode) {
        val style = getAttrValue(se, "style")

        // Parse complex style="fill:#fff;stroke:black"
        if (style != null) {
            parseStyle(style, node)
        }

        // Direct attributes take priority over style
        getAttrValue(se, "fill")?.let {
            node.fillColor = parseColor(it)
        }

        getAttrValue(se, "stroke")?.let {
            node.strokeColor = parseColor(it)
        }

        getAttrValue(se, "stroke-width")?.let {
            node.strokeWidthPxAt1x = it.toFloatOrNull()
        }

        getAttrValue(se, "fill-opacity")?.let {
            node.fillAlpha = it.toFloat()
        }

        getAttrValue(se, "stroke-opacity")?.let {
            node.strokeAlpha = it.toFloat()
        }

        getAttrValue(se, "opacity")?.let {
            val opacity = it.toFloat()
            node.fillAlpha = opacity
            node.strokeAlpha = opacity
        }

        getAttrValue(se, "stroke-linecap")?.let {
            node.cap = when (it.lowercase()) {
                "butt" -> StrokeCap.Butt
                "square" -> StrokeCap.Square
                else -> StrokeCap.Round
            }
        }

        getAttrValue(se, "stroke-linejoin")?.let {
            node.join = when (it.lowercase()) {
                "bevel" -> StrokeJoin.Bevel
                "miter" -> StrokeJoin.Miter
                else -> StrokeJoin.Round
            }
        }

        getAttrValue(se, "fill-rule")?.let {
            node.fillTypeEvenOdd = (it == "evenodd")
        }
    }

    private fun parseStyle(style: String, node: SvgNode) {
        val items = style.split(";")
        for (item in items) {
            val (name, value) = item.split(":").map { it.trim() }.let {
                if (it.size == 2) it[0] to it[1] else continue
            }
            when (name) {
                "fill" -> node.fillColor = parseColor(value)
                "stroke" -> node.strokeColor = parseColor(value)
                "stroke-width" -> node.strokeWidthPxAt1x = value.toFloatOrNull()
                "opacity" -> {
                    val op = value.toFloat()
                    node.fillAlpha = op
                    node.strokeAlpha = op
                }

                "fill-opacity" -> node.fillAlpha = value.toFloat()
                "stroke-opacity" -> node.strokeAlpha = value.toFloat()
                "stroke-linecap" -> node.cap =
                    when (value) {
                        "round" -> StrokeCap.Round
                        "square" -> StrokeCap.Square
                        else -> StrokeCap.Butt
                    }

                "stroke-linejoin" -> node.join =
                    when (value) {
                        "round" -> StrokeJoin.Round
                        "bevel" -> StrokeJoin.Bevel
                        else -> StrokeJoin.Miter
                    }

                "fill-rule" -> node.fillTypeEvenOdd = (value == "evenodd")
            }
        }
    }

    private fun parseColor(value: String): Color? {
        if (value == "none") return null

        return when {
            value.startsWith("#") -> {
                parseHexColor(value)
            }

            value.startsWith("rgb") -> {
                val nums = value.substringAfter("(").substringBefore(")")
                    .split(",").map { it.trim().toInt() }
                Color(nums[0], nums[1], nums[2])
            }

            value.startsWith("rgba") -> {
                val nums = value.substringAfter("(").substringBefore(")")
                    .split(",").map { it.trim().toFloat() }
                Color(nums[0] / 255f, nums[1] / 255f, nums[2] / 255f, nums[3])
            }

            else -> null
        }
    }

    private fun parseHexColor(v: String): Color? {
        val hex = v.removePrefix("#")

        return when (hex.length) {
            3 -> { // #RGB
                val r = (hex[0].digitToInt(16) * 17)
                val g = (hex[1].digitToInt(16) * 17)
                val b = (hex[2].digitToInt(16) * 17)
                Color(r, g, b, 255)
            }

            6 -> { // #RRGGBB
                val r = hex.take(2).toInt(16)
                val g = hex.substring(2, 4).toInt(16)
                val b = hex.substring(4, 6).toInt(16)
                Color(r, g, b)
            }

            8 -> { // #RRGGBBAA
                val r = hex.take(2).toInt(16)
                val g = hex.substring(2, 4).toInt(16)
                val b = hex.substring(4, 6).toInt(16)
                val a = hex.substring(6, 8).toInt(16)
                Color(a, r, g, b)
            }

            else -> null
        }
    }

    private fun parseViewBox(vb: String?): ViewBox? {
        if (vb == null) return null
        val parts = vb.trim().split(Regex("[ ,]+"))
        if (parts.size != 4) return null

        return ViewBox(
            parts[0].toFloat(),
            parts[1].toFloat(),
            parts[2].toFloat(),
            parts[3].toFloat()
        )
    }

    private fun mayHaveChildren(localName: String): Boolean {
        return when (localName) {
            "svg", "g", "defs", "symbol" -> true
            else -> false
        }
    }

    private fun addToCurrentGroup(stack: ArrayDeque<Group>, node: SvgNode) {
        val current = stack.firstOrNull() ?: return
        current.children.add(node)
        node.parent = current
    }

    private fun getAttrValue(se: StartElement, name: String): String? {
        val attr = se.getAttributeByName(qname(name))
        return attr?.value
    }

    private fun attributesMap(se: StartElement): Map<String, String> {
        val it = se.attributes
        val map = mutableMapOf<String, String>()
        while (it.hasNext()) {
            val a = it.next() as javax.xml.stream.events.Attribute
            val key = a.name.localPart
            val value = a.value
            map[key] = value
        }
        return map
    }

    private fun qname(local: String) = QName("", local)

}


