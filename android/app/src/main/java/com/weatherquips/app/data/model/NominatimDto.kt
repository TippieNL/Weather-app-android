package com.weatherquips.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NominatimPlace(
    val lat: String,
    val lon: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class NominatimReverse(val address: NominatimAddress? = null)

@Serializable
data class NominatimAddress(
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val county: String? = null,
    val state: String? = null,
)
