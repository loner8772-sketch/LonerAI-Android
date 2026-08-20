package com.example.groqchat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoneAITheme(darkTheme = true) {
                RootScreen()
            }
        }
    }
}

private fun prefs(context: Context) = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

private fun getApiKey(context: Context): String = prefs(context).getString("api_key", "") ?: ""
private fun saveApiKey(context: Context, key: String) { prefs(context).edit().putString("api_key", key).apply() }

private fun getUserName(context: Context): String = prefs(context).getString("user_name", "") ?: ""
private fun saveUserName(context: Context, name: String) { prefs(context).edit().putString("user_name", name).apply() }

private fun getGithubOwner(context: Context): String = prefs(context).getString("gh_owner", "") ?: ""
private fun getGithubRepo(context: Context): String = prefs(context).getString("gh_repo", "") ?: ""
private fun getGithubToken(context: Context): String = prefs(context).getString("gh_token", "") ?: ""
private fun saveGithubConfig(context: Context, owner: String, repo: String, token: String) {
    prefs(context).edit()
        .putString("gh_owner", owner)
        .putString("gh_repo", repo)
        .putString("gh_token", token)
        .apply()
}

private fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hey"
}

private fun looksLikeAppBuildRequest(text: String): Boolean {
    val t = text.lowercase()
    val hasTarget = listOf("app", "apk", "application").any { t.contains(it) }
    val hasAction = listOf("build", "make", "create", "generate").any { t.contains(it) }
    return hasAction && hasTarget
}

private fun looksLikeArtifactRequest(text: String): Boolean {
    val t = text.lowercase()
    val hasAction = listOf("write", "create", "generate", "make", "draft", "build").any { t.contains(it) }
    val hasTarget = listOf(
        "document", "file", "code", "script", "json", "config", "list", "table",
        "essay", "story", "plan", "spreadsheet", "report", "letter", "email",
        "poem", "outline", "summary", "resume", "cv", "function", "class", "program"
    ).any { t.contains(it) }
    return hasAction && hasTarget
}

@Composable
fun RootScreen() {
    var tab by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var apiKey by remember { mutableStateOf(getApiKey(context)) }
    var owner by remember { mutableStateOf(getGithubOwner(context)) }
    var repo by remember { mutableStateOf(getGithubRepo(context)) }
    var token by remember { mutableStateOf(getGithubToken(context)) }
    var userName by remember { mutableStateOf(getUserName(context)) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> ChatScreen(
                    apiKey = apiKey, owner = owner, repo = repo, token = token, userName = userName,
                    onNeedsSetup = { tab = 1 }
                )
                else -> SettingsScreen(
                    apiKey = apiKey, owner = owner, repo = repo, token = token, userName = userName,
                    onSave = { k, o, r, t, name ->
                        apiKey = k; owner = o; repo = r; token = t; userName = name
                        saveApiKey(context, k)
                        saveUserName(context, name)
                        saveGithubConfig(context, o, r, t)
                    }
                )
            }
        }
        NavigationBar {
            NavigationBarItem(
                selected = tab == 0, onClick = { tab = 0 },
                icon = { Icon(Icons.Filled.Chat, contentDescription = null) }, label = { Text("Chat") }
            )
            NavigationBarItem(
                selected = tab == 1, onClick = { tab = 1 },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, label = { Text("Settings") }
            )
        }
    }
}

// ---------------------------------------------------------------------
// Chat screen — drawer with conversation history + main chat
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(apiKey: String, owner: String, repo: String, token: String, userName: String, onNeedsSetup: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val conversations by db.conversationDao().getAllConversations()
        .collectAsState(initial = emptyList())

    var currentConversationId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conversations) {
        if (conversations.isEmpty()) {
            val id = UUID.randomUUID().toString()
            db.conversationDao().insertConversation(ConversationEntity(id, "New chat", System.currentTimeMillis()))
        } else if (currentConversationId == null || conversations.none { it.id == currentConversationId }) {
            currentConversationId = conversations.first().id
        }
    }

    var renamingConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    renamingConversation?.let { conv ->
        RenameDialog(
            currentTitle = conv.title,
            onDismiss = { renamingConversation = null },
            onSave = { newTitle ->
                scope.launch { db.conversationDao().renameConversation(conv.id, newTitle) }
                renamingConversation = null
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(16.dp)) {
                    Text("Lone AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    NavigationDrawerItem(
                        label = { Text("New Chat") },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                val id = UUID.randomUUID().toString()
                                db.conversationDao().insertConversation(
                                    ConversationEntity(id, "New chat", System.currentTimeMillis())
                                )
                                currentConversationId = id
                                drawerState.close()
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Recent Chats", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn {
                        items(conversations, key = { it.id }) { conv ->
                            val isSelected = conv.id == currentConversationId
                            NavigationDrawerItem(
                                label = { Text(conv.title, maxLines = 1) },
                                selected = isSelected,
                                onClick = {
                                    currentConversationId = conv.id
                                    scope.launch { drawerState.close() }
                                },
                                badge = {
                                    Row {
                                        IconButton(onClick = { renamingConversation = conv }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Rename", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    db.conversationDao().deleteMessagesForConversation(conv.id)
                                                    db.conversationDao().deleteConversation(conv.id)
                                                    if (currentConversationId == conv.id) currentConversationId = null
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) {
        currentConversationId?.let { convId ->
            ConversationView(
                conversationId = convId,
                db = db,
                apiKey = apiKey, owner = owner, repo = repo, token = token, userName = userName,
                onNeedsSetup = onNeedsSetup,
                onMenuClick = { scope.launch { drawerState.open() } }
            )
        }
    }
}

@Composable
fun RenameDialog(currentTitle: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(text.trim().ifBlank { currentTitle }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Converts a stored MessageEntity back into the right UI representation. */
private fun entityToArtifact(e: MessageEntity): Artifact {
    val type = when (e.meta) {
        "CODE" -> ArtifactType.CODE
        "JSON" -> ArtifactType.JSON
        "MARKDOWN" -> ArtifactType.MARKDOWN
        else -> ArtifactType.TEXT
    }
    return Artifact(id = e.id.toString(), title = e.title ?: "Untitled", type = type, content = e.content, language = e.language)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationView(
    conversationId: String,
    db: AppDatabase,
    apiKey: String, owner: String, repo: String, token: String, userName: String,
    onNeedsSetup: () -> Unit,
    onMenuClick: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val githubConfigured = owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank()

    val persistedMessages by remember(conversationId) {
        db.conversationDao().getMessagesForConversation(conversationId)
    }.collectAsState(initial = emptyList())

    var liveSteps by remember(conversationId) { mutableStateOf<Pair<androidx.compose.runtime.snapshots.SnapshotStateList<String>, MutableState<Boolean>>?>(null) }

    var editingArtifact by remember { mutableStateOf<Artifact?>(null) }
    editingArtifact?.let { artifact ->
        EditArtifactDialog(
            artifact = artifact,
            onDismiss = { editingArtifact = null },
            onSave = { newContent ->
                scope.launch {
                    db.conversationDao().insertMessage(
                        MessageEntity(
                            conversationId = conversationId, kind = "artifact",
                            title = artifact.title, content = newContent,
                            meta = artifact.type.name, language = artifact.language,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                editingArtifact = null
            }
        )
    }

    fun persistText(role: String, content: String) {
        scope.launch {
            db.conversationDao().insertMessage(MessageEntity(conversationId = conversationId, kind = "text", role = role, content = content, timestamp = System.currentTimeMillis()))
            if (role == "user" && persistedMessages.isEmpty()) {
                db.conversationDao().renameConversation(conversationId, titleFromFirstMessage(content))
            }
        }
    }
    fun persistSteps(steps: List<String>) {
        scope.launch { db.conversationDao().insertMessage(MessageEntity(conversationId = conversationId, kind = "steps", content = steps.joinToString("\n"), timestamp = System.currentTimeMillis())) }
    }
    fun persistArtifact(artifact: Artifact) {
        scope.launch { db.conversationDao().insertMessage(MessageEntity(conversationId = conversationId, kind = "artifact", title = artifact.title, content = artifact.content, meta = artifact.type.name, language = artifact.language, timestamp = System.currentTimeMillis())) }
    }
    fun persistApk(path: String) {
        scope.launch { db.conversationDao().insertMessage(MessageEntity(conversationId = conversationId, kind = "apk", content = path, timestamp = System.currentTimeMillis())) }
    }

    val isEmpty = persistedMessages.isEmpty() && liveSteps == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onMenuClick) { Icon(Icons.Filled.Menu, contentDescription = "Menu") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isEmpty) {
                GreetingEmptyState(userName = userName, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(persistedMessages, key = { it.id }) { e ->
                        when (e.kind) {
                            "text" -> MessageBubble(role = e.role ?: "assistant", content = e.content)
                            "steps" -> StepsPanel(steps = e.content.split("\n"), inProgress = false)
                            "artifact" -> ArtifactCard(
                                artifact = entityToArtifact(e),
                                onEdit = { editingArtifact = entityToArtifact(e) }
                            )
                            "apk" -> {
                                val f = File(e.content)
                                if (f.exists()) InstallApkRow(f)
                                else Text("(built APK no longer available on this device)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    liveSteps?.let { (steps, inProgress) ->
                        item { StepsPanel(steps = steps, inProgress = inProgress.value) }
                    }
                }
            }

            // Floating pill-shaped input bar
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                "Ask anything…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        inner()
                    }
                )
                IconButton(onClick = {
                    val text = input.trim()
                    if (text.isBlank()) return@IconButton
                    if (apiKey.isBlank()) { onNeedsSetup(); return@IconButton }

                    val filterResult = ContentFilter.check(text)
                    persistText("user", text)
                    input = ""

                    if (!filterResult.allowed) {
                        persistText("assistant", filterResult.reason ?: "I can't help with that.")
                        return@IconButton
                    }

                    when {
                        looksLikeAppBuildRequest(text) -> {
                            if (!githubConfigured) {
                                persistText("assistant", "I can build that, but I need your GitHub repo + token set up first. Head to Settings and fill those in, then ask again.")
                                return@IconButton
                            }
                            val stepsList = mutableStateListOf<String>()
                            val inProgress = mutableStateOf(true)
                            liveSteps = stepsList to inProgress
                            val outputDir = File(context.filesDir, "apks").apply { mkdirs() }
                            val pipeline = BuildPipeline(apiKey, owner, repo, token) { msg ->
                                scope.launch { stepsList.add(msg) }
                            }
                            scope.launch {
                                val file = pipeline.run(text, outputDir)
                                inProgress.value = false
                                persistSteps(stepsList)
                                if (file != null) persistApk(file.absolutePath)
                                else persistText("assistant", "Couldn't finish the build — see the steps above for what happened.")
                                liveSteps = null
                            }
                        }
                        looksLikeArtifactRequest(text) -> {
                            val stepsList = mutableStateListOf("Thinking…")
                            val inProgress = mutableStateOf(true)
                            liveSteps = stepsList to inProgress
                            ArtifactClient.generate(apiKey, text, object : ArtifactClient.Callback2 {
                                override fun onSuccess(artifact: Artifact) {
                                    stepsList.add("Creating ${artifact.title}…")
                                    inProgress.value = false
                                    persistSteps(stepsList)
                                    persistArtifact(artifact)
                                    liveSteps = null
                                }
                                override fun onError(message: String) {
                                    inProgress.value = false
                                    liveSteps = null
                                    persistText("assistant", "Couldn't generate that: $message")
                                }
                            })
                        }
                        else -> {
                            val stepsList = mutableStateListOf("Thinking…")
                            val inProgress = mutableStateOf(true)
                            liveSteps = stepsList to inProgress
                            val history = (persistedMessages.filter { it.kind == "text" }.map { (it.role ?: "assistant") to it.content }) + ("user" to text)
                            GroqClient.sendMessage(apiKey, history, object : GroqClient.ChatCallback {
                                override fun onSuccess(reply: String) {
                                    inProgress.value = false
                                    liveSteps = null
                                    persistText("assistant", reply)
                                }
                                override fun onError(message: String) {
                                    inProgress.value = false
                                    liveSteps = null
                                    persistText("assistant", "Error: $message")
                                }
                            })
                        }
                    }
                }) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
            }
        }
    }
}

@Composable
fun GreetingEmptyState(userName: String, modifier: Modifier = Modifier) {
    val hour = remember { java.time.LocalTime.now().hour }
    val greeting = greetingForHour(hour)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(36.dp)) {
                val strokeWidth = 3.dp.toPx()
                for (i in 0 until 8) {
                    val angle = (i * 45f) * (Math.PI / 180f)
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val r = size.minDimension / 2
                    drawLine(
                        color = androidx.compose.ui.graphics.Color(0xFF3E6BAE),
                        start = androidx.compose.ui.geometry.Offset(cx, cy),
                        end = androidx.compose.ui.geometry.Offset(
                            cx + (r * kotlin.math.cos(angle)).toFloat(),
                            cy + (r * kotlin.math.sin(angle)).toFloat()
                        ),
                        strokeWidth = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (userName.isNotBlank()) "$greeting, $userName" else greeting,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun MessageBubble(role: String, content: String) {
    val isUser = role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
            modifier = Modifier.padding(4.dp).fillMaxWidth(0.85f)
                .wrapContentWidth(if (isUser) Alignment.End else Alignment.Start)
        ) {
            Text(
                text = SimpleMarkdown.render(content),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun InstallApkRow(file: File) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Button(onClick = {
            val uri: Uri = FileProvider.getUriForFile(context, "com.example.groqchat.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }) { Text("Install built APK") }
    }
}

// ---------------------------------------------------------------------
// Settings screen
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKey: String, owner: String, repo: String, token: String, userName: String,
    onSave: (String, String, String, String, String) -> Unit
) {
    var k by remember(apiKey) { mutableStateOf(apiKey) }
    var o by remember(owner) { mutableStateOf(owner) }
    var r by remember(repo) { mutableStateOf(repo) }
    var t by remember(token) { mutableStateOf(token) }
    var n by remember(userName) { mutableStateOf(userName) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Your name", fontWeight = FontWeight.Bold)
            Text("Used for the greeting on a new chat. Optional.")
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = n, onValueChange = { n = it; saved = false },
                singleLine = true, placeholder = { Text("e.g. Alex") }, modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Text("Groq API key", fontWeight = FontWeight.Bold)
            Text("Used for chat and for generating apps/content. Get one free at console.groq.com.")
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = k, onValueChange = { k = it; saved = false },
                singleLine = true, placeholder = { Text("gsk_...") }, modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Text("GitHub build pipeline", fontWeight = FontWeight.Bold)
            Text("Where generated apps get pushed and compiled — only needed when you ask Lone AI to build an app. The token needs repo + workflow scope — treat it like a password.")
            Text(
                "If your token has an expiration date set (GitHub tokens often do, e.g. 1 year), app builds will start failing with an auth error once it expires — just generate a new one at github.com/settings/tokens and paste it in below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = o, onValueChange = { o = it; saved = false },
                singleLine = true, label = { Text("GitHub username/org") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = r, onValueChange = { r = it; saved = false },
                singleLine = true, label = { Text("Repo name") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = t, onValueChange = { t = it; saved = false },
                singleLine = true, label = { Text("GitHub token") }, modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Button(onClick = { onSave(k.trim(), o.trim(), r.trim(), t.trim(), n.trim()); saved = true }) { Text("Save") }
            if (saved) {
                Spacer(Modifier.height(8.dp))
                Text("Saved.", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
