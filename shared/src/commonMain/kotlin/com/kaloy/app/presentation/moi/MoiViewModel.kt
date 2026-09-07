package com.kaloy.app.presentation.moi

import com.kaloy.app.core.error.UserErrorMessages
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.me.*
import com.kaloy.app.data.repository.MeRepository
import com.kaloy.app.data.repository.ProfileData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MoiUiState {
    data object Loading : MoiUiState()
    data class ClientSuccess(val profile: ClientProfile) : MoiUiState()
    data class ArtistSuccess(val profile: ArtistProfile) : MoiUiState()
    data class Error(val message: String) : MoiUiState()
}

sealed class MoiOperationState {
    data object Idle : MoiOperationState()
    data object Loading : MoiOperationState()
    data class Success(val message: String) : MoiOperationState()
    data class Error(val message: String) : MoiOperationState()
    data object OtpRequired : MoiOperationState()
    data object NeedsRelogin : MoiOperationState()
    data object AccountDeleted : MoiOperationState()
}

class MoiViewModel(
    private val repository: MeRepository,
    private val sessionManager: AuthSessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow<MoiUiState>(MoiUiState.Loading)
    val uiState: StateFlow<MoiUiState> = _uiState.asStateFlow()

    private val _operationState = MutableStateFlow<MoiOperationState>(MoiOperationState.Idle)
    val operationState: StateFlow<MoiOperationState> = _operationState.asStateFlow()

    private val _instrumentRoles = MutableStateFlow<List<InstrumentRoleDto>>(emptyList())
    val instrumentRoles: StateFlow<List<InstrumentRoleDto>> = _instrumentRoles.asStateFlow()

    init {
        loadProfile()
        loadInstrumentRoles()
    }

    fun loadProfile() {
        scope.launch {
            _uiState.value = MoiUiState.Loading
            try {
                when (val data = repository.getProfile()) {
                    is ProfileData.Client -> _uiState.value = MoiUiState.ClientSuccess(data.profile)
                    is ProfileData.Artist -> _uiState.value = MoiUiState.ArtistSuccess(data.profile)
                }
            } catch (e: Exception) {
                if (e.message == "SESSION_EXPIRED") {
                    sessionManager.clear()
                    _operationState.value = MoiOperationState.NeedsRelogin
                } else {
                    _uiState.value = MoiUiState.Error(UserErrorMessages.fromThrowable(e))
                }
            }
        }
    }

    private fun loadInstrumentRoles() {
        scope.launch {
            try {
                _instrumentRoles.value = repository.getInstrumentRoles()
            } catch (_: Exception) {}
        }
    }

    fun updatePersonalInfo(firstName: String?, lastName: String?, userName: String?) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                val msg = repository.updatePersonalInfo(
                    UpdatePersonalInfoRequest(
                        firstName = firstName?.takeIf { it.isNotBlank() },
                        lastName = lastName?.takeIf { it.isNotBlank() },
                        userName = userName?.takeIf { it.isNotBlank() }
                    )
                )
                _operationState.value = MoiOperationState.Success(msg)
                loadProfile()
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun updateArtistProfile(stageName: String?, bio: String?, activeSinceYear: Int?) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                val msg = repository.updateArtistProfile(
                    UpdateArtistProfileRequest(
                        stageName = stageName?.takeIf { it.isNotBlank() },
                        bio = bio?.takeIf { it.isNotBlank() },
                        activeSinceYear = activeSinceYear
                    )
                )
                _operationState.value = MoiOperationState.Success(msg)
                loadProfile()
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun updatePhoto(photoUrl: String) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                val msg = repository.updatePhoto(UpdatePhotoRequest(photoUrl))
                _operationState.value = MoiOperationState.Success(msg)
                loadProfile()
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun initiateEmailChange(newEmail: String) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                repository.initiateEmailChange(ChangeEmailRequest(newEmail))
                _operationState.value = MoiOperationState.OtpRequired
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun confirmEmailChange(code: String) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                repository.confirmEmailChange(ConfirmEmailRequest(code))
                _operationState.value = MoiOperationState.NeedsRelogin
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun initiatePhoneChange(newPhone: String) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                repository.initiatePhoneChange(ChangePhoneRequest(newPhone))
                _operationState.value = MoiOperationState.OtpRequired
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun confirmPhoneChange(code: String) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                val msg = repository.confirmPhoneChange(ConfirmPhoneRequest(code))
                _operationState.value = MoiOperationState.Success(msg)
                loadProfile()
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun requestVerification() {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                val msg = repository.requestVerification()
                _operationState.value = MoiOperationState.Success(msg)
                loadProfile()
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun addGroupMember(fullName: String, roleInstrumentId: Long, photoUrl: String?) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                repository.addGroupMember(AddGroupMemberRequest(fullName, roleInstrumentId, photoUrl))
                _operationState.value = MoiOperationState.Success("Membre ajouté avec succès.")
                loadProfile()
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun updateGroupMember(id: Long, fullName: String?, roleInstrumentId: Long?, photoUrl: String?) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                repository.updateGroupMember(id, UpdateGroupMemberRequest(fullName, roleInstrumentId, photoUrl))
                _operationState.value = MoiOperationState.Success("Membre mis à jour.")
                loadProfile()
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun updateMemberStatus(id: Long, statusId: Long) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                repository.updateMemberStatus(id, GroupMemberStatusRequest(statusId))
                _operationState.value = MoiOperationState.Success("Statut mis à jour.")
                loadProfile()
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun deleteGroupMember(id: Long) {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                val msg = repository.deleteGroupMember(id)
                _operationState.value = MoiOperationState.Success(msg)
                loadProfile()
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun logout() {
        scope.launch {
            try { repository.logout() } catch (_: Exception) {}
            sessionManager.clear()
            _operationState.value = MoiOperationState.AccountDeleted
        }
    }

    fun deleteAccount() {
        scope.launch {
            _operationState.value = MoiOperationState.Loading
            try {
                repository.deleteMyAccount()
                sessionManager.clear()
                _operationState.value = MoiOperationState.AccountDeleted
            } catch (e: Exception) {
                _operationState.value = MoiOperationState.Error(UserErrorMessages.fromThrowable(e))
            }
        }
    }

    fun resetOperationState() {
        _operationState.value = MoiOperationState.Idle
    }

    fun dispose() = scope.cancel()
}
