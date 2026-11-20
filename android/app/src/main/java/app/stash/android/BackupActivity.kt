package app.stash.android

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** Native settings screen: server address and the photo/video auto backup. */
class BackupActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var db: BackupDb
    private lateinit var serverText: TextView
    private lateinit var statusText: TextView
    private lateinit var enableSwitch: Switch
    private lateinit var foldersButton: android.widget.Button
    private var updating = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> runOnUiThread { refresh() } }

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Perms.hasMedia(this)) {
            enable()
        } else {
            toast("Photo access is needed to back up your photos and videos")
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        db = BackupDb(this)

        serverText = Ui.body(this)
        statusText = Ui.body(this)
        enableSwitch = Ui.toggle(this, "Back up this phone to Stash", prefs.backupEnabled) { on ->
            if (!updating) if (on) turnOn() else turnOff()
        }
        foldersButton = Ui.button(this, "") { chooseFolders() }

        // Same layout idea as the website: grey page, one rounded card per group.
        val serverCard = Ui.card(
            this,
            serverText,
            Ui.button(this, "Change server") { startActivity(Intent(this, SetupActivity::class.java)) },
            Ui.gap(this, 4),
        )
        val scheduleCard = Ui.card(
            this,
            enableSwitch,
            Ui.toggle(this, "Wi-Fi only", prefs.wifiOnly) { prefs.wifiOnly = it; BackupScheduler.apply(this) },
            Ui.toggle(this, "Only while charging", prefs.chargingOnly) { prefs.chargingOnly = it; BackupScheduler.apply(this) },
        )
        val contentCard = Ui.card(
            this,
            Ui.toggle(this, "Back up photos", prefs.includePhotos) { prefs.includePhotos = it },
            Ui.toggle(this, "Back up videos", prefs.includeVideos) { prefs.includeVideos = it },
            Ui.toggle(this, "Include files already on this phone", prefs.includeExisting) { prefs.includeExisting = it },
            foldersButton,
            Ui.gap(this, 4),
        )
        val statusCard = Ui.card(
            this,
            statusText,
            Ui.button(this, "Back up now", Ui.Kind.PRIMARY) { backupNow() },
            Ui.gap(this, 4),
        )
        val note = Ui.muted(
            this,
            "Backup is one-way: nothing is ever deleted from your phone. Files the server already has (same content) are skipped. " +
                "It runs about once an hour in the background. If it says the key was rejected, switch backup off and on again while logged in.",
        )

        setContentView(
            Ui.page(this, Ui.header(this, "Phone backup", "Photos and videos to your Stash"), serverCard, scheduleCard, contentCard, statusCard, note)
        )
    }

    override fun onStart() {
        super.onStart()
        prefs.listen(prefListener)
        refresh()
    }

    override fun onStop() {
        prefs.unlisten(prefListener)
        super.onStop()
    }

    private fun refresh() {
        updating = true
        enableSwitch.isChecked = prefs.backupEnabled
        updating = false
        serverText.text = "Server: ${prefs.serverUrl}"
        foldersButton.text = "Folders: " + if (prefs.buckets.isEmpty()) "all" else prefs.buckets.sorted().joinToString(", ")
        statusText.text = buildString {
            append("Status: ").append(prefs.status.ifEmpty { if (prefs.backupEnabled) "Waiting for the next run" else "Off" })
            if (prefs.lastRun > 0) append("\nLast finished: ").append(DateFormat.getDateTimeInstance().format(Date(prefs.lastRun)))
            append("\nFiles backed up so far: ").append(db.count())
        }
    }

    private fun turnOff() {
        prefs.backupEnabled = false
        BackupScheduler.apply(this)
        prefs.status = "Off"
        refresh()
    }

    private fun turnOn() {
        if (Perms.hasMedia(this)) enable() else permissions.launch(Perms.requestList())
    }

    /** Connects to the server (fetching the API key through the website login) and starts the schedule. */
    private fun enable() {
        prefs.status = "Connecting…"
        Thread {
            try {
                val base = prefs.serverUrl
                val session = StashApi.session(base)
                if (session.api < 2) throw IOException("This server is too old: replace server.py with the updated version")
                if (session.authRequired) {
                    val cookie = CookieManager.getInstance().getCookie("$base/")
                    if (!cookie.isNullOrEmpty() && cookie.contains("stash_session=")) {
                        prefs.apiKey = StashApi.fetchAppKey(base, cookie)
                    } else if (prefs.apiKey.isEmpty()) {
                        throw IOException("Log in to Stash in the app first, then come back here")
                    }
                }
                runOnUiThread {
                    prefs.enabledSince = System.currentTimeMillis()
                    prefs.backupEnabled = true
                    prefs.status = "Starting…"
                    BackupScheduler.apply(this)
                    BackupScheduler.runNow(this)
                    refresh()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    prefs.backupEnabled = false
                    prefs.status = "Off"
                    toast(e.message ?: "Could not connect")
                    refresh()
                }
            }
        }.start()
    }

    private fun backupNow() {
        if (!prefs.backupEnabled) {
            toast("Switch backup on first")
            return
        }
        BackupScheduler.runNow(this)
        toast("Backup queued")
    }

    private fun chooseFolders() {
        if (!Perms.hasMedia(this)) {
            toast("Switch backup on first so the app can see your photo folders")
            return
        }
        Thread {
            val names = MediaSource.buckets(this).toList()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (names.isEmpty()) {
                    toast("No photo or video folders found")
                    return@runOnUiThread
                }
                val checked = BooleanArray(names.size) { names[it] in prefs.buckets }
                val dialog = AlertDialog.Builder(this)
                    .setTitle("Folders to back up")
                    .setMultiChoiceItems(names.toTypedArray(), checked) { _, index, isChecked -> checked[index] = isChecked }
                    .setPositiveButton("Save") { _, _ ->
                        prefs.buckets = names.filterIndexed { i, _ -> checked[i] }.toSet()
                        refresh()
                    }
                    .setNeutralButton("All folders") { _, _ ->
                        prefs.buckets = emptySet()
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                Ui.styleDialog(dialog)
            }
        }.start()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
