package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FocusNetRepository
import com.example.model.TrafficEventType
import com.example.model.TrafficLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FocusNet", appName)
    }

    @Test
    fun `repository toggles whitelist correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FocusNetRepository.getInstance(context)

        val testPkg = "com.spotify.music"
        repo.toggleAppWhitelist(testPkg, true)
        assertTrue(repo.whitelistedPackages.value.contains(testPkg))

        repo.toggleAppWhitelist(testPkg, false)
        assertFalse(repo.whitelistedPackages.value.contains(testPkg))
    }

    @Test
    fun `repository updates protection state and logs event`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FocusNetRepository.getInstance(context)

        repo.updateProtection(true)
        assertTrue(repo.settings.value.isProtectionEnabled)

        val firstLog = repo.logs.value.firstOrNull()
        assertTrue(firstLog != null)
        assertEquals(TrafficEventType.SERVICE_STARTED, firstLog?.eventType)
    }

    @Test
    fun `blocked attempts stores most recent on top`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FocusNetRepository.getInstance(context)

        repo.recordBlockedAttempt("com.zhiliaoapp.musically", "Tentativa 1")
        val mostRecent = repo.blockedAttempts.value.first()
        assertEquals("com.zhiliaoapp.musically", mostRecent.packageName)
        assertEquals("TikTok", mostRecent.appName)
    }

    @Test
    fun `autoActivateOnConnect toggles correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = FocusNetRepository.getInstance(context)

        assertTrue(repo.settings.value.autoActivateOnConnect)
        repo.updateAutoActivate(false)
        assertFalse(repo.settings.value.autoActivateOnConnect)
        repo.updateAutoActivate(true)
        assertTrue(repo.settings.value.autoActivateOnConnect)
    }
}
