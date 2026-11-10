package com.calories.importer.taco

import com.calories.importer.model.normalizeSpaces
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.nio.file.Path
import kotlin.math.abs

/**
 * Checagem cruzada entre os dois arquivos oficiais da TACO: os valores publicados derivados
 * da planilha precisam ser idênticos aos impressos no PDF (Tabelas 1 e 2).
 *
 * Cada palavra do PDF é lida com sua coordenada x e atribuída à coluna cujo rótulo de unidade
 * ("(%)", "(kcal)", "(mg)"…) está mais próximo. Assim células vazias não deslocam valores.
 */
class TacoPdfCrossCheck(private val pdf: Path) {

    data class Mismatch(val number: Int, val field: String, val expected: String, val pdf: String, val page: Int)
    data class Uncomparable(val number: Int, val block: String, val reason: String, val page: Int)
    data class DescriptionDiff(val number: Int, val workbook: String, val pdf: String, val page: Int)

    data class Result(
        val comparedValues: Map<String, Int>,
        val mismatches: List<Mismatch>,
        val uncomparable: List<Uncomparable>,
        val descriptionDiffs: List<DescriptionDiff>,
        val rowsFound: Map<String, Int>,
    ) {
        val totalCompared: Int get() = comparedValues.values.sum()
    }

    private data class Word(val text: String, val x0: Float, val x1: Float) {
        val center: Float get() = (x0 + x1) / 2
    }

    private data class Line(val page: Int, val words: List<Word>) {
        val text: String get() = words.joinToString(" ") { it.text }
    }

    private enum class Block(val label: String, val columns: List<TacoField?>, val hasDescription: Boolean) {
        COMPOSITION(
            "Tabela 1 (composição)",
            listOf(
                TacoField.MOISTURE, TacoField.ENERGY_KCAL, TacoField.ENERGY_KJ, TacoField.PROTEIN, TacoField.FAT,
                null /* colesterol */, TacoField.CARBOHYDRATE, TacoField.FIBER, TacoField.ASH, null /* Ca */, null, /* Mg */
            ),
            true,
        ),
        MINERALS(
            "Tabela 1 (minerais)",
            listOf(null /* Mn */, null /* P */, null /* Fe */, TacoField.SODIUM) + List(11) { null },
            false,
        ),
        FATTY_ACIDS("Tabela 2 (ácidos graxos)", listOf(TacoField.SATURATED) + List(10) { null }, true),
    }

    fun run(items: List<NormalizedTacoItem>): Result {
        val byNumber = items.associateBy { it.item.number }
        val compared = linkedMapOf<String, Int>()
        val mismatches = ArrayList<Mismatch>()
        val uncomparable = ArrayList<Uncomparable>()
        val descriptionDiffs = ArrayList<DescriptionDiff>()
        val found = linkedMapOf<String, Int>()
        val seen = HashSet<Pair<Block, Int>>()

        for ((page, lines) in extractLines().groupBy { it.page }.toSortedMap()) {
            val block = blockOf(lines) ?: continue
            val units = unitColumns(lines) ?: continue
            for (line in lines) {
                val first = line.words.firstOrNull()?.text ?: continue
                val n = first.toIntOrNull() ?: continue
                val item = byNumber[n] ?: continue
                if (line.words.size < 2) continue
                val rest = line.words.drop(1)

                val boundary = units.first() - (units[1] - units[0]) / 2
                val (descWords, valueWords) = if (block.hasDescription) rest.partition { it.center < boundary } else emptyList<Word>() to rest
                if (block.hasDescription && descWords.isEmpty()) continue
                if (!block.hasDescription && descWords.isNotEmpty()) continue
                if (!seen.add(block to n)) continue
                found.merge(block.label, 1, Int::plus)

                if (block == Block.COMPOSITION) {
                    val pdfDescription = normalizeSpaces(descWords.joinToString(" ") { it.text })
                    val workbookDescription = normalizeSpaces(item.item.rawDescription)
                    if (pdfDescription != workbookDescription) descriptionDiffs += DescriptionDiff(n, workbookDescription, pdfDescription, page)
                }

                // Atribui cada valor à coluna de unidade mais próxima.
                val cells = arrayOfNulls<String>(units.size)
                var collision = false
                for (w in valueWords) {
                    val col = units.indices.minBy { abs(units[it] - w.center) }
                    if (cells[col] != null) collision = true else cells[col] = w.text
                }
                val requiredColumns = block.columns.indexOfLast { it != null } + 1
                if (collision || units.size < requiredColumns) {
                    uncomparable += Uncomparable(n, block.label, if (collision) "duas palavras na mesma coluna" else "cabeçalho com ${units.size} colunas", page)
                    continue
                }
                block.columns.forEachIndexed { i, field ->
                    if (field == null) return@forEachIndexed
                    val expected = item.values.getValue(field).pdfText()
                    val pdfValue = cells[i] ?: ""
                    compared.merge(field.label, 1, Int::plus)
                    if (expected != pdfValue) mismatches += Mismatch(n, field.label, expected, pdfValue, page)
                }
            }
        }
        return Result(compared, mismatches, uncomparable.sortedBy { it.number }, descriptionDiffs.sortedBy { it.number }, found)
    }

    private fun blockOf(lines: List<Line>): Block? {
        val text = lines.joinToString("\n") { it.text }
        return when {
            "Tabela 1. Composição" in text && "Umidade" in text -> Block.COMPOSITION
            "Tabela 1. Composição" in text && "Manganês" in text -> Block.MINERALS
            "Tabela 2. Composição" in text && "turados" in text -> Block.FATTY_ACIDS
            else -> null
        }
    }

    /** Centros x dos rótulos de unidade da linha de cabeçalho "Alimento … (%) (kcal) …". */
    private fun unitColumns(lines: List<Line>): List<Float>? = lines
        .firstOrNull { line -> line.words.firstOrNull()?.text == "Alimento" && line.words.count { it.text.isUnit() } >= 3 }
        ?.words?.filter { it.text.isUnit() }?.map { it.center }

    private fun String.isUnit() = startsWith("(") && endsWith(")")

    private fun extractLines(): List<Line> = Loader.loadPDF(pdf.toFile()).use { doc ->
        val stripper = WordStripper()
        (1..doc.numberOfPages).flatMap { page ->
            stripper.startPage = page
            stripper.endPage = page
            stripper.collect(doc).map { Line(page, it) }
        }
    }

    /** Captura cada palavra com sua posição horizontal, agrupada por linha. */
    private class WordStripper : PDFTextStripper() {
        private val lines = ArrayList<List<Word>>()
        private var current = ArrayList<Word>()

        init {
            sortByPosition = true
        }

        fun collect(doc: org.apache.pdfbox.pdmodel.PDDocument): List<List<Word>> {
            lines.clear(); current = ArrayList()
            getText(doc)
            flush()
            return lines.toList()
        }

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            if (text.isBlank() || textPositions.isEmpty()) return
            // Uma "palavra" do PDFBox pode conter espaços; separa mantendo as posições de cada parte.
            var start = 0
            while (start < text.length) {
                while (start < text.length && text[start].isWhitespace()) start++
                if (start >= text.length) break
                var end = start
                while (end < text.length && !text[end].isWhitespace()) end++
                val first = textPositions[minOf(start, textPositions.lastIndex)]
                val last = textPositions[minOf(end - 1, textPositions.lastIndex)]
                current += Word(text.substring(start, end), first.xDirAdj, last.xDirAdj + last.widthDirAdj)
                start = end
            }
        }

        override fun writeLineSeparator() = flush()

        private fun flush() {
            if (current.isNotEmpty()) { lines += current; current = ArrayList() }
        }
    }
}
