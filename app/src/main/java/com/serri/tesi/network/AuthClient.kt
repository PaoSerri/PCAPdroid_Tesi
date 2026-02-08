package serri.tesi.network

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import serri.tesi.dto.LoginRequestDto
import serri.tesi.dto.LoginResponseDto

/**
 * Client HTTP per operazioni di autenticazione
 *
 * Gestisce esclusivamente il login dell'utente e
 * il recupero del token JWT rilasciato dal backend.
 *
 * È mantenuto separato da BackendClient per isolare
 * le chiamate non autenticate da quelle protette.
 */
class AuthClient(private val baseUrl: String) {

    private val client = OkHttpClient() //client x esecuzione rischieste autenticazione
    private val gson = Gson() //istanza x serializzazione/deserializzazione json

    //esegue login utente tramite credenziali
    //invio richiesta post al backend e restituisce token jwt associato a sessione utente
    fun login(email: String, password: String): String? {
        return try {
            //serializzazione credenziali in json
            val json = gson.toJson(LoginRequestDto(email, password))
            val body = json.toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

            //costruzione richiesta http verso endpoint login
            val request = Request.Builder()
                .url("$baseUrl/auth/login")
                .post(body)
                .build()

            //esecuzione richiesta (eventuali errori di rete intercettatty da trycatch)
            client.newCall(request).execute().use { response ->
                Log.d("TESI_LOGIN", "HTTP ${response.code}")
                if (!response.isSuccessful) return null //in caso di errore login fallisce senza crash

                //deserializzazione risposta json
                val resp = response.body?.string() ?: return null
                gson.fromJson(resp, LoginResponseDto::class.java).access_token
            }
        } catch (e: Exception) {
            null
        }
    }
}
