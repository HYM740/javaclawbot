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
import com.vladsch.flexmark.ast.*
import com.vladsch.flexmark.ast.util.TextCollectingVisitor
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import gui.ui.theme.AppColors
import gui.ui.theme.AppTheme

private val MARKDOWN_PARSER: Parser = Parser.builder().build()

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
