package com.example.greenstreem

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class XtreamLoginActivity : AppCompatActivity() {

    private var editingProfileId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xtream_login)

        val etName = findViewById<EditText>(R.id.etName)
        val etUrl = findViewById<EditText>(R.id.etUrl)
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val progressBar = findViewById<ProgressBar>(R.id.loginProgressBar)
        editingProfileId = intent.getStringExtra("edit_profile_id")

        if (editingProfileId != null) {
            val profile = PlaylistProfilesManager.loadProfiles(this).firstOrNull { it.id == editingProfileId }
            if (profile != null) {
                etName.setText(profile.name)
                etUrl.setText(profile.serverUrl)
                etUsername.setText(profile.username)
                etPassword.setText(profile.password)
            }
        }

        btnLogin.setOnClickListener {
            val name = etName.text.toString()
            val url = etUrl.text.toString()
            val username = etUsername.text.toString()
            val password = etPassword.text.toString()

            if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading
            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            // Safety: Initialize manager BEFORE calling the service
            XtreamManager.init(url)
            val service = XtreamManager.getService()
            
            if (service == null) {
                progressBar.visibility = View.GONE
                btnLogin.isEnabled = true
                Toast.makeText(this, "Failed to initialize server connection", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            service.login(username, password).enqueue(object : Callback<XtreamLoginResponse> {
                override fun onResponse(call: Call<XtreamLoginResponse>, response: Response<XtreamLoginResponse>) {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()
                        if (body?.userInfo != null && body.userInfo!!.status == "Active") {
                            saveCredentials(name, url, username, password)
                            
                            val intent = Intent(this@XtreamLoginActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@XtreamLoginActivity, "Account is not active or invalid response", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@XtreamLoginActivity, "Invalid credentials or server error", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<XtreamLoginResponse>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    Toast.makeText(this@XtreamLoginActivity, "Connection failed: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun saveCredentials(name: String, url: String, user: String, pass: String) {
        val existing = editingProfileId?.let { id ->
            PlaylistProfilesManager.loadProfiles(this).firstOrNull { it.id == id }
        }
        val profile = existing ?: PlaylistProfilesManager.createProfile(name, url, user, pass)
        profile.name = if (name.isBlank()) "Playlist" else name
        profile.serverUrl = url
        profile.username = user
        profile.password = pass
        PlaylistProfilesManager.upsertProfileAndActivate(this, profile)
    }
}
