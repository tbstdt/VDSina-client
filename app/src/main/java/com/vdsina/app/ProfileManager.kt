package com.vdsina.app

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import org.json.JSONArray
import org.json.JSONObject

class ProfileManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cookieManager = CookieManager.getInstance()

    fun getProfiles(): List<Profile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return listOf(Profile.createDefault())
        return try {
            val array = JSONArray(json)
            val profiles = mutableListOf<Profile>()
            for (i in 0 until array.length()) {
                profiles.add(Profile.fromJson(array.getJSONObject(i)))
            }
            if (profiles.isEmpty()) {
                listOf(Profile.createDefault())
            } else {
                profiles
            }
        } catch (e: Exception) {
            listOf(Profile.createDefault())
        }
    }

    fun saveProfiles(profiles: List<Profile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    fun getProfile(id: String): Profile? {
        return getProfiles().find { it.id == id }
    }

    fun addProfile(profile: Profile) {
        val profiles = getProfiles().toMutableList()
        profiles.add(profile)
        saveProfiles(profiles)
    }

    fun updateProfile(profile: Profile) {
        val profiles = getProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            profiles[index] = profile
            saveProfiles(profiles)
        }
    }

    fun deleteProfile(id: String) {
        val profiles = getProfiles().toMutableList()
        profiles.removeAll { it.id == id }
        if (profiles.isEmpty()) {
            profiles.add(Profile.createDefault())
        }
        saveProfiles(profiles)

        if (getCurrentProfileId() == id) {
            setCurrentProfileId(profiles.first().id)
        }
    }

    fun getCurrentProfileId(): String {
        val id = prefs.getString(KEY_CURRENT_PROFILE, null)
        if (id == null) {
            val profiles = getProfiles()
            val firstId = profiles.first().id
            setCurrentProfileId(firstId)
            return firstId
        }
        return id
    }

    fun setCurrentProfileId(id: String) {
        prefs.edit().putString(KEY_CURRENT_PROFILE, id).apply()
    }

    fun getCurrentProfile(): Profile {
        val id = getCurrentProfileId()
        return getProfile(id) ?: getProfiles().first()
    }

    fun saveCookiesForCurrentProfile(url: String) {
        val profile = getCurrentProfile()
        val cookies = cookieManager.getCookie(url)
        if (cookies != null) {
            profile.cookies = cookies
            updateProfile(profile)
        }
    }

    fun loadCookiesForProfile(profile: Profile) {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()

        if (profile.cookies.isNotEmpty() && profile.url.isNotEmpty()) {
            val cookies = profile.cookies.split(";")
            cookies.forEach { cookie ->
                val trimmed = cookie.trim()
                if (trimmed.isNotEmpty()) {
                    cookieManager.setCookie(profile.url, trimmed)
                }
            }
            cookieManager.flush()
        }
    }

    fun switchToProfile(id: String): Profile {
        val currentProfile = getCurrentProfile()
        saveCookiesForCurrentProfile(currentProfile.url)

        setCurrentProfileId(id)
        val newProfile = getCurrentProfile()
        loadCookiesForProfile(newProfile)

        return newProfile
    }

    companion object {
        private const val PREFS_NAME = "vdsina_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_CURRENT_PROFILE = "current_profile_id"

        @Volatile
        private var instance: ProfileManager? = null

        fun getInstance(context: Context): ProfileManager {
            return instance ?: synchronized(this) {
                instance ?: ProfileManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

