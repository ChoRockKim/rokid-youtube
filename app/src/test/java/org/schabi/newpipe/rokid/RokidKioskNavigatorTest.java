package org.schabi.newpipe.rokid;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RokidKioskNavigatorTest {
    @Test
    public void videosPreferGenericTrendingOverMusicEvenWhenMusicIsFirst() {
        assertEquals("Trending", RokidKioskNavigator.findVideoKioskForTesting(
                kiosks("trending_music", "Trending", "live")));
    }

    @Test
    public void videosFallbackToNonMusicVideoKiosk() {
        assertEquals("trending_gaming", RokidKioskNavigator.findVideoKioskForTesting(
                kiosks("trending_music", "trending_podcasts_episodes", "trending_gaming")));
    }

    @Test
    public void videosPreferWorkingYoutubeVideoCategoryOverBrokenGenericTrending() {
        assertEquals("trending_gaming", RokidKioskNavigator.findVideoKioskForTesting(kiosks(
                "live",
                "trending_podcasts_episodes",
                "trending_gaming",
                "trending_movies_and_shows",
                "trending_music",
                "Trending"
        )));
    }

    @Test
    public void videosDoNotOpenMusicOrPodcastsWhenNoVideoKiosk() {
        assertNull(RokidKioskNavigator.findVideoKioskForTesting(
                kiosks("live", "trending_music", "trending_podcasts_episodes")));
    }

    @Test
    public void topBarStartsWithSearchThenNewPipeSubTagsWithoutLiveOrBrokenTrending() {
        final List<RokidKioskNavigator.KioskNavItem> items =
                RokidKioskNavigator.findTopBarItemsForTesting(kiosks(
                        "live",
                        "trending_music",
                        "trending_gaming",
                        "trending_movies_and_shows",
                        "trending_podcasts_episodes",
                        "Trending"
                ));

        assertEquals(Arrays.asList(
                "__rokid_search__",
                "trending_gaming",
                "trending_music",
                "trending_movies_and_shows",
                "trending_podcasts_episodes"
        ), kioskIds(items));
    }

    @Test
    public void topBarKeepsSearchAndSubTagsEvenWithoutGenericVideosShortcut() {
        final List<RokidKioskNavigator.KioskNavItem> items =
                RokidKioskNavigator.findTopBarItemsForTesting(kiosks(
                        "live",
                        "trending_music",
                        "trending_podcasts_episodes"
                ));

        assertEquals(Arrays.asList(
                "__rokid_search__",
                "trending_music",
                "trending_podcasts_episodes"
        ), kioskIds(items));
    }

    private static Set<String> kiosks(final String... kioskIds) {
        return new LinkedHashSet<>(Arrays.asList(kioskIds));
    }

    private static List<String> kioskIds(
            final List<RokidKioskNavigator.KioskNavItem> items
    ) {
        final List<String> ids = new ArrayList<>();
        for (final RokidKioskNavigator.KioskNavItem item : items) {
            ids.add(item.getKioskId());
        }
        return ids;
    }
}
