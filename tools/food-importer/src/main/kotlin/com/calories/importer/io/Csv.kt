package com.calories.importer.io

import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Leitor CSV (RFC 4180): aspas, aspas escapadas ("") e quebras de linha dentro de campos.
 * Lê em streaming para suportar arquivos grandes (ex.: food_nutrient.csv do USDA, ~36 MB).
 */
class CsvReader(private val reader: Reader) {

    fun forEachRecord(action: (List<String>) -> Unit) {
        val field = StringBuilder()
        val record = ArrayList<String>()
        var inQuotes = false
        var fieldStarted = false
        var c = reader.read()
        while (c != -1) {
            val ch = c.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    val next = reader.read()
                    if (next == '"'.code) {
                        field.append('"')
                    } else {
                        inQuotes = false
                        c = next
                        continue
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> if (field.isEmpty()) { inQuotes = true; fieldStarted = true } else field.append(ch)
                    ',' -> { record += field.toString(); field.setLength(0); fieldStarted = true }
                    '\r' -> Unit
                    '\n' -> {
                        record += field.toString(); field.setLength(0)
                        if (!(record.size == 1 && record[0].isEmpty() && !fieldStarted)) action(record.toList())
                        record.clear(); fieldStarted = false
                    }
                    else -> { field.append(ch); fieldStarted = true }
                }
            }
            c = reader.read()
        }
        if (field.isNotEmpty() || record.isNotEmpty() || fieldStarted) {
            record += field.toString()
            action(record.toList())
        }
    }

    fun readAll(): List<List<String>> = buildList { forEachRecord { add(it) } }
}

/** Tabela CSV com cabeçalho; acesso por nome de coluna. */
class CsvTable(val header: List<String>, val rows: List<List<String>>, val origin: String) {
    private val index = header.withIndex().associate { (i, h) -> h.trim() to i }

    fun requireColumns(vararg names: String) {
        val missing = names.filter { it !in index }
        require(missing.isEmpty()) { "$origin: colunas ausentes $missing (tem ${index.keys})" }
    }

    operator fun List<String>.get(column: String): String {
        val i = index[column] ?: throw IllegalArgumentException("$origin: coluna '$column' não existe")
        return getOrElse(i) { "" }.trim()
    }

    companion object {
        /** Lê um CSV de curadoria: ignora linhas vazias e comentários iniciados por `#`. */
        fun readCuration(path: Path): CsvTable {
            val text = Files.readString(path, Charsets.UTF_8).removePrefix("﻿")
            val withoutComments = text.lineSequence().filterNot { it.trimStart().startsWith("#") || it.isBlank() }.joinToString("\n")
            val records = CsvReader(withoutComments.reader()).readAll()
            require(records.isNotEmpty()) { "$path: arquivo vazio" }
            return CsvTable(records.first(), records.drop(1), path.fileName.toString())
        }
    }
}
