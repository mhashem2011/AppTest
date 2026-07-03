package com.fundingledger.data

import com.fundingledger.model.Ledger
import com.fundingledger.model.SeedData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Persists the ledger as pretty-printed JSON in app-private storage. */
class LedgerRepository(private val file: File) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun load(): Ledger = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<Ledger>(file.readText()) }
            .getOrElse { SeedData.ledger() }
    }

    suspend fun save(ledger: Ledger) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Ledger.serializer(), ledger))
        tmp.renameTo(file)
    }

    fun exportJson(ledger: Ledger): String = json.encodeToString(Ledger.serializer(), ledger)
}
