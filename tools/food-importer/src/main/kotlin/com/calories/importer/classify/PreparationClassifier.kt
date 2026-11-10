package com.calories.importer.classify

import com.calories.domain.nutrition.Preparation
import java.text.Normalizer

/**
 * Deduz o preparo a partir da descrição da fonte (ex.: "Frango, peito, sem pele, grelhado").
 * Procura, do último segmento para o primeiro, uma palavra de preparo inteira. Itens sem
 * palavra de preparo usam o padrão do grupo ou uma decisão explícita da curadoria.
 */
object PreparationClassifier {

    private val vocabulary: List<Pair<Preparation, Set<String>>> = listOf(
        Preparation.RAW to setOf("cru", "crua", "crus", "cruas"),
        Preparation.COOKED to setOf("cozido", "cozida", "cozidos", "cozidas"),
        Preparation.GRILLED to setOf("grelhado", "grelhada", "grelhados", "grelhadas"),
        Preparation.ROASTED to setOf("assado", "assada", "assados", "assadas"),
        Preparation.FRIED to setOf("frito", "frita", "fritos", "fritas"),
        Preparation.SAUTEED to setOf("refogado", "refogada", "refogados", "refogadas"),
        Preparation.TOASTED to setOf("torrado", "torrada", "torrados", "torradas"),
        Preparation.CANNED to setOf("conserva", "enlatado", "enlatada", "enlatados", "enlatadas"),
        Preparation.DRIED to setOf("desidratado", "desidratada", "desidratados", "desidratadas", "po"),
    )

    data class Match(val preparation: Preparation, val segmentIndex: Int, val wholeSegment: Boolean)

    fun detect(description: String): Match? {
        val segments = segments(description)
        for (i in segments.indices.reversed()) {
            val words = normalize(segments[i]).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            for ((preparation, terms) in vocabulary) {
                if (words.any { it in terms }) {
                    val whole = words.isNotEmpty() && words.first() in terms && words.drop(1).all { it.all(Char::isDigit) || it in setOf("com", "sal", "minutos", "em", "oleo", "10minutos") }
                    return Match(preparation, i, whole)
                }
            }
        }
        return null
    }

    /**
     * Nome base: descrição sem o segmento final de preparo, quando ele é um segmento inteiro
     * ("…, sem pele, grelhado" → "…, sem pele"). Agrupa variações Cru · Cozido · Grelhado.
     * Preparo no meio da descrição ("Batata, frita, tipo chips, …") mantém o nome inteiro.
     */
    fun baseName(description: String): String {
        val match = detect(description) ?: return description
        val segments = segments(description)
        if (!match.wholeSegment || match.segmentIndex != segments.lastIndex || segments.size < 2) return description
        return segments.dropLast(1).joinToString(", ")
    }

    private fun segments(description: String) = description.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
