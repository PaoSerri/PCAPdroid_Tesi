package serri.tesi.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.emanuelef.remote_capture.R
import serri.tesi.repo.TrackerRepository

/**
 * Mostra una lista di connessioni aggregate utilizzando una RecyclerView.
 *
 * Questa Activity non implementa logica di business:
 * - i dati vengono recuperati dal repository locale
 * - la UI si limita a presentarli all'utente
 */
class TesiDataActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView // Recycler per visualizzare dati
    private lateinit var dataCountText: TextView // Elemento UI per visualizzare il numero di dati mostrati


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tesi_data)

        //inizializzazione elementi ui
        dataCountText = findViewById(R.id.dataCountText)

        recyclerView = findViewById<RecyclerView>(R.id.dataRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this) //layout verticale

        //per aprire sezione grafica
        val analysisButton = findViewById<ImageButton>(R.id.openAnalysisButton)

        analysisButton.setOnClickListener {
            val intent = Intent(this, TesiAnalysisActivity::class.java)
            startActivity(intent)
        }

        //separatore grafico tra elem. della lista
        recyclerView.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        //recupero dati dal repository locale
        val repo = TrackerRepository(this)

        val data = repo.getLastNetworkRequests(100)// caricamento iniziale, ultimi 100 record

        recyclerView.adapter = NetworkDataAdapter(data.toMutableList()) //inizializzazione adapter con i dati recuperati
        dataCountText.text = "Connessioni mostrate: ${data.size}"
    }
    // aggiorna adapter con nuovi dati
    override fun onResume() {
        super.onResume()

        val repo = TrackerRepository(this)
        val data = repo.getLastNetworkRequests(100)
        (recyclerView.adapter as NetworkDataAdapter).updateData(data) //aggiorna adapter con nuovi dati

        dataCountText.text = "Connessioni mostrate: ${data.size}"
    }

}