package com.calories.data.backup

import java.math.BigDecimal

/** Escritor CSV (RFC 4180). [excelPtBr]: separador ";" e vírgula decimal, como o Excel em português espera. */
class CsvWriter(private val excelPtBr: Boolean) {
    private val separator = if (excelPtBr) ';' else ','
    private val out = StringBuilder("\uFEFF") // BOM: acentos corretos ao abrir no Excel

    fun row(values: List<Any?>): CsvWriter {
        values.forEachIndexed { i, v ->
            if (i > 0) out.append(separator)
            out.append(escape(format(v)))
        }
        out.append("\r\n")
        return this
    }

    private fun format(v: Any?): String = when (v) {
        null -> ""
        is Double -> BigDecimal(v.toString()).stripTrailingZeros().toPlainString().let { if (excelPtBr) it.replace('.', ',') else it }
        else -> v.toString()
    }

    private fun escape(text: String): String =
        if (text.any { it == separator || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text

    override fun toString(): String = out.toString()
}
