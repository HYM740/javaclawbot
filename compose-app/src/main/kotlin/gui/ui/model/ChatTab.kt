package gui.ui.model

data class ChatTab(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val inputDraft: String = "",
    val isLoading: Boolean = false
)
