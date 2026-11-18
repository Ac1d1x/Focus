package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

// Screens use these to reach the shell without depending on the app module.
val LocalFocusDrawerOpener = staticCompositionLocalOf<() -> Unit> { {} }
val LocalFocusDrawerAvailable = staticCompositionLocalOf { true }
val LocalFocusSettingsOpener = staticCompositionLocalOf<() -> Unit> { {} }

val FocusChatTopBarHeight = 64.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusLargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = if (LocalFocusDrawerAvailable.current) LocalFocusDrawerOpener.current else null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeFlexibleTopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = hapticTap(onBack)) {
                    Icon(FocusIcons.Back, contentDescription = stringResource(R.string.common_back))
                }
                onMenu != null -> IconButton(onClick = hapticTap(onMenu)) {
                    Icon(FocusIcons.Menu, contentDescription = null)
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusCompactTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onMenu: (() -> Unit)? = if (LocalFocusDrawerAvailable.current) LocalFocusDrawerOpener.current else null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = hapticTap(onBack)) {
                    Icon(FocusIcons.Back, contentDescription = stringResource(R.string.common_back))
                }
                onMenu != null -> IconButton(onClick = hapticTap(onMenu)) {
                    Icon(FocusIcons.Menu, contentDescription = null)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
fun FocusTopBarCapsule(
    showBackground: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val motion = LocalFocusMotion.current
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (showBackground) 1f else 0f,
        animationSpec = motion.effectsDefault(),
        label = "topBarCapsule",
    )
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = backgroundAlpha },
            shape = CircleShape,
            color = scheme.surfaceContainerLow,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
            content = {},
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusChatTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    scrolledUnder: Boolean = false,
    onMenu: (() -> Unit)? = if (LocalFocusDrawerAvailable.current) LocalFocusDrawerOpener.current else null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null || onMenu != null) {
                FocusTopBarCapsule(
                    showBackground = scrolledUnder,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                ) {
                    when {
                        onBack != null -> IconButton(onClick = hapticTap(onBack)) {
                            Icon(FocusIcons.Back, contentDescription = stringResource(R.string.common_back))
                        }
                        onMenu != null -> IconButton(onClick = hapticTap(onMenu)) {
                            Icon(FocusIcons.Menu, contentDescription = null)
                        }
                    }
                }
            }
        },
        actions = {
            trailing()
            if (actions != null) {
                FocusTopBarCapsule(
                    showBackground = scrolledUnder,
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                    content = actions,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}
