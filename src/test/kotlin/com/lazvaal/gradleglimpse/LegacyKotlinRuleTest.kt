package com.lazvaal.gradleglimpse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegacyKotlinRuleTest {

    private val rule = LegacyKotlinRule()

    // We can pass a dummy file because our rule only checks the string content!
    private val dummyFile = File("dummy/path")

    @Test
    fun `test legacy apply plugin is flagged`() {
        val badGradleContent = """
            def localProperties = new Properties()
            apply plugin: 'kotlin-android'
            
            android { compileSdkVersion 34 }
        """.trimIndent()

        val violationFound = rule.hasViolation(dummyFile, badGradleContent, null)
        assertTrue("Rule should flag the legacy 'apply plugin' syntax", violationFound)
    }

    @Test
    fun `test legacy id block is flagged`() {
        val badGradleContent = """
            plugins {
                id 'com.android.library'
                id 'org.jetbrains.kotlin.android'
            }
        """.trimIndent()

        val violationFound = rule.hasViolation(dummyFile, badGradleContent, null)
        assertTrue("Rule should flag the legacy 'id' syntax", violationFound)
    }

    @Test
    fun `test modern gradle file passes cleanly`() {
        val goodGradleContent = """
            plugins {
                id 'com.android.library'
            }
            android {
                namespace 'com.example.modern'
                compileSdkVersion 34
            }
        """.trimIndent()

        val violationFound = rule.hasViolation(dummyFile, goodGradleContent, null)
        assertFalse("Rule should NOT flag a modern, headless plugin", violationFound)
    }
}