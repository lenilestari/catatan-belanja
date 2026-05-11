package com.lenilestari.aethersea.data.model

import com.google.firebase.Timestamp
import java.io.Serializable

data class ShoppingSession(
    val id: String = "",
    val date: Timestamp = Timestamp(0, 0),
    val period: String = "",
    val mainCategoryId: String = "",
    val mainCategoryName: String = "",
    val subCategoryId: String = "",
    val subCategoryName: String = "",
    val items: List<ShoppingItem> = emptyList(),
    val grandTotal: Double = 0.0,
    val createdAt: Timestamp = Timestamp(0, 0),
    val updatedAt: Timestamp = Timestamp(0, 0),
    val rawInputs: List<String> = emptyList()
) : Serializable {
    val displayCategory: String get() = if (subCategoryName.isNotEmpty()) subCategoryName else mainCategoryName
}
