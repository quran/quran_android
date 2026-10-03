package org.quran.app.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class AndroidRecitationFileCache(private val directory: File) : RecitationFileCache {
    private fun file(key: String): File { requireRecitationCacheKey(key); return File(directory, key) }
    override fun read(key: String): ByteArray? {
        val file = file(key)
        if (!file.isFile) return null
        if (file.length() !in 1..MAX_RECITATION_BYTES.toLong()) { file.delete(); return null }
        return file.inputStream().use { input ->
            val bytes = ByteArray(file.length().toInt())
            var total = 0
            while (total < bytes.size) {
                val count = input.read(bytes, total, bytes.size - total)
                if (count < 0) break
                total += count
            }
            if (total != bytes.size || input.read() != -1) { file.delete(); null } else bytes
        }
    }
    override fun localUri(key: String): String? = file(key).takeIf { it.isFile && it.length() in 1..MAX_RECITATION_BYTES.toLong() }?.let {
        // Java emits file:/; native players require the explicit file:// URI form.
        "file://" + it.toURI().rawPath
    }
    override suspend fun writeAtomic(key: String, content: ByteArray): String = withContext(Dispatchers.IO) {
        require(content.size in 1..MAX_RECITATION_BYTES)
        val final = file(key)
        check(directory.isDirectory || directory.mkdirs()) { "Could not create recitation storage" }
        val temporary = File.createTempFile("recitation-", ".part", directory)
        try {
            currentCoroutineContext().ensureActive()
            temporary.outputStream().use { output -> output.write(content); output.fd.sync() }
            currentCoroutineContext().ensureActive()
            Files.move(temporary.toPath(), final.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            checkNotNull(localUri(key))
        } finally { temporary.delete() }
    }
    override fun remove(key: String) {
        val file = file(key)
        check(!file.exists() || file.delete()) { "Could not remove cached recitation" }
    }
}

internal actual fun platformRecitationFileCache(): RecitationFileCache = AndroidRecitationFileCache(
    File(platformContext().filesDir, "recitations"),
)

internal actual suspend fun <T> recitationStorageWork(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
