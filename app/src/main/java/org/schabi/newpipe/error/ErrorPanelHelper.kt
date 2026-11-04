package org.schabi.newpipe.error

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.jakewharton.rxbinding4.view.clicks
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.rokid.RokidExternalNavigationHelper
import org.schabi.newpipe.rokid.RokidMode
import org.schabi.newpipe.util.external_communication.ShareUtils
import org.schabi.newpipe.util.text.setTextWithLinks

class ErrorPanelHelper(
    private val fragment: Fragment,
    rootView: View,
    onRetry: Runnable?
) {
    private val context: Context = rootView.context!!

    private val errorPanelRoot: View = rootView.findViewById(R.id.error_panel)

    // the only element that is visible by default
    private val errorTextView: TextView =
        errorPanelRoot.findViewById(R.id.error_message_view)
    private val errorServiceInfoTextView: TextView =
        errorPanelRoot.findViewById(R.id.error_message_service_info_view)
    private val errorServiceExplanationTextView: TextView =
        errorPanelRoot.findViewById(R.id.error_message_service_explanation_view)
    private val errorActionButton: Button =
        errorPanelRoot.findViewById(R.id.error_action_button)
    private val errorRetryButton: Button =
        errorPanelRoot.findViewById(R.id.error_retry_button)
    private val errorWifiSettingsButton: Button =
        errorPanelRoot.findViewById(R.id.error_wifi_settings_button)
    private val errorOpenInBrowserButton: Button =
        errorPanelRoot.findViewById(R.id.error_open_in_browser)

    private var errorDisposable: Disposable? = null
    private var retryShouldBeShown: Boolean = (onRetry != null)

    init {
        if (onRetry != null) {
            errorDisposable = errorRetryButton.clicks()
                .debounce(300, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { onRetry.run() }
        }
    }

    private fun ensureDefaultVisibility() {
        errorTextView.isVisible = true

        errorServiceInfoTextView.isVisible = false
        errorServiceExplanationTextView.isVisible = false
        errorActionButton.isVisible = false
        errorRetryButton.isVisible = false
        errorWifiSettingsButton.isVisible = false
        errorOpenInBrowserButton.isVisible = false
    }

    fun showError(errorInfo: ErrorInfo) {
        ensureDefaultVisibility()
        errorTextView.setTextWithLinks(errorInfo.getMessage(context))

        if (errorInfo.recaptchaUrl != null) {
            showAndSetErrorButtonAction(R.string.recaptcha_solve) {
                // Starting ReCaptcha Challenge Activity
                val intent = Intent(context, ReCaptchaActivity::class.java)
                intent.putExtra(ReCaptchaActivity.RECAPTCHA_URL_EXTRA, errorInfo.recaptchaUrl)
                fragment.startActivityForResult(intent, ReCaptchaActivity.RECAPTCHA_REQUEST)
                errorActionButton.setOnClickListener(null)
            }
        } else if (errorInfo.isReportable) {
            showAndSetErrorButtonAction(R.string.error_snackbar_action) {
                ErrorUtil.openActivity(context, errorInfo)
            }
        }

        if (errorInfo.isRetryable) {
            errorRetryButton.isVisible = retryShouldBeShown
        }

        if (RokidMode.enabled() && !hasInternetNetwork()) {
            errorWifiSettingsButton.isVisible = true
            errorWifiSettingsButton.setOnClickListener { openWifiSettings() }
        }

        if (!RokidMode.enabled() && errorInfo.openInBrowserUrl != null) {
            errorOpenInBrowserButton.isVisible = true
            errorOpenInBrowserButton.setOnClickListener {
                ShareUtils.openUrlInBrowser(context, errorInfo.openInBrowserUrl)
            }
        }

        setRootVisible()
    }

    /**
     * Shows the errorButtonAction, sets a text into it and sets the click listener.
     */
    private fun showAndSetErrorButtonAction(
        @StringRes resid: Int,
        listener: View.OnClickListener
    ) {
        errorActionButton.isVisible = true
        errorActionButton.setText(resid)
        errorActionButton.setOnClickListener(listener)
    }

    fun showTextError(errorString: String) {
        ensureDefaultVisibility()

        errorTextView.setTextWithLinks(errorString)

        setRootVisible()
    }

    private fun setRootVisible() {
        errorPanelRoot.animate(true, 300)
    }

    fun hide() {
        errorActionButton.setOnClickListener(null)
        errorWifiSettingsButton.setOnClickListener(null)
        errorPanelRoot.animate(false, 150)
    }

    fun isVisible(): Boolean {
        return errorPanelRoot.isVisible
    }

    fun dispose() {
        errorActionButton.setOnClickListener(null)
        errorRetryButton.setOnClickListener(null)
        errorWifiSettingsButton.setOnClickListener(null)
        errorDisposable?.dispose()
    }

    private fun hasInternetNetwork(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun openWifiSettings() {
        val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        RokidExternalNavigationHelper.openExternalActivity(
            context,
            wifiIntent,
            fallbackIntent
        )
    }

    companion object {
        val TAG: String = ErrorPanelHelper::class.simpleName!!
        val DEBUG: Boolean = MainActivity.DEBUG
    }
}
