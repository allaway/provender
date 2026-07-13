package com.provender.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.provender.mlkit.BarcodeAnalyzer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Capture screen (SPEC §5.2): CameraX preview with a location chip row, photo/barcode mode
 * toggle, multi-shot thumbnails, gallery import, and Analyze. Camera plumbing lives here;
 * all state and persistence go through [CaptureViewModel].
 */
@Composable
fun CaptureScreen(
    onSnapshotCreated: (Long) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(state.navigateToSnapshotId) {
        state.navigateToSnapshotId?.let { id ->
            viewModel.onNavigationHandled()
            onSnapshotCreated(id)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> viewModel.onGalleryPicked(uris.map { it.toString() }) }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LocationChips(
                state = state,
                onLocationSelected = viewModel::onLocationSelected,
            )

            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.mode == CaptureMode.PHOTO,
                    onClick = { viewModel.onModeChanged(CaptureMode.PHOTO) },
                    label = { Text("Photos") },
                )
                FilterChip(
                    selected = state.mode == CaptureMode.BARCODE,
                    onClick = { viewModel.onModeChanged(CaptureMode.BARCODE) },
                    label = { Text("Barcode") },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (hasCameraPermission) {
                    CameraPane(
                        mode = state.mode,
                        onBarcode = viewModel::onBarcodeDetected,
                        onPhotoCaptured = viewModel::onPhotoCaptured,
                        newPhotoFile = viewModel::newPhotoFile,
                        photosDisabled = state.mode != CaptureMode.PHOTO,
                    )
                } else {
                    PermissionPane(onRequest = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    })
                }

                state.barcodeOverlay?.let { overlay ->
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (overlay.product != null) {
                                Text(
                                    overlay.product.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                overlay.product.brand?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = viewModel::onAddBarcodeItem) {
                                        Text("Add to inventory")
                                    }
                                    TextButton(onClick = viewModel::onDismissBarcode) {
                                        Text("Dismiss")
                                    }
                                }
                            } else {
                                Text(
                                    "Barcode ${overlay.barcode} isn't in the product " +
                                        "database. Add the item manually from Inventory.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = viewModel::onDismissBarcode) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }

                if (state.isLookingUpBarcode) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp),
                    )
                }
            }

            if (state.mode == CaptureMode.PHOTO) {
                PhotoTray(
                    state = state,
                    onRemovePhoto = viewModel::onRemovePhoto,
                    onPickFromGallery = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onAnalyze = viewModel::onAnalyze,
                )
            }
        }
    }
}

@Composable
private fun LocationChips(
    state: CaptureUiState,
    onLocationSelected: (Long) -> Unit,
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.locations, key = { it.id }) { location ->
            FilterChip(
                selected = state.selectedLocationId == location.id,
                onClick = { onLocationSelected(location.id) },
                label = { Text(location.name) },
            )
        }
    }
}

@Composable
private fun CameraPane(
    mode: CaptureMode,
    onBarcode: (String) -> Unit,
    onPhotoCaptured: (String) -> Unit,
    newPhotoFile: () -> File,
    photosDisabled: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cameraProvider = awaitCameraProvider(context)
    }

    LaunchedEffect(cameraProvider, mode) {
        val provider = cameraProvider ?: return@LaunchedEffect
        provider.unbindAll()
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        when (mode) {
            CaptureMode.PHOTO -> provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )

            CaptureMode.BARCODE -> {
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    BarcodeAnalyzer(onBarcode),
                )
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        if (!photosDisabled) {
            FilledIconButton(
                onClick = {
                    if (isCapturing) return@FilledIconButton
                    isCapturing = true
                    val target = newPhotoFile()
                    scope.launch {
                        runCatching { imageCapture.takePictureTo(context, target) }
                            .onSuccess { onPhotoCaptured(target.absolutePath) }
                        isCapturing = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .size(72.dp),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = "Take photo")
            }
        }
    }
}

@Composable
private fun PhotoTray(
    state: CaptureUiState,
    onRemovePhoto: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    onAnalyze: () -> Unit,
) {
    Column(modifier = Modifier.padding(12.dp)) {
        if (state.photoPaths.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.photoPaths, key = { it }) { path ->
                    Box {
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                        )
                        IconButton(
                            onClick = { onRemovePhoto(path) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp),
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove photo")
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onPickFromGallery, enabled = !state.isImportingPhotos) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Text(" Gallery")
            }
            Button(
                onClick = onAnalyze,
                enabled = state.photoPaths.isNotEmpty() && !state.isImportingPhotos,
                modifier = Modifier.weight(1f),
            ) {
                Text("Analyze ${state.photoPaths.size} photo(s)")
            }
        }
    }
}

@Composable
private fun PermissionPane(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Provender needs the camera to photograph your pantry. Photos stay on this device.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("Grant camera access")
        }
    }
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess(continuation::resume)
                    .onFailure(continuation::resumeWithException)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

private suspend fun ImageCapture.takePictureTo(context: Context, target: File): Unit =
    suspendCancellableCoroutine { continuation ->
        takePicture(
            ImageCapture.OutputFileOptions.Builder(target).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    continuation.resume(Unit)
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            },
        )
    }
