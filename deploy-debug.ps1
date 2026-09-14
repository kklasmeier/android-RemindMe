$ErrorActionPreference = "Stop"

$versionFile = Join-Path $PSScriptRoot "version.properties"
$originalVersion = Get-Content $versionFile -Raw
$properties = @{}

Get-Content $versionFile | ForEach-Object {
    if ($_ -match "^\s*([^#][^=]*)=(.*)$") {
        $properties[$matches[1].Trim()] = $matches[2].Trim()
    }
}

$versionCode = [int]$properties["versionCode"] + 1
$parts = $properties["versionName"].Split(".")
if ($parts.Count -ne 3) {
    throw "versionName must use major.minor.patch format."
}
$versionName = "$($parts[0]).$($parts[1]).$([int]$parts[2] + 1)"

try {
    @"
versionCode=$versionCode
versionName=$versionName
"@ | Set-Content $versionFile

    Write-Host "Deploying RemindMe $versionName ($versionCode)"
    & (Join-Path $PSScriptRoot "gradlew.bat") assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed." }

    & adb install -r (Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk")
    if ($LASTEXITCODE -ne 0) { throw "APK installation failed." }
} catch {
    Set-Content $versionFile $originalVersion -NoNewline
    throw
}
