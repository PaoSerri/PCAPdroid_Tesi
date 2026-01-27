package serri.tesi.analysis

interface ChartFilterable {
    fun onFilterChanged(filter: TimeFilter)
}
//serve per notificare tutti i grafici quando cambia il filtro