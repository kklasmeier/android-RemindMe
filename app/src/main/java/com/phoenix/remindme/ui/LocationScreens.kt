package com.phoenix.remindme.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phoenix.remindme.ai.ReminderDraft
import com.phoenix.remindme.alerts.AlertPreferences
import com.phoenix.remindme.data.LocationTrigger
import com.phoenix.remindme.data.SavedPlaceEntity
import com.phoenix.remindme.location.PlaceCandidate
import com.phoenix.remindme.ui.theme.Accent
import com.phoenix.remindme.ui.theme.Accent2
import com.phoenix.remindme.ui.theme.Danger
import com.phoenix.remindme.ui.theme.Glass
import com.phoenix.remindme.ui.theme.GlassBorder
import com.phoenix.remindme.ui.theme.TextDim
import com.phoenix.remindme.ui.theme.TextMuted
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolygonOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LocationFlowScreen(
    draft: ReminderDraft,
    onClose: () -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
    placeLabel: String? = null,
    onLocationConfirmed: ((PlaceCandidate, LocationTrigger, Int, Float) -> Unit)? = null,
    viewModel: LocationViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val saved by viewModel.savedPlaces.collectAsStateWithLifecycle()
    var query by remember(draft.locationName) { mutableStateOf(draft.locationName.orEmpty()) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableStateOf(false) }
    var requestedFineForSearch by remember { mutableStateOf(false) }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh = !permissionRefresh
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefresh = !permissionRefresh
    }
    @Suppress("UNUSED_EXPRESSION")
    permissionRefresh
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh = !permissionRefresh
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val backgroundGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    LaunchedEffect(draft.locationName, fineGranted) {
        val query = draft.locationName?.trim().orEmpty()
        if (query.isBlank()) return@LaunchedEffect
        if (fineGranted) {
            viewModel.search(query)
        } else if (!requestedFineForSearch) {
            requestedFineForSearch = true
            fineLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    when {
        state.selected != null -> LocationVerifyScreen(
            draft = draft,
            candidate = state.selected,
            placeLabel = placeLabel,
            fineGranted = fineGranted,
            backgroundGranted = backgroundGranted,
            error = state.error,
            saving = state.saving,
            onBack = viewModel::clearSelection,
            onCancel = onCancel,
            onRequestFine = { fineLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            onRequestBackground = {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            },
            onSave = { trigger, offset, radius ->
                val candidate = state.selected ?: return@LocationVerifyScreen
                onLocationConfirmed?.invoke(candidate, trigger, offset, radius)
                    ?: viewModel.saveReminder(draft, candidate, trigger, offset, radius, onSaved)
            },
        )
        state.results.isNotEmpty() -> LocationResultsScreen(
            query = query,
            results = state.results,
            onBack = viewModel::clearSearch,
            onCancel = onCancel,
            onClear = {
                query = ""
                viewModel.clearSearch()
            },
            onSelect = viewModel::select,
        )
        else -> LocationSearchScreen(
            query = query,
            onQueryChange = { query = it },
            searching = state.searching,
            error = state.error,
            savedPlaces = saved,
            onBack = onClose,
            onCancel = onCancel,
            onSearch = { viewModel.search(query) },
            onSavedPlace = viewModel::select,
        )
    }
}

@Composable
private fun LocationSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    error: String?,
    savedPlaces: List<SavedPlaceEntity>,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSearch: () -> Unit,
    onSavedPlace: (SavedPlaceEntity) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LocationHeader("Set Location", onBack, onCancel) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = .1f)),
                border = BorderStroke(1.dp, Accent.copy(alpha = .3f)),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = Accent)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Find the place", color = Accent, fontWeight = FontWeight.Bold)
                        Text("Type a business, address, or landmark", color = TextDim, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            var searchFocused by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { searchFocused = it.isFocused },
                placeholder = {
                    if (!searchFocused && query.isEmpty()) {
                        Text(
                            "Search a place, address, or landmark",
                            color = TextDim,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSearch, enabled = query.isNotBlank() && !searching, modifier = Modifier.fillMaxWidth()) {
                Text("Search")
            }
            error?.let { Text(it, color = Danger, fontSize = 12.sp) }
        }
        item { Text("SAVED PLACES", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        items(savedPlaces.filter {
            query.isBlank() || it.label.contains(query, true) || it.address.contains(query, true)
        }, key = { it.id }) { place ->
            PlaceRow(place.displayName, place.address) { onSavedPlace(place) }
        }
    }
}

@Composable
private fun LocationResultsScreen(
    query: String,
    results: List<PlaceCandidate>,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
) {
    val mapResults = remember(results) { results.filterNot { it.isSaved } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { LocationHeader("Search Results", onBack, onCancel) }
        item {
            Text("“$query”", color = TextMuted)
            Text("${mapResults.size} matches · confirm the correct location", color = TextDim, fontSize = 11.sp)
        }
        items(mapResults) { candidate ->
            PlaceRow(
                candidate.name,
                buildString {
                    append(candidate.address)
                    candidate.distanceMeters?.let { append(" · ${formatDistance(it)}") }
                },
            ) { onSelect(candidate) }
        }
        item {
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text("Search for a different place")
            }
        }
    }
}

@Composable
private fun LocationVerifyScreen(
    draft: ReminderDraft,
    candidate: PlaceCandidate,
    placeLabel: String?,
    fineGranted: Boolean,
    backgroundGranted: Boolean,
    error: String?,
    saving: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onRequestFine: () -> Unit,
    onRequestBackground: () -> Unit,
    onSave: (LocationTrigger, Int, Float) -> Unit,
) {
    var trigger by remember { mutableStateOf(LocationTrigger.ARRIVING) }
    var offset by remember(draft.locationOffsetMinutes) {
        mutableStateOf(draft.locationOffsetMinutes.toString())
    }
    val context = LocalContext.current
    val defaultRadius = remember(context) { AlertPreferences(context).geofenceRadius() }
    var radius by remember { mutableStateOf(defaultRadius) }
    var mapFullscreen by remember { mutableStateOf(false) }
    BackHandler(mapFullscreen) { mapFullscreen = false }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                if (!mapFullscreen) OSMMap(candidate, radius)
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(Color(0xCC10131D), RoundedCornerShape(50)),
                ) { Icon(Icons.Default.ArrowBack, "Back") }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color(0xCC10131D), RoundedCornerShape(50)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel", color = TextMuted) }
                    IconButton(onClick = { mapFullscreen = true }) {
                        Icon(Icons.Default.Fullscreen, "View map full screen")
                    }
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(candidate.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(candidate.address, color = TextMuted, fontSize = 12.sp)
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("geo:${candidate.latitude},${candidate.longitude}?q=${candidate.latitude},${candidate.longitude}(${Uri.encode(candidate.name)})"),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Map, null)
                Text("  Open in Maps")
            }

            if (placeLabel == null) {
                Text("TRIGGER", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocationTrigger.entries.forEach { option ->
                        val label = when (option) {
                            LocationTrigger.ARRIVING -> "Arriving"
                            LocationTrigger.LEAVING -> "Leaving"
                            LocationTrigger.EITHER -> "Either"
                        }
                        if (trigger == option) {
                            FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) { Text(label, fontSize = 11.sp) }
                        } else {
                            OutlinedButton(onClick = { trigger = option }, modifier = Modifier.weight(1f)) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = offset,
                    onValueChange = { offset = it.filter(Char::isDigit).take(4) },
                    label = { Text("Offset minutes") },
                    supportingText = { Text("Alert still fires if you leave during the offset.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("GEOFENCE RADIUS", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(50f, 150f, 300f, 500f).forEach { option ->
                        if (radius == option) {
                            FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("${option.toInt()}m", fontSize = 10.sp) }
                        } else {
                            OutlinedButton(onClick = { radius = option }, modifier = Modifier.weight(1f)) {
                                Text("${option.toInt()}m", fontSize = 10.sp)
                            }
                        }
                    }
                }
                if (!fineGranted) {
                    PermissionCard("Precise location is required to create a geofence.", onRequestFine)
                } else if (!backgroundGranted) {
                    PermissionCard("Allow location access “all the time” so reminders work when the app is closed.", onRequestBackground)
                }
            } else {
                Text(
                    "This address will be saved as $placeLabel. Reminder options can be chosen later when you create a location reminder.",
                    color = TextDim,
                    fontSize = 12.sp,
                )
            }
            error?.let { Text(it, color = Danger, fontSize = 12.sp) }
            Button(
                onClick = { onSave(trigger, offset.toIntOrNull() ?: 0, radius) },
                enabled = (placeLabel != null || fineGranted && backgroundGranted) && !saving,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    if (saving) "  Saving…"
                    else if (placeLabel != null) "Save as $placeLabel"
                    else "Use This Location",
                    fontWeight = FontWeight.Bold,
                )
            }
            }
        }
        if (mapFullscreen) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                OSMMap(candidate, radius)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color(0xCC10131D), RoundedCornerShape(50)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel", color = TextMuted) }
                    IconButton(onClick = { mapFullscreen = false }) {
                        Icon(Icons.Default.FullscreenExit, "Exit full screen")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(text: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = .1f)),
        border = BorderStroke(1.dp, Accent.copy(alpha = .35f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(text, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClick) { Text("Allow location") }
        }
    }
}

@Composable
private fun OSMMap(candidate: PlaceCandidate, radiusMeters: Float) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            setOnTouchListener { view, event ->
                view.parent?.requestDisallowInterceptTouchEvent(
                    event.actionMasked != MotionEvent.ACTION_UP &&
                        event.actionMasked != MotionEvent.ACTION_CANCEL,
                )
                false
            }
        }
    }
    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.getMapAsync { map ->
                    fun render() {
                        @Suppress("DEPRECATION")
                        map.clear()
                        val center = LatLng(candidate.latitude, candidate.longitude)
                        @Suppress("DEPRECATION")
                        map.addMarker(MarkerOptions().position(center).title(candidate.name))
                        @Suppress("DEPRECATION")
                        map.addPolygon(
                            PolygonOptions()
                                .addAll(radiusPolygon(candidate.latitude, candidate.longitude, radiusMeters))
                                .fillColor(android.graphics.Color.argb(35, 123, 110, 246))
                                .strokeColor(android.graphics.Color.rgb(123, 110, 246)),
                        )
                        map.cameraPosition = CameraPosition.Builder()
                            .target(center)
                            .zoom(14.0)
                            .build()
                    }
                    if (map.style == null) {
                        map.setStyle(Style.Builder().fromJson(OSM_STYLE)) { render() }
                    } else {
                        render()
                    }
                }
            },
        )
        Text(
            "© OpenStreetMap contributors",
            color = Color.White,
            fontSize = 9.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color(0x99000000))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

private fun radiusPolygon(latitude: Double, longitude: Double, radiusMeters: Float): List<LatLng> {
    val earth = 6_378_137.0
    val latRadians = Math.toRadians(latitude)
    return (0..64).map { index ->
        val angle = 2.0 * Math.PI * index / 64.0
        val dLat = radiusMeters / earth * 180.0 / Math.PI
        val dLon = radiusMeters / (earth * cos(latRadians)) * 180.0 / Math.PI
        LatLng(latitude + dLat * sin(angle), longitude + dLon * cos(angle))
    }
}

private const val OSM_STYLE = """
{
  "version": 8,
  "name": "RemindMe OSM",
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [{"id": "osm", "type": "raster", "source": "osm"}]
}
"""

@Composable
private fun LocationHeader(title: String, onBack: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("Cancel", color = TextDim) }
    }
}

@Composable
private fun PlaceRow(name: String, address: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Glass),
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(Accent2.copy(alpha = .12f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.LocationOn, null, tint = Accent2) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(address, color = TextDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun PlaceOnboardingScreen(
    onSetPlace: (String) -> Unit,
    onComplete: (workSkipped: Boolean) -> Unit,
    viewModel: LocationViewModel = hiltViewModel(),
) {
    val places by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val home = places.firstOrNull { it.label.equals("Home", true) }
    val work = places.firstOrNull { it.label.equals("Work", true) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Set up your places", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "RemindMe uses Home and Work to understand requests without searching for a new location.",
            color = TextMuted,
        )
        PlaceSetupCard("Home", home?.address, onSetPlace)
        PlaceSetupCard("Work", work?.address, onSetPlace)
        Button(
            onClick = { onComplete(false) },
            enabled = home != null && work != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Finish setup") }
        TextButton(
            onClick = { onComplete(true) },
            enabled = home != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("I work from home") }
        TextButton(
            onClick = { onComplete(true) },
            enabled = home != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("I don't have a work location") }
        if (home == null) {
            Text("Add Home before finishing setup.", color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlaceSetupCard(label: String, address: String?, onSetPlace: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Glass),
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold)
                Text(address ?: "Not set", color = TextDim, fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onSetPlace(label) }) {
                Text(if (address == null) "Set" else "Change")
            }
        }
    }
}

@Composable
fun PlacesScreen(
    onAddPlace: (String) -> Unit,
    viewModel: LocationViewModel = hiltViewModel(),
) {
    val places by viewModel.savedPlaces.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SavedPlaceEntity?>(null) }
    var editLabel by remember { mutableStateOf("") }
    var editRadius by remember { mutableStateOf(150f) }
    var adding by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var blockedMessage by remember { mutableStateOf<String?>(null) }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add saved place") },
            text = {
                OutlinedTextField(
                    newLabel,
                    { newLabel = it },
                    label = { Text("Name, such as Church or Gym") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newLabel.isNotBlank(),
                    onClick = {
                        adding = false
                        onAddPlace(newLabel.trim())
                    },
                ) { Text("Choose location") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    editing?.let { place ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit saved place") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(editLabel, { editLabel = it }, label = { Text("Label") })
                    Text("Geofence radius", color = TextDim, fontSize = 12.sp)
                    listOf(50f, 150f, 300f, 500f).chunked(2).forEach { radii ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            radii.forEach { radius ->
                                if (editRadius == radius) {
                                    FilledTonalButton(
                                        onClick = { editRadius = radius },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("${radius.toInt()}m") }
                                } else {
                                    OutlinedButton(
                                        onClick = { editRadius = radius },
                                        modifier = Modifier.weight(1f),
                                    ) { Text("${radius.toInt()}m") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updatePlace(
                        place.copy(
                            label = editLabel.trim().ifBlank { place.label },
                            geofenceRadius = editRadius,
                        ),
                    )
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(place) { count ->
                        blockedMessage = "$count active reminder(s) use this place. Complete or delete them first."
                    }
                    editing = null
                }) { Text("Delete", color = Danger) }
            },
        )
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Saved Places", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Locations that trigger your reminders", color = TextDim, fontSize = 12.sp)
            Button(
                onClick = {
                    newLabel = ""
                    adding = true
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Add saved place") }
        }
        if (places.isEmpty()) {
            item {
                Text(
                    "Places are saved when you confirm your first location reminder.",
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 28.dp),
                )
            }
        }
        items(places, key = { it.id }) { place ->
            PlaceRow(place.label, place.address) {
                editLabel = place.label
                editRadius = place.geofenceRadius
                editing = place
            }
        }
        blockedMessage?.let { message ->
            item { Text(message, color = Danger, fontSize = 12.sp) }
        }
    }
}

private fun formatDistance(meters: Float): String =
    if (meters < 1_609) "${meters.toInt()} m" else "%.1f mi".format(meters / 1_609.344f)
