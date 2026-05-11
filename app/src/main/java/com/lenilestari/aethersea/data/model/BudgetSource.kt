package com.lenilestari.aethersea.data.model

import com.google.firebase.Timestamp
import java.io.Serializable

data class BudgetSource(
    val id: String = "",
    val name: String = "",
    val amount: Long = 0L,
    val period: String = "",
    val receivedDate: Timestamp = Timestamp(0, 0),
    val note: String = "",
    val createdAt: Timestamp = Timestamp(0, 0)
) : Serializable
