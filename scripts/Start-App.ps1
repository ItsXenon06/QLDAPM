param([switch]$Build)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
Push-Location $project
try {
    if($Build -or !(Test-Path 'target/qldapm-0.0.1-SNAPSHOT.jar')) {
        if(Get-Command mvn -ErrorAction SilentlyContinue){& mvn -B -ntp clean package}
        else {& ./mvnw.cmd -B -ntp clean package}
        if($LASTEXITCODE -ne 0){throw 'Build failed'}
    }
    & java -jar target/qldapm-0.0.1-SNAPSHOT.jar
    if($LASTEXITCODE -ne 0){throw 'Application stopped with an error'}
} finally {Pop-Location}
