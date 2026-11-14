package fan.x0.focus.feature.assistant.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fan.x0.focus.core.designsystem.kit.ExButton
import fan.x0.focus.core.designsystem.kit.ExPage
import fan.x0.focus.core.designsystem.kit.ExRow
import fan.x0.focus.core.designsystem.kit.Ic
import fan.x0.focus.core.designsystem.kit.group
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.overlay.FocusOverlay
import kotlinx.coroutines.launch

@Composable
internal fun SettingsOverlayPage(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val roleIntent = remember(context) { assistantRoleIntent(context) }
    val colorOs = remember { isColorOsDevice() }
    var canDraw by remember { mutableStateOf(FocusOverlay.canDraw(context)) }
    var isDefault by remember { mutableStateOf(isDefaultAssistant(context)) }
    var powerHijack by remember { mutableStateOf(hijackLoaded(context, HIJACK_SYSTEM)) }
    var uiHijack by remember { mutableStateOf(hijackLoaded(context, HIJACK_SYSTEM_UI)) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultAssistant(context)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDraw = FocusOverlay.canDraw(context)
                isDefault = isDefaultAssistant(context)
                powerHijack = hijackLoaded(context, HIJACK_SYSTEM)
                uiHijack = hijackLoaded(context, HIJACK_SYSTEM_UI)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val pinned = stringResource(R.string.settings_overlay_shortcut_pinned)
    val unsupported = stringResource(R.string.settings_overlay_shortcut_unsupported)
    val startFailed = stringResource(R.string.settings_overlay_start_failed)
    val unavailable = stringResource(R.string.settings_assistant_unavailable)
    val requestDefault: () -> Unit = {
        val intent = roleIntent ?: assistantSettingsIntent()
        runCatching { roleLauncher.launch(intent) }
            .onFailure { scope.launch { snackbar.showSnackbar(unavailable) } }
    }
    val permissionTitle = stringResource(R.string.settings_overlay_permission_title)
    val assistantTitle = stringResource(R.string.settings_assistant_title)
    val hijackTitle = stringResource(R.string.settings_assistant_hijack_title)
    ExPage(
        title = stringResource(R.string.settings_overlay_title),
        subtitle = stringResource(R.string.settings_overlay_intro),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        group(permissionTitle) {
            ExRow(
                title = stringResource(R.string.settings_overlay_permission),
                subtitle = if (canDraw) {
                    stringResource(R.string.settings_overlay_permission_granted)
                } else {
                    stringResource(R.string.settings_overlay_permission_needed)
                },
                icon = Ic.Layers,
                tint = if (canDraw) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                onClick = { FocusOverlay.openPermission(context) },
                trailing = {
                    if (canDraw) {
                        Icon(
                            imageVector = Ic.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            ExRow(
                title = stringResource(R.string.settings_overlay_shortcut),
                subtitle = stringResource(R.string.settings_overlay_shortcut_body),
                icon = Ic.Pin,
                onClick = {
                    if (FocusOverlay.pinShortcut(context)) {
                        scope.launch { snackbar.showSnackbar(pinned) }
                    } else {
                        scope.launch { snackbar.showSnackbar(unsupported) }
                    }
                },
            )
            ExRow(
                title = stringResource(R.string.settings_overlay_quick_tile),
                subtitle = stringResource(R.string.settings_overlay_quick_tile_body),
                icon = Ic.Widgets,
            )
        }
        group {
            ExRow(
                title = stringResource(R.string.settings_overlay_try),
                subtitle = stringResource(R.string.settings_overlay_try_body),
                icon = Ic.Overlay,
                enabled = canDraw,
                onClick = {
                    if (!FocusOverlay.canDraw(context)) {
                        FocusOverlay.openPermission(context)
                    } else if (!FocusOverlay.show(context)) {
                        scope.launch { snackbar.showSnackbar(startFailed) }
                    }
                },
            )
        }
        group(assistantTitle) {
            ExRow(
                title = stringResource(R.string.settings_assistant_status),
                subtitle = if (isDefault) {
                    stringResource(R.string.settings_assistant_status_on)
                } else {
                    stringResource(R.string.settings_assistant_status_off)
                },
                icon = Ic.Sparkle,
                onClick = if (isDefault) null else requestDefault,
                trailing = {
                    if (isDefault) {
                        Icon(
                            imageVector = Ic.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            if (!isDefault) {
                ExButton(
                    text = stringResource(
                        if (roleIntent != null) {
                            R.string.settings_assistant_set
                        } else {
                            R.string.settings_assistant_open_settings
                        },
                    ),
                    onClick = requestDefault,
                    icon = Ic.Sparkle,
                )
            }
            Text(
                text = stringResource(R.string.settings_assistant_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (colorOs) {
            group(hijackTitle) {
                ExRow(
                    title = stringResource(R.string.settings_assistant_hijack_power),
                    subtitle = stringResource(
                        if (powerHijack) {
                            R.string.settings_assistant_hijack_loaded
                        } else {
                            R.string.settings_assistant_hijack_missing
                        },
                    ),
                    icon = Ic.Bolt,
                    trailing = {
                        Icon(
                            imageVector = if (powerHijack) Ic.Check else Ic.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                ExRow(
                    title = stringResource(R.string.settings_assistant_hijack_system_ui),
                    subtitle = stringResource(
                        if (uiHijack) {
                            R.string.settings_assistant_hijack_loaded
                        } else {
                            R.string.settings_assistant_hijack_missing
                        },
                    ),
                    icon = Ic.Sparkle,
                    trailing = {
                        Icon(
                            imageVector = if (uiHijack) Ic.Check else Ic.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            item {
                Text(
                    text = stringResource(R.string.settings_assistant_hijack_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
private fun isDefaultAssistant(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val manager = context.getSystemService(RoleManager::class.java) ?: return false
    return manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
}

private fun assistantRoleIntent(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val manager = context.getSystemService(RoleManager::class.java) ?: return null
    if (!manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) return null
    return runCatching { manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT) }.getOrNull()
}

private fun assistantSettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

private fun isColorOsDevice(): Boolean {
    val names = setOf("oppo", "oneplus", "realme", "oplus")
    return Build.MANUFACTURER.lowercase() in names || Build.BRAND.lowercase() in names
}

private fun hijackLoaded(context: Context, key: String): Boolean = runCatching {
    val raw = Settings.Secure.getString(context.contentResolver, key) ?: return@runCatching false
    val parts = raw.split(':', limit = 2)
    if (parts.size != 2) return@runCatching false
    val bootCount = parts[0].toIntOrNull() ?: return@runCatching false
    val writtenAt = parts[1].toLongOrNull() ?: return@runCatching false
    val currentBoot = Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.BOOT_COUNT,
        -1,
    )
    bootCount == currentBoot && writtenAt <= SystemClock.elapsedRealtime()
}.getOrDefault(false)

private const val HIJACK_SYSTEM = "focus_hijack_system"
private const val HIJACK_SYSTEM_UI = "focus_hijack_systemui"
