package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.DialogPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.rokid.RokidDialogNavigationHelper;
import org.schabi.newpipe.rokid.RokidMode;
import org.schabi.newpipe.rokid.RokidTextInputHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class BasePreferenceFragment extends PreferenceFragmentCompat {
    protected final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    protected static final boolean DEBUG = MainActivity.DEBUG;

    SharedPreferences defaultPreferences;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        super.onCreate(savedInstanceState);
    }

    protected void addPreferencesFromResourceRegistry() {
        addPreferencesFromResource(
                SettingsResourceRegistry.getInstance().getPreferencesResId(this.getClass()));
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        setDivider(null);
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle());
    }

    @Override
    public void onResume() {
        super.onResume();
        ThemeHelper.setTitleToAppCompatActivity(getActivity(), getPreferenceScreen().getTitle());
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull final Preference preference) {
        if (!RokidMode.enabled()) {
            super.onDisplayPreferenceDialog(preference);
            return;
        }

        if (preference instanceof ListPreference
                && showRokidListPreferenceDialog((ListPreference) preference)) {
            return;
        }
        if (preference instanceof MultiSelectListPreference
                && showRokidMultiSelectPreferenceDialog(
                        (MultiSelectListPreference) preference)) {
            return;
        }
        if (preference instanceof EditTextPreference
                && showRokidEditTextPreferenceDialog((EditTextPreference) preference)) {
            return;
        }

        super.onDisplayPreferenceDialog(preference);
    }

    private boolean showRokidListPreferenceDialog(
            @NonNull final ListPreference preference
    ) {
        final CharSequence[] entries = preference.getEntries();
        final CharSequence[] entryValues = preference.getEntryValues();
        if (entries == null || entryValues == null || entries.length != entryValues.length) {
            return false;
        }

        final AlertDialog.Builder builder = newPreferenceDialogBuilder(preference)
                .setSingleChoiceItems(entries, preference.findIndexOfValue(preference.getValue()),
                        (dialog, which) -> {
                            final String value = entryValues[which].toString();
                            if (preference.callChangeListener(value)) {
                                preference.setValue(value);
                            }
                            dialog.dismiss();
                        })
                .setNegativeButton(R.string.cancel, null);

        RokidDialogNavigationHelper.show(requireContext(), builder);
        return true;
    }

    private boolean showRokidMultiSelectPreferenceDialog(
            @NonNull final MultiSelectListPreference preference
    ) {
        final CharSequence[] entries = preference.getEntries();
        final CharSequence[] entryValues = preference.getEntryValues();
        if (entries == null || entryValues == null || entries.length != entryValues.length) {
            return false;
        }

        final Set<String> selectedValues = new HashSet<>(preference.getValues());
        final boolean[] checked = new boolean[entryValues.length];
        for (int i = 0; i < entryValues.length; i++) {
            checked[i] = selectedValues.contains(entryValues[i].toString());
        }

        final AlertDialog.Builder builder = newPreferenceDialogBuilder(preference)
                .setMultiChoiceItems(entries, checked, (dialog, which, isChecked) -> {
                    final String value = entryValues[which].toString();
                    if (isChecked) {
                        selectedValues.add(value);
                    } else {
                        selectedValues.remove(value);
                    }
                })
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    final Set<String> values = new HashSet<>(selectedValues);
                    if (preference.callChangeListener(values)) {
                        preference.setValues(values);
                    }
                })
                .setNegativeButton(R.string.cancel, null);

        RokidDialogNavigationHelper.show(requireContext(), builder);
        return true;
    }

    private boolean showRokidEditTextPreferenceDialog(
            @NonNull final EditTextPreference preference
    ) {
        final DialogEditTextBinding binding = DialogEditTextBinding.inflate(getLayoutInflater());
        binding.dialogEditText.setText(preference.getText());
        binding.dialogEditText.setSelection(binding.dialogEditText.getText().length());

        final AlertDialog dialog = newPreferenceDialogBuilder(preference)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.ok, (dialogInterface, which) -> {
                    final String value = binding.dialogEditText.getText().toString();
                    if (preference.callChangeListener(value)) {
                        preference.setText(value);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        RokidTextInputHelper.attach(requireActivity(), dialog, binding.dialogEditText);
        dialog.show();
        return true;
    }

    @NonNull
    private AlertDialog.Builder newPreferenceDialogBuilder(
            @NonNull final DialogPreference preference
    ) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(getPreferenceDialogTitle(preference));
        final Drawable icon = preference.getDialogIcon();
        if (icon != null) {
            builder.setIcon(icon);
        }
        final CharSequence message = preference.getDialogMessage();
        if (message != null && message.length() > 0) {
            builder.setMessage(message);
        }
        return builder;
    }

    @Nullable
    private CharSequence getPreferenceDialogTitle(@NonNull final DialogPreference preference) {
        final CharSequence dialogTitle = preference.getDialogTitle();
        return dialogTitle == null || dialogTitle.length() == 0
                ? preference.getTitle() : dialogTitle;
    }

    @NonNull
    public final <T extends Preference> T requirePreference(@StringRes final int resId) {
        final T preference = findPreference(getString(resId));
        Objects.requireNonNull(preference);
        return preference;
    }
}
