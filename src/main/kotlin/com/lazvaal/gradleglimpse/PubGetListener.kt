package com.lazvaal.gradleglimpse

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class PubGetListener : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {

            override fun after(events: MutableList<out VFileEvent>) {
                for (event in events) {
                    val path = event.path

                    if (path.endsWith(".dart_tool/package_config.json")) {
                        DaemonCodeAnalyzer.getInstance(project).restart()
                        return
                    }
                }
            }
        })
    }
}