package pe.ecotrack

import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.Role
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private val Green = Color(0xFF245643)
private val Cream = Color(0xFFF8F8F1)
private val Mint = Color(0xFFDAEBDC)
private val Ink = Color(0xFF182A21)
private val Materials = listOf("Papel", "Cartón", "Vidrio", "Plástico")
private const val CameraPath = "M4 7h4l2-3h4l2 3h4v13H4zM15 13a3 3 0 1 1-6 0 3 3 0 1 1 6 0"
private const val QrPath = "M5 3h5v5H5zM14 3h5v5h-5zM5 12h5v5H5zM14 12v3m0 3h5v-6"
private val Tabs = listOf("Mapa", "Reciclar", "Premios", "Perfil")
private enum class AccountPage { Account, Deliveries, Rewards, Receipt }
private val TabIcons = listOf(Icons.Outlined.Place, Icons.Outlined.Refresh, Icons.Outlined.Star, Icons.Outlined.Person)
private data class Point(val name: String, val details: String, val lat: Double, val lng: Double, val materials: List<String>)
private val DemoPoints = listOf(
    Point("Punto piloto Centro", "Ubicación de prueba", -7.1578, -78.5161, Materials),
    Point("Punto piloto Santa Apolonia", "Ubicación de prueba", -7.1608, -78.5221, listOf("Papel")),
    Point("Punto piloto Chontapaccha", "Ubicación de prueba", -7.1489, -78.5210, listOf("Cartón")),
    Point("Punto piloto San Sebastián", "Ubicación de prueba", -7.1641, -78.5075, listOf("Vidrio")),
    Point("Punto piloto La Alameda", "Ubicación de prueba", -7.1533, -78.5088, listOf("Papel", "Cartón", "Plástico")),
    Point("Punto piloto Sur", "Ubicación de prueba", -7.1713, -78.5025, listOf("Cartón", "Vidrio"))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent {
            val font = FontFamily(Font(R.font.instrument_sans))
            fun type(size: Int, weight: FontWeight = FontWeight.Normal) =
                TextStyle(fontFamily = font, fontSize = size.sp, fontWeight = weight, lineHeight = (size + 6).sp)
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Green, primaryContainer = Mint, onPrimaryContainer = Ink,
                    secondaryContainer = Mint, background = Cream, surface = Cream, onSurface = Ink,
                    surfaceVariant = Color(0xFFE8EEE4), outlineVariant = Color(0xFFD5DDD2)
                ),
                typography = Typography(
                    headlineLarge = type(34, FontWeight.SemiBold), headlineMedium = type(28, FontWeight.SemiBold),
                    titleLarge = type(22, FontWeight.SemiBold), titleMedium = type(18, FontWeight.Medium),
                    bodyLarge = type(16), bodyMedium = type(14), labelLarge = type(15, FontWeight.Medium),
                    labelMedium = type(12, FontWeight.Medium)
                )
            ) { EcoTrack() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EcoTrack() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("ecotrack_preferences", android.content.Context.MODE_PRIVATE) }
    var askLocationOnStart by remember {
        mutableStateOf(!context.hasRouteLocation() && !preferences.getBoolean("location_intro_seen", false))
    }
    val api = remember(context) { Supabase(context.applicationContext) }
    val demoMode = BuildConfig.DEBUG && !api.configured
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var accountPage by rememberSaveable { mutableStateOf(AccountPage.Account) }
    var receiptId by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("Todos") }
    var material by remember { mutableStateOf("") }
    var photoPreview by remember { mutableStateOf<Bitmap?>(null) }
    var photoGuess by remember { mutableStateOf<RecyclingGuess?>(null) }
    var photoIssue by remember { mutableStateOf("") }
    var analyzingPhoto by remember { mutableStateOf(false) }
    var photoFilename by rememberSaveable { mutableStateOf<String?>(null) }
    var captureForReview by rememberSaveable { mutableStateOf(false) }
    val photoFolder = remember(context) { File(context.cacheDir, "material_photos") }
    var points by remember { mutableStateOf(if (demoMode) DemoPoints else emptyList()) }
    val demoCatalog = remember(context, demoMode) {
        if (!demoMode) return@remember emptyList()
        val data = context.assets.open("rewards-demo.json").bufferedReader().use { it.readText() }
        val json = JSONArray(data.trimStart('\uFEFF'))
        List(json.length()) { Reward.fromJson(json.getJSONObject(it), sample = true) }
    }
    fun demoRewards() = demoCatalog.map { it.copy(stock = preferences.getInt("demo_stock_${it.id}", it.stock)) }
    fun demoClaims() = demoCatalog.associate { item ->
        val previousStock = preferences.getInt("demo_stock_${item.id}", item.stock)
        item.id to preferences.getInt("demo_claims_${item.id}", (item.stock - previousStock).coerceAtLeast(0))
    }
    var rewards by remember { mutableStateOf(demoRewards()) }
    var rewardClaims by remember { mutableStateOf(demoClaims()) }
    fun demoReceipts() = runCatching {
        JSONArray(preferences.getString("demo_reward_receipts", "[]")).toRewardReceipts()
    }.getOrDefault(emptyList())
    var rewardReceipts by remember { mutableStateOf(if (demoMode) demoReceipts() else emptyList()) }
    var rewardCategory by rememberSaveable { mutableStateOf("Todas") }
    var selected by remember { mutableStateOf<Point?>(null) }
    var route by remember { mutableStateOf<WalkingRoute?>(null) }
    var routeDestination by remember { mutableStateOf<Point?>(null) }
    var routeMessage by remember { mutableStateOf("") }
    var liveLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var locationRevision by remember { mutableIntStateOf(0) }
    var routeRetry by remember { mutableIntStateOf(0) }
    var centerRequest by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (event == Lifecycle.Event.ON_RESUME) locationRevision++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val hasLocation = remember(locationRevision) { context.hasRouteLocation() }
    val locationEnabled = remember(locationRevision) { context.isRouteLocationEnabled() }
    DisposableEffect(tab, foreground, hasLocation, locationEnabled) {
        val stop = if (tab == 0 && foreground && hasLocation && locationEnabled) {
            context.observeRouteLocation { liveLocation = it }
        } else {
            liveLocation = null
            null
        }
        onDispose { stop?.invoke() }
    }
    fun demoHistory() = runCatching {
        JSONArray(preferences.getString("demo_delivery_history", "[]")).toDeliveryHistory()
    }.getOrDefault(emptyList())
    var history by remember { mutableStateOf(if (demoMode) demoHistory() else emptyList()) }
    var historyClock by remember { mutableStateOf(Instant.now()) }
    val activeDelivery = history.firstOrNull { it.statusAt(historyClock) == "pending" }
    val pending = activeDelivery?.code
    var replacingPhoto by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<String?>(null) }
    var scannedMaterial by remember { mutableStateOf("") }
    var scannedReward by remember { mutableStateOf<Pair<String, String>?>(null) }
    var rewardToClaim by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var balance by remember { mutableIntStateOf(if (demoMode) preferences.getInt("demo_balance", 0) else 0) }
    var collector by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(api.accountEmail) }
    var password by remember { mutableStateOf("") }
    var signedIn by remember { mutableStateOf(false) }
    val canParticipate = signedIn || demoMode
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(activeDelivery?.code) {
        replacingPhoto = false
        val expires = activeDelivery?.expires ?: return@LaunchedEffect
        val remaining = java.time.Duration.between(Instant.now(), expires).toMillis()
        delay(remaining.coerceAtLeast(0) + 1)
        historyClock = Instant.now()
    }
    BackHandler(tab == 3 && accountPage != AccountPage.Account) {
        accountPage = if (accountPage == AccountPage.Receipt) AccountPage.Rewards else AccountPage.Account
    }

    fun saveDemo() {
        preferences.edit().apply {
            putString("demo_delivery_history", history.toHistoryJson())
            putInt("demo_balance", balance)
            rewards.forEach { putInt("demo_stock_${it.id}", it.stock) }
            rewardClaims.forEach { (id, count) -> putInt("demo_claims_$id", count) }
            putString("demo_reward_receipts", JSONArray(rewardReceipts.map { it.toJson() }).toString())
        }.apply()
    }

    fun JSONArray.rows(transform: (JSONObject) -> Unit) {
        for (i in 0 until length()) transform(getJSONObject(i))
    }

    suspend fun refresh() {
        val wallet = api.rpc("ecotrack_wallet") as JSONObject
        balance = wallet.getInt("balance")
        collector = wallet.getBoolean("collector")
        val updated = (api.list("ecotrack_deliveries") as JSONArray).toDeliveryHistory()
        val current = updated.firstOrNull { it.code == pending }
        when (current?.status) {
            "approved" -> status = "Entrega aprobada. Puntos acreditados."
            "rejected" -> status = "Entrega rechazada."
        }
        history = updated
        historyClock = Instant.now()
        points = buildList {
            (api.list("ecotrack_sites") as JSONArray).rows { item ->
                val kinds = item.getJSONArray("materials")
                add(Point(item.getString("name"), item.optString("details"), item.getDouble("lat"),
                    item.getDouble("lng"), List(kinds.length()) { kinds.getString(it) }))
            }
        }
        rewards = buildList {
            (api.list("ecotrack_rewards") as JSONArray).rows { add(Reward.fromJson(it)) }
        }.sortedWith(compareBy<Reward>({ it.cost }, { it.name }))
        rewardReceipts = (api.rewardReceipts() as JSONArray).toRewardReceipts()
        rewardClaims = rewardReceipts.groupingBy { it.rewardId }.eachCount()
    }

    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                scope.launch { snacks.showSnackbar(ex.message ?: "No se pudo completar la operación.") }
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (api.accountEmail.isBlank()) return@LaunchedEffect
        busy = true
        try {
            refresh()
            signedIn = true
        } catch (ex: kotlinx.coroutines.CancellationException) {
            throw ex
        } catch (_: Exception) {
            scope.launch { snacks.showSnackbar("No pudimos recuperar tu sesión. Vuelve a iniciar sesión.") }
        } finally {
            busy = false
        }
    }

    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        val updates = Channel<Unit>(Channel.CONFLATED)
        var socket = api.watchActivity { updates.trySend(Unit) }
        try {
            var ref = 1
            while (true) {
                withTimeoutOrNull(20_000) { updates.receive() }
                if (socket?.send("""{"topic":"phoenix","event":"heartbeat","ref":"${++ref}","payload":{}}""") != true) {
                    socket?.cancel()
                    socket = api.watchActivity { updates.trySend(Unit) }
                }
                try {
                    refresh()
                    socket?.let { api.refreshRealtimeAccess(it, ++ref) }
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (_: Exception) {
                    // Keep the last state until the next update or manual refresh.
                }
            }
        } finally {
            socket?.close(1000, "Pantalla cerrada")
        }
    }
    fun inspectCode(value: String) {
        val code = ReviewCode.parse(value)
        if (code == null) {
            scope.launch { snacks.showSnackbar("Acerca el QR, enfócalo y vuelve a intentarlo.") }
            return
        }
        action {
            check(collector) { "Se requiere una cuenta de recolector." }
            if (code.isReward) {
                val name = if (signedIn) {
                    api.rpc("ecotrack_inspect_reward", JSONObject().put("receipt_id", code.id)) as String
                } else {
                    val receipt = rewardReceipts.firstOrNull { it.id == code.id && it.canCollect }
                        ?: error("El premio ya fue reclamado o el QR no está disponible.")
                    rewards.first { it.id == receipt.rewardId }.name
                }
                scannedReward = code.id to name
            } else {
                scannedMaterial = if (signedIn) {
                    api.rpc("ecotrack_lookup_delivery", JSONObject().put("delivery_code", code.id)) as String
                } else {
                    history.firstOrNull { it.code == code.id && it.statusAt(Instant.now()) == "pending" }?.material
                        ?: error("El QR venció o ya fue revisado.")
                }
                scanned = code.id
            }
        }
    }
    val pilotQrPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && api.localPilot && signedIn && collector) {
            scope.launch {
                analyzingPhoto = true
                try {
                    val code = withContext(Dispatchers.IO) {
                        val file = File.createTempFile("pilot-qr-", ".jpg", context.cacheDir)
                        try {
                            checkNotNull(context.contentResolver.openInputStream(uri)).use { source ->
                                file.outputStream().use { source.copyTo(it) }
                            }
                            readQr(decodeMaterialPhoto(file))
                        } finally {
                            file.delete()
                        }
                    }
                    inspectCode(code)
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (_: Exception) {
                    scope.launch { snacks.showSnackbar("La imagen no contiene un QR legible.") }
                } finally {
                    analyzingPhoto = false
                }
            }
        }
    }
    val captureCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = photoFilename?.let { File(photoFolder, it) }
        val review = captureForReview
        photoFilename = null
        if (file != null) {
            if (!saved) {
                file.delete()
            } else {
                analyzingPhoto = true
                if (!review) {
                    photoPreview = null
                    photoGuess = null
                    photoIssue = ""
                    material = ""
                }
                scope.launch {
                    try {
                        val photo = decodeMaterialPhoto(file)
                        if (review) {
                            inspectCode(withContext(Dispatchers.Default) { readQr(photo) })
                        } else {
                            photoPreview = photo
                            val guess = classifyRecycling(context, photo)
                            photoGuess = guess
                            if (guess?.confident == true) material = guess.material
                        }
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        throw ex
                    } catch (_: Exception) {
                        if (review) {
                            scope.launch { snacks.showSnackbar("Acerca el QR, enfócalo y vuelve a intentarlo.") }
                        } else photoIssue = "No pudimos analizar la foto. Puedes volver a tomarla o elegir el material."
                    } finally {
                        file.delete()
                        analyzingPhoto = false
                    }
                }
            }
        }
    }
    LaunchedEffect(selected, hasLocation, locationEnabled, routeRetry, foreground) {
        val destination = selected ?: return@LaunchedEffect
        if (!hasLocation || !locationEnabled || !foreground || routeDestination == destination) return@LaunchedEffect
        routeMessage = ""
        try {
            val origin = withTimeoutOrNull(15_000) { snapshotFlow { liveLocation }.filterNotNull().first() }
                ?: error("No encontramos tu ubicación. Inténtalo cerca de una ventana o al aire libre.")
            route = fetchWalkingRoute(origin, destination.lat, destination.lng)
            routeDestination = destination
        } catch (ex: kotlinx.coroutines.CancellationException) {
            throw ex
        } catch (ex: Exception) {
            routeMessage = if (ex is IllegalStateException) ex.message.orEmpty()
                else "No pudimos calcular la ruta. Revisa tu conexión."
        }
    }

    val locationPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationRevision++
        if (!context.hasRouteLocation() && selected != null) {
            routeMessage = "Permite la ubicación en los ajustes de EcoTrack."
        }
    }
    val locationSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        locationRevision++
        if (selected != null && context.isRouteLocationEnabled()) {
            if (!context.hasRouteLocation()) locationPermissions.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }
    fun selectPoint(point: Point) {
        routeMessage = ""
        route = null
        routeDestination = null
        selected = point
    }
    val permissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    fun dismissLocationIntro() {
        askLocationOnStart = false
        preferences.edit().putBoolean("location_intro_seen", true).apply()
    }
    val visiblePoints = points.filter { filter == "Todos" || filter in it.materials }
    val screenScroll = key(tab, if (tab == 3) accountPage else null) { rememberScrollState() }

    fun openCapture() {
        runCatching {
            if (analyzingPhoto || photoFilename != null) return
            photoFolder.mkdirs()
            val file = File.createTempFile("material-", ".jpg", photoFolder)
            photoFilename = file.name
            captureForReview = collector
            captureCamera.launch(FileProvider.getUriForFile(context, "${context.packageName}.photos", file))
        }.onFailure {
            photoFilename?.let { File(photoFolder, it).delete() }
            photoFilename = null
            scope.launch { snacks.showSnackbar("No hay una cámara disponible.") }
        }
    }

    fun openHistory() {
        accountPage = AccountPage.Deliveries
        tab = 3
    }
    fun openReceipt(id: String) {
        receiptId = id
        accountPage = AccountPage.Receipt
        tab = 3
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snacks) },
        bottomBar = {
            NavigationBar(containerColor = Cream) {
                Tabs.forEachIndexed { index, label ->
                    NavigationBarItem(selected = index == tab, onClick = {
                        if (index == 3) accountPage = AccountPage.Account
                        tab = index
                    },
                        icon = { Icon(TabIcons[index], contentDescription = null) },
                        label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(screenScroll).padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            when (tab) {
                0 -> {
                    EcoTrackHeader(balance, onBalanceClick = { tab = 2 })
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Recicla en Cajamarca", style = MaterialTheme.typography.headlineLarge)
                        Text("Toca un punto para ver qué recibe.", style = MaterialTheme.typography.bodyMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf("Todos") + Materials).forEach {
                            FilterChip(
                                selected = it == filter,
                                onClick = { filter = it },
                                label = { Text(it, style = MaterialTheme.typography.labelMedium) },
                                border = null,
                                colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xFFECEEE6))
                            )
                        }
                    }
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Box {
                            Leaflet(visiblePoints, route, liveLocation, centerRequest, ::selectPoint)
                            if (liveLocation != null) {
                                FilledTonalIconButton(
                                    onClick = { centerRequest++ },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                                ) { Icon(Icons.Rounded.Place, contentDescription = "Centrar en mi ubicación") }
                            }
                        }
                    }
                    routeDestination?.let { destination ->
                        route?.let { current ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${current.minutes} min a pie · ${current.meters} m\n${destination.name}", Modifier.weight(1f))
                                TextButton(onClick = { route = null; routeDestination = null }) { Text("Quitar ruta") }
                            }
                        }
                    }
                    if (visiblePoints.isEmpty()) {
                        Text(if (filter == "Todos") "Todavía no hay puntos de acopio." else "No hay puntos para este material.")
                    }
                    val resumeDelivery = !collector && pending != null
                    Button(
                        onClick = {
                            if (canParticipate) {
                                tab = 1
                                if (!resumeDelivery) openCapture()
                            } else {
                                accountPage = AccountPage.Account
                                tab = 3
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                        enabled = !busy && !analyzingPhoto
                    ) {
                        LineIcon(
                            if (resumeDelivery || collector) QrPath else CameraPath,
                            Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(when {
                            !canParticipate -> "Iniciar sesión"
                            collector -> "Escanear QR"
                            resumeDelivery -> "Ver mi QR de entrega"
                            else -> "Identificar material"
                        })
                    }
                    if (canParticipate && !collector && !resumeDelivery) {
                        TextButton(onClick = { tab = 1 }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Preparar entrega")
                            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
                1 -> {
                    if (!canParticipate) {
                        PilotSignIn("Reciclar") { accountPage = AccountPage.Account; tab = 3 }
                    } else if (collector) {
                        Text("Validar un QR", style = MaterialTheme.typography.headlineLarge)
                        CapturePanel(
                            title = "Entregas y premios",
                            detail = "Escanea el código del vecino para validar material o entregar su premio.",
                            button = "Escanear QR",
                            icon = QrPath,
                            analyzing = analyzingPhoto,
                            onCapture = ::openCapture
                        )
                        if (api.localPilot && signedIn) {
                            TextButton(enabled = !analyzingPhoto && !busy, onClick = { pilotQrPicker.launch(arrayOf("image/*")) }) {
                                Text("Leer imagen piloto")
                            }
                        }
                        if (demoMode && activeDelivery != null) {
                            TextButton(onClick = { inspectCode(activeDelivery.code) }) { Text("Usar entrega de prueba") }
                        }
                        if (demoMode) rewardReceipts.firstOrNull { it.canCollect }?.let { receipt ->
                            TextButton(onClick = { inspectCode(ReviewCode.rewardPayload(receipt.id)) }) {
                                Text("Usar premio de prueba")
                            }
                        }
                    } else {
                        if (activeDelivery == null) {
                            Text("Prepara tu entrega", style = MaterialTheme.typography.headlineLarge)
                            CapturePanel(
                                title = "Identifica el material",
                                detail = "Encadra un solo objeto entero con buena luz.",
                                button = if (photoPreview == null && photoIssue.isBlank()) "Tomar foto" else "Volver a tomar foto",
                                icon = CameraPath,
                                photo = photoPreview,
                                analyzing = analyzingPhoto,
                                result = photoGuess,
                                issue = photoIssue,
                                onCapture = ::openCapture
                            )
                            Text("Material de la entrega", style = MaterialTheme.typography.titleLarge)
                            Materials.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { option ->
                                        MaterialChoice(option, material == option, !analyzingPhoto, Modifier.weight(1f)) {
                                            material = option
                                        }
                                    }
                                }
                            }
                            Button(
                                enabled = !busy && !analyzingPhoto && material.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                onClick = {
                                    action {
                                        val id = if (signedIn) {
                                            api.rpc("ecotrack_create_delivery", JSONObject().put("kind", material)) as String
                                        } else {
                                            UUID.randomUUID().toString()
                                        }
                                        historyClock = Instant.now()
                                        history = listOf(DeliveryRecord(id, material, historyClock)) + history
                                        status = ""
                                        if (!signedIn) saveDemo()
                                    }
                                }
                            ) {
                                LineIcon(QrPath, MaterialTheme.colorScheme.onPrimary, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generar QR de entrega")
                            }
                        } else {
                            DeliveryQr(
                                activeDelivery, busy, onHistory = ::openHistory,
                                onRetake = { replacingPhoto = true },
                                onRefresh = if (signedIn) ({ action { refresh() } }) else null
                            )
                        }
                    }
                    if (status.isNotEmpty()) Text(status, Modifier.animateContentSize(), color = Green)
                }
                2 -> {
                    if (canParticipate) {
                        RewardsCatalog(rewards, balance, rewardClaims, rewardCategory,
                            onCategory = { rewardCategory = it }, onOpen = { rewardToClaim = it.id })
                    } else {
                        PilotSignIn("Premios") { accountPage = AccountPage.Account; tab = 3 }
                    }
                }
                else -> {
                    when (accountPage) {
                    AccountPage.Deliveries -> DeliveryHistory(history, historyClock) { accountPage = AccountPage.Account }
                    AccountPage.Rewards -> RewardHistory(rewardReceipts, rewards, onBack = { accountPage = AccountPage.Account },
                        onReceipt = ::openReceipt, onCatalog = { tab = 2 })
                    AccountPage.Receipt -> {
                        val receipt = rewardReceipts.firstOrNull { it.id == receiptId }
                        val reward = rewards.firstOrNull { it.id == receipt?.rewardId }
                        if (receipt != null && reward != null) {
                            RewardReceiptScreen(receipt, reward, onBack = { accountPage = AccountPage.Rewards },
                                onRefresh = if (signedIn) ({ action { refresh() } }) else null)
                        } else {
                            Text("El comprobante ya no está disponible.")
                            TextButton(onClick = { accountPage = AccountPage.Rewards }) { Text("Volver a Mis premios") }
                        }
                    }
                    AccountPage.Account -> {
                    Text("Tu cuenta", style = MaterialTheme.typography.headlineLarge)
                    if (signedIn) Text(email, style = MaterialTheme.typography.bodyMedium)
                    if (canParticipate) {
                        Text(if (collector) "Recolector" else "Vecino", style = MaterialTheme.typography.labelLarge, color = Green)
                        Text("Tu actividad", style = MaterialTheme.typography.titleLarge)
                        AccountActivityLink("Historial de recolección", "Materiales entregados y QR generados", QrPath, ::openHistory)
                        val pendingRewards = rewardReceipts.count { it.canCollect }
                        AccountActivityLink(
                        "Mis premios", if (pendingRewards > 0) "$pendingRewards por reclamar" else "Canjes y comprobantes",
                        "M4 9h16v12H4zM3 6h18v4H3zM12 6v15M12 6c-1-4-6-4-5-1 1 2 4 1 5 1Zm0 0c1-4 6-4 5-1-1 2-4 1-5 1z"
                        ) { accountPage = AccountPage.Rewards }
                    }
                    if (signedIn) {
                        OutlinedButton(enabled = !busy, onClick = {
                            action {
                                api.clearSession()
                                signedIn = false
                                collector = false
                                status = ""
                                history = emptyList()
                                balance = 0
                                points = emptyList()
                                rewards = emptyList()
                                rewardClaims = emptyMap()
                                rewardReceipts = emptyList()
                            }
                        }) { Text("Cerrar sesión") }
                    } else if (api.configured) {
                        OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true)
                        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") },
                            singleLine = true, visualTransformation = PasswordVisualTransformation())
                        val ready = !busy && email.contains('@') && password.length >= 6
                        Button(enabled = ready, onClick = {
                            action {
                                if (api.login(email.trim(), password, false)) {
                                    refresh()
                                    signedIn = true
                                    password = ""
                                    tab = 0
                                }
                            }
                        }) { Text("Iniciar sesión") }
                        OutlinedButton(enabled = ready, onClick = {
                            action {
                                if (api.login(email.trim(), password, true)) {
                                    refresh()
                                    signedIn = true
                                    password = ""
                                    tab = 0
                                } else {
                                    password = ""
                                    scope.launch { snacks.showSnackbar("Revisa tu correo para confirmar la cuenta.") }
                                }
                            }
                        }) { Text("Crear cuenta") }
                    } else if (demoMode) {
                        Text("Puedes probar el recorrido como vecino o recolector.")
                        OutlinedButton(onClick = { collector = !collector; tab = 1 }) {
                            Text(if (collector) "Probar como vecino" else "Probar como recolector")
                        }
                    } else Text("Configura la conexión del piloto para acceder a tu cuenta.")
                    }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (askLocationOnStart) {
        AlertDialog(
            onDismissRequest = ::dismissLocationIntro,
            icon = { Icon(Icons.Outlined.Place, contentDescription = null, tint = Green) },
            title = { Text("Tu ubicación para las rutas") },
            text = { Text("Mostramos tu ubicación mientras usas el mapa. Al tocar un punto, enviamos tu ubicación a FOSSGIS para estimar la ruta a pie. Puedes explorar el mapa sin dar permiso.") },
            confirmButton = {
                Button(onClick = {
                    dismissLocationIntro()
                    locationPermissions.launch(permissions)
                }) { Text("Permitir ubicación") }
            },
            dismissButton = { TextButton(onClick = ::dismissLocationIntro) { Text("Ahora no") } }
        )
    }

    selected?.let { point ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor = Cream,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(point.name, style = MaterialTheme.typography.headlineMedium)
                if (point.details.isNotBlank()) Text(point.details, style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Materiales que acepta", style = MaterialTheme.typography.labelLarge, color = Green)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) { point.materials.forEach { MaterialLabel(it) } }
                }
                val walking = route.takeIf { routeDestination == point }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LineIcon("M13 4a2 2 0 1 0 4 0 2 2 0 1 0-4 0 M6 12l3-2 3-2 3 4 3 1 M12 8l-2 7 4 2 1 5 M10 15l-2 5-4 2", Green)
                        Text("Ruta a pie", style = MaterialTheme.typography.titleMedium)
                    }
                    if (walking != null) {
                        val distance = remember(walking.meters) {
                            if (walking.meters < 1000) walking.meters.toString()
                            else NumberFormat.getNumberInstance(Locale.forLanguageTag("es-PE")).apply {
                                maximumFractionDigits = 2
                                isGroupingUsed = false
                            }.format(walking.meters / 1000.0)
                        }
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RouteMetric(Modifier.weight(1f).fillMaxHeight(), "Tiempo estimado", walking.minutes.toString(), "min", Mint,
                                "M22 12a10 10 0 1 1-20 0 10 10 0 1 1 20 0 M12 6v6l4 2")
                            RouteMetric(Modifier.weight(1f).fillMaxHeight(), "Distancia", distance,
                                if (walking.meters < 1000) "m" else "km", Color(0xFFE8ECE4),
                                "M6 4a2 2 0 1 0 0 4 2 2 0 1 0 0-4 M6 8v6a3 3 0 0 0 3 3h7 M16 13l4 4-4 4")
                        }
                        Button(onClick = { selected = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Ver en el mapa")
                            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    } else if (!hasLocation || !locationEnabled) {
                        Text("Activa la ubicación para ver el tiempo estimado.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            if (!locationEnabled) locationSettings.launch(
                                android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            ) else locationPermissions.launch(permissions)
                        }) { Text(if (locationEnabled) "Permitir ubicación" else "Activar ubicación") }
                    } else if (routeMessage.isNotBlank()) {
                        Text(routeMessage, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { routeRetry++ }) { Text("Reintentar") }
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
                        Text(if (liveLocation == null) "Buscando tu ubicación…" else "Calculando el tiempo estimado…")
                    }
                }
                TextButton(enabled = pending == null && !collector && point.materials.isNotEmpty(), onClick = {
                    material = point.materials.firstOrNull() ?: material
                    selected = null
                    tab = 1
                }) { Text("Preparar entrega") }
            }
        }
    }
    if (replacingPhoto && activeDelivery != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) replacingPhoto = false },
            title = { Text("¿Volver a tomar la foto?") },
            text = { Text("El QR actual se anulará y quedará registrado en el historial.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    action {
                        val code = activeDelivery.code
                        if (signedIn) {
                            api.rpc("ecotrack_cancel_delivery", JSONObject().put("delivery_code", code))
                        } else {
                            check(activeDelivery.statusAt(Instant.now()) == "pending") { "El QR ya venció." }
                        }
                        history = history.map {
                            if (it.code == code) it.copy(status = "cancelled", resolved = Instant.now()) else it
                        }
                        if (!signedIn) saveDemo()
                        replacingPhoto = false
                        photoPreview = null
                        photoGuess = null
                        photoIssue = ""
                        material = ""
                        openCapture()
                    }
                }) { Text("Anular QR y tomar foto") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { replacingPhoto = false }) { Text("Conservar QR") } }
        )
    }
    scanned?.let { code ->
        fun decide(approve: Boolean) = action {
            if (signedIn) {
                api.rpc("ecotrack_review_delivery", JSONObject().put("delivery_code", code).put("approve", approve))
                refresh()
            } else {
                val delivery = history.firstOrNull { it.code == code && it.statusAt(Instant.now()) == "pending" }
                    ?: error("El QR venció o ya fue revisado.")
                history = history.map { if (it.code == delivery.code) {
                    it.copy(status = if (approve) "approved" else "rejected", resolved = Instant.now())
                } else it }
                if (approve) balance += DeliveryPoints
                saveDemo()
            }
            scanned = null
            status = if (approve) "Entrega aprobada." else "Entrega rechazada."
        }
        AlertDialog(onDismissRequest = { if (!busy) scanned = null },
            title = { Text("Revisar entrega") },
            text = { Text("Material declarado: $scannedMaterial. Comprueba la entrega antes de aprobar.") },
            confirmButton = { Button(enabled = !busy, onClick = { decide(true) }) { Text("Aprobar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { decide(false) }) { Text("Rechazar") } })
    }
    scannedReward?.let { (id, title) ->
        AlertDialog(
            onDismissRequest = { if (!busy) scannedReward = null },
            title = { Text("Entregar premio") },
            text = { Text("Confirma la entrega o activación de «$title» cuando el vecino lo reciba.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    action {
                        check(collector) { "Se requiere una cuenta de recolector." }
                        if (signedIn) {
                            api.rpc("ecotrack_fulfill_reward", JSONObject().put("receipt_id", id))
                        } else {
                            check(rewardReceipts.any { it.id == id && it.canCollect }) {
                                "El premio ya fue reclamado o el QR no está disponible."
                            }
                            rewardReceipts = rewardReceipts.map { if (it.id == id) it.copy(fulfilled = Instant.now()) else it }
                            saveDemo()
                        }
                        scannedReward = null
                        status = "Premio entregado."
                        if (signedIn) refresh()
                    }
                }) { Text("Confirmar entrega") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { scannedReward = null }) { Text("Cancelar") } }
        )
    }
    rewards.firstOrNull { it.id == rewardToClaim }?.let { item ->
        val claimed = rewardClaims[item.id] ?: 0
        RewardDetails(item, balance, claimed, busy, onDismiss = { if (!busy) rewardToClaim = null }) {
            action {
                val unavailable = item.unavailable(balance, claimed)
                check(unavailable == null) { unavailable.orEmpty() }
                val code = if (signedIn) {
                    val requestPreference = "reward_request_${item.id}"
                    val request = preferences.getString(requestPreference, null) ?: UUID.randomUUID().toString().also {
                        preferences.edit().putString(requestPreference, it).apply()
                    }
                    (api.rpc("ecotrack_redeem", JSONObject().put("reward_id", item.id).put("request_key", request)) as String)
                        .also { preferences.edit().remove(requestPreference).apply() }
                } else {
                    balance -= item.cost
                    rewards = rewards.map { if (it.id == item.id) it.copy(stock = it.stock - 1) else it }
                    rewardClaims = rewardClaims + (item.id to claimed + 1)
                    UUID.randomUUID().toString()
                }
                val instant = Instant.now()
                val contribution = item.category == "Donaciones"
                if (rewardReceipts.none { it.id == code }) {
                    rewardReceipts = listOf(RewardReceipt(code, item.id, item.cost, instant, contribution,
                        if (contribution) instant else null)) + rewardReceipts
                }
                if (!signedIn) saveDemo()
                rewardToClaim = null
                openReceipt(code)
                if (signedIn) refresh()
            }
        }
    }
}

@Composable
private fun PilotSignIn(title: String, onSignIn: () -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Text("Inicia sesión para continuar.", style = MaterialTheme.typography.bodyMedium)
    Button(onClick = onSignIn) { Text("Ir a mi cuenta") }
}

@Composable
private fun DeliveryQr(
    delivery: DeliveryRecord,
    busy: Boolean,
    onHistory: () -> Unit,
    onRetake: () -> Unit,
    onRefresh: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Tu QR de entrega", style = MaterialTheme.typography.headlineLarge)
        Text("Muéstralo al recolector para validar tu entrega.", style = MaterialTheme.typography.bodyMedium)
    }
    MaterialLabel(delivery.material)
    Surface(Modifier.fillMaxWidth(), color = Mint, shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DeliveryStatus("Pendiente de validar", "pending")
            val qr = remember(delivery.code) { makeQr(delivery.code) }
            Image(qr.asImageBitmap(), "Código QR de la entrega", Modifier.size(188.dp).clip(RoundedCornerShape(14.dp)))
            Text("Código ${delivery.code.takeLast(8).uppercase(Locale.ROOT)}",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), color = Green)
        }
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RouteMetric(Modifier.weight(1f).fillMaxHeight(), "Al aprobarse", "+$DeliveryPoints", "puntos", Mint,
            "M22 12a10 10 0 1 1-20 0 10 10 0 1 1 20 0 M12 7l1.5 3.5 3.7.4-2.8 2.5.8 3.6-3.2-1.9-3.2 1.9.8-3.6-2.8-2.5 3.7-.4z")
        RouteMetric(Modifier.weight(1f).fillMaxHeight(), "Válido hasta", delivery.expires.deliveryTime(), "hora de Lima",
            Color(0xFFE8ECE4), "M22 12a10 10 0 1 1-20 0 10 10 0 1 1 20 0 M12 6v6l4 2")
    }
    val tip = when (delivery.material) {
        "Papel" -> "Entrega el papel seco y separado."
        "Cartón" -> "Dobla las cajas para que ocupen menos espacio."
        "Plástico" -> "Vacía y enjuaga los envases antes de entregarlos."
        "Vidrio" -> "Lleva los envases de vidrio vacíos y separados."
        else -> "Lleva el material limpio y separado."
    }
    Text(tip, style = MaterialTheme.typography.bodyMedium, color = Green)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            enabled = !busy, onClick = onRetake, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            LineIcon(CameraPath, Green, Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text("Repetir foto")
        }
        Button(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("Ver historial") }
    }
    if (onRefresh != null) TextButton(enabled = !busy, onClick = onRefresh) { Text("Actualizar estado") }
}

private fun deliveryStatusColors(state: String) = when (state) {
        "approved" -> Mint to Green
        "pending" -> Color(0xFFF1E7D2) to Color(0xFF674D18)
        "rejected" -> Color(0xFFF5E4DE) to Color(0xFF88452F)
        else -> Color(0xFFE8ECE4) to Color(0xFF465A4D)
}

@Composable
private fun DeliveryStatus(label: String, state: String) {
    val (container, foreground) = deliveryStatusColors(state)
    Surface(color = container, shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(Modifier.size(6.dp)) { drawCircle(foreground) }
            Text(label, style = MaterialTheme.typography.labelMedium, color = foreground)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeliveryHistory(history: List<DeliveryRecord>, now: Instant, onBack: () -> Unit) {
    var detailCode by rememberSaveable { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver a Perfil")
        }
        Text("Historial de entregas", style = MaterialTheme.typography.headlineMedium)
    }
    if (history.isEmpty()) {
        Text("Aún no hay entregas registradas.", style = MaterialTheme.typography.bodyMedium)
    }
    val groups = remember(history) {
        history.sortedByDescending { it.resolved ?: it.created }.groupBy { (it.resolved ?: it.created).deliveryDay() }
    }
    groups.forEach { (day, deliveries) ->
        Column {
            Text(day, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = Green)
            deliveries.forEachIndexed { index, delivery ->
                val state = delivery.statusAt(now)
                val (color, background, icon) = materialAppearance(delivery.material)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClickLabel = "Ver detalle de la entrega") { detailCode = delivery.code }
                        .heightIn(min = 60.dp).padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(color = background, shape = RoundedCornerShape(12.dp)) {
                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { LineIcon(icon, color, Modifier.size(20.dp)) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(delivery.material, style = MaterialTheme.typography.titleMedium)
                        Text(deliveryStatusLabel(state), style = MaterialTheme.typography.labelMedium,
                            color = deliveryStatusColors(state).second)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (state == "approved") {
                            Text("+$DeliveryPoints pts", style = MaterialTheme.typography.labelLarge, color = Green)
                        }
                        Text((delivery.resolved ?: delivery.created).deliveryTime(), style = MaterialTheme.typography.bodyMedium)
                    }
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(17.dp), tint = Green)
                }
                if (index < deliveries.lastIndex) HorizontalDivider(Modifier.padding(start = 50.dp))
            }
        }
    }
    history.firstOrNull { it.code == detailCode }?.let { delivery ->
        val state = delivery.statusAt(now)
        ModalBottomSheet(onDismissRequest = { detailCode = null }, containerColor = Cream) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(delivery.material, style = MaterialTheme.typography.headlineMedium)
                DeliveryStatus(deliveryStatusLabel(state), state)
                Text("QR generado: ${delivery.created.deliveryDate()}", style = MaterialTheme.typography.bodyMedium)
                delivery.resolved?.let {
                    val event = when (state) {
                        "approved" -> "Confirmado"
                        "cancelled" -> "Anulado"
                        else -> "Revisado"
                    }
                    Text("$event: ${it.deliveryDate()}", style = MaterialTheme.typography.bodyMedium)
                }
                if (state == "approved") Text("+$DeliveryPoints puntos", style = MaterialTheme.typography.labelLarge, color = Green)
                Text("Código ${delivery.code.takeLast(8).uppercase(Locale.ROOT)}",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp), color = Green)
            }
        }
    }
}

private fun deliveryStatusLabel(state: String) = when (state) {
    "approved" -> "Entregado"
    "rejected" -> "Rechazado"
    "cancelled" -> "QR anulado"
    "expired" -> "QR vencido"
    else -> "Pendiente"
}

@Composable
private fun EcoTrackHeader(balance: Int, onBalanceClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = Green, shape = RoundedCornerShape(14.dp)) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ecotrack_mark), contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            "EcoTrack",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.65).sp)
        )
        Surface(onClick = onBalanceClick, color = Mint, shape = RoundedCornerShape(16.dp)) {
            Row(
                Modifier.heightIn(min = 44.dp).padding(horizontal = 11.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LineIcon("M22 12a10 10 0 1 1-20 0 10 10 0 1 1 20 0 M12 7l1.5 3.5 3.7.4-2.8 2.5.8 3.6-3.2-1.9-3.2 1.9.8-3.6-2.8-2.5 3.7-.4z",
                    Green, Modifier.size(19.dp))
                Text("$balance", style = MaterialTheme.typography.titleMedium, color = Ink)
                Text("pts", style = MaterialTheme.typography.labelMedium, color = Green)
            }
        }
    }
}

private fun materialAppearance(material: String) = when (material) {
        "Papel" -> Triple(Color(0xFF285785), Color(0xFFE3EBF4), "M6 3h8l4 4v14H6zM14 3v5h4M9 12h6m-6 4h6")
        "Cartón" -> Triple(Color(0xFF825128), Color(0xFFF3E8DA), "m3 7 9-4 9 4v10l-9 4-9-4zM3 7l9 4 9-4m-9 4v10M7.5 5l9 4")
        "Vidrio" -> Triple(Color(0xFF6B407F), Color(0xFFF0E7F3), "M10 3h4v5l3 4v8a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1v-8l3-4zM7 14h10")
        "Plástico" -> Triple(Color(0xFF14645F), Color(0xFFDCEEEA), "M10 3h4m-3 0v4l-3 3v9a2 2 0 0 0 2 2h4a2 2 0 0 0 2-2v-9l-3-3V3M8 12h8m-8 5h8")
        else -> Triple(Green, Mint, "M4 5h16v14H4zM8 9h8m-8 5h6")
}

@Composable
private fun CapturePanel(
    title: String,
    detail: String,
    button: String,
    icon: String,
    photo: Bitmap? = null,
    analyzing: Boolean = false,
    result: RecyclingGuess? = null,
    issue: String = "",
    onCapture: () -> Unit
) {
    Surface(color = Mint, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val tile = Modifier.size(54.dp).clip(RoundedCornerShape(15.dp))
                if (photo != null) {
                    val bitmap = remember(photo) { photo.asImageBitmap() }
                    Image(bitmap, "Foto del material", tile, contentScale = ContentScale.Crop)
                } else {
                    Surface(tile, color = Cream) {
                        Box(contentAlignment = Alignment.Center) { LineIcon(icon, Green, Modifier.size(25.dp)) }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val heading = when {
                        analyzing -> if (icon == QrPath) "Leyendo código…" else "Analizando foto…"
                        result?.confident == true -> "Sugerencia: ${result.material}"
                        result != null -> "Podría ser ${result.material.lowercase()}"
                        photo != null || issue.isNotBlank() -> "Material sin confirmar"
                        else -> title
                    }
                    Text(heading, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            analyzing -> if (icon == QrPath) "Comprobando el tipo de QR." else "Buscando el material en el teléfono."
                            issue.isNotBlank() -> issue
                            result?.confident == true -> "Revisa la selección antes de generar el QR."
                            result != null || photo != null -> "Elige el material abajo o prueba con otra foto."
                            else -> detail
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (analyzing) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
            } else {
                FilledTonalButton(
                    onClick = onCapture,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Cream, contentColor = Green)
                ) { Text(button) }
            }
        }
    }
}

@Composable
private fun MaterialChoice(material: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val (color, background, path) = materialAppearance(material)
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier.clip(shape).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        color = if (selected) background else Color(0xFFECEEE6), shape = shape
    ) {
        Row(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineIcon(path, color, Modifier.size(20.dp))
            Text(material, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Ink)
            if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MaterialLabel(material: String) {
    val (color, background, path) = materialAppearance(material)
    Surface(color = background, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            LineIcon(path, color, Modifier.size(18.dp))
            Text(material, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
private fun RouteMetric(modifier: Modifier, label: String, value: String, unit: String, background: Color, path: String) {
    Surface(modifier, color = background, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LineIcon(path, Green, Modifier.size(17.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = Green)
            }
            Text(value, style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"), color = Ink)
            Text(unit, style = MaterialTheme.typography.labelLarge, color = Green)
        }
    }
}

@Composable
internal fun LineIcon(path: String, color: Color, modifier: Modifier = Modifier.size(22.dp)) {
    val drawing = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier) {
        val factor = size.minDimension / 24f
        scale(factor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(drawing, color, style = Stroke(width = 1.75f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun Leaflet(
    points: List<Point>,
    route: WalkingRoute?,
    location: android.location.Location?,
    centerRequest: Int,
    onPoint: (Point) -> Unit
) {
    val context = LocalContext.current
    val json = JSONArray()
    points.forEachIndexed { index, point ->
        val kind = if (point.materials.size == 1) Materials.indexOf(point.materials.first()) else -1
        json.put(JSONObject().put("index", index).put("name", point.name).put("lat", point.lat)
            .put("lng", point.lng).put("kind", kind).put("accepts", point.materials.joinToString(", ")))
    }
    val data by rememberUpdatedState(json.toString())
    val line by rememberUpdatedState(route?.line?.toString() ?: "[]")
    val position by rememberUpdatedState(location?.let {
        JSONArray().put(it.latitude).put(it.longitude).put(it.accuracy.toDouble()).toString()
    } ?: "null")
    val center by rememberUpdatedState(centerRequest)
    val openPoint by rememberUpdatedState { index: Int -> points.getOrNull(index)?.let(onPoint) }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(315.dp),
        factory = {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowContentAccess = false
                settings.allowFileAccess = false
                settings.userAgentString += " EcoTrack/0.1"
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                        val uri = request.url
                        if (request.isForMainFrame && uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
                            uri.pathSegments.firstOrNull() == "point") {
                            uri.lastPathSegment?.toIntOrNull()?.let(openPoint)
                        } else if (request.isForMainFrame && uri.scheme == "https" &&
                            uri.host in setOf("www.openstreetmap.org", "routing.openstreetmap.de")) {
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                        }
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        view.tag = true
                        view.evaluateJavascript("window.showPoints?.($data); window.showLocation?.($position, $center); window.showRoute?.($line);", null)
                    }
                }
                val html = context.assets.open("map.html").bufferedReader().use { it.readText() }.trimStart('\uFEFF')
                val css = context.assets.open("leaflet.css").bufferedReader().use { it.readText() }
                val script = context.assets.open("leaflet.js").bufferedReader().use { it.readText() }
                val page = html.replace("""<link rel="stylesheet" href="leaflet.css">""", "<style>$css</style>")
                    .replace("""<script src="leaflet.js"></script>""", "<script>$script\n</script>")
                loadDataWithBaseURL("https://appassets.androidplatform.net/", page, "text/html", "UTF-8", null)
            }
        },
        onRelease = { it.destroy() },
        update = { view ->
            val render = "window.showPoints?.($data); window.showLocation?.($position, $center); window.showRoute?.($line);"
            if (view.tag == true) view.evaluateJavascript(render, null)
        }
    )
}

internal fun makeQr(code: String): Bitmap {
    val matrix = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 480, 480)
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

internal fun readQr(bitmap: Bitmap): String {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val source = RGBLuminanceSource(width, height, pixels)
    return MultiFormatReader().decode(
        BinaryBitmap(HybridBinarizer(source)),
        mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)
    ).text
}






