param([int]$AppPort=8081,[int]$ProxyPort=18082)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$runtime=Join-Path $project 'work/sepay-tunnel'
New-Item -ItemType Directory -Force $runtime | Out-Null
$cloudflared=Join-Path $runtime 'cloudflared.exe'
if(!(Test-Path -LiteralPath $cloudflared)) {
    Invoke-WebRequest 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe' -OutFile $cloudflared
}
$signature=Get-AuthenticodeSignature -LiteralPath $cloudflared
if($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'Cloudflare, Inc\.') {throw 'Cloudflared signature verification failed.'}
if(!(Get-Command python -ErrorAction SilentlyContinue)){throw 'Python 3 is required for the webhook-only proxy.'}
if(Get-NetTCPConnection -LocalPort $ProxyPort -State Listen -ErrorAction SilentlyContinue){throw "Port $ProxyPort is in use. Stop the previous tunnel first."}
$proxy=Start-Process python -ArgumentList @(('"'+(Join-Path $PSScriptRoot 'sepay-webhook-proxy.py')+'"'),'--port',$ProxyPort,'--app-port',$AppPort) -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $runtime 'proxy-error.log')
$tunnel=Start-Process $cloudflared -ArgumentList @('tunnel','--url',"http://127.0.0.1:$ProxyPort",'--no-autoupdate','--protocol','http2') -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $runtime 'tunnel.log') -RedirectStandardOutput (Join-Path $runtime 'tunnel-output.log')
@{proxyPid=$proxy.Id;tunnelPid=$tunnel.Id;appPort=$AppPort;proxyPort=$ProxyPort} | ConvertTo-Json | Set-Content (Join-Path $runtime 'processes.json')
for($i=0;$i -lt 40;$i++) {
    Start-Sleep -Milliseconds 500
    $log=Get-Content -LiteralPath (Join-Path $runtime 'tunnel.log') -Raw -ErrorAction SilentlyContinue
    if($log -match 'https://[a-z0-9-]+\.trycloudflare\.com') {
        $url=$matches[0]+'/api/payments/sepay/webhook'
        $url | Set-Content (Join-Path $runtime 'webhook-url.txt')
        Write-Output "SePay webhook URL: $url"
        Write-Output 'Temporary test URL; changes after restarting the tunnel. Only the webhook path is exposed.'
        break
    }
    if($tunnel.HasExited){break}
}
if(!$url){throw 'No tunnel URL yet. Inspect work/sepay-tunnel/tunnel.log; processes.json contains the helper PIDs.'}
Write-Output 'Keep this terminal running. Ctrl+C or Stop-SePay-Tunnel.ps1 ends the tunnel.'
try { Wait-Process -Id $tunnel.Id }
finally {
    foreach($ownedId in @($proxy.Id,$tunnel.Id)) {
        if(Get-Process -Id $ownedId -ErrorAction SilentlyContinue){Stop-Process -Id $ownedId}
    }
}
