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

    fun syncOnce(): SyncResult {
        //repo istanziato localmente per contesto aggiornato + indipendente da stati precedenti del servizio
        val repo = TrackerRepository(context) //crea istanza del repo x accedere a db locale


        //istanzia sessionmanager, x gestione sessione utente
        val sessionManager = SessionManager(context) // consente di recuperare token jwt salvato e riutilizzarlo

        saveSyncState("SYNCING")//statto sincronizzazione, caso generale: prova sincronizzazione con backend, (salva stato)

        //gestione robustezza sessioni
        //punto in cui sync service parla con backend, nessun dato deve uscire se user non è autenticato

        //se utente non loggato, sync annullato
        if (!sessionManager.isLoggedIn()) {
            Log.w("TESI_SYNC", "Utente non loggato: sync annullato")
            saveSyncState("ERROR") //salva errore sync in stato
            return SyncResult.ERROR
        }

        //creazione client http (autenticato) per comunicazione con backend remoto
        val client = BackendClient(
            baseUrl = BackendConfig.getBaseUrl(), //indirizzo backend
            sessionManager = sessionManager // sessionManager passato a client x inclusione autom. token nell'header Auth. di ogni richiesta
        )

        //recuperare da db connessioni non sinc.
        val pending = repo.getPendingNetworkRequests(30)

        //se non trova record da sincronizzare, esce
        if (pending.isEmpty()) {
            Log.d("TESI_SYNC", "No record da sincronizzare")
            saveSyncState("IDLE")//salva stato, IDLE = tutto sinc, nulla da fare
            return SyncResult.NO_DATA
        }

        //convertire ogni request record (locale) in un dto (x trasmissione)
        val dtos = pending.map { NetworkRequestMapper.toDto(it) }
        val batch = BatchDto(dtos) // incapsula lista di dto in oggetto BatchDto

        // DEBUG anonimizzazione, da togliere*
        dtos.forEach {
            Log.d("TESI_PRIVACY", "DTO anonimizzato=$it")
        }

        //Log.d("TESI_SYNC", "Calling sendBatch()")
        val success = client.sendBatch(batch) //invia batch al backend, true se successo

        if (success) { //se invio a buon fine
            repo.markAsSynced(pending.mapNotNull { it.id }) //estrae id dei record sinc, synced=1 nel db locale
            Log.d("TESI_SYNC", "Synced ${pending.size} records")

            saveSyncState("SUCCESS") //salva stato
            return SyncResult.SUCCESS
        } else {
            Log.e("TESI_SYNC", "Sync fallito")
            saveSyncState("ERROR") //salva stato
            return SyncResult.ERROR
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

