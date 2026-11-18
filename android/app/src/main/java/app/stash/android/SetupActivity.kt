package app.stash.android

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Asks for the server address on first launch (and when you choose "Change server"). */
class SetupActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var connectBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        input = EditText(this).apply {
            hint = "http://192.168.1.50:8080"
            setText(prefs.serverUrl)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, _, _ -> connect(); true }
        }
        status = Ui.body(this)
        connectBtn = Ui.button(this, "Connect") { connect() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(24))
            addView(Ui.title(this@SetupActivity, "Connect to your Stash"))
            addView(Ui.body(this@SetupActivity, "Type the address of your Stash server. It's printed when the server starts, for example http://192.168.1.50:8080. Your phone has to be on the same network (or have a route to it)."))
            addView(Ui.gap(this@SetupActivity, 12))
            addView(input)
            addView(status)
            addView(Ui.gap(this@SetupActivity, 8))
            addView(connectBtn)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun connect() {
        val base = normalizeServerUrl(input.text.toString())
        if (base == null) {
            status.text = "That doesn't look like an address. Example: http://192.168.1.50:8080"
            return
        }
        connectBtn.isEnabled = false
        status.text = "Checking $base …"
        Thread {
            val error: String? = try {
                StashApi.session(base)
                null
            } catch (e: Exception) {
                friendly(e)
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                connectBtn.isEnabled = true
                if (error != null) status.text = error else save(base)
            }
        }.start()
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> "Can't find that address. Check the spelling."
        is ConnectException -> "The server refused the connection. Is it running, and is the port right?"
        is SocketTimeoutException -> "Timed out. Check the address and that the phone is on the same network."
        else -> e.message ?: "Could not connect"
    }

    private fun save(base: String) {
        val old = prefs.serverUrl
        if (old == base) { // nothing changed (opened from settings)
            if (old.isNotEmpty()) { finish(); return }
        } else if (old.isNotEmpty()) {
            // A different server knows nothing about what was backed up to the old one: start clean, ask to reconnect.
            prefs.backupEnabled = false
            prefs.apiKey = ""
            BackupDb(this).clear()
            BackupScheduler.apply(this)
        }
        prefs.serverUrl = base
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
