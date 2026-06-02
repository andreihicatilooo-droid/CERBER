package com.example.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.example.data.SettingsManager
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import kotlinx.coroutines.launch
import com.example.GeoCameraAppUI

@Composable
fun AppNavHost(navController: NavHostController, viewModel: MainViewModel, settingsManager: SettingsManager) {
    var isUnlocked by remember { mutableStateOf(settingsManager.getPassword().isNullOrEmpty()) }

    if (!isUnlocked) {
        val correctPassword = settingsManager.getPassword() ?: ""
        AuthScreen(correctPassword = correctPassword, onUnlocked = { isUnlocked = true })
    } else {
        NavHost(navController = navController, startDestination = "camera") {
            composable("camera") {
                GeoCameraAppUI(
                    onNavigateToAlbum = { navController.navigate("album") },
                    onImageSaved = { uriString, lat, lng ->
                        viewModel.insertPhoto(uriString, lat, lng)
                    },
                    settingsManager = settingsManager
                )
            }
        composable("album") {
            AlbumScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPhotoClick = { id -> navController.navigate("edit/$id") }
            )
        }
        composable(
            "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt("id") ?: return@composable
            EditPhotoScreen(
                id = id,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
    }
}

@Composable
fun AuthScreen(correctPassword: String, onUnlocked: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Вход", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { 
                    password = it
                    isError = false
                },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = isError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )
            if (isError) {
                Text("Неверный пароль", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (password == correctPassword) {
                        onUnlocked()
                    } else {
                        isError = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Войти", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlbumScreen(viewModel: MainViewModel, onBack: () -> Unit, onPhotoClick: (Int) -> Unit) {
    val photos by viewModel.allPhotos.collectAsStateWithLifecycle()
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPhotos by remember { mutableStateOf(setOf<Int>()) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "Выбрано: ${selectedPhotos.size}" else "Альбом") },
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            selectionMode = false
                            selectedPhotos = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    if (selectionMode && selectedPhotos.isNotEmpty()) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                var successCount = 0
                                selectedPhotos.forEach { id ->
                                    val photo = photos.find { it.id == id }
                                    if (photo != null) {
                                        val exported = com.example.ImageUtils.exportToGallery(context, photo.uriString)
                                        if (exported) successCount++
                                    }
                                }
                                android.widget.Toast.makeText(context, "Экспортировано $successCount из ${selectedPhotos.size}", android.widget.Toast.LENGTH_SHORT).show()
                                selectionMode = false
                                selectedPhotos = emptySet()
                            }
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Экспорт в галерею")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Нет сохраненных фото")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(4.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    val isSelected = selectedPhotos.contains(photo.id)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = if (isSelected) 4.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        selectedPhotos = if (isSelected) selectedPhotos - photo.id else selectedPhotos + photo.id
                                        if (selectedPhotos.isEmpty()) selectionMode = false
                                    } else {
                                        onPhotoClick(photo.id)
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selectedPhotos = setOf(photo.id)
                                    }
                                }
                            )
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photo.uriString)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                            )
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPhotoScreen(id: Int, viewModel: MainViewModel, onBack: () -> Unit) {
    val photo by viewModel.currentPhoto.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    var description by remember { mutableStateOf("") }
    
    LaunchedEffect(id) {
        viewModel.loadPhoto(id)
        viewModel.resetUploadState()
    }

    LaunchedEffect(photo) {
        if (photo != null && description.isEmpty()) {
            description = photo?.description ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактирование") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deletePhoto(id)
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        if (photo == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo!!.uriString)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Photo Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.1f))
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = {
                        viewModel.updatePhotoDescription(id, description)
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сохранить")
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Button(onClick = {
                        viewModel.uploadPhotoToNbox(photo!!)
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("В nbox.me")
                    }
                }

                if (uploadState != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uploadState!!,
                        color = if (uploadState!!.startsWith("Ошибка")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (photo!!.nboxUrl != null) {
                    val context = LocalContext.current
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ссылка nbox: ${photo!!.nboxUrl}",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(photo!!.nboxUrl))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
