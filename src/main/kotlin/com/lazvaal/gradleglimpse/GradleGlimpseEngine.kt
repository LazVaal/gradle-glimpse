package com.lazvaal.gradleglimpse

import java.io.File
import java.net.URI

// ==========================================
// 1. UTILITIES & DATA MODELS
// ==========================================

data class SemanticVersion(val version: String) : Comparable<SemanticVersion> {
    private val parts = version.split(".").map { it.toIntOrNull() ?: 0 }

    override fun compareTo(other: SemanticVersion): Int {
        val maxParts = maxOf(parts.size, other.parts.size)
        for (i in 0 until maxParts) {
            val v1 = parts.getOrElse(i) { 0 }
            val v2 = other.parts.getOrElse(i) { 0 }
            if (v1 != v2) return v1.compareTo(v2)
        }
        return 0
    }

    override fun toString() = version
}

data class RuleViolation(val rule: AgpRule)

data class GlimpseResult(
    val pluginName: String,
    val agpVersion: SemanticVersion,
    val hasAndroid: Boolean,
    val violations: List<RuleViolation>,
    val isDirect: Boolean,
    val requiredBy: List<String>
)

data class AnalysisResult(
    val allResults: List<GlimpseResult>,
    val uniqueFailingPlugins: Set<String>,
    val autoHealedIssues: Set<String>,
    val projectMaxAgpLevel: Int,
    val projectMaxAgpString: String,
    val highestDeclaredAgpVersion: SemanticVersion
)

// ==========================================
// 2. THE RULE ENGINE ARCHITECTURE
// ==========================================

interface AgpRule {
    val ruleId: String
    val maxSafeAgp: String
    val maxAgpLevel: Int
    val isAutoHealedByFlutter: Boolean

    fun hasViolation(pluginDir: File, buildGradleContent: String?, manifestContent: String?): Boolean
}

class LegacyKotlinRule : AgpRule {
    companion object {
        private val APPLY_REGEX = """apply\s+plugin:\s*['"]kotlin-android['"]""".toRegex()
        private val ID_REGEX = """id\s*\(?['"]org\.jetbrains\.kotlin\.android['"]\)?(.*)""".toRegex()
    }

    override val ruleId = "Legacy Kotlin"
    override val maxSafeAgp = "8.14.x"
    override val maxAgpLevel = 8
    override val isAutoHealedByFlutter = false

    override fun hasViolation(pluginDir: File, buildGradleContent: String?, manifestContent: String?): Boolean {
        if (buildGradleContent == null) return false
        return APPLY_REGEX.containsMatchIn(buildGradleContent) || ID_REGEX.containsMatchIn(buildGradleContent)
    }
}

class MissingNamespaceRule : AgpRule {
    override val ruleId = "Missing Namespace"
    override val maxSafeAgp = "7.4.x"
    override val maxAgpLevel = 7
    override val isAutoHealedByFlutter = false

    override fun hasViolation(pluginDir: File, buildGradleContent: String?, manifestContent: String?): Boolean {
        if (buildGradleContent == null) return false
        if (!buildGradleContent.contains("namespace") && manifestContent != null) {
            return manifestContent.contains("package=")
        }
        return false
    }
}

class OutdatedJavaVersionRule : AgpRule {
    override val ruleId = "Hardcoded Old Java (<= 11)"
    override val maxSafeAgp = "8.14.x"
    override val maxAgpLevel = 9
    override val isAutoHealedByFlutter = true

    override fun hasViolation(pluginDir: File, buildGradleContent: String?, manifestContent: String?): Boolean {
        if (buildGradleContent == null) return false
        return buildGradleContent.contains("VERSION_1_8") || buildGradleContent.contains("VERSION_1_11") ||
                buildGradleContent.contains("jvmTarget = '1.8'") || buildGradleContent.contains("jvmTarget = \"1.8\"")
    }
}

// ==========================================
// 3. THE SCANNER ENGINE
// ==========================================

class GradleGlimpseScanner(
    private val projectRootPath: String,
    private val activeRules: List<AgpRule> = listOf(LegacyKotlinRule(), MissingNamespaceRule(), OutdatedJavaVersionRule())
) {
    fun scanPlugins(): List<GlimpseResult> {
        val projectRoot = File(projectRootPath)
        val packageConfigFile = File(projectRoot, ".dart_tool/package_config.json")
        val rootPubspec = File(projectRoot, "pubspec.yaml")

        if (!packageConfigFile.exists()) {
            println("Error: Could not find .dart_tool/package_config.json.")
            return emptyList()
        }

        val directDeps = """^\s+([a-z0-9_]+)\s*:""".toRegex(RegexOption.MULTILINE)
            .findAll(if (rootPubspec.exists()) rootPubspec.readText() else "")
            .map { it.groupValues[1] }
            .toSet()

        val jsonContent = packageConfigFile.readText()
        val pluginDirs = """"name"\s*:\s*"([^"]+)",\s*"rootUri"\s*:\s*"([^"]+)"""".toRegex()
            .findAll(jsonContent)
            .filter { it.groupValues[1] != projectRoot.name }
            .associate { it.groupValues[1] to resolveUri(projectRoot, it.groupValues[2]) }

        // O(1) Pre-compiled Dependency Map
        val dependencyMap = pluginDirs.mapValues { (_, dir) ->
            val pubspec = File(dir, "pubspec.yaml")
            if (pubspec.exists()) extractDeps(pubspec.readText()) else emptySet()
        }

        val results = mutableListOf<GlimpseResult>()

        for ((name, pluginDir) in pluginDirs) {
            val androidDir = File(pluginDir, "android")
            val buildGradle = File(androidDir, "build.gradle").takeIf { it.exists() }
                ?: File(androidDir, "build.gradle.kts").takeIf { it.exists() }
            val manifestFile = File(androidDir, "src/main/AndroidManifest.xml").takeIf { it.exists() }

            val isDirect = directDeps.contains(name)
            val requiredBy = if (isDirect) emptyList() else dependencyMap.filter { it.value.contains(name) }.keys.toList()

            if (androidDir.exists() && buildGradle != null) {
                // Avoid redundant file reads
                val gradleContent = buildGradle.readText()
                val manifestContent = manifestFile?.takeIf { it.exists() }?.readText()
                val agpVersion = SemanticVersion(extractAgpVersionString(gradleContent))

                val violations = activeRules.filter { rule ->
                    rule.hasViolation(pluginDir, gradleContent, manifestContent)
                }.map { RuleViolation(it) }

                results.add(GlimpseResult(name, agpVersion, true, violations, isDirect, requiredBy))
            } else {
                results.add(GlimpseResult(name, SemanticVersion("0.0.0"), false, emptyList(), isDirect, requiredBy))
            }
        }
        return results // Sorting delegated to Reporter
    }

    private fun extractDeps(pubspecContent: String): Set<String> {
        val depsRegex = Regex("""\n\s+([a-z0-9_]+)\s*:""")
        return depsRegex.findAll(pubspecContent).map { it.groupValues[1] }.toSet()
    }

    private fun resolveUri(baseDir: File, uriStr: String): File {
        return if (uriStr.startsWith("file://")) File(URI(uriStr))
        else File(baseDir, ".dart_tool").resolve(uriStr).normalize()
    }

    private fun extractAgpVersionString(gradleContent: String): String {
        val match = """com\.android\.tools\.build:gradle:([0-9.]+)""".toRegex().find(gradleContent)
        return match?.groupValues?.get(1) ?: "Unknown/Inherited"
    }
}