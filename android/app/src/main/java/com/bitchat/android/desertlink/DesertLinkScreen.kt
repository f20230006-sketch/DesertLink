package com.bitchat.android.desertlink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.bitchat.android.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.ui.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Forest = Color(0xFF213B32)
private val Sand = Color(0xFFF5F3ED)
private val Orange = Color(0xFFCB632D)
private val Muted = Color(0xFF748071)
private val DesertFont = FontFamily(Font(R.font.desertlink_sans))
internal val DesertTypography = Typography().let { base -> base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = DesertFont),
    displayMedium = base.displayMedium.copy(fontFamily = DesertFont),
    displaySmall = base.displaySmall.copy(fontFamily = DesertFont),
    headlineLarge = base.headlineLarge.copy(fontFamily = DesertFont),
    headlineMedium = base.headlineMedium.copy(fontFamily = DesertFont),
    headlineSmall = base.headlineSmall.copy(fontFamily = DesertFont),
    titleLarge = base.titleLarge.copy(fontFamily = DesertFont),
    titleMedium = base.titleMedium.copy(fontFamily = DesertFont),
    titleSmall = base.titleSmall.copy(fontFamily = DesertFont),
    bodyLarge = base.bodyLarge.copy(fontFamily = DesertFont),
    bodyMedium = base.bodyMedium.copy(fontFamily = DesertFont),
    bodySmall = base.bodySmall.copy(fontFamily = DesertFont),
    labelLarge = base.labelLarge.copy(fontFamily = DesertFont),
    labelMedium = base.labelMedium.copy(fontFamily = DesertFont),
    labelSmall = base.labelSmall.copy(fontFamily = DesertFont)
) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesertLinkScreen(store: RescueStore, readiness: RadioReadiness = RadioReadiness(),
    updater: AppUpdater? = null, onEnableBluetooth: () -> Unit = {}, onInstallUpdate: () -> Unit = {}) {
    val state by store.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("Active") }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var composer by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RescueRecord?>(null) }
    var raw by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Breakdown") }
    var people by remember { mutableIntStateOf(1) }
    var landmark by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var openedAt by remember { mutableLongStateOf(0L) }
    var trust by remember(state.trustedRescue) { mutableStateOf(state.trustedRescue) }
    var resolving by remember { mutableStateOf<RescueRecord?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val selected = state.incidents.firstOrNull { it.incident == detailId }
    val owned = state.incidents.filter { it.fingerprint == state.fingerprint }
    val active = state.incidents.filter { it.expires > state.now && !RescueOperations.isResolved(it) }
    val actionable = active.count { it.fingerprint != state.fingerprint && state.evidence.none { ack -> ack.kind == "ack" && ack.target == it.id && ack.fingerprint == state.fingerprint } }
    fun openComposer(record: RescueRecord? = null) { editing = record; raw = record?.text ?: ""; notes = ""; landmark = ""; people = 1; category = "Breakdown"; openedAt = state.operation; composer = true }
    LaunchedEffect(state.operation) {
        if (state.operation > 0 && state.actionSucceeded) {
            if (composer && state.operation > openedAt) composer = false
            snackbar.showSnackbar(state.notice)
        }
    }
    androidx.activity.compose.BackHandler(enabled = detailId != null) { detailId = null }
    MaterialTheme(typography = DesertTypography, colorScheme = lightColorScheme(primary = Forest, secondary = Orange,
        background = Sand, surface = Sand, onPrimary = Color.White, onSurface = Forest, onBackground = Forest)) {
        Scaffold(containerColor = Sand, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            NavigationBar(containerColor = Color(0xFFEAEEE5), tonalElevation = 0.dp) {
                listOf("Operations", "Network", "Demo").forEachIndexed { index, label ->
                    NavigationBarItem(selected = tab == index && detailId == null, onClick = { tab = index; detailId = null },
                        icon = { Icon(when (index) { 0 -> Icons.Outlined.Home; 1 -> Icons.Outlined.Bluetooth; else -> Icons.Outlined.Science }, contentDescription = null) },
                        label = { Text(label, fontSize = 12.sp) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selected != null) IconButton(onClick = { detailId = null }) { Icon(Icons.Outlined.ArrowBack, "Back to incidents") }
                    Column(Modifier.weight(1f)) {
                        Text("DESERTLINK", fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, letterSpacing = (-1).sp)
                        Text(if (selected != null) "INCIDENT DETAILS" else "BLUETOOTH RESCUE NETWORK", fontSize = 9.sp, letterSpacing = 1.2.sp, color = Muted)
                    }
                    Surface(color = if (state.networkRunning) Color(0xFFE0E8D8) else Color(0xFFF2E2D2), shape = RoundedCornerShape(20.dp)) {
                        Text(if (state.networkRunning) "BLE ON" else "BLE OFF", Modifier.padding(10.dp, 6.dp), fontSize = 10.sp, color = Forest)
                    }
                }
                HorizontalDivider(color = Color(0xFFD5DBCF))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    state.error?.let { error ->
                        CardBlock { Text(error, color = Color(0xFFA54129), fontSize = 13.sp); TextButton(onClick = store::dismissError) { Text("Dismiss") } }
                    }
                    if (!state.ready && state.error == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Opening your saved incidents…", color = Muted)
                    } else if (selected != null) {
                        val mine = selected.fingerprint == state.fingerprint
                        val progress = RescueOperations.progress(selected, state.evidence, state.trustedRescue, state.now)
                        val localAck = state.evidence.any { it.kind == "ack" && it.target == selected.id && it.fingerprint == state.fingerprint }
                        Text(if (RescueOperations.isResolved(selected)) "Resolution · v${selected.revision}" else "SOS · v${selected.revision}", color = Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(selected.text, fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold)
                        CardBlock {
                            Text(if (mine) phaseTitle(progress.phase) else if (localAck) "You confirmed this revision" else "Saved on this device", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (mine) phaseExplanation(progress.phase) else if (state.role == "rescue") "Confirm the exact version you have read. The sender will see confirmation only after your ACK travels back." else "This phone carries a copy for onward Bluetooth forwarding.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                            if (mine) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf("Saved", "Carried", "Received", "Confirmed").forEachIndexed { i, label ->
                                    val reached = progress.phase != IncidentPhase.EXPIRED && progress.phase.ordinal >= i
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(if (reached) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null, tint = if (reached) Forest else Muted, modifier = Modifier.size(22.dp))
                                        Text(label, fontSize = 10.sp, color = Muted)
                                    }
                                }
                            }
                            Text("${progress.carriers} signed relay-copy receipt${if (progress.carriers == 1) "" else "s"} for this revision", fontSize = 11.sp, color = Muted)
                            if (progress.untrustedAcknowledgment) Text("An ACK exists, but its signing identity is not your trusted rescue identity. Verify the operator’s code in Network.", color = Orange, fontSize = 12.sp)
                        }
                        Text("Created ${clock(selected.created)} · ${remaining(selected.expires, state.now)}", color = Muted, fontSize = 12.sp)
                        if (mine) {
                            Button(onClick = { openComposer(selected) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text(if (selected.expires <= state.now) "Renew this SOS" else "Update situation / urgency") }
                            if (!RescueOperations.isResolved(selected)) OutlinedButton(onClick = { resolving = selected }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text("Mark resolved and notify the network") }
                        } else if (state.role == "rescue" && selected.expires > state.now) {
                            Button(onClick = { store.acknowledge(selected) }, enabled = !localAck && !state.busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Orange), shape = RoundedCornerShape(8.dp)) { Text(if (localAck) "ACK saved for return journey" else "I have read and confirm v${selected.revision}") }
                        }
                        SectionTitle("LOCAL EVENT HISTORY")
                        val timeline = state.events.filter { it.incident == selected.incident }
                        if (timeline.isEmpty()) Text("No earlier events retained for this incident.", color = Muted, fontSize = 12.sp)
                        timeline.forEach { e -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(clock(e.time), color = Muted, fontSize = 11.sp)
                            Text("v${e.revision} · ${e.title}", fontSize = 12.sp, lineHeight = 19.sp)
                        } }
                    } else if (tab == 0) {
                        Text(when (state.role) { "rescue" -> "Rescue operations"; "relay" -> "Carry the connection."; else -> "You’re never just a dot\non the map." }, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                        Text(when (state.role) { "rescue" -> "Read incoming incidents. Confirm the exact revision you received."; "relay" -> "Nearby SOS messages stay saved here and travel at the next Bluetooth encounter."; else -> "Create an SOS without signal. A passing device can carry it to rescue." }, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                        if (!state.roleChosen) CardBlock {
                            Text("First, choose this phone’s role", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            Text("You can change it later. All three roles can carry messages.", color = Muted, fontSize = 12.sp)
                            RoleChoices(state.role, store::setRole)
                        }
                        if (!readiness.permissions || !readiness.enabled || !state.networkRunning) CardBlock {
                            Text(when { !readiness.supported -> "Bluetooth is not available on this device"; !readiness.permissions -> "Allow nearby-device access"; !readiness.enabled -> "Turn Bluetooth on"; else -> "Starting the Bluetooth network…" }, fontWeight = FontWeight.SemiBold)
                            Text("No Wi-Fi or mobile data is needed. You can still create an SOS while offline; it stays on this phone.", fontSize = 12.sp, lineHeight = 19.sp, color = Muted)
                            if (readiness.supported) OutlinedButton(onClick = onEnableBluetooth) { Text(if (!readiness.permissions) "Set up Bluetooth" else "Start / retry Bluetooth") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stat("NEARBY PEERS", "${state.peers}", Modifier.weight(1f))
                            Stat(if (state.role == "rescue") "TO CONFIRM" else "ACTIVE SOS", "${if (state.role == "rescue") actionable else active.size}", Modifier.weight(1f))
                        }
                        if (!state.forwarding) Notice("Forwarding is paused. Received messages remain saved.")
                        Button(onClick = { openComposer() }, enabled = state.ready && !state.busy, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange), shape = RoundedCornerShape(8.dp)) { Icon(Icons.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Create an SOS", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Active", "Mine", "Received", "History").forEach { name -> FilterChip(selected = filter == name, onClick = { filter = name }, label = { Text(name) }) }
                        }
                        val visible = when (filter) { "Mine" -> owned; "Received" -> state.incidents.filter { it.fingerprint != state.fingerprint }; "History" -> state.incidents; else -> active }
                        if (visible.isEmpty()) CardBlock {
                            Text(if (filter == "Active") "No active incidents" else "Nothing here yet", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(if (state.peers == 0) "No nearby peer is connected. An SOS you create will wait safely in local storage." else "This view fills when an incident is created or received.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                            TextButton(onClick = { tab = 2 }) { Text("Try the three-node offline demo") }
                        }
                        visible.forEach { r ->
                            val progress = RescueOperations.progress(r, state.evidence, state.trustedRescue, state.now)
                            Surface(onClick = { detailId = r.incident }, color = Color(0xFFFAF9F4), shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5DBCF)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${if (RescueOperations.isResolved(r)) "RESOLVED" else "SOS"} · v${r.revision}", color = Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(if (r.fingerprint == state.fingerprint) "YOURS" else "RECEIVED", fontSize = 10.sp, color = Muted) }
                                    Text(r.text, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, maxLines = 3)
                                    Text(if (r.fingerprint == state.fingerprint) phaseTitle(progress.phase) else if (r.expires <= state.now) "Expired · retained in history" else "Saved here · open for details", fontSize = 12.sp, color = Forest)
                                    Text("${remaining(r.expires, state.now)} · View journey →", fontSize = 10.sp, color = Muted)
                                }
                            }
                        }
                    } else if (tab == 1) {
                        Text("Your network,\nyour control.", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
                        CardBlock {
                            Text("Bluetooth operations", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("${state.peers} nearby mesh peers · ${state.records.size} active records · ${state.transmissions} transmit requests", fontSize = 12.sp, color = Muted)
                            SettingSwitch("Forward stored messages", "Pause outgoing offers without deleting incidents.", state.forwarding, store::forwarding)
                            SettingSwitch("Conserve battery", "Retry older records every 45 seconds instead of 15.", state.batterySaver, store::batterySaver)
                            OutlinedButton(onClick = store::syncNow, enabled = !state.busy && state.forwarding) { Text("Offer pending records at next contact") }
                            Text("A transmit request does not prove delivery. Only signed receipts and the exact operator ACK change the incident’s status.", fontSize = 11.sp, lineHeight = 18.sp, color = Muted)
                        }
                        CardBlock {
                            Text("This phone’s role", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            RoleChoices(state.role, store::setRole)
                            Text("Traveller creates an SOS. Relay carries copies. Rescue reads and explicitly confirms them. Every role can forward.", fontSize = 12.sp, lineHeight = 19.sp, color = Muted)
                        }
                        CardBlock {
                            Text("Trust the rescue identity", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("Verify this full code directly with your rescue operator before treating an ACK as trusted.", fontSize = 12.sp, lineHeight = 19.sp, color = Muted)
                            OutlinedTextField(value = trust, onValueChange = { trust = it }, label = { Text("Rescue fingerprint or pairing code") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { store.trust(trust) }, enabled = !state.busy && (trust.isBlank() || trust.trim().lowercase().removePrefix("desertlink:rescue:").replace(" ", "").matches(Regex("[0-9a-f]{64}")))) { Text("Save trusted identity") }
                            Text("THIS DEVICE’S SIGNING FINGERPRINT", fontSize = 9.sp, color = Muted, letterSpacing = 1.sp)
                            SelectionContainer { Text(state.fingerprint.chunked(8).joinToString(" "), fontSize = 11.sp, lineHeight = 19.sp) }
                            TextButton(onClick = { clipboard.setText(AnnotatedString("desertlink:rescue:${state.fingerprint}")) }) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp)); Spacer(Modifier.width(7.dp)); Text("Copy pairing code") }
                            Text("A role label is not proof of authority. Public SOS payloads are signed, not encrypted.", fontSize = 11.sp, color = Orange, lineHeight = 18.sp)
                        }
                        if (updater != null) UpdatePanel(updater, onInstallUpdate)
                        Text("BLE delivery depends on future contacts and Android allowing the foreground service to run. No delivery is guaranteed across a permanent gap.", fontSize = 11.sp, color = Muted, lineHeight = 18.sp)
                    } else OfflineDemoContent()
                    Spacer(Modifier.height(4.dp))
                    Text("Hackathon prototype · Bitchat BLE foundation · GPL-3.0\nUse synthetic details for testing. This is not a production emergency service.", fontSize = 10.sp, lineHeight = 16.sp, color = Muted)
                }
            }
        }
        if (composer) {
            val message = if (editing != null) raw else RescueOperations.composeSituation(category, people, landmark, notes)
            ModalBottomSheet(onDismissRequest = { if (!state.busy) composer = false }, containerColor = Sand) {
                Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Text(if (editing == null) "What help do you need?" else "Update to v${editing!!.revision + 1}", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Text("Saved locally first. Your update waits here if nobody is in Bluetooth range.", fontSize = 12.sp, color = Muted, lineHeight = 19.sp)
                    if (editing == null) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Breakdown", "Medical", "Water", "Lost", "Other").forEach { kind -> FilterChip(selected = category == kind, onClick = { category = kind }, label = { Text(kind) }) } }
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("People needing help", Modifier.weight(1f)); OutlinedButton(onClick = { people-- }, enabled = people > 1) { Text("−") }; Text("$people", Modifier.padding(horizontal = 12.dp)); OutlinedButton(onClick = { people++ }, enabled = people < 20) { Text("+") } }
                        OutlinedTextField(value = landmark, onValueChange = { if (it.length <= 45) landmark = it }, label = { Text("Nearest landmark (optional)") }, supportingText = { Text("Enter a description manually. No GPS is collected.", fontSize = 10.sp) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = notes, onValueChange = { if (it.length <= 120) notes = it }, label = { Text("Situation / urgency") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    } else OutlinedTextField(value = raw, onValueChange = { if (it.length <= 160) raw = it }, label = { Text("Updated situation") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    CardBlock { Text("MESSAGE PREVIEW · ${message.length}/160", fontSize = 10.sp, color = if (message.length > 160) Orange else Muted); Text(message, fontSize = 14.sp, lineHeight = 21.sp) }
                    if (state.error != null) Text(state.error!!, color = Orange, fontSize = 12.sp)
                    Button(onClick = { store.send(message, editing?.incident) }, enabled = !state.busy && message.isNotBlank() && message.length <= 160, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange), shape = RoundedCornerShape(8.dp)) { Text(if (state.busy) "Saving on this phone…" else if (editing == null) "Save SOS and wait for a contact" else "Save the new revision") }
                }
            }
        }
        resolving?.let { record -> AlertDialog(onDismissRequest = { resolving = null }, title = { Text("Mark this incident resolved?") }, text = { Text("A new signed revision will say assistance is no longer requested. Devices that are out of range may still have the older SOS until they encounter the update.") }, confirmButton = { TextButton(onClick = { store.resolve(record); resolving = null }) { Text("Save resolution revision") } }, dismissButton = { TextButton(onClick = { resolving = null }) { Text("Keep incident active") } }) }
    }
}

@Composable private fun RoleChoices(role: String, select: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf("traveller", "relay", "rescue").forEach { value -> FilterChip(selected = role == value, onClick = { select(value) }, label = { Text(value.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) }) }
    }
}
@Composable private fun SectionTitle(text: String) { Text(text, fontSize = 10.sp, letterSpacing = 1.4.sp, color = Muted) }
@Composable private fun Notice(text: String) { Surface(color = Color(0xFFF2E2D2), shape = RoundedCornerShape(6.dp)) { Text(text, Modifier.padding(12.dp), fontSize = 12.sp, lineHeight = 18.sp, color = Forest) } }
@Composable private fun SettingSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium); Text(description, color = Muted, fontSize = 11.sp, lineHeight = 17.sp) }
        Switch(checked = checked, onCheckedChange = change, modifier = Modifier.semantics { contentDescription = title })
    }
}
@Composable private fun UpdatePanel(updater: AppUpdater, install: () -> Unit) {
    val update by updater.state.collectAsStateWithLifecycle()
    CardBlock {
        Text("App updates · ${com.bitchat.android.BuildConfig.VERSION_NAME}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Internet downloads the app only. Rescue incidents use Bluetooth exclusively.", fontSize = 12.sp, lineHeight = 19.sp, color = Muted)
        SettingSwitch("Check for updates when online", "At most once every six hours; no incident data is sent.", update.automaticChecks) { updater.configure(it, update.downloadOnWifi) }
        SettingSwitch("Download updates on Wi-Fi", "Optional download. Android always asks before installation.", update.downloadOnWifi) { updater.configure(update.automaticChecks, it) }
        Text(update.message, fontSize = 12.sp, color = Muted, lineHeight = 19.sp)
        if (update.downloading) { LinearProgressIndicator(progress = { update.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Orange); TextButton(onClick = updater::cancelDownload) { Text("Cancel download · ${update.progress}%") } }
        if (update.readyFile != null) Button(onClick = install) { Text("Install verified update") }
        else if (update.offer != null && !update.downloading) Button(onClick = updater::download) { Text("Download ${update.offer!!.version}") }
        OutlinedButton(onClick = { updater.check() }, enabled = !update.checking && !update.downloading) { Text(if (update.checking) "Checking…" else "Check for updates") }
        Text("First installation may require Android’s ‘Allow from this source’ setting. Installation restarts the app; incidents remain saved.", fontSize = 10.sp, lineHeight = 17.sp, color = Muted)
    }
}
private fun phaseTitle(phase: IncidentPhase): String = when (phase) {
    IncidentPhase.QUEUED -> "Saved · waiting for a confirmed contact"
    IncidentPhase.CARRIED -> "A relay has a copy"
    IncidentPhase.DELIVERED -> "Trusted rescue node received this version"
    IncidentPhase.CONFIRMED -> "Trusted operator confirmed this version"
    IncidentPhase.EXPIRED -> "Expired · kept in local history"
}
private fun phaseExplanation(phase: IncidentPhase): String = when (phase) {
    IncidentPhase.QUEUED -> "Stored on this phone. A transmit attempt alone is not proof that another device has it."
    IncidentPhase.CARRIED -> "A device returned a signed receipt saying it saved a copy. Rescue delivery is still unconfirmed."
    IncidentPhase.DELIVERED -> "The trusted rescue node returned a receipt. A human operator has not yet confirmed this revision."
    IncidentPhase.CONFIRMED -> "The exact revision was acknowledged by the rescue identity you trust. This does not itself mean rescue was dispatched."
    IncidentPhase.EXPIRED -> "The forwarding lifetime ended. Renew the SOS to create a fresh revision; the event history stays available."
}
private fun remaining(expires: Long, now: Long) = if (expires <= now) "Forwarding lifetime ended" else "${((expires - now + 59_999) / 60_000)} min left to forward"

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFFEAECE2), RoundedCornerShape(6.dp)).padding(12.dp)) {
        Text(label, fontSize = 8.sp, letterSpacing = 0.6.sp, color = Muted)
        Text(value, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, color = Forest)
    }
}
@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0xFFFAF9F4), shape = RoundedCornerShape(9.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5DBCF)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
private fun clock(time: Long): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(time))

@Composable
fun OfflineDemoContent() {
    val scope = rememberCoroutineScope()
    var demo by remember { mutableStateOf<RescueDemo?>(null) }
    var demoState by remember { mutableStateOf<DemoSnapshot?>(null) }
    var demoBusy by remember { mutableStateOf(false) }
    var autoplay by remember { mutableStateOf(false) }
    LaunchedEffect(autoplay) {
        if (autoplay) {
            while (demo != null && (demoState?.step ?: 8) < 8) {
                delay(if (demoState?.step == 5) 4500L else 2500L)
                demoState = withContext(Dispatchers.Default) { demo!!.advance() }
            }
            autoplay = false
        }
    }
    LaunchedEffect(Unit) { val created = withContext(Dispatchers.Default) { RescueDemo() }; demo = created; demoState = created.snapshot() }
                        Text("Make the network fail.\nWatch the message survive.", fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                        Text("ON-DEVICE SIMULATION · NO PHYSICAL PEERS", color = Orange, fontSize = 9.sp, letterSpacing = 1.sp)
                        Text("Three separate ledgers, explicit contacts, and real signed incident records. This demo does not transmit over Bluetooth.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                        demoState?.let { d ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Stat("SOURCE", "v${d.sourceRevision}", Modifier.weight(1f))
                                Stat("RELAY", "v${d.relayRevision}", Modifier.weight(1f))
                                Stat("RESCUE", "v${d.baseRevision}", Modifier.weight(1f))
                            }
                            LinearProgressIndicator(progress = { d.step / 8f }, modifier = Modifier.fillMaxWidth(), color = Orange)
                            CardBlock {
                                Text("STEP ${d.step} OF 8", color = Orange, fontSize = 10.sp, letterSpacing = 1.sp)
                                Text(d.title, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
                                Text(d.detail, color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                                Text(if (d.confirmed) "✓ Latest revision confirmed" else "Latest revision is NOT confirmed", color = if (d.confirmed) Forest else Orange, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Button(onClick = { scope.launch { demoBusy = true; try { demoState = withContext(Dispatchers.Default) { demo!!.advance() } } finally { demoBusy = false } } },
                                enabled = !demoBusy && !autoplay && d.step < 8, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange), shape = RoundedCornerShape(8.dp)) {
                                Text(if (d.step == 0) "Start the demo" else if (d.step == 8) "Journey complete" else "Next contact / event →")
                            }
                            OutlinedButton(onClick = { autoplay = !autoplay }, enabled = !demoBusy && d.step < 8, modifier = Modifier.fillMaxWidth()) { Text(if (autoplay) "Pause automatic walkthrough" else "Play full demo automatically") }
                            TextButton(onClick = { scope.launch { demoBusy = true; try { val fresh = withContext(Dispatchers.Default) { RescueDemo() }; demo = fresh; demoState = fresh.snapshot() } finally { demoBusy = false } } }, enabled = !demoBusy && !autoplay) { Text("Reset simulated nodes") }
                        }
}
