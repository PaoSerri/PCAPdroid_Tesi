package serri.tesi.ui

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.emanuelef.remote_capture.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.serri.tesi.ui.ChartPagerAdapter
import serri.tesi.analysis.ChartFilterable
import serri.tesi.analysis.TimeFilter
/**
 * Activity dedicata alla visualizzazione grafica dei dati di rete.
 *
 * I grafici sono organizzati in Fragment separati, navigabili
 * tramite ViewPager2 e TabLayout.
 *
 * Questa Activity coordina:
 * - la navigazione tra i grafici
 * - la gestione del filtro temporale
 *
 * Non elabora direttamente i dati, ogni Fragment è responsabile della propria visualizzazione.
 */
class TesiAnalysisActivity : AppCompatActivity() {

    private var currentFilter = TimeFilter.ALL //filtro temporale (default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tesi_analysis)

        val pager = findViewById<ViewPager2>(R.id.chartPager)
        val tabs = findViewById<TabLayout>(R.id.chartIndicator)

        pager.adapter = ChartPagerAdapter(this) //adapter responsabile di creazione dei fragment grafici
        TabLayoutMediator(tabs, pager) { _, _ -> }.attach() //collega viewpager e tablayout x navigazione swipe

        setupTimeFilter() //inizializza filtro temporale
    }

    // metodo x inizializzare spinner filtro temporale
    private fun setupTimeFilter() {
        val spinner = findViewById<Spinner>(R.id.timeFilterSpinner)
        val filters = TimeFilter.values()

        //adapter mostra etichette dei filtri temporali
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            filters.map { it.label }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        //listener x intercettare il cambio di filtro
        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    currentFilter = filters[position] //aggiorna filtro corrente
                    notifyFragments() //notifica tutti i fragment
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
    }

    // metodo x notificare fragment cambio filtro
    // comunicazione tramite interfaccia ChartFilterable
    private fun notifyFragments() {
        supportFragmentManager.fragments.forEach {
            if (it is ChartFilterable) {
                it.onFilterChanged(currentFilter)
            }
        }
    }

    // getter che restituisce filtro corrente
    fun getCurrentFilter(): TimeFilter = currentFilter

}
