package serri.tesi.analysis

enum class TimeFilter(val label: String, val millis: Long?) {
    ALL("Tutto", null),
    LAST_10_MIN("Ultimi 10 minuti", 10 * 60 * 1000L),
    LAST_HOUR("Ultima ora", 60 * 60 * 1000L),
    LAST_24_HOURS("Ultime 24 ore", 24 * 60 * 60 * 1000L)
}
