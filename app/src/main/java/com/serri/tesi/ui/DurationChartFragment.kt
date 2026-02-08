package serri.tesi.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.emanuelef.remote_capture.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import serri.tesi.analysis.ChartFilterable
import serri.tesi.analysis.TimeFilter
import serri.tesi.repo.TrackerRepository

class DurationChartFragment :
    Fragment(R.layout.fragment_duration_chart),
    ChartFilterable {

    private lateinit var chart: BarChart //riferimento a grafico a barre
    private var currentFilter: TimeFilter = TimeFilter.ALL //filtro temporale

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chart = view.findViewById(R.id.durationBarChart) //inizializza grafico da layout
        reloadChart() //caricamento iniziale grafico
    }

    //callback invocata da activity quando cambia filtro, aggiorna filtro e ricarica grafico
    override fun onFilterChanged(filter: TimeFilter) {
        currentFilter = filter
        reloadChart()
    }

    //ricostruzione grafico in base a filtro corrente
    //dati recuperati da repository locale
    private fun reloadChart() {
        val repo = TrackerRepository(requireContext())
        //crea grafico
        val histogram = repo.getConnectionDurationHistogram(currentFilter)

        val entries = ArrayList<BarEntry>()

        //etichette x intervalli di durata
        val labels = listOf(
            "< 100\nms",
            "100–500\nms",
            "500–1\ns",
            "1–5\ns",
            "> 5\ns"
        )

        // conversione dati aggregati in BarEntry
        histogram.entries.forEachIndexed { index, entry ->
            entries.add(BarEntry(index.toFloat(), entry.value.toFloat()))
        }

        // Dataset del grafico
        val dataSet = BarDataSet(entries, "Numero connessioni")
        val data = BarData(dataSet)
        data.barWidth = 0.7f //imposta larghezza barre
        chart.data = data

        //configurazione asse x con etichette personalizzate
        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            isGranularityEnabled = true
            labelCount = labels.size
            setDrawGridLines(false)
            // impostazioni x evitare sovrapposizioni + allineamento corretto
            setCenterAxisLabels(false)
            textSize = 12f
            axisMinimum = -0.5f
            axisMaximum = labels.size - 0.5f
        }

        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false

        chart.invalidate() //forza ridisegno del grafico
    }
}

