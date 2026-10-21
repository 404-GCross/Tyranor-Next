package com.core.nativeplugin

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest

/** Issue #111: old arm64 Vorbisfile compares zero/sign-extended Ogg serials as longs.
 * Publish a verified, immutable derivative before dlopen; never edit a mapped library or game.
 * Offsets below are FILE offsets, checked against the executable PT_LOAD in the bundled ELFs.
 * Only CMP Xn,Xm -> CMP Wn,Wm changes (sf bit); branches and signed seek offsets are untouched.
 */
object ArtemisVorbisRepair {
    private data class Repair(val source: String, val output: String, val sites: IntArray)

    private val repairs = listOf(
        Repair("cb2b58d8080b29529b27a8453f602646ab5f13ae5d4acc59b673c27c27e2d9cc",
            "258ce4eac42c0300d76355ab260962dd2cdcef486b39225ef1582b9400f73f9f", intArrayOf(
                0x4861a8, 0xeb0a017f.toInt(), 0x486cfc, 0xeb18017f.toInt(),
                0x48708c, 0xeb0b019f.toInt(), 0x487928, 0xeb08013f.toInt(),
                0x487e58, 0xeb19015f.toInt(), 0x487f90, 0xeb09015f.toInt(),
                0x48807c, 0xeb08017f.toInt())),
        Repair("9839e6fee3287ac7ad16149b49732b87b51276c3dff2a0633bdf7d2247807012",
            "2870de6d25e123f2a55eb2c064a8c57374177e0f27e6ec11649dde9cf375c2a1", intArrayOf(
                0x42ef08, 0xeb0a017f.toInt(), 0x42fa5c, 0xeb18017f.toInt(),
                0x42fdec, 0xeb0b019f.toInt(), 0x430688, 0xeb08013f.toInt(),
                0x430bb8, 0xeb19015f.toInt(), 0x430cf0, 0xeb09015f.toInt(),
                0x430ddc, 0xeb08017f.toInt())),
        Repair("75ffde0ad3a8c58fd82a3f797131686eb66ed7468a7db6452be7932718c44b35",
            "29866c0976e2334a01e4a659da0404470f0dfb6069582729b0e2cf2c77e5e1bd", intArrayOf(
                0x488314, 0xeb0a017f.toInt(), 0x488e68, 0xeb18017f.toInt(),
                0x4891f8, 0xeb0b019f.toInt(), 0x489a94, 0xeb08013f.toInt(),
                0x489fc4, 0xeb19015f.toInt(), 0x48a0fc, 0xeb09015f.toInt(),
                0x48a1e8, 0xeb08017f.toInt())),
        Repair("48e72b81092d30144af4a8362eb47f04c71d2e5f03539a9c5b39030d4f72b8ee",
            "d6004af17487a9f2fd3701b88de31c5f7d75836d54c687e61fae12888dfbc73e", intArrayOf(
                0x3983b4, 0xeb00027f.toInt(), 0x3983c8, 0xeb06027f.toInt(),
                0x3985c4, 0xeb00011f.toInt(), 0x3985e0, 0xeb06011f.toInt(),
                0x3988e0, 0xeb00009f.toInt(), 0x398900, 0xeb02009f.toInt(),
                0x3989b4, 0xeb00011f.toInt(), 0x3989c8, 0xeb05011f.toInt(),
                0x398dcc, 0xeb0000df.toInt(), 0x398dec, 0xeb0400df.toInt(),
                0x399a10, 0xeb06001f.toInt(), 0x399a28, 0xeb06007f.toInt(),
                0x39a958, 0xeb06001f.toInt(), 0x39a978, 0xeb06009f.toInt())),
    ).associateBy { it.source }

    /** Unknown/upstream-fixed/clean-room kernels pass through, never pattern-patched. */
    @JvmStatic
    @Synchronized
    @Throws(IOException::class)
    fun prepare(source: File, cache: File): File {
        if (!Files.isRegularFile(source.toPath(), NOFOLLOW_LINKS)) throw IOException("Invalid Artemis library")
        val sourceHash = sha256(source)
        val repair = repairs[sourceHash] ?: return source
        ensureDirectory(cache)
        val lock = File(cache, "vorbis-serial-v1.lock")
        if (Files.isSymbolicLink(lock.toPath())) throw IOException("Invalid repair lock")
        RandomAccessFile(lock, "rw").use { raf ->
            raf.channel.lock().use {
                val dir = File(cache, "vorbis-serial-v1-$sourceHash")
                ensureDirectory(dir)
                val output = File(dir, source.name)
                if (Files.exists(output.toPath(), NOFOLLOW_LINKS)) {
                    // Do not replace an inode another engine process may already have mapped.
                    if (!Files.isRegularFile(output.toPath(), NOFOLLOW_LINKS) || sha256(output) != repair.output) {
                        throw IOException("Corrupt Artemis repair cache")
                    }
                    if (!output.setReadOnly()) throw IOException("Cannot protect Artemis library")
                    return output
                }
                val temp = File.createTempFile(".vorbis-", ".so", dir)
                try {
                    // Android dynamic-code loading: make the inode read-only BEFORE writing
                    // through the already-open descriptor, then atomically publish it.
                    RandomAccessFile(temp, "rw").use { target ->
                        if (!temp.setReadOnly()) throw IOException("Cannot protect Artemis library")
                        source.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                target.write(buffer, 0, n)
                            }
                        }
                        if (sha256(temp) != sourceHash) throw IOException("Artemis library changed during repair")
                        for (i in repair.sites.indices step 2) {
                            target.seek(repair.sites[i].toLong())
                            val old = Integer.reverseBytes(target.readInt())
                            if (old != repair.sites[i + 1]) throw IOException("Unexpected Artemis instruction")
                            target.seek(repair.sites[i].toLong())
                            target.writeInt(Integer.reverseBytes(old and Int.MAX_VALUE))
                        }
                        target.fd.sync()
                    }
                    if (sha256(temp) != repair.output) throw IOException("Invalid repaired Artemis library")
                    Files.move(temp.toPath(), output.toPath(), ATOMIC_MOVE)
                    return output
                } finally {
                    Files.deleteIfExists(temp.toPath())
                }
            }
        }
    }

    private fun ensureDirectory(dir: File) {
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) throw IOException("Cannot create Artemis repair directory")
        // Parent aliases such as /data/user/0 and macOS /var are legitimate;
        // reject a redirected cache directory itself, not platform-owned ancestors.
        if (!Files.isDirectory(dir.toPath(), NOFOLLOW_LINKS)) {
            throw IOException("Invalid Artemis repair directory")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
