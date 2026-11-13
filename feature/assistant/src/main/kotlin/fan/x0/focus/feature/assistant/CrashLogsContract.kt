package fan.x0.focus.feature.assistant

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.CrashLogEntry

data class CrashLogOpenUi(
    val name: String,
    val content: String,
)

data class CrashLogsUiState(
    val loading: Boolean = true,
    val logs: List<CrashLogEntry> = emptyList(),
    val open: CrashLogOpenUi? = null,
) : UiState

sealed interface CrashLogsAction : UiAction {
    data object Refresh : CrashLogsAction
    data class Open(val name: String) : CrashLogsAction
    data object Close : CrashLogsAction
    data class Delete(val name: String) : CrashLogsAction
    data object Clear : CrashLogsAction
}

sealed interface CrashLogsEffect : UiEffect
