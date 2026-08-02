param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$process = $null
$workDir = $null

function Assert-Value {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Get-FreePort {
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback,
        0
    )
    $listener.Start()
    try {
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Get-JavaPath {
    $localJava = Join-Path $repoRoot '.tools\jdk-21\bin\java.exe'
    if (Test-Path -LiteralPath $localJava) {
        return $localJava
    }

    if ($env:JAVA_HOME) {
        $javaHomePath = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path -LiteralPath $javaHomePath) {
            return $javaHomePath
        }
    }

    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) {
        return $javaCommand.Source
    }

    throw 'java 21 was not found in .tools, JAVA_HOME, or PATH'
}

try {
    $javaPath = Get-JavaPath
    if (-not $SkipBuild) {
        & (Join-Path $repoRoot 'mvnw.cmd') package
        if ($LASTEXITCODE -ne 0) {
            throw 'maven package failed'
        }
    }

    $jarPath = Join-Path $repoRoot 'target\claims-service-0.0.1-SNAPSHOT.jar'
    Assert-Value (Test-Path -LiteralPath $jarPath) 'the application jar was not found'

    $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    $workDir = Join-Path $tempRoot ("chubb-claims-" + [guid]::NewGuid().ToString('N'))
    $workDir = [System.IO.Path]::GetFullPath($workDir)
    Assert-Value ($workDir.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) 'unsafe temporary path'
    New-Item -ItemType Directory -Path $workDir | Out-Null

    $port = Get-FreePort
    $baseUrl = "http://127.0.0.1:$port"
    $databasePath = (Join-Path $workDir 'claims').Replace('\', '/')
    $stdoutPath = Join-Path $workDir 'service.out.log'
    $stderrPath = Join-Path $workDir 'service.err.log'
    $arguments = @(
        '-jar',
        $jarPath,
        '--spring.profiles.active=demo',
        "--server.port=$port",
        "--spring.datasource.url=jdbc:h2:file:$databasePath;DB_CLOSE_ON_EXIT=FALSE"
    )
    $process = Start-Process `
        -FilePath $javaPath `
        -ArgumentList $arguments `
        -PassThru `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutPath `
        -RedirectStandardError $stderrPath

    $healthy = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($process.HasExited) {
            throw "the service exited with code $($process.ExitCode)"
        }
        try {
            $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -TimeoutSec 2
            if ($health.status -eq 'UP') {
                $healthy = $true
                break
            }
        }
        catch {
            Start-Sleep -Milliseconds 500
        }
    }
    Assert-Value $healthy 'the service did not become healthy in 30 seconds'

    $seedQueue = Invoke-RestMethod -Uri "$baseUrl/work-queue"
    Write-Output "demo queue count: $($seedQueue.Count)"
    Assert-Value ($seedQueue.Count -eq 3) 'the demo queue must contain three claims'

    $submissionBody = @{
        claimantId = 'live-claimant-101'
        type = 'motor'
        market = 'SG'
        incidentAt = [DateTimeOffset]::UtcNow.AddDays(-1).ToString('o')
        description = 'Fictional live check vehicle damage'
        estimatedLoss = 2800.00
        currency = 'SGD'
    } | ConvertTo-Json
    $claim = Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/claims" `
        -ContentType 'application/json' `
        -Body $submissionBody

    $beforeResponse = Invoke-RestMethod -Uri "$baseUrl/exposure?market=SG"
    $before = $beforeResponse |
        Where-Object { $_.currency -eq 'SGD' }
    Assert-Value ([decimal]$before.amount -eq [decimal]16600.00) 'sgd exposure before approval must be 16600.00'
    Assert-Value ([int]$before.claimCount -eq 3) 'sgd open count before approval must be three'

    Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/claims/$($claim.id)/assignment" `
        -ContentType 'application/json' `
        -Body (@{ officerId = 'live-officer-7' } | ConvertTo-Json) | Out-Null
    Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/claims/$($claim.id)/actions" `
        -ContentType 'application/json' `
        -Body (@{ action = 'startReview'; officerId = 'live-officer-7' } | ConvertTo-Json) | Out-Null
    Invoke-RestMethod `
        -Method Post `
        -Uri "$baseUrl/claims/$($claim.id)/actions" `
        -ContentType 'application/json' `
        -Body (@{ action = 'approve'; officerId = 'live-officer-7'; reason = 'fictional cover confirmed' } | ConvertTo-Json) | Out-Null

    $afterResponse = Invoke-RestMethod -Uri "$baseUrl/exposure?market=SG"
    $after = $afterResponse |
        Where-Object { $_.currency -eq 'SGD' }
    Assert-Value ([decimal]$after.amount -eq [decimal]13800.00) 'sgd exposure after approval must be 13800.00'
    Assert-Value ([int]$after.claimCount -eq 2) 'sgd open count after approval must be two'

    $details = Invoke-RestMethod -Uri "$baseUrl/claims/$($claim.id)"
    Assert-Value (@($details.timeline).Count -eq 4) 'the live claim must have four timeline entries'

    $openApi = Invoke-RestMethod -Uri "$baseUrl/v3/api-docs"
    $paths = @($openApi.paths.PSObject.Properties.Name)
    Assert-Value ($paths -contains '/work-queue') 'openapi is missing the work queue route'
    Assert-Value ($paths -contains '/exposure') 'openapi is missing the exposure route'
    Assert-Value ($paths -contains '/claims/{claimId}/information') 'openapi is missing the information route'

    Write-Output "live check passed on port $port"
}
catch {
    if ($workDir) {
        $stdoutPath = Join-Path $workDir 'service.out.log'
        $stderrPath = Join-Path $workDir 'service.err.log'
        if (Test-Path -LiteralPath $stdoutPath) {
            Get-Content -LiteralPath $stdoutPath -Tail 40
        }
        if (Test-Path -LiteralPath $stderrPath) {
            Get-Content -LiteralPath $stderrPath -Tail 40
        }
    }
    throw
}
finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force
        Wait-Process -Id $process.Id -ErrorAction SilentlyContinue
    }
    if ($workDir -and (Test-Path -LiteralPath $workDir)) {
        $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
        $resolvedWorkDir = [System.IO.Path]::GetFullPath($workDir)
        if ($resolvedWorkDir.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedWorkDir -Recurse -Force
        }
        else {
            Write-Warning "temporary files were kept because the path was unsafe: $resolvedWorkDir"
        }
    }
}
