package gui.ui.model

data class HistoryGroup(val label: String, val items: List<HistoryEntry>)
data class HistoryEntry(val sessionId: String, val title: String)
