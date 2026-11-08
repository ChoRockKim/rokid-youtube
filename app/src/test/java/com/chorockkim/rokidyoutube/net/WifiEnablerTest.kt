package com.chorockkim.rokidyoutube.net

import com.chorockkim.rokidyoutube.net.WifiEnabler.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiEnablerTest {
    @Test
    fun wifiStateComesBeforeTheInternetNotice() {
        assertEquals("Wi-Fi 켜는 중…", WifiEnabler.label(Status.ENABLING, online = false))
        assertEquals("Wi-Fi 꺼짐 · 도우미 앱 없음", WifiEnabler.label(Status.NO_HELPER, online = false))
        assertEquals("Wi-Fi 연결 기다리는 중", WifiEnabler.label(Status.WAITING_FOR_NETWORK, online = false))
        assertEquals("인터넷 연결 끊김", WifiEnabler.label(Status.CONNECTED, online = false))
        assertNull(WifiEnabler.label(Status.CONNECTED, online = true))
        assertNull(WifiEnabler.label(null, online = true))
    }
}
