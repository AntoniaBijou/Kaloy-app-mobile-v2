package com.kaloy.app.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RefreshRequest(val refreshToken: String)
