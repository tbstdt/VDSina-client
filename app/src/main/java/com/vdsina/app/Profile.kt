package com.vdsina.app

import org.json.JSONObject

data class Profile(
    val id: String,
    var name: String,
    var url: String,
    var login: String,
    var password: String,
    var cookies: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("url", url)
            put("login", login)
            put("password", password)
            put("cookies", cookies)
        }
    }

    companion object {
        const val DEFAULT_URL = "https://cp.vdsina.com/"

        fun fromJson(json: JSONObject): Profile {
            return Profile(
                id = json.getString("id"),
                name = json.getString("name"),
                url = json.optString("url", DEFAULT_URL),
                login = json.optString("login", ""),
                password = json.optString("password", ""),
                cookies = json.optString("cookies", "")
            )
        }

        fun createDefault(): Profile {
            return Profile(
                id = System.currentTimeMillis().toString(),
                name = "Default",
                url = DEFAULT_URL,
                login = "",
                password = ""
            )
        }
    }
}

