package com.calories.importer

import com.calories.library.format.LibraryAssetCodec
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Uso:
 *   food-importer --data-dir nutrition-data --output <asset.json> --report <relatorio.md> [--check]
 *
 * `--check` não grava nada: falha se o asset no disco for diferente do que seria gerado.
 */
// Referência forte: o java.util.logging descarta loggers sem referência e perderia o nível.
private val pdfBoxLogger = java.util.logging.Logger.getLogger("org.apache.pdfbox").apply { level = java.util.logging.Level.SEVERE }

fun main(args: Array<String>) {
    pdfBoxLogger.level = java.util.logging.Level.SEVERE
    System.setOut(PrintStream(System.out, true, Charsets.UTF_8))
    val options = parseArgs(args)
    val dataDir = Path.of(options.getValue("data-dir"))
    val output = Path.of(options.getValue("output"))
    val reportPath = Path.of(options.getValue("report"))
    val check = "check" in options

    val existingText = if (Files.exists(output)) Files.readString(output) else null
    val previous = existingText?.let { runCatching { LibraryAssetCodec.decode(it) }.getOrNull() }

    val result = try {
        ImportPipeline(dataDir).run(previous)
    } catch (e: ImportPipeline.IntegrityException) {
        System.err.println("Falha de integridade dos arquivos de origem:\n${e.message}")
        exitProcess(2)
    }

    val errors = result.errors
    val warnings = result.issues.size - errors.size
    println("Alimentos: ${result.asset.foods.size} | erros: ${errors.size} | avisos: $warnings")
    println("Checagem TACO planilha×PDF: ${result.crossCheck.totalCompared} valores comparados, ${result.crossCheck.mismatches.size} divergências")
    errors.forEach { println("ERRO ${it.code} ${it.key ?: ""} ${it.message}") }

    if (check) {
        when {
            errors.isNotEmpty() -> exitProcess(1)
            existingText != result.assetText -> {
                println("O asset em $output está desatualizado. Rode ./gradlew :tools:food-importer:generateFoodLibrary")
                exitProcess(1)
            }
            else -> println("OK: asset idêntico ao gerado.")
        }
        return
    }

    Files.createDirectories(reportPath.toAbsolutePath().parent)
    Files.writeString(reportPath, result.reportText)
    if (errors.isNotEmpty()) {
        println("Asset NÃO gravado por causa de erros. Detalhes em $reportPath")
        exitProcess(1)
    }
    Files.createDirectories(output.toAbsolutePath().parent)
    Files.writeString(output, result.assetText)
    println("Gravado: $output e $reportPath")
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val out = HashMap<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        require(a.startsWith("--")) { "Argumento inesperado: $a" }
        val name = a.removePrefix("--")
        if (name == "check") { out[name] = "true"; i++; continue }
        require(i + 1 < args.size) { "Falta valor para $a" }
        out[name] = args[i + 1]
        i += 2
    }
    for (required in listOf("data-dir", "output", "report")) require(required in out) { "Argumento obrigatório: --$required" }
    return out
}
