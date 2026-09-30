package com.rudrasinha.cue

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.provider.OpenableColumns
import android.util.Xml
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
        CaptureOrigin(type, if (type == "selection") "Selected text" else "Shared text", text),
        suggestedDue(text))
}

internal fun importedText(context: Context, uri: Uri): CaptureDraft {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "Document"
    val mime = resolver.getType(uri).orEmpty()
    val isImage = mime.startsWith("image/") || listOf(".jpg", ".jpeg", ".png", ".webp")
        .any { name.endsWith(it, true) }
    val documentText = if (mime == "application/pdf" || name.endsWith(".pdf", true)) {
        extractPdf(context, uri)
    } else if (isImage) {
        recognize(InputImage.fromFilePath(context, uri))
    } else if (mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
        name.endsWith(".docx", ignoreCase = true)) {
        resolver.openInputStream(uri)?.use(::extractDocx)
    } else if (mime.startsWith("text/") || mime == "application/csv" ||
        name.endsWith(".csv", true) || name.endsWith(".tsv", true) ||
        name.endsWith(".md", true)) {
        resolver.openInputStream(uri)?.use { it.readLimited(64 * 1024).toString(Charsets.UTF_8) }
    } else null
    val text = documentText?.trim()?.take(4000)?.takeIf { it.isNotEmpty() }
        ?: error("No readable text found. Try a clearer image or a text, PDF or DOCX file.")
    return CaptureDraft(UUID.randomUUID().toString(), text,
        CaptureOrigin(if (isImage) "image" else "document", name, text, uri.toString()))
}

private fun recognize(image: InputImage): String {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try { Tasks.await(recognizer.process(image)).text }
    finally { recognizer.close() }
}

private fun extractPdf(context: Context, uri: Uri): String {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: error("Could not open this PDF.")
    descriptor.use { file ->
        PdfRenderer(file).use { renderer ->
            require(renderer.pageCount <= 20) { "This PDF has over 20 pages. Split it and import a smaller part." }
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val pages = StringBuilder()
                repeat(renderer.pageCount) { index ->
                    renderer.openPage(index).use { page ->
                        val scale = minOf(2f, 2048f / maxOf(page.width, page.height))
                        val bitmap = Bitmap.createBitmap(
                            maxOf(1, (page.width * scale).toInt()),
                            maxOf(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0))).text
                            if (text.isNotBlank()) pages.append(text).append('\n')
                        } finally { bitmap.recycle() }
                    }
                }
                return pages.toString()
            } finally { recognizer.close() }
        }
    }
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
