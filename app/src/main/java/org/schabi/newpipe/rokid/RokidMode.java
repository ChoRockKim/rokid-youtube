package org.schabi.newpipe.rokid;

import org.schabi.newpipe.BuildConfig;

public final class RokidMode {
    private RokidMode() {
    }

    public static boolean enabled() {
        return BuildConfig.ROKID_GLASS_MODE;
    }
}
