package com.lazvaal.gradleglimpse

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@State(
    name = "GradleGlimpseSettings",
    storages = [Storage("GradleGlimpse.xml")]
)
@Service(Service.Level.APP)
class GradleGlimpseSettings : PersistentStateComponent<GradleGlimpseSettings.State> {

    class State {
        var showIcons: Boolean = true // Default to true so users see it on first install!
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        val instance: GradleGlimpseSettings
            get() = ApplicationManager.getApplication().getService(GradleGlimpseSettings::class.java)
    }
}