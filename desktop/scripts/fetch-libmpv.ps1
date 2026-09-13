# Fetches libmpv-2.dll (shinchiro/mpv-winbuild-cmake) into desktop/resources/windows.
# The DLL is ~120 MB, so it is gitignored rather than committed.
#
#   powershell -ExecutionPolicy Bypass -File desktop/scripts/fetch-libmpv.ps1

param(
    [string]$Release = "20260903",
    [string]$Build = "mpv-dev-x86_64-20260903-git-69e63f425a"
)

$ErrorActionPreference = "Stop"

$dest = Join-Path $PSScriptRoot "..\resources\windows"
$tmp = Join-Path ([IO.Path]::GetTempPath()) "exhale-libmpv"
New-Item -ItemType Directory -Force $dest, $tmp | Out-Null

$archive = Join-Path $tmp "$Build.7z"
Invoke-WebRequest "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/$Release/$Build.7z" -OutFile $archive

# Windows' bundled bsdtar reads 7z, so no 7-Zip install is needed.
& "$env:SystemRoot\System32\tar.exe" -xf $archive -C $tmp libmpv-2.dll
Copy-Item (Join-Path $tmp "libmpv-2.dll") $dest -Force

Write-Host "libmpv-2.dll -> $((Resolve-Path $dest).Path)"
