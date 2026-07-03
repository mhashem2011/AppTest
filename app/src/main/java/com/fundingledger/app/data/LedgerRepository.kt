package com.fundingledger.app.data

import android.content.Context
import com.fundingledger.logic.LedgerState
import kotlinx.serialization.json.Json
import java.io.File

class LedgerRepository(context: Context) {
    private val file = File(context.filesDir, "ledger.json")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun load(): LedgerState? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString(LedgerState.serializer(), file.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun save(state: LedgerState) {
        file.writeText(json.encodeToString(LedgerState.serializer(), state))
    }
}
