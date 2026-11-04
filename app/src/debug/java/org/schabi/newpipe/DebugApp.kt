package org.schabi.newpipe

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import java.lang.ref.WeakReference
import leakcanary.LeakCanary
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader

class DebugApp : App() {
    override fun onCreate() {
        super.onCreate()
        registerRokidDebugActivityTracker()
        initStetho()

        LeakCanary.config = LeakCanary.config.copy(
            dumpHeap = PreferenceManager
                .getDefaultSharedPreferences(this).getBoolean(
                    getString(
                        R.string.allow_heap_dumping_key
                    ),
                    false
                )
        )
    }

    override fun getDownloader(): Downloader {
        val downloader = DownloaderImpl.init(
            OkHttpClient.Builder()
                .addNetworkInterceptor(StethoInterceptor())
        )
        setCookiesToDownloader(downloader)
        return downloader
    }

    private fun initStetho() {
        // Create an InitializerBuilder
        val initializerBuilder = Stetho.newInitializerBuilder(this)

        // Enable Chrome DevTools
        initializerBuilder.enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))

        // Enable command line interface
        initializerBuilder.enableDumpapp(
            Stetho.defaultDumperPluginsProvider(applicationContext)
        )

        // Use the InitializerBuilder to generate an Initializer
        val initializer = initializerBuilder.build()

        // Initialize Stetho with the Initializer
        Stetho.initialize(initializer)
    }

    override fun isDisposedRxExceptionsReported(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(getString(R.string.allow_disposed_exceptions_key), false)
    }

    private fun registerRokidDebugActivityTracker() {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    currentActivity = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (currentActivity?.get() === activity) {
                        currentActivity = null
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {
                    if (currentActivity?.get() === activity) {
                        currentActivity = null
                    }
                }
            }
        )
    }

    companion object {
        private var currentActivity: WeakReference<Activity>? = null

        fun getCurrentActivity(): Activity? = currentActivity?.get()
    }
}
