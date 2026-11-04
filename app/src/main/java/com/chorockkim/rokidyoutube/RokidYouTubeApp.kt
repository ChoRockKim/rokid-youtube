package com.chorockkim.rokidyoutube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.chorockkim.rokidyoutube.auth.GoogleAuth
import com.chorockkim.rokidyoutube.auth.PrefsTokenStore
import com.chorockkim.rokidyoutube.data.Recommendations
import com.chorockkim.rokidyoutube.data.VideoItem
import com.chorockkim.rokidyoutube.data.WatchHistory
import com.chorockkim.rokidyoutube.data.YouTubeAccountApi
import com.chorockkim.rokidyoutube.net.OkHttpDownloader
import java.io.File
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

    val auth by lazy {
        GoogleAuth(
            BuildConfig.GOOGLE_CLIENT_ID,
            BuildConfig.GOOGLE_CLIENT_SECRET,
            PrefsTokenStore(this),
            { OkHttpDownloader.get().client }
        )
    }
    val accountApi by lazy { YouTubeAccountApi(OkHttpDownloader.get().client) { auth.accessToken() } }
    val history by lazy { WatchHistory(File(filesDir, "watch_history.json")) }
    val recommendations by lazy {
        Recommendations(history, object : Recommendations.Account {
            override val isSignedIn get() = auth.isSignedIn
            override suspend fun likedVideos(): List<VideoItem> = accountApi.likedVideos()
            override suspend fun subscriptionUploads(): List<VideoItem> = accountApi.subscriptionFeed()
        })
    }

    companion object {
        lateinit var instance: RokidYouTubeApp
            private set
    }
}
