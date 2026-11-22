package com.example.ui.screens

import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.model.AppLanguage
import com.example.model.SampleProduce
import com.example.model.SampleProduceCatalog
import com.example.model.SpatialScaleHint
import com.example.model.UserPreferences
import com.example.ui.components.BotanicalScanningOverlay
import com.example.ui.i18n.localizePreferenceOption
import com.example.ui.i18n.stringsFor
import com.example.ui.viewmodel.ScanUiState

private const val TAG = "ScannerScreen"

@Composable
fun LanguageTogglePill(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppLanguage.entries.forEach { lang ->
                val isSelected = lang == selected
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(lang) }
                        .testTag("home_lang_${lang.code}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = lang.shortLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    scanState: ScanUiState,
    preferences: UserPreferences,
    hasHardwareDepthSensor: Boolean,
    recentCount: Int,
    hasCameraPermission: Boolean = false,
    cameraPermissionDenied: Boolean = false,
    cameraPermissionPermanentlyDenied: Boolean = false,
    onRequestCameraPermission: () -> Unit = {},
    onDismissCameraDeniedNotice: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onCaptureImageProxy: (ImageProxy) -> Unit,
    onPickMultipleImages: (List<android.net.Uri>) -> Unit,
    onAnalyzeSample: (SampleProduce) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSetSpatialMode: (Boolean) -> Unit,
    onSetSpatialScaleHint: (SpatialScaleHint) -> Unit,
    onOpenPantry: () -> Unit,
    onOpenPreferences: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = stringsFor(preferences.language)

    var cameraWarmingUpNotice by remember { mutableStateOf(false) }
    var cameraAvailable by remember { mutableStateOf(true) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val multiPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onPickMultipleImages(uris)
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = strings.scannerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LanguageTogglePill(
                        selected = preferences.language,
                        onSelect = onSelectLanguage
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onOpenPreferences() }
                            .minimumInteractiveComponentSize()
                            .testTag("scanner_profile_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = strings.personalizeContentDesc,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            val activeFocus = preferences.nutrientNeeds.firstOrNull()
                                ?: preferences.healthFocuses.firstOrNull()
                                ?: strings.navForYou
                            Text(
                                text = localizePreferenceOption(activeFocus, preferences.language),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 92.dp)
                            )
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (preferences.spatialModeEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = if (preferences.spatialModeEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape
                        )
                        .clickable { onSetSpatialMode(!preferences.spatialModeEnabled) }
                        .testTag("spatial_mode_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ViewInAr,
                            contentDescription = null,
                            tint = if (preferences.spatialModeEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (preferences.spatialModeEnabled) strings.spatial3dMode else strings.standard2dMode,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                            color = if (preferences.spatialModeEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }

                if (preferences.spatialModeEnabled) {
                    val scaleLabel = if (preferences.language == AppLanguage.MONGOLIAN) {
                        preferences.spatialScaleHint.labelMn
                    } else {
                        preferences.spatialScaleHint.labelEn
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable {
                                val entries = SpatialScaleHint.entries
                                val next = entries[(preferences.spatialScaleHint.ordinal + 1) % entries.size]
                                onSetSpatialScaleHint(next)
                            }
                            .testTag("spatial_scale_hint_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Straighten,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = scaleLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Text(
                    text = if (hasHardwareDepthSensor) strings.hardwareDepthActive else strings.aiSpatialDepth,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            AnimatedVisibility(visible = cameraPermissionDenied && !hasCameraPermission) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.cameraDeniedNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (cameraPermissionPermanentlyDenied) {
                            TextButton(
                                onClick = onOpenAppSettings,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(strings.openSettingsLabel)
                            }
                        }
                        IconButton(
                            onClick = onDismissCameraDeniedNotice,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = strings.dismissContentDesc,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = cameraWarmingUpNotice) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.cameraWarmingUpNotice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { cameraWarmingUpNotice = false },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = strings.dismissContentDesc,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = scanState is ScanUiState.Error) {
                val msg = (scanState as? ScanUiState.Error)?.message.orEmpty()
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = strings.dismissContentDesc,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(324.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1A261F))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                when {
                    scanState is ScanUiState.Analyzing -> {
                        BotanicalScanningOverlay(
                            label = scanState.label,
                            completedCount = scanState.completedCount,
                            totalCount = scanState.totalCount,
                            previewBitmap = scanState.previewBitmap
                        )
                    }
                    hasCameraPermission && cameraAvailable -> {
                        CameraPreviewStage(
                            spatialModeEnabled = preferences.spatialModeEnabled,
                            onImageCaptureReady = {
                                imageCapture = it
                                cameraWarmingUpNotice = false
                            },
                            onCameraUnavailable = {
                                imageCapture = null
                                cameraAvailable = false
                            }
                        )
                    }
                    else -> {
                        CameraPromptHeroStage(
                            cameraUnavailable = !cameraAvailable,
                            heroText = if (cameraAvailable) strings.cameraHeroHint else strings.cameraUnavailableHint,
                            enableCameraText = if (cameraPermissionPermanentlyDenied) strings.openSettingsLabel else strings.enableCamera,
                            onRequestCamera = {
                                if (cameraPermissionPermanentlyDenied) {
                                    onOpenAppSettings()
                                } else {
                                    onRequestCameraPermission()
                                }
                            }
                        )
                    }
                }

                if (scanState !is ScanUiState.Analyzing && preferences.lightingCalibrationEnabled) {
                    Surface(
                        color = Color(0xFF19241D).copy(alpha = 0.72f),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WbSunny,
                                contentDescription = null,
                                tint = Color(0xFFFAD78A),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = strings.calibrationBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable(enabled = scanState !is ScanUiState.Analyzing) {
                                multiPhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                            .testTag("gallery_picker_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = strings.galleryMulti,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.galleryMulti,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val isAnalyzing = scanState is ScanUiState.Analyzing
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isAnalyzing) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            .clickable(enabled = !isAnalyzing) {
                                val activeCapture = imageCapture
                                if (hasCameraPermission && cameraAvailable) {
                                    if (activeCapture != null) {
                                        cameraWarmingUpNotice = false
                                        activeCapture.takePicture(
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageCapturedCallback() {
                                                override fun onCaptureSuccess(image: ImageProxy) {
                                                    onCaptureImageProxy(image)
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    Log.e(TAG, "Capture error", exception)
                                                    multiPhotoPickerLauncher.launch(
                                                        PickVisualMediaRequest(
                                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                    } else {
                                        cameraWarmingUpNotice = true
                                    }
                                } else if (!hasCameraPermission && cameraAvailable) {
                                    if (cameraPermissionPermanentlyDenied) {
                                        onOpenAppSettings()
                                    } else {
                                        onRequestCameraPermission()
                                    }
                                } else {
                                    multiPhotoPickerLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                            }
                            .testTag("capture_shutter_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = strings.capture,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { onOpenPantry() }
                            .testTag("recent_scans_shortcut")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = strings.navPantry,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (recentCount > 0) "$recentCount ${strings.savedLabel}" else strings.navPantry,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.trySamples,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(SampleProduceCatalog.samples, key = { it.key }) { sample ->
                        SampleProduceCard(
                            sample = sample,
                            language = preferences.language,
                            enabled = scanState !is ScanUiState.Analyzing,
                            onClick = { onAnalyzeSample(sample) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SampleProduceCard(
    sample: SampleProduce,
    language: AppLanguage,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val title = sample.localizedTitle(language)
    val subtitle = sample.localizedSubtitle(language)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("sample_produce_${sample.key}")
    ) {
        Column {
            Image(
                painter = painterResource(id = sample.drawableRes),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewStage(
    spatialModeEnabled: Boolean,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCameraUnavailable: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        var disposed = false
        var boundProvider: ProcessCameraProvider? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = cameraProviderFuture.get()
                boundProvider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                if (cameraProvider.hasCamera(selector)) {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    onImageCaptureReady(capture)
                } else {
                    onCameraUnavailable()
                }
            } catch (e: Exception) {
                Log.w(TAG, "CameraX preview unavailable on this device/emulator", e)
                onCameraUnavailable()
            }
        }, executor)

        onDispose {
            disposed = true
            boundProvider?.unbindAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .height(210.dp)
                .border(
                    width = 1.5.dp,
                    color = Color.White.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            if (spatialModeEnabled) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    val guideColor = Color(0xFF9BE6B9).copy(alpha = 0.65f)
                    drawLine(
                        color = guideColor,
                        start = Offset(0f, size.height * 0.5f),
                        end = Offset(size.width, size.height * 0.5f),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dash
                    )
                    drawLine(
                        color = guideColor,
                        start = Offset(size.width * 0.5f, 0f),
                        end = Offset(size.width * 0.5f, size.height),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dash
                    )
                    val corner = 18.dp.toPx()
                    drawLine(Color.White, Offset(12.dp.toPx(), 12.dp.toPx()), Offset(12.dp.toPx() + corner, 12.dp.toPx()), 2.dp.toPx())
                    drawLine(Color.White, Offset(12.dp.toPx(), 12.dp.toPx()), Offset(12.dp.toPx(), 12.dp.toPx() + corner), 2.dp.toPx())
                    drawLine(Color.White, Offset(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()), Offset(size.width - 12.dp.toPx() - corner, size.height - 12.dp.toPx()), 2.dp.toPx())
                    drawLine(Color.White, Offset(size.width - 12.dp.toPx(), size.height - 12.dp.toPx()), Offset(size.width - 12.dp.toPx(), size.height - 12.dp.toPx() - corner), 2.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun CameraPromptHeroStage(
    cameraUnavailable: Boolean,
    heroText: String,
    enableCameraText: String,
    onRequestCamera: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.img_sample_avocado_1795260014977),
            contentDescription = heroText,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color(0xFF111914).copy(alpha = 0.78f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = heroText,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White
            )
            if (!cameraUnavailable) {
                Button(
                    onClick = onRequestCamera,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF19241D)
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("enable_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = enableCameraText,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
