package com.example.greenstreem
import org.junit.Test
import org.junit.Assert.*
class RemoteManagementSettingsTest {
    @Test fun acceptsOnlySupportedPreferenceValues() {
        RemoteManagementSettings.validateSetting("player_buffer_size_sec",60)
        RemoteManagementSettings.validateSetting("dashboard_quick_0",false)
        RemoteManagementSettings.validateSetting("player_live_stream_format","hls")
    }
    @Test fun rejectsCredentialsAndEntitlementChanges() {
        for(key in listOf("password","server_url","pro_unlocked","parental_pin_hash")) {
            assertThrows(IllegalStateException::class.java) { RemoteManagementSettings.validateSetting(key,"secret") }
        }
    }
    @Test fun rejectsWrongTypesAndOutOfRangeChoices() {
        assertThrows(IllegalArgumentException::class.java) { RemoteManagementSettings.validateSetting("player_buffer_size_sec",-1) }
        assertThrows(IllegalArgumentException::class.java) { RemoteManagementSettings.validateSetting("autoplay_last_channel","false") }
        assertThrows(IllegalArgumentException::class.java) { RemoteManagementSettings.validateSetting("player_live_stream_format","invalid") }
    }
    @Test fun everySettingHasUniqueKeyAndValidDefault() {
        val settings=RemoteManagementSettings.settings
        assertEquals(settings.size,settings.map { it.key }.toSet().size)
        settings.forEach { RemoteManagementSettings.validateSetting(it.key,it.default) }
    }
}
