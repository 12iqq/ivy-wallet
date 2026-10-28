<#
Installs the app from the installer without any windows, checks its shortcuts, then removes it with the
"Uninstall Ivy Wallet UAE" shortcut and checks that nothing is left behind. For CI.
#>
param(
    [Parameter(Mandatory = $true)][string] $MsiDir
)
$ErrorActionPreference = 'Stop'

$msi = (Get-ChildItem -Path $MsiDir -Filter '*.msi' | Select-Object -First 1).FullName
$menu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Ivy Wallet UAE'
$desktopShortcut = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Ivy Wallet UAE.lnk'

function Run([string] $file, [string[]] $arguments) {
    $process = Start-Process -FilePath $file -ArgumentList $arguments -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "$file $arguments ended with $($process.ExitCode)" }
}

Run 'msiexec.exe' @('/i', "`"$msi`"", '/qn')

$exe = Get-ChildItem -Path $env:LOCALAPPDATA -Filter 'Ivy Wallet UAE.exe' -Recurse -Depth 2 -ErrorAction SilentlyContinue |
    Select-Object -First 1
if (-not $exe) { throw 'The app was not installed in the user folder' }
Write-Host "Installed in $($exe.DirectoryName)"
Get-ChildItem $menu | ForEach-Object { Write-Host "Start menu: $($_.Name)" }
if (-not (Test-Path (Join-Path $menu 'Ivy Wallet UAE.lnk'))) { throw 'No Start menu shortcut for the app' }
$uninstall = Join-Path $menu 'Uninstall Ivy Wallet UAE.lnk'
if (-not (Test-Path $uninstall)) { throw 'No Uninstall shortcut in the Start menu' }
if (-not (Test-Path $desktopShortcut)) { throw 'No desktop shortcut' }

$shortcut = (New-Object -ComObject WScript.Shell).CreateShortcut($uninstall)
Write-Host "Uninstall shortcut runs: $($shortcut.TargetPath) $($shortcut.Arguments)"
Run $shortcut.TargetPath (@($shortcut.Arguments -split ' ') + '/qn')

if (Test-Path $exe.DirectoryName) { throw "$($exe.DirectoryName) is still there after uninstalling" }
if (Test-Path $menu) { throw 'The Start menu folder is still there after uninstalling' }
if (Test-Path $desktopShortcut) { throw 'The desktop shortcut is still there after uninstalling' }
Write-Host 'Installed with both Start menu shortcuts, and the Uninstall shortcut removed everything'
