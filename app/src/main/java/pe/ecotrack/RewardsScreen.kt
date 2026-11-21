package pe.ecotrack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val rewardCategories = listOf("Todas", "Materiales", "Servicios", "Ayuda", "Donaciones")
private val coinPath = "M22 12a10 10 0 1 1-20 0 10 10 0 1 1 20 0 M12 7l1.5 3.5 3.7.4-2.8 2.5.8 3.6-3.2-1.9-3.2 1.9.8-3.6-2.8-2.5 3.7-.4z"
private const val GiftPath = "M4 9h16v12H4zM3 6h18v4H3zM12 6v15M12 6c-1-4-6-4-5-1 1 2 4 1 5 1Zm0 0c1-4 6-4 5-1-1 2-4 1-5 1z"

@Composable
internal fun AccountActivityLink(title: String, subtitle: String, path: String, onOpen: () -> Unit) {
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(13.dp)) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    LineIcon(path, MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun RewardHistory(
    receipts: List<RewardReceipt>,
    rewards: List<Reward>,
    onBack: () -> Unit,
    onReceipt: (String) -> Unit,
    onCatalog: () -> Unit
) {
    RewardPageTitle("Mis premios", "Volver a Tu cuenta", onBack)
    if (receipts.isEmpty()) {
        Text("Tus premios aparecerán aquí después de canjearlos.")
        Button(onClick = onCatalog) { Text("Explorar premios") }
        return
    }
    val catalog = remember(rewards) { rewards.associateBy { it.id } }
    val groups = remember(receipts) { receipts.groupBy { it.canCollect } }
    listOf(true, false).forEach { awaiting ->
        val group = groups[awaiting].orEmpty()
        if (group.isNotEmpty()) {
            Column {
                Text(if (awaiting) "Por reclamar" else "Completados",
                    Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                group.forEachIndexed { index, receipt ->
                    Surface(onClick = { onReceipt(receipt.id) }, shape = RoundedCornerShape(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                                Box(Modifier.size(39.dp), contentAlignment = Alignment.Center) {
                                    LineIcon(GiftPath, MaterialTheme.colorScheme.primary, Modifier.size(20.dp))
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(catalog[receipt.rewardId]?.name ?: "Premio", style = MaterialTheme.typography.titleMedium)
                                Text(if (awaiting) "Ver QR" else receipt.status, style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${receipt.cost} pts", style = MaterialTheme.typography.labelLarge)
                                Text((receipt.fulfilled ?: receipt.created).deliveryDay(), style = MaterialTheme.typography.labelMedium)
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (index < group.lastIndex) HorizontalDivider(Modifier.padding(start = 49.dp))
                }
            }
        }
    }
}

@Composable
internal fun RewardReceiptScreen(
    receipt: RewardReceipt, reward: Reward, onBack: () -> Unit, onRefresh: (() -> Unit)?
) {
    RewardPageTitle(if (receipt.canCollect) "Reclama tu premio" else "Comprobante", "Volver a Mis premios", onBack)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(reward.name, style = MaterialTheme.typography.titleLarge)
        Text(reward.category, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(receipt.status, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (receipt.canCollect) {
                val qr = remember(receipt.id) { makeQr(ReviewCode.rewardPayload(receipt.id)).asImageBitmap() }
                Image(qr, "QR para reclamar el premio", Modifier.size(196.dp).clip(RoundedCornerShape(14.dp)))
            } else {
                LineIcon(if (receipt.contribution) GiftPath else "M4 12l5 5L20 6", MaterialTheme.colorScheme.primary, Modifier.size(36.dp))
            }
            Text("Código ${receipt.id.takeLast(8).uppercase(Locale.ROOT)}", style = MaterialTheme.typography.labelLarge)
        }
    }
    if (receipt.canCollect) {
        Text("Muestra este QR al recolector cuando recibas o actives el premio.", style = MaterialTheme.typography.bodyMedium)
        if (reward.fulfillment.isNotBlank()) Text(reward.fulfillment, style = MaterialTheme.typography.bodyMedium)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${receipt.cost} puntos canjeados", style = MaterialTheme.typography.labelLarge)
        Text(receipt.created.deliveryDate(), style = MaterialTheme.typography.labelMedium)
    }
    if (!receipt.contribution) receipt.fulfilled?.let {
        Text("Recibido el ${it.deliveryDate()}", style = MaterialTheme.typography.bodyMedium)
        if (reward.catalogKey == "guia-reciclaje-cajamarca") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tu guía de separación", style = MaterialTheme.typography.titleLarge)
                Text("Papel y cartón. Sepáralos secos y sin grasa. Aplana las cajas para facilitar el transporte.")
                Text("Plástico. Vacía las botellas y déjalas secar. Consulta si el recolector acepta las tapas por separado.")
                Text("Vidrio. Entrega botellas y frascos vacíos. Mantén los vidrios rotos separados y avisa al recolector.")
                Text("Antes de salir, confirma los materiales y el horario con el punto de acopio.")
            }
        }
    }
    if (reward.sample) Text("Canje de ejemplo", style = MaterialTheme.typography.labelMedium)
    if (receipt.canCollect && onRefresh != null) TextButton(onClick = onRefresh) { Text("Actualizar estado") }
}

@Composable
private fun RewardPageTitle(title: String, backLabel: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel) }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RewardsCatalog(
    rewards: List<Reward>,
    balance: Int,
    claims: Map<String, Int>,
    category: String,
    onCategory: (String) -> Unit,
    onOpen: (Reward) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Premios", style = MaterialTheme.typography.headlineLarge)
        Text("Canjea tus puntos por algo útil o apoya una causa.", style = MaterialTheme.typography.bodyMedium)
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LineIcon(coinPath, MaterialTheme.colorScheme.primary, Modifier.size(27.dp))
            Text("Puntos disponibles", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("$balance", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        rewardCategories.forEach { name ->
            FilterChip(selected = name == category, onClick = { onCategory(name) },
                label = { Text(name, style = MaterialTheme.typography.labelMedium) }, border = null,
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant))
        }
    }
    val visible = rewards.filter { it.pilotActive && (category == "Todas" || it.category == category) }
    if (visible.isEmpty()) {
        Text("Aún no hay premios en esta categoría.", style = MaterialTheme.typography.bodyMedium)
    } else if (visible.all { it.sample }) {
        Text("Catálogo de ejemplo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
    visible.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val itemModifier = Modifier.weight(1f).fillMaxHeight()
            pair.forEach { reward ->
                val claimed = claims[reward.id] ?: 0
                RewardCard(reward, claimed, itemModifier) { onOpen(reward) }
            }
            if (pair.size == 1) Spacer(itemModifier)
        }
    }
}

@Composable
private fun RewardCard(reward: Reward, claimed: Int, modifier: Modifier, onOpen: () -> Unit) {
    Surface(onClick = onOpen, modifier = modifier, color = Color(0xFFF0F1E8), shape = RoundedCornerShape(17.dp)) {
        Column {
            RewardArtwork(reward, Modifier.fillMaxWidth().height(if (reward.photo == null) 62.dp else 95.dp))
            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(reward.name, style = MaterialTheme.typography.titleMedium)
                val status = when {
                    reward.stock <= 0 -> "Agotado"
                    claimed >= reward.maxPerUser -> "Límite alcanzado"
                    else -> "${reward.stock} disponibles"
                }
                Text("${reward.cost} pts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(status, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RewardDetails(
    reward: Reward,
    balance: Int,
    claimed: Int,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val unavailable = reward.unavailable(balance, claimed)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(reward.name, style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(reward.category, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("${reward.cost} pts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Surface(shape = RoundedCornerShape(18.dp)) {
                RewardArtwork(reward, Modifier.fillMaxWidth().height(if (reward.photo == null) 92.dp else 156.dp))
            }
            if (reward.details.isNotBlank()) Text(reward.details, style = MaterialTheme.typography.bodyLarge)
            if (reward.fulfillment.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Cómo se entrega", style = MaterialTheme.typography.labelLarge)
                    Text(reward.fulfillment, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RewardFact("${reward.stock}", "Disponibles", Modifier.weight(1f).fillMaxHeight())
                RewardFact("${reward.maxPerUser}", "Máximo por persona", Modifier.weight(1f).fillMaxHeight())
            }
            if (claimed > 0) Text("Ya canjeaste $claimed de ${reward.maxPerUser}.", style = MaterialTheme.typography.bodyMedium)
            if (reward.sample) Text("Premio de ejemplo · Canje simulado", style = MaterialTheme.typography.labelMedium)
            Button(onClick = onConfirm, enabled = !busy && unavailable == null, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Registrando…" else unavailable ?: "Canjear por ${reward.cost} puntos")
            }
        }
    }
}

@Composable
private fun RewardFact(number: String, label: String, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(number, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun RewardArtwork(reward: Reward, modifier: Modifier) {
    val context = LocalContext.current
    val photo by produceState<Bitmap?>(initialValue = null, reward.photo) {
        value = withContext(Dispatchers.IO) {
            reward.photo?.takeIf { it in setOf("gorro", "arbol", "botella") }?.let { name ->
                runCatching { context.assets.open("prizes/$name.webp").use(BitmapFactory::decodeStream) }.getOrNull()
            }
        }
    }
    val (surface, ink, path) = when (reward.category) {
        "Servicios" -> Triple(Color(0xFFE4EBF4), Color(0xFF285785), "M4 7h16v13H4zM4 11h16M8 4v5m8-5v5m-6 6h4")
        "Ayuda" -> Triple(Color(0xFFF2E5D6), Color(0xFF825128), "M3 14h5l3 2h3l5-3a2 2 0 0 1 2 2l-5 5H9l-6-2zM12 11l-3-3a2 2 0 0 1 3-3 2 2 0 0 1 3 3z")
        "Donaciones" -> Triple(Color(0xFFECE5F2), Color(0xFF67427C), "M12 20 4 12a4.5 4.5 0 0 1 8-4 4.5 4.5 0 0 1 8 4z")
        else -> Triple(Color(0xFFDDEBE0), Color(0xFF245643), "m3 7 9-4 9 4v10l-9 4-9-4zM3 7l9 4 9-4m-9 4v10")
    }
    Surface(modifier, color = surface) {
        val bitmap = photo
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) { LineIcon(path, ink, Modifier.size(38.dp)) }
        }
    }
}
