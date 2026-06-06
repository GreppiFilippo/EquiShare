/** Defines remote DTOs for Profile data. */
package it.unibo.equishare.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    val email: String? = null,
    @SerialName("full_name")        val fullName: String? = null,
    @SerialName("avatar_url")       val avatarUrl: String? = null,
    @SerialName("default_currency") val defaultCurrency: String = "EUR",
    val locale: String? = "en",
    @SerialName("is_active")        val isActive: Boolean = true,
    @SerialName("created_at")       val createdAt: String? = null,
    @SerialName("updated_at")       val updatedAt: String? = null,
)
