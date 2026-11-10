package com.calories.library.format

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.InputStream

/** Leitura e escrita determinísticas do asset da biblioteca. */
object LibraryAssetCodec {
    const val FORMAT_VERSION = 1

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    /** Sempre termina com `\n`, para que o arquivo gerado seja estável no git. */
    fun encode(asset: LibraryAsset): String = json.encodeToString(LibraryAsset.serializer(), asset) + "\n"

    fun decode(text: String): LibraryAsset = json.decodeFromString(LibraryAsset.serializer(), text).also(::checkVersion)

    fun decode(input: InputStream): LibraryAsset = decode(input.bufferedReader(Charsets.UTF_8).readText())

    private fun checkVersion(asset: LibraryAsset) {
        require(asset.formatVersion == FORMAT_VERSION) {
            "formatVersion ${asset.formatVersion} não suportado (esperado $FORMAT_VERSION)"
        }
    }
}
