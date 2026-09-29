package com.rudrasinha.cue.auth

import android.app.Activity
import android.content.Context
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.rudrasinha.cue.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.IDToken
import io.github.jan.supabase.postgrest.Postgrest
import java.security.SecureRandom

class CueAuth(context: Context) {
    val client = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
        install(Auth)
        install(Postgrest)
    }
    private val credentials = CredentialManager.create(context.applicationContext)

    suspend fun signIn(activity: Activity) {
        check(BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) { "Google login needs Cue's Web client ID." }
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setNonce(nonce).build()
        val response = credentials.getCredential(activity,
            GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = response.credential as? CustomCredential
            ?: error("Google did not return an ID credential.")
        check(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "The selected credential was not a Google ID token."
        }
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        client.auth.signInWith(IDToken) {
            idToken = token
            provider = Google
            this.nonce = nonce
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
        credentials.clearCredentialState(ClearCredentialStateRequest())
    }
}
