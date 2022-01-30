package de.practicetime.practicetime

import android.app.Application
import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.room.Room
import de.practicetime.practicetime.database.PTDao
import de.practicetime.practicetime.database.PTDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PracticeTime : Application() {
    val executorService: ExecutorService = Executors.newFixedThreadPool(4)

    companion object {
        lateinit var dao: PTDao         // the central static dao object of the application
        var serviceIsRunning = false
        var isRecording = false
        const val PREFERENCES_KEY_FIRSTRUN = "firstrun"
        const val PREFERENCES_KEY_THEME = "'theme'"

        /**
         * Get a color int from a theme attribute.
         * Activity context must be used instead of applicationContext: https://stackoverflow.com/q/34052810
         * Access like PracticeTime.getThemeColor() in Activity
         * */
        @ColorInt
        fun getThemeColor(@AttrRes color: Int, activityContext: Context): Int {
            val typedValue = TypedValue()
            activityContext.theme.resolveAttribute(color, typedValue, true)
            return typedValue.data
        }
    }

    override fun onCreate() {
        super.onCreate()
        openDatabase()
    }

    private fun openDatabase() {
        val db = Room.databaseBuilder(
            applicationContext,
            PTDatabase::class.java, "pt-database"
        ).build()
        dao = db.ptDao
    }
}