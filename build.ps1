param(
    [string]$SpenNdkPath=$env:ANDROID_NDK,
    [string]$SpenSdkPath="$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference='Stop'
if ([string]::IsNullOrWhiteSpace($SpenNdkPath)) {
    throw 'Set ANDROID_NDK to your NDK directory or pass -SpenNdkPath.'
}
Push-Location $PSScriptRoot
try {
    if (!(Test-Path -LiteralPath local.properties)) {
        Set-Content -LiteralPath local.properties -Encoding ascii -Value ('sdk.dir='+$SpenSdkPath.Replace('\','/'))
    }
    & .\build-native.ps1 -SpenNdkPath $SpenNdkPath
    if (!(Test-Path -LiteralPath debug.keystore)) {
        & keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=S Pen Mouse Development,O=Local,C=UZ'
        if($LASTEXITCODE -ne 0){throw 'Signing key generation failed'}
    }
    & .\gradlew.bat --no-daemon :app:assembleDebug
    if($LASTEXITCODE -ne 0){throw 'APK build failed'}
    Write-Output (Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk')
} finally {Pop-Location}
