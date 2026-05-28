package com.vibevault.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ProfileDto — Aligned with Supabase `profiles` table.
 */
@Serializable
data class ProfileDto(
    val id: String,
    val username: String? = null,
    @SerialName("account_holder_name") val accountHolderName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean? = false,
    @SerialName("subscription_tier") val subscriptionTier: String? = "free",
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean? = false,
    val preferences: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
