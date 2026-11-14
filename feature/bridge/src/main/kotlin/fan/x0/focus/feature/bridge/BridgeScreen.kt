@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package fan.x0.focus.feature.bridge

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerAvailable
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerOpener
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.kit.*
import org.koin.compose.viewmodel.koinViewModel

/** Computers reached through the bridge: pairing, persistent terminals, and background jobs. */
@Composable
fun BridgeRoute(onMenu: (() -> Unit)? = LocalFocusDrawerOpener.current.takeIf { LocalFocusDrawerAvailable.current }) {
    val vm: BridgeViewModel = koinViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val act = vm::onAction
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(vm.errors)
    val scheme = MaterialTheme.colorScheme
    val pairedTitle = if (s.devices.isEmpty()) null else tr("已配对", "Paired")
    val discoveryTitle = tr("局域网发现", "Found on the network")
    val rootsTitle = tr("工作区目录", "Workspace folders")
    val tabLabels = BridgeTab.entries.associateWith { tab ->
        when (tab) {
            BridgeTab.Devices -> tr("电脑", "Computers")
            BridgeTab.Terminal -> tr("终端", "Terminal")
            BridgeTab.Background -> tr("后台任务", "Background")
        }
    }
    LaunchedEffect(s.tab) {
        if (s.tab == BridgeTab.Terminal) act(BridgeAction.LoadSessions)
        if (s.tab == BridgeTab.Background) act(BridgeAction.LoadBackground)
    }
    ExPage(
        tr("电脑", "Computer"), onMenu = onMenu, snackbar = snackbar,
        actions = { ExIconButton(Ic.Refresh, tr("刷新", "Refresh"), { act(BridgeAction.Refresh) }) },
        fab = {
            when (s.tab) {
                BridgeTab.Devices -> ExFab(tr("配对电脑", "Pair a computer"), Ic.Add, { act(BridgeAction.OpenAdd) })
                BridgeTab.Terminal -> if (s.devices.isNotEmpty()) ExFab(tr("新终端", "New terminal"), Ic.Terminal, { act(BridgeAction.NewSession) })
                BridgeTab.Background -> Unit
            }
        },
    ) {
        item {
            ExSegmented(BridgeTab.entries, s.tab, { act(BridgeAction.TabChanged(it)) }) { tabLabels[it].orEmpty() }
        }
        if (s.loading || s.sessionsLoading || s.backgroundLoading) item { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
        when (s.tab) {
            BridgeTab.Devices -> {
                if (s.devices.isEmpty() && !s.loading) {
                    item { ExEmpty(Ic.Computer, tr("还没有配对的电脑", "No paired computers"), text = tr("在电脑上运行 focus-bridge serve，会打印一个六位配对码。", "Run focus-bridge serve on the computer. It prints a six-digit code.")) }
                }
                group(pairedTitle) {
                    s.devices.forEach { d ->
                        ExRow(d.name, subtitle = "${d.host}:${d.port} · ${d.os} ${d.arch} · v${d.version}", icon = Ic.Computer, tint = if (d.active) scheme.primary else scheme.outline, onClick = { act(BridgeAction.SelectDevice(d.id)) }) {
                            if (d.id in s.liveDevices) ExPill(tr("在线", "Online"))
                            ExIconButton(Ic.Delete, tr("移除", "Forget"), { act(BridgeAction.AskForgetDevice(d.id)) })
                        }
                    }
                }
                group(discoveryTitle) {
                    ExRow(tr("扫描局域网", "Scan the network"), icon = Ic.Search, onClick = { act(BridgeAction.Scan) }) { if (s.scanning) LoadingIndicator(Modifier.size(24.dp)) }
                    s.discovered.forEach { f -> ExRow(f.name, subtitle = "${f.host}:${f.port} · ${f.os} ${f.arch}", icon = Ic.Devices, chevron = true, onClick = { act(BridgeAction.UseDiscovered(f.host, f.port)) }) }
                }
                if (s.devices.any { it.active }) {
                    group(rootsTitle) {
                        s.roots.forEach { root -> ExRow(root, icon = Ic.Folder) { ExIconButton(Ic.Close, tr("移除", "Remove"), { act(BridgeAction.RemoveRoot(root)) }) } }
                        ExRow(tr("添加目录", "Add a folder"), icon = Ic.Add, below = {
                            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExField(s.rootDraft, { act(BridgeAction.RootDraftChanged(it)) }, tr("电脑上的绝对路径", "Absolute path on the computer"), Modifier.weight(1f), mono = true)
                                ExIconButton(Ic.Add, tr("添加", "Add"), { act(BridgeAction.AddRoot) }, enabled = s.rootDraft.isNotBlank())
                            }
                        })
                        ExRow(tr("保存目录", "Save folders"), icon = Ic.Check, onClick = { act(BridgeAction.SaveRoots) }) { if (s.savingRoots) LoadingIndicator(Modifier.size(24.dp)) }
                    }
                }
            }
            BridgeTab.Terminal -> {
                if (s.sessions.isEmpty() && !s.sessionsLoading) item { ExEmpty(Ic.Terminal, tr("还没有终端", "No terminals yet"), text = tr("终端在电脑上常驻，断开后可以再接回来。", "Terminals persist on the computer, so you can reattach later.")) }
                group {
                    s.sessions.forEach { t ->
                        ExRow(t.name.ifBlank { t.id.take(8) }, subtitle = "${t.shell} · ${t.cols}×${t.rows}", icon = Ic.Terminal, onClick = { act(BridgeAction.OpenSession(t.id)) }) {
                            ExIconButton(Ic.Close, tr("结束", "Close"), { act(BridgeAction.CloseSession(t.id)) })
                        }
                    }
                }
            }
            BridgeTab.Background -> {
                if (s.backgroundJobs.isEmpty() && !s.backgroundLoading) item { ExEmpty(Ic.Pending, tr("没有后台任务", "No background jobs"), text = tr("长命令结束后会通知你，也能自动接着聊。", "Long commands notify you when done and can continue the chat.")) }
                group {
                    s.backgroundJobs.forEach { j ->
                        val running = j.endedAt <= 0
                        val failed = !running && (j.exitCode != 0 || j.timedOut)
                        ExRow(
                            j.command, subtitle = listOf(j.status, if (running) relativeTime(j.startedAt) else tr("退出码 ${j.exitCode}", "exit ${j.exitCode}"), if (j.timedOut) tr("超时", "timed out") else "").filter { it.isNotBlank() }.joinToString(" · "),
                            icon = if (running) Ic.Play else if (failed) Ic.Error else Ic.Done, tint = if (failed) scheme.error else if (running) scheme.tertiary else scheme.primary,
                            onClick = { act(BridgeAction.OpenBackground(j.id)) },
                        ) { if (running) ExIconButton(Ic.Stop, tr("终止", "Kill"), { act(BridgeAction.KillBackground(j.id)) }) }
                    }
                }
                if (s.backgroundJobs.isNotEmpty()) item { ExButton(tr("清除已结束的任务", "Clear finished jobs"), { act(BridgeAction.ClearBackgroundJobs) }, kind = BtnKind.Text, icon = Ic.Delete) }
            }
        }
    }
    if (s.addOpen) {
        ExSheet(onDismiss = { act(BridgeAction.DismissAdd) }, title = tr("配对电脑", "Pair a computer")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExField(s.host, { act(BridgeAction.HostChanged(it)) }, tr("地址", "Host"), Modifier.weight(2f), mono = true)
                ExField(s.port, { act(BridgeAction.PortChanged(it)) }, tr("端口", "Port"), Modifier.weight(1f), keyboard = KeyboardType.Number, mono = true)
            }
            ExField(s.code, { act(BridgeAction.CodeChanged(it)) }, tr("六位配对码", "Six-digit code"), keyboard = KeyboardType.Number, mono = true, supporting = tr("五分钟内有效，只能用一次", "Valid for five minutes, single use"))
            ExButton(tr("配对", "Pair"), { act(BridgeAction.SubmitPair) }, Modifier.fillMaxWidth(), loading = s.pairing, enabled = s.host.isNotBlank() && s.code.isNotBlank(), icon = Ic.Link)
        }
    }
    s.opened?.let { session ->
        val lines = remember(s.terminalOutput) { parseTerminal(s.terminalOutput, 60_000) }
        val listState = rememberLazyListState()
        LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }
        ExSheet(onDismiss = { act(BridgeAction.CloseTerminal) }, title = session.name.ifBlank { tr("终端", "Terminal") }, full = true, scroll = false) {
            if (s.connecting) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.large).background(TerminalBackground).padding(10.dp), state = listState) {
                items(lines) { line -> Text(line, color = Color(0xFFE6E6E6), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Esc" to TerminalKey.Escape, "Tab" to TerminalKey.Tab, "⏎" to TerminalKey.Enter, "Ctrl+C" to TerminalKey.ControlC, "Ctrl+D" to TerminalKey.ControlD).forEach { (label, key) ->
                    ExButton(label, { act(BridgeAction.SendTerminalKey(key)) }, kind = BtnKind.Tonal)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExField(s.terminalInput, { act(BridgeAction.TerminalInputChanged(it)) }, tr("输入命令", "Type a command"), Modifier.weight(1f), mono = true)
                ExIconButton(Ic.Send, tr("发送", "Send"), { act(BridgeAction.SendTerminalInput) })
            }
        }
    }
    s.backgroundSelected?.let { id ->
        ExSheet(onDismiss = { act(BridgeAction.CloseBackground) }, title = tr("任务输出", "Job output"), full = true) {
            Text(s.backgroundJobs.firstOrNull { it.id == id }?.command.orEmpty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.backgroundFollowing) ExPill(tr("实时跟随", "Following"), color = scheme.tertiary)
                ExButton(tr("清空输出", "Clear output"), { act(BridgeAction.ClearBackgroundLogs) }, kind = BtnKind.Text)
            }
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(TerminalBackground).padding(10.dp)) {
                s.backgroundLogs.forEach { log -> Text(log.data, color = if (log.stream == "stderr") Color(0xFFFF8A80) else Color(0xFFE6E6E6), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp) }
            }
        }
    }
    s.deleteTarget?.let { d ->
        ExConfirm(tr("移除这台电脑？", "Forget this computer?"), tr("「${d.name}」需要重新配对才能再用。", "\"${d.name}\" will need pairing again."), tr("移除", "Forget"), onConfirm = { act(BridgeAction.ConfirmForget) }, onDismiss = { act(BridgeAction.CancelForget) }, danger = true)
    }
}
