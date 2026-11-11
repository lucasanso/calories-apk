package com.calories.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.calories.domain.model.AppSettings
import com.calories.domain.model.ThemeMode
import com.calories.domain.repository.SettingsRepository
import com.calories.ui.components.InfoNote
import com.calories.ui.components.SectionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setDynamic(enabled: Boolean) = viewModelScope.launch { repo.setDynamicColor(enabled) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onGoals: () -> Unit,
    onMealSlots: () -> Unit,
    onSources: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Configurações") }) }, contentWindowInsets = WindowInsets(0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            SectionCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                NavItem(Icons.Outlined.Flag, "Metas nutricionais", "Calorias, macronutrientes e fibras; calculadora de estimativa", onGoals)
                HorizontalDivider()
                NavItem(Icons.Outlined.Restaurant, "Refeições do dia", "Nomes, horários e ordem", onMealSlots)
                HorizontalDivider()
                NavItem(Icons.Outlined.MenuBook, "Fontes e licenças", "TACO, USDA e versão da biblioteca", onSources)
            }
            SectionCard {
                Text("Aparência", style = MaterialTheme.typography.titleMedium)
                listOf(ThemeMode.SYSTEM to "Seguir o sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Escuro").forEach { (mode, label) ->
                    Row(Modifier.fillMaxWidth().selectable(settings.themeMode == mode, role = Role.RadioButton) { viewModel.setTheme(mode) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = settings.themeMode == mode, onClick = null)
                        Text(label, Modifier.padding(start = 12.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { viewModel.setDynamic(!settings.dynamicColor) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cores do papel de parede")
                        InfoNote("Material You (Android 12 ou superior)")
                    }
                    Switch(checked = settings.dynamicColor, onCheckedChange = viewModel::setDynamic)
                }
            }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Text("Privacidade", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                }
                InfoNote(
                    "O Calories funciona sem internet: o app não tem permissão de acesso à rede, não usa conta, anúncios nem serviços de terceiros. " +
                        "Seus dados ficam só neste aparelho e não são enviados ao backup em nuvem do Android.",
                    Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurface,
                )
                InfoNote("As estimativas do app são aproximações e não substituem orientação de nutricionista ou médico.", Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
