package com.phoenix.remindme

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.phoenix.remindme.alerts.AlertPreferences
import com.phoenix.remindme.alerts.EXTRA_OPEN_ACTIVE_REMINDERS
import com.phoenix.remindme.data.BaseType
import com.phoenix.remindme.data.LocationTrigger
import com.phoenix.remindme.data.HistoryAction
import com.phoenix.remindme.data.ReminderEntity
import com.phoenix.remindme.data.Recurrence
import com.phoenix.remindme.data.ReminderStatus
import com.phoenix.remindme.ui.DashboardUiState
import com.phoenix.remindme.ui.DashboardViewModel
import com.phoenix.remindme.ui.EditReminderViewModel
import com.phoenix.remindme.ui.ApiKeySetupScreen
import com.phoenix.remindme.ui.VoiceFlowScreen
import com.phoenix.remindme.ui.LocationFlowScreen
import com.phoenix.remindme.ui.LocationViewModel
import com.phoenix.remindme.ui.PlaceOnboardingScreen
import com.phoenix.remindme.ui.PlacesScreen
import com.phoenix.remindme.ui.ReminderDetailViewModel
import com.phoenix.remindme.ai.ApiKeyRepository
import com.phoenix.remindme.ai.ReminderDraft
import com.phoenix.remindme.location.PlaceCandidate
import com.phoenix.remindme.location.LocationRepository
import com.phoenix.remindme.ui.theme.Accent
import com.phoenix.remindme.ui.theme.Accent2
import com.phoenix.remindme.ui.theme.Background
import com.phoenix.remindme.ui.theme.Glass
import com.phoenix.remindme.ui.theme.GlassBorder
import com.phoenix.remindme.ui.theme.RemindMeTheme
import com.phoenix.remindme.ui.theme.TextDim
import com.phoenix.remindme.ui.theme.TextMuted
import com.phoenix.remindme.ui.theme.Warning
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var apiKeys: ApiKeyRepository
    @Inject lateinit var locations: LocationRepository
    private var activeAlertNavigationRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_OPEN_ACTIVE_REMINDERS, false)) activeAlertNavigationRequest++
        setContent {
            RemindMeTheme {
                RemindMeApp(apiKeys, locations, activeAlertNavigationRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ACTIVE_REMINDERS, false)) activeAlertNavigationRequest++
    }
}

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object Today : Tab("dashboard", "Today", Icons.Default.Home)
    data object Upcoming : Tab("upcoming", "Upcoming", Icons.Default.Upcoming)
    data object Places : Tab("places", "Places", Icons.Default.Place)
    data object Settings : Tab("settings", "Settings", Icons.Default.Settings)
}

private val tabs = listOf(Tab.Today, Tab.Upcoming, Tab.Places, Tab.Settings)

private data class LocationSelection(
    val candidate: PlaceCandidate,
    val trigger: LocationTrigger,
    val offsetMinutes: Int,
    val radius: Float,
)

private fun placeDraft(label: String) = ReminderDraft(
    title = label,
    baseType = BaseType.LOCATION,
    triggerTime = null,
    locationName = null,
    locationOffsetMinutes = 0,
    isRecurring = false,
    recurrenceRule = null,
    alertFullScreen = true,
    alertSound = true,
    alertHeadsUp = true,
    alertLockScreen = true,
)

private fun nextFutureTime(originalMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(originalMillis).atZone(zone).toLocalTime()
    var next = java.time.LocalDate.now(zone).atTime(time).atZone(zone)
    if (!next.toInstant().isAfter(Instant.now())) next = next.plusDays(1)
    return next.toInstant().toEpochMilli()
}

@Composable
private fun RemindMeApp(
    apiKeys: ApiKeyRepository,
    locations: LocationRepository,
    activeAlertNavigationRequest: Int,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val dashboardState by dashboardViewModel.state.collectAsStateWithLifecycle()
    var pendingDraft by remember { mutableStateOf<ReminderDraft?>(null) }
    var pendingLocation by remember { mutableStateOf<LocationSelection?>(null) }
    var pendingPlaceLabel by remember { mutableStateOf<String?>(null) }
    var selectedReminderId by remember { mutableStateOf<Long?>(null) }
    val needsOnboarding = remember { apiKeys.shouldShowOnboarding() }
    val permissionOnboardingComplete = remember {
        context.getSharedPreferences("permission_onboarding", android.content.Context.MODE_PRIVATE)
            .getBoolean("complete", false)
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showNav = route in tabs.map { it.route }
    var initialActiveAlertsChecked by remember { mutableStateOf(false) }
    var handledActiveAlertRequest by remember { mutableIntStateOf(0) }

    fun abandonReminderCreation() {
        navController.popBackStack(Tab.Today.route, inclusive = false)
        pendingDraft = null
        pendingLocation = null
        pendingPlaceLabel = null
        if (navController.currentDestination?.route != Tab.Today.route) {
            navController.navigate(Tab.Today.route) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        when {
            needsOnboarding -> navController.navigate("api-key-setup") { launchSingleTop = true }
            locations.needsPlaceOnboarding() ->
                navController.navigate("place-onboarding") { launchSingleTop = true }
            !permissionOnboardingComplete ->
                navController.navigate("permission-setup") { launchSingleTop = true }
        }
    }
    LaunchedEffect(dashboardState.isLoaded, dashboardState.activeAlerts, activeAlertNavigationRequest) {
        val openedFromNotification = activeAlertNavigationRequest > handledActiveAlertRequest
        val shouldOpenForLaunch =
            dashboardState.isLoaded && !initialActiveAlertsChecked && dashboardState.activeAlerts.isNotEmpty()
        if (openedFromNotification || shouldOpenForLaunch) {
            navController.navigate("active-reminders") { launchSingleTop = true }
        }
        if (dashboardState.isLoaded) initialActiveAlertsChecked = true
        handledActiveAlertRequest = activeAlertNavigationRequest
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                if (showNav) BottomNavigation(navController)
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Tab.Today.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Tab.Today.route) {
                    DashboardScreen(
                        onAdd = {
                            navController.navigate(if (apiKeys.hasKey()) "voice-input" else "api-key-setup") {
                                launchSingleTop = true
                            }
                        },
                        onManual = {
                            pendingDraft = null
                            pendingLocation = null
                            pendingPlaceLabel = null
                            navController.navigate("reminder-edit") { launchSingleTop = true }
                        },
                        onOpen = { id ->
                            selectedReminderId = id
                            navController.navigate("reminder-detail")
                        },
                        onCreateAgain = { source ->
                            pendingPlaceLabel = null
                            pendingDraft = ReminderDraft(
                                title = source.title,
                                baseType = source.baseType,
                                triggerTime = source.triggerTime?.let(::nextFutureTime),
                                locationName = null,
                                locationOffsetMinutes = source.locationOffsetMinutes,
                                isRecurring = source.baseType == BaseType.TIME && source.isRecurring,
                                recurrenceRule = source.recurrenceRule,
                                alertFullScreen = source.alertFullScreen,
                                alertSound = source.alertSound,
                                alertHeadsUp = source.alertHeadsUp,
                                alertLockScreen = source.alertLockScreen,
                            )
                            pendingLocation = null
                            if (source.baseType == BaseType.LOCATION) {
                                scope.launch {
                                    val place = source.locationId?.let { locations.getPlace(it) }
                                    if (place != null) {
                                        pendingLocation = LocationSelection(
                                            PlaceCandidate(
                                                name = place.label,
                                                address = place.address,
                                                latitude = place.latitude,
                                                longitude = place.longitude,
                                                osmId = place.osmId,
                                                osmType = place.osmType,
                                                isSaved = true,
                                            ),
                                            source.locationTrigger ?: LocationTrigger.ARRIVING,
                                            source.locationOffsetMinutes,
                                            place.geofenceRadius,
                                        )
                                        navController.navigate("reminder-edit")
                                    } else {
                                        navController.navigate("location-picker")
                                    }
                                }
                            } else {
                                navController.navigate("reminder-edit")
                            }
                        },
                    )
                }
                composable(Tab.Upcoming.route) {
                    UpcomingScreen(onOpen = {
                        selectedReminderId = it
                        navController.navigate("reminder-detail")
                    })
                }
                composable(Tab.Places.route) {
                    PlacesScreen(
                        onAddPlace = { label ->
                            pendingPlaceLabel = label
                            pendingDraft = placeDraft(label)
                            navController.navigate("location-picker")
                        },
                    )
                }
                composable(Tab.Settings.route) {
                    SettingsScreen(onManageApiKey = { navController.navigate("api-key-setup") })
                }
                composable("active-reminders") {
                    ActiveRemindersScreen(onBack = { navController.popBackStack() })
                }
                composable("permission-setup") {
                    PermissionSetupScreen(
                        onComplete = {
                            context.getSharedPreferences(
                                "permission_onboarding",
                                android.content.Context.MODE_PRIVATE,
                            ).edit().putBoolean("complete", true).apply()
                            navController.navigate(Tab.Today.route) {
                                popUpTo("permission-setup") { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable("reminder-edit") {
                    EditReminderScreen(
                        initialDraft = pendingDraft,
                        locationSelection = pendingLocation,
                        onChooseLocation = { draft ->
                            pendingPlaceLabel = null
                            pendingDraft = draft
                            navController.navigate("location-picker") { launchSingleTop = true }
                        },
                        onClearLocation = { pendingLocation = null },
                        onSaved = {
                            pendingDraft = null
                            pendingLocation = null
                            navController.navigate(Tab.Today.route) {
                                popUpTo(Tab.Today.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onClose = {
                            abandonReminderCreation()
                        },
                    )
                }
                composable("reminder-detail") {
                    selectedReminderId?.let {
                        ReminderDetailScreen(it, onBack = { navController.popBackStack() })
                    } ?: LaunchedEffect(Unit) { navController.popBackStack() }
                }
                composable("api-key-setup") {
                    ApiKeySetupScreen(
                        onComplete = {
                            scope.launch {
                                val nextRoute = when {
                                    locations.needsPlaceOnboarding() -> "place-onboarding"
                                    context.getSharedPreferences(
                                        "permission_onboarding",
                                        android.content.Context.MODE_PRIVATE,
                                    ).getBoolean("complete", false) -> Tab.Today.route
                                    else -> "permission-setup"
                                }
                                navController.navigate(nextRoute) {
                                    popUpTo("api-key-setup") { inclusive = true }
                                }
                            }
                        },
                        onSkip = {
                            scope.launch {
                                val nextRoute = when {
                                    locations.needsPlaceOnboarding() -> "place-onboarding"
                                    context.getSharedPreferences(
                                        "permission_onboarding",
                                        android.content.Context.MODE_PRIVATE,
                                    ).getBoolean("complete", false) -> Tab.Today.route
                                    else -> "permission-setup"
                                }
                                navController.navigate(nextRoute) {
                                    popUpTo("api-key-setup") { inclusive = true }
                                }
                            }
                        },
                    )
                }
                composable("place-onboarding") {
                    PlaceOnboardingScreen(
                        onSetPlace = { label ->
                            pendingPlaceLabel = label
                            pendingDraft = placeDraft(label)
                            navController.navigate("location-picker")
                        },
                        onComplete = { workSkipped ->
                            locations.completePlaceOnboarding(workSkipped)
                            pendingPlaceLabel = null
                            pendingDraft = null
                            navController.navigate("permission-setup") {
                                popUpTo("place-onboarding") { inclusive = true }
                            }
                        },
                    )
                }
                composable("voice-input") {
                    VoiceFlowScreen(
                        onClose = ::abandonReminderCreation,
                        onEditDraft = { draft ->
                            pendingPlaceLabel = null
                            pendingDraft = draft
                            pendingLocation = null
                            if (draft.baseType == BaseType.LOCATION && !draft.locationName.isNullOrBlank()) {
                                scope.launch {
                                    val place = locations.findSavedPlace(draft.locationName)
                                    if (place != null) {
                                        pendingLocation = LocationSelection(
                                            candidate = PlaceCandidate(
                                                name = place.label,
                                                address = place.address,
                                                latitude = place.latitude,
                                                longitude = place.longitude,
                                                osmId = place.osmId,
                                                osmType = place.osmType,
                                                isSaved = true,
                                            ),
                                            trigger = LocationTrigger.ARRIVING,
                                            offsetMinutes = draft.locationOffsetMinutes,
                                            radius = place.geofenceRadius,
                                        )
                                        navController.navigate("reminder-edit")
                                    } else {
                                        navController.navigate("location-picker")
                                    }
                                }
                            } else {
                                navController.navigate("reminder-edit")
                            }
                        },
                    )
                }
                composable("location-picker") {
                    val locationViewModel: LocationViewModel = hiltViewModel()
                    pendingDraft?.let { draft ->
                        LocationFlowScreen(
                            draft = draft,
                            onClose = { navController.popBackStack() },
                            onCancel = {
                                if (pendingPlaceLabel != null) {
                                    navController.popBackStack()
                                    pendingPlaceLabel = null
                                    pendingDraft = null
                                } else {
                                    abandonReminderCreation()
                                }
                            },
                            onSaved = {
                                pendingDraft = null
                                navController.navigate(Tab.Today.route) {
                                    popUpTo(Tab.Today.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            placeLabel = pendingPlaceLabel,
                            onLocationConfirmed = { candidate, trigger, offset, radius ->
                                val placeLabel = pendingPlaceLabel
                                if (placeLabel != null) {
                                    locationViewModel.savePlace(candidate, radius, placeLabel) {
                                        pendingPlaceLabel = null
                                        pendingDraft = null
                                        navController.popBackStack()
                                    }
                                } else {
                                    pendingLocation = LocationSelection(candidate, trigger, offset, radius)
                                    navController.navigate("reminder-edit") {
                                        popUpTo("location-picker") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            viewModel = locationViewModel,
                        )
                    } ?: Box(Modifier.fillMaxSize())
                }
            }
        }
        if (showNav) {
            FloatingActionButton(
                onClick = {
                    navController.navigate(if (apiKeys.hasKey()) "voice-input" else "api-key-setup") {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 52.dp)
                    .size(58.dp),
                shape = CircleShape,
                containerColor = Accent,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Create reminder")
            }
        }
    }
}

@Composable
private fun BottomNavigation(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination
    NavigationBar(containerColor = Color(0xFF0A0C16)) {
        tabs.forEachIndexed { index, tab ->
            if (index == 2) Spacer(Modifier.width(64.dp))
            NavigationBarItem(
                selected = current?.hierarchy?.any { it.route == tab.route } == true,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, fontSize = 10.sp) },
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    onAdd: () -> Unit,
    onManual: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreateAgain: (ReminderEntity) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val displayName = AlertPreferences(context).displayName()
    var snoozing by remember { mutableStateOf<ReminderEntity?>(null) }

    snoozing?.let { reminder ->
        SnoozeSheet(
            reminder = reminder,
            onDismiss = { snoozing = null },
            onSnooze = { minutes ->
                viewModel.snooze(reminder.id, minutes)
                snoozing = null
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(Modifier.padding(20.dp)) {
                if (displayName.isBlank()) {
                    Text("Good evening", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Good evening,", color = TextDim, fontSize = 12.sp)
                    Text(displayName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    DateTimeFormatter.ofPattern("EEEE, MMMM d").format(java.time.LocalDate.now()),
                    color = TextDim,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(16.dp))
                VoicePill(onAdd)
                Text(
                    "Can't talk right now? Create manually",
                    color = Accent2,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(onClick = onManual)
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                )
                Spacer(Modifier.height(18.dp))
                StatsStrip(state)
            }
        }
        if ((state.overdue + state.today + state.upcoming).isEmpty()) {
            item { EmptyDashboard(onManual) }
        } else {
            reminderSection("Overdue", state.overdue, viewModel::complete, viewModel::remove, state.placeNames, onOpen = onOpen) { snoozing = it }
            reminderSection("Today", state.today, viewModel::complete, viewModel::remove, state.placeNames, onOpen = onOpen) { snoozing = it }
            reminderSection("Upcoming", state.upcoming, viewModel::complete, viewModel::remove, state.placeNames, onOpen = onOpen) { snoozing = it }
        }
        if (state.completed.isNotEmpty()) {
            reminderSection(
                "Completed",
                state.completed,
                viewModel::complete,
                viewModel::remove,
                state.placeNames,
                onRepeat = onCreateAgain,
                onOpen = onOpen,
            ) { snoozing = it }
        }
    }
}

@Composable
private fun VoicePill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Accent.copy(alpha = .10f), RoundedCornerShape(40.dp))
            .border(1.dp, Accent.copy(alpha = .28f), RoundedCornerShape(40.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Mic, null, Modifier.size(16.dp), tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text("What do you need to remember?", color = Accent, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("Tap to add", color = TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun StatsStrip(state: DashboardUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(state.dueTodayCount, "Due today", Accent, Modifier.weight(1f))
        StatCard(state.locationCount, "Location", Accent2, Modifier.weight(1f))
        StatCard(state.snoozedCount, "Snoozed", Warning, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Glass),
        border = BorderStroke(1.dp, GlassBorder),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$value", color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label.uppercase(), color = TextDim, fontSize = 9.sp)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reminderSection(
    title: String,
    reminders: List<ReminderEntity>,
    onComplete: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    placeNames: Map<Long, String>,
    onRepeat: ((ReminderEntity) -> Unit)? = null,
    onOpen: (Long) -> Unit = {},
    onSnooze: (ReminderEntity) -> Unit,
) {
    if (reminders.isEmpty()) return
    item {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            color = if (title == "Overdue") MaterialTheme.colorScheme.error else TextDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    items(reminders, key = { it.id }) { reminder ->
        ReminderCard(
            reminder = reminder,
            onComplete = onComplete,
            onRemove = onRemove,
            onRepeat = onRepeat,
            onOpen = onOpen,
            placeName = reminder.locationId?.let(placeNames::get),
            onSnooze = onSnooze,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ReminderCard(
    reminder: ReminderEntity,
    onComplete: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onRepeat: ((ReminderEntity) -> Unit)? = null,
    onOpen: (Long) -> Unit = {},
    placeName: String? = null,
    onSnooze: (ReminderEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (reminder.baseType == BaseType.TIME) Accent else Accent2
    Card(
        modifier = modifier.fillMaxWidth().clickable { onOpen(reminder.id) },
        colors = CardDefaults.cardColors(containerColor = Glass),
        border = BorderStroke(1.dp, GlassBorder),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        color = accent,
                        size = Size(4.dp.toPx(), size.height),
                    )
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = .15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (reminder.baseType == BaseType.TIME) Icons.Default.Schedule else Icons.Default.LocationOn,
                    null,
                    tint = accent,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(reminderMeta(reminder, placeName), color = accent, fontSize = 10.sp)
                if (reminder.baseType == BaseType.TIME &&
                    (reminder.status == ReminderStatus.ACTIVE || reminder.status == ReminderStatus.SNOOZED)
                ) {
                    val target = reminder.snoozedUntil ?: reminder.triggerTime
                    if (target != null) {
                        var now by remember(target) { mutableStateOf(System.currentTimeMillis()) }
                        LaunchedEffect(target) {
                            while (true) {
                                now = System.currentTimeMillis()
                                delay(1_000)
                            }
                        }
                        Text(
                            timeRemainingLabel(target, now, reminder.status == ReminderStatus.SNOOZED),
                            color = TextDim,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
            if (reminder.status == ReminderStatus.ACTIVE || reminder.status == ReminderStatus.SNOOZED) {
                Column {
                    IconButton(onClick = { onSnooze(reminder) }) {
                        Icon(Icons.Default.Snooze, contentDescription = "Snooze", tint = Warning)
                    }
                    IconButton(onClick = { onComplete(reminder.id) }) {
                        Icon(Icons.Default.Check, contentDescription = "Complete", tint = Accent2)
                    }
                    IconButton(onClick = { onRemove(reminder.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove reminder", tint = TextDim)
                    }
                }
            } else {
                Column {
                    onRepeat?.let { repeat ->
                        IconButton(onClick = { repeat(reminder) }) {
                            Icon(Icons.Default.Replay, contentDescription = "Create again", tint = Accent2)
                        }
                    }
                    IconButton(onClick = { onRemove(reminder.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove reminder", tint = TextDim)
                    }
                }
            }
        }
    }
}

private fun reminderMeta(reminder: ReminderEntity, placeName: String? = null): String = when (reminder.baseType) {
    BaseType.LOCATION -> placeName?.let { "At $it" } ?: "Location reminder"
    BaseType.TIME -> (reminder.snoozedUntil ?: reminder.triggerTime)?.let {
        val formatted = DateTimeFormatter.ofPattern("MMM d · h:mm a")
            .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        if (reminder.status == ReminderStatus.SNOOZED) "Snoozed until $formatted" else formatted
    } ?: "No time"
} + if (reminder.isRecurring) "  ·  Repeats" else ""

private fun timeRemainingLabel(target: Long, now: Long, isSnoozed: Boolean): String {
    val remainingSeconds = ((target - now).coerceAtLeast(0L) + 999L) / 1_000L
    val hours = remainingSeconds / 3_600
    val minutes = (remainingSeconds % 3_600) / 60
    val seconds = remainingSeconds % 60
    val duration = when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
    return if (isSnoozed) "Snoozed · $duration remaining" else "Due in $duration"
}

@Composable
private fun EmptyDashboard(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Notifications, null, Modifier.size(52.dp), tint = Accent)
        Spacer(Modifier.height(12.dp))
        Text("Nothing to remember yet", fontWeight = FontWeight.Bold)
        Text("Create your first time-based reminder.", color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("New reminder")
        }
    }
}

@Composable
private fun UpcomingScreen(
    onOpen: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var snoozing by remember { mutableStateOf<ReminderEntity?>(null) }
    snoozing?.let { reminder ->
        SnoozeSheet(
            reminder = reminder,
            onDismiss = { snoozing = null },
            onSnooze = { minutes ->
                viewModel.snooze(reminder.id, minutes)
                snoozing = null
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Upcoming",
            modifier = Modifier.padding(20.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyColumn {
            items(state.today + state.upcoming, key = { it.id }) {
                ReminderCard(
                    reminder = it,
                    onComplete = viewModel::complete,
                    onRemove = viewModel::remove,
                    onOpen = onOpen,
                    placeName = it.locationId?.let(state.placeNames::get),
                    onSnooze = { reminder -> snoozing = reminder },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun ActiveRemindersScreen(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var snoozing by remember { mutableStateOf<ReminderEntity?>(null) }
    snoozing?.let { reminder ->
        SnoozeSheet(
            reminder = reminder,
            onDismiss = { snoozing = null },
            onSnooze = { minutes ->
                viewModel.snooze(reminder.id, minutes)
                snoozing = null
            },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("‹", fontSize = 32.sp) }
                Column {
                    Text("Active reminders", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "These reminders are currently going off.",
                        color = TextDim,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (state.activeAlerts.isEmpty()) {
            item {
                Text(
                    "No active reminders",
                    color = TextDim,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        } else {
            items(state.activeAlerts, key = { it.id }) { reminder ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Glass),
                    border = BorderStroke(1.dp, Accent),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(reminder.title, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (reminder.baseType == BaseType.TIME) "Time reminder is going off"
                            else "Location reminder is going off",
                            color = Accent,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { snoozing = reminder },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Snooze, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Snooze")
                            }
                            Button(
                                onClick = { viewModel.dismiss(reminder.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderDetailScreen(
    reminderId: Long,
    onBack: () -> Unit,
    viewModel: ReminderDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(reminderId) { viewModel.load(reminderId) }
    val reminder by viewModel.reminder
    val history by viewModel.history
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("‹", fontSize = 32.sp) }
                Text("Reminder details", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        reminder?.let { reminderItem ->
            item {
                ReviewSection("REMINDER", reminderItem.title, reminderMeta(reminderItem))
            }
            if (reminderItem.baseType == BaseType.TIME) {
                item {
                    ReviewSection(
                        "REPEAT",
                        Recurrence.label(reminderItem.recurrenceRule),
                        if (reminderItem.isRecurring) "The next occurrence is shown above." else "One-time reminder",
                    )
                }
            }
        }
        item { Text("HISTORY", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        if (history.isEmpty()) {
            item { Text("No actions recorded yet.", color = TextMuted) }
        } else {
            items(history, key = { it.id }) { entry ->
                val action = when (entry.action) {
                    HistoryAction.COMPLETED -> "Completed"
                    HistoryAction.DISMISSED -> "Dismissed"
                    HistoryAction.SNOOZED -> "Snoozed ${entry.snoozeDurationMinutes ?: 0} minutes"
                }
                ReviewSection(
                    "ACTION",
                    action,
                    DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
                        .format(Instant.ofEpochMilli(entry.actionAt).atZone(ZoneId.systemDefault())),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnoozeSheet(
    reminder: ReminderEntity,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
) {
    var selected by remember(reminder.id) { mutableIntStateOf(30) }
    var custom by remember(reminder.id) { mutableStateOf("30") }
    val parsed = custom.toIntOrNull()?.coerceIn(1, 10_080)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121521),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Snooze reminder", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(reminder.title, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5 to "5 min", 30 to "30 min", 60 to "1 hr").forEach { (minutes, label) ->
                    val isSelected = selected == minutes && custom == minutes.toString()
                    if (isSelected) {
                        FilledTonalButton(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                        ) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selected = minutes
                                custom = minutes.toString()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(label) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("CUSTOM DURATION (MINUTES)", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = custom,
                onValueChange = {
                    custom = it.filter(Char::isDigit).take(5)
                    selected = -1
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { parsed?.let(onSnooze) },
                enabled = parsed != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Default.Snooze, null)
                Text("  Snooze for ${parsed ?: 0} minutes", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun PermissionSetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    val promptPreferences = remember {
        context.getSharedPreferences("permission_prompts", android.content.Context.MODE_PRIVATE)
    }
    var notificationAsked by remember {
        mutableStateOf(promptPreferences.getBoolean("notifications_asked", false))
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationAsked = true
        promptPreferences.edit().putBoolean("notifications_asked", true).apply()
        refresh++
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_EXPRESSION")
    refresh
    val notificationsGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    val exactGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    val fullScreenGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val batteryUnrestricted = isIgnoringBatteryOptimizations(context)

    LaunchedEffect(notificationsGranted, notificationAsked) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationsGranted &&
            !notificationAsked
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Allow reliable reminders", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Android controls whether RemindMe can alert you while the app is closed.",
                color = TextDim,
                fontSize = 12.sp,
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Glass),
                border = BorderStroke(1.dp, GlassBorder),
            ) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    PermissionRow(
                        title = "Notifications",
                        detail = "Required to display reminder alerts",
                        granted = notificationsGranted,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationAsked) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PermissionRow(
                            title = "Alarms & reminders",
                            detail = "Allows reminders to fire at the selected time",
                            granted = exactGranted,
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            },
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        PermissionRow(
                            title = "Full-screen alerts (optional)",
                            detail = "Shows alarm-style reminders over other apps",
                            granted = fullScreenGranted,
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            },
                        )
                    }
                    PermissionRow(
                        title = "Unrestricted battery",
                        detail = "Keeps alerts firing after the phone sleeps. On Samsung, choose Unrestricted.",
                        granted = batteryUnrestricted,
                        onClick = { openBatteryExemptionSettings(context) },
                    )
                }
            }
        }
        item {
            Button(
                onClick = onComplete,
                enabled = notificationsGranted,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(if (exactGranted) "Continue" else "Continue with inexact reminders")
            }
            if (!notificationsGranted) {
                Text(
                    "Notification permission is required before continuing.",
                    color = Warning,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else if (!batteryUnrestricted) {
                Text(
                    "You can continue, but Samsung and similar phones may delay alerts until battery is Unrestricted.",
                    color = Warning,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(onManageApiKey: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    val alertPreferences = remember(context) { AlertPreferences(context) }
    var alertDefaults by remember { mutableStateOf(alertPreferences.get()) }
    var defaultSnooze by remember { mutableIntStateOf(alertPreferences.snoozeMinutes()) }
    var defaultRadius by remember { mutableIntStateOf(alertPreferences.geofenceRadius().toInt()) }
    var autoSubmitSeconds by remember { mutableIntStateOf(alertPreferences.autoSubmitSeconds()) }
    var displayName by remember { mutableStateOf(alertPreferences.displayName()) }
    val permissionPrefs = remember {
        context.getSharedPreferences("permission_prompts", android.content.Context.MODE_PRIVATE)
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Reading refresh makes permission state recompute after returning from system settings.
    @Suppress("UNUSED_EXPRESSION")
    refresh
    val notificationsGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val exactGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    val fullScreenGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val preciseLocationGranted =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val backgroundLocationGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val batteryUnrestricted = isIgnoringBatteryOptimizations(context)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Permissions update when you return to the app.", color = TextDim, fontSize = 12.sp)
        }
        item {
            Text("YOU", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                    alertPreferences.saveDisplayName(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                placeholder = {
                    Text(
                        "Shown on the Today screen",
                        color = TextDim,
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                    )
                },
                singleLine = true,
            )
        }
        item {
            Text("AI", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageApiKey),
                colors = CardDefaults.cardColors(containerColor = Glass),
                border = BorderStroke(1.dp, GlassBorder),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Claude API Key", fontWeight = FontWeight.SemiBold)
                        Text("Tap to connect or replace your key", color = TextDim, fontSize = 11.sp)
                    }
                    Text("Manage", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text("PERMISSIONS", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            PermissionRow(
                title = "Notifications",
                detail = "Required to show reminder alerts",
                granted = notificationsGranted,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                        if (!permissionPrefs.getBoolean("notifications_asked", false)) {
                            permissionPrefs.edit().putBoolean("notifications_asked", true).apply()
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PermissionRow(
                    title = "Exact alarms",
                    detail = "Allows reminders to fire at the chosen minute",
                    granted = exactGranted,
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PermissionRow(
                    title = "Full-screen alerts",
                    detail = "Allows urgent reminders over the lock screen",
                    granted = fullScreenGranted,
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                )
            }
            PermissionRow(
                title = "Precise location",
                detail = "Required to detect geofence boundaries",
                granted = preciseLocationGranted,
                onClick = {
                    locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
            )
            PermissionRow(
                title = "Background location",
                detail = "Allows geofences while RemindMe is closed",
                granted = backgroundLocationGranted,
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )
            PermissionRow(
                title = "Unrestricted battery",
                detail = "Keeps alerts firing after the phone sleeps. On Samsung, choose Unrestricted.",
                granted = batteryUnrestricted,
                onClick = { openBatteryExemptionSettings(context) },
            )
        }
        item {
            Text("DEFAULTS", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Glass),
                border = BorderStroke(1.dp, GlassBorder),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Default snooze", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 15, 30, 60).forEach { minutes ->
                            DefaultChoiceButton(
                                label = "${minutes}m",
                                selected = defaultSnooze == minutes,
                                onClick = {
                                    defaultSnooze = minutes
                                    alertPreferences.saveSnoozeMinutes(minutes)
                                },
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = GlassBorder)
                    Text("Default geofence radius", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(50, 150, 300, 500).forEach { radius ->
                            DefaultChoiceButton(
                                label = "${radius}m",
                                selected = defaultRadius == radius,
                                onClick = {
                                    defaultRadius = radius
                                    alertPreferences.saveGeofenceRadius(radius.toFloat())
                                },
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = GlassBorder)
                    Text("Voice auto-submit", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 3, 5, 10).forEach { seconds ->
                            DefaultChoiceButton(
                                label = if (seconds == 0) "Off" else "${seconds}s",
                                selected = autoSubmitSeconds == seconds,
                                onClick = {
                                    autoSubmitSeconds = seconds
                                    alertPreferences.saveAutoSubmitSeconds(seconds)
                                },
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = GlassBorder)
                    Text("Default alert style", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Applied to new reminders. You can still change each reminder.",
                        color = TextDim,
                        fontSize = 11.sp,
                    )
                    SettingToggle(
                        "Full-screen",
                        "Opens an alarm-style screen over other apps when Android permits it.",
                        alertDefaults.fullScreen,
                    ) {
                        alertDefaults = alertDefaults.copy(fullScreen = it)
                        alertPreferences.save(alertDefaults)
                    }
                    SettingToggle(
                        "Sound",
                        "Plays the notification sound; turn off for a silent alert.",
                        alertDefaults.sound,
                    ) {
                        alertDefaults = alertDefaults.copy(sound = it)
                        alertPreferences.save(alertDefaults)
                    }
                    SettingToggle(
                        "Heads-up",
                        "Shows a temporary banner while you are using the phone.",
                        alertDefaults.headsUp,
                    ) {
                        alertDefaults = alertDefaults.copy(headsUp = it)
                        alertPreferences.save(alertDefaults)
                    }
                    SettingToggle(
                        "Lock screen",
                        "Shows reminder details when the device is locked.",
                        alertDefaults.lockScreen,
                    ) {
                        alertDefaults = alertDefaults.copy(lockScreen = it)
                        alertPreferences.save(alertDefaults)
                    }
                }
            }
        }
        item {
            Text(
                text = "RemindMe ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                modifier = Modifier.fillMaxWidth(),
                color = TextDim,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DefaultChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) { Text(label, fontSize = 11.sp) }
    } else {
        FilledTonalButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) { Text(label, fontSize = 11.sp) }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    return context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)
}

private fun openBatteryExemptionSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    if (!isIgnoringBatteryOptimizations(context)) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        if (request.resolveActivity(context.packageManager) != null) {
            context.startActivity(request)
            return
        }
    }
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    onClick: () -> Unit,
    statusLabel: String = if (granted) "Granted" else "Denied",
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, color = TextDim, fontSize = 11.sp)
        }
        Text(
            statusLabel,
            color = if (granted) com.phoenix.remindme.ui.theme.Success
            else if (!enabled) Warning else MaterialTheme.colorScheme.error,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    (if (granted) com.phoenix.remindme.ui.theme.Success
                    else if (!enabled) Warning else MaterialTheme.colorScheme.error).copy(alpha = .12f),
                    RoundedCornerShape(20.dp),
                )
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
    HorizontalDivider(color = GlassBorder)
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = TextDim)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditReminderScreen(
    initialDraft: ReminderDraft? = null,
    locationSelection: LocationSelection?,
    onChooseLocation: (ReminderDraft) -> Unit,
    onClearLocation: () -> Unit,
    onSaved: () -> Unit,
    onClose: () -> Unit,
    viewModel: EditReminderViewModel = hiltViewModel(),
    locationViewModel: LocationViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialDraft) {
        viewModel.loadDraft(initialDraft)
    }
    val state by viewModel.state
    val context = LocalContext.current
    var step by remember(initialDraft) {
        mutableStateOf(
            when {
                initialDraft == null || initialDraft.title.isBlank() -> 0
                initialDraft.baseType == BaseType.TIME && initialDraft.triggerTime != null -> 2
                else -> 1
            },
        )
    }
    val isLocation = locationSelection != null
    LaunchedEffect(locationSelection) {
        if (locationSelection != null) step = 2
    }
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            0 -> "New Reminder"
                            1 -> "When should I remind you?"
                            else -> "Review Reminder"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                step > 1 -> step = 1
                                step == 1 && initialDraft == null -> step = 0
                                else -> onClose()
                            }
                        },
                    ) { Text("‹", fontSize = 32.sp) }
                },
                actions = {
                    TextButton(onClick = onClose) {
                        Text("Cancel", color = TextDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (step == 0) item {
                Text("WHAT DO YOU NEED TO DO?", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { value -> viewModel.update { it.copy(title = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "What do you need to remember?",
                            color = TextDim,
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                        )
                    },
                    singleLine = true,
                )
                Button(
                    onClick = { step = if (isLocation) 2 else 1 },
                    enabled = state.title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Continue") }
            }
            if (step == 1) item {
                Text("TRIGGER TYPE", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isLocation) {
                        FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Schedule, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Time")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onClearLocation() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Schedule, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Time")
                        }
                    }
                    if (isLocation) {
                        FilledTonalButton(onClick = {}, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.LocationOn, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Location")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onChooseLocation(
                                    ReminderDraft(
                                        title = state.title.trim(),
                                        baseType = BaseType.LOCATION,
                                        triggerTime = null,
                                        locationName = null,
                                        locationOffsetMinutes = 0,
                                        isRecurring = state.isRecurring,
                                        recurrenceRule = state.recurrenceRule,
                                        alertFullScreen = state.alertFullScreen,
                                        alertSound = state.alertSound,
                                        alertHeadsUp = state.alertHeadsUp,
                                        alertLockScreen = state.alertLockScreen,
                                    ),
                                )
                            },
                            enabled = state.title.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.LocationOn, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Location")
                        }
                    }
                }
            }
            if (step == 1 && !isLocation) item {
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        calendar.set(year, month, day, hour, minute, 0)
                                        calendar.set(Calendar.MILLISECOND, 0)
                                        viewModel.update { it.copy(triggerTime = calendar.timeInMillis) }
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    false,
                                ).show()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH),
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.DateRange, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.triggerTime?.let {
                            DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")
                                .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
                        } ?: "Choose date and time",
                    )
                }
            }
            if (step == 1 && isLocation) item {
                Text("LOCATION", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(locationSelection.candidate.name, fontWeight = FontWeight.SemiBold)
                Text(locationSelection.candidate.address, color = TextDim, fontSize = 12.sp)
                Text(
                    "${locationSelection.trigger.name.lowercase().replaceFirstChar(Char::uppercase)} · " +
                        if (locationSelection.offsetMinutes == 0) "Immediately" else "${locationSelection.offsetMinutes} min after",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                OutlinedButton(
                    onClick = {
                        onChooseLocation(
                            ReminderDraft(
                                title = state.title.trim(),
                                baseType = BaseType.LOCATION,
                                triggerTime = null,
                                locationName = locationSelection.candidate.name,
                                locationOffsetMinutes = locationSelection.offsetMinutes,
                                isRecurring = state.isRecurring,
                                recurrenceRule = state.recurrenceRule,
                                alertFullScreen = state.alertFullScreen,
                                alertSound = state.alertSound,
                                alertHeadsUp = state.alertHeadsUp,
                                alertLockScreen = state.alertLockScreen,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Change location") }
            }
            if (step == 1 && !isLocation) item {
                SettingToggle(
                    "Repeat",
                    "Schedule this reminder again.",
                    checked = state.isRecurring,
                    onCheckedChange = { enabled ->
                        viewModel.update {
                            it.copy(
                                isRecurring = enabled,
                                recurrenceRule = if (enabled) it.recurrenceRule ?: "FREQ=DAILY" else null,
                            )
                        }
                    },
                )
                if (state.isRecurring) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Recurrence.presets.entries.chunked(3).forEach { choices ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                choices.forEach { (label, rule) ->
                                    val selected = state.recurrenceRule == rule ||
                                        (label == "Monthly" && state.recurrenceRule?.startsWith("FREQ=MONTHLY") == true)
                                    DefaultChoiceButton(
                                        label = label,
                                        selected = selected,
                                        onClick = {
                                            viewModel.update {
                                                val selectedRule = if (rule == "FREQ=MONTHLY") {
                                                    val day = it.triggerTime
                                                        ?.let { time ->
                                                            Instant.ofEpochMilli(time)
                                                                .atZone(ZoneId.systemDefault()).dayOfMonth
                                                        }
                                                        ?: 1
                                                    "FREQ=MONTHLY;BYMONTHDAY=$day"
                                                } else {
                                                    rule
                                                }
                                                it.copy(recurrenceRule = selectedRule)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Text("Repeat on days", color = TextDim, fontSize = 10.sp)
                    listOf(
                        listOf("M" to "MO", "T" to "TU", "W" to "WE", "T" to "TH"),
                        listOf("F" to "FR", "S" to "SA", "S" to "SU"),
                    ).forEach { days ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            days.forEach { (label, code) ->
                                DefaultChoiceButton(
                                    label = label,
                                    selected = recurrenceHasDay(state.recurrenceRule, code),
                                    onClick = {
                                        viewModel.update {
                                            it.copy(recurrenceRule = toggleRecurrenceDay(it.recurrenceRule, code))
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (state.recurrenceRule?.startsWith("FREQ=MONTHLY") == true) {
                        var monthlyDay by remember(state.triggerTime) {
                            mutableStateOf(
                                recurrenceValue(state.recurrenceRule, "BYMONTHDAY")
                                    ?: state.triggerTime?.let {
                                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).dayOfMonth.toString()
                                    }.orEmpty(),
                            )
                        }
                        OutlinedTextField(
                            value = monthlyDay,
                            onValueChange = { value ->
                                if (value.length <= 2 && value.all(Char::isDigit)) {
                                    monthlyDay = value
                                    value.toIntOrNull()?.takeIf { it in 1..31 }?.let { day ->
                                        viewModel.update {
                                            it.copy(recurrenceRule = "FREQ=MONTHLY;BYMONTHDAY=$day")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Day of month (1–31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }
            }
            if (step == 1) item {
                Button(
                    onClick = { step = 2 },
                    enabled = state.title.isNotBlank() && (isLocation || state.triggerTime != null),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Review reminder") }
            }
            if (step == 2) item {
                Text("Check everything before saving.", color = TextMuted, fontSize = 13.sp)
            }
            if (step == 2) item {
                ReviewSection(
                    label = "REMINDER",
                    title = state.title,
                    detail = "What you need to remember",
                    action = "Edit",
                    onAction = { step = 0 },
                )
            }
            if (step == 2 && !isLocation) item {
                ReviewSection(
                    label = "WHEN",
                    title = "At a time",
                    detail = state.triggerTime?.let {
                        DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a")
                            .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
                    }.orEmpty(),
                    action = "Change",
                    onAction = { step = 1 },
                )
            }
            if (step == 2 && isLocation && state.isRecurring) item {
                ReviewSection(
                    label = "REPEAT",
                    title = "Every visit",
                    detail = "The geofence re-arms after you dismiss or complete each alert.",
                )
            }
            if (step == 2 && isLocation) item {
                ReviewSection(
                    label = "WHEN",
                    title = when (locationSelection.trigger) {
                        LocationTrigger.ARRIVING -> "When I arrive"
                        LocationTrigger.LEAVING -> "When I leave"
                        LocationTrigger.EITHER -> "When I arrive or leave"
                    },
                    detail = buildString {
                        append(locationSelection.candidate.name)
                        append("\n")
                        append(locationSelection.candidate.address)
                        append("\n")
                        append(
                            if (locationSelection.offsetMinutes == 0) {
                                "Alert immediately"
                            } else {
                                "Alert ${locationSelection.offsetMinutes} minutes after"
                            },
                        )
                        append(" · ${locationSelection.radius.toInt()} m radius")
                    },
                    action = "Change",
                    onAction = { step = 1 },
                )
            }
            if (step == 2 && !isLocation) item {
                ReviewSection(
                    label = "REPEAT",
                    title = Recurrence.label(state.recurrenceRule),
                    detail = if (state.isRecurring) "Future occurrences are scheduled automatically." else "This reminder happens once.",
                )
            }
            if (step == 2) item {
                ReviewSection(
                    label = "ALERT",
                    title = listOfNotNull(
                        "Full-screen".takeIf { state.alertFullScreen },
                        "Sound".takeIf { state.alertSound },
                        "Heads-up".takeIf { state.alertHeadsUp },
                        "Lock screen".takeIf { state.alertLockScreen },
                    ).joinToString(" · ").ifBlank { "Silent notification" },
                    detail = "Uses your default alert style from Settings.",
                )
            }
            if (step == 2) item {
                Button(
                    onClick = {
                        if (isLocation) {
                            locationViewModel.saveReminder(
                                ReminderDraft(
                                    title = state.title.trim(),
                                    baseType = BaseType.LOCATION,
                                    triggerTime = null,
                                    locationName = locationSelection.candidate.name,
                                    locationOffsetMinutes = locationSelection.offsetMinutes,
                                    isRecurring = state.isRecurring,
                                    recurrenceRule = state.recurrenceRule,
                                    alertFullScreen = state.alertFullScreen,
                                    alertSound = state.alertSound,
                                    alertHeadsUp = state.alertHeadsUp,
                                    alertLockScreen = state.alertLockScreen,
                                ),
                                locationSelection.candidate,
                                locationSelection.trigger,
                                locationSelection.offsetMinutes,
                                locationSelection.radius,
                                onSaved,
                            )
                        } else {
                            viewModel.save(onSaved)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save reminder") }
            }
        }
    }
}

@Composable
private fun ReviewSection(
    label: String,
    title: String,
    detail: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Glass),
        border = BorderStroke(1.dp, GlassBorder),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, color = TextMuted, fontSize = 12.sp)
            }
            if (action != null && onAction != null) {
                Text(
                    action,
                    color = Accent2,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onAction).padding(8.dp),
                )
            }
        }
    }
}

private val recurrenceDayOrder = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

private fun recurrenceValue(rule: String?, key: String): String? =
    rule?.split(';')?.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')

private fun recurrenceHasDay(rule: String?, day: String): Boolean =
    recurrenceValue(rule, "BYDAY")?.split(',')?.contains(day) == true

private fun toggleRecurrenceDay(rule: String?, day: String): String {
    val selected = recurrenceValue(rule, "BYDAY")?.split(',')?.toMutableSet() ?: mutableSetOf()
    if (!selected.add(day)) selected.remove(day)
    if (selected.isEmpty()) selected.add(day)
    val ordered = recurrenceDayOrder.filter(selected::contains)
    return "FREQ=WEEKLY;BYDAY=${ordered.joinToString(",")}"
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, color = TextDim, fontSize = 11.sp) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    HorizontalDivider(color = GlassBorder)
}
