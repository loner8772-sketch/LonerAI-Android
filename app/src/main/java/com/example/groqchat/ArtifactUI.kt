package com.example.groqchat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Collapsible list of in-progress step messages (e.g. "Generating code…",
 * "Pushing to GitHub…", "Still working on it…"). Mirrors the general shape
 * of a step-by-step progress log, functionally — not a copy of any
 * particular product's exact visuals.
 */
@Composable
fun StepsPanel(steps: List<String>, inProgress: Boolean) {
    var expanded by remember { mutableStateOf(true) }
    if (steps.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (inProgress) "Working…" else "Done",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 4.dp)) {
                    steps.forEachIndexed { idx, step ->
                        val isLast = idx == steps.lastIndex
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLast && inProgress) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(step, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Result card for a generated artifact (text/code/json/markdown): title,
 * type label, and Copy / Edit / Download actions.
 */
@Composable
fun ArtifactCard(
    artifact: Artifact,
    onEdit: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(4.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(artifact.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                artifact.type.name.lowercase().replaceFirstChar { it.uppercase() } +
                    (artifact.language?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText(artifact.title, artifact.content))
                }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }

                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }

                IconButton(onClick = { downloadArtifact(context, artifact) }) {
                    Icon(Icons.Filled.Download, contentDescription = "Download")
                }
            }
        }
    }
}

private fun extensionFor(artifact: Artifact): String = when (artifact.type) {
    ArtifactType.JSON -> "json"
    ArtifactType.MARKDOWN -> "md"
    ArtifactType.CODE -> when (artifact.language?.lowercase()) {
        "python" -> "py"
        "kotlin" -> "kt"
        "java" -> "java"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "html" -> "html"
        "css" -> "css"
        "c" -> "c"
        "c++", "cpp" -> "cpp"
        "swift" -> "swift"
        "go" -> "go"
        "rust" -> "rs"
        "shell", "bash" -> "sh"
        else -> "txt"
    }
    else -> "txt"
}

private fun safeFileName(title: String): String =
    title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().replace(" ", "_").ifBlank { "artifact" }

fun downloadArtifact(context: Context, artifact: Artifact) {
    val dir = File(context.filesDir, "artifacts").apply { mkdirs() }
    val file = File(dir, "${safeFileName(artifact.title)}.${extensionFor(artifact)}")
    file.writeText(artifact.content)

    val uri: Uri = FileProvider.getUriForFile(context, "com.example.groqchat.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Save ${file.name}"))
}

@Composable
fun EditArtifactDialog(artifact: Artifact, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(artifact.content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit: ${artifact.title}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(320.dp),
                textStyle = MaterialTheme.typography.bodySmall
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
