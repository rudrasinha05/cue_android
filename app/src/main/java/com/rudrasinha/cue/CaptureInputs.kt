package com.rudrasinha.cue

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.rudrasinha.cue.data.CaptureOrigin
import com.rudrasinha.cue.ui.CaptureDraft
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

internal fun sharedText(intent: Intent?): CaptureDraft? {
    val text = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        else -> null
    }?.trim()?.take(4000)?.takeIf { it.isNotBlank() } ?: return null
    val type = if (intent?.action == Intent.ACTION_PROCESS_TEXT) "selection" else "share"
    return CaptureDraft(UUID.randomUUID().toString(), text,
        CaptureOrigin(type, if (type == "selection") "Selected text" else "Shared text", text))
}

internal fun importedText(context: Context, uri: Uri): CaptureDraft {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "Document"
    val mime = resolver.getType(uri).orEmpty()
    val documentText = if (mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
        name.endsWith(".docx", ignoreCase = true)) {
        resolver.openInputStream(uri)?.use(::extractDocx)
    } else if (mime.startsWith("text/") || mime == "application/csv" ||
        name.endsWith(".csv", true) || name.endsWith(".tsv", true) ||
        name.endsWith(".md", true)) {
        resolver.openInputStream(uri)?.use { it.readLimited(64 * 1024).toString(Charsets.UTF_8) }
    } else null
    val text = documentText?.trim()?.take(4000)?.takeIf { it.isNotEmpty() }
        ?: error("Choose a text, CSV, TSV, Markdown or DOCX file with readable text.")
    return CaptureDraft(UUID.randomUUID().toString(), text,
        CaptureOrigin("document", name, text, uri.toString()))
}

private fun extractDocx(input: InputStream): String {
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name != "word/document.xml") continue
            val xml = Xml.newPullParser()
            xml.setInput(ByteArrayInputStream(zip.readLimited(256 * 1024)), "UTF-8")
            val out = StringBuilder()
            var event = xml.eventType
            while (event != XmlPullParser.END_DOCUMENT && out.length < 4000) {
                if (event == XmlPullParser.START_TAG) when (xml.name) {
                    "t" -> out.append(xml.nextText())
                    "tab" -> out.append('\t')
                    "p" -> if (out.isNotEmpty()) out.append('\n')
                }
                event = xml.next()
            }
            return out.toString()
        }
    }
    return ""
}

private fun InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val chunk = ByteArray(4096)
    while (output.size() < limit) {
        val read = read(chunk, 0, minOf(chunk.size, limit - output.size()))
        if (read < 0) break
        output.write(chunk, 0, read)
    }
    return output.toByteArray()
}
