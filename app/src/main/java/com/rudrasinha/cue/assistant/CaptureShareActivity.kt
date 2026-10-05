package com.rudrasinha.cue.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.rudrasinha.cue.CaptureIntake
import com.rudrasinha.cue.importedText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Receives Android's URI grant while visible, analyzes the share, then returns to the source app. */
class CaptureShareActivity : Activity() {
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val incoming = intent
        work.launch {
            val intake = CaptureIntake(applicationContext)
            try {
                val text = withContext(Dispatchers.IO) {
                    val chunks = mutableListOf<String>()
                    val action = incoming?.action
                    val direct = when (action) {
                        Intent.ACTION_PROCESS_TEXT -> incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                        else -> incoming?.getCharSequenceExtra(Intent.EXTRA_TEXT)
                    }?.toString()
                    if (!direct.isNullOrBlank()) chunks += direct
                    val uris = mutableListOf<Uri>()
                    if (Build.VERSION.SDK_INT >= 33)
                        incoming?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(uris::add)
                    else @Suppress("DEPRECATION")
                        incoming?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::add)
                    if (action == Intent.ACTION_SEND_MULTIPLE) {
                        if (Build.VERSION.SDK_INT >= 33)
                            incoming?.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                                ?.let(uris::addAll)
                        else @Suppress("DEPRECATION")
                            incoming?.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                                ?.let(uris::addAll)
                    }
                    incoming?.clipData?.let { clip ->
                        repeat(minOf(clip.itemCount, 8)) { index ->
                            val item = clip.getItemAt(index)
                            item.uri?.let(uris::add)
                            if (item.uri == null) item.text?.toString()?.let(chunks::add)
                        }
                    }
                    uris.distinct().take(8).forEach { uri ->
                        chunks += importedText(this@CaptureShareActivity, uri).text
                    }
                    chunks.joinToString("\n").take(4000)
                }
                if (text.isBlank()) intake.failure("Shared item contained no readable text.")
                else intake.accept(text, "share", "Shared from another app")
            } catch (e: Exception) {
                intake.failure(e.message ?: "Could not read the shared item.")
            } finally { finish() }
        }
    }

    override fun onDestroy() {
        work.cancel()
        super.onDestroy()
    }
}
