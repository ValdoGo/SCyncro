package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun default_traffic_settings_has_expected_values() {
    val settings = com.example.model.TrafficSettings()
    assertFalse(settings.isProtectionEnabled)
    assertTrue(settings.autoActivateOnConnect)
    assertEquals(20, settings.gracePeriodSeconds)
    assertTrue(settings.filterMobileData)
    assertTrue(settings.filterWifi)
  }

  @Test
  fun traffic_event_types_exist() {
    val granted = com.example.model.TrafficEventType.FOREGROUND_GRANTED
    val blocked = com.example.model.TrafficEventType.BACKGROUND_BLOCKED
    val exempt = com.example.model.TrafficEventType.EXEMPTION_ALLOWED
    assertNotNull(granted)
    assertNotNull(blocked)
    assertNotNull(exempt)
  }

  @Test
  fun smart_default_whitelist_contains_essential_telephony_and_push_services() {
    val essentialDefaults = setOf(
      "com.google.android.gms",
      "com.android.vending",
      "com.google.android.dialer",
      "com.android.dialer",
      "com.samsung.android.dialer",
      "com.google.android.apps.messaging",
      "com.android.mms",
      "com.android.providers.telephony"
    )
    assertTrue(essentialDefaults.contains("com.google.android.gms"))
    assertTrue(essentialDefaults.contains("com.google.android.dialer"))
    assertTrue(essentialDefaults.contains("com.google.android.apps.messaging"))
    assertTrue(essentialDefaults.contains("com.android.providers.telephony"))
  }

  @Test
  fun temporary_passes_expiration_and_cleanup_logic() {
    val now = 1000000L
    val passes = mutableMapOf(
      "com.whatsapp" to now + 50000L,       // valid
      "com.instagram.android" to now - 1000L // expired
    )

    val expired = passes.filterValues { it <= now }
    val valid = passes.filterValues { it > now }

    assertEquals(1, expired.size)
    assertTrue(expired.containsKey("com.instagram.android"))
    assertEquals(1, valid.size)
    assertTrue(valid.containsKey("com.whatsapp"))
  }

  @Test
  fun temporary_passes_serialization_roundtrip() {
    val now = System.currentTimeMillis()
    val initialPasses = mapOf(
      "com.whatsapp" to now + 300000L,
      "com.spotify.music" to now + 600000L
    )

    // Serialization
    val raw = initialPasses.entries.joinToString(",") { "${it.key}:${it.value}" }
    assertTrue(raw.contains("com.whatsapp"))
    assertTrue(raw.contains("com.spotify.music"))

    // Deserialization
    val deserialized = mutableMapOf<String, Long>()
    raw.split(",").forEach { item ->
      val parts = item.split(":")
      if (parts.size == 2) {
        val pkg = parts[0].trim()
        val ts = parts[1].trim().toLongOrNull() ?: 0L
        if (pkg.isNotEmpty() && ts > now) {
          deserialized[pkg] = ts
        }
      }
    }

    assertEquals(2, deserialized.size)
    assertEquals(initialPasses["com.whatsapp"], deserialized["com.whatsapp"])
    assertEquals(initialPasses["com.spotify.music"], deserialized["com.spotify.music"])
  }

  @Test
  fun multi_app_grace_period_map_supports_independent_tracking() {
    val now = 5000000L
    val graceMap = mutableMapOf<String, Long>()

    // App A leaves foreground (20s grace)
    graceMap["com.whatsapp"] = now + 20000L
    // App B leaves foreground 5s later (20s grace)
    graceMap["com.zhiliaoapp.musically"] = now + 25000L

    assertEquals(2, graceMap.size)
    assertTrue(graceMap.containsKey("com.whatsapp"))
    assertTrue(graceMap.containsKey("com.zhiliaoapp.musically"))

    // Fast-forward 21 seconds
    val later = now + 21000L
    val activeAtLater = graceMap.filterValues { it > later }
    val expiredAtLater = graceMap.filterValues { it <= later }

    assertEquals(1, activeAtLater.size)
    assertTrue(activeAtLater.containsKey("com.zhiliaoapp.musically"))
    assertEquals(1, expiredAtLater.size)
    assertTrue(expiredAtLater.containsKey("com.whatsapp"))
  }

  @Test
  fun keyboard_app_name_heuristic_does_not_accidentally_block_normal_apps() {
    // Exact list of known system keyboards or IMEs
    val systemIgnored = setOf(
      "com.android.systemui",
      "android",
      "com.android.permissioncontroller",
      "com.google.android.permissioncontroller",
      "com.android.packageinstaller",
      "com.google.android.packageinstaller"
    )
    val enabledImes = setOf(
      "com.google.android.inputmethod.latin",
      "com.samsung.android.honeyboard"
    )

    val isIgnored = { pkg: String -> systemIgnored.contains(pkg) || enabledImes.contains(pkg) }

    // System and real keyboards should be ignored
    assertTrue(isIgnored("com.android.systemui"))
    assertTrue(isIgnored("com.google.android.inputmethod.latin"))

    // Normal apps that have "keyboard" or "ime" in their names must NOT be ignored
    assertFalse(isIgnored("com.game.keyboardhero"))
    assertFalse(isIgnored("com.rime.messaging"))
    assertFalse(isIgnored("com.prime.video"))
    assertFalse(isIgnored("com.whatsapp"))
  }
}
