package serri.tesi.ui

import android.os.Bundle // usato per passare lo stato dell’Activity

//Componenti UI di base Android
import android.widget.Button
import android.widget.Toast
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity //Classe base per Activity compatibili con AppCompat
import com.emanuelef.remote_capture.R // Risorse grafiche/layout generate automaticamente
import serri.tesi.auth.SessionManager //Classe che gestisce la sessione utente (token JWT)
import serri.tesi.service.SyncService //Servizio che si occupa della sincronizzazione con il backend
import android.content.Intent // Intent x navigazione tra Activity
import android.os.Environment // Accesso a directory standard del filesystem Android
import serri.tesi.capture.TesiCaptureController // Classe che gestisce la cattura dati

// Classi Java per gestione file e scrittura binaria
import java.io.File
import java.io.FileOutputStream

import serri.tesi.network.BackendClient //Client HTTP per comunicazione col backend (sync, export, delete)
import androidx.appcompat.app.AlertDialog //Dialog per conferme utente
import serri.tesi.service.SyncResult // Enum che rappresenta l’esito della sincronizzazione
import android.util.Log //Utility per logging su Logcat
import serri.tesi.config.BackendConfig //x configurazione
import serri.tesi.repo.TrackerRepository // x eliminazione dati locali

//Permessi Android
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

import serri.tesi.utils.DownloadNotifier //per notifica download
/**
 * Activity principale dell'applicazione
 *
 * Rappresenta il punto di ingresso dell'utente e fornisce
 * i controlli principali per:
 * - avviare e arrestare la cattura del traffico
 * - visualizzare i dati raccolti
 * - gestire esportazione e cancellazione dei dati
 *
 * Questa Activity NON implementa logica di business:
 * - delega la cattura a TesiCaptureController
 * - delega la sincronizzazione a SyncService / BackendClient
 * - utilizza il repository solo per lettura dello stato
 */

class MainActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager //Gestisce lo stato di autenticazione e il token JWT
    private lateinit var captureStatusText: TextView // Elemento UI per visualizzare lo stato della cattura

    // variabili bottoni avvio/femra cattura, globali
    private lateinit var startCaptureButton: Button
    private lateinit var stopCaptureButton: Button

    // campi per info generali
    private lateinit var infoConnectionsText: TextView
    private lateinit var infoLastSyncText: TextView
    private lateinit var infoSyncStatusText: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Inizializzazione standard dell’Activity

        setContentView(R.layout.activity_main) //Associa layout XML aActivity
        TesiCaptureController.init(this) //Inizializza il controller della cattura
        //inizializza CaptureHelper x rispettare lifcycle android, evita crash

        showFirstRunWarningIfNeeded() //Mostra avviso informativo solo al primo avvio (privacy / consenso)

        DownloadNotifier.requestNotificationPermissionIfNeeded(this) // richiede permesso notifiche
        sessionManager = SessionManager(this) // Inizializza gestore sessione usando il Context dell’Activity

        //assegnazione variabili --> elementi ui
        // button per operazioni
        val openDataButton = findViewById<Button>(R.id.openDataButton)
        val exportButton = findViewById<Button>(R.id.exportButton)
        val deleteButton = findViewById<Button>(R.id.deleteButton)
        // elementi gestione cattura dati
        startCaptureButton = findViewById<Button>(R.id.startCaptureButton)
        stopCaptureButton = findViewById<Button>(R.id.stopCaptureButton)
        captureStatusText = findViewById(R.id.captureStatusText)

        //info generali
        infoConnectionsText = findViewById(R.id.infoConnectionsText)
        infoLastSyncText = findViewById(R.id.infoLastSyncText)
        infoSyncStatusText = findViewById(R.id.infoSyncStatusText)

        //logout button
        val logoutButton = findViewById<Button>(R.id.logoutButton)

        // start cattura dati
        startCaptureButton.setOnClickListener {
            TesiCaptureController.start(this)
            Toast.makeText(this, "Avvio cattura richiesto", Toast.LENGTH_SHORT).show()
            refreshCaptureStatusUntilStable()
        }


        //stop cattura dati
        stopCaptureButton.setOnClickListener {
            TesiCaptureController.stop()
            Toast.makeText(this, "Stop cattura richiesto", Toast.LENGTH_SHORT).show()
            refreshCaptureStatusUntilStable()
        }


        //gestione logout
        logoutButton.setOnClickListener {

            //dialog del logout
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Vuoi davvero uscire?")
                .setPositiveButton("Sì") { _, _ ->

                    TesiCaptureController.stop() //ferma cattura se in corso

                    sessionManager.logout() // effettua il logout

                    //torna alla schermata di login
                    val intent = Intent(this, TesiLoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK //pulisce stack activity, impedisce di tornare indietro
                    startActivity(intent)
                }
                .setNegativeButton("Annulla", null)
                .show()
        }


        // VISUALIZZA DATI
        openDataButton.setOnClickListener {
            val intent = Intent(this, TesiDataActivity::class.java)
            startActivity(intent)
        }

        //EXPORT CSV
        exportButton.setOnClickListener {

            if (!sessionManager.isLoggedIn()) {
                Toast.makeText(this, "Devi fare login", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Thread per download file CSV
            Thread {
                // Client backend con autenticazione JWT
                val client = BackendClient(
                    baseUrl = BackendConfig.getBaseUrl(),
                    sessionManager = sessionManager
                )

                val csvBytes = client.downloadCsv() //Scarica CSV come array di byte

                if (csvBytes == null || csvBytes.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this, "Nessun dato pronto da esportare", Toast.LENGTH_SHORT).show()
                    }
                    return@Thread
                }

                runOnUiThread {
                    try {

                        // Crea un formatter per generare un timestamp nel nome del file
                        val sdf = java.text.SimpleDateFormat(
                            "yyyyMMdd_HHmmss",
                            java.util.Locale.getDefault()
                        )

                        // Genera il timestamp corrente
                        val timestamp = sdf.format(java.util.Date())

                        // Costruisce il nome del file includendo il timestamp
                        // (evita sovrascritture di file precedenti)
                        val fileName = "tesi_network_data_$timestamp.csv"

                        // Ottiene il ContentResolver per interagire con il MediaStore
                        val resolver = contentResolver

                        // Prepara i metadati del file da salvare
                        val contentValues = android.content.ContentValues().apply {

                            // Nome visibile del file
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)

                            // Tipo MIME del file
                            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")

                            // Se Android >= 10 (API 29 - Q), usa Scoped Storage
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                                // Specifica la cartella Download come percorso relativo
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/")

                                // Marca il file come "in scrittura"
                                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                        }

                        // Se Android >= 10 usa la collezione Downloads ufficiale
                        // altrimenti usa la collezione generica Files
                        val collection =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                android.provider.MediaStore.Downloads
                                    .getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            } else {
                                android.provider.MediaStore.Files
                                    .getContentUri("external")
                            }

                        // Inserisce il file nel MediaStore (crea l'entry)
                        val itemUri = resolver.insert(collection, contentValues)

                        if (itemUri != null) {

                            // Apre uno stream di scrittura verso il file creato
                            resolver.openOutputStream(itemUri)?.use { outputStream ->

                                // Scrive i byte del CSV nel file
                                outputStream.write(csvBytes)
                            }

                            // Se Android >= 10, rende il file definitivo e visibile
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                contentValues.clear()
                                contentValues.put(
                                    android.provider.MediaStore.MediaColumns.IS_PENDING,
                                    0
                                )
                                resolver.update(itemUri, contentValues, null, null)
                            }

                            DownloadNotifier.showDownloadCompleted(this, fileName, itemUri) //notifica download
                            // Notifica di successo
                            Toast.makeText(
                                this,
                                "CSV salvato in Download come $fileName",
                                Toast.LENGTH_LONG
                            ).show()


                        } else {
                            Toast.makeText(this, "Errore creazione file", Toast.LENGTH_SHORT).show()
                        }

                    } catch (e: Exception) {

                        // Log tecnico per debug
                        Log.e("TESI_CSV", "Errore salvataggio file", e)

                        // Notifica utente
                        Toast.makeText(this, "Errore salvataggio file", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }

        // CANCELLAZIONE DATI
        deleteButton.setOnClickListener {

            if (!sessionManager.isLoggedIn()) {
                Toast.makeText(this, "Devi fare login", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this) //Dialog di conferma operazione

                .setTitle("Cancella dati")
                .setMessage( "Questa operazione cancellerà definitivamente tutti i dati associati al tuo account.\n\nVuoi continuare?" )

                .setPositiveButton("Sì, cancella") { _, _ ->

                    Thread {
                        val client = BackendClient(
                            baseUrl = BackendConfig.getBaseUrl(),
                            sessionManager = sessionManager
                        )

                        val success = client.deleteMyData() //Richiesta DELETE al backend

                        runOnUiThread {
                            if (success) {
                                TrackerRepository(this).clearAllNetworkRequests() // Cancella dati locali
                                Toast.makeText(this, "Operazione di cancellazione completata", Toast.LENGTH_LONG).show()
                                val prefs = getSharedPreferences("tesi_prefs", MODE_PRIVATE)
                                prefs.edit()
                                    .remove("last_sync_ts")
                                    .apply()

                                updateInfoPanel()

                            } else {
                                Toast.makeText(this, "Errore durante la cancellazione", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.start()
                }

                .setNegativeButton("Annulla", null) // Chiude il dialog senza fare nulla

                .show()
        }
        updateCaptureStatus() // Aggiorna lo stato della cattura
        updateInfoPanel() //aggirona info generali
    }

    //AVVISO PRIMO AVVIO
    private fun showFirstRunWarningIfNeeded() {

        val prefs = getSharedPreferences("tesi_prefs", MODE_PRIVATE) //SharedPreferences per memorizzare stato persistente semplice

        val alreadyShown = prefs.getBoolean("warning_shown", false) // Verifica se il warning è già stato mostrato

        if (alreadyShown) return // Se sì esce dal metodo

        AlertDialog.Builder(this)
            .setTitle("Avviso importante")
            .setMessage(
                "Questa applicazione utilizza un servizio VPN locale per monitorare " +
                        "il traffico di rete generato dal dispositivo, al fine di analizzare " +
                        "le connessioni effettuate dalle applicazioni installate.\n\n" +

                        "I dati raccolti sono anonimizzati e possono includere una posizione " +
                        "geografica approssimativa. Non vengono analizzati né memorizzati " +
                        "i contenuti delle comunicazioni.\n\n" +

                        "Le informazioni vengono inviate a un server remoto esclusivamente " +
                        "per finalità di analisi."
            )
            .setPositiveButton("Ho capito") { _, _ ->
                prefs.edit()
                    .putBoolean("warning_shown", true) // Segna il warning come mostrato
                    .apply()

                requestLocationPermissionIfNeeded()
            }
            .setCancelable(false) //Impedisce chiusura senza consenso

            .show()
    }

    // al ritorno alla schermata, richiama metodo per aggiornare stato
    override fun onResume() {
        super.onResume()
        updateInfoPanel()
        updateCaptureStatus()
    }

    // metodo per aggiornare lo stato della cattura
    private fun updateCaptureStatus() {
        val active = isVpnActive()

        if (active) {
            captureStatusText.text = "Stato cattura: ATTIVA"
            captureStatusText.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            )
            startCaptureButton.isEnabled = false
            stopCaptureButton.isEnabled = true
        } else {
            captureStatusText.text = "Stato cattura: FERMA"
            captureStatusText.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
            startCaptureButton.isEnabled = true
            stopCaptureButton.isEnabled = false
        }
    }

    // metodo per verificare se la cattura è attiva/disattiva
    private fun isVpnActive(): Boolean {
        //recupera connectivity manager di sistema
        val connectivityManager =
            getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

        val networks = connectivityManager.allNetworks //recupera reti
        //itera su tutte le reti attive del dispositivo
        for (network in networks) {
            val caps = connectivityManager.getNetworkCapabilities(network) //ottiene capacità associate a rete
            // se la rete utilizza trasport vpn, cattura considerata attiva
            if (caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                return true
            }
        }
        return false
    }

    //metodo per aggiornare info panel
    private fun updateInfoPanel() {
        val repo = TrackerRepository(this) //accesso al repo per leggere stato locale

        val count = repo.countAllNetworkRequests() //numero totale di connessioni memorizzate localmente
        infoConnectionsText.text = "Connessioni raccolte: $count"

        //sharedPreferences x memorizzare stato sync
        val prefs = getSharedPreferences("tesi_prefs", MODE_PRIVATE)

        val lastSync = prefs.getLong("last_sync_ts", 0L)
        val syncState = prefs.getString("last_sync_state", "IDLE")
        val pending = repo.countPendingNetworkRequests()

        // visualizza timestamp ultimo tentativo sync
        if (lastSync == 0L) {
            infoLastSyncText.text = "Ultimo sync: -"
        } else {
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            infoLastSyncText.text =
                "Ultimo sync: ${sdf.format(java.util.Date(lastSync))}"
        }

        // stato sync
        infoSyncStatusText.text = when {
            syncState == "SYNCING" ->
                "Stato sync: sincronizzazione in corso…"

            syncState == "ERROR" ->
                "Stato sync: errore di connessione"

            pending > 0 ->
                "Stato sync: $pending dati in attesa"

            else ->
                "Stato sync: sincronizzato"
        }
    }


    //richiesta permessi geolocation
    private fun requestLocationPermissionIfNeeded() {
        //verifica se permesso è già stato concesso
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // se non concesso, avvia richiesta permessi
        if (!fineGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
        }
    }

    // Metodo che aggiorna lo stato della cattura più volte,
    // finché non si stabilizza (retry con piccolo delay)
    private fun refreshCaptureStatusUntilStable() {
        // Crea un Handler associato al Main Thread (UI Thread)
        // Serve per eseguire codice con un ritardo temporale
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        var attempts = 0 //contatore tentativi effettuati
        val maxAttempts = 10 //max tentativi x evitare loop

        val runnable = object : Runnable { //def. runnable = blocco di codice eseguibile
            override fun run() { //metodo eseguito ogni volta che runnable parte
                updateCaptureStatus() //aggiorna stato corrente cattura

                attempts++
                if (attempts < maxAttempts) {
                    handler.postDelayed(this, 500) //riesegue runnable dopo 500ms
                }
            }
        }

        handler.post(runnable) //avvia subito prima esecuzione del runnable
    }
}
