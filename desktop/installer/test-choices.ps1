<#
Opens the installer like people do, with its pages, and clicks through them, for CI:

0. With nothing installed: the usual pages, Welcome, where to install, and Install
1. With an older version installed (from the newest release before this one): Update installs this version in its
   place
2. With this version installed: Repair puts back a deleted file, then Uninstall removes the app
3. With the older version installed again: Uninstall removes it, after Windows asks if you're sure

Saves a picture of each page in the Screenshots folder. Gets the older version with gh, which needs GH_TOKEN.
#>
param(
    [Parameter(Mandatory = $true)][string] $MsiDir,
    [Parameter(Mandatory = $true)][string] $Screenshots
)
$ErrorActionPreference = 'Stop'
# shows where it went wrong, inside functions too
trap {
    Write-Host "$_`n$($_.ScriptStackTrace)"
    exit 1
}
. (Join-Path $PSScriptRoot 'msi.ps1')
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes, System.Drawing

$msi = (Get-ChildItem -Path $MsiDir -Filter '*.msi' | Select-Object -First 1).FullName
$version = MsiProperty $msi 'ProductVersion'
$upgradeCode = MsiProperty $msi 'UpgradeCode'
$folder = Join-Path $env:LOCALAPPDATA 'Ivy Wallet UAE'
$setup = 'Ivy Wallet UAE Setup'
New-Item -ItemType Directory -Force -Path $Screenshots | Out-Null

$automation = [Windows.Automation.AutomationElement]
$scope = [Windows.Automation.TreeScope]
Add-Type -Namespace Win32 -Name Window -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool PostMessage(IntPtr window, uint message, IntPtr w, IntPtr l);
[DllImport("user32.dll")] public static extern int GetDlgCtrlID(IntPtr control);
[DllImport("user32.dll")] public static extern IntPtr GetParent(IntPtr control);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
'@

function Run([string] $file, [string[]] $arguments) {
    $process = Start-Process -FilePath $file -ArgumentList $arguments -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "$file $arguments ended with $($process.ExitCode)" }
}

# The installer of the newest release before this version that has one
function OlderInstaller {
    $dir = Join-Path ([IO.Path]::GetTempPath()) 'ivy-wallet-older'
    if (Test-Path $dir) { Remove-Item -Recurse -Force $dir }
    $tags = @(gh release list --limit 100 --json tagName --jq '.[].tagName')
    $older = @($tags |
        Where-Object { $_ -match '^v(\d+\.\d+\.\d+)$' -and [version] $Matches[1] -lt [version] $version } |
        Sort-Object { [version] $_.Substring(1) } -Descending)
    foreach ($tag in $older) {
        $assets = @(gh release view $tag --json assets --jq '.assets[].name')
        if ($assets -match '^IvyWalletUAE-Desktop-.+\.msi$') {
            gh release download $tag --pattern 'IvyWalletUAE-Desktop-*.msi' --dir $dir
            if ($LASTEXITCODE -ne 0) { throw "Couldn't download the installer of $tag" }
            return (Get-ChildItem -Path $dir -Filter '*.msi' | Select-Object -First 1).FullName
        }
    }
    throw "No release before $version has a Windows installer"
}

function OpenInstaller {
    $process = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', "`"$msi`"") -PassThru
    # so that ExitCode is there once it ends
    $null = $process.Handle
    return $process
}

function ExpectExit($process, [int] $code) {
    if (-not $process.WaitForExit(180000)) { throw 'The installer is still open after 3 minutes' }
    if ($process.ExitCode -ne $code) { throw "The installer ended with $($process.ExitCode) instead of $code" }
}

function ExpectInstalled([string] $expected) {
    $versions = (InstalledVersions $upgradeCode) -join ', '
    if ($versions -ne $expected) { throw "Installed: '$versions' instead of '$expected'" }
}

function WaitUntilRemoved {
    $deadline = (Get-Date).AddMinutes(3)
    while (((Test-Path $folder) -or (InstalledVersions $upgradeCode).Count -gt 0) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 1
    }
    if (Test-Path $folder) { throw "$folder is still there after uninstalling" }
    ExpectInstalled ''
}

# What's shown on a window, by its Windows class: Button for buttons, Static for text. UI Automation doesn't know
# Windows Installer's buttons are buttons, so it can't be asked for those. Pages have hidden ones too, like a second
# Install button.
function Shown($window, [string] $class) {
    $condition = New-Object Windows.Automation.PropertyCondition($automation::ClassNameProperty, $class)
    # one by one down the pipeline
    @($window.FindAll($scope::Descendants, $condition)) | Where-Object { -not $_.Current.IsOffscreen }
}

function Buttons($window) { Shown $window 'Button' }

function Texts($window) {
    @(Shown $window 'Static' | ForEach-Object { Name $_ } | Where-Object { $_ })
}

# A control's name, like a button's text, without the & that underlines a letter
function Name($element) { $element.Current.Name -replace '&', '' }

# Everything on a window, for when it isn't as expected
function Describe($window) {
    @($window.FindAll($scope::Descendants, [Windows.Automation.Condition]::TrueCondition) | ForEach-Object {
        $info = $_.Current
        "  $($info.ControlType.ProgrammaticName) '$($info.Name)' class=$($info.ClassName) " +
            "enabled=$($info.IsEnabled) offscreen=$($info.IsOffscreen)"
    }) -join "`n"
}

# The window with this title and a button with this name, once it's open
function WaitFor([string] $title, [string] $button) {
    $deadline = (Get-Date).AddMinutes(2)
    $titled = New-Object Windows.Automation.PropertyCondition($automation::NameProperty, $title)
    $problem = ''
    while ((Get-Date) -lt $deadline) {
        foreach ($window in @($automation::RootElement.FindAll($scope::Children, $titled))) {
            try {
                if (Buttons $window | Where-Object { (Name $_) -eq $button -and $_.Current.IsEnabled }) {
                    Write-Host "$title`: $((Texts $window) -join ' | ')"
                    return $window
                }
            } catch {
                # it may have closed while being looked at
                $problem = "$_"
            }
        }
        Start-Sleep -Milliseconds 500
    }
    $windows = @($automation::RootElement.FindAll($scope::Children, $titled))
    $number = 0
    foreach ($window in $windows) {
        $number++
        Write-Host "$title window $number`:`n$(Describe $window)"
        Screenshot $window "timeout-$number"
    }
    $open = @($automation::RootElement.FindAll($scope::Children, [Windows.Automation.Condition]::TrueCondition) |
        ForEach-Object { "'$($_.Current.Name)'" }) -join ', '
    throw "No $title window with a $button button after 2 minutes. Open windows: $open. $problem"
}

# Clicks a button the way Windows tells the page about a click: WM_COMMAND, with the button's id and BN_CLICKED (0)
function Click($window, [string] $name) {
    $button = Buttons $window | Where-Object { (Name $_) -eq $name -and $_.Current.IsEnabled } |
        Select-Object -First 1
    if (-not $button) { throw "No $name button to click" }
    $handle = [IntPtr] $button.Current.NativeWindowHandle
    $id = [Win32.Window]::GetDlgCtrlID($handle)
    if (-not [Win32.Window]::PostMessage([Win32.Window]::GetParent($handle), 0x0111, [IntPtr] $id, $handle)) {
        throw "Couldn't click $name"
    }
}

function ExpectText($window, [string] $text) {
    $texts = Texts $window
    if (-not ($texts | Where-Object { $_.Contains($text) })) {
        throw "The page doesn't say '$text'. It says: $($texts -join ' | ')"
    }
}

function Screenshot($window, [string] $name) {
    [void] [Win32.Window]::SetForegroundWindow([IntPtr] $window.Current.NativeWindowHandle)
    Start-Sleep -Milliseconds 500
    try {
        $area = $window.Current.BoundingRectangle
        $picture = New-Object Drawing.Bitmap ([int] $area.Width), ([int] $area.Height)
        $graphics = [Drawing.Graphics]::FromImage($picture)
        $graphics.CopyFromScreen([int] $area.X, [int] $area.Y, 0, 0, $picture.Size)
        $picture.Save((Join-Path $Screenshots "$name.png"), [Drawing.Imaging.ImageFormat]::Png)
        $graphics.Dispose()
        $picture.Dispose()
    } catch {
        Write-Host "Couldn't take a picture of ${name}: $_"
    }
}

$older = OlderInstaller
$olderVersion = MsiProperty $older 'ProductVersion'

Write-Host '0. Nothing installed: the usual pages'
$process = OpenInstaller
$page = WaitFor $setup 'Next'
Screenshot $page 'installer-welcome'
ExpectText $page 'Welcome to the Ivy Wallet UAE Setup Wizard'
Click $page 'Next'
$page = WaitFor $setup 'Change...'
Click $page 'Next'
$page = WaitFor $setup 'Install'
Click $page 'Install'
$page = WaitFor $setup 'Finish'
Click $page 'Finish'
ExpectExit $process 0
ExpectInstalled $version
Run 'msiexec.exe' @('/x', "`"$msi`"", '/qn')
WaitUntilRemoved

Write-Host "1. $olderVersion installed: Update"
Run 'msiexec.exe' @('/i', "`"$older`"", '/qn')
ExpectInstalled $olderVersion
$process = OpenInstaller
$page = WaitFor $setup 'Update'
Screenshot $page 'installer-update'
ExpectText $page 'Ivy Wallet UAE is already installed'
ExpectText $page "This installer has version $version."
Click $page 'Update'
$page = WaitFor $setup 'Finish'
Click $page 'Finish'
ExpectExit $process 0
ExpectInstalled $version

Write-Host "2. $version installed: Repair, then Uninstall"
$uninstaller = Join-Path $folder 'Uninstall Ivy Wallet UAE.exe'
Remove-Item $uninstaller
$process = OpenInstaller
$page = WaitFor $setup 'Repair'
Screenshot $page 'installer-repair'
ExpectText $page "Version $version is on this computer, the same as this installer."
Click $page 'Repair'
$page = WaitFor $setup 'Finish'
Click $page 'Finish'
ExpectExit $process 0
if (-not (Test-Path $uninstaller)) { throw "Repair didn't put back Uninstall Ivy Wallet UAE.exe" }
$process = OpenInstaller
$page = WaitFor $setup 'Uninstall'
Click $page 'Uninstall'
$page = WaitFor $setup 'Finish'
Click $page 'Finish'
ExpectExit $process 0
WaitUntilRemoved

Write-Host "3. $olderVersion installed: Uninstall"
Run 'msiexec.exe' @('/i', "`"$older`"", '/qn')
$process = OpenInstaller
$page = WaitFor $setup 'Uninstall'
Click $page 'Uninstall'
# 1602: closed without installing anything
ExpectExit $process 1602
$question = WaitFor 'Windows Installer' 'Yes'
Screenshot $question 'installer-uninstall-older'
Click $question 'Yes'
WaitUntilRemoved
Write-Host 'Every page of the installer did what it says'
