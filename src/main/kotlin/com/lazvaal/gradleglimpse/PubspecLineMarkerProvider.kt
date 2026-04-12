package com.lazvaal.gradleglimpse

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import java.io.File

class PubspecLineMarkerProvider : RelatedItemLineMarkerProvider() {

    private var cachedResults: List<GlimpseResult> = emptyList()
    private var lastConfigModTime: Long = -1L
    private var lastPubspecModTime: Long = -1L

    private data class ProjectSummary(
        val projectMaxAgpLevel: Int,
        val projectMaxAgpString: String,
        val highestDeclaredAgpVersion: SemanticVersion,
        val autoHealedIssues: Set<String>
    )

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        if (!GradleGlimpseSettings.instance.state.showIcons) return

        val file = element.containingFile as? YAMLFile ?: return
        if (file.name != "pubspec.yaml") return
        if (element !is YAMLKeyValue) return

        val basePath = element.project.basePath ?: return
        val packageConfig = File(basePath, ".dart_tool/package_config.json")
        val pubspecFile = File(basePath, "pubspec.yaml")

        if (!packageConfig.exists()) {
            if (element.keyText == "dependencies") {
                val marker = NavigationGutterIconBuilder.create(AllIcons.General.Warning)
                    .setTooltipText("<b>Gradle Glimpse:</b> Please run 'flutter pub get' to enable AGP compatibility scanning.")
                    .setTarget(element)
                    .createLineMarkerInfo(element)
                result.add(marker)
            }
            return
        }

        val currentConfigTime = packageConfig.lastModified()
        val currentPubspecTime = pubspecFile.lastModified()

        if (cachedResults.isEmpty() || currentConfigTime != lastConfigModTime || currentPubspecTime != lastPubspecModTime) {
            val scanner = GradleGlimpseScanner(basePath)
            cachedResults = scanner.scanPlugins()

            lastConfigModTime = currentConfigTime
            lastPubspecModTime = currentPubspecTime
        }

        if (element.keyText == "dependencies") {
            val summary = analyzeProject(cachedResults)
            val tooltipHtml = buildSummaryHtml(summary)

            val marker = NavigationGutterIconBuilder.create(AllIcons.General.Information)
                .setTooltipText(tooltipHtml)
                .setTarget(element)
                .createLineMarkerInfo(element)
            result.add(marker)
            return
        }

        val packageName = element.keyText
        val pluginResult = cachedResults.find { it.pluginName == packageName && it.isDirect }

        if (pluginResult != null) {
            val hiddenDeps = cachedResults.filter { !it.isDirect && it.hasAndroid && it.requiredBy.contains(pluginResult.pluginName) }
            val tooltipHtml = buildPluginHtml(pluginResult, hiddenDeps)

            val hasDirectViolations = pluginResult.violations.isNotEmpty()
            val hasHiddenViolations = hiddenDeps.any { it.violations.isNotEmpty() }

            val icon = if (hasDirectViolations || hasHiddenViolations) {
                AllIcons.General.Error
            } else {
                AllIcons.General.InspectionsOK
            }

            val marker = NavigationGutterIconBuilder.create(icon)
                .setTooltipText(tooltipHtml)
                .setTarget(element)
                .createLineMarkerInfo(element)

            result.add(marker)
        }
    }

    private fun analyzeProject(results: List<GlimpseResult>): ProjectSummary {
        var projectMaxAgpLevel = 9
        var projectMaxAgpString = "9.1.x (Latest)"
        var highestDeclaredAgpVersion = SemanticVersion("7.0.0")
        val autoHealedIssues = mutableSetOf<String>()

        for (plugin in results) {
            if (!plugin.hasAndroid) continue

            if (plugin.agpVersion.version != "Unknown/Inherited" && plugin.agpVersion > highestDeclaredAgpVersion) {
                highestDeclaredAgpVersion = plugin.agpVersion
            }

            for (violation in plugin.violations) {
                val rule = violation.rule
                if (rule.isAutoHealedByFlutter) {
                    autoHealedIssues.add(rule.ruleId)
                } else if (rule.maxAgpLevel < projectMaxAgpLevel) {
                    projectMaxAgpLevel = rule.maxAgpLevel
                    projectMaxAgpString = rule.maxSafeAgp
                }
            }
        }
        return ProjectSummary(projectMaxAgpLevel, projectMaxAgpString, highestDeclaredAgpVersion, autoHealedIssues)
    }

    private fun buildSummaryHtml(summary: ProjectSummary): String {
        val minAgpDisplay = if (summary.highestDeclaredAgpVersion.version == "7.0.0") "Inherited from App" else summary.highestDeclaredAgpVersion.version

        val sb = StringBuilder("<html><body style='padding: 5px; width: 450px;'>")
        sb.append("<b>📊 Gradle Glimpse Capability Range:</b><br/>")
        sb.append("<ul>")
        sb.append("<li><b>Recommended Minimum AGP:</b> $minAgpDisplay <i>(Based on your oldest strict plugins)</i></li>")
        sb.append("<li><b>Absolute Maximum Safe AGP:</b> ${summary.projectMaxAgpString}</li>")
        sb.append("<li><b>Safe Upgrade Range:</b> [ $minAgpDisplay &rarr; ${summary.projectMaxAgpString} ]</li>")
        sb.append("</ul>")

        sb.append("<b>💡 Technical Insights:</b><br/>")
        if (summary.autoHealedIssues.isNotEmpty()) {
            sb.append("<span style='color: #E6A23C;'>&nbsp;&nbsp;Flutter Auto-Healing Active: Issues like [${summary.autoHealedIssues.joinToString()}] were detected.</span><br/>")
            sb.append("<i>&nbsp;&nbsp;(Note: Flutter patches these at runtime for now, but these plugins are technically legacy and may break in AGP 10+)</i><br/><br/>")
        } else {
            sb.append("&nbsp;&nbsp;All native dependencies are handling their own configurations natively. No critical legacy interventions required by Flutter.<br/><br/>")
        }

        sb.append("<b>⚖️ Verdict:</b> ")
        when (summary.projectMaxAgpLevel) {
            7 -> sb.append("<span style='color: red;'>You are completely blocked from modern AGP versions. You must resolve the 'Missing Namespace' issues before you can move off AGP 7.x.</span>")
            8 -> sb.append("<span style='color: #E6A23C;'>You can safely update to the top of the AGP 8 series (like 8.14.x), but the 'Legacy Kotlin' issues block you from moving to AGP 9.0+.</span>")
            9 -> sb.append("<span style='color: #67C23A;'>Your project is fully modernized! You can confidently update your root build.gradle to target AGP 9.0+.</span>")
        }

        sb.append("</body></html>")
        return sb.toString()
    }

    private fun buildPluginHtml(direct: GlimpseResult, hiddenDeps: List<GlimpseResult>): String {
        val sb = StringBuilder("<html><body style='padding: 5px;'>")
        val agpString = if (direct.hasAndroid) "AGP ${direct.agpVersion}" else "Pure Dart"
        val directIssues = if (direct.violations.isEmpty()) "✅ Clean" else "❌ Issues Detected"

        sb.append("<b>📦 ${direct.pluginName} ($agpString) - $directIssues</b><br/>")
        if (direct.violations.isNotEmpty()) {
            sb.append("<span style='color: red;'>Violations: ${direct.violations.joinToString { it.rule.ruleId }}</span><br/>")
        }

        if (hiddenDeps.isNotEmpty()) {
            sb.append("<hr/><b>Hidden Android Dependencies:</b><br/>")
            for (hidden in hiddenDeps.sortedBy { it.pluginName }) {
                val hiddenIssues = if (hidden.violations.isEmpty()) "✅ Clean" else "❌ Issues"
                sb.append("&nbsp;&nbsp;↳ 🧩 ${hidden.pluginName} (AGP ${hidden.agpVersion}) - $hiddenIssues<br/>")
                if (hidden.violations.isNotEmpty()) {
                    sb.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<span style='color: red;'>└ ${hidden.violations.joinToString { it.rule.ruleId }}</span><br/>")
                }
            }
        }
        sb.append("</body></html>")
        return sb.toString()
    }
}