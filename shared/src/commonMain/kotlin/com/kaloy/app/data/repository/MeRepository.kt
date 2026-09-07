package com.kaloy.app.data.repository

import com.kaloy.app.data.dto.me.*

sealed class ProfileData {
    data class Client(val profile: ClientProfile) : ProfileData()
    data class Artist(val profile: ArtistProfile) : ProfileData()
}

interface MeRepository {
    suspend fun getProfile(): ProfileData
    suspend fun updatePersonalInfo(request: UpdatePersonalInfoRequest): String
    suspend fun updateArtistProfile(request: UpdateArtistProfileRequest): String
    suspend fun updatePhoto(request: UpdatePhotoRequest): String
    suspend fun initiateEmailChange(request: ChangeEmailRequest): String
    suspend fun confirmEmailChange(request: ConfirmEmailRequest): String
    suspend fun initiatePhoneChange(request: ChangePhoneRequest): String
    suspend fun confirmPhoneChange(request: ConfirmPhoneRequest): String
    suspend fun requestVerification(): String
    suspend fun getGroupMembers(): List<GroupMemberDto>
    suspend fun addGroupMember(request: AddGroupMemberRequest): GroupMemberDto
    suspend fun updateGroupMember(id: Long, request: UpdateGroupMemberRequest): GroupMemberDto
    suspend fun updateMemberStatus(id: Long, request: GroupMemberStatusRequest): GroupMemberDto
    suspend fun deleteGroupMember(id: Long): String
    suspend fun getInstrumentRoles(): List<InstrumentRoleDto>
    suspend fun logout(): String
    suspend fun deleteMyAccount(): String
}
