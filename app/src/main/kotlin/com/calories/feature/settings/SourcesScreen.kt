package com.calories.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.FoodSource
import com.calories.domain.model.LibraryStatus
import com.calories.domain.repository.FoodRepository
import com.calories.domain.repository.LibraryRepository
import com.calories.ui.components.InfoNote
import com.calories.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(foods: FoodRepository, library: LibraryRepository) : ViewModel() {
    val sources: StateFlow<List<FoodSource>> = foods.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val status = MutableStateFlow<LibraryStatus?>(null)
    init { viewModelScope.launch { status.value = runCatching { library.ensureLibrary() }.getOrNull() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesRoute(onBack: () -> Unit, viewModel: SourcesViewModel = hiltViewModel()) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Fontes e licenças") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } })
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            status?.let { InfoNote("Biblioteca nutricional versão ${it.installedVersion} · ${it.foodCount} alimentos.") }
            sources.filter { it.code != "USER" }.forEach { s ->
                SectionCard {
                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                    s.edition?.let { InfoNote(it) }
                    Text("Licença", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    InfoNote(s.license, color = MaterialTheme.colorScheme.onSurface)
                    Text("Como citar", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    InfoNote(s.citation, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            SectionCard {
                Text("Como os dados foram preparados", style = MaterialTheme.typography.titleMedium)
                InfoNote(
                    "Os valores da TACO vêm da planilha oficial do NEPA/UNICAMP, arredondados como na publicação e conferidos contra o PDF oficial. " +
                        "Itens que não existem na TACO (tilápia, cottage, macarrão cozido, pasta de amendoim, whey, chocolate amargo, iogurte grego, granola) " +
                        "vêm do USDA FoodData Central e são marcados como referência internacional. " +
                        "Nenhum valor foi estimado: quando a fonte não traz um nutriente, o app mostra “—”.",
                    color = MaterialTheme.colorScheme.onSurface,
                )
                InfoNote("A TBCA (USP) não foi incorporada porque seu termo de uso proíbe a reprodução do material. Você pode cadastrar alimentos dela como seus, citando a fonte.", Modifier.padding(top = 8.dp))
            }
        }
    }
}
