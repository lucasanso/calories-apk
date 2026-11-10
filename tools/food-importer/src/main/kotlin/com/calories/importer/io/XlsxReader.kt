package com.calories.importer.io

import java.nio.file.Path
import java.util.zip.ZipFile
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

/** Conteúdo de uma célula: número (texto decimal exatamente como gravado no XML) ou texto. */
sealed interface XlsxCell {
    data class Number(val raw: String) : XlsxCell
    data class Text(val value: String) : XlsxCell
}

data class XlsxRow(val excelRow: Int, val cells: Map<Int, XlsxCell>) {
    operator fun get(column: Int): XlsxCell? = cells[column]
    fun text(column: Int): String? = (cells[column] as? XlsxCell.Text)?.value
}

/**
 * Leitor mínimo de .xlsx (Office Open XML) sem dependências: lê só valores de células
 * (strings compartilhadas, strings inline e números). Não avalia fórmulas; usa o valor
 * gravado em cache, que é o que o Excel exibe.
 */
class XlsxWorkbook private constructor(private val sheets: Map<String, List<XlsxRow>>) {

    val sheetNames: Set<String> get() = sheets.keys

    fun sheet(name: String): List<XlsxRow> =
        sheets[name] ?: throw IllegalArgumentException("Planilha '$name' não encontrada (tem $sheetNames)")

    companion object {
        private const val NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

        fun read(path: Path): XlsxWorkbook = ZipFile(path.toFile()).use { zip ->
            fun open(name: String): XMLStreamReader {
                val entry = zip.getEntry(name) ?: throw IllegalArgumentException("$path: entrada '$name' ausente")
                return factory().createXMLStreamReader(zip.getInputStream(entry), "UTF-8")
            }

            val shared = zip.getEntry("xl/sharedStrings.xml")?.let { readSharedStrings(open("xl/sharedStrings.xml")) } ?: emptyList()
            val relTargets = readRelationships(open("xl/_rels/workbook.xml.rels"))
            val sheetFiles = readSheetList(open("xl/workbook.xml")).associate { (name, relId) ->
                val target = relTargets[relId] ?: throw IllegalArgumentException("Relação $relId não encontrada")
                name to ("xl/" + target.removePrefix("/xl/").removePrefix("/"))
            }
            XlsxWorkbook(sheetFiles.mapValues { (_, file) -> readSheet(open(file), shared) })
        }

        private fun factory(): XMLInputFactory = XMLInputFactory.newInstance().apply {
            // Arquivo externo: sem DTD e sem entidades externas.
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }

        private fun readSharedStrings(r: XMLStreamReader): List<String> {
            val out = ArrayList<String>()
            val current = StringBuilder()
            var inSi = false
            var phoneticDepth = 0
            var inT = false
            while (r.hasNext()) {
                when (r.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                        "si" -> { inSi = true; current.setLength(0) }
                        "rPh" -> phoneticDepth++
                        "t" -> inT = inSi && phoneticDepth == 0
                    }
                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (inT) current.append(r.text)
                    XMLStreamConstants.END_ELEMENT -> when (r.localName) {
                        "si" -> { out += current.toString(); inSi = false }
                        "rPh" -> phoneticDepth--
                        "t" -> inT = false
                    }
                }
            }
            r.close()
            return out
        }

        private fun readRelationships(r: XMLStreamReader): Map<String, String> {
            val out = HashMap<String, String>()
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && r.localName == "Relationship") {
                    out[r.getAttributeValue(null, "Id")] = r.getAttributeValue(null, "Target")
                }
            }
            r.close()
            return out
        }

        private fun readSheetList(r: XMLStreamReader): List<Pair<String, String>> {
            val out = ArrayList<Pair<String, String>>()
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && r.localName == "sheet") {
                    out += r.getAttributeValue(null, "name") to r.getAttributeValue(NS_REL, "id")
                }
            }
            r.close()
            return out
        }

        private fun readSheet(r: XMLStreamReader, shared: List<String>): List<XlsxRow> {
            val rows = ArrayList<XlsxRow>()
            var rowNumber = 0
            var cells = LinkedHashMap<Int, XlsxCell>()
            var cellRef: String? = null
            var cellType: String? = null
            val value = StringBuilder()
            var capture = false
            var hasValue = false
            while (r.hasNext()) {
                when (r.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (r.localName) {
                        "row" -> { rowNumber = r.getAttributeValue(null, "r").toInt(); cells = LinkedHashMap() }
                        "c" -> {
                            cellRef = r.getAttributeValue(null, "r"); cellType = r.getAttributeValue(null, "t")
                            value.setLength(0); hasValue = false
                        }
                        "v", "t" -> { capture = true; hasValue = true }
                    }
                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (capture) value.append(r.text)
                    XMLStreamConstants.END_ELEMENT -> when (r.localName) {
                        "v", "t" -> capture = false
                        "c" -> {
                            if (hasValue) {
                                val col = columnIndex(cellRef ?: error("Célula sem referência na linha $rowNumber"))
                                val raw = value.toString()
                                cells[col] = when (cellType) {
                                    "s" -> XlsxCell.Text(shared[raw.trim().toInt()])
                                    "str", "inlineStr" -> XlsxCell.Text(raw)
                                    "b" -> XlsxCell.Text(if (raw.trim() == "1") "TRUE" else "FALSE")
                                    "e" -> XlsxCell.Text(raw) // erro do Excel (#N/D etc.): tratado como texto desconhecido
                                    else -> XlsxCell.Number(raw.trim())
                                }
                            }
                        }
                        "row" -> rows += XlsxRow(rowNumber, cells)
                    }
                }
            }
            r.close()
            return rows
        }

        /** "A1" → 0, "Z9" → 25, "AA3" → 26. */
        fun columnIndex(cellRef: String): Int {
            var index = 0
            for (ch in cellRef) {
                if (!ch.isLetter()) break
                index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
            }
            return index - 1
        }
    }
}
