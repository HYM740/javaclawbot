package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.ast.util.TextCollectingVisitor
import com.vladsch.flexmark.ext.tables.*
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

private val MARKDOWN_PARSER: Parser = Parser.builder()
    .extensions(listOf(TablesExtension.create()))
    .build()

@Composable
fun MarkdownContent(markdown: String, modifier: Modifier = Modifier) {
    val document = remember(markdown) { MARKDOWN_PARSER.parse(markdown) }
    SelectionContainer {
        Column(modifier = modifier) {
            document.children.forEach { node -> RenderMarkdownNode(node) }
        }
    }
}

@Composable
private fun RenderMarkdownNode(node: Node) {
    when (node) {
        is Paragraph -> {
            Text(
                buildAnnotatedString {
                    node.children.forEach { child -> appendInlineContent(child, this) }
                },
                style = AppTheme.typography.body,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        is Heading -> {
            val size = when (node.level) { 1 -> 24.sp; 2 -> 20.sp; 3 -> 17.sp; else -> 15.sp }
            Text(
                buildAnnotatedString {
                    node.children.forEach { child -> appendInlineContent(child, this) }
                },
                fontSize = size, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        is FencedCodeBlock -> {
            val text = node.contentChars.toString()
            CodeBlock(text)
        }
        is BulletListItem -> Row(modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)) {
            Text("• ", style = AppTheme.typography.body)
            if (node.children.count() == 1 && node.children.first() is Paragraph) {
                Text(
                    buildAnnotatedString {
                        node.children.first().children.forEach { appendInlineContent(it, this) }
                    },
                    style = AppTheme.typography.body
                )
            } else {
                Column { node.children.forEach { RenderMarkdownNode(it) } }
            }
        }
        is OrderedListItem -> Row(modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)) {
            Text("${node.openingMarker?.toString() ?: "1."} ", style = AppTheme.typography.body)
            if (node.children.count() == 1 && node.children.first() is Paragraph) {
                Text(
                    buildAnnotatedString {
                        node.children.first().children.forEach { appendInlineContent(it, this) }
                    },
                    style = AppTheme.typography.body
                )
            } else {
                Column { node.children.forEach { RenderMarkdownNode(it) } }
            }
        }
        is BulletList -> Column(modifier = Modifier.padding(vertical = 4.dp)) {
            node.children.forEach { RenderMarkdownNode(it) }
        }
        is OrderedList -> Column(modifier = Modifier.padding(vertical = 4.dp)) {
            node.children.forEach { RenderMarkdownNode(it) }
        }
        is Link -> LinkNode(node)
        is Image -> ImageNode(node)
        is BlockQuote -> Box(modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(AppColors.CodeBackground, RoundedCornerShape(0.dp, 4.dp, 4.dp, 0.dp))
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Column { node.children.forEach { RenderMarkdownNode(it) } }
        }
        is TableBlock -> TableNode(node)
        else -> {
            val text = node.chars.toString().ifEmpty { return }
            Text(text, style = AppTheme.typography.body)
        }
    }
}

private fun appendInlineContent(node: Node, builder: AnnotatedString.Builder) {
    when (node) {
        is Text, is TextBase -> builder.append(node.chars.toString())
        is Code -> {
            val text = node.text.toString()
            builder.withStyle(SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = AppColors.CodeBackground,
                color = AppColors.TextPrimary
            )) {
                append(text)
            }
        }
        is Emphasis -> {
            builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.children.forEach { appendInlineContent(it, builder) }
            }
        }
        is StrongEmphasis -> {
            builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                node.children.forEach { appendInlineContent(it, builder) }
            }
        }
        is Link -> {
            builder.withStyle(SpanStyle(
                color = Color(0xFF3B82F6),
                textDecoration = TextDecoration.Underline
            )) {
                node.children.forEach { appendInlineContent(it, builder) }
            }
        }
        is SoftLineBreak -> builder.append(" ")
        is HardLineBreak -> builder.append("\n")
        else -> builder.append(node.chars.toString())
    }
}

@Composable
private fun LinkNode(node: Link) {
    val url = node.getUrl().toString()
    val text = buildAnnotatedString {
        node.children.forEach { appendInlineContent(it, this) }
    }
    val fullText = buildAnnotatedString {
        withStyle(SpanStyle(color = Color(0xFF3B82F6), textDecoration = TextDecoration.Underline)) {
            append(text)
        }
    }
    Text(
        fullText,
        modifier = Modifier.padding(vertical = 4.dp).clickable {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI(url))
            } catch (_: Exception) {}
        }
    )
}

@Composable
private fun ImageNode(node: Image) {
    val url = node.getUrl().toString()
    val altText = TextCollectingVisitor().collectAndGetText(node)
    var bitmap by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(url) {
        bitmap = try {
            val file = File(url)
            if (file.exists()) {
                val awtImage = ImageIO.read(file) ?: null
                awtImage?.let { awtToComposeBitmap(it) }
            } else null
        } catch (_: Exception) { null }
    }

    val mod = Modifier.padding(vertical = 4.dp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = altText,
            modifier = mod.fillMaxWidth().heightIn(max = 400.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Text(altText.ifEmpty { url }, style = AppTheme.typography.body, modifier = mod, color = AppColors.TextSecondary)
    }
}

private fun awtToComposeBitmap(awtImage: BufferedImage): androidx.compose.ui.graphics.ImageBitmap {
    return awtImage.toComposeImageBitmap()
}

@Composable
private fun CodeBlock(text: String) {
    val clipboard = LocalClipboardManager.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.CodeBackground)
            .clickable { clipboard.setText(AnnotatedString(text)) }
            .padding(12.dp)
    ) {
        Text(text, style = AppTheme.typography.mono, color = AppColors.TextPrimary, softWrap = true)
    }
}

@Composable
private fun TableNode(node: TableBlock) {
    var showDialog by remember { mutableStateOf(false) }
    val colWidths = remember(node) { calculateColumnWidths(node) }
    val tableScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp))
    ) {
        Column {
            Column(modifier = Modifier.horizontalScroll(tableScrollState)) {
                node.children.forEach { child ->
                    when (child) {
                        is TableHead -> TableHeaderRow(child, colWidths)
                        is TableBody -> TableBodyContent(child, colWidths)
                        is TableSeparator -> { /* skip separator row */ }
                        else -> RenderMarkdownNode(child)
                    }
                }
            }
            HorizontalScrollbar(
                modifier = Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 2.dp),
                adapter = rememberScrollbarAdapter(scrollState = tableScrollState)
            )
        }

        // Top-right expand button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AppColors.CodeBackground.copy(alpha = 0.7f))
                .clickable { showDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Text("\u26F6", fontSize = 12.sp, color = AppColors.TextSecondary)
        }
    }

    // Independent window for expanded table view
    if (showDialog) {
        val windowState = rememberWindowState(
            position = WindowPosition.Aligned(Alignment.Center),
            width = 800.dp,
            height = 600.dp
        )

        Window(
            onCloseRequest = { showDialog = false },
            title = "表格预览",
            state = windowState,
            resizable = true
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.Surface)
                    .padding(16.dp)
            ) {
                val dialogHScrollState = rememberScrollState()
                val dialogVScrollState = rememberScrollState()

                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f)) {
                        Column(modifier = Modifier.weight(1f).horizontalScroll(dialogHScrollState)) {
                            Column(modifier = Modifier.verticalScroll(dialogVScrollState)) {
                                node.children.forEach { child ->
                                    when (child) {
                                        is TableHead -> TableHeaderRow(child, colWidths)
                                        is TableBody -> TableBodyContent(child, colWidths)
                                        is TableSeparator -> { /* skip separator row */ }
                                        else -> RenderMarkdownNode(child)
                                    }
                                }
                            }
                        }
                        HorizontalScrollbar(
                            modifier = Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 2.dp),
                            adapter = rememberScrollbarAdapter(scrollState = dialogHScrollState)
                        )
                    }
                    VerticalScrollbar(
                        modifier = Modifier.width(8.dp).padding(vertical = 2.dp),
                        adapter = rememberScrollbarAdapter(scrollState = dialogVScrollState)
                    )
                }
            }
        }
    }
}

@Composable
private fun TableHeaderRow(node: TableHead, colWidths: List<Dp>) {
    Row {
        node.children.forEach { row ->
            if (row is TableRow) {
                row.children.forEachIndexed { colIndex, cell ->
                    if (cell is TableCell) {
                        TableCellContent(cell, isHeader = true, colWidth = colWidths.getOrElse(colIndex) { 80.dp })
                    }
                }
            }
        }
    }
}

@Composable
private fun TableBodyContent(node: TableBody, colWidths: List<Dp>) {
    Column {
        node.children.forEachIndexed { rowIndex, row ->
            if (row is TableRow) {
                val bgColor = if (rowIndex % 2 == 0) Color.Transparent
                    else AppColors.CodeBackground
                Row(modifier = Modifier.background(bgColor)) {
                    row.children.forEachIndexed { colIndex, cell ->
                        if (cell is TableCell) {
                            TableCellContent(cell, isHeader = false, colWidth = colWidths.getOrElse(colIndex) { 80.dp })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCellContent(node: TableCell, isHeader: Boolean, colWidth: Dp) {
    val align = when (node.alignment) {
        TableCell.Alignment.LEFT -> Alignment.TopStart
        TableCell.Alignment.CENTER -> Alignment.TopCenter
        TableCell.Alignment.RIGHT -> Alignment.TopEnd
        else -> Alignment.TopStart
    }

    Box(
        modifier = Modifier
            .width(colWidth)
            .border(0.5.dp, AppColors.Border)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = align
    ) {
        if (isHeader) {
            val visitor = TextCollectingVisitor()
            val text = visitor.collectAndGetText(node)
            Text(text, fontWeight = FontWeight.Bold, style = AppTheme.typography.body,
                textAlign = when (node.alignment) {
                    TableCell.Alignment.LEFT -> TextAlign.Start
                    TableCell.Alignment.CENTER -> TextAlign.Center
                    TableCell.Alignment.RIGHT -> TextAlign.End
                    else -> TextAlign.Start
                })
        } else {
            Column {
                node.children.forEach { RenderMarkdownNode(it) }
            }
        }
    }
}

private fun calculateColumnWidths(node: TableBlock): List<Dp> {
    val charWidth = 8.5f
    val padding = 24f
    val minWidth = 80f
    val maxChars = mutableListOf<Int>()

    fun scanRow(row: Node) {
        row.children.forEachIndexed { i, cell ->
            if (cell is TableCell) {
                while (maxChars.size <= i) maxChars.add(0)
                val text = TextCollectingVisitor().collectAndGetText(cell)
                if (text.length > maxChars[i]) maxChars[i] = text.length
            }
        }
    }

    for (child in node.children) {
        when (child) {
            is TableHead -> child.children.forEach { n -> if (n is TableRow) scanRow(n) }
            is TableBody -> child.children.forEach { n -> if (n is TableRow) scanRow(n) }
            else -> {}
        }
    }

    return maxChars.map { maxOf(minWidth, it * charWidth + padding).dp }
}
