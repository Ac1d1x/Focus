package fan.x0.focus.data.assistant

import kotlinx.serialization.Serializable

@Serializable
enum class ToolPack {
    Core,
    Workspace,
    Feed,
    Goals,
    Calendar,
    Device,
    DeviceSystem,
    Computer,
    Messaging,
    Agents,
    Skills,
    Mcp,
    Models,
    Plugin,
    ;

    companion object {
        val Default: Set<ToolPack> = setOf(
            Core,
            Workspace,
            Feed,
            Goals,
            Calendar,
            Device,
            Plugin,
        )

        val Phone: Set<ToolPack> = setOf(Core, Device)

        val All: Set<ToolPack> = entries.toSet()
    }
}

internal val PhoneCoreTools: Set<String> = setOf(
    "ask",
    "set_working_text",
    "stay_silent",
)
