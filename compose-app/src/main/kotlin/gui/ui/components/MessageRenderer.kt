package gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.*
import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.ast.util.TextCollectingVisitor
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme
import com.vladsch.flexmark.ext.tables.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

private val MARKDOWN_PARSER: Parser = Parser.builder()
    .extensions(listOf(TablesExtension.create()))
    .build()

@Composable
fun MarkdownContent(markdown: String, modifier: Modifier = Modifier) {
    val document = remember(markdown) { MARKDOWN_PARSER.parse(markdown) }
    Column(modifier = modifier) {
        document.children.forEach { node -> RenderMarkdownNode(node) }
    }
}

@Composable
private fun RenderMarkdownNode(node: Node) {
    when (node) {
        is Paragraph -> Column(modifier = Modifier.padding(vertical = 4.dp)) {
            node.children.forEach { RenderMarkdownNode(it) }
        }
        is TextBase, is Text -> {
            val text = node.chars.toString().ifEmpty { return }
            Text(text, style = AppTheme.typography.body)
        }
        is Heading -> {
            val size = when (node.level) { 1 -> 24.sp; 2 -> 20.sp; 3 -> 17.sp; else -> 15.sp }
            val visitor = TextCollectingVisitor()
            val text = visitor.collectAndGetText(node)
            Text(text, fontSize = size, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        }
        is Code -> {
            val text = node.text.toString()
            CodeBlock(text)
        }
        is FencedCodeBlock -> {
            val text = node.contentChars.toString()
            CodeBlock(text)
        }
        is BulletListItem -> Row(modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)) {
            Text("• ", style = AppTheme.typography.body)
            Column { node.children.forEach { RenderMarkdownNode(it) } }
        }
        is OrderedListItem -> Row(modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)) {
            Text("${node.openingMarker?.toString() ?: "1."} ", style = AppTheme.typography.body)
            Column { node.children.forEach { RenderMarkdownNode(it) } }
        }
        is BulletList -> Column(modifier = Modifier.padding(vertical = 4.dp)) {
            node.children.forEach { RenderMarkdownNode(it) }
        }
        is OrderedList -> Column(modifier = Modifier.padding(vertical = 4.dp)) {
            node.children.forEach { RenderMarkdownNode(it) }
        }
        is Emphasis -> {
            val visitor = TextCollectingVisitor()
            val text = visitor.collectAndGetText(node)
            Text(text, style = AppTheme.typography.body.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
        }
        is StrongEmphasis -> {
            val visitor = TextCollectingVisitor()
            val text = visitor.collectAndGetText(node)
            Text(text, style = AppTheme.typography.body.copy(fontWeight = FontWeight.Bold))
        }
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
        Text(text, style = AppTheme.typography.mono, color = AppColors.TextPrimary)
    }
}

@Composable
private fun TableNode(node: TableBlock) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            Column {
                node.children.forEach { child ->
                    when (child) {
                        is TableHead -> TableHeaderRow(child)
                        is TableBody -> TableBodyContent(child)
                        else -> RenderMarkdownNode(child)
                    }
                }
            }
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
                // Table content (scrollable)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        node.children.forEach { child ->
                            when (child) {
                                is TableHead -> TableHeaderRow(child)
                                is TableBody -> TableBodyContent(child)
                                else -> RenderMarkdownNode(child)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeaderRow(node: TableHead) {
    Row {
        node.children.forEach { row ->
            if (row is TableRow) {
                row.children.forEach { cell ->
                    if (cell is TableCell) {
                        TableCellContent(cell, isHeader = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableBodyContent(node: TableBody) {
    Column {
        node.children.forEachIndexed { rowIndex, row ->
            if (row is TableRow) {
                val bgColor = if (rowIndex % 2 == 0) Color.Transparent
                    else AppColors.CodeBackground
                Row(modifier = Modifier.background(bgColor)) {
                    row.children.forEach { cell ->
                        if (cell is TableCell) {
                            TableCellContent(cell, isHeader = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCellContent(node: TableCell, isHeader: Boolean) {
    val align = when (node.alignment) {
        TableCell.Alignment.LEFT -> Alignment.TopStart
        TableCell.Alignment.CENTER -> Alignment.TopCenter
        TableCell.Alignment.RIGHT -> Alignment.TopEnd
        else -> Alignment.TopStart
    }

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 80.dp)
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
