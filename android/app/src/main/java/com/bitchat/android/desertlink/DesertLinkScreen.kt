package com.bitchat.android.desertlink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable
fun DesertLinkScreen(viewModel: ChatViewModel) {
    val state by viewModel.rescue.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var compose by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RescueRecord?>(null) }
    var text by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    var trust by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val latest = state.records.filter { it.kind == "sos" }.sortedByDescending { it.created }
    val mine = latest.filter { it.fingerprint == state.fingerprint }

    MaterialTheme(typography = DesertTypography, colorScheme = lightColorScheme(primary = Forest, secondary = Orange, background = Sand,
        surface = Sand, onPrimary = Color.White, onSurface = Forest, onBackground = Forest)) {
        Surface(Modifier.fillMaxSize(), color = Sand) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(22.dp, 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("DESERTLINK", fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, letterSpacing = (-1).sp)
                        Text("CONNECTION BEYOND COVERAGE", fontSize = 8.sp, letterSpacing = 1.5.sp, color = Muted) }
                    TextButton(onClick = { trust = state.trustedRescue; settings = true }) { Text("Identity") }
                }
                HorizontalDivider(color = Color(0xFFD5DBCF))
                TabRow(selectedTabIndex = tab, containerColor = Sand) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Live Bluetooth") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Offline demo") })
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (tab == 0) {
                        Text("Help can travel.\nEven when you can’t.", fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                        Text("SOS messages stay on this phone, then travel with nearby Bluetooth devices running DESERTLINK.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Stat("BLE PEERS", "${state.peers}", Modifier.weight(1f))
                            Stat("STORED", "${state.records.size}", Modifier.weight(1f))
                            Stat("TX ATTEMPTS", "${state.transmissions}", Modifier.weight(1f))
                        }
                        Text("${if (state.peers == 0) "● No peer in range" else "● Bluetooth contacts available"}  ·  ${state.role.replaceFirstChar { it.uppercase() }}", color = if (state.peers == 0) Orange else Forest, fontSize = 12.sp)
                        Text(state.notice, fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                        Button(onClick = { editing = null; text = "Vehicle immobilized. Need assistance."; compose = true },
                            enabled = state.ready, modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange), shape = RoundedCornerShape(8.dp)) {
                            Text("Send an SOS", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("INCIDENTS", fontSize = 10.sp, letterSpacing = 2.sp, color = Muted)
                        if (latest.isEmpty()) CardBlock {
                            Text("No incidents stored.", fontWeight = FontWeight.Medium)
                            Text("Create an SOS, or try the offline demo with simulated nodes.", fontSize = 12.sp, color = Muted, lineHeight = 19.sp)
                        }
                        latest.forEach { record ->
                            val owned = record.fingerprint == state.fingerprint
                            val receipts = state.records.filter { it.target == record.id }
                            val confirmed = receipts.any { it.kind == "ack" && state.trustedRescue.isNotBlank() && it.fingerprint == state.trustedRescue }
                            val received = receipts.any { it.kind == "receipt" && state.trustedRescue.isNotBlank() && it.fingerprint == state.trustedRescue }
                            CardBlock {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("SOS · v${record.revision}", color = Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(if (owned) "YOUR INCIDENT" else "CARRIED INCIDENT", fontSize = 9.sp, color = Muted)
                                }
                                Text(record.text, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                                Text(when {
                                    confirmed -> "✓ Trusted operator confirmed v${record.revision}"
                                    received -> "Delivered to trusted rescue node · awaiting operator"
                                    receipts.any { it.kind == "ack" } -> "Operator ACK received · identity not trusted yet"
                                    receipts.isNotEmpty() -> "Receipt received · rescue identity not trusted yet"
                                    owned -> "Stored locally · destination unconfirmed"
                                    else -> "Stored here for onward forwarding"
                                }, color = if (confirmed) Forest else Orange, fontSize = 12.sp, lineHeight = 18.sp)
                                Text("Created ${clock(record.created)} · expires ${clock(record.expires)}", color = Muted, fontSize = 10.sp)
                                if (owned) OutlinedButton(onClick = { editing = record; text = record.text; compose = true }) { Text("Update while disconnected") }
                                if (!owned && state.role == "rescue") Button(onClick = { viewModel.rescue.acknowledge(record) }) { Text("Confirm receipt of v${record.revision}") }
                            }
                        }
                        if (mine.isNotEmpty()) Text("Updates stay local until a future contact. An ACK for v1 never confirms v2.", fontSize = 11.sp, color = Muted, lineHeight = 18.sp)
                        Text("Public SOS payloads are signed, not encrypted. Use synthetic details for this prototype. No live GPS is collected by DESERTLINK’s incident screen.", color = Muted, fontSize = 10.sp, lineHeight = 17.sp)
                    } else {
                        OfflineDemoContent()
                    }
                    HorizontalDivider(color = Color(0xFFD5DBCF))
                    Text("BLE transport powered by Bitchat · GPL-3.0\nDESERTLINK rescue layer · Hackathon prototype\nNo connection or future contact means no delivery guarantee.", color = Muted, fontSize = 10.sp, lineHeight = 17.sp)
                }
            }
        }
        if (compose) AlertDialog(onDismissRequest = { compose = false }, title = { Text(if (editing == null) "Create an SOS" else "Create revision ${editing!!.revision + 1}") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Saved locally first. No peer nearby means it waits here.", fontSize = 12.sp)
                OutlinedTextField(value = text, onValueChange = { if (it.length <= 160) text = it }, label = { Text("Situation · ${text.length}/160") }, minLines = 3)
                Text("Use demo details. Do not rely on this prototype for real emergencies.", color = Orange, fontSize = 11.sp)
            }
        }, confirmButton = { TextButton(onClick = { viewModel.rescue.send(text, editing?.incident); compose = false }, enabled = text.isNotBlank()) { Text("Save SOS locally") } }, dismissButton = { TextButton(onClick = { compose = false }) { Text("Cancel") } })
        if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("Device & rescue identity") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("YOUR SIGNING FINGERPRINT", fontSize = 10.sp, color = Muted)
                Text(state.fingerprint, fontSize = 10.sp, lineHeight = 16.sp)
                TextButton(onClick = { clipboard.setText(AnnotatedString(state.fingerprint)) }) { Text("Copy fingerprint") }
                Text("This phone acts as", fontSize = 12.sp)
                Row { listOf("traveller", "relay", "rescue").forEach { role ->
                    FilterChip(selected = state.role == role, onClick = { viewModel.rescue.setRole(role) }, label = { Text(role.replaceFirstChar { it.uppercase() }, fontSize = 10.sp) }, modifier = Modifier.padding(end = 4.dp))
                } }
                OutlinedTextField(value = trust, onValueChange = { trust = it }, label = { Text("Trusted rescue fingerprint") }, supportingText = { Text("Verify the full 64-character code directly with the rescue operator. A self-selected role is not proof of identity.", fontSize = 10.sp) }, minLines = 2)
                Text("BLE transport uses the upstream foreground service. For the first demo, keep the app visible. Physical range and background behavior must be tested on actual devices.", fontSize = 11.sp, color = Muted)
            }
        }, confirmButton = { TextButton(onClick = { viewModel.rescue.trust(trust); settings = false }, enabled = trust.isBlank() || trust.trim().replace(" ", "").matches(Regex("[0-9a-fA-F]{64}"))) { Text("Save identity settings") } }, dismissButton = { TextButton(onClick = { settings = false }) { Text("Close") } })
    }
}

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
                                enabled = !demoBusy && d.step < 8, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Orange), shape = RoundedCornerShape(8.dp)) {
                                Text(if (d.step == 0) "Start the demo" else if (d.step == 8) "Journey complete" else "Next contact / event →")
                            }
                            TextButton(onClick = { scope.launch { demoBusy = true; try { val fresh = withContext(Dispatchers.Default) { RescueDemo() }; demo = fresh; demoState = fresh.snapshot() } finally { demoBusy = false } } }, enabled = !demoBusy) { Text("Reset simulated nodes") }
                        }
}
