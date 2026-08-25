package com.cutequran.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

data class Verse(
    val surah: Int,
    val ayah: Int,
    val arabic: String,
    val english: String,
    val surahArabic: String,
    val surahEnglish: String,
    val surahMeaning: String
) {
    /** e.g. "Al-Baqara 2:255" */
    val reference: String get() = "$surahEnglish $surah:$ayah"

    /** The ayah as plain text, the way it goes to the clipboard or a share sheet. */
    val shareText: String get() = "$arabic\n\n\u201C$english\u201D\n\n\u2014 $reference"
}

/**
 * Reads verses straight off the bundled asset instead of holding the whole Quran in
 * memory — the widget and the service both need a verse now and then, and neither is
 * worth 2 MB of heap.
 */
object Quran {

    const val TOTAL_VERSES = 6236

    private var surahNames: Array<Array<String>>? = null

    private fun surahs(context: Context): Array<Array<String>> {
        surahNames?.let { return it }
        val rows = Array(115) { arrayOf("", "", "") }
        context.assets.open("surahs.txt").use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).forEachLine { line ->
                val p = line.split("|")
                if (p.size >= 4) {
                    val n = p[0].toIntOrNull() ?: return@forEachLine
                    if (n in 1..114) rows[n] = arrayOf(p[1], p[2], p[3])
                }
            }
        }
        surahNames = rows
        return rows
    }

    /** Verse at a 0-based index into the whole Quran, in order. */
    fun verseAt(context: Context, index: Int): Verse {
        val target = ((index % TOTAL_VERSES) + TOTAL_VERSES) % TOTAL_VERSES
        var line: String? = null
        context.assets.open("quran.txt").use { stream ->
            val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
            var i = 0
            while (true) {
                val l = reader.readLine() ?: break
                if (i == target) {
                    line = l
                    break
                }
                i++
            }
        }
        val parts = (line ?: "1|1||").split("|")
        val surah = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val names = surahs(context).getOrElse(surah) { arrayOf("", "", "") }
        return Verse(
            surah = surah,
            ayah = parts.getOrNull(1)?.toIntOrNull() ?: 1,
            arabic = parts.getOrNull(2).orEmpty(),
            english = parts.getOrNull(3).orEmpty(),
            surahArabic = names[0],
            surahEnglish = names[1],
            surahMeaning = names[2]
        )
    }

    fun randomVerse(context: Context): Verse = verseAt(context, Random.nextInt(TOTAL_VERSES))
}
