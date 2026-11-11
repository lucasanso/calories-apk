package com.calories

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.domain.model.ThemeMode
import com.calories.navigation.CaloriesNavHost
import com.calories.ui.theme.CaloriesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val library by viewModel.libraryState.collectAsStateWithLifecycle()
            CaloriesTheme(themeMode = settings?.themeMode ?: ThemeMode.SYSTEM, dynamicColor = settings?.dynamicColor ?: false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (val state = library) {
                        LibraryState.Loading -> CenteredMessage("Preparando a biblioteca de alimentos…", progress = true)
                        is LibraryState.Failed -> CenteredMessage("Não foi possível preparar a biblioteca.\n${state.message}", retry = viewModel::loadLibrary)
                        is LibraryState.Ready -> CaloriesNavHost()
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, progress: Boolean = false, retry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress) CircularProgressIndicator()
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        retry?.let { Button(onClick = it) { Text("Tentar novamente") } }
    }
}
