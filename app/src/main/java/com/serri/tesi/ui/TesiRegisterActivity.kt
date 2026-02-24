package serri.tesi.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread
import serri.tesi.auth.SessionManager
import serri.tesi.network.AuthClient
import serri.tesi.config.BackendConfig
import com.emanuelef.remote_capture.R

class TesiRegisterActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tesi_register)

        sessionManager = SessionManager(this)

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val registerButton = findViewById<Button>(R.id.registerButton)

        registerButton.setOnClickListener {

            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            //pulizia errori
            emailInput.error = null
            passwordInput.error = null

            if (email.isBlank()) {
                emailInput.error = "Email obbligatoria"
                return@setOnClickListener
            }

            if (password.isBlank()) {
                passwordInput.error = "Password obbligatoria"
                return@setOnClickListener
            }

            registerButton.isEnabled = false
            thread {
                val authClient = AuthClient(
                    BackendConfig.getBaseUrl()
                )

                val result = authClient.register(email, password)
                val registered = result.first
                val errorMessage = result.second

                runOnUiThread {

                    if (registered) {

                        thread {

                            val token = authClient.login(email, password)

                            runOnUiThread {
                                if (token != null) {
                                    sessionManager.saveToken(token)
                                    Toast.makeText(this, "Registrazione completata", Toast.LENGTH_SHORT).show()

                                    startActivity(Intent(this, MainActivity::class.java))
                                    finish()
                                } else {
                                    registerButton.isEnabled = true
                                    Toast.makeText(this, "Errore login automatico", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                    } else {
                        registerButton.isEnabled = true
                        when (errorMessage) {
                            "Email non valida" -> {
                                emailInput.error = "Formato email non valido"
                            }
                            "Password troppo corta" -> {
                                passwordInput.error = "Minimo 6 caratteri"
                            }
                            "Email già registrata" -> {
                                emailInput.error = "Email già utilizzata"
                            }
                            else -> {
                                Toast.makeText(
                                    this,
                                    errorMessage ?: "Registrazione fallita",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }
}