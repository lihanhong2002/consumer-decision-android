package com.sixmodel.consumerdecision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.sixmodel.consumerdecision.ai.*
import com.sixmodel.consumerdecision.data.*

class MainActivity: ComponentActivity() {
    private lateinit var model: AppModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        val repository=DecisionRepository(Room.databaseBuilder(applicationContext,AppDatabase::class.java,"consumer-decision.db").addMigrations(AppDatabase.MIGRATION_1_2).build())
        val rules=assets.open("model-spec.md").bufferedReader().use { it.readText() }+"\n"+assets.open("engineering-rules.md").bufferedReader().use { it.readText() }
        model=ViewModelProvider(this,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = AppModel(repository,AiSettingsStore(applicationContext),AiClient(rules)) as T
        })[AppModel::class.java]
        setContent { ConsumerTheme { ConsumerApp(model) } }
    }
    override fun onStop() { if(::model.isInitialized) model.save();super.onStop() }
}
