package com.greyloop.aurelius.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Simple markdown rendering for AI responses.
 * Supports: **bold**, *italic*, `code`, ```code blocks```, lists
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    BasicText(
        text = parseMarkdown(text),
        modifier = modifier,
        style = androidx.compose.ui.text.TextStyle(color = color)
    )
}

private fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val lines = text.split("\n")

        lines.forEachIndexed { lineIndex, line ->
            // Code block (```...```)
            if (line.startsWith("```")) {
                if (line.removePrefix("```").isNotEmpty()) {
                    // Opening or single-line code block
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(line.removePrefix("```").removeSuffix("```"))
                    }
                }
            } else {
                // Parse inline styles
                var lineText = line
                var hasContent = false

                while (lineText.isNotEmpty()) {
                    hasContent = true
                    when {
                        lineText.startsWith("**") -> {
                            val endIndex = lineText.indexOf("**", 2)
                            if (endIndex > 0) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(lineText.substring(2, endIndex))
                                }
                                lineText = lineText.substring(endIndex + 2)
                            } else {
                                append(lineText[0])
                                lineText = lineText.substring(1)
                            }
                        }
                        lineText.startsWith("*") && !lineText.startsWith("**") -> {
                            val endIndex = lineText.indexOf("*", 1)
                            if (endIndex > 0) {
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(lineText.substring(1, endIndex))
                                }
                                lineText = lineText.substring(endIndex + 1)
                            } else {
                                append(lineText[0])
                                lineText = lineText.substring(1)
                            }
                        }
                        lineText.startsWith("`") -> {
                            val endIndex = lineText.indexOf("`", 1)
                            if (endIndex > 0) {
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                                    append(lineText.substring(1, endIndex))
                                }
                                lineText = lineText.substring(endIndex + 1)
                            } else {
                                append(lineText[0])
                                lineText = lineText.substring(1)
                            }
                        }
                        lineText.startsWith("- ") || lineText.startsWith("* ") -> {
                            append("  \u2022 ")
                            lineText = lineText.substring(2)
                        }
                        lineText.matches(Regex("^\\d+\\.\\s.*")) -> {
                            val match = Regex("^(\\d+)\\.\\s(.*)").find(lineText)
                            if (match != null) {
                                append("  ${match.groupValues[1]}. ")
                                lineText = match.groupValues[2]
                            }
                        }
                        else -> {
                            append(lineText[0])
                            lineText = lineText.substring(1)
                        }
                    }
                }

                if (!hasContent) {
                    append(" ")
                }
            }

            if (lineIndex < lines.size - 1) {
                append("\n")
            }
        }
    }
}
