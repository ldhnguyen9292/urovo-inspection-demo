package com.nxsys.inspectiondemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class NamingTest {

    private val t = LocalDateTime.of(2026, 9, 25, 14, 30, 12)

    @Test
    fun sanitizeSn_keepsNormalSerial() {
        assertEquals("SN12345", Naming.sanitizeSn("SN12345"))
    }

    @Test
    fun sanitizeSn_trimsScannerSuffix() {
        assertEquals("SN12345", Naming.sanitizeSn("  SN12345\r\n"))
    }

    @Test
    fun sanitizeSn_replacesIllegalCharacters() {
        assertEquals("AB_12_3_4_5_6_7_8_9", Naming.sanitizeSn("AB/12\\3:4*5?6\"7<8>9"))
        assertEquals("A_B", Naming.sanitizeSn("A|B"))
        assertEquals("A_B", Naming.sanitizeSn("A\tB"))
    }

    @Test
    fun sanitizeSn_urlQrCodeBecomesSingleFolderName() {
        assertEquals("https___example.com_p_1", Naming.sanitizeSn("https://example.com/p/1"))
    }

    @Test
    fun sanitizeSn_rejectsBlankAndDotsOnly() {
        assertNull(Naming.sanitizeSn(""))
        assertNull(Naming.sanitizeSn("   \r\n"))
        assertNull(Naming.sanitizeSn("."))
        assertNull(Naming.sanitizeSn(".."))
    }

    @Test
    fun sanitizeSn_truncatesLongValues() {
        val result = Naming.sanitizeSn("X".repeat(500))!!
        assertEquals(Naming.MAX_SN_LENGTH, result.length)
    }

    @Test
    fun photoFileName_usesSnAndTimestamp() {
        assertEquals("SN1_20260925_143012.jpg", Naming.photoFileName("SN1", t, emptySet()))
    }

    @Test
    fun photoFileName_addsSuffixOnCollision() {
        val existing = setOf("SN1_20260925_143012.jpg", "SN1_20260925_143012_2.jpg")
        assertEquals("SN1_20260925_143012_3.jpg", Naming.photoFileName("SN1", t, existing))
    }

    @Test
    fun formatTimestamp_isHumanReadable() {
        assertEquals("2026-09-25 14:30:12", Naming.formatTimestamp(t))
    }

    @Test
    fun buildInfoTxt_matchesSpecFormat() {
        val text = Naming.buildInfoTxt(
            rawSn = "SN12345",
            firstInspected = "2026-09-25 14:30:12",
            lastUpdated = "2026-09-25 14:30:45",
            photoNames = listOf("SN12345_20260925_143045.jpg", "SN12345_20260925_143012.jpg"),
        )
        val expected = listOf(
            "Serial Number: SN12345",
            "First inspected: 2026-09-25 14:30:12",
            "Last updated: 2026-09-25 14:30:45",
            "Photo count: 2",
            "Photos:",
            "SN12345_20260925_143012.jpg",
            "SN12345_20260925_143045.jpg",
            "",
        ).joinToString("\r\n")
        assertEquals(expected, text)
    }

    @Test
    fun buildInfoTxt_keepsRawSnOnOneLine() {
        val text = Naming.buildInfoTxt(" AB/1\r\n", "a", "b", emptyList())
        assertEquals("Serial Number: AB/1", text.lines().first())
    }

    @Test
    fun parseFirstInspected_roundTrips() {
        val text = Naming.buildInfoTxt("SN1", "2026-09-25 14:30:12", "2026-09-26 09:00:00", emptyList())
        assertEquals("2026-09-25 14:30:12", Naming.parseFirstInspected(text))
    }

    @Test
    fun parseFirstInspected_returnsNullWhenMissing() {
        assertNull(Naming.parseFirstInspected("garbage\nmore garbage"))
        assertNull(Naming.parseFirstInspected("First inspected:   "))
    }
}
