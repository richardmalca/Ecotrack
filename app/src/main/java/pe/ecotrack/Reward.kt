package pe.ecotrack

import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.util.UUID

internal data class Reward(
    val id: String,
    val name: String,
    val category: String,
    val cost: Int,
    val stock: Int,
    val maxPerUser: Int,
    val details: String,
    val fulfillment: String,
    val photo: String?,
    val sample: Boolean,
    val pilotActive: Boolean = true,
    val catalogKey: String = ""
) {
    fun unavailable(balance: Int, claimed: Int): String? = when {
        stock == 0 -> "Agotado"
        claimed >= maxPerUser -> "Límite alcanzado"
        balance < cost -> "Te faltan ${cost - balance} puntos"
        else -> null
    }

    companion object {
        fun fromJson(json: JSONObject, sample: Boolean = json.optBoolean("is_sample")) = Reward(
            id = json.getString("id"),
            name = json.getString("name"),
            category = json.optString("category", "Materiales"),
            cost = json.getInt("cost"),
            stock = json.getInt("stock"),
            maxPerUser = json.optInt("max_per_user", 1),
            details = json.optString("details"),
            fulfillment = json.optString("fulfillment"),
            photo = if (json.isNull("photo")) null else json.optString("photo"),
            sample = sample,
            pilotActive = json.optBoolean("pilot_active", true),
            catalogKey = json.optString("catalog_key")
        )
    }
}

internal data class RewardReceipt(
    val id: String,
    val rewardId: String,
    val cost: Int,
    val created: Instant,
    val contribution: Boolean,
    val fulfilled: Instant? = null
) {
    val canCollect get() = !contribution && fulfilled == null
    val status get() = when {
        contribution -> "Aporte registrado"
        fulfilled != null -> "Recibido"
        else -> "Por reclamar"
    }

    fun toJson() = JSONObject()
        .put("id", id).put("reward_id", rewardId).put("amount", -cost)
        .put("created_at", created.toString()).put("reward_delivery", if (contribution) "contribution" else "pickup")
        .put("fulfilled_at", fulfilled?.toString())
}

internal fun JSONArray.toRewardReceipts(): List<RewardReceipt> = List(length()) { index ->
    val row = getJSONObject(index)
    RewardReceipt(
        row.getString("id"), row.getString("reward_id"), -row.getInt("amount"),
        Instant.parse(row.getString("created_at")),
        row.optString("reward_delivery") == "contribution",
        if (row.isNull("fulfilled_at")) null else Instant.parse(row.getString("fulfilled_at"))
    )
}.sortedByDescending { it.created }

internal data class ReviewCode(val id: String, val isReward: Boolean) {
    companion object {
        private const val RewardPrefix = "ecotrack:reward:"

        fun rewardPayload(id: String) = RewardPrefix + id

        fun parse(value: String): ReviewCode? {
            val reward = value.startsWith(RewardPrefix)
            val id = if (reward) value.removePrefix(RewardPrefix) else value
            if (runCatching { UUID.fromString(id).toString() }.getOrNull() != id) return null
            return ReviewCode(id, reward)
        }
    }
}
