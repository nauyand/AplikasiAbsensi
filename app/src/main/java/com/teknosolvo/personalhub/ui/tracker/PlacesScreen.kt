package com.teknosolvo.personalhub.ui.tracker

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.data.Place
import com.teknosolvo.personalhub.data.PlaceKind
import com.teknosolvo.personalhub.data.TrackerStateEntity
import com.teknosolvo.personalhub.ui.components.ConfirmDialog
import com.teknosolvo.personalhub.ui.components.EmptyState
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.ui.components.IconBadge
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.Pill
import com.teknosolvo.personalhub.ui.components.PlaceColors
import com.teknosolvo.personalhub.ui.components.PlaceIcons
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.asColor
import com.teknosolvo.personalhub.ui.components.placeIcon
import com.teknosolvo.personalhub.ui.components.rememberPermissionActions
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.theme.Hub
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

@Composable
fun PlacesTab(onEdit: (Long) -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val places by c.tracker.placesFlow.collectAsStateWithLifecycle(emptyList())
    val state by c.tracker.stateFlow.collectAsStateWithLifecycle(TrackerStateEntity())

    if (places.isEmpty()) {
        EmptyState(Icons.Filled.LocationOn, "Belum ada lokasi",
            "Tambahkan Kantor, Rumah, tempat istirahat, atau lokasi lain. Atur radius deteksi sesuai kebutuhan.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(places, key = { it.id }) { p ->
            val color = p.colorArgb.asColor()
            HubCard(onClick = { onEdit(p.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(placeIcon(p.iconKey), if (p.enabled) color else Hub.extras.muted, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            if (state.currentPlaceId == p.id) {
                                Spacer(Modifier.width(8.dp)); Pill("Di sini", Hub.extras.success)
                            }
                        }
                        Text("${PlaceKind.label(p.kind)} · radius ${p.radiusM} m", style = MaterialTheme.typography.bodySmall,
                            color = Hub.extras.muted)
                        Text("%.5f, %.5f".format(p.latitude, p.longitude), style = MaterialTheme.typography.bodySmall,
                            color = Hub.extras.muted)
                    }
                    Switch(checked = p.enabled, onCheckedChange = { en -> scope.launch { c.tracker.setPlaceEnabled(p, en) } })
                }
            }
        }
    }
}

private fun defaultIconFor(kind: String) = when (kind) {
    PlaceKind.OFFICE -> "office"
    PlaceKind.HOME -> "home"
    PlaceKind.BREAK -> "coffee"
    PlaceKind.FOOD -> "food"
    else -> "place"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceEditScreen(id: Long, onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissions = rememberPermissionActions()

    var loaded by remember { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<Place?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(PlaceKind.OFFICE) }
    var icon by rememberSaveable { mutableStateOf("office") }
    var color by rememberSaveable { mutableStateOf(PlaceColors[0]) }
    var radius by rememberSaveable { mutableStateOf(100) }
    var lat by rememberSaveable { mutableStateOf("") }
    var lng by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var address by rememberSaveable { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L && !loaded) {
            c.db.places().byId(id)?.let { p ->
                original = p; name = p.name; kind = p.kind; icon = p.iconKey; color = p.colorArgb; radius = p.radiusM
                lat = p.latitude.toString(); lng = p.longitude.toString(); enabled = p.enabled; address = p.address
            }
            loaded = true
        } else if (id == 0L && lat.isEmpty()) {
            // Centre the map on the last known position for new places.
            c.db.trackerState().get()?.let { s -> if (s.lastLat != null && s.lastLng != null) { lat = "%.6f".format(java.util.Locale.US, s.lastLat); lng = "%.6f".format(java.util.Locale.US, s.lastLng) } }
        }
    }

    val latD = lat.replace(',', '.').toDoubleOrNull()
    val lngD = lng.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && latD != null && lngD != null && latD in -90.0..90.0 && lngD in -180.0..180.0 && radius in 20..5000

    fun useCurrent() {
        if (!c.locations.hasAnyLocation()) { permissions.requestForeground(); return }
        locating = true
        scope.launch {
            val loc = c.locations.currentFix(highAccuracy = true)
            locating = false
            if (loc == null) context.toast("Gagal membaca lokasi. Pastikan GPS aktif.")
            else {
                lat = "%.6f".format(java.util.Locale.US, loc.latitude); lng = "%.6f".format(java.util.Locale.US, loc.longitude)
                context.toast("Lokasi diambil (akurasi ±${loc.accuracy.toInt()} m)")
                address = c.locations.address(loc.latitude, loc.longitude)
            }
        }
    }

    HubScaffold(
        title = if (id == 0L) "Lokasi baru" else "Ubah lokasi",
        onBack = onBack,
        actions = {
            if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Hapus", tint = Hub.extras.danger) }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text("Nama lokasi") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())

            SectionLabel("Jenis")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceKind.all.forEach { k ->
                    FilterChip(selected = kind == k, onClick = {
                        if (icon == defaultIconFor(kind)) icon = defaultIconFor(k)
                        kind = k
                    }, label = { Text(PlaceKind.label(k)) })
                }
            }

            SectionLabel("Peta · ketuk untuk memilih titik")
            Box(Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.medium)
                .border(1.dp, Hub.extras.cardBorder, MaterialTheme.shapes.medium)) {
                MapPicker(latD, lngD, radius, color.asColor()) { p ->
                    lat = "%.6f".format(java.util.Locale.US, p.latitude); lng = "%.6f".format(java.util.Locale.US, p.longitude)
                    address = null
                }
            }
            Text("Peta: © OpenStreetMap contributors (butuh internet hanya untuk memuat peta).",
                style = MaterialTheme.typography.labelSmall, color = Hub.extras.muted)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(lng, { lng = it }, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            OutlinedButton(onClick = ::useCurrent, enabled = !locating, modifier = Modifier.fillMaxWidth()) {
                if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.MyLocation, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Gunakan lokasi saat ini")
            }
            address?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted) }

            SectionLabel("Radius deteksi")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = radius.toFloat().coerceIn(20f, 1000f), onValueChange = { radius = (it / 5).toInt() * 5 },
                    valueRange = 20f..1000f, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    radius.toString(), { v -> v.filter(Char::isDigit).take(4).toIntOrNull()?.let { radius = it } ?: run { if (v.isEmpty()) radius = 0 } },
                    suffix = { Text("m") }, singleLine = true, modifier = Modifier.width(110.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text("Disarankan ≥ 100 m. Radius kecil (< 75 m) rentan terhadap GPS goyah di dalam gedung.",
                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)

            SectionLabel("Ikon")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceIcons.forEach { (key, vec) ->
                    val sel = icon == key
                    Surface(onClick = { icon = key }, shape = MaterialTheme.shapes.small,
                        color = if (sel) color.asColor().copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (sel) BorderStroke(1.5.dp, color.asColor()) else null, modifier = Modifier.size(46.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(vec, key, tint = if (sel) color.asColor() else Hub.extras.muted) }
                    }
                }
            }
            SectionLabel("Warna")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaceColors.forEach { col ->
                    Box(Modifier.size(30.dp).clip(CircleShape).background(col.asColor()).clickable { color = col },
                        contentAlignment = Alignment.Center) {
                        if (col == color) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aktif", style = MaterialTheme.typography.bodyLarge)
                    Text("Lokasi nonaktif tidak dideteksi", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                }
                Switch(enabled, { enabled = it })
            }
            Button(
                enabled = valid,
                onClick = {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val base = original
                        val p = Place(
                            id = base?.id ?: 0, name = name.trim(), kind = kind, latitude = latD!!, longitude = lngD!!,
                            radiusM = radius, colorArgb = color, iconKey = icon, enabled = enabled,
                            address = address ?: base?.address, createdAt = base?.createdAt ?: now, updatedAt = now,
                        )
                        c.tracker.savePlace(p)
                        if (base != null && base.enabled != enabled && !enabled) c.tracker.setPlaceEnabled(p, false)
                        context.toast("Lokasi disimpan")
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Simpan lokasi") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) ConfirmDialog(
        "Hapus lokasi?",
        "Geofence dihapus. Riwayat aktivitas di lokasi ini tetap disimpan dengan nama aslinya.",
        onConfirm = { scope.launch { original?.let { c.tracker.deletePlace(it) }; onBack() } },
        onDismiss = { confirmDelete = false },
    )
}

/** OpenStreetMap picker (osmdroid). No API key, tiles cached locally. */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapPicker(lat: Double?, lng: Double?, radius: Int, color: Color, onPick: (GeoPoint) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(if (lat != null) 17.0 else 5.0)
            controller.setCenter(GeoPoint(lat ?: -2.5, lng ?: 118.0))
            // Let the map handle drags instead of the scrolling parent.
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
    }
    val marker = remember { Marker(map).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = "Titik lokasi" } }
    val circle = remember { Polygon(map) }
    var centered by remember { mutableStateOf(lat != null) }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, ev ->
            when (ev) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { onPick(p); return true }
            override fun longPressHelper(p: GeoPoint): Boolean { onPick(p); return true }
        }))
        onDispose {
            lifecycle.removeObserver(obs)
            map.onDetach()
        }
    }

    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize(), update = { m ->
        m.overlays.remove(circle); m.overlays.remove(marker)
        if (lat != null && lng != null) {
            val p = GeoPoint(lat, lng)
            circle.points = Polygon.pointsAsCircle(p, radius.toDouble())
            circle.fillPaint.color = color.copy(alpha = 0.22f).toArgb()
            circle.outlinePaint.color = color.toArgb()
            circle.outlinePaint.strokeWidth = 3f
            marker.position = p
            m.overlays.add(circle); m.overlays.add(marker)
            if (!centered) { m.controller.setZoom(17.0); m.controller.setCenter(p); centered = true }
        }
        m.invalidate()
    })
}
