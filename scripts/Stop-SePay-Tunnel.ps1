$ErrorActionPreference='Stop'
$runtime=Join-Path (Split-Path $PSScriptRoot -Parent) 'work/sepay-tunnel'
$state=Get-Content -LiteralPath (Join-Path $runtime 'processes.json') -Raw | ConvertFrom-Json
foreach($processId in @($state.proxyPid,$state.tunnelPid)) {
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
    if($process -and (($process.Name -eq 'cloudflared.exe' -and $process.ExecutablePath -eq (Join-Path $runtime 'cloudflared.exe')) -or
       ($process.Name -match '^python' -and $process.CommandLine -like '*sepay-webhook-proxy.py*'))) {
        Stop-Process -Id $processId
    }
}
Write-Output 'SePay test tunnel stopped.'
