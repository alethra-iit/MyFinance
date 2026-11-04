package com.myfinance.app.util

import com.myfinance.app.data.*
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {
    fun toJson(d: AllData): String {
        val root = JSONObject()
        root.put("app", "MyFinance")
        root.put("version", 1)
        root.put("transactions", JSONArray().also { a ->
            d.transactions.forEach {
                a.put(JSONObject().put("id", it.id).put("type", it.type).put("amount", it.amount)
                    .put("category", it.category).put("dateMillis", it.dateMillis)
                    .put("note", it.note).put("createdAt", it.createdAt))
            }
        })
        root.put("categories", JSONArray().also { a ->
            d.categories.forEach {
                a.put(JSONObject().put("id", it.id).put("name", it.name).put("type", it.type).put("sortOrder", it.sortOrder))
            }
        })
        root.put("lists", JSONArray().also { a ->
            d.lists.forEach {
                a.put(JSONObject().put("id", it.id).put("title", it.title).put("createdAt", it.createdAt)
                    .put("lastConvertedAt", it.lastConvertedAt ?: JSONObject.NULL))
            }
        })
        root.put("items", JSONArray().also { a ->
            d.items.forEach {
                a.put(JSONObject().put("id", it.id).put("listId", it.listId).put("name", it.name)
                    .put("quantity", it.quantity).put("price", it.price)
                    .put("checked", it.checked).put("converted", it.converted))
            }
        })
        d.settings?.let {
            root.put("settings", JSONObject().put("userName", it.userName).put("theme", it.theme)
                .put("currency", it.currency).put("notifications", it.notifications))
        }
        return root.toString(2)
    }

    fun fromJson(json: String): AllData {
        val r = JSONObject(json)
        require(r.optString("app") == "MyFinance") { "Bukan berkas backup MyFinance" }
        fun <T> JSONArray.map(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
        return AllData(
            transactions = r.getJSONArray("transactions").map {
                TransactionEntity(it.getLong("id"), it.getString("type"), it.getLong("amount"),
                    it.getString("category"), it.getLong("dateMillis"), it.optString("note", ""), it.optLong("createdAt"))
            },
            categories = r.optJSONArray("categories")?.map {
                CategoryEntity(it.getLong("id"), it.getString("name"), it.getString("type"), it.getInt("sortOrder"))
            } ?: emptyList(),
            lists = r.getJSONArray("lists").map {
                ShoppingListEntity(it.getLong("id"), it.getString("title"), it.getLong("createdAt"),
                    if (it.isNull("lastConvertedAt")) null else it.getLong("lastConvertedAt"))
            },
            items = r.getJSONArray("items").map {
                ShoppingItemEntity(it.getLong("id"), it.getLong("listId"), it.getString("name"),
                    it.getInt("quantity"), it.getLong("price"), it.getBoolean("checked"), it.getBoolean("converted"))
            },
            settings = r.optJSONObject("settings")?.let {
                UserSettingsEntity(1, it.optString("userName", "Pengguna"), it.optString("theme", "LIGHT"),
                    it.optString("currency", "IDR"), it.optBoolean("notifications", false))
            }
        )
    }
}
