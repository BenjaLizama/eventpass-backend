#Requires -Version 7.0
[CmdletBinding()]
param([switch] $VerifyOnly, [string] $AwsScript = (Join-Path $PSScriptRoot 'Invoke-LabAws.ps1'))
$ErrorActionPreference = 'Stop'
function Invoke-ScalingAws([string[]] $Arguments, [string] $InputFile) {
    $result = & $AwsScript -AwsArguments ($Arguments + @('--output','json')) -InputFile $InputFile
    if ($result) { $result | ConvertFrom-Json -Depth 30 }
}
$identity = Invoke-ScalingAws -Arguments @('sts','get-caller-identity')
if ($identity.Account -ne '119065066402') { throw 'Cuenta incorrecta.' }
$resource = 'service/eventpass/eventpass-ms-events'
$common = @('--service-namespace','ecs','--resource-id',$resource,'--scalable-dimension','ecs:service:DesiredCount')
if (-not $VerifyOnly) {
    # AWS creates/uses its service-linked role. Never reuse or modify LabRole for the auto scaler.
    Invoke-ScalingAws -Arguments (@('application-autoscaling','register-scalable-target') + $common + @('--min-capacity','1','--max-capacity','2')) | Out-Null
    $policy = Join-Path $PSScriptRoot 'events-scaling-policy.json'
    Invoke-ScalingAws -Arguments (@('application-autoscaling','put-scaling-policy') + $common + @(
        '--policy-name','eventpass-events-cpu','--policy-type','TargetTrackingScaling',
        '--target-tracking-scaling-policy-configuration','file:///input/request.json')) -InputFile $policy | Out-Null
}
$target = Invoke-ScalingAws -Arguments @('application-autoscaling','describe-scalable-targets',
    '--service-namespace','ecs','--resource-ids',$resource)
$policies = Invoke-ScalingAws -Arguments @('application-autoscaling','describe-scaling-policies',
    '--service-namespace','ecs','--resource-id',$resource)
$t = @($target.ScalableTargets)
$p = @($policies.ScalingPolicies | Where-Object PolicyName -eq 'eventpass-events-cpu')
if ($t.Count -ne 1 -or $t[0].MinCapacity -ne 1 -or $t[0].MaxCapacity -ne 2 -or
    $t[0].RoleARN -ne 'arn:aws:iam::119065066402:role/aws-service-role/ecs.application-autoscaling.amazonaws.com/AWSServiceRoleForApplicationAutoScaling_ECSService' -or
    $t[0].SuspendedState.DynamicScalingInSuspended -or $t[0].SuspendedState.DynamicScalingOutSuspended -or
    $p.Count -ne 1 -or $p[0].TargetTrackingScalingPolicyConfiguration.TargetValue -ne 60 -or
    $p[0].TargetTrackingScalingPolicyConfiguration.PredefinedMetricSpecification.PredefinedMetricType -ne 'ECSServiceAverageCPUUtilization' -or
    $p[0].TargetTrackingScalingPolicyConfiguration.DisableScaleIn -eq $true -or
    $p[0].TargetTrackingScalingPolicyConfiguration.ScaleOutCooldown -ne 60 -or
    $p[0].TargetTrackingScalingPolicyConfiguration.ScaleInCooldown -ne 180) {
    throw 'La política no coincide con Eventos: CPU 60%, entre una y dos tareas, subida y bajada habilitadas.'
}
$local = Join-Path $PSScriptRoot '../../.local'
New-Item -ItemType Directory -Force $local | Out-Null
@{ CheckedAt=[DateTimeOffset]::UtcNow.ToString('o'); Resource=$resource; Min=1; Max=2;
    CpuTargetPercent=60; Configured=$true; AutomaticTransitionObserved=$false; PolicyArn=$p[0].PolicyARN } |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $local 'aws-scaling-verification.json')
Write-Host 'OK: Eventos tiene escalado automático por CPU al 60%, mínimo 1 y máximo 2. La configuración no demuestra por sí sola un escalado provocado por carga.'
