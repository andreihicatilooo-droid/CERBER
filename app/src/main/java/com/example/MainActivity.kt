package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.MyApplicationTheme
import com.example.ImageUtils
import com.example.GeoLocationHelper
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.ui.AppNavHost
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.data.SettingsManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val app = application as MainApplication
                val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(app.repository))
                val navController = rememberNavController()
                val context = LocalContext.current
                val settingsManager = remember { SettingsManager(context) }
                AppNavHost(navController = navController, viewModel = viewModel, settingsManager = settingsManager)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GeoCameraAppUI(
    onNavigateToAlbum: () -> Unit,
    onImageSaved: (String, Double?, Double?) -> Unit,
    settingsManager: SettingsManager
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    val locationHelper = remember { GeoLocationHelper(context) }
    var location by remember { mutableStateOf<Location?>(null) }
    var savedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    
    // CameraX elements
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            locationHelper.getLocationFlow().collect {
                location = it
            }
        }
    }

    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e("CameraX", "Binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        if (!permissionsState.allPermissionsGranted) {
            Box(
                contentAlignment = Alignment.Center, 
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Доступ к камере и геолокации",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Приложению необходим доступ к камере и геоданным для создания фото с водяным знаком.",
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { permissionsState.launchMultiplePermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Разрешить", fontFamily = FontFamily.SansSerif, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        } else {
            // Main Camera UI
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Background Camera Preview
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Top Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = innerPadding.calculateTopPadding() + 16.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Локация",
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "GeoCam",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            color = Color.White
                        )
                    }
                    
                    // Accuracy badge
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.GpsFixed,
                            contentDescription = "Точность",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val accText = location?.let { "±${String.format("%.1f", it.accuracy)}м" } ?: "Поиск..."
                        Text(
                            accText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.SansSerif,
                            color = Color.White
                        )
                    }
                }
                
                // Bottom Center Overlays (Coordinates etc)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding() + 96.dp) // space for bottom buttons
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val lat = location?.let { "Шир: ${String.format("%.4f", it.latitude)}°" } ?: "Ожидание..."
                            val lng = location?.let { "Долг: ${String.format("%.4f", it.longitude)}°" } ?: "Ожидание..."
                            Text(lat, color = Color.White, fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(lng, color = Color.White, fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            val timeStr = SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault()).format(Date())
                            Text(
                                "Время: $timeStr",
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                
                // Bottom UI Bar (Shutter, Gallery, Settings)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding() + 24.dp)
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery (Last Photo thumbnail)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable {
                                onNavigateToAlbum()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (savedImageUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(savedImageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Галерея",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.ImageIcon, contentDescription = "Галерея", tint = Color.White)
                        }
                    }
                    
                    // Shutter Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(if (isCapturing) Color.Gray.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.8f), CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .clickable(enabled = !isCapturing) {
                                isCapturing = true
                                val tempFile = ImageUtils.createTempFile(context)
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                                imageCapture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val finalUri = ImageUtils.addWatermarkAndSaveToInternal(context, tempFile, location)
                                                withContext(Dispatchers.Main) {
                                                    if (finalUri != null) {
                                                        savedImageUri = finalUri
                                                        onImageSaved(finalUri.toString(), location?.latitude, location?.longitude)
                                                        Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                                                    }
                                                    isCapturing = false
                                                    tempFile.delete()
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isCapturing = false
                                            Toast.makeText(context, "Ошибка съемки: ${exception.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    
                    // Settings Button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { showSettings = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showSettings) {
        var showPasswordDialog by remember { mutableStateOf(false) }
        var currentPasswordState by remember { mutableStateOf(settingsManager.getPassword() ?: "") }

        if (showPasswordDialog) {
            var newPassword by remember { mutableStateOf(currentPasswordState) }
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Пароль", color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Column {
                        Text("Оставьте пустым для отключения пароля", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Новый пароль") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        settingsManager.setPassword(if (newPassword.isBlank()) null else newPassword)
                        currentPasswordState = settingsManager.getPassword() ?: ""
                        showPasswordDialog = false
                    }) {
                        Text("Сохранить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        "Настройки",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column {
                        Text(
                            "Безопасность:",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showPasswordDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (currentPasswordState.isEmpty()) "Установить пароль" else "Изменить пароль", color = MaterialTheme.colorScheme.onSecondaryContainer, fontFamily = FontFamily.SansSerif)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            "Поделиться приложением:",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val appUrl = "https://ais-pre-7d7bzzaynft2isgsl52lkd-101130027326.europe-west2.run.app"
                        
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("APK Link", appUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Скачать APK (копировать ссылку)", color = MaterialTheme.colorScheme.onPrimaryContainer, fontFamily = FontFamily.SansSerif)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Смотри, классное приложение для фото с координатами: $appUrl")
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Поделиться", color = MaterialTheme.colorScheme.onPrimaryContainer, fontFamily = FontFamily.SansSerif)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) {
                        Text("Закрыть", fontFamily = FontFamily.SansSerif)
                    }
                }
            )
        }
    }
}
