package org.schabi.newpipe.player.ui;

import static org.schabi.newpipe.player.Player.STATE_BUFFERING;
import static org.schabi.newpipe.player.Player.STATE_COMPLETED;
import static org.schabi.newpipe.player.Player.STATE_PLAYING;

import android.view.View;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.rokid.RokidKeyMapper;
import org.schabi.newpipe.rokid.RokidMode;

import java.util.ArrayList;

final class RokidPlayerActionRailController {
    @NonNull
    private final VideoPlayerUi ui;
    @NonNull
    private final Player player;
    @NonNull
    private final PlayerBinding binding;
    private int actionIndex = -1;

    RokidPlayerActionRailController(@NonNull final VideoPlayerUi ui) {
        this.ui = ui;
        this.player = ui.player;
        this.binding = ui.binding;
    }

    void initListeners() {
        binding.rokidActionPlayPause.setOnClickListener(ui.makeOnClickListener(() -> {
            player.playPause();
            updatePlayPauseAction();
            binding.rokidActionPlayPause.postDelayed(this::update, 500);
        }));
        binding.rokidActionRewind.setOnClickListener(ui.makeOnClickListener(
                () -> seek(false)));
        binding.rokidActionForward.setOnClickListener(ui.makeOnClickListener(
                () -> seek(true)));
        binding.rokidActionFullscreen.setOnClickListener(ui.makeOnClickListener(
                ui::openRokidFullscreen));
        binding.rokidActionQuality.setOnClickListener(ui.makeOnClickListener(
                ui::onQualityClicked));
        binding.rokidActionSpeed.setOnClickListener(ui.makeOnClickListener(
                ui::onPlaybackSpeedClicked));
        binding.rokidActionCaptions.setOnClickListener(ui.makeOnClickListener(
                ui::onCaptionClicked));
        binding.rokidActionClose.setOnClickListener(ui.makeOnClickListener(
                ui::closeRokidPlayer));
    }

    void clearListeners() {
        binding.rokidActionPlayPause.setOnClickListener(null);
        binding.rokidActionRewind.setOnClickListener(null);
        binding.rokidActionForward.setOnClickListener(null);
        binding.rokidActionFullscreen.setOnClickListener(null);
        binding.rokidActionQuality.setOnClickListener(null);
        binding.rokidActionSpeed.setOnClickListener(null);
        binding.rokidActionCaptions.setOnClickListener(null);
        binding.rokidActionClose.setOnClickListener(null);
    }

    void configure() {
        actionIndex = -1;
        clearSelection();
        binding.rokidActionRail.setVisibility(View.GONE);
        binding.playbackSeekBar.setFocusable(false);
        binding.playbackSeekBar.setFocusableInTouchMode(false);
        binding.playbackSeekBar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        binding.playPreviousButton.setVisibility(View.GONE);
        binding.playNextButton.setVisibility(View.GONE);
        binding.playPauseButton.setVisibility(View.GONE);
        binding.playPreviousButton.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        binding.playNextButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        binding.playPauseButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        RokidActionAccessibilityBinder.bindAll(this::syncSelectionFromAccessibility,
                binding.rokidActionPlayPause, binding.rokidActionRewind,
                binding.rokidActionForward, binding.rokidActionFullscreen,
                binding.rokidActionQuality, binding.rokidActionSpeed,
                binding.rokidActionCaptions, binding.rokidActionClose);
        update();
    }

    boolean onKeyDown(@NonNull final RokidKeyMapper.Action action) {
        switch (action) {
            case PREVIOUS:
            case NEXT:
                if (isVisible()) {
                    return moveFocus(action == RokidKeyMapper.Action.NEXT);
                }
                if (ui.isControlsVisible()) {
                    ui.showControlsThenHide();
                    return moveFocus(action == RokidKeyMapper.Action.NEXT);
                }
                return false;
            case SELECT:
                return selectFromKey();
            case BACK:
                if (isVisible() || ui.isControlsVisible()) {
                    ui.hideControls(0, 0);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    void hide() {
        actionIndex = -1;
        clearSelection();
        binding.rokidActionRail.setVisibility(View.GONE);
    }

    void update() {
        update(ui.isControlsVisible());
    }

    void update(final boolean controlsVisible) {
        if (!RokidMode.enabled() || binding.rokidActionRail == null) {
            return;
        }

        binding.rokidActionRail.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        if (!controlsVisible) {
            actionIndex = -1;
            clearSelection();
        }
        updatePlayPauseAction();
        binding.rokidActionRewind.setText(R.string.rokid_seek_backward_static);
        binding.rokidActionRewind.setContentDescription(ui.context.getString(R.string.rewind)
                + " " + ui.context.getString(R.string.rokid_seek_backward_static));
        binding.rokidActionForward.setText(R.string.rokid_seek_forward_static);
        binding.rokidActionForward.setContentDescription(ui.context.getString(R.string.forward)
                + " " + ui.context.getString(R.string.rokid_seek_forward_static));
        binding.rokidActionFullscreen.setText(ui.isFullscreen()
                ? R.string.rokid_player_exit_fullscreen : R.string.rokid_player_fullscreen);
        binding.rokidActionFullscreen.setContentDescription(
                binding.rokidActionFullscreen.getText());

        final CharSequence quality = binding.qualityTextView.getText();
        binding.rokidActionQuality.setText(quality == null || quality.length() == 0
                ? ui.context.getString(R.string.rokid_player_quality) : quality);
        binding.rokidActionQuality.setContentDescription(
                ui.context.getString(R.string.rokid_player_quality) + " "
                        + binding.rokidActionQuality.getText());
        binding.rokidActionQuality.setVisibility(
                binding.qualityTextView.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);

        binding.rokidActionSpeed.setText(binding.playbackSpeed.getText());
        binding.rokidActionSpeed.setContentDescription(
                ui.context.getString(R.string.rokid_player_speed) + " "
                        + binding.rokidActionSpeed.getText());

        final boolean captionsVisible = binding.captionTextView.getVisibility() == View.VISIBLE;
        binding.rokidActionCaptions.setText(R.string.rokid_player_subtitles);
        binding.rokidActionCaptions.setVisibility(captionsVisible ? View.VISIBLE : View.GONE);
        binding.rokidActionCaptions.setContentDescription(captionsVisible
                ? ui.context.getString(R.string.rokid_player_subtitles) + " "
                + binding.captionTextView.getText()
                : ui.context.getString(R.string.rokid_player_subtitles));

        binding.rokidActionClose.setText(R.string.rokid_player_close);
        binding.rokidActionClose.setContentDescription(
                ui.context.getString(R.string.rokid_player_close));
    }

    void updatePlayPauseAction() {
        switch (getPlayPauseAction()) {
            case PAUSE:
                binding.rokidActionPlayPause.setText(R.string.rokid_player_pause);
                break;
            case REPLAY:
                binding.rokidActionPlayPause.setText(R.string.replay);
                break;
            case PLAY:
            default:
                binding.rokidActionPlayPause.setText(R.string.rokid_player_play);
                break;
        }
        binding.rokidActionPlayPause.setContentDescription(binding.rokidActionPlayPause.getText());
    }

    void restoreFocusAfterPopup() {
        update(true);
        final ArrayList<View> actions = getVisibleActions();
        if (actions.isEmpty()) {
            return;
        }

        if (actionIndex < 0 || actionIndex >= actions.size()) {
            actionIndex = 0;
        }
        applySelection(actions);

        final View target = actions.get(actionIndex);
        target.requestFocusFromTouch();
        target.requestFocus();
    }

    private boolean isVisible() {
        return RokidMode.enabled() && binding.rokidActionRail.getVisibility() == View.VISIBLE
                && binding.rokidActionRail.isShown();
    }

    private boolean moveFocus(final boolean forward) {
        if (!isVisible()) {
            ui.showControlsThenHide();
        }

        final ArrayList<View> actions = getVisibleActions();
        if (actions.isEmpty()) {
            return false;
        }

        final int current = currentActionIndex(actions);
        final int targetIndex;
        if (current < 0) {
            targetIndex = forward ? 0 : actions.size() - 1;
        } else if (forward) {
            targetIndex = current == actions.size() - 1 ? 0 : current + 1;
        } else {
            targetIndex = current == 0 ? actions.size() - 1 : current - 1;
        }

        final View target = actions.get(targetIndex);
        actionIndex = targetIndex;
        ui.showControlsThenHide();
        applySelection(actions);
        target.requestFocusFromTouch();
        target.requestFocus();
        return true;
    }

    private boolean selectFromKey() {
        if (!isVisible()) {
            actionIndex = 0;
            ui.showControlsThenHide();
            binding.rokidActionPlayPause.requestFocus();
            applySelection(getVisibleActions());
            ui.showSystemUIPartially();
            return true;
        }

        final ArrayList<View> actions = getVisibleActions();
        syncActionIndexFromFocusedAction(actions);
        if (actionIndex >= 0 && actionIndex < actions.size()) {
            actions.get(actionIndex).performClick();
            return true;
        }

        final View focused = binding.getRoot().findFocus();
        if (focused != null && focused.isShown() && focused.isEnabled() && focused.isClickable()) {
            focused.performClick();
        } else {
            player.playPause();
        }
        return true;
    }

    private ArrayList<View> getVisibleActions() {
        final ArrayList<View> actions = new ArrayList<>();
        addVisibleAction(actions, binding.rokidActionPlayPause);
        addVisibleAction(actions, binding.rokidActionRewind);
        addVisibleAction(actions, binding.rokidActionForward);
        addVisibleAction(actions, binding.rokidActionFullscreen);
        addVisibleAction(actions, binding.rokidActionQuality);
        addVisibleAction(actions, binding.rokidActionSpeed);
        addVisibleAction(actions, binding.rokidActionCaptions);
        addVisibleAction(actions, binding.rokidActionClose);
        return actions;
    }

    private void addVisibleAction(final ArrayList<View> actions, final View view) {
        if (view.getVisibility() == View.VISIBLE && view.isShown() && view.isEnabled()) {
            actions.add(view);
        }
    }

    private int currentActionIndex(final ArrayList<View> actions) {
        final View focused = binding.getRoot().findFocus();
        final int focusedIndex = actions.indexOf(focused);
        return focusedIndex >= 0 ? focusedIndex : actionIndex;
    }

    private void applySelection(final ArrayList<View> actions) {
        clearSelection();
        if (actionIndex >= 0 && actionIndex < actions.size()) {
            actions.get(actionIndex).setSelected(true);
        }
    }

    private void clearSelection() {
        binding.rokidActionPlayPause.setSelected(false);
        binding.rokidActionRewind.setSelected(false);
        binding.rokidActionForward.setSelected(false);
        binding.rokidActionFullscreen.setSelected(false);
        binding.rokidActionQuality.setSelected(false);
        binding.rokidActionSpeed.setSelected(false);
        binding.rokidActionCaptions.setSelected(false);
        binding.rokidActionClose.setSelected(false);
    }

    private void syncActionIndexFromFocusedAction(@NonNull final ArrayList<View> actions) {
        final View focused = binding.getRoot().findFocus();
        final int focusedIndex = actions.indexOf(focused);
        if (focusedIndex >= 0) {
            actionIndex = focusedIndex;
            applySelection(actions);
        }
    }

    private void syncSelectionFromAccessibility(@NonNull final View actionView) {
        final ArrayList<View> actions = getVisibleActions();
        final int index = actions.indexOf(actionView);
        if (index < 0) {
            return;
        }

        actionIndex = index;
        applySelection(actions);
        ui.showControlsThenHide();
    }

    private void seek(final boolean forward) {
        if (forward) {
            player.fastForward();
        } else {
            player.fastRewind();
        }
        ui.showControlsThenHide();
    }

    private PlayPauseAction getPlayPauseAction() {
        final int state = player.getCurrentState();
        if (state == STATE_COMPLETED) {
            return PlayPauseAction.REPLAY;
        }
        if (player.isPlaying() || state == STATE_PLAYING || state == STATE_BUFFERING) {
            return PlayPauseAction.PAUSE;
        }
        return PlayPauseAction.PLAY;
    }

    private enum PlayPauseAction {
        PLAY, PAUSE, REPLAY
    }
}
