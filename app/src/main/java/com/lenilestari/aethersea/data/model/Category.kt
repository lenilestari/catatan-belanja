package com.lenilestari.aethersea.data.model

import java.io.Serializable

data class Category(
    val id: String = "",
    val name: String = "",
    val icon: String = "",
    val parentId: String? = null,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0
) : Serializable {
    val isMain: Boolean get() = parentId == null
}
