param(
    [string]$GlassesSerial = "",
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$RunPlayer
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Artifacts = Join-Path $Root "qa\artifacts"
$Package = "com.anezium.rokid.newpipe"
$Apk = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
New-Item -ItemType Directory -Force -Path $Artifacts | Out-Null

function Invoke-GlassesAdb {
    param([string[]]$Arguments)
    $adbArgs = @()
    if ($GlassesSerial -ne "") {
        $adbArgs += @("-s", $GlassesSerial)
    }
    $adbArgs += $Arguments
    & adb @adbArgs
}

function Assert-AdbDeviceAvailable {
    $devices = @(& adb devices | Select-Object -Skip 1 | Where-Object {
        $_ -match "\tdevice$"
    })
    if ($GlassesSerial -ne "") {
        $matchesSerial = @($devices | Where-Object {
            $_ -match "^$([regex]::Escape($GlassesSerial))\tdevice$"
        })
        Assert-Condition ($matchesSerial.Count -gt 0) `
                "ADB device '$GlassesSerial' is not connected"
    } else {
        Assert-Condition ($devices.Count -gt 0) `
                "no ADB device connected; connect the Rokid glasses or pass -GlassesSerial"
    }
    Write-Host "Verified ADB device is connected"
}

function Get-WifiEnabled {
    $value = Invoke-GlassesAdb -Arguments @("shell", "settings", "get", "global", "wifi_on") |
            Select-Object -First 1
    return $value.Trim() -eq "1"
}

function Set-WifiEnabled {
    param([bool]$Enabled)
    $state = if ($Enabled) { "enable" } else { "disable" }
    Invoke-GlassesAdb -Arguments @("shell", "svc", "wifi", $state) | Out-Null
}

function Save-DeviceScreenshot {
    param([string]$Name)
    $remote = "/sdcard/$Name.png"
    $local = Join-Path $Artifacts "$Name.png"
    Invoke-GlassesAdb -Arguments @("shell", "screencap", "-p", $remote) | Out-Null
    Invoke-GlassesAdb -Arguments @("pull", $remote, $local) | Out-Null
    Invoke-GlassesAdb -Arguments @("shell", "rm", $remote) | Out-Null
    Write-Host "Saved screenshot: $local"
}

function Save-WindowState {
    param([string]$Name)
    $local = Join-Path $Artifacts "$Name-window.txt"
    Invoke-GlassesAdb -Arguments @("shell", "dumpsys", "window", "windows") |
            Out-File -Encoding utf8 $local
    Write-Host "Saved window state: $local"
}

function Save-UiDump {
    param([string]$Name)
    $remote = "/sdcard/$Name.xml"
    $local = Join-Path $Artifacts "$Name-uiautomator.xml"
    Invoke-GlassesAdb -Arguments @("shell", "uiautomator", "dump", $remote) | Out-Null
    Invoke-GlassesAdb -Arguments @("pull", $remote, $local) | Out-Null
    Invoke-GlassesAdb -Arguments @("shell", "rm", $remote) | Out-Null
    Write-Host "Saved UI dump: $local"
    return $local
}

function Save-DebugState {
    param([string]$Name)
    $action = "$Package.debug.DUMP_ROKID_STATE"
    $local = Join-Path $Artifacts "$Name-rokid-state.json"
    Invoke-GlassesAdb -Arguments @(
            "shell", "am", "broadcast", "-a", $action, "-p", $Package
    ) | Out-Null
    Start-Sleep -Milliseconds 500
    Invoke-GlassesAdb -Arguments @(
            "shell", "run-as", $Package, "cat", "files/rokid-debug-state.json"
    ) | Out-File -Encoding utf8 $local
    Write-Host "Saved Rokid debug state: $local"
    return $local
}

function Get-NodeAttribute {
    param(
        [System.Xml.XmlNode]$Node,
        [string]$Name
    )
    $attribute = $Node.Attributes.GetNamedItem($Name)
    if ($null -eq $attribute) {
        return ""
    }
    return [string]$attribute.Value
}

function Get-NodeLabel {
    param([System.Xml.XmlNode]$Node)
    $text = Get-NodeAttribute $Node "text"
    $description = Get-NodeAttribute $Node "content-desc"
    $label = if ($description.Length -gt 0) {
        $description
    } else {
        $text
    }
    return ($label -replace "\s+", " ").Trim()
}

function Get-UiNodes {
    param([string]$Path)
    [xml]$xml = Get-Content -Path $Path -Raw
    return @($xml.SelectNodes("//node"))
}

function Test-HasAncestorResourceId {
    param(
        [System.Xml.XmlNode]$Node,
        [string]$ResourceId
    )
    $current = $Node.ParentNode
    while ($null -ne $current) {
        if ((Get-NodeAttribute $current "resource-id") -eq $ResourceId) {
            return $true
        }
        $current = $current.ParentNode
    }
    return $false
}

function Assert-Condition {
    param(
        [bool]$Condition,
        [string]$Message
    )
    if (-not $Condition) {
        throw "R08 accessibility assertion failed: $Message"
    }
}

function Assert-NoTodoContentDescriptions {
    $layoutGlob = Join-Path $Root "app\src\main\res\layout\*.xml"
    $matches = @(Select-String -Path $layoutGlob -Pattern 'contentDescription="TODO"')
    $summary = ($matches | ForEach-Object {
        "$($_.Path):$($_.LineNumber)"
    }) -join "; "

    Assert-Condition ($matches.Count -eq 0) `
            "layouts contain TODO content descriptions: $summary"
    Write-Host "Verified layout content descriptions do not contain TODO"
}

function Assert-SourceContains {
    param(
        [string]$RelativePath,
        [string]$Pattern,
        [string]$Message
    )
    $path = Join-Path $Root $RelativePath
    $matches = @(Select-String -Path $path -Pattern $Pattern -SimpleMatch)
    Assert-Condition ($matches.Count -gt 0) $Message
}

function Assert-SourceDoesNotContain {
    param(
        [string]$RelativePath,
        [string]$Pattern,
        [string]$Message
    )
    $path = Join-Path $Root $RelativePath
    $matches = @(Select-String -Path $path -Pattern $Pattern -SimpleMatch)
    Assert-Condition ($matches.Count -eq 0) $Message
}

function Assert-RokidSourceGuards {
    Assert-SourceContains `
            "shared\src\androidMain\AndroidManifest.xml" `
            "net.newpipe.app.ComposeActivity" `
            "ComposeActivity is missing from the merged Android manifests"
    Assert-SourceDoesNotContain `
            "app\src\main\AndroidManifest.xml" `
            ".about.AboutActivity" `
            "Manifest still declares the removed non-R08 AboutActivity"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\RouterActivity.java" `
            "RokidDialogNavigationHelper.attach(this, alertDialogChoice)" `
            "RouterActivity share chooser is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\error\ReCaptchaActivity.java" `
            "RokidFocusNavigator.handle(this, event)" `
            "ReCaptchaActivity is missing R08 key navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\download\DownloadActivity.java" `
            "RokidFocusNavigator.handle(this, event)" `
            "DownloadActivity is missing R08 key navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\download\DownloadDialog.java" `
            "handleRokidKeyEvent(event)" `
            "DownloadDialog is missing R08 key navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\download\DownloadDialog.java" `
            "dialogBinding.threads.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO)" `
            "DownloadDialog still exposes the thread SeekBar as an R08 target"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\util\FilePickerActivityHelper.java" `
            "RokidFocusNavigator.handle(this, event)" `
            "FilePickerActivityHelper is missing R08 key navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\SettingsActivity.java" `
            "RokidKeyboardController.forActivity(this).show" `
            "SettingsActivity search is missing the Rokid keyboard"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\BasePreferenceFragment.java" `
            "onDisplayPreferenceDialog" `
            "AndroidX preference dialogs are not intercepted for R08 navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\BasePreferenceFragment.java" `
            "showRokidListPreferenceDialog" `
            "ListPreference dialogs still rely on AndroidX defaults in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\BasePreferenceFragment.java" `
            "showRokidMultiSelectPreferenceDialog" `
            "MultiSelectListPreference dialogs still rely on AndroidX defaults in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\BasePreferenceFragment.java" `
            "RokidTextInputHelper.attach(requireActivity(), dialog, binding.dialogEditText)" `
            "EditTextPreference dialogs are missing the Rokid keyboard"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\ContentSettingsFragment.java" `
            "Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !RokidMode.enabled()" `
            "App language setting can still force R08 users into Android system settings"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidExternalNavigationHelper.java" `
            "confirmAndOpen(" `
            "External Android settings launches are missing the R08 confirmation helper"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\error\ErrorPanelHelper.kt" `
            "RokidExternalNavigationHelper.openExternalActivity(" `
            "Offline Wi-Fi recovery no longer opens Android Wi-Fi settings directly"
    Assert-SourceDoesNotContain `
            "app\src\main\java\org\schabi\newpipe\error\ErrorPanelHelper.kt" `
            "RokidExternalNavigationHelper.confirmAndOpen(" `
            "Offline Wi-Fi recovery still shows a confirmation before Android Wi-Fi settings"
    Assert-SourceContains `
            "app\src\test\java\org\schabi\newpipe\rokid\RokidKioskNavigatorTest.java" `
            "videosDoNotOpenMusicOrPodcastsWhenNoVideoKiosk" `
            "Rokid Videos shortcut lacks a regression test against Trending Music"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\feed\notifications\NotificationHelper.kt" `
            "R.string.rokid_notification_settings_message" `
            "Notification settings can still jump directly into Android settings in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\AppearanceSettingsFragment.java" `
            "R.string.rokid_caption_settings_message" `
            "Caption settings can still jump directly into Android settings in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\player\PlayQueueActivity.java" `
            "R.string.rokid_sound_settings_message" `
            "Play queue system-audio action can still jump directly into Android settings"
    Assert-SourceDoesNotContain `
            "app\src\main\java\org\schabi\newpipe\player\PlayQueueActivity.java" `
            "startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS))" `
            "Play queue system-audio action still launches Android sound settings directly"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\download\DownloadDialog.java" `
            "R.string.rokid_storage_settings_message" `
            "Insufficient-storage recovery can still jump directly into Android storage settings"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\util\PermissionHelper.java" `
            "R.string.rokid_overlay_settings_message" `
            "Overlay permission recovery can still jump directly into Android settings"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\streams\io\NoFileManagerSafeGuard.java" `
            "isAndroidFilePickerIntent" `
            "System file pickers can still open without a R08 confirmation gate"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\streams\io\NoFileManagerSafeGuard.java" `
            "R.string.rokid_system_file_picker_message" `
            "System file picker confirmation is missing R08 copy"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\error\ErrorActivity.kt" `
            "RokidFocusNavigator.handle(this, event)" `
            "ErrorActivity is missing R08 key navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\error\ErrorActivity.kt" `
            "RokidTextInputHelper.show(this, binding.errorCommentBox)" `
            "ErrorActivity comment field is missing the Rokid keyboard"
    Assert-SourceDoesNotContain `
            "app\src\main\java\org\schabi\newpipe\error\ErrorActivity.kt" `
            "applicationContext," `
            "ErrorActivity share action bypasses the R08 chooser by using applicationContext"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\player\PlayQueueActivity.java" `
            "cycleRokidPlaybackSpeed" `
            "PlayQueueActivity is missing the R08 fixed-speed action"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\player\helper\PlaybackParameterDialog.java" `
            "RokidDialogNavigationHelper.attach(requireActivity(), dialog)" `
            "PlaybackParameterDialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\player\helper\PlaybackParameterDialog.java" `
            "disableRokidSeekBar(binding.tempoSeekbar)" `
            "PlaybackParameterDialog still exposes sliders as R08 targets"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\player\PlayQueueActivity.java" `
            "moveQueueItemDown(viewHolder.getBindingAdapterPosition())" `
            "PlayQueueActivity queue reorder still requires drag in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\player\ui\MainPlayerUi.java" `
            "binding.itemsList.setClickable(!RokidMode.enabled())" `
            "MainPlayerUi itemsList can still become a R08 target"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\player\ui\MainPlayerUi.java" `
            "moveQueueItemDown(viewHolder.getBindingAdapterPosition())" `
            "MainPlayerUi queue reorder still requires drag in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\player\playqueue\PlayQueueItemBuilder.java" `
            "R.string.rokid_move_down" `
            "Play queue handle is missing a labeled R08 move action"
    Assert-SourceDoesNotContain `
            "app\src\main\java\org\schabi\newpipe\player\playqueue\PlayQueueItemBuilder.java" `
            '"Move down"' `
            "Play queue R08 move action is still hardcoded in English"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\QueueItemMenuUtil.java" `
            "showRokidDialogMenu(playQueue, item, view, hideDetails" `
            "Play queue item context menu still opens an inaccessible PopupMenu in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\QueueItemMenuUtil.java" `
            "RokidDialogNavigationHelper.attach(activity, dialog)" `
            "Play queue item R08 dialog menu is missing dialog key navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\info_list\dialog\InfoItemDialog.java" `
            "RokidDialogNavigationHelper.attach(activity, dialog)" `
            "InfoItemDialog context actions are missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\dialog\PlaylistDialog.java" `
            "RokidDialogNavigationHelper.attach(requireActivity(), dialog)" `
            "Playlist append dialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidDialogNavigationHelper.java" `
            "public static AlertDialog show(" `
            "R08 dialog helper cannot safely show and attach AlertDialogs from a Context"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\feed\FeedFragment.kt" `
            "RokidDialogNavigationHelper.show(" `
            "Feed dialogs are missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\subscription\SubscriptionFragment.kt" `
            "RokidDialogNavigationHelper.show(" `
            "Subscription context dialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\subscription\SubscriptionFragment.kt" `
            "showRokidImportMenu()" `
            "Subscription import still depends on Android submenus in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\subscription\SubscriptionFragment.kt" `
            "showRokidExportMenu()" `
            "Subscription export still depends on Android submenus in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\subscription\ImportConfirmationDialog.java" `
            "RokidDialogNavigationHelper.attach(requireActivity(), dialog)" `
            "Subscription import confirmation is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\custom\NotificationSlot.java" `
            "RokidDialogNavigationHelper.attach(context, alertDialog)" `
            "Notification action chooser is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\HistorySettingsFragment.java" `
            "RokidDialogNavigationHelper.show(context, new AlertDialog.Builder(context)" `
            "History settings destructive dialogs are missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\error\ErrorActivity.kt" `
            "RokidDialogNavigationHelper.show(" `
            "Error privacy policy dialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "shared\src\androidMain\kotlin\net\newpipe\app\ComposeActivity.kt" `
            "onKeyEvent" `
            "ComposeActivity is missing one-axis R08 key navigation"
    Assert-SourceContains `
            "shared\src\androidMain\kotlin\net\newpipe\app\ComposeActivity.kt" `
            "KEYCODE_ROKID_DOUBLE_TAP" `
            "ComposeActivity does not map the R08 double-tap key to select"
    Assert-SourceContains `
            "shared\src\androidMain\kotlin\net\newpipe\app\ComposeActivity.kt" `
            "DIRECTION_DEBOUNCE_MS" `
            "ComposeActivity does not debounce paired R08 directional aliases"
    Assert-SourceContains `
            "shared\src\androidMain\kotlin\net\newpipe\app\ComposeActivity.kt" `
            "focusRequester.requestFocus()" `
            "ComposeActivity cannot receive the first R08 swipe before a child has focus"
    Assert-SourceContains `
            "app\src\main\java\us\shandian\giga\ui\adapter\MissionAdapter.java" `
            "showRokidPopupMenu()" `
            "Download item context menu still opens an inaccessible PopupMenu in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\us\shandian\giga\ui\adapter\MissionAdapter.java" `
            "RokidDialogNavigationHelper.show(mContext, builder)" `
            "Download error dialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\us\shandian\giga\ui\fragment\MissionsFragment.java" `
            "RokidDialogNavigationHelper.show(mContext, new AlertDialog.Builder(mContext)" `
            "Download history confirmation dialogs are missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\BackupRestoreSettingsFragment.java" `
            "RokidDialogNavigationHelper.show(requireContext(), builder)" `
            "Backup/reset settings dialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\BackupRestoreSettingsFragment.java" `
            "RokidDialogNavigationHelper.show(requireActivity()," `
            "Backup import confirmation dialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\util\NavigationHelper.java" `
            "RokidDialogNavigationHelper.show(context, new AlertDialog.Builder(context)" `
            "External player install dialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\util\external_communication\KoreUtils.java" `
            "RokidDialogNavigationHelper.show(context, new AlertDialog.Builder(context)" `
            "Kore install dialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\migration\MigrationManager.java" `
            "RokidDialogNavigationHelper.attach(uiContext, dialog)" `
            "Migration info dialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidSnackbarHelper.java" `
            "com.google.android.material.R.id.snackbar_action" `
            "Snackbar actions are not made focusable for R08 navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\error\ErrorUtil.kt" `
            "RokidSnackbarHelper.show(" `
            "Error snackbar action is missing R08 focus handling"
    Assert-SourceContains `
            "app\src\main\java\us\shandian\giga\ui\adapter\MissionAdapter.java" `
            "RokidSnackbarHelper.show(mSnackbar)" `
            "Download undo snackbar is missing R08 focus handling"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\util\external_communication\ShareUtils.java" `
            "showRokidAppChooser(context, intent, setTitleChooser)" `
            "ShareUtils still depends only on system choosers in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\util\external_communication\ShareUtils.java" `
            "tryOpenRokidResolvedIntent(context, intent, true)" `
            "Implicit external intents can still fall into an Android system resolver in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\util\external_communication\ShareUtils.java" `
            "RokidDialogNavigationHelper.show(context, new AlertDialog.Builder(context)" `
            "R08 app chooser is missing dialog key navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\util\external_communication\ShareUtils.java" `
            "target.setSelector(null)" `
            "R08 app chooser explicit launch still carries Android selector state"
    Assert-SourceContains `
            "app\src\main\java\us\shandian\giga\ui\adapter\MissionAdapter.java" `
            "ShareUtils.openIntentChooser(mContext, viewIntent, true)" `
            "Download open-with flow still bypasses the R08 app chooser"
    Assert-SourceContains `
            "app\src\main\java\us\shandian\giga\ui\adapter\MissionAdapter.java" `
            "ShareUtils.openIntentChooser(mContext, shareIntent, false)" `
            "Download share flow still bypasses the R08 app chooser"
    Assert-SourceDoesNotContain `
            "app\src\main\java\us\shandian\giga\ui\adapter\MissionAdapter.java" `
            "createChooser" `
            "Download file actions still create a raw system chooser"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidFocusNavigator.java" `
            "View.IMPORTANT_FOR_ACCESSIBILITY_NO" `
            "RokidFocusNavigator does not respect Accessibility importance"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidFocusNavigator.java" `
            "return moved;" `
            "RokidFocusNavigator can consume a list swipe without moving focus"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidFocusNavigator.java" `
            "findActivatableAncestorOrSelf(current)" `
            "RokidFocusNavigator can activate a stale target when focus is on a child view"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidFocusNavigator.java" `
            "target.performLongClick()" `
            "RokidFocusNavigator cannot activate long-click-only R08 targets"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidFocusNavigator.java" `
            "focusSiblingTargetInRecyclerHolder" `
            "RokidFocusNavigator cannot reach secondary actions inside RecyclerView rows"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidFocusNavigator.java" `
            "current == targets.get(i)" `
            "RokidFocusNavigator does not prefer exact focused targets before parent rows"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidKeyboardController.java" `
            "getOverlayParent(activity, editText)" `
            "Rokid keyboard overlay is not attached to the active text-entry window"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidKeyboardController.java" `
            "LETTER_KEYS_NO_VOICE" `
            "Rokid keyboard exposes a dead voice key when no voice action is available"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidKeyboardController.java" `
            "enterKeyLabel" `
            "Rokid keyboard enter/go key is not context-labeled for non-search text fields"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidKeyboardController.java" `
            "R.string.rokid_keyboard_letter_key" `
            "Rokid keyboard key speech labels are not backed by string resources"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidKeyboardController.java" `
            "R.string.rokid_keyboard_selected_key" `
            "Rokid keyboard selected-key speech label is not backed by a string resource"
    Assert-SourceDoesNotContain `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidKeyboardController.java" `
            '"Selected " + label' `
            "Rokid keyboard selected-key speech is still hardcoded in Java"
    Assert-SourceDoesNotContain `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidKeyboardController.java" `
            '"Key " + key' `
            "Rokid keyboard letter-key speech is still hardcoded in Java"
    Assert-SourceDoesNotContain `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidKeyboardController.java" `
            "Search text" `
            "Rokid keyboard preview still announces every text field as search text"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\rokid\RokidTextInputHelper.java" `
            "getPositiveButtonLabel(activity, dialog)" `
            "Rokid text-entry dialogs do not announce their positive button as the keyboard action"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\download\DownloadDialog.java" `
            "R.string.rokid_tap_for_next_option" `
            "DownloadDialog R08 controls still use hardcoded action hints"
    Assert-SourceDoesNotContain `
            "app\src\main\java\org\schabi\newpipe\download\DownloadDialog.java" `
            "Tap for next option" `
            "DownloadDialog R08 content descriptions still contain hardcoded English action hints"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\player\helper\PlaybackParameterDialog.java" `
            "R.string.rokid_decrease" `
            "PlaybackParameterDialog R08 controls still use hardcoded action hints"
    Assert-SourceDoesNotContain `
            "app\src\main\java\org\schabi\newpipe\player\helper\PlaybackParameterDialog.java" `
            '"Selected"' `
            "PlaybackParameterDialog R08 selected state is still hardcoded in English"
    Assert-SourceContains `
            "app\src\main\res\values\strings.xml" `
            "rokid_move_down" `
            "R08 reorder action label is missing from string resources"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\subscription\SubscriptionsImportFragment.java" `
            "getString(R.string.import_title)" `
            "Subscription import URL keyboard does not announce Import as its action"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\dialog\PlaylistCreationDialog.java" `
            "RokidTextInputHelper.attachOnShow(requireActivity(), dialog, dialogBinding.dialogEditText)" `
            "Playlist creation dialog is missing the Rokid keyboard"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\playlist\LocalPlaylistFragment.java" `
            "RokidTextInputHelper.attach(requireActivity(), dialog, dialogBinding.dialogEditText)" `
            "Local playlist rename dialog is missing the Rokid keyboard"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\playlist\LocalPlaylistFragment.java" `
            "movePlaylistItemDown(viewHolder.getBindingAdapterPosition())" `
            "Local playlist reorder still requires drag in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\bookmark\BookmarkFragment.java" `
            "RokidTextInputHelper.attach(activity, dialog, dialogBinding.dialogEditText)" `
            "Bookmark rename dialog is missing the Rokid keyboard"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\bookmark\BookmarkFragment.java" `
            "moveBookmarkItemDown(viewHolder.getBindingAdapterPosition())" `
            "Bookmark reorder still requires drag in R08 mode"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\LocalItemListAdapter.java" `
            "moveItemDown(final int fromAdapterPosition)" `
            "Local list adapter lacks a discrete R08 reorder action"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\PeertubeInstanceListFragment.java" `
            "RokidTextInputHelper.attach(requireActivity(), dialog, dialogBinding.dialogEditText)" `
            "PeerTube instance dialog is missing the Rokid keyboard"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\subscription\dialog\FeedGroupDialog.kt" `
            "RokidTextInputHelper.show(requireActivity(), feedGroupCreateBinding.groupNameInput)" `
            "Feed group dialog is missing the Rokid keyboard"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\subscription\SubscriptionsImportFragment.java" `
            "RokidTextInputHelper.show(" `
            "Subscription import URL field is missing the Rokid keyboard"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\local\subscription\dialog\FeedGroupReorderDialog.kt" `
            "moveGroupDown(item.groupId)" `
            "Feed group reorder still lacks a non-drag R08 action"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\SelectChannelFragment.java" `
            "RokidDialogNavigationHelper.attach(requireActivity(), dialog)" `
            "SelectChannel dialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\tabs\AddTabDialog.java" `
            "RokidDialogNavigationHelper.attach((Activity) context, dialog)" `
            "AddTabDialog is missing R08 dialog navigation"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\tabs\ChooseTabsFragment.java" `
            "SelectedTabsAdapter.this.swapItems(fromPosition, toPosition)" `
            "ChooseTabsFragment still lacks a non-drag R08 reorder action"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\PeertubeInstanceListFragment.java" `
            "moveItemDown(getBindingAdapterPosition())" `
            "PeerTube instance list still lacks a non-drag R08 reorder action"
    Assert-SourceContains `
            "app\src\main\java\org\schabi\newpipe\settings\PeertubeInstanceListFragment.java" `
            "refreshSelectionState()" `
            "PeerTube instance selection does not refresh R08 accessibility state"
    Write-Host "Verified Rokid source guards"
}

function Assert-KeyboardAccessibility {
    param([string]$Path)
    $nodes = Get-UiNodes $Path
    $keyNodes = @($nodes | Where-Object {
        (Get-NodeAttribute $_ "package") -eq $Package -and
        (Get-NodeAttribute $_ "clickable") -eq "true" -and
        (Get-NodeAttribute $_ "focusable") -eq "true" -and
        ((Get-NodeLabel $_) -match "^(Selected )?(Key [a-z]|Space|Delete|Clear text|Voice search|Search|Numbers and symbols|Letters)$")
    })

    Assert-Condition ($keyNodes.Count -ge 30) "keyboard should expose at least 30 labeled focusable keys; found $($keyNodes.Count)"
    foreach ($label in @("Selected Key a", "Voice search", "Search", "Numbers and symbols")) {
        Assert-Condition (@($keyNodes | Where-Object { (Get-NodeLabel $_) -eq $label }).Count -gt 0) `
                "keyboard missing labeled key '$label'"
    }
    Write-Host "Verified keyboard Accessibility nodes: $($keyNodes.Count)"
}

function Assert-PlayerRailAccessibility {
    param([string]$Path)
    $nodes = Get-UiNodes $Path
    foreach ($id in @(
            "rokidActionPlayPause",
            "rokidActionFullscreen",
            "rokidActionQuality",
            "rokidActionSpeed",
            "rokidActionClose"
    )) {
        $resourceId = "${Package}:id/$id"
        $matches = @($nodes | Where-Object {
            (Get-NodeAttribute $_ "resource-id") -eq $resourceId -and
            (Get-NodeAttribute $_ "clickable") -eq "true" -and
            (Get-NodeAttribute $_ "focusable") -eq "true" -and
            (Get-NodeLabel $_).Length -gt 0
        })
        Assert-Condition ($matches.Count -gt 0) "player rail action '$id' is not labeled, clickable, and focusable"
    }
    Write-Host "Verified player rail Accessibility nodes"
}

function Assert-RokidPlayerMenuAccessibility {
    param(
        [string]$Path,
        [int]$MinimumRows
    )
    $nodes = Get-UiNodes $Path
    $overlayId = "${Package}:id/rokidPlayerMenuOverlay"
    Assert-Condition (@($nodes | Where-Object { (Get-NodeAttribute $_ "resource-id") -eq $overlayId }).Count -gt 0) `
            "Rokid player menu overlay is missing"

    $rows = @($nodes | Where-Object {
        (Test-HasAncestorResourceId $_ $overlayId) -and
        (Get-NodeAttribute $_ "clickable") -eq "true" -and
        (Get-NodeAttribute $_ "focusable") -eq "true" -and
        (Get-NodeLabel $_).Length -gt 0
    })
    Assert-Condition ($rows.Count -ge $MinimumRows) "Rokid player menu should expose at least $MinimumRows labeled rows; found $($rows.Count)"
    Write-Host "Verified Rokid player menu Accessibility rows: $($rows.Count)"
}

function Assert-NoUnnamedFocusableActions {
    param([string]$Path)
    $nodes = Get-UiNodes $Path
    $unnamedNodes = @($nodes | Where-Object {
        (Get-NodeAttribute $_ "package") -eq $Package -and
        ((Get-NodeAttribute $_ "clickable") -eq "true" -or
                (Get-NodeAttribute $_ "long-clickable") -eq "true") -and
        (Get-NodeAttribute $_ "focusable") -eq "true" -and
        (Get-NodeLabel $_).Length -eq 0
    })

    $summary = ($unnamedNodes | ForEach-Object {
        $id = Get-NodeAttribute $_ "resource-id"
        $class = Get-NodeAttribute $_ "class"
        $bounds = Get-NodeAttribute $_ "bounds"
        "$id $class $bounds".Trim()
    }) -join "; "
    Assert-Condition ($unnamedNodes.Count -eq 0) `
            "dump contains $($unnamedNodes.Count) focusable clickable app nodes without labels: $summary"
    Write-Host "Verified named focusable actions"
}

function Assert-DebugState {
    param(
        [string]$Path,
        [switch]$KeyboardVisible,
        [switch]$PlayerRailVisible,
        [switch]$PlayerMenuVisible,
        [switch]$RequireCustomSelectAction
    )
    $state = Get-Content -Path $Path -Raw | ConvertFrom-Json
    Assert-Condition ($state.rokidMode -eq $true) "debug state says Rokid mode is disabled"
    $unnamedSummary = (@($state.unnamedFocusableActions) | ForEach-Object {
        "$($_.idName) $($_.className) $($_.bounds)".Trim()
    }) -join "; "
    Assert-Condition (@($state.unnamedFocusableActions).Count -eq 0) `
            "debug state reports unnamed focusable actions: $unnamedSummary"
    if ($KeyboardVisible) {
        Assert-Condition ($state.keyboardVisible -eq $true) "debug state says keyboard is hidden"
    }
    if ($PlayerRailVisible) {
        Assert-Condition ($state.playerRailVisible -eq $true) "debug state says player rail is hidden"
    }
    if ($PlayerMenuVisible) {
        Assert-Condition ($state.playerMenuVisible -eq $true) "debug state says player menu is hidden"
    }
    if ($RequireCustomSelectAction) {
        $customSelectActions = @($state.focusableActions | Where-Object {
            @($_.accessibilityActions | Where-Object {
                $_.idName -eq "${Package}:id/rokid_accessibility_select" -and
                        $_.label -eq "Select"
            }).Count -gt 0
        })
        Assert-Condition ($customSelectActions.Count -gt 0) `
                "debug state does not expose the Rokid custom Select accessibility action"
    }
    Write-Host "Verified Rokid debug state"
}

function Send-Key {
    param([int]$KeyCode)
    Invoke-GlassesAdb -Arguments @("shell", "input", "keyevent", "$KeyCode") | Out-Null
    Start-Sleep -Milliseconds 500
}

function Focus-ActionByLabel {
    param(
        [string]$Label,
        [string]$ArtifactName,
        [int]$DirectionKey = 21,
        [int]$MaxMoves = 12
    )
    $lastState = $null
    for ($move = 0; $move -le $MaxMoves; $move++) {
        $statePath = Save-DebugState $ArtifactName
        $lastState = Get-Content -Path $statePath -Raw | ConvertFrom-Json
        if ($lastState.focused.label -eq $Label) {
            Save-DebugState "$ArtifactName-focused" | Out-Null
            return
        }
        if ($move -lt $MaxMoves) {
            Send-Key $DirectionKey
        }
    }

    $labels = (@($lastState.focusableActions) | ForEach-Object { $_.label }) -join ", "
    Assert-Condition $false `
            "expected focus on '$Label' after $MaxMoves moves; focused '$($lastState.focused.label)'; visible actions: $labels"
}

function Assert-TopResumedContains {
    param(
        [string]$Pattern,
        [string]$Message
    )
    $topResumed = Invoke-GlassesAdb -Arguments @(
            "shell", "dumpsys", "activity", "activities"
    ) | Select-String -Pattern "topResumedActivity" | Select-Object -First 1
    Assert-Condition ($null -ne $topResumed -and $topResumed.Line -match $Pattern) `
            "$Message; topResumedActivity was '$($topResumed.Line)'"
}

function Show-PlayerRail {
    Invoke-GlassesAdb -Arguments @("shell", "input", "tap", "240", "135") | Out-Null
    Start-Sleep -Seconds 1
}

function Invoke-RailAction {
    param([int]$RightSwipes)
    Start-Sleep -Seconds 9
    Show-PlayerRail
    for ($index = 0; $index -lt $RightSwipes; $index++) {
        Send-Key 22
    }
    Send-Key 23
    Start-Sleep -Seconds 1
}

Write-Host "RokidPipe regression smoke"
Write-Host "Package: $Package"
Write-Host "Scenarios:"
Write-Host " - app-launch-smoke"
Write-Host " - search-keyboard-swipe"
Write-Host " - offline-wifi-recovery"
Write-Host " - r08-accessibility-tree"
Write-Host " - secondary-r08-surfaces"
if ($RunPlayer) {
    Write-Host " - player-rail-actions"
}

Assert-NoTodoContentDescriptions
Assert-RokidSourceGuards

if (-not $SkipBuild) {
    Push-Location $Root
    try {
        & .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace -DskipFormatKtlint
    } finally {
        Pop-Location
    }
}

Assert-AdbDeviceAvailable

if (-not $SkipInstall) {
    Invoke-GlassesAdb -Arguments @("install", "-r", $Apk)
}

Invoke-GlassesAdb -Arguments @("logcat", "-c") | Out-Null
Invoke-GlassesAdb -Arguments @("shell", "am", "force-stop", $Package) | Out-Null
Invoke-GlassesAdb -Arguments @(
        "shell", "monkey", "-p", $Package, "-c", "android.intent.category.LAUNCHER", "1"
) | Out-Null
Start-Sleep -Seconds 10
Save-DeviceScreenshot "01-launch"
Save-WindowState "01-launch"

Focus-ActionByLabel "Search" "02-search-focus" 21
Send-Key 23
Start-Sleep -Seconds 1
Save-DeviceScreenshot "02-search-keyboard"
$keyboardDump = Save-UiDump "02-search-keyboard"
Assert-KeyboardAccessibility $keyboardDump
Assert-NoUnnamedFocusableActions $keyboardDump
$keyboardState = Save-DebugState "02-search-keyboard"
Assert-DebugState $keyboardState -KeyboardVisible -RequireCustomSelectAction

Invoke-GlassesAdb -Arguments @("shell", "input", "keyevent", "4") | Out-Null
Start-Sleep -Milliseconds 800
$wifiWasEnabled = Get-WifiEnabled
try {
    if ($wifiWasEnabled) {
        Set-WifiEnabled $false
        Start-Sleep -Seconds 6
    }
    Invoke-GlassesAdb -Arguments @("shell", "pm", "clear", $Package) | Out-Null
    Invoke-GlassesAdb -Arguments @("shell", "am", "force-stop", $Package) | Out-Null
    Invoke-GlassesAdb -Arguments @(
            "shell", "monkey", "-p", $Package, "-c", "android.intent.category.LAUNCHER", "1"
    ) | Out-Null
    Start-Sleep -Seconds 12
    Save-WindowState "03-wifi-recovery"
    Save-DeviceScreenshot "03-wifi-recovery"
    Focus-ActionByLabel "Wi-Fi" "03-wifi-focus" 22
    Send-Key 23
    Start-Sleep -Seconds 2
    Save-WindowState "03-wifi-settings-open"
    Save-DeviceScreenshot "03-wifi-settings-open"
    Start-Sleep -Seconds 2
    Assert-TopResumedContains "com\.android\.settings" `
            "Wi-Fi button did not open Android Settings directly"
} finally {
    Invoke-GlassesAdb -Arguments @("shell", "input", "keyevent", "4") | Out-Null
    if ($wifiWasEnabled) {
        Set-WifiEnabled $true
        Start-Sleep -Seconds 3
    }
}

if ($RunPlayer) {
    Invoke-GlassesAdb -Arguments @(
            "shell",
            "am",
            "start",
            "-a",
            "android.intent.action.VIEW",
            "-d",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "-p",
            $Package
    ) | Out-Null
    Start-Sleep -Seconds 3
    Invoke-GlassesAdb -Arguments @("shell", "input", "tap", "210", "224") | Out-Null
    Start-Sleep -Milliseconds 600
    Invoke-GlassesAdb -Arguments @("shell", "input", "tap", "180", "574") | Out-Null
    Start-Sleep -Seconds 8
    Show-PlayerRail
    Save-DeviceScreenshot "04-player-rail"
    $playerRailDump = Save-UiDump "04-player-rail"
    Assert-PlayerRailAccessibility $playerRailDump
    Assert-NoUnnamedFocusableActions $playerRailDump
    $playerRailState = Save-DebugState "04-player-rail"
    Assert-DebugState $playerRailState -PlayerRailVisible -RequireCustomSelectAction

    Start-Sleep -Seconds 12
    Save-DeviceScreenshot "04-player-after-hide"

    Invoke-RailAction 2
    Save-DeviceScreenshot "05-player-quality-menu"
    $qualityMenuDump = Save-UiDump "05-player-quality-menu"
    Assert-RokidPlayerMenuAccessibility $qualityMenuDump 1
    Assert-NoUnnamedFocusableActions $qualityMenuDump
    $qualityMenuState = Save-DebugState "05-player-quality-menu"
    Assert-DebugState $qualityMenuState -PlayerMenuVisible -RequireCustomSelectAction
    Send-Key 4

    Invoke-RailAction 3
    Save-DeviceScreenshot "06-player-speed-menu"
    $speedMenuDump = Save-UiDump "06-player-speed-menu"
    Assert-RokidPlayerMenuAccessibility $speedMenuDump 7
    Assert-NoUnnamedFocusableActions $speedMenuDump
    $speedMenuState = Save-DebugState "06-player-speed-menu"
    Assert-DebugState $speedMenuState -PlayerMenuVisible -RequireCustomSelectAction
    Send-Key 4

    Invoke-RailAction 4
    Save-DeviceScreenshot "07-player-subs-menu"
    $subsMenuDump = Save-UiDump "07-player-subs-menu"
    Assert-RokidPlayerMenuAccessibility $subsMenuDump 1
    Assert-NoUnnamedFocusableActions $subsMenuDump
    $subsMenuState = Save-DebugState "07-player-subs-menu"
    Assert-DebugState $subsMenuState -PlayerMenuVisible -RequireCustomSelectAction
    Send-Key 4

    Invoke-RailAction 1
    Save-DeviceScreenshot "08-player-fullscreen"
    Save-UiDump "08-player-fullscreen"
}

Invoke-GlassesAdb -Arguments @("logcat", "-d", "-v", "time") |
        Out-File -Encoding utf8 (Join-Path $Artifacts "glasses-logcat.txt")
Write-Host "Done. Artifacts: $Artifacts"
