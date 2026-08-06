package org.wikitide.wikiportal.data.store

import app.cash.sqldelight.ColumnAdapter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.wikitide.wikiportal.data.model.Rank
import org.wikitide.wikiportal.data.model.SkinOption

object SkinOptionListColumnAdapter : ColumnAdapter<List<SkinOption>, String> {
    private val json = Json { ignoreUnknownKeys = true }

    override fun decode(databaseValue: String): List<SkinOption> =
        if (databaseValue.isEmpty()) emptyList() else json.decodeFromString(databaseValue)

    override fun encode(value: List<SkinOption>): String = json.encodeToString(value)
}

object RankColumnAdapter : ColumnAdapter<Rank, String> {
    override fun decode(databaseValue: String): Rank = Rank(databaseValue)
    override fun encode(value: Rank): String = value.value
}
