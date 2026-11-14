@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package fan.x0.focus.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusMarkdown
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerAvailable
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerOpener
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.kit.*
import org.koin.compose.viewmodel.koinViewModel

/** Reading and reference: fetched articles, feed subscriptions, and a connected notes folder. */
@Composable
fun LibraryRoute(onMenu: (() -> Unit)? = LocalFocusDrawerOpener.current.takeIf { LocalFocusDrawerAvailable.current }) {
    val vm: LibraryViewModel = koinViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val act = vm::onAction
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(vm.errors)
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) act(LibraryAction.FolderPicked(uri.toString())) }
    val sectionLabels = LibrarySection.entries.associateWith { section ->
        when (section) {
            LibrarySection.Articles -> tr("文章", "Articles")
            LibrarySection.Feeds -> tr("订阅", "Feeds")
            LibrarySection.Files -> tr("文件", "Files")
        }
    }
    ExPage(
        tr("资料库", "Library"), onMenu = onMenu, snackbar = snackbar,
        actions = { ExIconButton(Ic.Refresh, tr("刷新", "Refresh"), { act(LibraryAction.Refresh) }) },
        fab = {
            AnimatedVisibility(
                visible = s.section == LibrarySection.Feeds || (s.section == LibrarySection.Files && s.folderName != null),
                enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f),
                exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f),
            ) {
                if (s.section == LibrarySection.Feeds) {
                    ExFab(tr("添加订阅", "Add feed"), Ic.Add, { act(LibraryAction.OpenAddFeed) })
                } else {
                    ExFab(tr("新笔记", "New note"), Ic.Edit, { act(LibraryAction.OpenNote) })
                }
            }
        },
    ) {
        item {
            ExSegmented(LibrarySection.entries, s.section, { act(LibraryAction.SectionChanged(it)) }) { sectionLabels[it].orEmpty() }
        }
        item(key = "loading", contentType = "loading") {
            AnimatedVisibility(
                visible = s.loading || s.refreshing,
                enter = fadeIn(motion.defaultEffectsSpec()),
                exit = fadeOut(motion.fastEffectsSpec()),
            ) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        when (s.section) {
            LibrarySection.Articles -> {
                item { FilterChip(s.unreadOnly, { act(LibraryAction.ToggleUnread) }, label = { Text(tr("只看未读", "Unread only")) }) }
                if (s.articles.isEmpty() && !s.loading) item { ExEmpty(Ic.Article, tr("还没有文章", "No articles yet"), text = tr("添加订阅后刷新一下。", "Add a feed, then refresh.")) }
                group(key = "articles") {
                    s.articles.forEach { a ->
                        val articleTint by animateColorAsState(if (a.read) scheme.outline else scheme.primary, motion.defaultEffectsSpec(), label = "articleTint")
                        ExRow(
                            a.title, subtitle = listOfNotNull("${a.feed} · ${a.time}", a.summary?.take(90)).joinToString("\n"),
                            icon = if (a.read) Ic.Article else Ic.Sparkle, tint = articleTint,
                            onClick = { act(LibraryAction.OpenArticle(a.id)) }, onLongClick = { act(LibraryAction.AskDeleteArticle(a.id)) },
                        )
                    }
                }
            }
            LibrarySection.Feeds -> {
                if (s.feeds.isEmpty() && !s.loading) item { ExEmpty(Ic.Rss, tr("还没有订阅", "No feeds yet"), text = tr("支持 RSS 和 Atom。", "RSS and Atom are supported.")) }
                group(key = "feeds") {
                    s.feeds.forEach { f ->
                        ExRow(f.title.ifBlank { f.url }, subtitle = f.error ?: f.url, icon = Ic.Rss, tint = if (f.error != null) scheme.error else scheme.primary) {
                            ExIconButton(Ic.Delete, tr("取消订阅", "Remove feed"), { act(LibraryAction.AskRemoveFeed(f.id)) })
                        }
                    }
                }
            }
            LibrarySection.Files -> {
                group(key = "folder") {
                    ExRow(s.folderName ?: tr("连接一个文件夹", "Connect a folder"), subtitle = if (s.folderName == null) tr("助手可以读写其中的文件", "The assistant can read and write files in it") else tr("点按更换", "Tap to change"), icon = Ic.Folder, onClick = { pickFolder.launch(null) }) {
                        if (s.folderName != null) ExIconButton(Ic.Close, tr("断开", "Disconnect"), { act(LibraryAction.ClearFolder) })
                    }
                }
                if (s.files.isNotEmpty()) {
                    group(key = "files") {
                        s.files.forEach { f ->
                            ExRow(f.name, subtitle = f.sizeLabel, icon = if (f.isDirectory) Ic.Folder else Ic.File, tint = if (f.isDirectory) scheme.tertiary else scheme.primary, onClick = { act(LibraryAction.OpenFile(f.uri)) }, onLongClick = { act(LibraryAction.AskDeleteFile(f.uri)) })
                        }
                    }
                }
            }
        }
    }
    s.reader?.let { r ->
        ExSheet(onDismiss = { act(LibraryAction.CloseReader) }, title = r.title, full = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExPill("${r.feed} · ${r.time}", color = scheme.outline)
                if (r.url.isNotBlank()) ExButton(tr("原文", "Open original"), { openUrl(context, r.url) }, kind = BtnKind.Text, icon = Ic.OpenNew)
            }
            FocusMarkdown(text = r.content, modifier = Modifier.fillMaxWidth())
        }
    }
    s.fileReader?.let { f ->
        ExSheet(onDismiss = { act(LibraryAction.CloseFile) }, title = f.name, full = true) {
            if (f.name.endsWith(".md", ignoreCase = true)) {
                FocusMarkdown(text = f.content, modifier = Modifier.fillMaxWidth())
            } else {
                Text(f.content, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (s.addFeedOpen) {
        ExSheet(onDismiss = { act(LibraryAction.DismissAddFeed) }, title = tr("添加订阅", "Add a feed")) {
            ExField(s.feedUrl, { act(LibraryAction.FeedUrlChanged(it)) }, tr("RSS 或 Atom 地址", "RSS or Atom URL"), keyboard = KeyboardType.Uri, mono = true)
            ExButton(tr("订阅", "Subscribe"), { act(LibraryAction.SubmitFeed) }, Modifier.fillMaxWidth(), loading = s.addingFeed, enabled = s.feedUrl.isNotBlank())
        }
    }
    if (s.noteOpen) {
        ExSheet(onDismiss = { act(LibraryAction.DismissNote) }, title = tr("新笔记", "New note"), full = true) {
            ExField(s.noteName, { act(LibraryAction.NoteNameChanged(it)) }, tr("文件名", "File name"))
            ExField(s.noteBody, { act(LibraryAction.NoteBodyChanged(it)) }, tr("内容", "Content"), singleLine = false, minLines = 6, maxLines = 16)
            ExButton(tr("保存", "Save"), { act(LibraryAction.SaveNote) }, Modifier.fillMaxWidth(), loading = s.savingNote, enabled = s.noteName.isNotBlank())
        }
    }
    s.deleteTarget?.let { target ->
        val name = when (target) {
            is DeleteTarget.Article -> target.title
            is DeleteTarget.Feed -> target.title
            is DeleteTarget.File -> target.name
        }
        ExConfirm(tr("确认删除？", "Delete this?"), name, tr("删除", "Delete"), onConfirm = { act(LibraryAction.ConfirmDelete) }, onDismiss = { act(LibraryAction.CancelDelete) }, danger = true)
    }
}
