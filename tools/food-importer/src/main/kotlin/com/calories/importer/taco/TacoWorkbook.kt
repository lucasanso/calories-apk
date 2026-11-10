package com.calories.importer.taco

import com.calories.importer.io.XlsxCell
import com.calories.importer.io.XlsxRow
import com.calories.importer.io.XlsxWorkbook
import java.nio.file.Path

/**
 * Colunas da planilha oficial da TACO 4ª ed. (`Taco-4a-Edicao.xlsx`, NEPA/UNICAMP) usadas pelo app,
 * com o número de casas decimais com que cada uma é publicada no PDF oficial (Tabelas 1 e 2).
 */
enum class TacoField(val sheet: String, val column: Int, val decimals: Int, val label: String) {
    MOISTURE(TacoWorkbook.SHEET_COMPOSITION, 2, 1, "Umidade (%)"),
    ENERGY_KCAL(TacoWorkbook.SHEET_COMPOSITION, 3, 0, "Energia (kcal)"),
    ENERGY_KJ(TacoWorkbook.SHEET_COMPOSITION, 4, 0, "Energia (kJ)"),
    PROTEIN(TacoWorkbook.SHEET_COMPOSITION, 5, 1, "Proteína (g)"),
    FAT(TacoWorkbook.SHEET_COMPOSITION, 6, 1, "Lipídeos (g)"),
    CARBOHYDRATE(TacoWorkbook.SHEET_COMPOSITION, 8, 1, "Carboidrato (g)"),
    FIBER(TacoWorkbook.SHEET_COMPOSITION, 9, 1, "Fibra alimentar (g)"),
    ASH(TacoWorkbook.SHEET_COMPOSITION, 10, 1, "Cinzas (g)"),
    SODIUM(TacoWorkbook.SHEET_COMPOSITION, 17, 0, "Sódio (mg)"),
    SATURATED(TacoWorkbook.SHEET_FATTY_ACIDS, 2, 1, "Ácidos graxos saturados (g)"),
}

data class TacoItem(
    val number: Int,
    /** Descrição exatamente como na planilha (sem normalizar espaços). */
    val rawDescription: String,
    val group: String,
    val composition: XlsxRow,
    /** Linha da Tabela 2 (ácidos graxos); `null` quando a TACO não analisou o item. */
    val fattyAcids: XlsxRow?,
) {
    fun cell(field: TacoField): XlsxCell? = when (field.sheet) {
        TacoWorkbook.SHEET_COMPOSITION -> composition[field.column]
        else -> fattyAcids?.get(field.column)
    }
}

class TacoWorkbook(val items: List<TacoItem>) {

    companion object {
        const val SHEET_COMPOSITION = "CMVCol taco3"
        const val SHEET_FATTY_ACIDS = "AGtaco3"
        const val EXPECTED_ITEMS = 597

        fun read(path: Path): TacoWorkbook {
            val workbook = XlsxWorkbook.read(path)
            val composition = workbook.sheet(SHEET_COMPOSITION)
            val fatty = workbook.sheet(SHEET_FATTY_ACIDS)
            checkHeaders(composition, fatty)

            val fattyByNumber = fatty.mapNotNull { row -> itemNumber(row)?.let { it to row } }.toMap()
            val items = ArrayList<TacoItem>()
            var group: String? = null
            for (row in composition.drop(3)) {
                val number = itemNumber(row)
                if (number == null) {
                    val label = row.text(0)?.trim()
                    if (!label.isNullOrEmpty() && row[1] == null && !label.startsWith("Número") && label != "Alimento") {
                        group = label
                    }
                    continue
                }
                val description = row.text(1) ?: throw IllegalArgumentException("TACO item $number sem descrição")
                items += TacoItem(number, description, group ?: error("TACO item $number antes do primeiro grupo"), row, fattyByNumber[number])
            }

            val numbers = items.map { it.number }
            require(numbers == (1..EXPECTED_ITEMS).toList()) {
                "TACO: esperados itens 1..$EXPECTED_ITEMS em ordem; encontrados ${numbers.size} (primeiro ${numbers.firstOrNull()}, último ${numbers.lastOrNull()})"
            }
            val orphanFatty = fattyByNumber.keys - numbers.toSet()
            require(orphanFatty.isEmpty()) { "TACO: linhas de ácidos graxos sem item correspondente: $orphanFatty" }
            return TacoWorkbook(items)
        }

        private fun itemNumber(row: XlsxRow): Int? {
            val cell = row[0] as? XlsxCell.Number ?: return null
            return cell.raw.toBigDecimal().intValueExact()
        }

        /** Garante que as colunas estão onde o importador espera (protege contra mudança de layout). */
        private fun checkHeaders(composition: List<XlsxRow>, fatty: List<XlsxRow>) {
            fun header(rows: List<XlsxRow>, line: Int, col: Int) = rows[line].text(col)?.trim()
            val expectations = listOf(
                Triple(composition, 1 to 2, "Umidade"), Triple(composition, 1 to 3, "Energia"),
                Triple(composition, 2 to 3, "(kcal)"), Triple(composition, 2 to 4, "(kJ)"),
                Triple(composition, 1 to 5, "Proteína"), Triple(composition, 1 to 6, "Lipídeos"),
                Triple(composition, 0 to 8, "Carbo-"), Triple(composition, 1 to 8, "idrato"),
                Triple(composition, 0 to 9, "Fibra"), Triple(composition, 1 to 9, "Alimentar"),
                Triple(composition, 1 to 10, "Cinzas"), Triple(composition, 1 to 17, "Sódio"),
                Triple(composition, 2 to 17, "(mg)"),
                Triple(fatty, 0 to 2, "Sa-"), Triple(fatty, 1 to 2, "turados"), Triple(fatty, 2 to 2, "(g)"),
            )
            for ((rows, pos, expected) in expectations) {
                val actual = header(rows, pos.first, pos.second)
                require(actual == expected) {
                    "TACO: cabeçalho inesperado na linha ${pos.first + 1}, coluna ${pos.second + 1}: '$actual' (esperado '$expected')"
                }
            }
        }
    }
}
