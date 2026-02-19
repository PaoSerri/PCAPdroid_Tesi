package serri.tesi.capture

import androidx.activity.ComponentActivity
import androidx.preference.PreferenceManager
import com.emanuelef.remote_capture.CaptureHelper
import com.emanuelef.remote_capture.CaptureService
import com.emanuelef.remote_capture.model.CaptureSettings
import serri.tesi.service.LocationService

/**
 * Controller dedicato all'avvio e all'arresto della cattura del traffico di rete.
 *
 * Agisce tra la UI dell'applicazione e il servizio di cattura fornito dal fork di PCAPdroid.
 *
 * NON avvia direttamente il CaptureService, ma utilizza CaptureHelper
 * per garantire che:
 * - il consenso VPN sia stato concesso
 * - le impostazioni di cattura siano correttamente inizializzate
 * - il ciclo di vita del servizio rispetti i vincoli imposti da Android
 */
object TesiCaptureController {
    //singleton per evitare inizializzazioni multiple o stati non coerenti

    private var captureHelper: CaptureHelper? = null //helper fornito da PCAPdroid, gestione servizio cattura

    //inizializza controller e CaptureHelper
    //invocato da activity (main), richiesta autorizzazione vpn richiede contesto con ui e lifecycle
    fun init(activity: ComponentActivity) {
        if (captureHelper == null) {
            captureHelper = CaptureHelper(activity, true)
        }

        //Inizializza anche LocationService
        LocationService.init(activity.applicationContext)
    }

    //avvio cattura traffico
    fun start(activity: ComponentActivity) {
        if (CaptureService.isServiceActive()) return //evita avvii multipli

        // carica le impostazioni di cattura da SharedPreferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val settings = CaptureSettings(activity, prefs)

        captureHelper?.startCapture(settings) //avvio da helper, non diretto

        LocationService.start() //avvio servizio gps
    }

    //arresta il servizio di cattura
    fun stop() {
        if (CaptureService.isServiceActive()) {
            CaptureService.stopService()
        }
        LocationService.stop() //ferma servizio gps
    }
}
