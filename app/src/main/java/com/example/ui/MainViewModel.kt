package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PhotoEntity
import com.example.data.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repository: PhotoRepository) : ViewModel() {
    val allPhotos: StateFlow<List<PhotoEntity>> = repository.allPhotos.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentPhoto = MutableStateFlow<PhotoEntity?>(null)
    val currentPhoto: StateFlow<PhotoEntity?> = _currentPhoto.asStateFlow()

    private val _uploadState = MutableStateFlow<String?>(null)
    val uploadState = _uploadState.asStateFlow()

    fun insertPhoto(uriString: String, latitude: Double?, longitude: Double?) {
        viewModelScope.launch {
            repository.insert(PhotoEntity(
                uriString = uriString,
                latitude = latitude,
                longitude = longitude
            ))
        }
    }

    fun loadPhoto(id: Int) {
        viewModelScope.launch {
            _currentPhoto.value = repository.getById(id)
        }
    }

    fun updatePhotoDescription(id: Int, desc: String) {
        viewModelScope.launch {
            val photo = repository.getById(id)
            if (photo != null) {
                repository.update(photo.copy(description = desc))
                _currentPhoto.value = repository.getById(id)
            }
        }
    }

    fun uploadPhotoToNbox(photo: PhotoEntity) {
        viewModelScope.launch {
            _uploadState.value = "Загрузка..."
            val result = repository.uploadToNbox(photo)
            result.onSuccess { url ->
                val updatedPhoto = photo.copy(nboxUrl = url)
                repository.update(updatedPhoto)
                _currentPhoto.value = updatedPhoto
                _uploadState.value = "Успех! Ссылка: $url"
            }.onFailure {
                _uploadState.value = "Ошибка: ${it.message}"
            }
        }
    }

    fun resetUploadState() {
        _uploadState.value = null
    }

    fun deletePhoto(id: Int) {
         viewModelScope.launch {
             repository.delete(id)
             _currentPhoto.value = null
         }
    }
}

class MainViewModelFactory(private val repository: PhotoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
