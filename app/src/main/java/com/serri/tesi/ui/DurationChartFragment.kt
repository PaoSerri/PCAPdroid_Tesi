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

    private lateinit var chart: BarChart
    private var currentFilter: TimeFilter = TimeFilter.ALL

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chart = view.findViewById(R.id.durationBarChart)
        reloadChart()
    }

    override fun onFilterChanged(filter: TimeFilter) {
        currentFilter = filter
        reloadChart()
    }

    private fun reloadChart() {
        val repo = TrackerRepository(requireContext())
        val histogram = repo.getConnectionDurationHistogram(currentFilter)

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        histogram.entries.forEachIndexed { index, entry ->
            entries.add(BarEntry(index.toFloat(), entry.value.toFloat()))
            labels.add(entry.key)
        }

        val dataSet = BarDataSet(entries, "Numero connessioni")
        chart.data = BarData(dataSet)

        chart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
        }

        chart.axisRight.isEnabled = false
        chart.description.isEnabled = false
        chart.invalidate()
    }
}

