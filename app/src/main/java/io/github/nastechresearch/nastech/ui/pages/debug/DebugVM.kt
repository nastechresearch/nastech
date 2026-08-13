package io.github.nastechresearch.nastech.ui.pages.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import io.github.nastechresearch.nastech.data.datastore.DEFAULT_ASSISTANT_ID
import io.github.nastechresearch.nastech.data.datastore.Settings
import io.github.nastechresearch.nastech.data.datastore.SettingsStore
import io.github.nastechresearch.nastech.data.model.Conversation
import io.github.nastechresearch.nastech.data.model.MessageNode
import io.github.nastechresearch.nastech.data.repository.ConversationRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.random.Random
import kotlin.uuid.Uuid

class DebugVM(
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    private val _conversationCount = MutableStateFlow<Int?>(null)
    val conversationCount: StateFlow<Int?> = _conversationCount.asStateFlow()

    init {
        refreshConversationCount()
    }

    fun refreshConversationCount() {
        viewModelScope.launch {
            _conversationCount.value = conversationRepository.countConversations()
        }
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    /**
     * 创建一个超大的对话用于测试 CursorWindow 限制
     * @param sizeMB 目标大小（MB）
     */
    fun createOversizedConversation(sizeMB: Int = 3) {
        viewModelScope.launch {
            val targetSize = sizeMB * 1024 * 1024
            val messageNodes = mutableListOf<MessageNode>()
            var currentSize = 0

            // 生成大量消息直到达到目标大小
            var index = 0
            while (currentSize < targetSize) {
                // 生成一个包含大量文本的消息（约 100KB 每条）
                val largeText = buildString {
                    repeat(100) {
                        append("This is a long test text used to test the size limit of CursorWindow.")
                        append("Row too big to fit into CursorWindow The error usually occurs when a single row of data exceeds 2MB.")
                        append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ")
                        append("Index: $index, Block: $it. ")
                    }
                }

                val userMessage = UIMessage(
                    id = Uuid.random(),
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(largeText)),
                    createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                )
                val assistantMessage = UIMessage(
                    id = Uuid.random(),
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text("Reply: $largeText")),
                    createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                )

                messageNodes.add(MessageNode.of(userMessage))
                messageNodes.add(MessageNode.of(assistantMessage))

                currentSize += largeText.length * 2 * 2 // 大约估算
                index++
            }

            val conversation = Conversation(
                id = Uuid.random(),
                assistantId = DEFAULT_ASSISTANT_ID,
                title = "Extra-large conversation test (${sizeMB}MB)",
                messageNodes = messageNodes,
            )

            conversationRepository.insertConversation(conversation)
        }
    }

    fun createConversationWithMessages(messageCount: Int = 1024) {
        viewModelScope.launch {
            val messageNodes = ArrayList<MessageNode>(messageCount)
            val timeZone = TimeZone.currentSystemDefault()
            repeat(messageCount) { index ->
                val role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT
                val message = UIMessage(
                    id = Uuid.random(),
                    role = role,
                    parts = listOf(UIMessagePart.Text(randomMessageText(index, role))),
                    createdAt = Clock.System.now().toLocalDateTime(timeZone),
                )
                messageNodes.add(MessageNode.of(message))
            }

            val conversation = Conversation(
                id = Uuid.random(),
                assistantId = DEFAULT_ASSISTANT_ID,
                title = "${messageCount} messages test",
                messageNodes = messageNodes,
            )

            conversationRepository.insertConversation(conversation)
        }
    }

    private fun randomMessageText(index: Int, role: MessageRole): String {
        val fragments = listOf(
            "Quick", "Random", "Message", "Sample", "For", "Testing", "List", "Rendering", "Scrolling", "Performance",
            "chat", "conversation", "content", "structure", "validation", "pagination", "order", "stability", "system",
        )
        val wordCount = Random.nextInt(6, 14)
        val prefix = if (role == MessageRole.USER) "User" else "Assistant"
        val body = List(wordCount) { fragments.random() }.joinToString(" ")
        return "$prefix#${index + 1}: $body"
    }
}
