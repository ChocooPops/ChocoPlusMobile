package com.chocoplusmobile

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

class ChocoApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // Before anything builds a request: NetworkModule reads it on init.
        com.chocoplusmobile.api.ServerConfigHolder.load(this)
        com.chocoplusmobile.download.DownloadManager.init(this)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
}
