@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package fan.x0.focus.feature.device

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerAvailable
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerOpener
import fan.x0.focus.core.designsystem.kit.*
import fan.x0.focus.core.mvi.CollectEffect
import org.koin.compose.viewmodel.koinViewModel

@Composable
private fun Screenshot(data: String?) {
    val image = remember(data) {
        if (data.isNullOrBlank()) null else runCatching {
            val bytes = Base64.decode(data.substringAfter(","), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (image != null) Image(image, tr("屏幕画面", "Screen"), Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge), contentScale = ContentScale.FillWidth)
}

/** Phone control: this device, being controlled by another phone, paired phones, and event triggers. */
@Composable
fun DeviceRoute(onMenu: (() -> Unit)? = LocalFocusDrawerOpener.current.takeIf { LocalFocusDrawerAvailable.current }) {
    val vm: DeviceViewModel = koinViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val act = vm::onAction
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    CollectEffect(vm.effect) { effect ->
        when (effect) {
            is DeviceEffect.Message -> {
                snackbar.showSnackbar(
                    if (effect.arg != null) context.getString(effect.res, effect.arg) else context.getString(effect.res),
                )
            }
            DeviceEffect.OpenAccessibility -> {
                runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                Unit
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    val capabilitiesTitle = tr("能力", "Capabilities")
    val sessionTitle = tr("会话", "Session")
    val displayTitle = tr("虚拟屏", "Virtual display")
    val controllersTitle = tr("已授权的控制端", "Authorised controllers")
    val tabLabels = DeviceTab.entries.associateWith { tab ->
        when (tab) {
            DeviceTab.Local -> tr("本机", "This phone")
            DeviceTab.Controlled -> tr("受控", "Controlled")
            DeviceTab.Devices -> tr("其他手机", "Phones")
            DeviceTab.Triggers -> tr("触发器", "Triggers")
        }
    }
    ExPage(
        tr("设备", "Device"), onMenu = onMenu, snackbar = snackbar,
        actions = { ExIconButton(Ic.Refresh, tr("刷新", "Refresh"), { act(DeviceAction.Refresh) }) },
        fab = {
            when (s.tab) {
                DeviceTab.Devices -> ExFab(tr("配对手机", "Pair a phone"), Ic.Add, { act(DeviceAction.OpenPair) })
                DeviceTab.Triggers -> ExFab(tr("新触发器", "New trigger"), Ic.Add, { act(DeviceAction.OpenTrigger) })
                else -> Unit
            }
        },
    ) {
        item {
            ExSegmented(DeviceTab.entries, s.tab, { act(DeviceAction.TabChanged(it)) }) { tabLabels[it].orEmpty() }
        }
        if (s.loading) item { ExLoading() }
        when (s.tab) {
            DeviceTab.Local -> {
                group(capabilitiesTitle) {
                    ExSwitchRow(tr("允许助手操作这台手机", "Let the assistant operate this phone"), s.localEnabled, { act(DeviceAction.LocalEnabledChanged(it)) }, icon = Ic.Touch)
                    ExRow(tr("无障碍服务", "Accessibility service"), subtitle = if (s.accessibility) tr("已开启：读屏、点按、滑动、输入", "On: read screen, tap, swipe, type") else tr("点按去开启", "Tap to turn on"), icon = Ic.Accessibility, tint = if (s.accessibility) scheme.primary else scheme.outline, onClick = { act(DeviceAction.OpenAccessibility) })
                    ExRow("Shizuku", subtitle = if (s.shizuku) tr("已授权：shell、应用、设置、虚拟屏", "Granted: shell, apps, settings, virtual display") else tr("点按请求授权", "Tap to request access"), icon = Ic.Terminal, tint = if (s.shizuku) scheme.primary else scheme.outline, onClick = { act(DeviceAction.RequestShizuku) })
                    ExRow("Root", subtitle = if (s.root) tr("可用", "Available") else tr("不可用", "Not available"), icon = Ic.Key, tint = if (s.root) scheme.primary else scheme.outline)
                }
                group(sessionTitle) {
                    if (s.session) {
                        ExRow(tr("正在被操作", "Being operated"), subtitle = s.sessionOwner, icon = Ic.Robot, tint = scheme.tertiary) { ExButton(tr("停止", "Stop"), { act(DeviceAction.StopSession) }, kind = BtnKind.Tonal, danger = true) }
                    } else {
                        ExRow(tr("开始本机会话", "Start a local session"), icon = Ic.Play, onClick = { act(DeviceAction.StartLocal) })
                    }
                    ExRow(tr("截取当前画面", "Capture the screen"), icon = Ic.Image, onClick = { act(DeviceAction.Preview) }) { if (s.previewing) LoadingIndicator(Modifier.size(24.dp)) }
                    ExSwitchRow(tr("实时画面", "Live view"), s.live && s.liveTarget == null, { act(DeviceAction.ToggleLive()) }, icon = Ic.Screen)
                }
                item { Screenshot(s.preview) }
                group(displayTitle) {
                    if (s.displayRunning) {
                        ExRow("#${s.displayId} · ${s.displayWidth}×${s.displayHeight} · ${s.displayDensity}dpi", subtitle = s.displayBackend + if (s.displayTrusted) tr(" · 受信任", " · trusted") else "", icon = Ic.Cast)
                        ExRow(tr("预览虚拟屏", "Preview the display"), icon = Ic.Eye, onClick = { act(DeviceAction.ToggleDisplayPreview) }) { if (s.displayPreviewing) LoadingIndicator(Modifier.size(24.dp)) }
                        ExRow(tr("停止虚拟屏", "Stop the display"), icon = Ic.Stop, tint = scheme.error, onClick = { act(DeviceAction.StopDisplay) })
                    } else {
                        ExRow(tr("启动虚拟屏", "Start a virtual display"), subtitle = tr("需要 Android 10 以上，以及 Shizuku 或 Root", "Needs Android 10+ and Shizuku or root"), icon = Ic.Cast, onClick = { act(DeviceAction.StartDisplay) }) { if (s.displayStarting) LoadingIndicator(Modifier.size(24.dp)) }
                    }
                    if (s.displayError.isNotBlank()) ExRow(s.displayError, icon = Ic.Error, tint = scheme.error)
                }
                item { Screenshot(s.displayPreview) }
            }
            DeviceTab.Controlled -> {
                group {
                    ExSwitchRow(tr("允许其他手机控制这台", "Let another phone control this one"), s.remoteEnabled, { act(DeviceAction.RemoteEnabledChanged(it)) }, subtitle = tr("仅限同一可信局域网", "Trusted local network only"), icon = Ic.Phone)
                    ExRow(tr("地址", "Address"), subtitle = if (s.serverRunning) "${s.address}:${s.port}" else tr("服务未运行", "Server not running"), icon = Ic.Globe)
                    ExRow(s.pairingCode.ifBlank { "------" }, subtitle = tr("配对码，点按换一个", "Pairing code. Tap for a new one"), icon = Ic.Key, onClick = { act(DeviceAction.NewPairingCode) })
                    ExRow(tr("恢复为完整模式", "Return to full mode"), subtitle = tr("重新把这台手机当作主力使用", "Use this phone as a primary device again"), icon = Ic.Undo, onClick = { act(DeviceAction.UseFullMode) })
                }
                group(controllersTitle) {
                    if (s.controllers.isEmpty()) ExRow(tr("还没有控制端", "No controllers yet"), icon = Ic.Devices, tint = scheme.outline)
                    s.controllers.forEach { c -> ExRow(c.name, subtitle = relativeTime(c.lastSeen), icon = Ic.Phone) { ExIconButton(Ic.Delete, tr("撤销", "Revoke"), { act(DeviceAction.RevokeController(c.id)) }) } }
                }
            }
            DeviceTab.Devices -> {
                if (s.endpoints.isEmpty() && !s.loading) item { ExEmpty(Ic.Devices, tr("还没有配对的手机", "No paired phones"), text = tr("在另一台装了 Focus 的手机上打开受控模式，再回来配对。", "Turn on controlled mode on another phone with Focus, then pair here.")) }
                group {
                    s.endpoints.forEach { d ->
                        val on = d.id == s.activeEndpointId
                        val live = s.liveTarget == d.id
                        ExRow(d.name, subtitle = listOf("${d.host}:${d.port}", d.model, if (d.android.isBlank()) "" else "Android ${d.android}").filter { it.isNotBlank() }.joinToString(" · "), icon = Ic.Phone, tint = if (on) scheme.primary else scheme.outline, onClick = { act(DeviceAction.SelectDevice(d.id)) }) {
                            if (live) ExPill(tr("直播中", "Live"), color = scheme.tertiary)
                            ExMenu(listOf(
                                MenuItem(tr("开始控制", "Start control"), Ic.Play) { act(DeviceAction.StartRemote(d.id)) },
                                MenuItem(tr("停止控制", "Stop control"), Ic.Stop) { act(DeviceAction.StopRemote(d.id)) },
                                MenuItem(tr("实时画面", "Live view"), Ic.Screen) { act(DeviceAction.ToggleLive(d.id)) },
                                MenuItem(tr("移除", "Forget"), Ic.Delete, danger = true) { act(DeviceAction.ForgetDevice(d.id)) },
                            ))
                        }
                    }
                }
                if (s.liveTarget != null) item { Screenshot(s.preview) }
            }
            DeviceTab.Triggers -> {
                if (s.triggers.isEmpty() && !s.loading) item { ExEmpty(Ic.Bolt, tr("还没有设备触发器", "No device triggers"), text = tr("按应用、界面文字或事件类型匹配，然后执行命令、通知或开始对话。", "Match by app, on-screen text or event type, then run a command, notify, or start a chat.")) }
                group {
                    s.triggers.forEach { t ->
                        ExRow(t.name, subtitle = listOf(t.packageFilter, t.textFilter, t.typeFilter).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { tr("任意事件", "Any event") } + " → ${t.action}", icon = Ic.Bolt, onLongClick = { act(DeviceAction.DeleteTrigger(t.id)) }, onClick = {}) {
                            Switch(t.enabled, { act(DeviceAction.ToggleTrigger(t.id, it)) })
                            ExIconButton(Ic.Delete, tr("删除", "Delete"), { act(DeviceAction.DeleteTrigger(t.id)) })
                        }
                    }
                }
            }
        }
    }
    if (s.pairOpen) {
        ExSheet(onDismiss = { act(DeviceAction.ClosePair) }, title = tr("配对手机", "Pair a phone")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExField(s.host, { act(DeviceAction.HostChanged(it)) }, tr("地址", "Host"), Modifier.weight(2f), mono = true)
                ExField(s.portText, { act(DeviceAction.PortChanged(it)) }, tr("端口", "Port"), Modifier.weight(1f), keyboard = KeyboardType.Number, mono = true)
            }
            ExField(s.code, { act(DeviceAction.CodeChanged(it)) }, tr("配对码", "Pairing code"), keyboard = KeyboardType.Number, mono = true)
            ExButton(tr("配对", "Pair"), { act(DeviceAction.Pair) }, Modifier.fillMaxWidth(), loading = s.pairing, enabled = s.host.isNotBlank() && s.code.isNotBlank())
        }
    }
    if (s.triggerOpen) {
        val shellLabel = tr("执行命令", "Run command")
        val notifyLabel = tr("发通知", "Notify")
        val chatLabel = tr("开始对话", "Start chat")
        ExSheet(onDismiss = { act(DeviceAction.CloseTrigger) }, title = tr("新触发器", "New trigger"), full = true) {
            ExField(s.triggerName, { act(DeviceAction.TriggerNameChanged(it)) }, tr("名称", "Name"))
            ExField(s.triggerPackage, { act(DeviceAction.TriggerPackageChanged(it)) }, tr("应用包名（可选）", "App package (optional)"), mono = true)
            ExField(s.triggerText, { act(DeviceAction.TriggerTextChanged(it)) }, tr("界面文字包含（可选）", "On-screen text contains (optional)"))
            ExField(s.triggerType, { act(DeviceAction.TriggerTypeChanged(it)) }, tr("事件类型（可选）", "Event type (optional)"), mono = true)
            ExSegmented(listOf("shell", "notify", "chat"), s.triggerAction, { act(DeviceAction.TriggerActionChanged(it)) }) {
                when (it) {
                    "shell" -> shellLabel
                    "notify" -> notifyLabel
                    else -> chatLabel
                }
            }
            ExField(s.triggerCommand, { act(DeviceAction.TriggerCommandChanged(it)) }, if (s.triggerAction == "shell") tr("命令", "Command") else tr("内容", "Content"), singleLine = false, mono = s.triggerAction == "shell")
            ExField(s.triggerCooldown, { act(DeviceAction.TriggerCooldownChanged(it)) }, tr("冷却秒数", "Cooldown seconds"), keyboard = KeyboardType.Number)
            ExButton(tr("保存", "Save"), { act(DeviceAction.SaveTrigger) }, Modifier.fillMaxWidth(), enabled = s.triggerName.isNotBlank() && s.triggerCommand.isNotBlank())
        }
    }
}
