// From the README of Capsa, word for word: https://github.com/MairwunNx/Capsa
// When this stops compiling or passing, the README needs the same change.
package com.mairwunnx.capsa.samples.readme

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mairwunnx.capsa.compose.rememberCapsa

@Composable
fun CounterScreen() {
    val screen = rememberCapsa { expose(install(counter())) }
    val state by screen.state.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { screen.send(MinusClicked) }) { Text("−") }
        Text("${state.count}")
        Button(onClick = { screen.send(PlusClicked) }) { Text("+") }
    }
}
