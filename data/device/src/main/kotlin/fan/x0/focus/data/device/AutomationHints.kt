package fan.x0.focus.data.device

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

@Single
class AutomationHints {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    fun update(value: String) {
        _text.value = value
    }

    fun clear() {
        _text.value = ""
    }
}
