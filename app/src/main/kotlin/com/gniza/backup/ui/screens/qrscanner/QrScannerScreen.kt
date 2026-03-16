package com.gniza.backup.ui.screens.qrscanner

import android.Manifest
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.gniza.backup.data.repository.ServerRepository
import com.gniza.backup.domain.model.AuthMethod
import com.gniza.backup.domain.model.Server
import com.gniza.backup.service.ssh.SshKeyManager
import com.gniza.backup.ui.components.GnizaTopAppBar
import com.gniza.backup.util.Constants
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class QrScannerViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val sshKeyManager: SshKeyManager,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    sealed class QrState {
        object Scanning : QrState()
        object Processing : QrState()
        data class Done(val serverId: Long) : QrState()
        data class Error(val message: String) : QrState()
    }

    private val _state = MutableStateFlow<QrState>(QrState.Scanning)
    val state: StateFlow<QrState> = _state.asStateFlow()

    fun processQrCode(json: String) {
        if (_state.value != QrState.Scanning) return
        _state.value = QrState.Processing

        viewModelScope.launch {
            try {
                val obj = org.json.JSONObject(json)
                if (!obj.has("gniza")) {
                    _state.value = QrState.Error("Invalid QR code")
                    return@launch
                }

                var privateKeyPath: String? = null

                // Handle embedded key
                if (obj.has("key")) {
                    val compressed = android.util.Base64.decode(obj.getString("key"), android.util.Base64.DEFAULT)
                    val keyBytes = java.util.zip.GZIPInputStream(compressed.inputStream()).readBytes()
                    val keyName = "qr_${System.currentTimeMillis()}"
                    sshKeyManager.importKey(keyName, keyBytes)
                    privateKeyPath = sshKeyManager.getPrivateKeyPath(keyName)
                }

                // Handle wormhole key transfer
                if (obj.has("wormhole")) {
                    val wormholeCode = obj.getString("wormhole")
                    privateKeyPath = receiveWormholeKey(wormholeCode)
                }

                val server = Server(
                    name = obj.optString("name", obj.optString("host", "Server")),
                    host = obj.optString("host", ""),
                    port = obj.optInt("port", 22),
                    username = obj.optString("user", ""),
                    authMethod = if (obj.optString("auth") == "password") AuthMethod.PASSWORD else AuthMethod.SSH_KEY,
                    password = if (obj.has("pass")) obj.optString("pass", "") else null,
                    privateKeyPath = privateKeyPath
                )
                val serverId = serverRepository.saveServer(server)

                // Save destination path for schedule creation
                val path = obj.optString("path", "")
                if (path.isNotBlank()) {
                    val pathFile = File(context.filesDir, "qr_destination_path")
                    pathFile.writeText(path)
                }

                _state.value = QrState.Done(serverId)
            } catch (e: Exception) {
                _state.value = QrState.Error(e.message ?: "Failed to process QR code")
            }
        }
    }

    private suspend fun receiveWormholeKey(wormholeCode: String): String? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val wormholeBinary = File(nativeLibDir, Constants.BUNDLED_WORMHOLE_LIB)
        if (!wormholeBinary.exists() || !wormholeBinary.canExecute()) return null

        return withContext(Dispatchers.IO) {
            val receiveDir = File(context.filesDir, "wormhole_receive")
            receiveDir.listFiles()?.forEach { it.delete() }
            receiveDir.mkdirs()

            val pb = ProcessBuilder(
                wormholeBinary.absolutePath, "recv", "--hide-progress", "-o", receiveDir.absolutePath, wormholeCode
            )
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.redirectErrorStream(true)
            pb.directory(receiveDir)
            val process = pb.start()

            process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(60, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()

            val receivedFile = receiveDir.listFiles()?.firstOrNull { it.isFile && it.length() > 0 }
            if (receivedFile != null) {
                val keyBytes = receivedFile.readBytes()
                val keyName = "wh_${System.currentTimeMillis()}"
                sshKeyManager.importKey(keyName, keyBytes)
                val path = sshKeyManager.getPrivateKeyPath(keyName)
                receivedFile.delete()
                path
            } else {
                null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    navController: NavController,
    viewModel: QrScannerViewModel = hiltViewModel()
) {
    var hasCameraPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val qrState by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        val result = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Navigate when done
    LaunchedEffect(qrState) {
        if (qrState is QrScannerViewModel.QrState.Done) {
            val serverId = (qrState as QrScannerViewModel.QrState.Done).serverId
            navController.navigate("servers/${serverId}/edit") {
                popUpTo("qrscanner") { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            GnizaTopAppBar(
                title = "Scan QR Code",
                onNavigateBack = { navController.popBackStack() }
            )
        }
    ) { innerPadding ->
        when (qrState) {
            is QrScannerViewModel.QrState.Processing -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Receiving SSH key...")
                    }
                }
            }
            is QrScannerViewModel.QrState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (qrState as QrScannerViewModel.QrState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                if (!hasCameraPermission) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Camera permission is required to scan QR codes.")
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Grant Permission")
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                val executor = Executors.newSingleThreadExecutor()
                                val barcodeScanner = BarcodeScanning.getClient()

                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }

                                    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setTargetResolution(Size(1280, 720))
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(executor) { imageProxy ->
                                                val mediaImage = imageProxy.image
                                                if (mediaImage != null && qrState is QrScannerViewModel.QrState.Scanning) {
                                                    val inputImage = InputImage.fromMediaImage(
                                                        mediaImage,
                                                        imageProxy.imageInfo.rotationDegrees
                                                    )
                                                    barcodeScanner.process(inputImage)
                                                        .addOnSuccessListener { barcodes ->
                                                            for (barcode in barcodes) {
                                                                if (barcode.valueType == Barcode.TYPE_TEXT) {
                                                                    val raw = barcode.rawValue ?: continue
                                                                    if (raw.contains("\"gniza\"")) {
                                                                        viewModel.processQrCode(raw)
                                                                        return@addOnSuccessListener
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        .addOnCompleteListener {
                                                            imageProxy.close()
                                                        }
                                                } else {
                                                    imageProxy.close()
                                                }
                                            }
                                        }

                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            }
                        )

                        Text(
                            text = "Point camera at the QR code",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(24.dp)
                        )
                    }
                }
            }
        }
    }
}
