package serri.tesi.service

import android.content.Context //context android x accedere a db locale
import android.util.Log //utility android per logcat
import serri.tesi.dto.BatchDto //dto batch di richieste da inviare a backend
import serri.tesi.mapper.NetworkRequestMapper //mapper converte modello richieste in dto, per la trasmissione
import serri.tesi.network.BackendClient //client responsabile di comunicazione http con backend remoto
import serri.tesi.repo.TrackerRepository //repo per accesso ai dati locali
import serri.tesi.auth.SessionManager //per accedere a token jwt
import serri.tesi.config.BackendConfig //url backend
// x retry sync service
import android.net.ConnectivityManager
import android.net.Network



/**
 * Servizio responsabile di sincronizzazione dati con backend remoto.
 *
 * legge i record di rete non ancora sincronizzati dal database
 * locale, li converte in DTO(Data Transfer Object) e li invia in modalità batch al server.
 *
 * In caso di esito positivo, i record vengono marcati come sincronizzati,
 * garantendo consistenza ed evitando duplicati
 */

// per mostrare risultato operazione invio dati backend
enum class SyncResult {
    NO_DATA,
    SUCCESS,
    ERROR
}

class SyncService(private val context: Context) {

    /**
     * Singola operazione di sincronizzazione.
     *
     * metodo recupera un batch di record non sincronizzati, li invia al backend
     * remoto e, in caso di successo, aggiorna lo stato locale della cache.
     */

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var isSyncRunning = false

    /**
     * Esegue una singola operazione completa di sincronizzazione.
     *
     * - no sync parallele
     * - verifica autenticazione utente
     * - recupera batch di record non sincronizzati
     * - invia dati al backend in modalità iterativa
     * - marca  sincronizzati i record inviati con successo
     * - ripete finché il backlog locale non è vuoto
     *
     * Implementa un modello di sincronizzazione a batch con
     * consistenza eventuale e gestione robusta degli errori.
     */
    fun syncOnce(): SyncResult {

        // Evita concorrenza
        if (isSyncRunning) return SyncResult.NO_DATA
        isSyncRunning = true //sync in esecuzione

        try {

            val repo = TrackerRepository(context) //repo x accesso a db locale
            val sessionManager = SessionManager(context) //gestione sessione utente jwt

            if (!sessionManager.isLoggedIn()) { //verifica autenticazione
                saveSyncState("ERROR")
                return SyncResult.ERROR
            }

            //client http autenticato verso backend
            val client = BackendClient(
                baseUrl = BackendConfig.getBaseUrl(),
                sessionManager = sessionManager
            )

            saveSyncState("SYNCING") //stato ui sincronizzazione in corso

            var totalSynced = 0 //contatore record syncati

            while (true) { //ciclo finche esistono record non synced

                // Batch size, recupera 100 record (configurabile)
                val pending = repo.getPendingNetworkRequests(100)

                if (pending.isEmpty()) break // termina se non ci sono + record

                //conversione modello locale in dto
                val dtos = pending.map { NetworkRequestMapper.toDto(it) }
                val batch = BatchDto(dtos)

                // Invio del batch corrente al backend remoto
                val success = client.sendBatch(batch)

// Se l'invio fallisce (errore rete, server o autenticazione)
                if (!success) {

                    // Caso specifico: il token JWT è scaduto.
                    // In questo scenario il BackendClient ha già eseguito il logout
                    // (cancellazione del token tramite SessionManager).
                    // Verifichiamo quindi se l'utente risulta non autenticato.
                    if (!sessionManager.isLoggedIn()) {

                        // Le operazioni di UI devono essere eseguite sul Main Thread.
                        android.os.Handler(android.os.Looper.getMainLooper()).post {

                            // Mostra un messaggio informativo all'utente
                            // per spiegare che la sessione è scaduta.
                            android.widget.Toast.makeText(
                                context,
                                "Sessione scaduta. Effettua nuovamente l’accesso.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()

                            // Creazione Intent verso la schermata di login.
                            // FLAG_ACTIVITY_NEW_TASK: necessario perché partiamo da Context non-Activity.
                            // FLAG_ACTIVITY_CLEAR_TASK: pulisce lo stack per evitare ritorno a schermate precedenti.
                            val intent = android.content.Intent(
                                context,
                                serri.tesi.ui.TesiLoginActivity::class.java
                            )

                            intent.flags =
                                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK

                            // Avvio automatico della LoginActivity
                            context.startActivity(intent)
                        }

                        // Aggiorna lo stato di sincronizzazione come errore
                        saveSyncState("ERROR")

                        // Interrompe il ciclo di sincronizzazione
                        return SyncResult.ERROR
                    }

                    // Caso generico di errore (non legato alla scadenza del token)
                    saveSyncState("ERROR")
                    return SyncResult.ERROR
                }

                repo.markAsSynced(pending.mapNotNull { it.id }) //marca record come già syncronizzati (synced=1)
                totalSynced += pending.size
            }

            //stato finale
            if (totalSynced > 0) {
                Log.d("TESI_SYNC", "Total synced: $totalSynced")
                saveSyncState("SUCCESS") //successo
                return SyncResult.SUCCESS
            }

            saveSyncState("IDLE") //nessun dato da sincronizzare, IDLE = nulla da fare
            return SyncResult.NO_DATA

        } finally {
            isSyncRunning = false //ripristino stato
        }
    }

    //metodo x salvare stato della sincronizzazione e mostrarlo in info panel (mainactivity)
    private fun saveSyncState(state: String) {
        val prefs = context.getSharedPreferences("tesi_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_sync_state", state)
            .putLong("last_sync_ts", System.currentTimeMillis())
            .apply()
    }

    /**
     * Avvia il meccanismo di retry automatico della sincronizzazione.
     *
     * Il servizio registra  listener di rete a livello di sistema,
     * in modo da intercettare il momento in cui una connessione Internet diventa disponibile.
     *
     * Quando la rete torna disponibile:
     * - verifica che non sia già in corso una sincronizzazione
     * - controlla che esistano record pending
     * - avvia un tentativo asincrono di sync
     *
     * Questo approccio consente di implementare un modello offline-first
     * con consistenza eventuale, evitando dipendenze dal livello UI.
     */
    fun startAutoRetry() {

        // Recupera il ConnectivityManager di sistema
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Evita doppia registrazione del callback
        // (importante per prevenire retry duplicati)
        if (networkCallback != null) return

        // Definizione del callback di rete
        networkCallback = object : ConnectivityManager.NetworkCallback() {

            /**
             * Metodo invocato automaticamente dal sistema
             * quando una rete con capability INTERNET diventa disponibile.
             */
            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                // Se una sincronizzazione è già in corso,
                // evita di avviarne una nuova (no concorrenza)
                if (isSyncRunning) return

                // Verifica presenza di record non sincronizzati
                val repo = TrackerRepository(context)
                val pending = repo.countPendingNetworkRequests()

                // Se non ci sono dati pending, non serve sincronizzare
                if (pending == 0) return

                isSyncRunning = true

                // Avvio asincrono della sincronizzazione
                Thread {
                    try {
                        val result = syncOnce()
                        Log.d("TESI_SYNC", "Auto-retry result: $result")
                    } finally {
                        // Ripristina lo stato per consentire
                        // eventuali futuri tentativi
                        isSyncRunning = false
                    }
                }.start()
            }
        }

        // Costruzione della richiesta di rete:
        // ascolta qualunque rete con accesso Internet
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        // Registrazione effettiva del callback
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }


    /**
     * Interrompe il meccanismo di retry automatico.
     *
     * Rimuove il listener di rete precedentemente registrato
     * per evitare memory leak o duplicazioni di callback.
     *
     * Utile nel caso in cui si voglia disattivare esplicitamente
     * il servizio di sincronizzazione.
     */
    fun stopAutoRetry() {

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
        }
    }

}

// Il SyncService non gestisce l'autenticazione.
// Utilizza il token JWT esclusivamente per identificare l'utente durante l'invio dei dati
// l'associazione avviene lato backend.

