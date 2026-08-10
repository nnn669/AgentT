package com.agentt

import android.app.Application

class AgentTApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AgentTApp
            private set
    }
}
