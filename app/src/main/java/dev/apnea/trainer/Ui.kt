package dev.apnea.trainer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.text.DateFormat
import kotlin.math.abs
import java.util.Date

// Dark palette from the web app.
private val Bg = Color(0xFF081214)
private val Bg2 = Color(0xFF0B1A1C)
private val Surface = Color(0xFF10211F)
private val Surface2 = Color(0xFF0D1C1B)
private val Ink = Color(0xFFE9F1EE)
private val Muted = Color(0xFF8BA49F)
private val Line = Color(0xFF1E332F)
private val Accent = Color(0xFFE8A05A)
private val AccentSoft = Color(0xFFF0B878)
private val Cool = Color(0xFF5FC0B0)
private val CoolSoft = Color(0xFF7FD0C2)
private val OrbCore = Color(0xFFF0B878)
private val OrbEdge = Color(0xFFD9803F)
private val RingTrack = Color(0xFF1C302C)
private val Stop1 = Color(0xFFD7684F)
private val Stop2 = Color(0xFFB64A34)

private val Serif = FontFamily.Serif
private val Mono = FontFamily.Monospace
private val Caps = TextStyle(fontSize = 11.sp, letterSpacing = 1.8.sp, color = Muted)

@Composable
fun ApneaApp(
    saved: Saved,
    mode: Mode,
    frame: Frame,
    onFrame: () -> Unit,
    onMode: (Mode) -> Unit,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    onSettings: (Saved) -> Unit,
    onClearLog: () -> Unit,
    onExportLog: () -> Unit,
    onImportLog: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    val running = frame.running
    val currentMode by rememberUpdatedState(mode)
    val currentOnMode by rememberUpdatedState(onMode)
    LaunchedEffect(running) {
        onKeepScreenOn(running)
        while (running) withFrameNanos { onFrame() }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Surface, primary = Accent)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Bg2, Bg)))
                .pointerInput(running) {
                    // Horizontal swipe anywhere cycles modes, but only while idle.
                    if (running) return@pointerInput
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            if (abs(dx) > 64.dp.toPx()) currentOnMode(currentMode.cycle(if (dx < 0) 1 else -1))
                        },
                    ) { _, delta -> dx += delta }
                }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Header()
                    ModeTabs(mode, onMode)
                    Stage(frame, mode, onPrimary, onSkip)
                    Stats(saved)
                    if (mode == Mode.CO2 || mode == Mode.O2) Plan(mode, saved.tableBasis)
                    Settings(saved, running, onSettings)
                    History(saved, onClearLog, onExportLog, onImportLog)
                    Safety()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
        Spacer(Modifier.width(12.dp))
        Text("Apnea", fontFamily = Serif, fontSize = 32.sp, color = Ink)
        Spacer(Modifier.width(12.dp))
        Text("STATIC BREATH-HOLD", style = Caps)
    }
}

@Composable
private fun ModeTabs(mode: Mode, onMode: (Mode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mode.entries.forEach { m ->
            val sel = m == mode
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Color(0xFF26281F) else Surface)
                    .border(1.dp, if (sel) Accent else Line, RoundedCornerShape(14.dp))
                    .clickable { onMode(m) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(m.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(m.tag, style = Caps.copy(fontSize = 9.sp, letterSpacing = 1.2.sp), color = if (sel) Accent else Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Surface, Surface2)))
            .border(1.dp, Line, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) { content() }
}

@Composable
private fun Stage(f: Frame, mode: Mode, onPrimary: () -> Unit, onSkip: () -> Unit) {
    val ringColor by animateColorAsState(if (f.cool) Cool else Accent, tween(600), label = "ring")
    val core by animateColorAsState(if (f.cool) CoolSoft else OrbCore, tween(600), label = "core")
    val edge by animateColorAsState(if (f.cool) Cool else OrbEdge, tween(600), label = "edge")

    Card {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth(0.82f).aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 4.dp.toPx()
                    val r = size.minDimension / 2 - sw
                    val tl = Offset(center.x - r, center.y - r)
                    val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                    drawCircle(RingTrack, r, style = Stroke(sw))
                    if (f.ring > 0f) drawArc(ringColor, -90f, 360f * f.ring, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
                // Orb: 45% of the ring at scale 1, so HI (1.32) stays inside it.
                Canvas(Modifier.fillMaxSize(0.45f).scale(f.orbScale)) {
                    val r = size.minDimension / 2
                    drawCircle(
                        Brush.radialGradient(listOf(edge.copy(alpha = 0.28f), Color.Transparent), center, r * 1.6f),
                        r * 1.6f,
                    )
                    drawCircle(
                        Brush.radialGradient(
                            0f to core, 0.78f to edge, 1f to edge,
                            center = Offset(size.width * 0.38f, size.height * 0.32f), radius = r * 1.3f,
                        ),
                        r,
                    )
                }
                if (f.round != null) {
                    Text(
                        f.round,
                        style = Caps.copy(color = Ink),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .clip(RoundedCornerShape(50))
                            .background(Bg.copy(alpha = 0.7f))
                            .border(1.dp, Line, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(f.label, fontFamily = Serif, fontSize = 24.sp, color = Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(f.hint.uppercase(), style = Caps, textAlign = TextAlign.Center, modifier = Modifier.height(16.dp))
            Text(f.timer, fontFamily = Mono, fontSize = 64.sp, color = Ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))

            val stopping = f.running
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(if (stopping) listOf(Stop1, Stop2) else listOf(AccentSoft, Accent)))
                    .clickable(onClick = onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        !stopping -> "Begin"
                        mode == Mode.FREE -> "Stop hold"
                        else -> "End session"
                    },
                    color = if (stopping) Ink else Color(0xFF1B1209),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            Box(Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (f.ghost != null) {
                    Text(
                        f.ghost, color = Muted, fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onSkip)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stats(s: Saved) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("PERSONAL BEST", if (s.pb > 0) fmt(s.pb) else "—", Modifier.weight(1f))
        Stat("LAST HOLD", if (s.last > 0) fmt(s.last) else "—", Modifier.weight(1f))
        Stat("SESSIONS", s.count.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(k: String, v: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(k, style = Caps.copy(fontSize = 9.sp), maxLines = 1)
        Text(v, fontFamily = Mono, fontSize = 22.sp, color = Ink)
    }
}

@Composable
private fun SectionTitle(title: String, meta: String = "", action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontFamily = Serif, fontSize = 19.sp, color = Ink)
        Spacer(Modifier.weight(1f))
        if (meta.isNotEmpty()) Text(meta, style = Caps.copy(letterSpacing = 0.5.sp))
        action?.invoke()
    }
}

@Composable
private fun Plan(mode: Mode, basis: Int) {
    val rows = schedule(mode, basis)
    val total = rows.sumOf { it.first + it.second }
    Card {
        Column {
            SectionTitle("Session plan", "${rows.size} rounds · ~${(total / 60.0).let { kotlin.math.floor(it + 0.5).toInt() }} min")
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text("#", style = Caps, modifier = Modifier.weight(0.5f))
                Text(if (mode == Mode.CO2) "RECOVER ↓" else "RECOVER", style = Caps, modifier = Modifier.weight(1f))
                Text(if (mode == Mode.O2) "HOLD ↑" else "HOLD", style = Caps, modifier = Modifier.weight(1f))
            }
            rows.forEachIndexed { i, (rest, hold) ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text("${i + 1}", fontFamily = Mono, color = Muted, modifier = Modifier.weight(0.5f))
                    Text(fmt(rest), fontFamily = Mono, color = Cool, modifier = Modifier.weight(1f))
                    Text(fmt(hold), fontFamily = Mono, color = Accent, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Settings(s: Saved, running: Boolean, onChange: (Saved) -> Unit) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("Settings")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your max hold", color = Ink, fontSize = 15.sp)
                    Text("Sizes the CO₂ / O₂ tables", color = Muted, fontSize = 12.sp)
                }
            }
            val basis = s.tableBasis
            fun set(v: Int) {
                val n = maxOf(20, v)
                onChange(s.copy(pb = n, maxHold = n))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("−15", !running) { set(basis - 15) }
                Stepper("−5", !running) { set(basis - 5) }
                Text(fmt(basis), fontFamily = Mono, fontSize = 22.sp, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Stepper("+5", !running) { set(basis + 5) }
                Stepper("+15", !running) { set(basis + 15) }
            }
            Spacer(Modifier.height(4.dp))
            Toggle("Guided breathe-up", "40 s calm breathing before a max hold", s.breatheUp) { onChange(s.copy(breatheUp = it)) }
            Toggle("Sound cues", "Tones on transitions and the last 3 s", s.sound) { onChange(s.copy(sound = it)) }
            Toggle("Vibration cues", "Haptics so you can train eyes-closed", s.vibrate) { onChange(s.copy(vibrate = it)) }
        }
    }
}

@Composable
private fun Stepper(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 48.dp, height = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontFamily = Mono, fontSize = 13.sp, color = if (enabled) Ink else Muted) }
}

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp)
            Text(sub, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Accent, checkedThumbColor = Bg,
                uncheckedTrackColor = Surface2, uncheckedThumbColor = Muted, uncheckedBorderColor = Line,
            ),
        )
    }
}

@Composable
private fun History(s: Saved, onClear: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    Card {
        Column {
            SectionTitle("History", if (s.history.isNotEmpty()) "${s.history.size} logged  " else "") {
                LinkAction("import", onImport)
                if (s.history.isNotEmpty()) {
                    LinkAction("export", onExport)
                    LinkAction("clear", onClear)
                }
            }
            if (s.history.isEmpty()) {
                Text("No sessions yet. Your holds and completed tables will appear here.", color = Muted, fontSize = 13.sp)
            } else {
                val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                s.history.take(12).forEach { e ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.label, color = Ink, fontSize = 14.sp)
                            if (e.at > 0) Text(df.format(Date(e.at)), color = Muted, fontSize = 11.sp)
                        }
                        Text(e.value, fontFamily = Mono, color = Ink)
                    }
                }
            }
        }
    }
}

/** Small text button for section-header actions (import / export / clear). */
@Composable
private fun LinkAction(label: String, onClick: () -> Unit) {
    Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClick).padding(4.dp))
}

@Composable
private fun Safety() {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("⚠ Dry static only", fontFamily = Serif, fontSize = 17.sp, color = Accent)
            listOf(
                "Never hold your breath in or near water without a trained buddy — shallow-water blackout gives no warning.",
                "Never train while standing, driving, or in a bath.",
                "Breathe up calmly — hard hyperventilation hides the urge to breathe.",
                "Dizziness, tingling lips/fingers or tunnel vision mean stop now.",
                "One table per day is plenty. Not medical advice.",
            ).forEach { Text("· $it", color = Muted, fontSize = 13.sp, lineHeight = 18.sp) }
        }
    }
}
