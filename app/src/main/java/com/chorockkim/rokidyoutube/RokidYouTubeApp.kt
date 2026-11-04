package com.chorockkim.rokidyoutube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.chorockkim.rokidyoutube.net.OkHttpDownloader
import com.chorockkim.rokidyoutube.potoken.PoTokenProviderImpl
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

class RokidYouTubeApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        instance = this

        NewPipe.init(OkHttpDownloader.get(), Localization("ko", "KR"), ContentCountry("KR"))
        // Without a poToken YouTube rejects or throttles the stream URLs (same as RokidPipe).
        YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(this)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = OkHttpDownloader.get().client)) }
        .build()

    companion object {
        lateinit var instance: RokidYouTubeApp
            private set
    }
}
