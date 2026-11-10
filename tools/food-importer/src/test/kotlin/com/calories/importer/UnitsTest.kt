package com.calories.importer

import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.Preparation
import com.calories.importer.classify.PreparationClassifier
import com.calories.importer.io.CsvReader
import com.calories.importer.io.XlsxCell
import com.calories.importer.io.XlsxWorkbook
import com.calories.importer.taco.TacoValue
import com.calories.importer.validate.LibraryValidator
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CsvReaderTest {
    @Test
    fun `aspas, aspas escapadas e quebra de linha dentro do campo`() {
        val text = "a,b,c\n\"x, y\",\"diz \"\"oi\"\"\",\"linha1\nlinha2\"\n\n1,,3\r\n"
        val rows = CsvReader(text.reader()).readAll()
        assertEquals(listOf(listOf("a", "b", "c"), listOf("x, y", "diz \"oi\"", "linha1\nlinha2"), listOf("1", "", "3")), rows)
    }

    @Test
    fun `ultima linha sem quebra de linha e lida`() {
        assertEquals(listOf(listOf("1", "2")), CsvReader("1,2".reader()).readAll())
    }
}

class XlsxReaderTest {
    @Test
    fun `referencia de celula vira indice de coluna`() {
        assertEquals(0, XlsxWorkbook.columnIndex("A1"))
        assertEquals(25, XlsxWorkbook.columnIndex("Z10"))
        assertEquals(26, XlsxWorkbook.columnIndex("AA3"))
        assertEquals(17, XlsxWorkbook.columnIndex("R597"))
    }
}

class TacoValueTest {
    private fun num(raw: String, decimals: Int) = TacoValue.parse(XlsxCell.Number(raw), decimals)

    @Test
    fun `arredonda como o PDF publicado`() {
        // Arroz integral cozido: planilha 123,53489250000001 kcal → PDF 124.
        assertEquals("124", num("123.53489250000001", 0).pdfText())
        assertEquals("2,6", num("2.58825", 1).pdfText())
        assertEquals("0,63", num("0.6273333333333333", 2).pdfText())
    }

    @Test
    fun `ruido de ponto flutuante nao muda o arredondamento`() {
        // Quibe cru (item 441): planilha 0,6499999999999999 g de saturada; o PDF publica 0,7.
        assertEquals("0,7", num("0.6499999999999999", 1).pdfText())
    }

    @Test
    fun `carboidrato levemente negativo e publicado como zero`() {
        val v = num("-0.026666666666666172", 1) as TacoValue.Number
        assertEquals("0,0", v.pdfText())
        assertTrue(v.raw.signum() < 0)
        assertEquals(NutrientValue.Measured(0.0), v.toNutrientValue())
    }

    @Test
    fun `simbolos da TACO`() {
        assertEquals(NutrientValue.Trace, TacoValue.parse(XlsxCell.Text("Tr"), 1).toNutrientValue())
        assertEquals(NutrientValue.NotApplicable, TacoValue.parse(XlsxCell.Text("NA"), 1).toNutrientValue())
        assertEquals(NutrientValue.NotAvailable, TacoValue.parse(XlsxCell.Text("*"), 1).toNutrientValue())
        assertEquals(NutrientValue.NotAvailable, TacoValue.parse(null, 1).toNutrientValue())
        assertEquals(NutrientValue.NotAvailable, TacoValue.parse(XlsxCell.Text("  "), 1).toNutrientValue())
    }

    @Test
    fun `simbolo desconhecido interrompe a importacao`() {
        assertFailsWith<IllegalArgumentException> { TacoValue.parse(XlsxCell.Text("ND"), 1) }
    }
}

class PreparationClassifierTest {
    @Test
    fun `detecta o preparo pelo ultimo segmento com palavra de preparo`() {
        assertEquals(Preparation.GRILLED, PreparationClassifier.detect("Frango, peito, sem pele, grelhado")?.preparation)
        assertEquals(Preparation.COOKED, PreparationClassifier.detect("Ovo, de galinha, inteiro, cozido/10minutos")?.preparation)
        assertEquals(Preparation.COOKED, PreparationClassifier.detect("Cuscuz, de milho, cozido com sal")?.preparation)
        assertEquals(Preparation.FRIED, PreparationClassifier.detect("Batata, frita, tipo chips, industrializada")?.preparation)
        assertEquals(Preparation.CANNED, PreparationClassifier.detect("Atum, conserva em óleo")?.preparation)
        assertEquals(Preparation.TOASTED, PreparationClassifier.detect("Café, pó, torrado")?.preparation)
        assertEquals(Preparation.DRIED, PreparationClassifier.detect("Leite, de vaca, integral, pó")?.preparation)
        assertEquals(Preparation.DRIED, PreparationClassifier.detect("Coentro, folhas desidratadas")?.preparation)
        assertEquals(Preparation.RAW, PreparationClassifier.detect("Castanha-do-Brasil, crua")?.preparation)
        assertNull(PreparationClassifier.detect("Pão, trigo, francês"))
    }

    @Test
    fun `palavra dentro de outra nao conta`() {
        // "Crustáceo" contém "cru", mas não é a palavra "cru".
        assertNull(PreparationClassifier.detect("Crustáceo, molho"))
    }

    @Test
    fun `nome base remove apenas o segmento de preparo inteiro`() {
        assertEquals("Frango, peito, sem pele", PreparationClassifier.baseName("Frango, peito, sem pele, grelhado"))
        assertEquals("Frango, peito, sem pele", PreparationClassifier.baseName("Frango, peito, sem pele, cru"))
        assertEquals("Ovo, de galinha, inteiro", PreparationClassifier.baseName("Ovo, de galinha, inteiro, cozido/10minutos"))
        assertEquals("Pão, trigo, francês", PreparationClassifier.baseName("Pão, trigo, francês"))
        // "conserva em óleo" é um segmento só de preparo: agrupa com "Sardinha, assada", "Sardinha, frita"...
        assertEquals("Sardinha", PreparationClassifier.baseName("Sardinha, conserva em óleo"))
        assertEquals("Sardinha", PreparationClassifier.baseName("Sardinha, frita"))
        // Segmento com outras palavras além do preparo não é removido.
        assertEquals("Batata, frita, tipo chips, industrializada", PreparationClassifier.baseName("Batata, frita, tipo chips, industrializada").let { it })
    }
}

class GoldenMatchTest {
    @Test
    fun `EXACT exige igualdade numerica`() {
        assertTrue(LibraryValidator.goldenMatches(BigDecimal("32.0"), BigDecimal("32"), "EXACT"))
        assertFalse(LibraryValidator.goldenMatches(BigDecimal("32.01"), BigDecimal("32"), "EXACT"))
    }

    @Test
    fun `SIG3 aceita meia unidade do terceiro algarismo significativo`() {
        assertTrue(LibraryValidator.goldenMatches(BigDecimal("30.86"), BigDecimal("30.9"), "SIG3"))
        assertTrue(LibraryValidator.goldenMatches(BigDecimal("22.031"), BigDecimal("22"), "SIG3"))
        assertTrue(LibraryValidator.goldenMatches(BigDecimal("10.45"), BigDecimal("10.4"), "SIG3"))
        assertTrue(LibraryValidator.goldenMatches(BigDecimal("3.957"), BigDecimal("3.96"), "SIG3"))
        assertFalse(LibraryValidator.goldenMatches(BigDecimal("30.96"), BigDecimal("30.9"), "SIG3"))
        assertFalse(LibraryValidator.goldenMatches(BigDecimal("0.01"), BigDecimal("0"), "SIG3"))
    }
}
