package com.lazvaal.gradleglimpse

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager

class ToggleGlimpseIconsAction : ToggleAction("Toggle Gradle Glimpse Icons") {

    override fun isSelected(e: AnActionEvent): Boolean {
        return GradleGlimpseSettings.instance.state.showIcons
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        GradleGlimpseSettings.instance.state.showIcons = state

        ApplicationManager.getApplication().invokeLater {
            for (project in ProjectManager.getInstance().openProjects) {
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }
    }
}