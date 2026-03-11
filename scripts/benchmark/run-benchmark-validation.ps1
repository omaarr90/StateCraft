[CmdletBinding()]
param(
    [ValidateRange(1, 1000)]
    [int]$Runs = 5,
    [string]$OutputDir = "build/reports/benchmark-validation",
    [string]$ReportPath = "docs/deliverables/benchmark-validation-report.md"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $PSNativeCommandUseErrorActionPreference = $false
}

$script:InvariantCulture = [System.Globalization.CultureInfo]::InvariantCulture

function Convert-ToAbsolutePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string]$PathValue
    )

    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return [System.IO.Path]::GetFullPath($PathValue)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $Root $PathValue))
}

function Invoke-CommandCapture {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [switch]$IgnoreExitCode
    )

    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & cmd.exe /d /s /c "$Command" 2>&1 | ForEach-Object { $_.ToString() }
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }

    if (-not $IgnoreExitCode -and $exitCode -ne 0) {
        $joined = ($output -join [Environment]::NewLine)
        throw "Command failed (exit code $exitCode): $Command`n$joined"
    }

    return [pscustomobject]@{
        command = $Command
        output = $output
        exit_code = $exitCode
    }
}

function Parse-RequiredMatch {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Text,
        [Parameter(Mandatory = $true)]
        [string]$Pattern,
        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    $match = [System.Text.RegularExpressions.Regex]::Match($Text, $Pattern)
    if (-not $match.Success) {
        throw "Unable to parse $Label from benchmark output."
    }
    return $match.Groups[1].Value
}

function Parse-InvariantDouble {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    return [double]::Parse($Value, $script:InvariantCulture)
}

function Get-Stats {
    param(
        [Parameter(Mandatory = $true)]
        [double[]]$Values
    )

    if ($Values.Count -eq 0) {
        throw "Cannot compute stats for empty value set."
    }

    $measure = $Values | Measure-Object -Average -Minimum -Maximum
    $mean = [double]$measure.Average
    $sumSq = 0.0
    foreach ($value in $Values) {
        $sumSq += [Math]::Pow($value - $mean, 2)
    }

    return [ordered]@{
        count = $Values.Count
        mean = $mean
        min = [double]$measure.Minimum
        max = [double]$measure.Maximum
        stddev = [Math]::Sqrt($sumSq / $Values.Count)
    }
}

function Format-Decimal3 {
    param(
        [Parameter(Mandatory = $true)]
        [double]$Value
    )

    return $Value.ToString("0.000", $script:InvariantCulture)
}

function Format-Scientific3 {
    param(
        [Parameter(Mandatory = $true)]
        [double]$Value
    )

    return $Value.ToString("0.000e+00", $script:InvariantCulture)
}

function Format-BenchmarkRunTable {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$RunsData
    )

    $lines = @(
        "| Run | AoS (ms) | Split (ms) | Speedup (x) | Max abs diff |",
        "| --- | ---: | ---: | ---: | ---: |"
    )

    foreach ($run in $RunsData) {
        $lines += "| $($run.run) | $(Format-Decimal3 $run.aos_ms) | $(Format-Decimal3 $run.split_ms) | $(Format-Decimal3 $run.speedup_x) | $(Format-Scientific3 $run.max_abs_diff) |"
    }

    return ($lines -join [Environment]::NewLine)
}

function Format-BenchmarkSummaryTable {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$SummaryData
    )

    $lines = @(
        "| Metric | Mean | Min | Max | Stddev |",
        "| --- | ---: | ---: | ---: | ---: |",
        "| AoS (ms) | $(Format-Decimal3 $SummaryData.aos_ms.mean) | $(Format-Decimal3 $SummaryData.aos_ms.min) | $(Format-Decimal3 $SummaryData.aos_ms.max) | $(Format-Decimal3 $SummaryData.aos_ms.stddev) |",
        "| Split (ms) | $(Format-Decimal3 $SummaryData.split_ms.mean) | $(Format-Decimal3 $SummaryData.split_ms.min) | $(Format-Decimal3 $SummaryData.split_ms.max) | $(Format-Decimal3 $SummaryData.split_ms.stddev) |",
        "| Speedup (x) | $(Format-Decimal3 $SummaryData.speedup_x.mean) | $(Format-Decimal3 $SummaryData.speedup_x.min) | $(Format-Decimal3 $SummaryData.speedup_x.max) | $(Format-Decimal3 $SummaryData.speedup_x.stddev) |"
    )

    return ($lines -join [Environment]::NewLine)
}

function Format-ModuleTable {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$ByModule
    )

    $lines = @(
        "| Module | Suites | Tests | Failures | Errors | Skipped | Time (s) |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"
    )

    foreach ($row in $ByModule) {
        $lines += "| $($row.module) | $($row.suites) | $($row.tests) | $($row.failures) | $($row.errors) | $($row.skipped) | $(Format-Decimal3 $row.time_sec) |"
    }

    return ($lines -join [Environment]::NewLine)
}

function Format-SuiteTable {
    param(
        [Parameter(Mandatory = $true)]
        [object[]]$Suites
    )

    $lines = @(
        "| Module | Suite | Tests | Failures | Errors | Skipped | Time (s) |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: |"
    )

    foreach ($suite in $Suites) {
        $lines += "| $($suite.module) | $($suite.name) | $($suite.tests) | $($suite.failures) | $($suite.errors) | $($suite.skipped) | $(Format-Decimal3 $suite.time_sec) |"
    }

    return ($lines -join [Environment]::NewLine)
}

function Convert-ToMarkdownList {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Lines
    )

    return ($Lines -join [Environment]::NewLine)
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$outputDirAbs = Convert-ToAbsolutePath -Root $repoRoot -PathValue $OutputDir
$reportPathAbs = Convert-ToAbsolutePath -Root $repoRoot -PathValue $ReportPath
$reportDirAbs = Split-Path -Parent $reportPathAbs
$templatePath = Join-Path $repoRoot "docs\deliverables\benchmark-validation-report-template.md"

New-Item -Path $outputDirAbs -ItemType Directory -Force | Out-Null
if (-not [string]::IsNullOrWhiteSpace($reportDirAbs)) {
    New-Item -Path $reportDirAbs -ItemType Directory -Force | Out-Null
}

Push-Location $repoRoot
try {
    Write-Host "Collecting run metadata..."

    $nowLocal = Get-Date
    $nowUtc = (Get-Date).ToUniversalTime()
    $timezone = [System.TimeZoneInfo]::Local
    $timezoneOffset = $timezone.GetUtcOffset($nowLocal)

    $gitCommit = "UNKNOWN"
    $gitBranch = "UNKNOWN"
    try {
        $gitCommit = ((Invoke-CommandCapture -Command "git rev-parse HEAD").output | Select-Object -First 1).Trim()
        $gitBranch = ((Invoke-CommandCapture -Command "git branch --show-current").output | Select-Object -First 1).Trim()
    } catch {
        Write-Warning "Git metadata unavailable: $($_.Exception.Message)"
    }

    $javaVersionResult = Invoke-CommandCapture -Command "java -version" -IgnoreExitCode
    if ($javaVersionResult.exit_code -ne 0) {
        throw "java -version failed."
    }
    $javaVersion = ($javaVersionResult.output | Select-Object -First 1)

    $gradleVersionResult = Invoke-CommandCapture -Command ".\gradlew.bat -version --console=plain"
    $gradleVersion = ($gradleVersionResult.output | Where-Object { $_ -match "^Gradle\s+" } | Select-Object -First 1)
    if (-not $gradleVersion) {
        $gradleVersion = "UNKNOWN"
    }

    $cpu = Get-CimInstance -ClassName Win32_Processor | Select-Object -First 1
    $computer = Get-CimInstance -ClassName Win32_ComputerSystem | Select-Object -First 1
    $memoryGiB = [Math]::Round(([double]$computer.TotalPhysicalMemory / 1GB), 2)
    $os = Get-CimInstance -ClassName Win32_OperatingSystem | Select-Object -First 1

    Write-Host "Compiling benchmark test classes..."
    $compileCommand = ".\gradlew.bat :engines:compileTestJava --console=plain"
    [void](Invoke-CommandCapture -Command $compileCommand)

    $classPath = "engines\build\classes\java\main;engines\build\classes\java\test;core\build\classes\java\main"
    $benchmarkCommand = "java --enable-preview --add-modules jdk.incubator.vector --class-path `"$classPath`" com.omaarr90.statecraft.engines.statevector.StatevectorKernelMicrobenchmark"
    $benchmarkRuns = @()

    Write-Host "Running benchmark $Runs time(s)..."
    for ($runIndex = 1; $runIndex -le $Runs; $runIndex++) {
        Write-Host "  Benchmark run $runIndex/$Runs"
        $benchmarkResult = Invoke-CommandCapture -Command $benchmarkCommand
        $benchmarkText = $benchmarkResult.output -join [Environment]::NewLine

        $aosRaw = Parse-RequiredMatch -Text $benchmarkText -Pattern "AoS kernels:\s*([0-9]+(?:\.[0-9]+)?)\s*ms" -Label "AoS kernels"
        $splitRaw = Parse-RequiredMatch -Text $benchmarkText -Pattern "Split reference:\s*([0-9]+(?:\.[0-9]+)?)\s*ms" -Label "Split reference"
        $speedupRaw = Parse-RequiredMatch -Text $benchmarkText -Pattern "Speedup\s*\(split/AoS\):\s*([0-9]+(?:\.[0-9]+)?)x" -Label "Speedup"
        $maxDiffRaw = Parse-RequiredMatch -Text $benchmarkText -Pattern "Max\s+\|.\|:\s*([0-9]+(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)" -Label "Max abs diff"

        $benchmarkRuns += [pscustomobject][ordered]@{
                run = $runIndex
                aos_ms = Parse-InvariantDouble -Value $aosRaw
                split_ms = Parse-InvariantDouble -Value $splitRaw
                speedup_x = Parse-InvariantDouble -Value $speedupRaw
                max_abs_diff = Parse-InvariantDouble -Value $maxDiffRaw
            }
    }

    $aosValues = @($benchmarkRuns | ForEach-Object { [double]$_.aos_ms })
    $splitValues = @($benchmarkRuns | ForEach-Object { [double]$_.split_ms })
    $speedupValues = @($benchmarkRuns | ForEach-Object { [double]$_.speedup_x })
    $maxAbsDiffWorst = [double](($benchmarkRuns | ForEach-Object { [double]$_.max_abs_diff } | Measure-Object -Maximum).Maximum)

    $benchmarkSummary = [ordered]@{
        aos_ms = Get-Stats -Values $aosValues
        split_ms = Get-Stats -Values $splitValues
        speedup_x = Get-Stats -Values $speedupValues
    }

    $validationCommand = ".\gradlew.bat test --console=plain"
    Write-Host "Running full validation..."
    $validationCommandResult = Invoke-CommandCapture -Command $validationCommand -IgnoreExitCode

    $junitDirs = @(
        "core\build\test-results\test",
        "engines\build\test-results\test",
        "app\build\test-results\test"
    )

    $junitFiles = @()
    foreach ($junitDir in $junitDirs) {
        $absDir = Join-Path $repoRoot $junitDir
        if (Test-Path -LiteralPath $absDir) {
            $items = Get-ChildItem -LiteralPath $absDir -File -Filter "*.xml"
            foreach ($item in $items) {
                $junitFiles += $item
            }
        }
    }

    if ($junitFiles.Count -eq 0) {
        throw "No JUnit XML files found under build/test-results/test."
    }

    $suiteRows = @()
    foreach ($file in $junitFiles) {
        [xml]$xml = Get-Content -LiteralPath $file.FullName
        $suite = $xml.testsuite

        if (-not $suite) {
            continue
        }

        $moduleMatch = [System.Text.RegularExpressions.Regex]::Match($file.FullName, "\\(core|engines|app)\\build\\test-results\\test\\")
        $moduleName = if ($moduleMatch.Success) { $moduleMatch.Groups[1].Value } else { "unknown" }

        $suiteRows += [pscustomobject][ordered]@{
                module = $moduleName
                name = [string]$suite.name
                tests = [int]$suite.tests
                failures = [int]$suite.failures
                errors = [int]$suite.errors
                skipped = [int]$suite.skipped
                time_sec = Parse-InvariantDouble -Value ([string]$suite.time)
                file = $file.FullName
            }
    }

    $suiteRowsSorted = @($suiteRows | Sort-Object module, name)
    $byModule = @(
        $suiteRowsSorted |
        Group-Object module |
        ForEach-Object {
            $groupRows = $_.Group
            [pscustomobject][ordered]@{
                module = $_.Name
                suites = $groupRows.Count
                tests = [int](($groupRows | Measure-Object -Property tests -Sum).Sum)
                failures = [int](($groupRows | Measure-Object -Property failures -Sum).Sum)
                errors = [int](($groupRows | Measure-Object -Property errors -Sum).Sum)
                skipped = [int](($groupRows | Measure-Object -Property skipped -Sum).Sum)
                time_sec = [double](($groupRows | Measure-Object -Property time_sec -Sum).Sum)
            }
        } |
        Sort-Object module
    )

    $totalValidation = [ordered]@{
        suites = $suiteRowsSorted.Count
        tests = [int](($suiteRowsSorted | Measure-Object -Property tests -Sum).Sum)
        failures = [int](($suiteRowsSorted | Measure-Object -Property failures -Sum).Sum)
        errors = [int](($suiteRowsSorted | Measure-Object -Property errors -Sum).Sum)
        skipped = [int](($suiteRowsSorted | Measure-Object -Property skipped -Sum).Sum)
        time_sec = [double](($suiteRowsSorted | Measure-Object -Property time_sec -Sum).Sum)
    }

    $failureSuites = @(
        $suiteRowsSorted |
        Where-Object { $_.failures -gt 0 -or $_.errors -gt 0 } |
        ForEach-Object {
            [pscustomobject][ordered]@{
                module = $_.module
                suite = $_.name
                failures = $_.failures
                errors = $_.errors
                file = $_.file
            }
        }
    )

    $runMetadata = [pscustomobject]@{
        run_metadata = [pscustomobject]@{
            timestamp_local = $nowLocal.ToString("yyyy-MM-dd HH:mm:ss zzz", $script:InvariantCulture)
            timestamp_utc = $nowUtc.ToString("yyyy-MM-dd HH:mm:ss 'UTC'", $script:InvariantCulture)
            timezone = $timezone.Id
            timezone_offset = $timezoneOffset.ToString()
            git = [pscustomobject]@{
                branch = $gitBranch
                commit = $gitCommit
            }
            environment = [pscustomobject]@{
                os = "$($os.Caption) ($($os.Version))"
                cpu_name = [string]$cpu.Name
                cpu_cores = [int]$cpu.NumberOfCores
                cpu_logical_processors = [int]$cpu.NumberOfLogicalProcessors
                memory_gib = $memoryGiB
                java_version = $javaVersion
                gradle_version = $gradleVersion
            }
            configuration = [pscustomobject]@{
                runs = $Runs
                output_dir = [System.IO.Path]::GetFullPath($outputDirAbs)
                report_path = [System.IO.Path]::GetFullPath($reportPathAbs)
            }
        }
    }

    $benchmarkResults = [pscustomobject]@{
        benchmark = [pscustomobject]@{
            command = $benchmarkCommand
            runs = @($benchmarkRuns)
            summary = @{
                aos_ms = $benchmarkSummary.aos_ms
                split_ms = $benchmarkSummary.split_ms
                speedup_x = $benchmarkSummary.speedup_x
            }
            correctness = [pscustomobject]@{
                max_abs_diff = $maxAbsDiffWorst
            }
        }
    }

    $validationResults = [pscustomobject]@{
        validation = [pscustomobject]@{
            command = $validationCommand
            total = $totalValidation
            by_module = $byModule
            suites = $suiteRowsSorted
            failures = $failureSuites
        }
    }

    $runMetadataPath = Join-Path $outputDirAbs "run-metadata.json"
    $benchmarkResultsPath = Join-Path $outputDirAbs "benchmark-results.json"
    $validationResultsPath = Join-Path $outputDirAbs "validation-results.json"

    $runMetadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $runMetadataPath -Encoding utf8
    $benchmarkResults | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $benchmarkResultsPath -Encoding utf8
    $validationResults | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $validationResultsPath -Encoding utf8

    if (-not (Test-Path -LiteralPath $templatePath)) {
        throw "Template file not found at $templatePath"
    }

    $templateText = Get-Content -LiteralPath $templatePath -Raw

    $runMetadataMarkdown = Convert-ToMarkdownList -Lines @(
        "- Run timestamp (local): $($runMetadata.run_metadata.timestamp_local)",
        "- Run timestamp (UTC): $($runMetadata.run_metadata.timestamp_utc)",
        "- Timezone: $($runMetadata.run_metadata.timezone) (offset $($runMetadata.run_metadata.timezone_offset))",
        "- Git branch: $gitBranch",
        "- Git commit: $gitCommit",
        "- OS: $($runMetadata.run_metadata.environment.os)",
        "- CPU: $($runMetadata.run_metadata.environment.cpu_name)",
        "- CPU topology: $($runMetadata.run_metadata.environment.cpu_cores) cores / $($runMetadata.run_metadata.environment.cpu_logical_processors) logical processors",
        "- RAM: $($runMetadata.run_metadata.environment.memory_gib) GiB",
        "- Java: $javaVersion",
        "- Gradle: $gradleVersion"
    )

    $codeTick = [char]96

    $benchmarkConfigMarkdown = Convert-ToMarkdownList -Lines @(
        "- Iterations: $Runs",
        "- Compile command: $codeTick$compileCommand$codeTick",
        "- Benchmark command: $codeTick$benchmarkCommand$codeTick",
        "- Classpath: $codeTick$classPath$codeTick"
    )

    $benchmarkSummaryMarkdown = @(
        (Format-BenchmarkSummaryTable -SummaryData $benchmarkSummary),
        "",
        "- Worst max abs diff: $(Format-Scientific3 $maxAbsDiffWorst)"
    ) -join [Environment]::NewLine

    $validationSummaryMarkdown = Convert-ToMarkdownList -Lines @(
        "- Validation command: $codeTick$validationCommand$codeTick",
        "- Suites: $($totalValidation.suites)",
        "- Tests: $($totalValidation.tests)",
        "- Failures: $($totalValidation.failures)",
        "- Errors: $($totalValidation.errors)",
        "- Skipped: $($totalValidation.skipped)",
        "- Total time (from JUnit XML): $(Format-Decimal3 $totalValidation.time_sec) s"
    )

    $reproCommandsMarkdown = @(
        '```powershell',
        ".\scripts\benchmark\run-benchmark-validation.ps1 -Runs $Runs",
        "$compileCommand",
        "$benchmarkCommand",
        $validationCommand,
        '```'
    ) -join [Environment]::NewLine

    $replacements = [ordered]@{
        "{{run_metadata}}" = $runMetadataMarkdown
        "{{benchmark_config}}" = $benchmarkConfigMarkdown
        "{{benchmark_run_table}}" = (Format-BenchmarkRunTable -RunsData $benchmarkRuns)
        "{{benchmark_summary}}" = $benchmarkSummaryMarkdown
        "{{validation_summary}}" = $validationSummaryMarkdown
        "{{validation_module_table}}" = (Format-ModuleTable -ByModule $byModule)
        "{{validation_suite_table}}" = (Format-SuiteTable -Suites $suiteRowsSorted)
        "{{repro_commands}}" = $reproCommandsMarkdown
    }

    $reportText = $templateText
    foreach ($key in $replacements.Keys) {
        $reportText = $reportText.Replace($key, [string]$replacements[$key])
    }

    Set-Content -LiteralPath $reportPathAbs -Value $reportText -Encoding utf8

    Write-Host "Artifacts written:"
    Write-Host "  $runMetadataPath"
    Write-Host "  $benchmarkResultsPath"
    Write-Host "  $validationResultsPath"
    Write-Host "  $reportPathAbs"

    if ($validationCommandResult.exit_code -ne 0 -or $totalValidation.failures -gt 0 -or $totalValidation.errors -gt 0) {
        throw "Validation failed (exit code $($validationCommandResult.exit_code), failures $($totalValidation.failures), errors $($totalValidation.errors))."
    }

    Write-Host "Benchmark and validation report generation completed successfully."
}
finally {
    Pop-Location
}
