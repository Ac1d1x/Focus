package fan.x0.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.theme.FocusTheme
import fan.x0.focus.data.assistant.CrashLogEntry
import fan.x0.focus.data.assistant.CrashLogStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = CrashLogStore(applicationContext)
        store.markReviewed()
        CrashNotifier.cancel(this)
        setContent {
            FocusTheme {
                CrashLogScreen(
                    store = store,
                    onClose = { finish() },
                    onContinue = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashLogScreen(
    store: CrashLogStore,
    onClose: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<CrashLogEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var openName by remember { mutableStateOf<String?>(null) }
    var openContent by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        logs = store.list()
        loading = false
    }
    LaunchedEffect(openName) {
        openName?.let { openContent = store.read(it).orEmpty() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crash_page_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.crash_page_close))
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (logs.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                store.clear()
                                logs = emptyList()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.crash_page_clear))
                    }
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.crash_page_continue))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusReveal(index = 0) {
                Text(
                    text = stringResource(R.string.crash_page_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!loading && logs.isEmpty()) {
                Text(
                    text = stringResource(R.string.crash_page_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                itemsIndexed(logs, key = { _, log -> log.name }) { index, log ->
                    FocusReveal(index = index + 1) {
                        Card(
                            onClick = { openName = log.name },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                        .format(Date(log.time)),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = log.preview.ifBlank { stringResource(R.string.crash_page_unknown) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { openName = log.name }) {
                                        Text(stringResource(R.string.crash_page_view))
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val content = store.read(log.name).orEmpty()
                                                val intent = Intent(Intent.ACTION_SEND)
                                                    .setType("text/plain")
                                                    .putExtra(Intent.EXTRA_TEXT, content.ifBlank { log.preview })
                                                context.startActivity(
                                                    Intent.createChooser(
                                                        intent,
                                                        context.getString(R.string.crash_page_share_title),
                                                    ),
                                                )
                                            }
                                        },
                                    ) {
                                        Text(stringResource(R.string.crash_page_share))
                                    }
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                store.delete(log.name)
                                                logs = store.list()
                                            }
                                        },
                                    ) {
                                        Text(
                                            text = stringResource(R.string.crash_page_delete),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    openName?.let { name ->
        AlertDialog(
            onDismissRequest = { openName = null },
            title = { Text(name) },
            text = {
                SelectionContainer {
                    Text(
                        text = openContent,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { openName = null }) {
                    Text(stringResource(R.string.crash_page_close))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, openContent)
                        context.startActivity(
                            Intent.createChooser(
                                intent,
                                context.getString(R.string.crash_page_share_title),
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.crash_page_share))
                }
            },
        )
    }
}
