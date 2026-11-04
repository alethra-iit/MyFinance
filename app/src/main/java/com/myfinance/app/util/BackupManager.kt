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
                a.put(JSONObject().put("id", it.id).put("name", it.name).put("type", it.type).put("sortOrder", it.sortOrder)
                    .put("icon", it.icon).put("color", it.color))
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
        root.put("budgets", JSONArray().also { a ->
            d.budgets.forEach { a.put(JSONObject().put("category", it.category).put("amount", it.amount)) }
        })
        root.put("recurring", JSONArray().also { a ->
            d.recurring.forEach {
                a.put(JSONObject().put("id", it.id).put("type", it.type).put("amount", it.amount)
                    .put("category", it.category).put("note", it.note).put("frequency", it.frequency)
                    .put("anchorDay", it.anchorDay).put("nextDue", it.nextDue).put("active", it.active))
            }
        })
        root.put("debts", JSONArray().also { a ->
            d.debts.forEach {
                a.put(JSONObject().put("id", it.id).put("person", it.person).put("type", it.type).put("amount", it.amount)
                    .put("paid", it.paid).put("dueDate", it.dueDate ?: JSONObject.NULL)
                    .put("note", it.note).put("createdAt", it.createdAt))
            }
        })
        root.put("receipts", JSONArray().also { a ->
            d.receipts.forEach { a.put(JSONObject().put("transactionId", it.transactionId).put("data", it.data)) }
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
                CategoryEntity(it.getLong("id"), it.getString("name"), it.getString("type"), it.getInt("sortOrder"),
                    it.optString("icon", ""), it.optLong("color", 0L))
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
            },
            budgets = r.optJSONArray("budgets")?.map { BudgetEntity(it.getString("category"), it.getLong("amount")) } ?: emptyList(),
            recurring = r.optJSONArray("recurring")?.map {
                RecurringEntity(it.getLong("id"), it.getString("type"), it.getLong("amount"), it.getString("category"),
                    it.optString("note", ""), it.optString("frequency", FREQ_MONTHLY), it.optInt("anchorDay", 1),
                    it.getLong("nextDue"), it.optBoolean("active", true))
            } ?: emptyList(),
            debts = r.optJSONArray("debts")?.map {
                DebtEntity(it.getLong("id"), it.getString("person"), it.getString("type"), it.getLong("amount"),
                    it.optLong("paid", 0L), if (it.isNull("dueDate")) null else it.getLong("dueDate"),
                    it.optString("note", ""), it.optLong("createdAt", System.currentTimeMillis()))
            } ?: emptyList(),
            receipts = r.optJSONArray("receipts")?.map {
                ReceiptEntity(it.getLong("transactionId"), it.getString("data"))
            } ?: emptyList()
        )
    }
}
