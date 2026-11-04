package org.schabi.newpipe.rokid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.schabi.newpipe.R;

public class RokidKeyboardControllerTest {
    private static final RokidKeyboardController.StringProvider STRINGS = (resId, args) -> {
        if (resId == R.string.rokid_keyboard_key_space) {
            return "Space";
        } else if (resId == R.string.rokid_keyboard_key_delete) {
            return "Delete";
        } else if (resId == R.string.rokid_keyboard_key_clear_text) {
            return "Clear text";
        } else if (resId == R.string.rokid_keyboard_key_voice_search) {
            return "Voice search";
        } else if (resId == R.string.rokid_keyboard_key_numbers_symbols) {
            return "Numbers and symbols";
        } else if (resId == R.string.rokid_keyboard_key_letters) {
            return "Letters";
        } else if (resId == R.string.rokid_keyboard_key_dot) {
            return "Dot";
        } else if (resId == R.string.rokid_keyboard_key_dash) {
            return "Dash";
        } else if (resId == R.string.rokid_keyboard_key_underscore) {
            return "Underscore";
        } else if (resId == R.string.rokid_keyboard_key_at_sign) {
            return "At sign";
        } else if (resId == R.string.rokid_keyboard_key_slash) {
            return "Slash";
        } else if (resId == R.string.rokid_keyboard_key_colon) {
            return "Colon";
        } else if (resId == R.string.rokid_keyboard_key_hash) {
            return "Hash";
        } else if (resId == R.string.rokid_keyboard_key_plus) {
            return "Plus";
        } else if (resId == R.string.rokid_keyboard_letter_key) {
            return "Key " + args[0];
        } else if (resId == R.string.rokid_keyboard_selected_key) {
            return "Selected " + args[0];
        }
        throw new IllegalArgumentException("Unexpected string id: " + resId);
    };

    @Test
    public void letterKeysHaveSpeakableLabels() {
        assertEquals("Key a", keyLabel("a"));
        assertEquals("Key z", keyLabel("z"));
    }

    @Test
    public void commandKeysHaveSpeakableLabels() {
        assertEquals("Space", keyLabel("space"));
        assertEquals("Delete", keyLabel("del"));
        assertEquals("Clear text", keyLabel("clear"));
        assertEquals("Voice search", keyLabel("voice"));
        assertEquals("Search", keyLabel("go"));
        assertEquals("Numbers and symbols", keyLabel("123"));
        assertEquals("Letters", keyLabel("abc"));
    }

    @Test
    public void symbolKeysHaveSpeakableLabels() {
        assertEquals("Dot", keyLabel("."));
        assertEquals("Dash", keyLabel("-"));
        assertEquals("Underscore", keyLabel("_"));
        assertEquals("At sign", keyLabel("@"));
        assertEquals("Slash", keyLabel("/"));
        assertEquals("Colon", keyLabel(":"));
        assertEquals("Hash", keyLabel("#"));
        assertEquals("Plus", keyLabel("+"));
    }

    @Test
    public void voiceKeyIsOnlyExposedWhenVoiceActionExists() {
        assertTrue(Arrays.asList(RokidKeyboardController.keysForTesting(false, true))
                .contains("voice"));
        assertTrue(Arrays.asList(RokidKeyboardController.keysForTesting(true, true))
                .contains("voice"));
        assertFalse(Arrays.asList(RokidKeyboardController.keysForTesting(false, false))
                .contains("voice"));
        assertFalse(Arrays.asList(RokidKeyboardController.keysForTesting(true, false))
                .contains("voice"));
    }

    @Test
    public void enterKeyCanUseContextualLabel() {
        assertEquals("Done",
                RokidKeyboardController.keyAccessibilityLabelForTesting("go", "Done", STRINGS));
        assertEquals("Import",
                RokidKeyboardController.keyAccessibilityLabelForTesting("go", "Import", STRINGS));
    }

    private static String keyLabel(final String key) {
        return RokidKeyboardController.keyAccessibilityLabelForTesting(key, "Search", STRINGS);
    }
}
