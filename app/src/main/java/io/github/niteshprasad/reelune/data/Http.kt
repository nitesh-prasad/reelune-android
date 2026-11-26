package io.github.niteshprasad.reelune.data

import io.github.niteshprasad.reelune.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A problem the user can act on; [message] is shown in the UI. */
class UserFacingException(message: String, val code: Int = 0) : Exception(message)

object Http {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Volatile
    private var cacheDir: File? = null

    fun init(cacheDir: File) {
        this.cacheDir = cacheDir
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply { cacheDir?.let { cache(Cache(File(it, "http"), 50L * 1024 * 1024)) } }
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Reelune-Android/${BuildConfig.VERSION_NAME}")
                        .build(),
                )
            }
            .build()
    }

    suspend fun get(request: Request): String = withContext(Dispatchers.IO) {
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw UserFacingException("Can't reach the internet. Check your connection and try again.")
        }
        response.use { r ->
            val body = r.body.string()
            if (!r.isSuccessful) throw HttpStatusException(r.code, body)
            body
        }
    }
}

class HttpStatusException(val code: Int, val body: String) : IOException("HTTP $code")

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
