package fan.x0.focus.core.designsystem.kit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun shareText(context: Context, text: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private val FENCE = Regex("```[\\s\\S]*?```")
private val MD_LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
private val MD_MARKS = Regex("[#>*_`~|]")

/** Strips Markdown down to readable prose, for read-aloud and previews. */
fun plainText(markdown: String): String =
    markdown.replace(FENCE, " ").replace(MD_LINK, "$1").replace(MD_MARKS, "").replace(Regex("\\s+"), " ").trim()

/** Read-aloud through the system speech engine. The engine is created on first use and reused. */
object Speaker {
    private var engine: TextToSpeech? = null

    fun speak(context: Context, text: String) {
        val ready = engine
        if (ready != null) {
            ready.speak(text, TextToSpeech.QUEUE_FLUSH, null, "focus")
            return
        }
        var created: TextToSpeech? = null
        created = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine = created
                created?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "focus")
            }
        }
    }

    fun stop() {
        engine?.stop()
    }
}
