package app.fahrtenbuch.mobile.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Rundes Profilbild: Gravatar zur E-Mail-Adresse, sonst die Initialen.
 * Gravatar wird mit `d=404` abgefragt – ohne hinterlegtes Bild kommt kein Platzhalter,
 * sondern die Initialen bleiben stehen. An Gravatar geht nur der SHA-256-Hash der Adresse.
 */
@Composable
fun UserAvatar(
    email: String?,
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val pixels = with(LocalDensity.current) { size.roundToPx() }.coerceIn(48, 512)
    val image by produceState<ImageBitmap?>(initialValue = GravatarCache.cached(email, pixels), email, pixels) {
        if (value == null) value = GravatarCache.load(email, pixels)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                initials,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private object GravatarCache {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // Auch „kein Bild“ merken, damit nicht bei jedem Öffnen erneut gefragt wird
    private val images = ConcurrentHashMap<String, ImageBitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    private fun key(email: String?, pixels: Int): String? =
        email?.trim()?.lowercase()?.takeIf { it.contains('@') }?.let { "${sha256(it)}?s=$pixels" }

    fun cached(email: String?, pixels: Int): ImageBitmap? = key(email, pixels)?.let(images::get)

    suspend fun load(email: String?, pixels: Int): ImageBitmap? {
        val key = key(email, pixels) ?: return null
        images[key]?.let { return it }
        if (key in missing) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://gravatar.com/avatar/$key&d=404")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        // 404 = kein Gravatar; bei Netzfehlern (Exception) später erneut versuchen
                        if (response.code == 404) missing += key
                        return@use null
                    }
                    val bytes = response.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()?.also { images[key] = it }
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
