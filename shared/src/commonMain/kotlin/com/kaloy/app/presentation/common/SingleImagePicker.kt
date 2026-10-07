package com.kaloy.app.presentation.common

import androidx.compose.runtime.Composable

@Composable
expect fun rememberSingleImagePicker(
    onImageSelected: (String?) -> Unit
): () -> Unit

@Composable
expect fun rememberSingleImagePickerWithBytes(
    onImageSelected: (uri: String?, bytes: ByteArray?) -> Unit
): () -> Unit
