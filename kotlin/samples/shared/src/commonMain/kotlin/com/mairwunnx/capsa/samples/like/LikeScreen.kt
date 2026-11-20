package com.mairwunnx.capsa.samples.like

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.compose.CapsaComposable
import com.mairwunnx.capsa.compose.CapsaScope
import com.mairwunnx.capsa.compose.CollectEffects
import com.mairwunnx.capsa.compose.rememberCapsa
import com.mairwunnx.capsa.effectHandlers
import com.mairwunnx.capsa.mapState
import com.mairwunnx.capsa.samples.SampleScaffold
import com.mairwunnx.capsa.samples.backend.Backend
import com.mairwunnx.capsa.samples.backend.LikeServer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The red of a liked heart, the same in the light and the dark theme. */
private val LikeRed = Color(0xFFE53935)

data class LikeUi(val liked: Boolean, val label: String)

fun LikeSlice.toUi() = LikeUi(liked = liked, label = if (likes == 1) "1 like" else "$likes likes")

/** The host of the like screen: a class in the composition, without a view model. */
class LikeCapsule(scope: CapsaScope, server: LikeServer) : CapsaComposable(scope) {
    private val like = install(likes(LikePorts(server::setLiked), likes = server.likes))
    val ui: StateFlow<LikeUi> = like.mapState(capsaScope) { it.toUi() }
}

@Composable
fun LikeScreen(onBack: () -> Unit) {
    val like = rememberCapsa { LikeCapsule(this, Backend.likes) }
    val ui by like.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    CollectEffects(
        like.effects,
        effectHandlers {
            on<ShowLikeError> { scope.launch { snackbar.showSnackbar("Couldn’t save the like") } }
        },
    )
    SampleScaffold("Like", onBack, snackbar) {
        LikeContent(ui, onIntent = like::send)
        ServerSwitch()
    }
}

@Composable
private fun LikeContent(ui: LikeUi, onIntent: (Intent) -> Unit) {
    FilledTonalIconToggleButton(
        checked = ui.liked,
        onCheckedChange = { onIntent(LikeClicked) },
        modifier = Modifier.size(96.dp),
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            checkedContainerColor = LikeRed.copy(alpha = 0.16f),
            checkedContentColor = LikeRed,
        ),
    ) {
        Icon(
            if (ui.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = "Like",
            modifier = Modifier.size(48.dp),
        )
    }
    Text(ui.label, style = MaterialTheme.typography.headlineMedium)
}

/** Not part of the sample: turns the fake server off, so you can watch the retries and the rollback. */
@Composable
private fun ServerSwitch() {
    val online by Backend.likes.online.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("The server answers", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = online, onCheckedChange = { Backend.likes.online.value = it })
    }
}
