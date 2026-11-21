#Requires -Version 7.0
[CmdletBinding()]
param([switch] $ServicesReady)

$ErrorActionPreference = 'Stop'
$awsScript = Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'
function Get-NetworkJson([string[]] $Arguments) {
    (& $awsScript -AwsArguments ($Arguments + @('--output', 'json'))) | ConvertFrom-Json -Depth 60
}
function Assert-Network($Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}
$identity = Get-NetworkJson -Arguments @('sts', 'get-caller-identity')
Assert-Network ($identity.Account -eq '119065066402') 'Cuenta incorrecta.'
$stack = (Get-NetworkJson -Arguments @('cloudformation', 'describe-stacks', '--stack-name', 'eventpass-network')).Stacks[0]
Assert-Network ($stack.StackStatus -in @('CREATE_COMPLETE', 'UPDATE_COMPLETE')) "Red incompleta: $($stack.StackStatus)"
$ids = @{}
foreach ($item in $stack.Outputs) { $ids[$item.OutputKey] = $item.OutputValue }
$link = Get-NetworkJson -Arguments @('apigatewayv2', 'get-vpc-link', '--vpc-link-id', $ids.VpcLink)
Assert-Network ($link.VpcLinkStatus -eq 'AVAILABLE') "VPC Link aún no disponible: $($link.VpcLinkStatus)"
$listener = (Get-NetworkJson -Arguments @('elbv2', 'describe-listeners', '--listener-arns', $ids.Listener)).Listeners[0]
$balancer = (Get-NetworkJson -Arguments @('elbv2', 'describe-load-balancers', '--load-balancer-arns', $listener.LoadBalancerArn)).LoadBalancers[0]
Assert-Network ($balancer.Scheme -eq 'internal' -and $balancer.State.Code -eq 'active') 'El balanceador debe ser interno y estar activo.'
$routes = (Get-NetworkJson -Arguments @('apigatewayv2', 'get-routes', '--api-id', $ids.Api)).Items.RouteKey
$documented = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '../gateway/routes.json') | ConvertFrom-Json
$expected = @($documented.PSObject.Properties | ForEach-Object { $_.Value })
Assert-Network (-not (Compare-Object -ReferenceObject $expected -DifferenceObject $routes)) 'Las rutas reales no coinciden con las probadas con Floci.'
$subnets = (Get-NetworkJson -Arguments @('ec2', 'describe-subnets', '--filters', "Name=vpc-id,Values=$($ids.Vpc)")).Subnets
Assert-Network ($subnets.Count -eq 6 -and -not ($subnets | Where-Object MapPublicIpOnLaunch)) 'Las seis subredes deben impedir asignación automática de IP pública.'
$tables = (Get-NetworkJson -Arguments @('ec2', 'describe-route-tables', '--filters', "Name=vpc-id,Values=$($ids.Vpc)")).RouteTables
$data = $tables | Where-Object { 'eventpass-data-private' -in $_.Tags.Value }
Assert-Network ($null -ne $data -and '0.0.0.0/0' -notin $data.Routes.DestinationCidrBlock) 'Las subredes de datos no deben tener salida por defecto a Internet.'
$app = $tables | Where-Object { 'eventpass-app-private' -in $_.Tags.Value }
$default = $app.Routes | Where-Object DestinationCidrBlock -eq '0.0.0.0/0'
Assert-Network ($default.State -eq 'active' -and $default.NatGatewayId) 'La aplicación debe salir mediante NAT.'
$groups = (Get-NetworkJson -Arguments @('ec2', 'describe-security-groups', '--filters', "Name=vpc-id,Values=$($ids.Vpc)")).SecurityGroups
foreach ($group in $groups) {
    foreach ($rule in $group.IpPermissions) {
        Assert-Network ('0.0.0.0/0' -notin $rule.IpRanges.CidrIp -and '::/0' -notin $rule.Ipv6Ranges.CidrIpv6) 'Se detectó ingreso abierto en un grupo de seguridad.'
    }
}
foreach ($service in @('Auth', 'Events', 'Orders', 'Tickets')) {
    $target = (Get-NetworkJson -Arguments @('elbv2', 'describe-target-groups', '--target-group-arns', $ids[$service + 'TargetGroupArn'])).TargetGroups[0]
    Assert-Network ($target.TargetType -eq 'ip' -and $target.HealthCheckPath -eq '/actuator/health') "Target group incorrecto: $service"
}
$gateway = $ids.GatewayUrl.TrimEnd('/')
$checks = @(
    @{ Method = 'GET'; Path = '/actuator/health'; Status = 404 },
    @{ Method = 'POST'; Path = '/api/v1/auth/service-token'; Status = 404 },
    @{ Method = 'GET'; Path = '/api/v1/events?page=0&size=1'; Status = $(if ($ServicesReady) { 200 } else { 503 }) }
)
foreach ($check in $checks) {
    $response = Invoke-WebRequest -Uri ($gateway + $check.Path) -Method $check.Method -SkipHttpErrorCheck -MaximumRedirection 0 -TimeoutSec 30
    Assert-Network ([int]$response.StatusCode -eq $check.Status) "Gateway $($check.Path): HTTP $($response.StatusCode), se esperaba $($check.Status)."
    Write-Host "$($check.Method) $($check.Path) -> HTTP $($response.StatusCode)"
}
Write-Host 'OK: rutas, VPC Link, ALB interno, subredes, NAT y grupos de seguridad.'
if (-not $ServicesReady) { Write-Host 'El catálogo responde 503 porque los servicios ECS todavía no están desplegados.' }
