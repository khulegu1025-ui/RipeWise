package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai.configureAppCheck
import com.example.ui.i18n.stringsFor
import com.example.ui.screens.PantryHistoryScreen
import com.example.ui.screens.ProduceReportScreen
import com.example.ui.screens.ProfilePreferencesScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.RipeWiseTheme
import com.example.ui.viewmodel.RipeWiseViewModel
import com.example.ui.viewmodel.ScanUiState

const val ROUTE_SCANNER = "scanner"
const val ROUTE_PANTRY = "pantry"
const val ROUTE_PROFILE = "profile"

private const val PERMISSION_PREFS = "ripewise_permission_prefs"
private const val KEY_CAMERA_PROMPTED = "camera_prompted_once"

private data class RipeWiseDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val DESTINATIONS = listOf(
    RipeWiseDestination(
        route = ROUTE_SCANNER,
        selectedIcon = Icons.Rounded.CameraAlt,
        unselectedIcon = Icons.Outlined.CameraAlt
    ),
    RipeWiseDestination(
        route = ROUTE_PANTRY,
        selectedIcon = Icons.Rounded.Kitchen,
        unselectedIcon = Icons.Outlined.Kitchen
    ),
    RipeWiseDestination(
        route = ROUTE_PROFILE,
        selectedIcon = Icons.Rounded.Person,
        unselectedIcon = Icons.Outlined.Person
    )
)

class MainActivity : ComponentActivity() {

    private val hasCameraPermissionState = mutableStateOf(false)
    private val cameraPermissionDeniedState = mutableStateOf(false)
    private val cameraPermissionPermanentlyDeniedState = mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermissionState.value = granted
        cameraPermissionDeniedState.value = !granted
        cameraPermissionPermanentlyDeniedState.value =
            !granted && !ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent BEFORE rendering Compose UI or ViewModels
        configureAppCheck(this, intent)

        refreshCameraPermissionState()
        if (!hasCameraPermissionState.value) {
            val prefs = getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_CAMERA_PROMPTED, false)) {
                prefs.edit { putBoolean(KEY_CAMERA_PROMPTED, true) }
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        enableEdgeToEdge()
        setContent {
            RipeWiseTheme {
                RipeWiseAppRoot(
                    hasCameraPermission = hasCameraPermissionState.value,
                    cameraPermissionDenied = cameraPermissionDeniedState.value,
                    cameraPermissionPermanentlyDenied = cameraPermissionPermanentlyDeniedState.value,
                    onRequestCameraPermission = { requestCameraPermission() },
                    onDismissCameraDeniedNotice = { cameraPermissionDeniedState.value = false },
                    onOpenAppSettings = { openAppSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCameraPermissionState()
    }

    private fun refreshCameraPermissionState() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        hasCameraPermissionState.value = granted
        if (granted) {
            cameraPermissionDeniedState.value = false
            cameraPermissionPermanentlyDeniedState.value = false
        }
    }

    private fun requestCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            refreshCameraPermissionState()
            return
        }
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        runCatching { startActivity(intent) }
    }
}

@Composable
fun RipeWiseAppRoot(
    viewModel: RipeWiseViewModel = viewModel(),
    hasCameraPermission: Boolean = false,
    cameraPermissionDenied: Boolean = false,
    cameraPermissionPermanentlyDenied: Boolean = false,
    onRequestCameraPermission: () -> Unit = {},
    onDismissCameraDeniedNotice: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_SCANNER

    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val recentScans by viewModel.recentScans.collectAsStateWithLifecycle()
    val storageStats by viewModel.scanStorageStats.collectAsStateWithLifecycle()

    val strings = stringsFor(preferences.language)
    val isShowingReport = currentRoute == ROUTE_SCANNER && scanState is ScanUiState.Success

    fun labelForRoute(route: String): String = when (route) {
        ROUTE_SCANNER -> strings.navScan
        ROUTE_PANTRY -> strings.navPantry
        else -> strings.navForYou
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 840.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!useRail && !isShowingReport) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = labelForRoute(dest.route)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (dest.route == ROUTE_SCANNER && currentRoute == ROUTE_SCANNER && scanState is ScanUiState.Success) {
                                        viewModel.resetToScanner()
                                    }
                                    navController.navigate(dest.route) {
                                        popUpTo(ROUTE_SCANNER) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isShowingReport) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DESTINATIONS.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val label = labelForRoute(dest.route)
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (dest.route == ROUTE_SCANNER && currentRoute == ROUTE_SCANNER && scanState is ScanUiState.Success) {
                                        viewModel.resetToScanner()
                                    }
                                    navController.navigate(dest.route) {
                                        popUpTo(ROUTE_SCANNER) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_SCANNER,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_SCANNER) {
                        when (val state = scanState) {
                            is ScanUiState.Success -> {
                                ProduceReportScreen(
                                    results = state.results,
                                    selectedIndex = state.selectedIndex,
                                    totalAttempted = state.totalAttempted,
                                    isTranslating = state.isTranslating,
                                    translationWarning = state.translationWarning,
                                    preferences = preferences,
                                    chatState = chatState,
                                    onSelectBatchIndex = { idx -> viewModel.selectBatchIndex(idx) },
                                    onSelectLanguage = { lang -> viewModel.setLanguage(lang) },
                                    onRefreshForProfile = { viewModel.reanalyzeActiveScan() },
                                    onDismissTranslationWarning = { viewModel.dismissTranslationWarning() },
                                    onBackToScanner = { viewModel.resetToScanner() },
                                    onOpenChat = { viewModel.setChatSheetOpen(true) },
                                    onCloseChat = { viewModel.setChatSheetOpen(false) },
                                    onSendChatMessage = { msg -> viewModel.sendChatMessage(msg) },
                                    onDismissChatError = { viewModel.clearChatError() }
                                )
                            }
                            else -> {
                                ScannerScreen(
                                    scanState = state,
                                    preferences = preferences,
                                    hasHardwareDepthSensor = viewModel.depthCapability.hasHardwareDepthSensor,
                                    recentCount = recentScans.size,
                                    hasCameraPermission = hasCameraPermission,
                                    cameraPermissionDenied = cameraPermissionDenied,
                                    cameraPermissionPermanentlyDenied = cameraPermissionPermanentlyDenied,
                                    onRequestCameraPermission = onRequestCameraPermission,
                                    onDismissCameraDeniedNotice = onDismissCameraDeniedNotice,
                                    onOpenAppSettings = onOpenAppSettings,
                                    onCaptureImageProxy = { proxy -> viewModel.analyzeCameraCapture(proxy) },
                                    onPickMultipleImages = { uris -> viewModel.analyzePhotoUris(uris) },
                                    onAnalyzeSample = { sample -> viewModel.analyzeSampleProduce(sample) },
                                    onSelectLanguage = { lang -> viewModel.setLanguage(lang) },
                                    onSetSpatialMode = { enabled -> viewModel.setSpatialMode(enabled) },
                                    onSetSpatialScaleHint = { hint -> viewModel.setSpatialScaleHint(hint) },
                                    onOpenPantry = {
                                        navController.navigate(ROUTE_PANTRY) {
                                            popUpTo(ROUTE_SCANNER) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onOpenPreferences = {
                                        navController.navigate(ROUTE_PROFILE) {
                                            popUpTo(ROUTE_SCANNER) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    onDismissError = { viewModel.dismissError() }
                                )
                            }
                        }
                    }

                    composable(ROUTE_PANTRY) {
                        PantryHistoryScreen(
                            scans = recentScans,
                            storageStats = storageStats,
                            language = preferences.language,
                            onSelectLanguage = { lang -> viewModel.setLanguage(lang) },
                            onSelectScan = { item ->
                                viewModel.openScanReport(item)
                                navController.navigate(ROUTE_SCANNER) {
                                    popUpTo(ROUTE_SCANNER) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onDeleteScan = { id -> viewModel.deleteScan(id) },
                            onClearAllScans = { viewModel.clearAllHistory() },
                            onNavigateToScanner = {
                                viewModel.resetToScanner()
                                navController.navigate(ROUTE_SCANNER) {
                                    popUpTo(ROUTE_SCANNER) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    composable(ROUTE_PROFILE) {
                        ProfilePreferencesScreen(
                            preferences = preferences,
                            hasHardwareDepthSensor = viewModel.depthCapability.hasHardwareDepthSensor,
                            onSelectLanguage = { viewModel.setLanguage(it) },
                            onToggleNutrientNeed = { viewModel.toggleNutrientNeed(it) },
                            onToggleDietaryGoal = { viewModel.toggleDietaryGoal(it) },
                            onToggleHealthFocus = { viewModel.toggleHealthFocus(it) },
                            onToggleCookingTool = { viewModel.toggleCookingTool(it) },
                            onSetLightingCalibration = { viewModel.setLightingCalibration(it) },
                            onSetSpatialMode = { viewModel.setSpatialMode(it) },
                            onSetSpatialScaleHint = { viewModel.setSpatialScaleHint(it) }
                        )
                    }
                }
            }
        }
    }
}
