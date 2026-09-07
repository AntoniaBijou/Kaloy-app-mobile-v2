package com.kaloy.app.data.dto.me

import kotlinx.serialization.Serializable

// ── Réponses backend ──────────────────────────────────────────────────────────

@Serializable
data class ClientProfile(
    val userId: Long = 0L,
    val email: String = "",
    val emailVerificationStatus: String = "",
    val phone: String? = null,
    val phoneVerificationStatus: String = "",
    val accountStatus: String? = null,
    val memberSince: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val userName: String? = null,
    val photoUrl: String? = null
)

@Serializable
data class ArtistProfile(
    val userId: Long = 0L,
    val email: String = "",
    val emailVerificationStatus: String = "",
    val phone: String? = null,
    val phoneVerificationStatus: String = "",
    val accountStatus: String? = null,
    val memberSince: String? = null,
    val artistType: String = "",
    val stageName: String = "",
    val activeSinceYear: Int? = null,
    val photoUrl: String? = null,
    val bio: String? = null,
    val verificationStatus: String = "",
    val isCertified: Boolean = false,
    val members: List<GroupMemberDto> = emptyList(),
    val activeMembers: List<GroupMemberDto> = emptyList()
)

@Serializable
data class GroupMemberDto(
    val id: Long = 0L,
    val fullName: String? = null,
    val roleInstrumentId: Long? = null,
    val roleInstrumentLabel: String? = null,
    val photoUrl: String? = null,
    val status: String? = null,
    val joinedAt: String? = null
)

@Serializable
data class InstrumentRoleDto(
    val id: Long = 0L,
    val label: String = ""
)

// ── Requêtes ──────────────────────────────────────────────────────────────────

@Serializable
data class UpdatePersonalInfoRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val userName: String? = null
)

@Serializable
data class UpdateArtistProfileRequest(
    val stageName: String? = null,
    val bio: String? = null,
    val activeSinceYear: Int? = null
)

@Serializable
data class UpdatePhotoRequest(val photoUrl: String)

@Serializable
data class ChangeEmailRequest(val newEmail: String)

@Serializable
data class ConfirmEmailRequest(val code: String)

@Serializable
data class ChangePhoneRequest(val newPhone: String)

@Serializable
data class ConfirmPhoneRequest(val code: String)

@Serializable
data class AddGroupMemberRequest(
    val fullName: String,
    val roleInstrumentId: Long,
    val photoUrl: String? = null
)

@Serializable
data class UpdateGroupMemberRequest(
    val fullName: String? = null,
    val roleInstrumentId: Long? = null,
    val photoUrl: String? = null
)

@Serializable
data class GroupMemberStatusRequest(val statusId: Long)
