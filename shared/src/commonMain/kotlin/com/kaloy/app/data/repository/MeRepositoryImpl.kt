package com.kaloy.app.data.repository

import com.kaloy.app.core.error.UserErrorMessages
import com.kaloy.app.core.network.BASE_URL
import com.kaloy.app.data.dto.RestResponse
import com.kaloy.app.data.dto.me.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

class MeRepositoryImpl(private val client: HttpClient) : MeRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private suspend fun HttpResponse.decodeMessage(): String {
        val rest = body<RestResponse>()
        if (status.value >= 400) throw Exception(UserErrorMessages.fromRawMessage(rest.message))
        return rest.message
    }

    private suspend inline fun <reified T> HttpResponse.decodeData(): T {
        val rest = body<RestResponse>()
        if (status.value >= 400) throw Exception(UserErrorMessages.fromRawMessage(rest.message))
        val data = rest.data ?: throw Exception("Une erreur est survenue. Veuillez réessayer.")
        return json.decodeFromJsonElement(serializer<T>(), data)
    }

    override suspend fun getProfile(): ProfileData {
        val response = client.get("$BASE_URL/me")
        val rest = response.body<RestResponse>()
        if (response.status.value >= 400) throw Exception(UserErrorMessages.fromRawMessage(rest.message))
        val data = rest.data?.jsonObject ?: throw Exception("Données de profil introuvables.")
        return if (data.containsKey("artistType")) {
            ProfileData.Artist(json.decodeFromJsonElement(serializer<ArtistProfile>(), data))
        } else {
            ProfileData.Client(json.decodeFromJsonElement(serializer<ClientProfile>(), data))
        }
    }

    override suspend fun updatePersonalInfo(request: UpdatePersonalInfoRequest): String =
        client.put("$BASE_URL/me/personal-info") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeMessage()

    override suspend fun updateArtistProfile(request: UpdateArtistProfileRequest): String =
        client.put("$BASE_URL/me/artist-profile") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeMessage()

    override suspend fun updatePhoto(request: UpdatePhotoRequest): String =
        client.put("$BASE_URL/me/photo") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeMessage()

    override suspend fun initiateEmailChange(request: ChangeEmailRequest): String =
        client.post("$BASE_URL/me/email") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeMessage()

    override suspend fun confirmEmailChange(request: ConfirmEmailRequest): String =
        client.post("$BASE_URL/me/email/confirm") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeMessage()

    override suspend fun initiatePhoneChange(request: ChangePhoneRequest): String =
        client.post("$BASE_URL/me/phone") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeMessage()

    override suspend fun confirmPhoneChange(request: ConfirmPhoneRequest): String =
        client.post("$BASE_URL/me/phone/confirm") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeMessage()

    override suspend fun requestVerification(): String =
        client.post("$BASE_URL/me/verification-request").decodeMessage()

    override suspend fun getGroupMembers(): List<GroupMemberDto> =
        client.get("$BASE_URL/me/group/members").decodeData()

    override suspend fun addGroupMember(request: AddGroupMemberRequest): GroupMemberDto =
        client.post("$BASE_URL/me/group/members") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeData()

    override suspend fun updateGroupMember(id: Long, request: UpdateGroupMemberRequest): GroupMemberDto =
        client.put("$BASE_URL/me/group/members/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeData()

    override suspend fun updateMemberStatus(id: Long, request: GroupMemberStatusRequest): GroupMemberDto =
        client.patch("$BASE_URL/me/group/members/$id/status") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.decodeData()

    override suspend fun deleteGroupMember(id: Long): String =
        client.delete("$BASE_URL/me/group/members/$id").decodeMessage()

    override suspend fun getInstrumentRoles(): List<InstrumentRoleDto> =
        client.get("$BASE_URL/me/instrument-roles").decodeData()

    override suspend fun logout(): String =
        client.post("$BASE_URL/me/logout").decodeMessage()

    override suspend fun deleteMyAccount(): String =
        client.delete("$BASE_URL/me").decodeMessage()
}
