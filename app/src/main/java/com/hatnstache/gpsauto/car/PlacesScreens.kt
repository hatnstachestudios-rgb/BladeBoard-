package com.hatnstache.gpsauto.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.hatnstache.gpsauto.data.PlaceStore
import com.hatnstache.gpsauto.map.Bitmaps
import com.hatnstache.gpsauto.nav.Geo
import com.hatnstache.gpsauto.nav.NavigationEngine
import com.hatnstache.gpsauto.nav.PlaceSearch
import com.hatnstache.gpsauto.nav.SearchResult
import com.hatnstache.gpsauto.nav.Units
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

private fun here(): LatLng? = NavigationEngine.location.value?.let { LatLng(it.latitude, it.longitude) }

/** Saved places with their blip icons. Tap one to start the route. */
class PlacesScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        val places = PlaceStore.places.value
        val me = here()
        if (places.isEmpty()) list.setNoItemsMessage("Save places in GPS Auto on your phone")
        places.forEach { p ->
            val ll = LatLng(p.lat, p.lon)
            val row = Row.Builder()
                .setTitle(p.name)
                .setImage(
                    CarIcon.Builder(IconCompat.createWithBitmap(Bitmaps.blip(carContext, p.icon.res, p.color, 96))).build(),
                    Row.IMAGE_TYPE_SMALL,
                )
                .setOnClickListener {
                    NavigationEngine.navigateTo(p.name, ll)
                    screenManager.pop()
                }
            if (me != null) row.addText(Units.distance(Geo.distance(me, ll)))
            list.addItem(row.build())
        }
        return ListTemplate.Builder()
            .setTitle("Saved places")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}

/** Address / place search. The keyboard only works while parked; results work any time. */
class SearchScreen(carContext: CarContext) : Screen(carContext) {
    private var results: List<SearchResult> = emptyList()
    private var loading = false
    private var message: String? = "Search for a place or address"
    private var job: Job? = null

    private fun search(text: String) {
        if (text.isBlank()) return
        job?.cancel()
        loading = true
        invalidate()
        job = lifecycleScope.launch {
            message = try {
                results = PlaceSearch.search(text, here())
                if (results.isEmpty()) "No places found" else null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                results = emptyList()
                "Search isn't reachable right now"
            }
            loading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val b = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}
            override fun onSearchSubmitted(searchText: String) = search(searchText)
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Search a place or address")
            .setShowKeyboardByDefault(false)

        if (loading) {
            b.setLoading(true)
        } else {
            val me = here()
            val list = ItemList.Builder()
            message?.let { list.setNoItemsMessage(it) }
            results.take(6).forEach { r ->
                val ll = LatLng(r.lat, r.lon)
                val detail = listOfNotNull(me?.let { Units.distance(Geo.distance(it, ll)) }, r.subtitle.ifBlank { null }).joinToString(" · ")
                val row = Row.Builder().setTitle(r.title).setOnClickListener {
                    NavigationEngine.navigateTo(r.title, ll)
                    screenManager.popToRoot()
                }
                if (detail.isNotBlank()) row.addText(detail)
                list.addItem(row.build())
            }
            b.setItemList(list.build())
        }
        return b.build()
    }
}
