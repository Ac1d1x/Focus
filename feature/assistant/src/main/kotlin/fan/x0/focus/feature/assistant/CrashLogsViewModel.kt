package fan.x0.focus.feature.assistant

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.error.toAppError
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.CrashLogStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class CrashLogsViewModel(
    private val store: CrashLogStore,
) : BaseViewModel<CrashLogsAction, CrashLogsUiState, CrashLogsEffect>(CrashLogsUiState()) {

    init {
        reload()
    }

    override fun onAction(action: CrashLogsAction) {
        when (action) {
            CrashLogsAction.Refresh -> reload()
            is CrashLogsAction.Open -> open(action.name)
            CrashLogsAction.Close -> updateState { copy(open = null) }
            is CrashLogsAction.Delete -> delete(action.name)
            CrashLogsAction.Clear -> clear()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            try {
                val logs = store.list()
                updateState { copy(loading = false, logs = logs) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateState { copy(loading = false) }
                emitError(e.toAppError())
            }
        }
    }

    private fun open(name: String) {
        viewModelScope.launch {
            try {
                val content = store.read(name) ?: return@launch
                updateState { copy(open = CrashLogOpenUi(name, content)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun delete(name: String) {
        viewModelScope.launch {
            try {
                store.delete(name)
                val logs = store.list()
                updateState { copy(logs = logs, open = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }

    private fun clear() {
        viewModelScope.launch {
            try {
                store.clear()
                updateState { copy(logs = emptyList(), open = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                emitError(e.toAppError())
            }
        }
    }
}
