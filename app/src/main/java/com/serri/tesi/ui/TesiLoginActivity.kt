package serri.tesi.ui

import android.content.Intent //x avviare altre activity
import android.os.Bundle //x stato activity
// componenti ui x input e feedback utente
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity //classe base x activity compatibily con AppCompat
import kotlin.concurrent.thread //utility x eseguire operazioni su thread separati/background
import serri.tesi.auth.SessionManager //gestore della sessione utente (salvataggio token jwt)
import serri.tesi.network.AuthClient //client http per autenticazione verso backend
import com.emanuelef.remote_capture.R //risorse grafiche/layout app
import serri.tesi.config.BackendConfig
import android.widget.TextView

/**
 * Activity responsabile di autenticazione utente.
 *
 * Permette all’utente di inserire le credenziali:
 * in caso di successo, salva il token JWT e reindirizza alla schermata principale.
 */
class TesiLoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager //variabile x gestione persistenza token

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tesi_login)

        sessionManager = SessionManager(this) //inizializza gestore sessione con context

        //campi input
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton) //bottone login

        //link registrazione
        val goToRegister = findViewById<TextView>(R.id.goToRegister)

        goToRegister.setOnClickListener {
            startActivity(Intent(this, TesiRegisterActivity::class.java))
        }

        //login
        loginButton.setOnClickListener {

            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()

            // Pulizia errori precedenti
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

            loginButton.isEnabled = false

            thread {
                val authClient = AuthClient(
                    BackendConfig.getBaseUrl()
                )

                val token = authClient.login(email, password)

                runOnUiThread {

                    if (token != null) {
                        sessionManager.saveToken(token)
                        Toast.makeText(this, "Login effettuato", Toast.LENGTH_SHORT).show()

                        startActivity(
                            Intent(this, MainActivity::class.java)
                        )
                        finish()
                    } else {
                        loginButton.isEnabled = true
                        passwordInput.error = "Credenziali non valide"
                    }
                }
            }
        }
    }
}
