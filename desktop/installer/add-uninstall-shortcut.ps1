<#
Adds "Uninstall Ivy Wallet UAE" to the app's folder in the Start menu, next to the app. jpackage makes the
installer (.msi) without one, so this adds the shortcut to the finished installer's tables. Windows Installer
then creates it when installing, and removes it when uninstalling, like the app's own shortcut.
#>
param(
    [Parameter(Mandatory = $true)][string] $MsiDir
)
$ErrorActionPreference = 'Stop'

$msi = Get-ChildItem -Path $MsiDir -Filter '*.msi' | Select-Object -First 1
if (-not $msi) { throw "No installer (.msi) in $MsiDir" }

$installer = New-Object -ComObject WindowsInstaller.Installer

function Call($target, [string] $name, [object[]] $arguments = @()) {
    $target.GetType().InvokeMember($name, [Reflection.BindingFlags]::InvokeMethod, $null, $target, $arguments)
}

function SetField($record, [int] $field, $value) {
    $property = if ($value -is [int]) { 'IntegerData' } else { 'StringData' }
    [void] $record.GetType().InvokeMember(
        $property, [Reflection.BindingFlags]::SetProperty, $null, $record, @($field, $value))
}

function GetField($record, [int] $field) {
    $record.GetType().InvokeMember('StringData', [Reflection.BindingFlags]::GetProperty, $null, $record, @($field))
}

# 1: open it to make changes, saved by Commit
$db = Call $installer 'OpenDatabase' @($msi.FullName, 1)

# Runs a query, with a value for each ? in it. Gives the first column of each row a SELECT finds.
function Sql([string] $query, [object[]] $values = @()) {
    $view = Call $db 'OpenView' @($query)
    if ($values.Count -gt 0) {
        $record = Call $installer 'CreateRecord' @($values.Count)
        for ($i = 0; $i -lt $values.Count; $i++) { SetField $record ($i + 1) $values[$i] }
        [void] (Call $view 'Execute' @($record))
    } else {
        [void] (Call $view 'Execute')
    }
    $rows = @()
    if ($query.StartsWith('SELECT')) {
        while ($row = Call $view 'Fetch') { $rows += GetField $row 1 }
    }
    [void] (Call $view 'Close')
    return , $rows
}

if ((Sql 'SELECT `Shortcut` FROM `Shortcut` WHERE `Shortcut` = ?' @('IvyUninstall')).Count -gt 0) {
    Write-Host "$($msi.Name) already has the Uninstall shortcut"
    return
}

# the app's folder in the Start menu, made by jpackage for its own shortcut (menu = true)
$menu = (Sql 'SELECT `Directory` FROM `Directory` WHERE `Directory_Parent` = ?' @('ProgramMenuFolder'))[0]
if (-not $menu) { throw 'The installer has no folder in the Start menu: is menu = true in build.gradle.kts?' }
$feature = (Sql 'SELECT `Feature` FROM `Feature`')[0]

# The same id in every version, so an update replaces the shortcut instead of adding a second one.
# Attributes 260: 64-bit, and kept track of by the registry value below, as shortcuts for one user must be.
Sql 'INSERT INTO `Component` (`Component`, `ComponentId`, `Directory_`, `Attributes`, `KeyPath`) VALUES (?, ?, ?, ?, ?)' `
    @('IvyUninstall', '{6E2C4A57-3B8D-4C1F-9A3E-7D5B2F0C8E41}', $menu, 260, 'IvyUninstallKey') | Out-Null
# Root 1: HKEY_CURRENT_USER
Sql 'INSERT INTO `Registry` (`Registry`, `Root`, `Key`, `Name`, `Value`, `Component_`) VALUES (?, ?, ?, ?, ?, ?)' `
    @('IvyUninstallKey', 1, 'Software\Ivy Wallet UAE', 'UninstallShortcut', '#1', 'IvyUninstall') | Out-Null
Sql 'INSERT INTO `Shortcut` (`Shortcut`, `Directory_`, `Name`, `Component_`, `Target`, `Arguments`, `Description`) VALUES (?, ?, ?, ?, ?, ?, ?)' `
    @('IvyUninstall', $menu, 'UNINST~1|Uninstall Ivy Wallet UAE', 'IvyUninstall', '[System64Folder]msiexec.exe',
        '/x [ProductCode]', 'Removes Ivy Wallet UAE from this computer') | Out-Null
Sql 'INSERT INTO `FeatureComponents` (`Feature_`, `Component_`) VALUES (?, ?)' @($feature, 'IvyUninstall') | Out-Null

[void] (Call $db 'Commit')
[void] [Runtime.InteropServices.Marshal]::ReleaseComObject($db)
Write-Host "Added the Uninstall shortcut to $($msi.Name)"
