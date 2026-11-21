package pe.ecotrack

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.util.concurrent.TimeUnit

internal class Supabase(context: Context) {
    private val url = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_KEY
    val localPilot = BuildConfig.DEBUG && url.toHttpUrlOrNull()?.host == "127.0.0.1" && key.isNotBlank()
    val configured = url.toHttpUrlOrNull()?.let { endpoint ->
        (endpoint.isHttps && key.startsWith("sb_publishable_")) || localPilot
    } ?: false
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val store = SupabaseSessionStore(context)
    private val sessionLock = Mutex()
    @Volatile private var session = if (configured) runCatching { store.read(url)?.let(Session::fromJson) }.getOrNull() else null
    @Volatile private var realtimeAccess: String? = null
    val accountEmail get() = session?.email.orEmpty()

    suspend fun clearSession() = sessionLock.withLock {
        session = null
        store.clear()
    }

    suspend fun login(email: String, password: String, register: Boolean): Boolean {
        val endpoint = if (register) "signup" else "token?grant_type=password"
        val result = execute("/auth/v1/$endpoint", JSONObject().put("email", email).put("password", password)) as JSONObject
        val authenticated = Session.fromAuth(result, email) ?: return false
        sessionLock.withLock { saveSession(authenticated) }
        return true
    }

    suspend fun list(table: String) = request("/rest/v1/$table?select=*")
    suspend fun rewardReceipts() =
        request("/rest/v1/ecotrack_points?select=id,reward_id,amount,created_at,reward_delivery,fulfilled_at&reward_id=not.is.null")
    suspend fun rpc(name: String, body: JSONObject = JSONObject()) = request("/rest/v1/rpc/$name", body)

    private suspend fun request(path: String, body: JSONObject? = null): Any =
        execute(path, body, validToken() ?: error("Inicia sesión para continuar."))

    private suspend fun validToken(): String? = sessionLock.withLock {
        val current = session ?: return@withLock null
        if (current.expiresAt > Instant.now().epochSecond + 45) return@withLock current.access
        val renewed = try {
            execute("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", current.refresh)) as JSONObject
        } catch (error: SupabaseHttpError) {
            if (error.status in listOf(400, 401, 403)) {
                session = null
                store.clear()
            }
            throw error
        }
        val next = Session.fromAuth(renewed, current.email) ?: error("No se pudo renovar tu sesión.")
        saveSession(next)
        next.access
    }

    private suspend fun saveSession(value: Session) = withContext(Dispatchers.IO) {
        store.save(url, value.toJson())
        session = value
    }

    private suspend fun execute(path: String, body: JSONObject? = null, access: String? = null): Any = withContext(Dispatchers.IO) {
        check(configured) { "Falta configurar Supabase." }
        val request = Request.Builder().url(url + path).header("apikey", key)
        access?.let { request.header("Authorization", "Bearer $it") }
        body?.let { request.post(it.toString().toRequestBody("application/json".toMediaType())) }
        client.newCall(request.build()).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching { JSONObject(payload) }.getOrNull()
                val message = error?.optString("message").orEmpty().ifBlank { error?.optString("msg").orEmpty() }
                throw SupabaseHttpError(response.code, message.ifBlank { "Supabase respondió con un error (${response.code})." })
            }
            if (payload.isBlank()) JSONObject.NULL else JSONTokener(payload).nextValue()
        }
    }

    suspend fun watchActivity(onUpdate: () -> Unit): WebSocket? {
        val access = validToken() ?: return null
        realtimeAccess = access
        val socketClient = client.newBuilder().readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("$url/realtime/v1/websocket?apikey=$key&vsn=1.0.0").build()
        return socketClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val changes = listOf("ecotrack_deliveries", "ecotrack_points", "ecotrack_rewards").map {
                    JSONObject().put("event", "*").put("schema", "public").put("table", it)
                }
                val config = JSONObject().put("private", false).put("postgres_changes", org.json.JSONArray(changes))
                val payload = JSONObject().put("access_token", access).put("config", config)
                webSocket.send(JSONObject().put("topic", ActivityTopic).put("event", "phx_join")
                    .put("ref", "1").put("join_ref", "1").put("payload", payload).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (runCatching { JSONObject(text).optString("event") }.getOrNull() == "postgres_changes") onUpdate()
            }
        })
    }

    suspend fun refreshRealtimeAccess(socket: WebSocket, ref: Int) {
        val access = validToken() ?: return
        if (access == realtimeAccess) return
        if (socket.send(JSONObject().put("topic", ActivityTopic).put("event", "access_token")
            .put("ref", ref.toString()).put("join_ref", "1")
            .put("payload", JSONObject().put("access_token", access)).toString())) {
            realtimeAccess = access
        }
    }

    private data class Session(val access: String, val refresh: String, val expiresAt: Long, val email: String) {
        fun toJson() = JSONObject().put("access", access).put("refresh", refresh).put("expires_at", expiresAt).put("email", email)

        companion object {
            fun fromJson(data: JSONObject) = Session(data.getString("access"), data.getString("refresh"),
                data.getLong("expires_at"), data.getString("email"))

            fun fromAuth(data: JSONObject, email: String): Session? {
                val access = data.optString("access_token").takeIf { it.isNotBlank() } ?: return null
                val refresh = data.optString("refresh_token").takeIf { it.isNotBlank() } ?: return null
                val expires = data.optLong("expires_at", Instant.now().epochSecond + data.optLong("expires_in", 3600))
                return Session(access, refresh, expires, data.optJSONObject("user")?.optString("email")?.ifBlank { email } ?: email)
            }
        }
    }

    private class SupabaseHttpError(val status: Int, message: String) : IllegalStateException(message)

    private companion object {
        const val ActivityTopic = "realtime:ecotrack_activity"
    }
}


