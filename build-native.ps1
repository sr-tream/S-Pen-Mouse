param([string]$SpenNdkPath = 'C:\Users\SR_team\Projects\android-ndk-r26d')
$ErrorActionPreference = 'Stop'
$SpenRoot = $PSScriptRoot
$SpenPrebuilt = Join-Path $SpenNdkPath 'toolchains\llvm\prebuilt\windows-x86_64'
$SpenCompiler = Join-Path $SpenPrebuilt 'bin\clang.exe'
$SpenOutput = Join-Path $SpenRoot 'app\src\main\jniLibs\arm64-v8a'
New-Item -ItemType Directory -Force -Path $SpenOutput | Out-Null
& $SpenCompiler --target=aarch64-linux-android29 "--sysroot=$SpenPrebuilt\sysroot" -shared -fPIC -O2 -Wall -Wextra '-Wl,-z,max-page-size=16384' '-Wl,--build-id' -o "$SpenOutput\libspenmouse.so" "$SpenRoot\app\src\main\cpp\input.c"
if ($LASTEXITCODE -ne 0) { throw "NDK compilation failed ($LASTEXITCODE)" }
& $SpenCompiler --target=aarch64-linux-android29 "--sysroot=$SpenPrebuilt\sysroot" -fPIE -pie -O2 -Wall -Wextra '-Wl,-z,max-page-size=16384' -o "$SpenOutput\libspen_guard.so" "$SpenRoot\app\src\main\cpp\guard.c"
if ($LASTEXITCODE -ne 0) { throw "Guard compilation failed ($LASTEXITCODE)" }
