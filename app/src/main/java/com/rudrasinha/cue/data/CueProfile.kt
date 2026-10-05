package com.rudrasinha.cue.data

import com.rudrasinha.cue.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CueProfile(val name: String, val email: String, val gender: String,
    val contactMobile: String)

/** User-owned Auth metadata; mobile is a contact field, not a verified login number. */
class CueProfileStore {
    suspend fun load(token: String): CueProfile = request(token, "GET")

    suspend fun save(token: String, name: String, gender: String, mobile: String): CueProfile {
        require(name.trim().length in 1..80) { "Enter a name (up to 80 characters)." }
        require(gender.length <= 40) { "Gender is too long." }
        require(mobile.isBlank() ||
            (mobile.length in 7..20 && mobile.matches(Regex("[+0-9() -]+")))) {
            "Enter a valid contact number or leave it blank."
        }
        val body = JSONObject().put("data", JSONObject()
            .put("cue_display_name", name.trim())
            .put("cue_gender", gender.trim())
            .put("cue_contact_mobile", mobile.trim()))
        return request(token, "PUT", body)
    }

    suspend fun deleteAccount(token: String) = withContext(Dispatchers.IO) {
        val connection = URL("${BuildConfig.SUPABASE_URL}/functions/v1/cue-delete-account")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            connection.doOutput = true
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write("{\"confirm\":\"DELETE MY CUE ACCOUNT\"}".toByteArray(Charsets.UTF_8))
            }
            if (connection.responseCode !in 200..299) {
                val message = runCatching {
                    JSONObject(connection.errorStream.bufferedReader().use { it.readText() })
                        .optString("message")
                }.getOrNull()
                error(message?.takeIf { it.isNotBlank() } ?: "Account deletion failed. Try again online.")
            }
        } finally { connection.disconnect() }
    }

    private suspend fun request(token: String, method: String, body: JSONObject? = null): CueProfile =
        withContext(Dispatchers.IO) {
            val connection = URL("${BuildConfig.SUPABASE_URL}/auth/v1/user")
                .openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                if (connection.responseCode !in 200..299) error("Could not load Cue profile. Check your connection.")
                val user = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val meta = user.optJSONObject("user_metadata") ?: JSONObject()
                CueProfile(
                    name = meta.optString("cue_display_name").ifBlank {
                        meta.optString("full_name").ifBlank { meta.optString("name") }
                    },
                    email = user.optString("email"),
                    gender = meta.optString("cue_gender"),
                    contactMobile = meta.optString("cue_contact_mobile"))
            } finally { connection.disconnect() }
        }
}
