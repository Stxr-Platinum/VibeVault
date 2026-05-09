package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PfpDto — Data Transfer Object for the new reorganized `pfps` table.
 */
@Serializable
data class PfpDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val url: String,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)
