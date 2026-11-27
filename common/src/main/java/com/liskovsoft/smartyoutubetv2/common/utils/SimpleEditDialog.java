package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.settingssearch.SettingsCapture;

public class SimpleEditDialog {
    /**
     * No microphone key on the keyboard: "nm" for the AOSP keyboards, the other for Gboard (Android TV's included)
     */
    private static final String NO_MICROPHONE = "nm,com.google.android.inputmethod.latin.noMicrophoneKey";
    /**
     * What an IPv4 address is typed with, on the number pad
     */
    @VisibleForTesting
    static final String IPV4_CHARACTERS = "0123456789.";
    @VisibleForTesting
    static final int IPV4_INPUT_TYPE = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

    public interface OnChange {
        boolean onChange(String newValue);
    }

    public static void show(Context context, String dialogTitle, String defaultValue, OnChange onChange) {
        show(context, dialogTitle, dialogTitle, defaultValue, onChange, null);
    }

    public static void show(Context context, String dialogTitle, String dialogHint, String defaultValue, OnChange onChange) {
        show(context, dialogTitle, dialogHint, defaultValue, onChange, null);
    }

    public static void show(Context context, String dialogTitle, String dialogHint, String defaultValue, OnChange onChange, Runnable onDismiss) {
        show(context, dialogTitle, dialogHint, defaultValue, onChange, onDismiss, InputType.TYPE_NULL);
    }

    public static void showPassword(Context context, String dialogTitle, String defaultValue, OnChange onChange) {
        showPassword(context, dialogTitle, defaultValue, onChange, null);
    }

    public static void showPassword(Context context, String dialogTitle, String defaultValue, OnChange onChange, Runnable onDismiss) {
        show(context, dialogTitle, dialogTitle, defaultValue, onChange, onDismiss, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    /**
     * A search, e.g. inside a channel: the keyboard's corrections as the search setting has them (off by default)
     */
    public static void showSearch(Context context, String dialogTitle, String defaultValue, OnChange onChange) {
        showSearch(context, dialogTitle, dialogTitle, defaultValue, onChange, null);
    }

    /**
     * @param onDismiss once it's closed: by OK, Cancel or Back. Null for nothing.
     */
    public static void showSearch(Context context, String dialogTitle, String dialogHint, String defaultValue, OnChange onChange,
                                  Runnable onDismiss) {
        show(context, dialogTitle, dialogHint, defaultValue, onChange, onDismiss, InputType.TYPE_NULL, null, null, false, true);
    }

    /**
     * A name, e.g. of a subscription group: the keyboard starts with a capital letter
     */
    public static void showName(Context context, String dialogTitle, String defaultValue, OnChange onChange) {
        show(context, dialogTitle, dialogTitle, defaultValue, onChange, null, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }

    /**
     * An address, e.g. of a server: no autocorrection, no suggestions, no capital letter, no voice input. The layout's type
     * (textUri|textPersonName) adds up to a postal address, which keyboards autocorrect.
     */
    public static void showUrl(Context context, String dialogTitle, String dialogHint, String defaultValue, OnChange onChange) {
        showUrl(context, dialogTitle, dialogHint, defaultValue, onChange, null, false);
    }

    /**
     * @param onDismiss once it's closed: by OK, Cancel or Back. Null for nothing.
     * @param isEmptyAllowed an empty field is a value too (e.g. an optional server cleared), given to onChange as ""
     */
    public static void showUrl(Context context, String dialogTitle, String dialogHint, String defaultValue, OnChange onChange,
                               Runnable onDismiss, boolean isEmptyAllowed) {
        show(context, dialogTitle, dialogHint, defaultValue, onChange, onDismiss,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, null, NO_MICROPHONE,
                isEmptyAllowed, false);
    }

    /**
     * An IPv4 address, e.g. of a DNS server: the keyboard's number pad, with its dot, instead of the letters. Digits and dots only,
     * any number of dots: the number pad's own filter takes one dot, or the locale's decimal separator in its place (a comma in
     * German).
     *
     * @param onDismiss once it's closed: by OK, Cancel or Back. Null for nothing.
     * @param isEmptyAllowed an empty field is a value too (e.g. an optional server cleared), given to onChange as ""
     */
    public static void showIpv4Address(Context context, String dialogTitle, String dialogHint, String defaultValue, OnChange onChange,
                                       Runnable onDismiss, boolean isEmptyAllowed) {
        show(context, dialogTitle, dialogHint, defaultValue, onChange, onDismiss,
                IPV4_INPUT_TYPE, IPV4_CHARACTERS, NO_MICROPHONE, isEmptyAllowed, false);
    }

    private static void show(Context context, String dialogTitle, String dialogHint, String defaultValue, OnChange onChange, Runnable onDismiss, int inputType) {
        show(context, dialogTitle, dialogHint, defaultValue, onChange, onDismiss, inputType, null, null, false, false);
    }

    /**
     * @param inputType the InputType.TYPE_* of the field, or {@link InputType#TYPE_NULL} for the one of the layout
     * @param characters the only ones the field takes, typed or pasted, null for any. The keyboard is still the inputType's.
     * @param privateImeOptions for the keyboard (see EditorInfo.privateImeOptions), null for none
     * @param isSearch the search setting turns the keyboard's corrections off (see {@link TypingCorrection})
     */
    private static void show(Context context, String dialogTitle, String dialogHint, String defaultValue, OnChange onChange, Runnable onDismiss,
                             int inputType, String characters, String privateImeOptions, boolean isEmptyAllowed, boolean isSearch) {
        // Clicked by the settings search as it looks the menus through: does nothing then (see SettingsCapture)
        if (SettingsCapture.isActive()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppDialog);
        LayoutInflater inflater = LayoutInflater.from(context);
        View contentView = inflater.inflate(R.layout.simple_edit_dialog, null);

        EditText editField = contentView.findViewById(R.id.simple_edit_value);
        if (characters != null) {
            setCharacters(editField, characters, inputType);
        } else if (inputType != InputType.TYPE_NULL) {
            editField.setInputType(inputType);
        }
        if (isSearch) {
            TypingCorrection.applySearchSetting(editField);
        }
        if (privateImeOptions != null) {
            editField.setPrivateImeOptions(privateImeOptions);
        }
        KeyHelpers.fixShowKeyboard(editField);

        editField.setText(defaultValue);
        editField.setHint(dialogHint);
        editField.setNextFocusDownId(android.R.id.button1); // OK button

        if (defaultValue != null) { // move cursor to the end
            editField.setSelection(defaultValue.length());
        }

        // keep empty, will override below.
        // https://stackoverflow.com/a/15619098/5379584
        AlertDialog configDialog = builder
                .setTitle(dialogTitle)
                .setView(contentView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> { })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> { })
                // The remote has no way to paste into the field
                .setNeutralButton(android.R.string.paste, (dialog, which) -> { })
                .create();

        if (onDismiss != null) {
            configDialog.setOnDismissListener(dialog -> onDismiss.run());
        }

        // Enter presses OK, whether the keyboard sends an action or the Enter key itself (e.g. a hardware keyboard)
        editField.setOnEditorActionListener((v, actionId, event) -> {
            boolean isEnterKey = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;

            if (actionId != EditorInfo.IME_ACTION_DONE && actionId != EditorInfo.IME_ACTION_NEXT && !isEnterKey) {
                return false;
            }

            // Once per press: the key comes down and up, and a keyboard might send the action and the key both
            if ((event == null || event.getAction() == KeyEvent.ACTION_DOWN) && configDialog.isShowing()) {
                configDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            }

            return true;
        });

        try {
            configDialog.show();
        } catch (RuntimeException e) {
            // BadTokenException: Unable to add window -- token null is not for an application
            // RuntimeException: InputChannel is not initialized
            e.printStackTrace();
            MessageHelpers.showMessage(context, e.getMessage());
            return;
        }

        configDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener((view) -> {
            String newValue = editField.getText().toString();

            if (newValue.isEmpty() && !isEmptyAllowed) {
                // Empty fields not allowed
                editField.setHint(R.string.enter_value);
                return;
            }

            boolean dismiss = onChange.onChange(newValue);

            if (dismiss) {
                configDialog.dismiss();
            }
        });

        configDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener((view) -> configDialog.dismiss());

        // Stays open: the pasted text can be checked before OK
        configDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener((view) -> paste(context, editField));

        //editField.setNextFocusDownId(configDialog.getButton(AlertDialog.BUTTON_POSITIVE).getId()); // OK button
    }

    /**
     * The field takes these characters only, typed or pasted, and the keyboard is the inputType's: the listener's own type would be
     * set otherwise. The text the field opens with isn't filtered: a value saved before shows as it is.
     */
    @VisibleForTesting
    static void setCharacters(EditText editField, String characters, int inputType) {
        editField.setKeyListener(DigitsKeyListener.getInstance(characters));
        editField.setRawInputType(inputType);
    }

    /**
     * Puts the clipboard's text in place of the selection, or at the cursor. One line: the fields are single-line.
     */
    private static void paste(Context context, EditText editField) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
        CharSequence text = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).coerceToText(context) : null;
        String pasted = text != null ? text.toString().replaceAll("\\s*[\\r\\n]+\\s*", " ").trim() : "";

        if (pasted.isEmpty()) {
            MessageHelpers.showMessage(context, R.string.clipboard_empty);
            return;
        }

        Editable editable = editField.getText();
        // -1 when the field never had the cursor
        int start = Math.max(0, Math.min(editField.getSelectionStart(), editField.getSelectionEnd()));
        int end = Math.max(0, Math.max(editField.getSelectionStart(), editField.getSelectionEnd()));

        editable.replace(start, end, pasted);
        editField.setSelection(start + pasted.length());
        editField.requestFocus();
    }
}
