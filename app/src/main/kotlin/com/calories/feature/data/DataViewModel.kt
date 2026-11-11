package com.calories.feature.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calories.domain.repository.BackupCheck
import com.calories.domain.repository.BackupRepository
import com.calories.ui.DocumentIo
import com.calories.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Backup lido e validado, aguardando a confirmação do usuário para restaurar. */
data class PendingRestore(val text: String, val check: BackupCheck)

enum class CsvPeriod(val label: String) { ALL("Todo o histórico"), LAST_30("Últimos 30 dias"), THIS_YEAR("Este ano") }

@HiltViewModel
class DataViewModel @Inject constructor(
    private val backup: BackupRepository,
    private val io: DocumentIo,
    private val clock: Clock,
) : ViewModel() {
    val today: LocalDate get() = LocalDate.now(clock)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _pending = MutableStateFlow<PendingRestore?>(null)
    val pending: StateFlow<PendingRestore?> = _pending.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun fileName(kind: String, extension: String) = "calories-$kind-$today.$extension"

    private fun work(failure: String, block: suspend () -> String?) = viewModelScope.launch {
        _busy.value = true
        runCatching { block() }
            .onSuccess { it?.let { msg -> _events.send(UiEvent.Message(msg)) } }
            .onFailure { _events.send(UiEvent.Message(it.message?.let { m -> "$failure: $m" } ?: failure)) }
        _busy.value = false
    }

    fun exportCsv(uri: Uri, period: CsvPeriod, excelPtBr: Boolean) = work("Não foi possível exportar") {
        val from = when (period) {
            CsvPeriod.ALL -> null
            CsvPeriod.LAST_30 -> today.minusDays(29)
            CsvPeriod.THIS_YEAR -> today.withDayOfYear(1)
        }
        io.write(uri, backup.exportDiaryCsv(from, if (from == null) null else today, excelPtBr))
        "Histórico exportado em CSV"
    }

    fun exportBackup(uri: Uri) = work("Não foi possível criar o backup") {
        io.write(uri, backup.exportBackup(clock.instant()))
        "Backup salvo. Guarde o arquivo fora do celular também."
    }

    fun exportPlans(uri: Uri) = work("Não foi possível exportar os planos") {
        io.write(uri, backup.exportPlans(clock.instant()))
        "Planos exportados"
    }

    /** Lê e valida o arquivo; só restaura depois de [confirmRestore]. */
    fun inspectBackup(uri: Uri) = work("Não foi possível ler o arquivo") {
        val text = io.read(uri)
        val check = backup.check(text)
        if (check.isValid) { _pending.value = PendingRestore(text, check); null }
        else "Backup recusado: ${check.errors.first()}" + if (check.errors.size > 1) " (+${check.errors.size - 1} problema(s))" else ""
    }

    fun confirmRestore() {
        val p = _pending.value ?: return
        _pending.value = null
        work("Não foi possível restaurar") {
            val result = backup.restore(p.text)
            if (result.isValid) "Backup restaurado" else "Backup recusado: ${result.errors.first()}"
        }
    }

    fun cancelRestore() { _pending.value = null }

    fun importPlans(uri: Uri) = work("Não foi possível importar") {
        val n = backup.importPlans(io.read(uri))
        "$n plano(s) importado(s)"
    }
}
