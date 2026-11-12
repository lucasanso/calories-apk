package com.calories.ui

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Leitura e escrita de arquivos escolhidos pelo usuário no seletor do sistema (sem permissão de armazenamento). */
class DocumentIo @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Não foi possível gravar o arquivo")
    }

    /** Lê o arquivo inteiro como UTF-8 (sem BOM), recusando arquivos maiores que [maxBytes]. */
    suspend fun read(uri: Uri, maxBytes: Int = 50 * 1024 * 1024): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Não foi possível ler o arquivo")
        input.use {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = it.read(buffer)
                if (n < 0) break
                require(out.size() + n <= maxBytes) { "Arquivo grande demais" }
                out.write(buffer, 0, n)
            }
            out.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
        }
    }
}
