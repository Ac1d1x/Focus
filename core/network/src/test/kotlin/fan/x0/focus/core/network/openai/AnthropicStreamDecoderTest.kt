package fan.x0.focus.core.network.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicStreamDecoderTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesServerToolsCitationsAndTitle() {
        val decoder = AnthropicStreamDecoder()
        val events = mutableListOf<ChatStreamEvent>()
        val payloads = listOf(
            """{"type":"message_start","message":{"usage":{"input_tokens":10,"cache_read_input_tokens":4}}}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"server_tool_use","id":"srvtoolu_1","name":"deepseek_web_search","input":{"action":"search","queries":["a","b"]},"caller":{"type":"direct"},"kind":"search"}}""",
            """{"type":"content_block_start","index":2,"content_block":{"type":"web_search_tool_result","tool_use_id":"srvtoolu_1","content":[{"type":"web_search_result","title":"T","url":"https://example.com","snippet":"S","site_name":"Example","encrypted_content":"x"}],"kind":"search"}}""",
            """{"type":"content_block_start","index":3,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":3,"delta":{"type":"text_delta","text":"Hello"}}""",
            """{"type":"content_block_delta","index":3,"delta":{"type":"citations_delta","citation":{"type":"web_search_result_location","url":"https://example.com","title":"T","encrypted_index":"","cited_text":"Hello"}}}""",
            """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null,"title":"Greeting"},"usage":{"output_tokens":2}}""",
        )
        payloads.forEach { payload ->
            events += decoder.decode(json.parseToJsonElement(payload) as JsonObject)
        }

        val started = events.filterIsInstance<ChatStreamEvent.ServerToolStarted>().single()
        assertEquals("srvtoolu_1", started.call.id)
        assertEquals("deepseek_web_search", started.call.name)
        assertEquals("search", started.call.kind)
        assertEquals("search", started.call.action?.wireString("action"))

        val finished = events.filterIsInstance<ChatStreamEvent.ServerToolFinished>().single()
        assertEquals("srvtoolu_1", finished.call.id)
        assertEquals(1, finished.call.sources.size)
        assertEquals("https://example.com", finished.call.sources.first().url)

        val raw = events.filterIsInstance<ChatStreamEvent.ServerToolCalls>().last()
        assertEquals(2, raw.raw.size)

        assertEquals("Hello", events.filterIsInstance<ChatStreamEvent.Text>().joinToString("") { it.delta })

        val annotations = events.filterIsInstance<ChatStreamEvent.Annotations>().last()
        assertEquals(1, annotations.raw.size)
        val citation = (annotations.raw.first() as JsonObject)["url_citation"] as JsonObject
        assertEquals(5, citation.wireInt("end_index"))

        assertEquals("Greeting", events.filterIsInstance<ChatStreamEvent.Title>().single().title)
        assertTrue(events.any { it is ChatStreamEvent.Usage && it.prompt == 10 && it.completion == 2 })
        assertEquals("end_turn", events.filterIsInstance<ChatStreamEvent.Finished>().single().reason)
    }
}
