package serri.tesi.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.emanuelef.remote_capture.R
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import serri.tesi.analysis.ChartFilterable
import serri.tesi.analysis.TimeFilter
import serri.tesi.repo.TrackerRepository

class TopAppsChartFragment :
    Fragment(R.layout.fragment_top_apps_chart),
    ChartFilterable {

    private lateinit var chart: PieChart
    private var currentFilter: TimeFilter = TimeFilter.ALL

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chart = view.findViewById(R.id.topAppsPieChart)
        reloadChart()
    }

    //callback che aggiorna il filtro corrente e ricarica il grafico
    override fun onFilterChanged(filter: TimeFilter) {
        currentFilter = filter
        reloadChart()
    }

    private fun reloadChart() {
        val repo = TrackerRepository(requireContext()) // recupera dati dal repository locale

        // top 5 app per byte, filtrate temporalmente
        val dataMap = repo.getTopAppsByBytes(
            filter = currentFilter,
            limit = 5
        )

        // se nessun dato, elimina il grafico
        val totalBytes = dataMap.values.sum()
        if (totalBytes == 0L) {
            chart.clear()
            return
        }

        val entries = mutableListOf<PieEntry>() // dati da visualizzare
        var shownBytes = 0L

        dataMap.entries.forEach {
            entries.add(PieEntry(it.value.toFloat(), it.key))
            shownBytes += it.value
        }

        // aggiunta Altre app
        val otherBytes = totalBytes - shownBytes
        if (otherBytes > 0) {
            entries.add(PieEntry(otherBytes.toFloat(), "Altre app"))
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = listOf(
            Color.rgb(66, 133, 244),
            Color.rgb(219, 68, 55),
            Color.rgb(244, 180, 0),
            Color.rgb(15, 157, 88),
            Color.rgb(171, 71, 188),
            Color.GRAY // altre app
        )

        // stile valori
        dataSet.valueTextSize = 12f
        dataSet.valueFormatter = PercentFormatter(chart)

        val pieData = PieData(dataSet)
        chart.data = pieData

        //configurazione chart
        chart.setUsePercentValues(true) //percentuali nel grafico
        chart.setDrawEntryLabels(false) //niente testo nel cerchio
        chart.description.isEnabled = false

        chart.legend.isEnabled = true
        chart.legend.isWordWrapEnabled = true
        chart.legend.setDrawInside(false)
        chart.legend.textSize = 12f
        chart.legend.formSize = 12f
        chart.legend.isWordWrapEnabled = true

        chart.invalidate() // ridisegna il grafico
    }
}
