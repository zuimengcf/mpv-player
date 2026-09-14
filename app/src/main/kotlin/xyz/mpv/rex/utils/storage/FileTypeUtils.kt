package xyz.mpv.rex.utils.storage

import java.io.File
import java.util.Locale

/**
 * File Type Utilities
 * Handles file type detection
 */
object FileTypeUtils {

    // Video file extensions
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "3g2",
        "mpg", "mpeg", "m2v", "ogv", "ts", "mts", "m2ts", "vob", "divx", "xvid",
        "f4v", "rm", "rmvb", "asf"
    )

    // Custom video extensions added by the user at runtime (parsed from preferences)
    @Volatile
    private var customVideoExtensions: Set<String> = emptySet()

    /**
     * Returns the effective video extension set (built-in + custom).
     * Used by callers that need the full list (e.g. mime detection fallback).
     */
    fun effectiveVideoExtensions(): Set<String> = VIDEO_EXTENSIONS + customVideoExtensions

    /**
     * Parses a raw extension string into a clean set.
     * @param raw comma-separated string like "iso,bin,dat" (dots/spaces/uppercase tolerated)
     */
    fun parseExtensions(raw: String): Set<String> = raw
        .split(',', '，', ';', '、', ' ')
        .map { it.trim().removePrefix(".").lowercase(Locale.getDefault()) }
        .filter { it.isNotEmpty() }
        .toSet()

    /**
     * Updates the custom video extension set.
     * @param raw comma-separated string like "iso,bin,dat" (dots/spaces/uppercase tolerated)
     */
    fun setCustomVideoExtensions(raw: String) {
        customVideoExtensions = parseExtensions(raw)
    }

    // Audio file extensions
    val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "m4a", "ogg", "opus", "wma", "aac", "aiff", "alac",
        "dsd", "dff", "dsf", "pcm", "mka", "oga"
    )

    // Subtitle file extensions
    val SUBTITLE_EXTENSIONS = setOf(
        "srt", "ass", "ssa", "vtt", "sub", "idx"
    )

    /**
     * Checks if a file is a video based on extension (built-in + custom)
     */
    fun isVideoFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return VIDEO_EXTENSIONS.contains(extension) || customVideoExtensions.contains(extension)
    }

    /**
     * Checks if a file is an audio file based on extension
     */
    fun isAudioFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return AUDIO_EXTENSIONS.contains(extension)
    }

    /**
     * Checks if a file is a subtitle file based on extension
     */
    fun isSubtitleFile(file: File): Boolean {
        val extension = file.extension.lowercase(Locale.getDefault())
        return SUBTITLE_EXTENSIONS.contains(extension)
    }

    /**
     * Checks if a file is either video or audio
     */
    fun isMediaFile(file: File): Boolean {
        return isVideoFile(file) || isAudioFile(file)
    }

    /**
     * Gets MIME type from file extension
     */
    fun getMimeTypeFromExtension(extension: String): String =
        when (val ext = extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "m4v" -> "video/x-m4v"
            "3gp" -> "video/3gpp"
            "mpg", "mpeg" -> "video/mpeg"
            // Audio
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg", "opus", "oga" -> "audio/ogg"
            "mka" -> "audio/x-matroska"
            else -> if (VIDEO_EXTENSIONS.contains(ext) || customVideoExtensions.contains(ext)) "video/*" else if (AUDIO_EXTENSIONS.contains(ext)) "audio/*" else "*/*"
        }
}
