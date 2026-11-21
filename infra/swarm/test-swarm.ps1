#Requires -Version 7.0
[CmdletBinding()]
param([switch] $KeepRunning, [switch] $SkipBuild)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$compose = @('compose','-p','eventpass-swarm-lab','-f',(Join-Path $PSScriptRoot 'compose.lab.yaml'))
$local = Join-Path $root '.local/swarm'
New-Item -ItemType Directory -Force -Path $local | Out-Null
function Invoke-Node([string] $NodeName, [string[]] $Arguments) {
    & docker @compose exec -T $NodeName docker @Arguments
}
function Wait-Condition([scriptblock] $Check, [string] $Description, [int] $Seconds = 300) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($Seconds)
    do {
        if (& $Check) { Write-Host "OK: $Description"; return }
        Start-Sleep -Seconds 5
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw "Tiempo agotado: $Description"
}
function Test-Endpoints {
    foreach ($port in 18081..18084) {
        try {
            $health = Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 5
            if ($health.status -ne 'UP') { return $false }
        } catch { return $false }
    }
    return $true
}
function Test-Replicas([int] $Count) {
    $tasks = @(Invoke-Node manager @('service','ps','eventpass_ms-events','--filter','desired-state=running','--format','{{.CurrentState}}'))
    return $tasks.Count -eq $Count -and @($tasks | Where-Object { $_ -notlike 'Running *' }).Count -eq 0
}
Push-Location $root
try {
    # Se construye en el Docker del PC, nunca dentro de los nodos del clúster.
    foreach ($service in 'ms-auth','ms-events','ms-orders','ms-tickets') {
        if (-not $SkipBuild) { docker build -t "eventpass/${service}:ep3" $service | Out-Host }
        docker image inspect "eventpass/${service}:ep3" --format '{{.Id}}' | Out-Null
    }
    if (-not (Test-Path (Join-Path $PSScriptRoot '.env'))) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot '.env.example') -Destination (Join-Path $PSScriptRoot '.env')
    }
    docker @compose up -d --wait | Out-Host
    foreach ($name in 'manager','worker') {
        Wait-Condition {
            try { Invoke-Node $name @('info','--format','{{.ServerVersion}}') | Out-Null; return $true }
            catch { return $false }
        } "motor Docker $name listo" 120
    }
    $managerId = docker @compose ps -q manager
    $managerIp = docker inspect $managerId --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'
    $state = Invoke-Node manager @('info','--format','{{.Swarm.LocalNodeState}}')
    if ($state -eq 'inactive') { Invoke-Node manager @('swarm','init','--advertise-addr',$managerIp) | Out-Null }
    $state = Invoke-Node worker @('info','--format','{{.Swarm.LocalNodeState}}')
    if ($state -eq 'inactive') {
        $joinToken = Invoke-Node manager @('swarm','join-token','-q','worker')
        try { Invoke-Node worker @('swarm','join','--token',$joinToken,"${managerIp}:2377") | Out-Host }
        finally { $joinToken = $null }
    }
    $managerNode = Invoke-Node manager @('info','--format','{{.Swarm.NodeID}}')
    Invoke-Node manager @('node','update','--label-add','eventpass-data=true',$managerNode) | Out-Null
    Wait-Condition {
        $nodes = @(Invoke-Node manager @('node','ls','--format','{{json .}}') | ForEach-Object { $_ | ConvertFrom-Json })
        return $nodes.Count -eq 2 -and @($nodes | Where-Object Status -eq Ready).Count -eq 2 -and
            @($nodes | Where-Object ManagerStatus -eq Leader).Count -eq 1
    } 'manager y worker Ready' 120
    $nodes = @(Invoke-Node manager @('node','ls','--format','{{json .}}') | ForEach-Object { $_ | ConvertFrom-Json })

    # Los nodos tienen discos independientes. Se carga la misma imagen en ambos;
    # en servidores reales, ECR reemplaza este transporte local.
    $archive = Join-Path $local 'images.tar'
    docker image save -o $archive eventpass/ms-auth:ep3 eventpass/ms-events:ep3 eventpass/ms-orders:ep3 eventpass/ms-tickets:ep3
    foreach ($name in 'manager','worker') {
        Invoke-Node $name @('image','load','-i','/images/images.tar') | Out-Host
    }
    $id = docker @compose ps -q manager
    # El stack y su .env se montan de solo lectura en el manager.
    docker exec -w /workspace $id docker stack deploy --resolve-image never -c docker-stack.yml eventpass | Out-Host
    # Local tags are mutable: stack deploy alone may leave the old container alive.
    # Force replacement and verify actual running image IDs, not just HTTP health.
    $expectedImages = @{}
    foreach ($service in 'ms-auth','ms-events','ms-orders','ms-tickets') {
        $expectedImages[$service] = Invoke-Node worker @('image','inspect',"eventpass/${service}:ep3",'--format','{{.Id}}')
        Invoke-Node manager @('service','update','--force','--detach=true','--no-resolve-image',"eventpass_$service") | Out-Null
    }
    Wait-Condition {
        foreach ($service in $expectedImages.Keys) {
            $containers = @(Invoke-Node worker @('ps','-q','--filter',"label=com.docker.swarm.service.name=eventpass_$service"))
            if ($containers.Count -ne 1) { return $false }
            $actual = Invoke-Node worker @('inspect',$containers[0],'--format','{{.Image}} {{.State.Health.Status}}')
            if ($actual -ne "$($expectedImages[$service]) healthy") { return $false }
            $update = Invoke-Node manager @('service','inspect',"eventpass_$service",'--format','{{.UpdateStatus.State}}')
            if ($update -ne 'completed') { return $false }
        }
        return $true
    } 'las cuatro tareas ejecutan exactamente las imágenes nuevas y están sanas' 420
    Wait-Condition { Test-Endpoints } 'cuatro microservicios accesibles por la red Swarm' 420
    Wait-Condition {
        $states = @(Invoke-Node manager @('service','ps','eventpass_init-queues','--format','{{.CurrentState}}'))
        return @($states | Where-Object { $_ -like 'Complete *' }).Count -gt 0
    } 'colas creadas en Floci' 180
    $catalog = Invoke-RestMethod 'http://127.0.0.1:18082/api/v1/events?page=0&size=1'
    if ($catalog.status -ne 200) { throw 'Catálogo no disponible.' }
    $loginBody = @{email='admin@eventpass.cl';password='Cambiame123!'} | ConvertTo-Json
    $login = Invoke-RestMethod 'http://127.0.0.1:18081/api/v1/auth/login' -Method Post -ContentType application/json -Body $loginBody
    $headers = @{Authorization='Bearer '+$login.data.access_token}
    try {
        Invoke-Node manager @('service','scale','--detach=true','eventpass_ms-events=2') | Out-Host
        Wait-Condition { (Test-Replicas 2) -and (Test-Endpoints) } 'escalado Eventos de una a dos réplicas' 240
        # Comprueba ambas tareas, no solamente una petición balanceada al servicio.
        $containers = @(Invoke-Node worker @('ps','-q','--filter','label=com.docker.swarm.service.name=eventpass_ms-events'))
        if ($containers.Count -ne 2) { throw 'Faltan réplicas de Eventos en el worker.' }
        foreach ($container in $containers) {
            Invoke-Node worker @('exec',$container,'wget','--spider','-q','http://localhost:8082/actuator/health') | Out-Null
        }
        $null = Invoke-RestMethod 'http://127.0.0.1:18082/api/v1/events/my-events?page=0&size=1' -Headers $headers
        Invoke-Node manager @('service','scale','--detach=true','eventpass_ms-events=1') | Out-Host
        Wait-Condition { (Test-Replicas 1) -and (Test-Endpoints) } 'reducción a una réplica con sesión vigente' 180
        $null = Invoke-RestMethod 'http://127.0.0.1:18082/api/v1/events/my-events?page=0&size=1' -Headers $headers
    } finally {
        $null = Invoke-RestMethod 'http://127.0.0.1:18081/api/v1/auth/logout' -Method Post -Headers $headers
        $headers = $null; $login = $null
    }
    @{ CheckedAt=[DateTimeOffset]::UtcNow.ToString('o'); Nodes=$nodes; ScaleUp=2; ScaleDown=1; Endpoints='UP'; QueueInitialization='Complete' } |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $local 'verification.json')
    Write-Host 'OK: manager y worker reales en Docker-in-Docker; cuatro aplicaciones, Floci, sesión y escalado 1 -> 2 -> 1.'
} catch {
    try { Invoke-Node manager @('stack','ps','eventpass','--no-trunc') | Out-Host } catch {}
    throw
} finally {
    if (-not $KeepRunning) { docker @compose down | Out-Host }
    Pop-Location
}
