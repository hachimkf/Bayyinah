package com.bayyinah.app.book

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Keeps the imported Mushaf pack in the app's private storage. The pack comes from the user
 * (a file she was given), never from the app's own download or update channel.
 */
object MushafPackStore {
    private const val FILE_NAME = "mushaf-warsh.bayyinah-pack"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun isInstalled(context: Context) = file(context).isFile

    /** Loads the installed pack, or null when none is installed or it can't be read. */
    fun load(context: Context): MushafPack? {
        val f = file(context)
        if (!f.isFile) return null
        return try {
            f.inputStream().buffered().use { MushafPack.read(it) }
        } catch (e: Exception) {
            android.util.Log.e("Bayyinah", "Mushaf pack unreadable", e)
            null
        }
    }

    /**
     * Validates the picked file and installs it (replacing an older pack).
     * Returns the loaded pack; throws IllegalArgumentException with a short reason when the file isn't a pack.
     */
    fun import(context: Context, uri: Uri): MushafPack {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Can't open the file")
        require(bytes.size in 1..64_000_000) { "Unexpected file size" }
        val pack = MushafPack.read(bytes)          // throws if it isn't a valid pack
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        tmp.writeBytes(bytes)
        val dest = file(context)
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
        return pack
    }

    fun remove(context: Context) { file(context).delete() }
}
