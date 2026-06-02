package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import com.example.network.NetworkClient
import com.example.network.PlaceResult
import com.example.BuildConfig
import com.google.android.gms.maps.model.BitmapDescriptorFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasLocationPermission = isGranted }
    )

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Карта") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasLocationPermission) {
                MapContent()
            } else {
                Text(
                    "Требуется разрешение на геопозицию для работы с картой",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MapContent() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var places by remember { mutableStateOf<List<Pair<PlaceResult, String>>>(emptyList()) }
    var cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(55.7558, 37.6173), 10f) // Moscow default
    }

    LaunchedEffect(Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val currentLatLng = LatLng(location.latitude, location.longitude)
                currentLocation = currentLatLng
                cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLatLng, 14f)
                
                // Fetch nearby places
                coroutineScope.launch {
                    try {
                        val types = listOf("school", "hospital", "police", "local_government_office")
                        val apiKey = BuildConfig.MAPS_API_KEY
                        if (apiKey.isNotEmpty() && apiKey != "MY_MAPS_API_KEY") {
                            val allPlaces = mutableListOf<Pair<PlaceResult, String>>()
                            val locString = "${location.latitude},${location.longitude}"
                            
                            types.forEach { type ->
                                val response = NetworkClient.placesApi.getNearbyPlaces(
                                    location = locString,
                                    radius = 2000,
                                    type = type,
                                    apiKey = apiKey
                                )
                                response.results.forEach { place ->
                                    allPlaces.add(place to type)
                                }
                            }
                            places = allPlaces
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = true)
    ) {
        places.forEach { (place, type) ->
            val color = when (type) {
                "school" -> BitmapDescriptorFactory.HUE_ORANGE
                "hospital" -> BitmapDescriptorFactory.HUE_RED
                "police" -> BitmapDescriptorFactory.HUE_BLUE
                "local_government_office" -> BitmapDescriptorFactory.HUE_VIOLET
                else -> BitmapDescriptorFactory.HUE_AZURE
            }
            Marker(
                state = MarkerState(position = LatLng(place.geometry.location.lat, place.geometry.location.lng)),
                title = place.name,
                icon = BitmapDescriptorFactory.defaultMarker(color)
            )
        }
    }
}
