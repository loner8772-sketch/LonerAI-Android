package com.example.groqchat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Minimal markdown renderer covering what LLM chat replies actually use:
 * **bold**, *italic*, `inline code`, and ```code blocks```. Not a full
 * CommonMark parser — just enough to stop raw ** and ``` from showing up
 * literally in the chat UI.
 */
object SimpleMarkdown {

    fun render(raw: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        val text = raw
        while (i < text.length) {
            when {
                text.startsWith("```", i) -> {
                    val end = text.indexOf("```", i + 3)
                    val block = if (end == -1) text.substring(i + 3) else text.substring(i + 3, end)
                    // Drop an optional language tag on the first line of the block
                    val cleaned = block.substringAfter("\n", block).let {
                        if (block.substringBefore("\n").isNotBlank() && !block.substringBefore("\n").contains(" "))
                            block.substringAfter("\n") else block
                    }
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = androidx.compose.ui.graphics.Color(0x1A000000))) {
                        append(cleaned.trim('\n'))
                    }
                    i = if (end == -1) text.length else end + 3
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end == -1) { append(text.substring(i)); i = text.length }
                    else {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                    }
                }
                text.startsWith("`", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end == -1) { append(text.substring(i)); i = text.length }
                    else {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = androidx.compose.ui.graphics.Color(0x1A000000))) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    }
                }
                text.startsWith("*", i) && !text.startsWith("**", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end == -1) { append(text.substring(i)); i = text.length }
                    else {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
