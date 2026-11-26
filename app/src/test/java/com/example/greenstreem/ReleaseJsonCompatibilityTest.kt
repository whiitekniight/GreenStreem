package com.example.greenstreem

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

class ReleaseJsonCompatibilityTest {
    private val gson = Gson()

    @Test fun savedPlaylistFieldsRemainCompatible() {
        val type = object : TypeToken<List<PlaylistProfile>>() {}.type
        val profiles = gson.fromJson<List<PlaylistProfile>>(
            """[{"id":"existing","name":"Saved playlist","serverUrl":"https://example.com","username":"test","password":"fixture","enabled":true,"updateIntervalHours":12}]""",
            type
        )
        assertEquals("https://example.com", profiles.single().serverUrl)
        assertEquals("test", profiles.single().username)
        val encoded = gson.toJsonTree(profiles.single()).asJsonObject
        assertEquals("fixture", encoded.get("password").asString)
        assertEquals(12, encoded.get("updateIntervalHours").asInt)
    }

    @Test fun nestedSeriesCacheAndDirectUrlsSurviveRoundTrip() {
        val type = object : TypeToken<Map<String, List<XtreamEpisode>>>() {}.type
        val json = """{"1":[{"id":"123","title":"Fixture","container_extension":"mp4","season":1,"episode_num":2,"directUrl":"https://example.com/episode.mp4"}]}"""
        val seasons = gson.fromJson<Map<String, List<XtreamEpisode>>>(json, type)
        val restored = gson.fromJson<Map<String, List<XtreamEpisode>>>(gson.toJson(seasons), type)
        assertEquals(2, restored.getValue("1").single().episodeNum)
        assertEquals("https://example.com/episode.mp4", restored.getValue("1").single().directUrl)
    }

    @Test fun providerAlternateCategoryFieldsRemainSupported() {
        val category = gson.fromJson("""{"cat_id":"7","title":"Fixture","parent":0}""", XtreamCategory::class.java)
        assertEquals("7", category.id)
        assertEquals("Fixture", category.name)
    }
}
