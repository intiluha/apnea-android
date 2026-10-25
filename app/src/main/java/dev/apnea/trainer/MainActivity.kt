package dev.apnea.trainer

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var cues: Cues
    private lateinit var session: Session

    private var saved by mutableStateOf(Saved())
    private var frame by mutableStateOf<Frame?>(null)
    private var mode by mutableStateOf(Mode.FREE)

    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportHistory(uri)
    }
    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importHistory(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        store = Store(this)
        cues = Cues(this)
        saved = store.load()
        applyCueSettings()
        session = Session(cue = cues::play, onOutcome = ::record)
        frame = session.tick(now())

        setContent {
            ApneaApp(
                saved = saved,
                mode = mode,
                frame = frame ?: session.tick(now()),
                onFrame = { frame = session.tick(now()) },
                onMode = { session.select(it); mode = it; refresh() },
                onPrimary = {
                    if (session.running) session.primary(now())
                    else session.start(now(), saved.breatheUp, saved.tableBasis)
                    refresh()
                },
                onSkip = { session.skip(now()); refresh() },
                onSettings = { update(it) },
                onClearLog = { update(saved.copy(history = emptyList())) },
                onExportLog = {
                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportPicker.launch("apnea-history-$day.json")
                },
                // JSON files come back as application/json, text/plain or octet-stream depending on the
                // file manager, so accept anything and validate the contents instead.
                onImportLog = { importPicker.launch(arrayOf("*/*")) },
                onKeepScreenOn = { window.decorView.keepScreenOn = it },
            )
        }
    }

    override fun onDestroy() {
        cues.close()
        super.onDestroy()
    }

    private fun now() = SystemClock.elapsedRealtimeNanos() / 1e9

    private fun refresh() {
        frame = session.tick(now())
    }

    private fun update(s: Saved) {
        saved = s
        store.save(s)
        applyCueSettings()
    }

    private fun exportHistory(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(historyToJson(saved.history).toByteArray()) }
            toast("Exported ${saved.history.size} entries")
        } catch (e: Exception) {
            toast("Couldn't write the file")
        }
    }

    private fun importHistory(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            toast("Couldn't read the file"); return
        }
        val imported = parseHistory(text) ?: run { toast("Not an Apnea history file"); return }
        val merged = mergeHistory(saved.history, imported)
        val added = merged.size - saved.history.size
        update(saved.copy(history = merged))
        toast(if (added > 0) "Imported $added new entries" else "Nothing new to import")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun applyCueSettings() {
        cues.sound = saved.sound
        cues.vibrate = saved.vibrate
    }

    private fun record(o: Outcome) {
        val s = saved
        val at = System.currentTimeMillis()
        when (o) {
            is Outcome.FreeHold -> {
                val isPb = o.seconds > s.pb
                update(
                    s.copy(
                        last = o.seconds,
                        pb = if (isPb) o.seconds else s.pb,
                        maxHold = if (isPb) o.seconds else s.maxHold,
                        count = s.count + 1,
                        history = (listOf(LogEntry(if (isPb) "Max hold · new best" else "Max hold", fmt(o.seconds), at)) + s.history).take(HISTORY_MAX),
                    )
                )
                if (isPb) session.markNewBest()
            }
            is Outcome.TableDone -> {
                val label = when (o.mode) {
                    Mode.CO2 -> "CO₂ table"
                    Mode.O2 -> "O₂ table"
                    Mode.BOX -> "Box breathing"
                    Mode.FREE -> "Session"
                }
                update(
                    s.copy(
                        count = s.count + 1,
                        history = (listOf(LogEntry("$label · complete", "done", at)) + s.history).take(HISTORY_MAX),
                    )
                )
            }
        }
    }
}
