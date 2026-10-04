@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package org.quran.app.data

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import platform.Foundation.*
import platform.posix.memcpy
import platform.posix.rename

internal class IosRecitationFileCache(private val directory: String) : RecitationFileCache {
    private val manager = NSFileManager.defaultManager
    private fun path(key: String): String { requireRecitationCacheKey(key); return "$directory/$key" }
    private fun regular(path: String): Boolean = manager.attributesOfItemAtPath(path, null)?.get(NSFileType) == NSFileTypeRegular
    override fun entries(): List<RecitationCacheEntry> = manager.contentsOfDirectoryAtPath(directory, null).orEmpty()
        .mapNotNull { item ->
            val name = item as? String ?: return@mapNotNull null
            val path = "$directory/$name"
            val attributes = manager.attributesOfItemAtPath(path, null) ?: return@mapNotNull null
            if (attributes[NSFileType] != NSFileTypeRegular) return@mapNotNull null
            val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: return@mapNotNull null
            RecitationCacheEntry(name, size).takeIf { RecitationCacheMetadata.parse(it) != null }
        }.sortedBy { it.key }
    override fun read(key: String): ByteArray? {
        val path = path(key)
        if (!regular(path)) return null
        val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: return null
        val data = try { handle.readDataOfLength((MAX_RECITATION_BYTES + 1).toULong()) } finally { handle.closeFile() }
        if (data.length == 0uL || data.length > MAX_RECITATION_BYTES.toULong()) return null
        return ByteArray(data.length.toInt()).apply {
            usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        }
    }
    override fun localUri(key: String): String? = path(key).takeIf {
        regular(it) &&
            ((manager.attributesOfItemAtPath(it, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L) in 1..MAX_RECITATION_BYTES.toLong()
    }
        ?.let { NSURL.fileURLWithPath(it).absoluteString }
    override suspend fun writeAtomic(key: String, content: ByteArray): String {
        require(content.size in 1..MAX_RECITATION_BYTES)
        val final = path(key)
        check(manager.createDirectoryAtPath(directory, true, null, null)) { "Could not create recitation storage" }
        val temporary = "$directory/recitation-${NSUUID().UUIDString}.part"
        try {
            currentCoroutineContext().ensureActive()
            val data = content.usePinned { NSData.create(bytes = it.addressOf(0), length = content.size.toULong()) }
            check(data.writeToFile(temporary, false)) { "Could not write recitation" }
            currentCoroutineContext().ensureActive()
            check(rename(temporary, final) == 0) { "Could not finalize recitation" }
            return checkNotNull(localUri(key))
        } finally { manager.removeItemAtPath(temporary, null) }
    }
    override fun remove(key: String) {
        val path = path(key)
        check(!manager.fileExistsAtPath(path) || manager.removeItemAtPath(path, null)) { "Could not remove cached recitation" }
    }
}
internal actual fun platformRecitationFileCache(): RecitationFileCache = IosRecitationFileCache(
    NSHomeDirectory() + "/Library/Application Support/Quran/recitations",
)

// Native background dispatcher performs Foundation file IO away from the UI thread.
internal actual suspend fun <T> recitationStorageWork(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }
