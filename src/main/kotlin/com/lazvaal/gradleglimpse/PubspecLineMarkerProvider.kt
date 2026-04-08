package com.lazvaal.gradleglimpse // Ensure this matches your package name!

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
    private var isScanned = false

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        val file = element.containingFile as? YAMLFile ?: return
        if (file.name != "pubspec.yaml") return
        if (element !is YAMLKeyValue) return

        val basePath = element.project.basePath ?: return
        val packageConfig = File(basePath, ".dart_tool/package_config.json")

        // 1. The "No Cache" Edge Case
        if (!packageConfig.exists()) {
            // Attach a warning specifically to the "dependencies:" line
            if (element.keyText == "dependencies") {
                val marker = NavigationGutterIconBuilder.create(AllIcons.General.Warning)
                    .setTooltipText("<b>Gradle Glimpse:</b> Please run 'flutter pub get' to enable AGP compatibility scanning.")
                    .setTarget(element)
                    .createLineMarkerInfo(element)
                result.add(marker)
            }
            return
        }

        // 2. Normal Scanning Logic
        if (!isScanned) {
            val scanner = GradleGlimpseScanner(basePath)
            cachedResults = scanner.scanPlugins()
            isScanned = true
        }

        val packageName = element.keyText
        val pluginResult = cachedResults.find { it.pluginName == packageName && it.isDirect }

        if (pluginResult != null) {
            val hiddenDeps = cachedResults.filter { !it.isDirect && it.hasAndroid && it.requiredBy.contains(pluginResult.pluginName) }
            val tooltipHtml = buildTooltipHtml(pluginResult, hiddenDeps)

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

    private fun buildTooltipHtml(direct: GlimpseResult, hiddenDeps: List<GlimpseResult>): String {
        val sb = StringBuilder("<html><body style='padding: 5px;'>")
        val agpString = if (direct.hasAndroid) "AGP ${direct.agpVersion}" else "Pure Dart"
        val directIssues = if (!direct.hasAndroid) "✅ N/A" else if (direct.violations.isEmpty()) "✅ Clean" else "❌ Issues Detected"

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