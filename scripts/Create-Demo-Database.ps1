param([string]$Database='QLDAPM_Demo',[string]$Server='')
$ErrorActionPreference='Stop'
if(!(Get-Command sqlcmd -ErrorAction SilentlyContinue)){throw 'Install Microsoft sqlcmd (ODBC command-line tools), reopen PowerShell, then retry.'}
if($Database -notmatch '^QLDAPM_Demo(?:_[A-Za-z0-9]+)*$'){throw 'Choose QLDAPM_Demo or a QLDAPM_Demo_... name. This helper never targets the working database.'}
$project=Split-Path $PSScriptRoot -Parent
$config=@{}
foreach($name in @('DB_HOST','DB_PORT','DB_USERNAME','DB_PASSWORD')){$config[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
if(Test-Path -LiteralPath (Join-Path $project '.env')){
    Get-Content -LiteralPath (Join-Path $project '.env')|ForEach-Object{
        if($_ -match '^([^#=]+)=(.*)$' -and !$config[$matches[1].Trim()]){$config[$matches[1].Trim()]=$matches[2].Trim()}
    }
}
if(!$config['DB_USERNAME'] -or !$config['DB_PASSWORD']){throw 'Configure DB_USERNAME and DB_PASSWORD in the project .env or current process.'}
if(!$Server){$Server=($(if($config['DB_HOST']){$config['DB_HOST']}else{'localhost'}))+','+($(if($config['DB_PORT']){$config['DB_PORT']}else{'6000'}))}
$oldPassword=$env:SQLCMDPASSWORD
try{
    $env:SQLCMDPASSWORD=$config['DB_PASSWORD']
    $exists=& sqlcmd -S $Server -U $config['DB_USERNAME'] -C -I -b -h -1 -W -Q "SET NOCOUNT ON; SELECT CASE WHEN DB_ID(N'$Database') IS NULL THEN 0 ELSE 1 END;"
    if($LASTEXITCODE -ne 0){throw 'Cannot connect to SQL Server.'}
    if(($exists -join '').Trim() -eq '0'){
        $work=Join-Path $project 'work'
        New-Item -ItemType Directory -Path $work -Force|Out-Null
        $schema=[IO.File]::ReadAllText((Join-Path $project 'database/001_initial_schema.sql')).Replace('QLSieuThiDienMay',$Database)
        $temp=Join-Path $work ('schema-'+$Database+'.sql')
        [IO.File]::WriteAllText($temp,$schema,[Text.UTF8Encoding]::new($true))
        & sqlcmd -S $Server -U $config['DB_USERNAME'] -C -I -b -f 65001 -i $temp
        if($LASTEXITCODE -ne 0){throw 'Demo schema creation failed. Inspect the SQL error before retrying.'}
    }
    & sqlcmd -S $Server -U $config['DB_USERNAME'] -C -I -b -f 65001 -d $Database -i (Join-Path $project 'database/002_demo_seed.sql')
    if($LASTEXITCODE -ne 0){throw 'Demo seed failed. No seed changes were committed.'}
    Write-Output "Demo database ready: $Database"
    Write-Output 'To use it without editing .env:'
    Write-Output ('$env:DB_NAME='''+$Database+'''; .\scripts\Start-App.ps1 -Build')
}finally{[Environment]::SetEnvironmentVariable('SQLCMDPASSWORD',$oldPassword,'Process')}
