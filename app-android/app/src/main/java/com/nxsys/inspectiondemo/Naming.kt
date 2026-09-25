package com.nxsys.inspectiondemo

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Naming and text-format rules for the inspection folder. No Android dependencies. */
object Naming {

    const val INFO_FILE_NAME = "info.txt"
    const val MAX_SN_LENGTH = 100

    private val illegalChars = Regex("""[/\\:*?"<>|\p{Cntrl}]""")
    private val controlChars = Regex("""\p{Cntrl}""")
    private val fileTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val displayTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private const val FIRST_INSPECTED = "First inspected:"
    private const val NEWLINE = "\r\n" // Windows-friendly: files are read on a PC

    /** Returns a folder/file-safe SN, or null if nothing usable remains. */
    fun sanitizeSn(raw: String): String? {
        val safe = raw.trim().replace(illegalChars, "_").take(MAX_SN_LENGTH)
        if (safe.isEmpty() || safe.all { it == '.' }) return null
        return safe
    }

    fun photoFileName(sn: String, time: LocalDateTime, existingNames: Set<String>): String {
        val base = "${sn}_${time.format(fileTimeFormat)}"
        if ("$base.jpg" !in existingNames) return "$base.jpg"
        var i = 2
        while ("${base}_$i.jpg" in existingNames) i++
        return "${base}_$i.jpg"
    }

    fun formatTimestamp(time: LocalDateTime): String = time.format(displayTimeFormat)

    fun buildInfoTxt(
        rawSn: String,
        firstInspected: String,
        lastUpdated: String,
        photoNames: List<String>,
    ): String {
        val lines = buildList {
            add("Serial Number: ${rawSn.trim().replace(controlChars, " ")}")
            add("$FIRST_INSPECTED $firstInspected")
            add("Last updated: $lastUpdated")
            add("Photo count: ${photoNames.size}")
            add("Photos:")
            addAll(photoNames.sorted())
        }
        return lines.joinToString(NEWLINE, postfix = NEWLINE)
    }

    fun parseFirstInspected(infoTxt: String): String? =
        infoTxt.lines()
            .firstOrNull { it.startsWith(FIRST_INSPECTED) }
            ?.substringAfter(FIRST_INSPECTED)
            ?.trim()
            ?.ifEmpty { null }
}
