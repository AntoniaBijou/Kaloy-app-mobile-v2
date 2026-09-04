package com.kaloy.app.presentation.moi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kaloy.app.core.session.AuthSessionManager
import com.kaloy.app.data.dto.me.GroupMemberDto
import com.kaloy.app.data.repository.MeRepository
import com.kaloy.app.presentation.auth.welcome.WelcomeScreen
import com.kaloy.app.presentation.moi.components.*
import com.kaloy.app.ui.theme.*
import org.koin.compose.koinInject

// ── Etats de dialog ───────────────────────────────────────────────────────────

private sealed class ActiveDialog {
    data object EditPersonalInfo : ActiveDialog()
    data object EditArtistProfile : ActiveDialog()
    data object EditPhoto : ActiveDialog()
    data class ChangeEmail(val step: Int = 1, val pendingEmail: String = "") : ActiveDialog()
    data class ChangePhone(val step: Int = 1, val pendingPhone: String = "") : ActiveDialog()
    data object AddMember : ActiveDialog()
    data class EditMember(val member: GroupMemberDto) : ActiveDialog()
    data class ChangeMemberStatus(val member: GroupMemberDto) : ActiveDialog()
    data class DeleteMember(val member: GroupMemberDto) : ActiveDialog()
    data object ConfirmLogout : ActiveDialog()
    data object ConfirmDeleteAccount : ActiveDialog()
}

// ── Ecran ─────────────────────────────────────────────────────────────────────

class MoiScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository = koinInject<MeRepository>()
        val sessionManager = koinInject<AuthSessionManager>()
        val viewModel = remember { MoiViewModel(repository, sessionManager) }

        val uiState by viewModel.uiState.collectAsState()
        val operationState by viewModel.operationState.collectAsState()
        val instrumentRoles by viewModel.instrumentRoles.collectAsState()

        var activeDialog by remember { mutableStateOf<ActiveDialog?>(null) }
        var operationError by remember { mutableStateOf<String?>(null) }

        DisposableEffect(Unit) {
            onDispose { viewModel.dispose() }
        }

        LaunchedEffect(operationState) {
            when (val state = operationState) {
                is MoiOperationState.OtpRequired -> {
                    when (val d = activeDialog) {
                        is ActiveDialog.ChangeEmail -> activeDialog = d.copy(step = 2)
                        is ActiveDialog.ChangePhone -> activeDialog = d.copy(step = 2)
                        else -> {}
                    }
                    viewModel.resetOperationState()
                }
                is MoiOperationState.NeedsRelogin -> {
                    sessionManager.clear()
                    navigator.replaceAll(WelcomeScreen())
                }
                is MoiOperationState.AccountDeleted -> {
                    navigator.replaceAll(WelcomeScreen())
                }
                is MoiOperationState.Success -> {
                    activeDialog = null
                    operationError = null
                    viewModel.resetOperationState()
                }
                is MoiOperationState.Error -> {
                    operationError = state.message
                }
                else -> {
                    if (state is MoiOperationState.Loading) operationError = null
                }
            }
        }

        val isOperationLoading = operationState is MoiOperationState.Loading

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text("Mon profil", fontWeight = FontWeight.Bold, color = KaloyTextPrimary)
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Retour",
                                tint = KaloyTextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = KaloyDarkSurface)
                )
            },
            containerColor = KaloyDarkBg
        ) { innerPadding ->
            when (val state = uiState) {
                is MoiUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = KaloyPurple)
                    }
                }

                is MoiUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = state.message,
                                color = KaloyRed,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { viewModel.loadProfile() },
                                colors = ButtonDefaults.buttonColors(containerColor = KaloyPurple)
                            ) {
                                Text("Réessayer")
                            }
                        }
                    }
                }

                is MoiUiState.ClientSuccess -> {
                    val profile = state.profile
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).background(KaloyDarkBg),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item {
                            ProfileHeader(
                                displayName = listOfNotNull(profile.firstName, profile.lastName)
                                    .joinToString(" ")
                                    .ifBlank { profile.userName ?: profile.email.substringBefore("@") },
                                photoUrl = profile.photoUrl,
                                emailVerificationStatus = profile.emailVerificationStatus,
                                phoneVerificationStatus = profile.phone?.let { profile.phoneVerificationStatus },
                                accountStatus = profile.accountStatus,
                                onEditPhoto = { activeDialog = ActiveDialog.EditPhoto }
                            )
                        }
                        item {
                            ClientProfileContent(
                                profile = profile,
                                onEditPersonalInfo = { activeDialog = ActiveDialog.EditPersonalInfo },
                                onChangeEmail = { activeDialog = ActiveDialog.ChangeEmail() },
                                onChangePhone = { activeDialog = ActiveDialog.ChangePhone() },
                                onLogout = { activeDialog = ActiveDialog.ConfirmLogout },
                                onDeleteAccount = { activeDialog = ActiveDialog.ConfirmDeleteAccount }
                            )
                        }
                    }
                }

                is MoiUiState.ArtistSuccess -> {
                    val profile = state.profile
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).background(KaloyDarkBg),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item {
                            ProfileHeader(
                                displayName = profile.stageName.ifBlank { profile.email.substringBefore("@") },
                                photoUrl = profile.photoUrl,
                                emailVerificationStatus = profile.emailVerificationStatus,
                                phoneVerificationStatus = profile.phone?.let { profile.phoneVerificationStatus },
                                accountStatus = profile.accountStatus,
                                onEditPhoto = { activeDialog = ActiveDialog.EditPhoto }
                            )
                        }
                        item {
                            ArtistProfileContent(
                                profile = profile,
                                instrumentRoles = instrumentRoles,
                                onEditArtistProfile = { activeDialog = ActiveDialog.EditArtistProfile },
                                onRequestVerification = { viewModel.requestVerification() },
                                onChangeEmail = { activeDialog = ActiveDialog.ChangeEmail() },
                                onChangePhone = { activeDialog = ActiveDialog.ChangePhone() },
                                onAddMember = { activeDialog = ActiveDialog.AddMember },
                                onEditMember = { id ->
                                    profile.members.find { it.id == id }
                                        ?.let { activeDialog = ActiveDialog.EditMember(it) }
                                },
                                onChangeMemberStatus = { id ->
                                    profile.members.find { it.id == id }
                                        ?.let { activeDialog = ActiveDialog.ChangeMemberStatus(it) }
                                },
                                onDeleteMember = { id ->
                                    profile.members.find { it.id == id }
                                        ?.let { activeDialog = ActiveDialog.DeleteMember(it) }
                                },
                                onLogout = { activeDialog = ActiveDialog.ConfirmLogout },
                                onDeleteAccount = { activeDialog = ActiveDialog.ConfirmDeleteAccount }
                            )
                        }
                    }
                }
            }
        }

        // ── Dialogs ──────────────────────────────────────────────────────────

        val currentProfile = when (val s = uiState) {
            is MoiUiState.ClientSuccess -> s.profile
            else -> null
        }
        val artistProfile = when (val s = uiState) {
            is MoiUiState.ArtistSuccess -> s.profile
            else -> null
        }

        when (val d = activeDialog) {
            is ActiveDialog.EditPersonalInfo -> EditPersonalInfoDialog(
                currentFirstName = currentProfile?.firstName,
                currentLastName = currentProfile?.lastName,
                currentUserName = currentProfile?.userName,
                isLoading = isOperationLoading,
                errorMessage = operationError,
                onConfirm = { fn, ln, un -> viewModel.updatePersonalInfo(fn, ln, un) },
                onDismiss = { activeDialog = null; operationError = null }
            )

            is ActiveDialog.EditArtistProfile -> EditArtistProfileDialog(
                currentStageName = artistProfile?.stageName,
                currentBio = artistProfile?.bio,
                currentYear = artistProfile?.activeSinceYear,
                isLoading = isOperationLoading,
                errorMessage = operationError,
                onConfirm = { sn, bio, yr -> viewModel.updateArtistProfile(sn, bio, yr) },
                onDismiss = { activeDialog = null; operationError = null }
            )

            is ActiveDialog.EditPhoto -> EditPhotoDialog(
                currentPhotoUrl = currentProfile?.photoUrl ?: artistProfile?.photoUrl,
                isLoading = isOperationLoading,
                errorMessage = operationError,
                onConfirm = { url -> viewModel.updatePhoto(url) },
                onDismiss = { activeDialog = null; operationError = null }
            )

            is ActiveDialog.ChangeEmail -> ChangeEmailDialog(
                step = d.step,
                pendingEmail = d.pendingEmail,
                isLoading = isOperationLoading,
                errorMessage = operationError,
                onSendCode = { email ->
                    activeDialog = d.copy(pendingEmail = email)
                    viewModel.initiateEmailChange(email)
                },
                onConfirmCode = { code -> viewModel.confirmEmailChange(code) },
                onDismiss = { activeDialog = null; operationError = null }
            )

            is ActiveDialog.ChangePhone -> ChangePhoneDialog(
                step = d.step,
                pendingPhone = d.pendingPhone,
                isLoading = isOperationLoading,
                errorMessage = operationError,
                onSendCode = { phone ->
                    activeDialog = d.copy(pendingPhone = phone)
                    viewModel.initiatePhoneChange(phone)
                },
                onConfirmCode = { code -> viewModel.confirmPhoneChange(code) },
                onDismiss = { activeDialog = null; operationError = null }
            )

            is ActiveDialog.AddMember -> AddMemberDialog(
                instrumentRoles = instrumentRoles,
                isLoading = isOperationLoading,
                errorMessage = operationError,
                onConfirm = { name, roleId, photo -> viewModel.addGroupMember(name, roleId, photo) },
                onDismiss = { activeDialog = null; operationError = null }
            )

            is ActiveDialog.EditMember -> EditMemberDialog(
                member = d.member,
                instrumentRoles = instrumentRoles,
                isLoading = isOperationLoading,
                errorMessage = operationError,
                onConfirm = { name, roleId, photo -> viewModel.updateGroupMember(d.member.id, name, roleId, photo) },
                onDismiss = { activeDialog = null; operationError = null }
            )

            is ActiveDialog.ChangeMemberStatus -> MemberStatusDialog(
                member = d.member,
                isLoading = isOperationLoading,
                onConfirm = { statusId -> viewModel.updateMemberStatus(d.member.id, statusId) },
                onDismiss = { activeDialog = null }
            )

            is ActiveDialog.DeleteMember -> ConfirmDeleteMemberDialog(
                memberName = d.member.fullName,
                isLoading = isOperationLoading,
                onConfirm = { viewModel.deleteGroupMember(d.member.id) },
                onDismiss = { activeDialog = null }
            )

            is ActiveDialog.ConfirmLogout -> ConfirmLogoutDialog(
                onConfirm = { viewModel.logout() },
                onDismiss = { activeDialog = null }
            )

            is ActiveDialog.ConfirmDeleteAccount -> ConfirmDeleteAccountDialog(
                isLoading = isOperationLoading,
                onConfirm = { viewModel.deleteAccount() },
                onDismiss = { activeDialog = null }
            )

            null -> {}
        }
    }
}
