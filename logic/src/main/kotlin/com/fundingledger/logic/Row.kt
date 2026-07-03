package com.fundingledger.logic

import kotlinx.serialization.Serializable

@Serializable
enum class Category { GREEN, RED, NONE }

@Serializable
enum class Mode { FIXED, GOLD, PLUG }

@Serializable
data class Row(
    val id: String,
    val label: String,
    val category: Category,
    val mode: Mode,
    val amount: Double = 0.0,
    val grams: Double? = null,
    val inTransfer: Boolean = false
)
