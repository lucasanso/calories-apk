package com.calories.feature.shopping

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.domain.model.ShoppingItem
import com.calories.feature.planning.ConfirmDialog
import com.calories.ui.UiEvent
import com.calories.ui.components.EmptyState
import com.calories.ui.components.InfoNote
import com.calories.ui.components.NumberField
import com.calories.ui.components.SectionCard
import com.calories.ui.format.Fmt
import com.calories.ui.theme.TabularNumbers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListRoute(onBack: () -> Unit, viewModel: ShoppingListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is UiEvent.Message -> launch { snackbar.showSnackbar(e.text) }
                UiEvent.Close -> onBack()
                else -> Unit
            }
        }
    }
    val list = state?.list

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(list?.name ?: "", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        list?.let { l -> Text("${l.items.count { it.isChecked }} de ${l.items.size} comprados", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                actions = {
                    IconButton(onClick = {
                        list?.let { l ->
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, viewModel.shareText(l))
                            context.startActivity(Intent.createChooser(send, "Compartilhar lista"))
                        }
                    }, enabled = list != null) { Icon(Icons.Outlined.Share, contentDescription = "Compartilhar lista") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções da lista") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Limpar comprados") }, enabled = list?.items?.any { it.isChecked } == true, onClick = { menu = false; viewModel.clearChecked() })
                            DropdownMenuItem(text = { Text("Excluir lista") }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Item") },
                modifier = Modifier.semantics { contentDescription = "Adicionar item" },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val l = list ?: return@Scaffold
        if (l.items.isEmpty()) {
            EmptyState("Lista vazia", "Adicione itens com o botão “Item”.", Modifier.padding(padding))
            return@Scaffold
        }
        val (pending, done) = l.items.partition { !it.isChecked }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pending.isNotEmpty()) item(key = "pending") {
                ItemsCard(pending, onToggle = viewModel::toggle, onQuantity = { editing = it }, onDelete = viewModel::delete)
            }
            if (done.isNotEmpty()) {
                item(key = "done-title") { Text("Comprados", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item(key = "done") { ItemsCard(done, onToggle = viewModel::toggle, onQuantity = { editing = it }, onDelete = viewModel::delete) }
            }
            item(key = "note") { InfoNote("Quantidades geradas do planejamento podem ser editadas. Alimentos preparados estão em peso pronto.") }
        }
    }

    if (adding) AddItemDialog(onAdd = { n, q, u -> viewModel.addManual(n, q, u); adding = false }, onDismiss = { adding = false })
    editing?.let { item ->
        QuantityDialog(item, onSave = { viewModel.setQuantity(item, it); editing = null }, onDismiss = { editing = null })
    }
    if (confirmDelete) ConfirmDialog(
        title = "Excluir a lista?", text = "A lista “${list?.name}” e seus itens serão apagados. O planejamento não muda.",
        confirm = "Excluir", destructive = true,
        onConfirm = { confirmDelete = false; viewModel.deleteList() }, onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun ItemsCard(items: List<ShoppingItem>, onToggle: (ShoppingItem) -> Unit, onQuantity: (ShoppingItem) -> Unit, onDelete: (ShoppingItem) -> Unit) {
    SectionCard(padding = PaddingValues(vertical = 4.dp)) {
        items.forEach { i ->
            Row(Modifier.fillMaxWidth().clickable { onToggle(i) }.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = i.isChecked, onCheckedChange = { onToggle(i) })
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        i.name, style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (i.isChecked) TextDecoration.LineThrough else null,
                        color = if (i.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    i.detail?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                TextButton(onClick = { onQuantity(i) }) {
                    Text(Fmt.quantity(i.quantity, i.unit).ifEmpty { "qtd." }, style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers))
                }
                IconButton(onClick = { onDelete(i) }) { Icon(Icons.Outlined.Close, contentDescription = "Remover ${i.name}") }
            }
        }
    }
}

@Composable
private fun QuantityDialog(item: ShoppingItem, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(Fmt.input(item.quantity)) }
    val value = Fmt.parse(text)
    val valid = text.isBlank() || (value != null && value > 0 && value <= 100_000)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = { NumberField(text, { text = it }, "Quantidade", suffix = item.unit, isError = !valid, supporting = "Deixe vazio para não indicar quantidade.") },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = valid) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun AddItemDialog(onAdd: (String, Double?, String?) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    val q = Fmt.parse(quantity)
    val validQuantity = quantity.isBlank() || (q != null && q > 0 && q <= 100_000)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Item") }, placeholder = { Text("Detergente, café em pó…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(quantity, { quantity = it }, "Quantidade", Modifier.weight(1f), isError = !validQuantity)
                    OutlinedTextField(unit, { unit = it.take(12) }, label = { Text("Unidade") }, placeholder = { Text("g, un, pct") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, q, unit) }, enabled = name.isNotBlank() && validQuantity) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
