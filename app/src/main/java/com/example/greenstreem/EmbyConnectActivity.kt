package com.example.greenstreem

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class EmbyConnectActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) render()
    }

    private fun render() {
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), dp(30), dp(56), dp(40))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        setContentView(scroll)
        if (EmbyConnectEntitlement.isUnlocked(this)) renderUnlocked() else renderLocked()
    }

    private fun renderLocked() {
        addTitle("Unlock Emby Connect — $9.99 Lifetime")
        addBody(
            "Connect your own Emby server and enjoy its Live TV, antenna channels, guide data, movies, and series—all inside GreenStreem, at home or away.\n\n" +
                "Your Emby server must already be set up with your own TV tuner, antenna channels, media libraries, and remote access. GreenStreem does not provide channels or media—it brings the content available on your Emby server into one convenient app.\n\n" +
                "One-time purchase. No GreenStreem subscription required."
        )
        addLabel("Your activation ID")
        addCode(EmbyConnectEntitlement.activationId(this))
        addBody("Keep this ID handy. Discord will ask for it when you order the add-on.")
        addButton("Purchase on Discord") {
            lifecycleScope.launch {
                EmbyConnectActivationClient.submitPurchaseRequest(this@EmbyConnectActivity)
                openDiscordOrders()
            }
        }
        addButton("I Paid — Check Activation") { checkActivation() }
    }

    private fun renderUnlocked() {
        addTitle("Emby Connect")
        val saved = EmbySecureStore.load(this)
        if (saved != null) {
            addBody("Connected to ${saved.serverUrl}\nSigned in as ${saved.userName}")
            addButton("Open Emby Library") {
                startActivity(Intent(this, EmbyLibraryActivity::class.java))
            }
            addButton("Disconnect Emby Server") {
                EmbySecureStore.clear(this)
                render()
            }
            return
        }

        addBody("Sign in to your own Emby server. Use the same server address, username, and password you use in the Emby app.")
        val server = addInput("Emby server address", "https://your-server:8920", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val username = addInput("Emby username", "Username", InputType.TYPE_CLASS_TEXT)
        val password = addInput("Emby password", "Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        addButton("Connect Emby Server") {
            val serverValue = server.text.toString()
            val userValue = username.text.toString()
            val passwordValue = password.text.toString()
            lifecycleScope.launch {
                setBusy(true)
                EmbyApiClient.authenticate(serverValue, userValue, passwordValue)
                    .onSuccess { result ->
                        EmbySecureStore.save(
                            this@EmbyConnectActivity,
                            EmbySecureStore.Credentials(result.serverUrl, result.userId, result.userName, result.accessToken)
                        )
                        password.text?.clear()
                        Toast.makeText(this@EmbyConnectActivity, "Emby connected", Toast.LENGTH_SHORT).show()
                        render()
                    }
                    .onFailure { error ->
                        Toast.makeText(this@EmbyConnectActivity, error.message ?: "Could not connect to Emby", Toast.LENGTH_LONG).show()
                        setBusy(false)
                    }
            }
        }
    }

    private fun checkActivation() {
        lifecycleScope.launch {
            setBusy(true)
            EmbyConnectActivationClient.refreshStatus(this@EmbyConnectActivity)
                .onSuccess { unlocked ->
                    if (unlocked) {
                        Toast.makeText(this@EmbyConnectActivity, "Emby Connect unlocked", Toast.LENGTH_LONG).show()
                        render()
                    } else {
                        Toast.makeText(this@EmbyConnectActivity, "Payment has not been approved yet", Toast.LENGTH_LONG).show()
                        setBusy(false)
                    }
                }
                .onFailure {
                    Toast.makeText(this@EmbyConnectActivity, "Activation service is not ready yet. Your ID is saved.", Toast.LENGTH_LONG).show()
                    setBusy(false)
                }
        }
    }

    private fun openDiscordOrders() {
        val url = "https://discord.com/channels/1495933593497767996/1513549164406116442"
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "Open GreenStreem Discord and go to #order-here", Toast.LENGTH_LONG).show() }
    }

    private fun addTitle(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 30f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(20))
        })
    }

    private fun addLabel(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(0xFFB8C4D8.toInt())
            setPadding(0, dp(14), 0, dp(6))
        })
    }

    private fun addBody(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(0xFFE7ECF4.toInt())
            setLineSpacing(0f, 1.15f)
            setPadding(0, 0, 0, dp(16))
        })
    }

    private fun addCode(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            textSize = 24f
            setTextColor(0xFF4ADE80.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = AppearanceTheme.buttonBackground(this@EmbyConnectActivity)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun addInput(label: String, hint: String, type: Int): EditText {
        addLabel(label)
        return EditText(this).also { input ->
            input.hint = hint
            input.inputType = type
            input.setTextColor(Color.WHITE)
            input.setHintTextColor(0xFF8190A8.toInt())
            input.setSingleLine(true)
            input.setPadding(dp(16), dp(12), dp(16), dp(12))
            input.background = AppearanceTheme.buttonBackground(this)
            content.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply {
                bottomMargin = dp(8)
            })
        }
    }

    private fun addButton(text: String, action: () -> Unit) {
        content.addView(Button(this).apply {
            this.text = text
            isFocusable = true
            isFocusableInTouchMode = true
            setTextColor(Color.WHITE)
            textSize = 18f
            background = AppearanceTheme.buttonBackground(this@EmbyConnectActivity)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
            topMargin = dp(10)
        })
    }

    private fun setBusy(busy: Boolean) {
        fun visit(view: View) {
            view.isEnabled = !busy
            if (view is LinearLayout) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(content)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
