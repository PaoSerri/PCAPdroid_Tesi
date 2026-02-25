package serri.tesi.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.emanuelef.remote_capture.R

object DownloadNotifier {

    private const val CHANNEL_ID = "tesi_download_channel" //id univoco del notification channel (android8+)
    private const val CHANNEL_NAME = "Download CSV" //nome visibile canale in impostazioni sistema
    private const val NOTIFICATION_ID = 2001 //id notifica, serve x aggiornare/sovrascrivere

    //mostra notifica di download completato
    fun showDownloadCompleted(context: Context, fileName: String, fileUri: Uri) {
// ottiene notification manager da sistema
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crea channel notifiche(obblig da Android 8 - api 26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel) //registra canale nel sistema
        }

        // Intent per aprire il file dalla notifica
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "text/csv") //imposta uri e tipo mime del file
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK //concede permesso temp. di lettura da altre app, FLAG_ACTIVITY_NEW_TASK necessario fuori da Activity
        }

        // Wrappa l'Intent in un PendingIntent
        //serve perché la notifica verrà eseguita dal sistema
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        //costruzione notifica
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) //icona in status bar
            .setContentTitle("Download completato") //titolo notifica
            .setContentText(fileName) //nome file
            .setAutoCancel(true) //chiusura al click
            .setContentIntent(pendingIntent) //azione a click, apertura file
            .build()

        manager.notify(NOTIFICATION_ID, notification) //mostra notifica
    }

    //richiesta permesso notifiche
    fun requestNotificationPermissionIfNeeded(activity: android.app.Activity) {

        // permesso runtime per notifiche, richiesto da android 13 api 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            //controlla se permesso gia concesso
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    activity,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {

                //richiede permesso a utente
                androidx.core.app.ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    2001
                )
            }
        }
    }
}