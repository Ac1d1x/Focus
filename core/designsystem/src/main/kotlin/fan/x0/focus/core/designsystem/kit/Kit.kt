@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package fan.x0.focus.core.designsystem.kit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import fan.x0.focus.core.designsystem.components.BrandIcons
import fan.x0.focus.core.designsystem.components.FocusSnackbarHost
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

@Composable
fun tr(zh: String, en: String): String =
    if (LocalConfiguration.current.locales[0].language.startsWith("zh")) zh else en

enum class AvatarShape { Circle, Squircle, Cookie, Clover, Sunny, Flower, Gem, Pill }

fun copyText(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("focus", text))
}

fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

fun compactNumber(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
    value >= 10_000 -> "%.0fk".format(value / 1_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}

fun shortDuration(ms: Long): String = when {
    ms <= 0 -> ""
    ms < 1_000 -> "${ms}ms"
    ms < 60_000 -> "%.1fs".format(ms / 1000f)
    else -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
}

fun bytesLabel(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toFloat())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toFloat())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0) return ""
    val minutes = (now - timestamp) / 60_000
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 7 -> "${minutes / 1440}d"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}

/** A light tick on toggles, tabs and send. Silent when the user turned haptics off. */
@Composable
fun rememberTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
}

class MenuItem(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

enum class BtnKind { Filled, Tonal, Outlined, Text }

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

@Composable
fun ExIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = modifier, enabled = enabled) {
        Icon(icon, label, tint = if (tint.isSpecified) tint else LocalContentColor.current)
    }
}

/** Filled or tonal icon button with the expressive press-morph. Used for send, stop and dictation. */
@Composable
fun ExFilledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false,
    tint: Color = Color.Unspecified,
) {
    val tick = rememberTick()
    val press = {
        tick()
        onClick()
    }
    val content: @Composable () -> Unit = { Icon(icon, label, tint = if (tint.isSpecified) tint else LocalContentColor.current) }
    if (tonal) {
        FilledTonalIconButton(onClick = press, shapes = IconButtonDefaults.shapes(), modifier = modifier, enabled = enabled, content = content)
    } else {
        FilledIconButton(onClick = press, shapes = IconButtonDefaults.shapes(), modifier = modifier, enabled = enabled, content = content)
    }
}

@Composable
fun ExButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    danger: Boolean = false,
) {
    val body: @Composable RowScope.() -> Unit = {
        if (loading || icon != null) {
            Crossfade(
                targetState = loading,
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "buttonLeading",
            ) { showLoading ->
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (showLoading) {
                        LoadingIndicator(Modifier.size(20.dp), color = LocalContentColor.current)
                    } else if (icon != null) {
                        Icon(icon, null, Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val on = enabled && !loading
    val scheme = MaterialTheme.colorScheme
    when (kind) {
        BtnKind.Filled -> Button(
            onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier, enabled = on,
            colors = if (danger) ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError) else ButtonDefaults.buttonColors(),
            content = body,
        )
        BtnKind.Tonal -> FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier, enabled = on, content = body)
        BtnKind.Outlined -> OutlinedButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier, enabled = on, content = body)
        BtnKind.Text -> TextButton(
            onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = modifier, enabled = on,
            colors = if (danger) ButtonDefaults.textButtonColors(contentColor = scheme.error) else ButtonDefaults.textButtonColors(),
            content = body,
        )
    }
}

/** Connected toggle group: the expressive replacement for segmented buttons and tab rows. */
@Composable
fun <T> ExSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
) {
    val tick = rememberTick()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            val selectedOption = option == selected
            val weight by animateFloatAsState(
                targetValue = if (selectedOption) 1.2f else 1f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "segmentWeight",
            )
            ToggleButton(
                checked = selectedOption,
                onCheckedChange = {
                    tick()
                    onSelect(option)
                },
                modifier = Modifier.weight(weight),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ExMenu(items: List<MenuItem>, modifier: Modifier = Modifier, icon: ImageVector = Ic.More) {
    var open by remember { mutableStateOf(false) }
    val danger = MaterialTheme.colorScheme.error
    Box(modifier) {
        ExIconButton(icon, tr("更多", "More"), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, color = if (item.danger) danger else Color.Unspecified) },
                    onClick = { open = false; item.onClick() },
                    leadingIcon = { Icon(item.icon, null, tint = if (item.danger) danger else LocalContentColor.current) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Page scaffolding
// ---------------------------------------------------------------------------------------------

/** Standard page: collapsing flexible app bar over a padded lazy column of groups and cards. */
@Composable
fun ExPage(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scheme = MaterialTheme.colorScheme
    val sub: (@Composable () -> Unit)? = if (subtitle.isNullOrBlank()) null else {
        { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    Scaffold(
        modifier = modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = scheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = sub,
                navigationIcon = {
                    if (onBack != null) ExIconButton(Ic.Back, tr("返回", "Back"), onBack)
                    else if (onMenu != null) ExIconButton(Ic.Menu, tr("菜单", "Menu"), onMenu)
                },
                actions = actions,
                scrollBehavior = behavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface, scrolledContainerColor = scheme.surfaceContainer),
            )
        },
        floatingActionButton = fab,
        snackbarHost = { if (snackbar != null) FocusSnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 4.dp, bottom = pad.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun ExFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.85f),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.85f),
    ) {
        ExtendedFloatingActionButton(
            text = { Text(text) },
            icon = { Icon(icon, null) },
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
        )
    }
}

/** A titled stack of rows. Rows keep a 2dp seam and the stack owns the large outer corners. */
@Composable
fun ExGroup(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                Modifier.padding(start = 14.dp, top = 6.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.clip(MaterialTheme.shapes.extraLarge), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

fun LazyListScope.group(title: String? = null, key: Any? = null, content: @Composable ColumnScope.() -> Unit) {
    item(key = key, contentType = "group") {
        val motion = MaterialTheme.motionScheme
        val modifier = if (key == null) {
            Modifier
        } else {
            Modifier.animateItem(motion.defaultSpatialSpec(), motion.defaultEffectsSpec(), motion.fastEffectsSpec())
        }
        ExGroup(modifier = modifier, title = title, content = content)
    }
}

@Composable
fun ExIconTile(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 40.dp) {
    Box(Modifier.size(size).background(tint.copy(alpha = 0.14f), MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = tint)
    }
}

@Composable
fun ExRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    chevron: Boolean = false,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(scheme.surfaceContainer)
    val interactive = if (onClick != null || onLongClick != null) {
        base.combinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = { onClick?.invoke() })
    } else {
        base
    }
    Column(interactive.alpha(if (enabled) 1f else 0.5f).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            } else if (icon != null) {
                ExIconTile(icon, tint)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
            if (chevron) Icon(Ic.ChevronRight, null, tint = scheme.outline)
        }
        if (below != null) below()
    }
}

@Composable
fun ExSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val tick = rememberTick()
    val toggle: (Boolean) -> Unit = {
        tick()
        onChange(it)
    }
    ExRow(title, modifier, subtitle, icon, onClick = { toggle(!checked) }, enabled = enabled) {
        Switch(
            checked = checked,
            onCheckedChange = toggle,
            enabled = enabled,
            thumbContent = if (checked) {
                { Icon(Ic.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
    }
}

@Composable
fun ExSliderRow(
    title: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    label: String = "",
    icon: ImageVector? = null,
    subtitle: String? = null,
) {
    ExRow(
        title, modifier, subtitle, icon,
        trailing = { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
        below = { Slider(value = value, onValueChange = onChange, modifier = Modifier.padding(top = 4.dp), valueRange = range, steps = steps) },
    )
}

@Composable
fun <T> ExChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    label: (T) -> String,
) {
    ExRow(title, modifier, subtitle, icon, below = {
        Spacer(Modifier.height(10.dp))
        ExSegmented(options, selected, onSelect, label = label)
    })
}

@Composable
fun ExCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.fillMaxWidth().clip(shape).background(color)
    Column(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Sheets, dialogs, inputs
// ---------------------------------------------------------------------------------------------

@Composable
fun ExSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    full: Boolean = false,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = full),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val base = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).imePadding()
        Column(if (scroll) base.verticalScroll(rememberScrollState()) else base, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

@Composable
fun ExConfirm(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { ExButton(confirm, onConfirm, danger = danger) },
        dismissButton = { ExButton(tr("取消", "Cancel"), onDismiss, kind = BtnKind.Text) },
    )
}

@Composable
fun ExField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 10,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    error: String? = null,
    supporting: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val hint = error ?: supporting
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        supportingText = if (hint != null) {
            { Text(hint) }
        } else {
            null
        },
        trailingIcon = if (secret) {
            { ExIconButton(if (visible) Ic.EyeOff else Ic.Eye, tr("显示或隐藏", "Toggle visibility"), { visible = !visible }) }
        } else {
            trailing
        },
        isError = error != null,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
        textStyle = if (mono) LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace) else LocalTextStyle.current,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
fun ExSearch(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Ic.Search, null) },
        trailingIcon = if (value.isNotEmpty()) {
            { ExIconButton(Ic.Close, tr("清除", "Clear"), { onChange("") }) }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Status and identity
// ---------------------------------------------------------------------------------------------

@Composable
fun ExPill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, icon: ImageVector? = null) {
    Row(
        modifier.background(color.copy(alpha = 0.14f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = color)
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ExEmpty(icon: ImageVector, title: String, modifier: Modifier = Modifier, text: String? = null, action: (@Composable () -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.94f),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(96.dp).background(scheme.secondaryContainer, MaterialShapes.Cookie9Sided.toShape()), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(40.dp), tint = scheme.onSecondaryContainer)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            action?.invoke()
        }
    }
}

@Composable
fun ExLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

@Composable
fun avatarShapeOf(shape: AvatarShape): Shape = when (shape) {
    AvatarShape.Circle -> CircleShape
    AvatarShape.Squircle -> RoundedCornerShape(30)
    AvatarShape.Cookie -> MaterialShapes.Cookie9Sided.toShape()
    AvatarShape.Clover -> MaterialShapes.Clover4Leaf.toShape()
    AvatarShape.Sunny -> MaterialShapes.Sunny.toShape()
    AvatarShape.Flower -> MaterialShapes.Flower.toShape()
    AvatarShape.Gem -> MaterialShapes.Gem.toShape()
    AvatarShape.Pill -> MaterialShapes.Pill.toShape()
}

/** Identity badge: a picked photo, an emoji, or the first letter, inside a user-chosen expressive shape. */
@Composable
fun ExAvatar(
    name: String,
    modifier: Modifier = Modifier,
    emoji: String = "",
    image: String = "",
    size: Dp = 40.dp,
    shape: AvatarShape = AvatarShape.Cookie,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    onColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val bitmap = remember(image) {
        if (image.isBlank()) null else runCatching {
            BitmapFactory.decodeFile(image)?.asImageBitmap()
        }.getOrNull()
    }
    Box(modifier.size(size).clip(avatarShapeOf(shape)).background(color), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(bitmap, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            emoji.isNotBlank() -> Text(emoji, fontSize = (size.value * 0.5f).sp)
            else -> Text(name.trim().take(1).uppercase().ifEmpty { "✦" }, color = onColor, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Provider or model mark: custom uploaded image, a known brand glyph, or a letter tile. */
@Composable
fun ExBrandIcon(icon: String, name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val scheme = MaterialTheme.colorScheme
    val key = icon.ifBlank { name }.lowercase()
    val custom = remember(icon) {
        if (icon.length < 64) null else runCatching {
            val bytes = Base64.decode(icon.substringAfter(","), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    val brand = remember(key) {
        BrandIcons.providerIcons.firstOrNull { it.key == key }
            ?: BrandIcons.providerIcons.firstOrNull { option -> option.aliases.any { key.contains(it) } }
    }
    Box(modifier.size(size).clip(MaterialTheme.shapes.medium).background(scheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        when {
            custom != null -> Image(custom, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            brand != null -> Icon(painterResource(brand.icon), name, Modifier.size(size * 0.58f), tint = scheme.onSurface)
            else -> Text(name.trim().take(1).uppercase(), fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        }
    }
}
