<#
Adds to the installer (.msi) jpackage makes what jpackage can't do by itself, by changing the installer's tables.
Windows Installer reads them when it installs, updates or removes the app.

- "Uninstall Ivy Wallet UAE.exe" next to the app's exe, instead of in app\resources where jpackage puts it
- "Uninstall Ivy Wallet UAE" in the app's folder in the Start menu, next to the app
- the version in the registry, so the next installer can say which version is installed
- a first page for when the app is already installed. With another version installed, it offers Update (or Replace,
  if the installed one is newer) and Uninstall. With this version, Repair and Uninstall. Without it, the installer
  only has jpackage's pages: Welcome, then where to install, and Install.
#>
param(
    [Parameter(Mandatory = $true)][string] $MsiDir
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'msi.ps1')

$msi = Get-ChildItem -Path $MsiDir -Filter '*.msi' | Select-Object -First 1
if (-not $msi) { throw "No installer (.msi) in $MsiDir" }

# 1: open it to make changes, saved by Commit
$db = Call $Installer 'OpenDatabase' @($msi.FullName, 1)

function Has([string] $table, [string] $key, [string] $value) {
    (Sql $db "SELECT ``$key`` FROM ``$table`` WHERE ``$key`` = ?" @($value)).Count -gt 0
}

function Insert([string] $table, [string[]] $columns, [object[]] $values) {
    $names = ($columns | ForEach-Object { "``$_``" }) -join ', '
    $marks = (@('?') * $columns.Count) -join ', '
    [void] (Sql $db "INSERT INTO ``$table`` ($names) VALUES ($marks)" $values)
}

function SetCondition([string] $action, [string] $condition) {
    [void] (Sql $db 'UPDATE `InstallUISequence` SET `Condition` = ? WHERE `Action` = ?' @($condition, $action))
}

# --- "Uninstall Ivy Wallet UAE.exe" next to the app's exe

# It's one of the app's resources for jpackage, so it's in app\resources until its component moves to INSTALLDIR, the
# app's folder. A file's name is "short|long", or one name if it's short already.
$files = Rows $db 'SELECT `Component_`, `FileName` FROM `File`'
$uninstaller = @($files | Where-Object { ($_[1] -split '\|')[-1] -eq 'Uninstall Ivy Wallet UAE.exe' })
if ($uninstaller.Count -ne 1) {
    throw 'The installer has no Uninstall Ivy Wallet UAE.exe: is it in appResourcesRootDir in build.gradle.kts?'
}
[void] (Sql $db 'UPDATE `Component` SET `Directory_` = ? WHERE `Component` = ?' @('INSTALLDIR', $uninstaller[0][0]))

# --- "Uninstall Ivy Wallet UAE" in the Start menu

if (Has 'Shortcut' 'Shortcut' 'IvyUninstall') {
    Write-Host "$($msi.Name) already has the Uninstall shortcut"
} else {
    # the app's folder in the Start menu, made by jpackage for its own shortcut (menu = true)
    $menu = (Sql $db 'SELECT `Directory` FROM `Directory` WHERE `Directory_Parent` = ?' @('ProgramMenuFolder'))[0]
    if (-not $menu) { throw 'The installer has no folder in the Start menu: is menu = true in build.gradle.kts?' }
    $feature = (Sql $db 'SELECT `Feature` FROM `Feature`')[0]

    # The same id in every version, so an update replaces the shortcut instead of adding a second one.
    # Attributes 260: 64-bit, and kept track of by the registry value below, as shortcuts for one user must be.
    Insert 'Component' @('Component', 'ComponentId', 'Directory_', 'Attributes', 'KeyPath') `
        @('IvyUninstall', '{6E2C4A57-3B8D-4C1F-9A3E-7D5B2F0C8E41}', $menu, 260, 'IvyUninstallKey')
    # Root 1: HKEY_CURRENT_USER
    Insert 'Registry' @('Registry', 'Root', 'Key', 'Name', 'Value', 'Component_') `
        @('IvyUninstallKey', 1, 'Software\Ivy Wallet UAE', 'UninstallShortcut', '#1', 'IvyUninstall')
    Insert 'Shortcut' @('Shortcut', 'Directory_', 'Name', 'Component_', 'Target', 'Arguments', 'Description') `
        @('IvyUninstall', $menu, 'UNINST~1|Uninstall Ivy Wallet UAE', 'IvyUninstall', '[System64Folder]msiexec.exe',
            '/x [ProductCode]', 'Removes Ivy Wallet UAE from this computer')
    Insert 'FeatureComponents' @('Feature_', 'Component_') @($feature, 'IvyUninstall')
    Write-Host "Added the Uninstall shortcut to $($msi.Name)"
}

# --- the page for when the app is already installed

if (Has 'Dialog' 'Dialog' 'IvyInstalledDlg') {
    Write-Host "$($msi.Name) already has the page for when the app is installed"
} else {
    # HKEY_CURRENT_USER\Software\Ivy Wallet UAE, Version: written by installers from 1.2.2 on, and read back by the
    # next one into IVY_INSTALLED_VERSION. Type 18: the value as it is, from the 64-bit registry.
    Insert 'Registry' @('Registry', 'Root', 'Key', 'Name', 'Value', 'Component_') `
        @('IvyVersion', 1, 'Software\Ivy Wallet UAE', 'Version', '[ProductVersion]', 'IvyUninstall')
    Insert 'RegLocator' @('Signature_', 'Root', 'Key', 'Name', 'Type') `
        @('IvyInstalledVersion', 1, 'Software\Ivy Wallet UAE', 'Version', 18)
    Insert 'AppSearch' @('Property', 'Signature_') @('IVY_INSTALLED_VERSION', 'IvyInstalledVersion')

    # What the page says depends on what's installed, so it's set just before the page opens. Windows Installer finds
    # other versions by the upgrade code: JP_UPGRADABLE_FOUND has an older one, JP_DOWNGRADABLE_FOUND a newer one.
    $older = 'NOT Installed AND JP_UPGRADABLE_FOUND'
    $newer = 'NOT Installed AND JP_DOWNGRADABLE_FOUND'
    $texts = @(
        @('IVY_INSTALLED_TEXT', "$older AND IVY_INSTALLED_VERSION",
            'Version [IVY_INSTALLED_VERSION] is on this computer. This installer has version [ProductVersion].'),
        @('IVY_INSTALLED_TEXT', "$older AND NOT IVY_INSTALLED_VERSION",
            'An older version is on this computer. This installer has version [ProductVersion].'),
        @('IVY_FIRST_LABEL', $older, '&Update'),
        @('IVY_FIRST_TEXT', $older, ('Installs version [ProductVersion] in place of the older one. ' +
            'Your backups and settings stay as they are.')),
        @('IVY_INSTALLED_TEXT', "$newer AND IVY_INSTALLED_VERSION", ('Version [IVY_INSTALLED_VERSION] is on this ' +
            'computer. This installer has an older version, [ProductVersion].')),
        @('IVY_INSTALLED_TEXT', "$newer AND NOT IVY_INSTALLED_VERSION",
            'A newer version is on this computer. This installer has an older version, [ProductVersion].'),
        @('IVY_FIRST_LABEL', $newer, '&Replace'),
        @('IVY_FIRST_TEXT', $newer,
            'Installs this older version in place of the newer one. Your backups and settings stay as they are.'),
        @('IVY_INSTALLED_TEXT', 'Installed',
            'Version [ProductVersion] is on this computer, the same as this installer.'),
        @('IVY_FIRST_LABEL', 'Installed', 'Re&pair'),
        @('IVY_FIRST_TEXT', 'Installed',
            'Puts back any of its files that are missing or broken, for example if it doesn''t open anymore.')
    )
    $sequence = 1280
    foreach ($text in $texts) {
        $sequence++
        $action = "IvyText$sequence"
        # 51: sets a property
        Insert 'CustomAction' @('Action', 'Type', 'Source', 'Target') @($action, 51, $text[0], $text[2])
        Insert 'InstallUISequence' @('Action', 'Condition', 'Sequence') @($action, $text[1], $sequence)
    }

    # Laid out like jpackage's "Change, repair, or remove installation" page, with the same banner
    Insert 'Dialog' @('Dialog', 'HCentering', 'VCentering', 'Width', 'Height', 'Attributes', 'Title',
        'Control_First', 'Control_Default', 'Control_Cancel') `
        @('IvyInstalledDlg', 50, 50, 370, 270, 7, '[ProductName] Setup', 'FirstButton', 'FirstButton', 'Cancel')
    # Name, type, x, y, width, height, attributes, text, and the next one for the Tab key.
    # Attributes: 1 shown, 2 enabled, 0x10000 see-through (over the banner), 0x20000 shows & as it is.
    $controls = @(
        @('FirstButton', 'PushButton', 40, 65, 80, 17, 3, '[IVY_FIRST_LABEL]', 'UninstallButton'),
        @('FirstText', 'Text', 60, 85, 280, 30, 131075, '[IVY_FIRST_TEXT]', $null),
        @('UninstallButton', 'PushButton', 40, 118, 80, 17, 3, 'U&ninstall', 'Cancel'),
        @('UninstallText', 'Text', 60, 138, 280, 30, 131075,
            'Removes [ProductName] from this computer. Your backups stay where they are.', $null),
        @('Cancel', 'PushButton', 304, 243, 56, 17, 3, 'Cancel', 'BannerBitmap'),
        # in the Tab order like on jpackage's pages, so it's made before the title that goes over it
        @('BannerBitmap', 'Bitmap', 0, 0, 370, 44, 1, 'WixUI_Bmp_Banner', 'FirstButton'),
        @('BannerLine', 'Line', 0, 44, 370, 0, 1, $null, $null),
        @('BottomLine', 'Line', 0, 234, 370, 0, 1, $null, $null),
        @('Title', 'Text', 15, 6, 340, 15, 196611, '{\WixUI_Font_Title}[ProductName] is already installed', $null),
        @('Description', 'Text', 25, 23, 340, 20, 196611, '[IVY_INSTALLED_TEXT]', $null)
    )
    foreach ($control in $controls) {
        $columns = @('Dialog_', 'Control', 'Type', 'X', 'Y', 'Width', 'Height', 'Attributes')
        $values = @('IvyInstalledDlg') + $control[0..6]
        if ($control[7]) { $columns += 'Text'; $values += $control[7] }
        if ($control[8]) { $columns += 'Control_Next'; $values += $control[8] }
        Insert 'Control' $columns $values
    }

    # What the buttons do: control, event, argument, condition, order
    $events = @(
        @('Cancel', 'SpawnDialog', 'CancelDlg', '1', 1),
        # another version: Update or Replace installs this one, which removes the other one first
        @('FirstButton', 'EndDialog', 'Return', 'NOT Installed', 1),
        # this version: Repair, like the Repair button on jpackage's pages
        @('FirstButton', 'ReinstallMode', 'ecmus', 'Installed', 1),
        @('FirstButton', 'Reinstall', 'All', 'Installed', 2),
        @('FirstButton', 'EndDialog', 'Return', 'Installed', 3),
        @('UninstallButton', 'Remove', 'All', 'Installed', 1),
        @('UninstallButton', 'EndDialog', 'Return', 'Installed', 2),
        # another version: Windows Installer removes that one, in a window of its own, and this installer closes
        @('UninstallButton', '[IVY_UNINSTALL_OTHER]', '1', 'NOT Installed', 1),
        @('UninstallButton', 'DoAction', 'IvyUninstallOther', 'NOT Installed', 2),
        @('UninstallButton', 'EndDialog', 'Exit', 'NOT Installed', 3)
    )
    foreach ($row in $events) {
        Insert 'ControlEvent' @('Dialog_', 'Control_', 'Event', 'Argument', 'Condition', 'Ordering') `
            (@('IvyInstalledDlg') + $row)
    }
    # 34: runs a program, in TARGETDIR (the root of a drive), and 192: doesn't wait for it
    Insert 'CustomAction' @('Action', 'Type', 'Source', 'Target') @('IvyUninstallOther', 226, 'TARGETDIR',
        '"[System64Folder]msiexec.exe" /x [JP_UPGRADABLE_FOUND][JP_DOWNGRADABLE_FOUND]')

    # The page opens instead of jpackage's Welcome page when another version is installed, and instead of its
    # maintenance pages when this version is.
    Insert 'InstallUISequence' @('Action', 'Condition', 'Sequence') @('IvyInstalledDlg',
        ('(Installed AND NOT RESUME AND NOT Preselected AND NOT PATCH) OR ' +
        '(NOT Installed AND (JP_UPGRADABLE_FOUND OR JP_DOWNGRADABLE_FOUND))'), 1295)
    [void] (Sql $db 'DELETE FROM `InstallUISequence` WHERE `Action` = ?' @('MaintenanceWelcomeDlg'))
    SetCondition 'WelcomeDlg' '(NOT Installed OR PATCH) AND NOT JP_UPGRADABLE_FOUND AND NOT JP_DOWNGRADABLE_FOUND'
    # no "Setup was interrupted" page after Uninstall closes this installer
    SetCondition 'UserExit' 'NOT IVY_UNINSTALL_OTHER'
    Write-Host "Added the page for when the app is installed to $($msi.Name)"
}

[void] (Call $db 'Commit')
[void] [Runtime.InteropServices.Marshal]::ReleaseComObject($db)
