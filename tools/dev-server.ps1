param([switch]$Start)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$runDirectory = Join-Path $projectRoot 'run'
New-Item -ItemType Directory -Force -Path (Join-Path $runDirectory 'plugins') | Out-Null
Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') build
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    $jar = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'build/libs') -Filter 'RushHour-*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $runDirectory 'plugins/RushHour.jar') -Force
    $headers = @{ 'User-Agent' = 'RushHour-dev/0.1.0 (https://github.com/Julz0711/rush-hour)' }
    if (!(Test-Path -LiteralPath (Join-Path $runDirectory 'paper.jar'))) {
        $builds = Invoke-RestMethod -Headers $headers -Uri 'https://fill.papermc.io/v3/projects/paper/versions/26.3/builds'
        $build = $builds | Where-Object channel -eq 'STABLE' | Sort-Object id -Descending | Select-Object -First 1
        if (!$build) { throw 'No stable Paper 26.3 build is available' }
        $download = $build.downloads.'server:default'
        Invoke-WebRequest -Headers $headers -Uri $download.url -OutFile (Join-Path $runDirectory 'paper.jar')
        if ($download.checksums.sha256) {
            $hash = (Get-FileHash -LiteralPath (Join-Path $runDirectory 'paper.jar') -Algorithm SHA256).Hash
            if ($hash -ne $download.checksums.sha256) { throw 'Paper checksum mismatch' }
        }
    }
    $properties = Join-Path $runDirectory 'server.properties'
    if (!(Test-Path -LiteralPath $properties)) {
        @('server-ip=127.0.0.1', 'server-port=25565', 'online-mode=true', 'motd=Rush Hour development', 'spawn-protection=0', 'view-distance=6', 'simulation-distance=4', 'pause-when-empty-seconds=-1') | Set-Content -LiteralPath $properties
    }
    $eula = Join-Path $runDirectory 'eula.txt'
    if (!(Test-Path -LiteralPath $eula)) { 'eula=false' | Set-Content -LiteralPath $eula }
    if ($Start) {
        if (!(Select-String -LiteralPath $eula -Pattern '^eula=true$' -Quiet)) { throw "Read https://aka.ms/MinecraftEULA, then set eula=true in $eula if you agree." }
        Set-Location -LiteralPath $runDirectory
        & java -Xms1G -Xmx3G -jar paper.jar --nogui
    } else { Write-Output "Ready in $runDirectory. Review eula.txt, then run tools/dev-server.ps1 -Start." }
} finally { Pop-Location }
