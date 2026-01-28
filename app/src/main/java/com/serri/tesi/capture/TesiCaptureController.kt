package serri.tesi.capture

import androidx.activity.ComponentActivity
import androidx.preference.PreferenceManager
import com.emanuelef.remote_capture.CaptureHelper
import com.emanuelef.remote_capture.CaptureService
import com.emanuelef.remote_capture.model.CaptureSettings

object TesiCaptureController {

    private var captureHelper: CaptureHelper? = null

    fun init(activity: ComponentActivity) {
        if (captureHelper == null) {
            captureHelper = CaptureHelper(activity, true)
        }
    }

    fun start(activity: ComponentActivity) {
        if (CaptureService.isServiceActive()) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val settings = CaptureSettings(activity, prefs)

        captureHelper?.startCapture(settings)
    }

    fun stop() {
        if (CaptureService.isServiceActive()) {
            CaptureService.stopService()
        }
    }
}
