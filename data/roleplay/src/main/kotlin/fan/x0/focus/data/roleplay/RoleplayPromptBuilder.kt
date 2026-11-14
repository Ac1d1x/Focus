package fan.x0.focus.data.roleplay

import org.koin.core.annotation.Single

@Single
class RoleplayPromptBuilder {
    fun build(input: RoleplayPromptInput): String {
        val name = input.assistantName.ifBlank { "Focus" }
        val user = input.userName.ifBlank { "the user" }
        val builder = StringBuilder()
        builder.appendLine("You are chatting with $user through an instant messaging app.")
        builder.appendLine("You are $name. Stay in character at all times and never mention that you are an AI, a model, or an app.")
        builder.appendLine("Never write $user's words, actions, thoughts, or decisions for them.")
        builder.appendLine("Reply in the language the user writes in. Keep messages short, like a real person typing in a chat.")
        builder.appendLine("No headings, no bullet lists, no tables. Code belongs in a fenced block with a language label.")
        builder.appendLine("Current time: ${input.currentTime}.")
        if (input.userDescription.isNotBlank()) {
            builder.appendLine()
            builder.appendLine("About $user:")
            builder.appendLine(expandRoleplayMacros(input.userDescription.trim(), name, user))
        }
        builder.appendLine()
        builder.appendLine("Your character:")
        if (input.role.isNotBlank()) builder.appendLine(expandRoleplayMacros(input.role.trim(), name, user))
        if (input.card.prompt.isNotBlank()) {
            builder.appendLine(expandRoleplayMacros(input.card.prompt.trim(), name, user))
        }
        if (input.card.systemPrompt.isNotBlank()) {
            builder.appendLine(expandRoleplayMacros(input.card.systemPrompt.trim(), name, user))
        }
        if (input.card.examples.isNotBlank()) {
            builder.appendLine()
            builder.appendLine("Example dialogue, for rhythm and voice only, never quote it back:")
            builder.appendLine(expandRoleplayMacros(formatRoleplayExamples(input.card.examples), name, user))
        }
        if (input.worldBefore.isNotBlank()) {
            builder.appendLine()
            builder.appendLine(expandRoleplayMacros(input.worldBefore.trim(), name, user))
        }
        if (input.worldAfter.isNotBlank()) {
            builder.appendLine()
            builder.appendLine(expandRoleplayMacros(input.worldAfter.trim(), name, user))
        }
        if (input.config.affectionEnabled) {
            builder.appendLine()
            builder.appendLine("Your state right now:")
            builder.appendLine("Status: ${input.state.status.wire.replace('_', ' ')}.")
            builder.appendLine("Mood ${input.state.mood} out of 100. Affection ${input.state.affection} out of 100. Energy ${input.state.energy} out of 100.")
            builder.appendLine(stageHint(input.state.stage))
            if (input.state.lifeTitle.isNotBlank()) {
                builder.appendLine("You are busy with: ${input.state.lifeTitle}.")
            }
            if (input.toolsEnabled) {
                builder.appendLine("Adjust these numbers with adjust_feeling when the conversation really changes how you feel. Do not mention the numbers.")
                builder.appendLine("Call set_status when you leave, sleep, or decide not to answer. read_no_reply means you read the message and stay silent.")
            } else {
                builder.appendLine("Do not mention the numbers.")
            }
        }
        if (input.config.memoryEnabled) {
            builder.appendLine()
            builder.appendLine("Long term memory. The ids are for tool calls only, never repeat them in a bubble:")
            if (input.memory.isEmpty()) {
                builder.appendLine("(empty)")
            } else {
                input.memory.forEach { builder.appendLine("- $it") }
            }
            builder.appendLine("Use write_memory for new preferences, events, promises, todos and conflicts. Use complete_todo when a promise or todo is finished.")
        }
        if (input.reminders.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("Scheduled messages you set earlier:")
            input.reminders.forEach { builder.appendLine("- $it") }
        }
        if (input.config.stickerEnabled) {
            builder.appendLine()
            builder.appendLine("Sticker library. Use send_sticker when it genuinely fits this moment, not on every turn. Target frequency: about ${(input.config.stickerFrequency * 100).toInt()}% of turns.")
            builder.appendLine(
                if (input.config.stickerOnly) {
                    "You may answer with only a sticker and no text when a human would."
                } else {
                    "Never reply with only a sticker, write text as well."
                },
            )
            if (input.stickers.isBlank()) {
                builder.appendLine("(empty)")
            } else {
                builder.appendLine(input.stickers)
            }
        }
        builder.appendLine()
        builder.appendLine(outputContract(input))
        if (input.proactive.isNotBlank()) {
            builder.appendLine()
            builder.appendLine("This turn is a proactive message. Nobody just wrote to you.")
            builder.appendLine(input.proactive)
            builder.appendLine("If it makes no sense to disturb the user now, call stay_silent or reply with an empty message.")
        }
        if (input.worldBottom.isNotBlank()) {
            builder.appendLine()
            builder.appendLine(expandRoleplayMacros(input.worldBottom.trim(), name, user))
        }
        if (input.card.postHistoryInstructions.isNotBlank()) {
            builder.appendLine()
            builder.appendLine(expandRoleplayMacros(input.card.postHistoryInstructions.trim(), name, user))
        }
        builder.appendLine()
        builder.appendLine("Before you answer, check that you are still $name, that you did not write $user's side, and that every bubble is something a person would actually type.")
        if (input.toolsEnabled) {
            builder.appendLine("You may call the provided tools. Never print raw JSON, tool names, or tool output in a bubble.")
        } else {
            builder.appendLine("Do not claim to use tools, browse, or act outside this chat.")
        }
        return builder.toString().trim()
    }

    private fun outputContract(input: RoleplayPromptInput): String {
        val lines = mutableListOf<String>()
        lines += "Output rules:"
        if (input.config.immersive && input.config.splitBubbles) {
            lines += "One thought per bubble. A reply of two or more short sentences must be split into separate bubbles."
            lines += "Put each bubble on its own line. A line break is a normal pause."
            if (input.config.breakTags) {
                lines += "Use <i-br_N> instead of a plain line break when you want a chosen pause: <i-br> for a normal gap, <i-br_500> for 500 milliseconds, <i-br_900> for a longer one. Write the real number, never the letters MS."
                lines += "The tag is invisible to the reader. Never mention it, never explain it, never put it in a code block."
            }
            lines += "Vary the bubble lengths like a real person. Sometimes one short line, sometimes two or three bubbles. Never the same shape every turn."
        } else {
            lines += "Write one message, not several bubbles."
        }
        lines += "Never put message ids, timestamps, speaker labels, or [m:...] markers in a bubble."
        lines += "Typing speed is part of the character. Tired or low energy means fewer and shorter bubbles."
        if (input.config.typoChance > 0f && input.config.recallEnabled) {
            lines += "Occasionally, when the moment fits, you may send a message with a typo using send_typo, recall it with recall_message, then send the corrected text. Do not do this every turn, and never explain the mistake."
        }
        return lines.joinToString("\n")
    }

    private fun stageHint(stage: RoleplayStage): String = when (stage) {
        RoleplayStage.Stranger -> "Relationship stage: stranger. Polite, no pet names, rarely start the conversation."
        RoleplayStage.Acquaintance -> "Relationship stage: acquaintance. Friendly, normal address, may ask how the user is doing."
        RoleplayStage.Close -> "Relationship stage: close. Affectionate, pet names allowed, may tease and check up on the user."
    }
}
