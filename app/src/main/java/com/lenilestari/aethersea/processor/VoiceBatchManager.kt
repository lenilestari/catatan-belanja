package com.lenilestari.aethersea.processor

import com.lenilestari.aethersea.data.model.BatchInput
import com.lenilestari.aethersea.data.model.ShoppingItem

object VoiceBatchManager {
    private val _items = mutableListOf<BatchInput>()
    val items: List<BatchInput> get() = _items.toList()

    val hasVoiceInputs: Boolean get() = _items.any { it.source == "voice" }
    val count: Int get() = _items.size
    val isEmpty: Boolean get() = _items.isEmpty()

    fun addVoice(rawText: String) {
        if (rawText.isNotBlank()) {
            _items.add(BatchInput(
                id = System.currentTimeMillis().toString() + _items.size,
                rawText = rawText.trim(),
                source = "voice"
            ))
        }
    }

    fun addManual(item: ShoppingItem) {
        val display = "${item.item} ${item.qty} ${item.unit} Rp${item.total.toLong()}"
        _items.add(BatchInput(
            id = System.currentTimeMillis().toString() + _items.size,
            rawText = display,
            source = "manual",
            parsedItem = item
        ))
    }

    fun remove(id: String) {
        _items.removeAll { it.id == id }
    }

    fun buildGeminiInput(): String =
        _items.filter { it.source == "voice" }
            .joinToString("\n") { TextNormalizer.normalize(it.rawText) }

    fun getManualItems(): List<ShoppingItem> =
        _items.filter { it.source == "manual" }.mapNotNull { it.parsedItem }

    fun clear() = _items.clear()
}
