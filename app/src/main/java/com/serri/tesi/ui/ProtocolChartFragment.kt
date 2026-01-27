package serri.tesi.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.emanuelef.remote_capture.R
import serri.tesi.repo.TrackerRepository
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import serri.tesi.analysis.ChartFilterable
import serri.tesi.analysis.TimeFilter

class ProtocolChartFragment :
    Fragment(R.layout.fragment_protocol_chart),
    ChartFilterable {

    private lateinit var chart: BarChart
    private var currentFilter: TimeFilter = TimeFilter.ALL

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chart = view.findViewById(R.id.protocolBarChart)

        // carica con filtro iniziale
        reloadChart()
    }

    override fun onFilterChanged(filter: TimeFilter) {
        currentFilter = filter
        reloadChart()
    }

    private fun reloadChart() {
        val repo = TrackerRepository(requireContext())
        val data = repo.getBytesGroupedByProtocol(currentFilter)

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        data.entries.forEachIndexed { index, entry ->
            entries.add(BarEntry(index.toFloat(), entry.value.toFloat()))
            labels.add(entry.key)
        }

        val dataSet = BarDataSet(entries, "Byte totali per protocollo")
        val barData = BarData(dataSet)

        chart.data = barData
        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
        }

        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.invalidate()
    }
}
