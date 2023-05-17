package com.example.mqtttest

import android.app.Application

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private var instance: MyApp? = null

        fun getContext(): MyApp {
            return instance!!
        }
    }
}