<#
Helpers for the other scripts here, which change installers (.msi) and check what's installed, with Windows Installer's
COM object. PowerShell can't see that object's methods and properties, so they're called by name.
#>
$Installer = New-Object -ComObject WindowsInstaller.Installer

function Call($target, [string] $name, [object[]] $arguments = @()) {
    $target.GetType().InvokeMember($name, [Reflection.BindingFlags]::InvokeMethod, $null, $target, $arguments)
}

function Property($target, [string] $name, [object[]] $arguments = @()) {
    $target.GetType().InvokeMember($name, [Reflection.BindingFlags]::GetProperty, $null, $target, $arguments)
}

function SetField($record, [int] $field, $value) {
    $kind = if ($value -is [int]) { 'IntegerData' } else { 'StringData' }
    [void] $record.GetType().InvokeMember(
        $kind, [Reflection.BindingFlags]::SetProperty, $null, $record, @($field, $value))
}

# Runs a query on an installer's tables, with a value for each ? in it. Gives the first column of each row a SELECT
# finds.
function Sql($db, [string] $query, [object[]] $values = @()) {
    $view = Call $db 'OpenView' @($query)
    if ($values.Count -gt 0) {
        $record = Call $Installer 'CreateRecord' @($values.Count)
        for ($i = 0; $i -lt $values.Count; $i++) { SetField $record ($i + 1) $values[$i] }
        [void] (Call $view 'Execute' @($record))
    } else {
        [void] (Call $view 'Execute')
    }
    $rows = @()
    if ($query.StartsWith('SELECT')) {
        while ($row = Call $view 'Fetch') { $rows += Property $row 'StringData' @(1) }
    }
    [void] (Call $view 'Close')
    return , $rows
}

# A value from an installer's Property table, like its ProductVersion
function MsiProperty([string] $path, [string] $name) {
    # 0: only to read
    $db = Call $Installer 'OpenDatabase' @($path, 0)
    $value = (Sql $db 'SELECT `Value` FROM `Property` WHERE `Property` = ?' @($name))[0]
    [void] [Runtime.InteropServices.Marshal]::ReleaseComObject($db)
    return $value
}

# The versions installed now of the app with this upgrade code
function InstalledVersions([string] $upgradeCode) {
    $products = Property $Installer 'RelatedProducts' @($upgradeCode)
    $count = Property $products 'Count'
    $versions = @()
    for ($i = 0; $i -lt $count; $i++) {
        $versions += Property $Installer 'ProductInfo' @((Property $products 'Item' @($i)), 'VersionString')
    }
    return , $versions
}
