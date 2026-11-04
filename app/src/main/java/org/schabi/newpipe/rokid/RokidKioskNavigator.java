package org.schabi.newpipe.rokid;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RokidKioskNavigator {
    private static final String LIVE_KIOSK_ID = "live";
    private static final String SEARCH_ACTION_ID = "__rokid_search__";
    private static final String BROKEN_COMBINED_TRENDING_KIOSK_ID = "Trending";

    private static final String[] PREFERRED_VIDEO_KIOSKS = {
            "trending_gaming",
            "trending_movies_and_shows",
            "New & hot",
            "Recently added",
            "Top 50",
            "Trending"
    };

    private static final String[] PREFERRED_TOP_BAR_KIOSKS = {
            "trending_gaming",
            "trending_music",
            "trending_movies_and_shows",
            "trending_podcasts_episodes",
            "New & hot",
            "Recently added",
            "Top 50",
            "Featured",
            "Radio"
    };

    private RokidKioskNavigator() {
    }

    public static void open(@NonNull final Fragment fragment, final boolean live)
            throws ExtractionException {
        final StreamingService service = ServiceHelper.getSelectedService(
                fragment.requireContext());
        final String kioskId = findKiosk(service, live);
        if (service == null || kioskId == null) {
            return;
        }
        NavigationHelper.openKioskFragment(fragment.getParentFragmentManager(),
                service.getServiceId(), kioskId);
    }

    public static void open(@NonNull final Fragment fragment, @NonNull final String kioskId)
            throws ExtractionException {
        final StreamingService service = ServiceHelper.getSelectedService(
                fragment.requireContext());
        if (service == null) {
            return;
        }
        NavigationHelper.openKioskFragment(fragment.getParentFragmentManager(),
                service.getServiceId(), kioskId);
    }

    @Nullable
    public static String findKiosk(@Nullable final StreamingService service, final boolean live)
            throws ExtractionException {
        if (service == null) {
            return null;
        }

        final Set<String> kiosks = service.getKioskList().getAvailableKiosks();
        if (live) {
            return kiosks.contains(LIVE_KIOSK_ID) ? LIVE_KIOSK_ID : null;
        }

        return findVideoKiosk(kiosks);
    }

    @NonNull
    public static List<KioskNavItem> getTopBarItems(@NonNull final Context context)
            throws ExtractionException {
        final StreamingService service = ServiceHelper.getSelectedService(context);
        if (service == null) {
            return Collections.emptyList();
        }

        final Set<String> kiosks = service.getKioskList().getAvailableKiosks();
        return findTopBarItems(kiosks, kioskId -> getTopBarLabel(context, kioskId),
                context.getString(R.string.search));
    }

    @Nullable
    static String findVideoKioskForTesting(@NonNull final Set<String> kiosks) {
        return findVideoKiosk(kiosks);
    }

    @NonNull
    static List<KioskNavItem> findTopBarItemsForTesting(@NonNull final Set<String> kiosks) {
        return findTopBarItems(kiosks, kioskId -> kioskId, "Search");
    }

    @Nullable
    private static String findVideoKiosk(@NonNull final Set<String> kiosks) {
        for (final String kioskId : PREFERRED_VIDEO_KIOSKS) {
            if (kiosks.contains(kioskId) && isVideoKiosk(kioskId)) {
                return kioskId;
            }
        }
        for (final String kioskId : kiosks) {
            if (isVideoKiosk(kioskId)) {
                return kioskId;
            }
        }
        return null;
    }

    private static boolean isVideoKiosk(@NonNull final String kioskId) {
        return !LIVE_KIOSK_ID.equals(kioskId)
                && !"trending_music".equals(kioskId)
                && !"trending_podcasts_episodes".equals(kioskId);
    }

    @NonNull
    private static List<KioskNavItem> findTopBarItems(
            @NonNull final Set<String> kiosks,
            @NonNull final LabelProvider labelProvider,
            @NonNull final String searchLabel
    ) {
        final ArrayList<KioskNavItem> items = new ArrayList<>();
        items.add(KioskNavItem.search(searchLabel));

        final HashSet<String> addedCategoryKiosks = new HashSet<>();
        for (final String kioskId : PREFERRED_TOP_BAR_KIOSKS) {
            addKioskIfAvailable(items, kiosks, addedCategoryKiosks, kioskId, labelProvider);
        }
        for (final String kioskId : kiosks) {
            addKioskIfAvailable(items, kiosks, addedCategoryKiosks, kioskId, labelProvider);
        }

        return items;
    }

    private static void addKioskIfAvailable(
            @NonNull final List<KioskNavItem> items,
            @NonNull final Set<String> kiosks,
            @NonNull final Set<String> addedCategoryKiosks,
            @NonNull final String kioskId,
            @NonNull final LabelProvider labelProvider
    ) {
        if (!kiosks.contains(kioskId) || LIVE_KIOSK_ID.equals(kioskId)
                || BROKEN_COMBINED_TRENDING_KIOSK_ID.equals(kioskId)
                || !addedCategoryKiosks.add(kioskId)) {
            return;
        }
        items.add(KioskNavItem.kiosk(kioskId, labelProvider.labelFor(kioskId)));
    }

    private interface LabelProvider {
        String labelFor(String kioskId);
    }

    @NonNull
    private static String getTopBarLabel(
            @NonNull final Context context,
            @NonNull final String kioskId
    ) {
        switch (kioskId) {
            case "trending_gaming":
                return context.getString(R.string.rokid_kiosk_gaming);
            case "trending_music":
                return context.getString(R.string.rokid_kiosk_music);
            case "trending_movies_and_shows":
                return context.getString(R.string.rokid_kiosk_movies);
            case "trending_podcasts_episodes":
                return context.getString(R.string.rokid_kiosk_podcasts);
            case "New & hot":
                return context.getString(R.string.rokid_kiosk_new_hot);
            case "Recently added":
                return context.getString(R.string.rokid_kiosk_recent);
            case "Top 50":
                return context.getString(R.string.rokid_kiosk_top);
            case "Featured":
                return context.getString(R.string.rokid_kiosk_featured);
            case "Radio":
                return context.getString(R.string.rokid_kiosk_radio);
            default:
                return KioskTranslator.getTranslatedKioskName(kioskId, context);
        }
    }

    public static final class KioskNavItem {
        private final String kioskId;
        private final String label;
        private final boolean searchAction;

        private KioskNavItem(
                @NonNull final String kioskId,
                @NonNull final String label,
                final boolean searchAction
        ) {
            this.kioskId = kioskId;
            this.label = label;
            this.searchAction = searchAction;
        }

        @NonNull
        private static KioskNavItem kiosk(
                @NonNull final String kioskId,
                @NonNull final String label
        ) {
            return new KioskNavItem(kioskId, label, false);
        }

        @NonNull
        private static KioskNavItem search(@NonNull final String label) {
            return new KioskNavItem(SEARCH_ACTION_ID, label, true);
        }

        @NonNull
        public String getKioskId() {
            return kioskId;
        }

        @NonNull
        public String getLabel() {
            return label;
        }

        public boolean isSearchAction() {
            return searchAction;
        }
    }
}
