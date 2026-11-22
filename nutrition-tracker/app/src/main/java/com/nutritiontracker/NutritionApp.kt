package com.nutritiontracker

import android.app.Application
import com.nutritiontracker.data.db.AppDatabase

class NutritionApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
}
