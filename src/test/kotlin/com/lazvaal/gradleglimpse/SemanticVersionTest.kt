package com.lazvaal.gradleglimpse

import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {

    @Test
    fun `test minor version double digits override single digits`() {
        val oldVersion = SemanticVersion("8.2.0")
        val newVersion = SemanticVersion("8.13.1")

        assertTrue("8.13.1 should mathematically be greater than 8.2.0", newVersion > oldVersion)
    }

    @Test
    fun `test major version upgrades`() {
        val agp8 = SemanticVersion("8.14.0")
        val agp9 = SemanticVersion("9.0.0")

        assertTrue("9.0.0 should be greater than 8.14.0", agp9 > agp8)
    }

    @Test
    fun `test string equality`() {
        val v1 = SemanticVersion("7.4.2")
        val v2 = SemanticVersion("7.4.2")

        assertTrue("Identical versions should compare equally", v1.compareTo(v2) == 0)
    }
}