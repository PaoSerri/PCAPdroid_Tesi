package com.serri.tesi.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import serri.tesi.ui.DurationChartFragment
import serri.tesi.ui.ProtocolChartFragment
import serri.tesi.ui.TopAppsChartFragment
/**
 * Adapter x gestione dei Fragment dei grafici all'interno di ViewPager2.
 *
 * Ogni pagina del ViewPager corrisponde a un grafico
 *
 * FragmentStateAdapter consente una gestione efficiente
 * del ciclo di vita dei Fragment, creando e distruggendo le viste
 * in base alla navigazione dell'utente.
 */
class ChartPagerAdapter(activity: AppCompatActivity) :
    FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3 //num grafici disponibili

    //crazione fragment associato a posizione indicata
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ProtocolChartFragment()
            1 -> TopAppsChartFragment()
            else -> DurationChartFragment()
        }
    }
}
