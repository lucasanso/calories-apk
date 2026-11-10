package com.calories.importer

import com.calories.importer.io.Hashing
import com.calories.library.format.AssetSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

/**
 * `nutrition-data/sources.lock.json`: metadados de cada fonte (citação, licença) e os
 * arquivos brutos com SHA-256. O importador recusa arquivos que não batem com o hash.
 */
data class SourcesLock(val sources: List<LockedSource>) {

    data class LockedSource(val meta: AssetSource, val files: List<LockedFile>) {
        fun file(role: String): LockedFile =
            files.firstOrNull { it.role == role } ?: throw IllegalArgumentException("${meta.code}: arquivo '$role' ausente no lock")
    }

    data class LockedFile(val role: String, val path: String, val url: String, val sha256: String)

    fun source(code: String): LockedSource =
        sources.firstOrNull { it.meta.code == code } ?: throw IllegalArgumentException("Fonte $code ausente no lock")

    /** Confere existência e hash de todos os arquivos; devolve erros legíveis. */
    fun verify(dataDir: Path): List<String> = buildList {
        for (source in sources) for (file in source.files) {
            val path = dataDir.resolve(file.path)
            if (!Files.isRegularFile(path)) {
                add("E2 ${source.meta.code}: arquivo ausente ${file.path} (baixe de ${file.url})")
                continue
            }
            val actual = Hashing.sha256(path)
            if (!actual.equals(file.sha256, ignoreCase = true)) {
                add("E2 ${source.meta.code}: SHA-256 de ${file.path} é $actual, esperado ${file.sha256}")
            }
        }
    }

    companion object {
        fun load(path: Path): SourcesLock {
            val root = Json.parseToJsonElement(Files.readString(path)).jsonObject
            val sources = root.getValue("sources").jsonArray.map { element ->
                val o = element.jsonObject
                val meta = AssetSource(
                    code = o.string("code"),
                    name = o.string("name"),
                    edition = o.optString("edition"),
                    publisher = o.optString("publisher"),
                    year = o["year"]?.jsonPrimitive?.int,
                    url = o.optString("url"),
                    license = o.string("license"),
                    citation = o.string("citation"),
                    carbohydrateBasis = o.string("carbohydrateBasis"),
                    accessedAt = o.optString("accessedAt"),
                )
                val files = o.getValue("files").jsonArray.map { f ->
                    val fo = f.jsonObject
                    LockedFile(fo.string("role"), fo.string("path"), fo.string("url"), fo.string("sha256"))
                }
                LockedSource(meta, files)
            }
            return SourcesLock(sources)
        }

        private fun JsonObject.string(key: String): String =
            this[key]?.jsonPrimitive?.content ?: throw IllegalArgumentException("sources.lock.json: campo '$key' ausente")

        private fun JsonObject.optString(key: String): String? = this[key]?.jsonPrimitive?.content
    }
}
