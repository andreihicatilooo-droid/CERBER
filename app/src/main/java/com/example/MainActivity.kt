package com.example

import android.Manifest
import android.content.Context
import android.location.Location
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                GeoCameraApp()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GeoCameraApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val locationHelper = remember { GeoLocationHelper(context) }
    var location by remember { mutableStateOf<Location?>(null) }
    var savedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    var tempFile by remember { mutableStateOf<File?>(null) }
    var tempUri by remember { mutableStateOf<Uri?>(null) }
    var showSettings by remember { mutableStateOf(false) }

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

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempFile != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val finalUri = ImageUtils.addWatermarkAndSave(context, tempFile!!, location)
                withContext(Dispatchers.Main) {
                    if (finalUri != null) {
                        savedImageUri = finalUri
                        Toast.makeText(context, "DATA_COMMITTED", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "ERR_WRITE_FAILED", Toast.LENGTH_SHORT).show()
                    }
                    tempFile?.delete()
                }
            }
        } else {
            tempFile?.delete()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!permissionsState.allPermissionsGranted) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "> SYS_ERR: ACCESS_DENIED",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "REQUIRED: [CAMERA, LOCATION_FINE]. OVERRIDE NEEDED.",
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionsState.launchMultiplePermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(0.dp)
                        ) {
                            Text("EXEC // SUDO CHMOD", fontFamily = FontFamily.Monospace, color = Color.Black)
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "Location",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "ONION_GEO_TAG",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.GpsFixed,
                            contentDescription = "Accuracy",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val accText = location?.let { "ACCURACY: ±${String.format("%.1f", it.accuracy)}m" } ?: "LINKING..."
                        Text(
                            accText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // Main Camera View Finder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .background(Color.Black)
                        .border(2.dp, MaterialTheme.colorScheme.primary)
                ) {
                    if (savedImageUri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(savedImageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Last Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Decorative elements inside viewfinder (crosshairs)
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Grid lines
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)).align(Alignment.TopCenter).offset(y = 150.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)).align(Alignment.BottomCenter).offset(y = (-150).dp))
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)).align(Alignment.CenterStart).offset(x = 120.dp))
                            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)).align(Alignment.CenterEnd).offset(x = (-120).dp))
                            
                            // Center rectangle target
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                            }
                            
                            // Signal Strong badge
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(24.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "STATUS: SECURE",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            
                            // Coordinate Overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(24.dp)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.8f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        Text(
                                            "[METADATA_INJECTION]",
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val lat = location?.let { "LAT : ${String.format("%.4f", it.latitude)}°" } ?: "LAT : NO_DATA"
                                        val lng = location?.let { "LNG : ${String.format("%.4f", it.longitude)}°" } ?: "LNG : NO_DATA"
                                        Text(lat, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                        Text(lng, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        val timeStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
                                        val altStr = location?.takeIf { it.hasAltitude() }?.let { "${String.format("%.1f", it.altitude)}m" } ?: "REQ_ALT"
                                        Text(
                                            "ALTI: $altStr / TIME: $timeStr",
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    }
                                    
                                    Column(horizontalAlignment = Alignment.End) {
                                        Box(
                                            modifier = Modifier
                                                .padding(bottom = 4.dp)
                                                .size(24.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.WaterDrop,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            "ARMED",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .padding(horizontal = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Gallery Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ImageIcon, contentDescription = "Gallery", tint = MaterialTheme.colorScheme.primary)
                    }
                    
                    // Shutter Button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.Black)
                            .border(2.dp, MaterialTheme.colorScheme.primary)
                            .clickable {
                                val (file, uri) = ImageUtils.createTempFileUri(context)
                                tempFile = file
                                tempUri = uri
                                takePictureLauncher.launch(uri)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "REC",
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                    
                    // Settings Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.primary)
                            .clickable { showSettings = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Color.Black,
            shape = RoundedCornerShape(0.dp),
            modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.primary),
            title = {
                Text(
                    "> SYS_CONFIG",
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "NETWORK OPTIONS:",
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val appUrl = "https://ais-pre-7d7bzzaynft2isgsl52lkd-101130027326.europe-west2.run.app"
                    
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("APK Link", appUrl)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "LINK_COPIED_TO_CLIPBOARD", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text("DOWNLOAD_APK", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Secure Onion GeoCamera Access: $appUrl")
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text("SHARE_ACCESS", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("CLOSE", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}
