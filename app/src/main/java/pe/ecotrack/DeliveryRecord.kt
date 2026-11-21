package pe.ecotrack

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val DeliveryPoints = 20

internal data class DeliveryRecord(
    val code: String,
    val material: String,
    val created: Instant,
    val status: String = "pending",
    val resolved: Instant? = null
) {
    val expires: Instant get() = created.plusSeconds(30 * 60)

    fun statusAt(now: Instant): String =
        if (status == "pending" && now >= expires) "expired" else status

    fun toJson() = JSONObject()
        .put("code", code)
        .put("material", material)
        .put("created_at", created.toString())
        .put("status", status)
        .put("resolved_at", resolved?.toString())
}

internal fun JSONArray.toDeliveryHistory(): List<DeliveryRecord> =
    (0 until length()).map { index ->
        val row = getJSONObject(index)
        DeliveryRecord(
            row.getString("code"),
            row.getString("material"),
            Instant.parse(row.getString("created_at")),
            row.getString("status"),
            if (row.isNull("resolved_at")) null else Instant.parse(row.getString("resolved_at"))
        )
    }.sortedByDescending { it.created }

internal fun List<DeliveryRecord>.toHistoryJson() =
    JSONArray().also { json -> forEach { json.put(it.toJson()) } }.toString()

private val deliveryDate = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.forLanguageTag("es-PE"))
    .withZone(ZoneId.of("America/Lima"))
private val deliveryTime = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("America/Lima"))
private val deliveryDay = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))
    .withZone(ZoneId.of("America/Lima"))

internal fun Instant.deliveryDate(): String = deliveryDate.format(this)
internal fun Instant.deliveryTime(): String = deliveryTime.format(this)
internal fun Instant.deliveryDay(): String = deliveryDay.format(this)
