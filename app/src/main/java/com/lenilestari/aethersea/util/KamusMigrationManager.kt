package com.lenilestari.aethersea.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lenilestari.aethersea.data.model.KamusItem
import com.lenilestari.aethersea.data.repository.KamusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KamusMigrationManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("kamus_migration", Context.MODE_PRIVATE)
    private val repo = KamusRepository()

    suspend fun migrateIfNeeded() {
        if (prefs.getBoolean("v1_uploaded", false)) return
        withContext(Dispatchers.IO) {
            try {
                val items = parseKamusFromAssets()
                if (items.isEmpty()) return@withContext
                val result = repo.uploadBatch(items)
                if (result.isSuccess) {
                    prefs.edit().putBoolean("v1_uploaded", true).apply()
                    Log.i("KamusMigration", "Uploaded ${items.size} items to Firestore kamus")
                } else {
                    Log.e("KamusMigration", "Upload failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("KamusMigration", "Migration error: ${e.message}")
            }
        }
    }

    private fun parseKamusFromAssets(): List<KamusItem> {
        val json = context.assets.open("kamus_items.json").bufferedReader().readText()
        val root = Gson().fromJson(json, JsonObject::class.java)
        val categories = root.getAsJsonObject("categories") ?: return emptyList()
        val items = mutableListOf<KamusItem>()
        categories.keySet().forEach { categoryKey ->
            val catObj = categories.getAsJsonObject(categoryKey)
            val itemsArr = catObj.getAsJsonArray("items") ?: return@forEach
            itemsArr.forEach { el ->
                val obj = el.asJsonObject
                val name = obj.get("name")?.asString ?: return@forEach
                val aliases = obj.getAsJsonArray("aliases")
                    ?.map { it.asString } ?: emptyList()
                val units = obj.getAsJsonArray("common_units")
                    ?.map { it.asString } ?: emptyList()
                items.add(KamusItem(
                    name = name,
                    aliases = aliases,
                    commonUnits = units,
                    category = categoryKey
                ))
            }
        }
        return items
    }
}
