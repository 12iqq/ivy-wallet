<#
Installs the app from the installer without any windows and checks what it put where. Then removes it with the
"Uninstall Ivy Wallet UAE" shortcut, installs it again and removes it with "Uninstall Ivy Wallet UAE.exe", and checks
that nothing is left behind each time. For CI.
#>
param(
    [Parameter(Mandatory = $true)][string] $MsiDir
)
$ErrorActionPreference = 'Stop'
# shows where it went wrong, inside functions too
trap {
    Write-Host "$_`n$($_.ScriptStackTrace)"
    exit 1
}
. (Join-Path $PSScriptRoot 'msi.ps1')

$msi = (Get-ChildItem -Path $MsiDir -Filter '*.msi' | Select-Object -First 1).FullName
$version = MsiProperty $msi 'ProductVersion'
$upgradeCode = MsiProperty $msi 'UpgradeCode'
$menu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Ivy Wallet UAE'
$desktopShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Ivy Wallet UAE.lnk'

function Run([string] $file, [string[]] $arguments) {
    $process = Start-Process -FilePath $file -ArgumentList $arguments -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "$file $arguments ended with $($process.ExitCode)" }
}

function Install {
    Run 'msiexec.exe' @('/i', "`"$msi`"", '/qn')
    $exe = Get-ChildItem -Path $env:LOCALAPPDATA -Filter 'Ivy Wallet UAE.exe' -Recurse -Depth 2 `
        -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $exe) { throw 'The app was not installed in the user folder' }
    Write-Host "Installed $version in $($exe.DirectoryName)"
    return $exe.DirectoryName
}

function ExpectRemoved([string] $folder) {
    if (Test-Path $folder) { throw "$folder is still there after uninstalling" }
    if (Test-Path $menu) { throw 'The Start menu folder is still there after uninstalling' }
    if (Test-Path $desktopShortcut) { throw 'The desktop shortcut is still there after uninstalling' }
    if ((InstalledVersions $upgradeCode).Count -gt 0) { throw 'Windows still lists the app after uninstalling' }
}

$folder = Install
Get-ChildItem $menu | ForEach-Object { Write-Host "Start menu: $($_.Name)" }
if (-not (Test-Path (Join-Path $menu 'Ivy Wallet UAE.lnk'))) { throw 'No Start menu shortcut for the app' }
$uninstallShortcut = Join-Path $menu 'Uninstall Ivy Wallet UAE.lnk'
if (-not (Test-Path $uninstallShortcut)) { throw 'No Uninstall shortcut in the Start menu' }
if (-not (Test-Path $desktopShortcut)) { throw 'No desktop shortcut' }
$uninstaller = Join-Path $folder 'Uninstall Ivy Wallet UAE.exe'
if (-not (Test-Path $uninstaller)) { throw 'No Uninstall Ivy Wallet UAE.exe next to the app' }
# for the next installer, to say which version is installed
$registered = (Get-ItemProperty -Path 'HKCU:\Software\Ivy Wallet UAE').Version
if ($registered -ne $version) { throw "The registry has version $registered instead of $version" }

$shortcut = (New-Object -ComObject WScript.Shell).CreateShortcut($uninstallShortcut)
Write-Host "Uninstall shortcut runs: $($shortcut.TargetPath) $($shortcut.Arguments)"
Run $shortcut.TargetPath (@($shortcut.Arguments -split ' ') + '/qn')
ExpectRemoved $folder
Write-Host 'The Uninstall shortcut removed everything'

$folder = Install
# It starts Windows Installer and ends straight away, so that its own file can be removed. Start-Process -Wait waits
# for Windows Installer too, but in case it doesn't:
Run $uninstaller @('/quiet')
$deadline = (Get-Date).AddMinutes(3)
while (((Test-Path $folder) -or (InstalledVersions $upgradeCode).Count -gt 0) -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 1
}
ExpectRemoved $folder
Write-Host 'Uninstall Ivy Wallet UAE.exe removed everything'
