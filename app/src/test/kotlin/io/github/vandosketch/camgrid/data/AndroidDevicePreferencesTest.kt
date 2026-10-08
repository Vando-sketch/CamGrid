package io.github.vandosketch.camgrid.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDevicePreferencesTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun nothingSavedGivesNull() {
        assertNull(AndroidDevicePreferences(app).getInt("minutes"))
    }

    @Test
    fun aValueComesBackAfterARestart() {
        AndroidDevicePreferences(app).putInt("minutes", 10)
        assertEquals(10, AndroidDevicePreferences(app).getInt("minutes"))
    }
}
