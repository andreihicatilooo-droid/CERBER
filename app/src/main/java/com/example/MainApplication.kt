package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.PhotoRepository

class MainApplication : Application() {
    lateinit var database: AppDatabase
    lateinit var repository: PhotoRepository

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "geo_camera_database"
        ).build()
        repository = PhotoRepository(database.photoDao(), this)
    }
}
