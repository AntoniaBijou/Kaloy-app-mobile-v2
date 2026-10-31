package com.kaloy.app.presentation.favoris

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.kaloy.app.ui.theme.KaloyPink

@Composable
fun BoutonFavori(
    isFavori: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFavori) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "favori_scale"
    )
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (isFavori) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isFavori) "Retirer des favoris" else "Ajouter aux favoris",
            tint = if (isFavori) KaloyPink else LocalContentColor.current,
            modifier = Modifier.scale(scale)
        )
    }
}
