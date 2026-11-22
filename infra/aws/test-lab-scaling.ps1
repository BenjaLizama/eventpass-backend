#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateRange(180,900)][int] $LoadSeconds = 900,
    [ValidateRange(1,45)][int] $RequestsPerSecond = 45,
    [ValidateRange(1,50)][int] $EventCount = 48,
    [ValidateRange(300,1800)][int] $ScaleInWaitSeconds = 1800,
    [string] $AwsScript = (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1')
)
$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '../../.local'
$started = [DateTimeOffset]::UtcNow
$events = [Collections.Generic.List[string]]::new()
$samples = [Collections.Generic.List[object]]::new()
$report = @{ StartedAt=$started.ToString('o'); Phase='preparing'; Policy='eventpass-events-cpu'; Threshold=60;
    ManualCapacityChanges=$false; SyntheticMetrics=$false; Success=$false; SeedEventCount=$EventCount }
$token = ''; $load = $null; $stdout = $null; $stderr = $null
function Invoke-ScalingProbeAws([string[]] $Arguments) {
    $text = & $AwsScript -AwsArguments ($Arguments + @('--output','json'))
    if ($text) { $text | ConvertFrom-Json -Depth 40 }
}
function Probe([string] $Method, [string] $Path, $Body = $null) {
    $parameters = @{ Uri=$gateway+$Path; Method=$Method; MaximumRedirection=0; TimeoutSec=20; Headers=@{} }
    if ($token) { $parameters.Headers.Authorization='Bearer '+$token }
    if ($null -ne $Body) { $parameters.Body=$Body | ConvertTo-Json -Depth 12 -Compress; $parameters.ContentType='application/json' }
    (Invoke-RestMethod @parameters).data
}
function Snapshot {
    $service = (Invoke-ScalingProbeAws @('ecs','describe-services','--cluster','eventpass','--services','eventpass-ms-events')).services[0]
    if ($service.deployments.Count -ne 1 -or $service.deployments[0].rolloutState -ne 'COMPLETED') {
        throw 'Existe un despliegue concurrente: no se atribuirá a autoescalado.'
    }
    $activities = (Invoke-ScalingProbeAws @('application-autoscaling','describe-scaling-activities',
        '--service-namespace','ecs','--resource-id','service/eventpass/eventpass-ms-events','--include-not-scaled-activities','--max-results','30')).ScalingActivities
    $automatic = @($activities | Where-Object {
        $_.StatusCode -eq 'Successful' -and [DateTimeOffset]$_.StartTime -ge $started -and
        $_.Cause -like '*eventpass-events-cpu*'
    })
    $targets=Invoke-ScalingProbeAws @('elbv2','describe-target-health','--target-group-arn',$service.loadBalancers[0].targetGroupArn)
    $healthy=@($targets.TargetHealthDescriptions | Where-Object { $_.TargetHealth.State -eq 'healthy' }).Count
    $catalog=Invoke-RestMethod "$gateway/api/v1/events?page=0&size=1" -TimeoutSec 10 -MaximumRedirection 0
    if ($catalog.status -ne 200) { throw 'El catálogo no respondió correctamente durante la observación del escalado.' }
    $sample = @{ At=[DateTimeOffset]::UtcNow.ToString('o'); Desired=$service.desiredCount;
        Running=$service.runningCount; Pending=$service.pendingCount; HealthyTargets=$healthy; CatalogStatus=200; Activities=$automatic;
        Decisions=@($activities | Where-Object { [DateTimeOffset]$_.StartTime -ge $started -and $_.NotScaledReasons }) }
    $samples.Add($sample)
    Save-Report
    Write-Host "Eventos: deseadas=$($sample.Desired), ejecutándose=$($sample.Running), pendientes=$($sample.Pending)."
    $sample
}
function Save-Report {
    $report.Events=@($events); $report.Samples=@($samples)
    $destination=Join-Path $local 'aws-scaling-smoke.json'
    $temporary=Join-Path $local ('scaling-report-'+[guid]::NewGuid().ToString('N')+'.tmp')
    [IO.File]::WriteAllText($temporary, ($report | ConvertTo-Json -Depth 20))
    for ($attempt=0; $attempt -lt 10; $attempt++) {
        try { Move-Item -LiteralPath $temporary -Destination $destination -Force -ErrorAction Stop; return }
        catch [IO.IOException] { if ($attempt -eq 9) { throw }; Start-Sleep -Milliseconds 200 }
    }
}
try {
    $identity = Invoke-ScalingProbeAws @('sts','get-caller-identity')
    if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta.' }
    & (Join-Path $PSScriptRoot 'start-lab-scaling.ps1') -VerifyOnly -AwsScript $AwsScript
    $network = Get-Content -Raw (Join-Path $local 'aws-network-outputs.json') | ConvertFrom-Json
    $gateway = ($network | Where-Object OutputKey -eq GatewayUrl).OutputValue.TrimEnd('/')
    if ($gateway -notmatch '^https://[a-z0-9]+\.execute-api\.us-east-1\.amazonaws\.com$') { throw 'Gateway inesperado.' }
    $initial = Snapshot
    if ($initial.Desired -ne 1 -or $initial.Running -ne 1 -or $initial.Pending) { throw 'Se requiere una tarea estable antes de la prueba.' }
    $settings = Get-Content -Raw (Join-Path $local 'aws-task-config.json') | ConvertFrom-Json
    $secretArn = $settings.secrets.ADMIN_PASSWORD -replace ':password::$',''
    $secret = Invoke-ScalingProbeAws @('secretsmanager','get-secret-value','--secret-id',$secretArn)
    $password = ($secret.SecretString | ConvertFrom-Json).password
    $secret = $null
    $login = Probe POST '/api/v1/auth/login' @{ email=$settings.environment.ADMIN_EMAIL; password=$password }
    $password=$null; $token=$login.access_token; $login=$null
    $run = [guid]::NewGuid().ToString('N').Substring(0,12)
    $venue = Probe POST '/api/v1/venues' @{ name="Carga ECS $run"; address='Recinto de prueba de escalabilidad'; city='Santiago'; country='Chile'; capacity=10000 }
    $date = [DateTimeOffset]::UtcNow.AddDays(30)
    foreach ($index in 1..$EventCount) {
        $categories = @(1..16 | ForEach-Object { @{ name="Localidad $_"; price=10000; totalCapacity=100; maxPerUser=2 } })
        $event = Probe POST '/api/v1/events' @{ title="Carga ECS $run $index"; description='Datos temporales de prueba de escalado; sin compras';
            category='CONCERT'; startDate=$date.ToString('o'); endDate=$date.AddHours(2).ToString('o'); venueId=$venue.id; ticketCategories=$categories }
        $events.Add($event.id); Save-Report
        $null = Probe PATCH "/api/v1/events/$($event.id)/publish"
    }
    # The load generator uses only public GETs. Tokens never enter child arguments, files or logs.
    $loadReport = Join-Path $local 'aws-catalog-load.json'
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = (Get-Command node).Source
    $info.UseShellExecute=$false; $info.CreateNoWindow=$true
    $info.RedirectStandardInput=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    foreach ($argument in @((Join-Path $PSScriptRoot 'catalog-load.mjs'), "$gateway/api/v1/events?page=0&size=100", "$LoadSeconds", "$RequestsPerSecond", $loadReport)) {
        $info.ArgumentList.Add($argument)
    }
    $load = [Diagnostics.Process]::Start($info)
    $report.Phase='load'
    $stdout=$load.StandardOutput.ReadToEndAsync(); $stderr=$load.StandardError.ReadToEndAsync()
    $deadline=[DateTimeOffset]::UtcNow.AddSeconds($LoadSeconds+30)
    $out=$null
    do {
        Start-Sleep -Seconds 25
        $sample=Snapshot
        $cause=@($sample.Activities | Where-Object Description -match 'desired count to 2\b')
        if ($cause.Count -and $sample.Desired -eq 2 -and $sample.Running -eq 2 -and $sample.HealthyTargets -eq 2 -and -not $sample.Pending) { $out=$sample; break }
        if ($load.HasExited) { break }
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    if (-not $load.HasExited) { $load.StandardInput.WriteLine('stop') }
    if (-not $load.WaitForExit(30000)) { $load.Kill($true); throw 'El generador no terminó dentro del límite.' }
    if (-not (Test-Path -LiteralPath $loadReport)) { throw 'El generador no produjo un informe; revisar su salida de error.' }
    $report.Load=Get-Content -Raw $loadReport | ConvertFrom-Json
    if ($load.ExitCode -ne 0 -or $report.Load.failures) { throw 'Hubo errores HTTP o de red bajo carga. Revisar aws-catalog-load.json.' }
    if (-not $out) { throw 'La carga real no produjo un escalado automático verificable al 60% de CPU. No se cambió el umbral ni la capacidad manualmente.' }
    $report.ScaleOut=$out
    $report.Phase='waiting-for-scale-in'
    Save-Report
    Write-Host 'OK: subida automática a dos tareas confirmada por el historial de AWS. Carga detenida; esperando reducción automática.'
    $deadline=[DateTimeOffset]::UtcNow.AddSeconds($ScaleInWaitSeconds)
    do {
        Start-Sleep -Seconds 30
        $sample=Snapshot
        $cause=@($sample.Activities | Where-Object { $_.Description -match 'desired count to 1\b' -and [DateTimeOffset]$_.StartTime -gt [DateTimeOffset]$out.At })
        if ($cause.Count -and $sample.Desired -eq 1 -and $sample.Running -eq 1 -and $sample.HealthyTargets -eq 1 -and -not $sample.Pending) {
            $report.ScaleIn=$sample; $report.Success=$true; $report.Phase='completed'; break
        }
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    if (-not $report.Success) { throw 'No se observó la reducción automática dentro del tiempo acotado. Revisar métricas e historial.' }
    Write-Host 'OK: escalado automático real 1 -> 2 -> 1 sin cambios manuales de capacidad ni métricas artificiales.'
} catch {
    $report.Phase='failed'
    $report.Error=$_.Exception.Message
    throw
} finally {
    if ($load -and -not $load.HasExited) {
        try { $load.StandardInput.WriteLine('stop'); if (-not $load.WaitForExit(30000)) { $load.Kill($true) } } catch {}
    }
    if ($load -and $load.HasExited) { $report.WorkerExitCode=$load.ExitCode }
    if ($stderr -and $stderr.IsCompleted) {
        try { $report.WorkerError=$stderr.GetAwaiter().GetResult() }
        catch { $report.WorkerOutputUnavailable=$true }
    }
    $cleanup=[Collections.Generic.List[string]]::new()
    foreach ($id in $events) {
        try { $null=Probe PATCH "/api/v1/events/$id/cancel" } catch { $cleanup.Add($id) }
    }
    $report.CleanupPending=@($cleanup)
    if ($cleanup.Count) { $report.Success=$false; $report.CleanupFailed=$true }
    if ($token) { try { $null=Probe POST '/api/v1/auth/logout' } catch {} }
    $token=$null; $password=$null
    try {
        $cpu=Invoke-ScalingProbeAws @('cloudwatch','get-metric-statistics','--namespace','AWS/ECS','--metric-name','CPUUtilization',
            '--dimensions','Name=ClusterName,Value=eventpass','Name=ServiceName,Value=eventpass-ms-events',
            '--start-time',$started.ToString('o'),'--end-time',[DateTimeOffset]::UtcNow.ToString('o'),
            '--period','60','--statistics','Average')
        $report.Cpu=@($cpu.Datapoints | Sort-Object Timestamp)
    } catch { $report.MetricsUnavailable=$true }
    Save-Report
    if ($cleanup.Count) { throw 'Quedaron eventos de prueba pendientes de cancelar. No se considera una prueba completada.' }
}
