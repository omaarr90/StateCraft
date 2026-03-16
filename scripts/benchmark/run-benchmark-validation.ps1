#!/usr/bin/env pwsh
[CmdletBinding()]
param(
    [ValidateRange(1, 1000)]
    [int]$Runs = 5,
    [string]$OutputDir = "build/reports/benchmark-validation",
    [string]$ReportPath = "docs/deliverables/benchmark-validation-report.md"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$isWindows = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Windows)
$gradleWrapper = if ($isWindows) { ".\gradlew.bat" } else { "./gradlew" }

$arguments = @(
    "benchmarkValidationReport",
    "--console=plain",
    "-PbenchmarkRuns=$Runs",
    "-PbenchmarkOutputDir=$OutputDir",
    "-PbenchmarkReportPath=$ReportPath"
)

Push-Location $repoRoot
try {
    & $gradleWrapper @arguments
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
