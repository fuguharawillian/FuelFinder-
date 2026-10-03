$ErrorActionPreference = "Stop"
Push-Location $PSScriptRoot

try {
    foreach ($command in @("java", "mvn", "docker")) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "Não encontrei '$command'. Instale-o e confira se está no PATH."
        }
    }

    $javaVersion = (& java --version 2>&1 | Out-String)
    if ($javaVersion -notmatch '(?m)(?:openjdk|java) 21(?:[.\s]|$)') {
        throw "Este projeto requer Java 21. Versão detectada:`n$javaVersion"
    }

    $mavenVersion = (& mvn --version 2>&1 | Out-String)
    if ($mavenVersion -notmatch 'Java version:\s*21') {
        throw "O Maven não está usando Java 21. Confira JAVA_HOME e PATH.`n$mavenVersion"
    }

    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "O Docker não está disponível. Abra o Docker Desktop e tente novamente."
    }

    Write-Host "Iniciando o PostgreSQL..."
    docker compose up -d postgres
    if ($LASTEXITCODE -ne 0) {
        throw "Não foi possível iniciar o PostgreSQL com Docker Compose."
    }

    Write-Host "Aguardando o banco ficar pronto..."
    $databaseReady = $false
    $deadline = (Get-Date).AddSeconds(90)

    do {
        docker compose exec -T postgres pg_isready -q -h localhost *> $null
        if ($LASTEXITCODE -eq 0) {
            $databaseReady = $true
            break
        }

        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if (-not $databaseReady) {
        throw "O PostgreSQL não ficou pronto em 90 segundos. Confira com: docker compose logs postgres"
    }

    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
        $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
    }
    finally {
        $rng.Dispose()
    }

    Write-Host "Iniciando o FuelFinder em http://localhost:8080 ..."
    Write-Host "Swagger: http://localhost:8080/swagger-ui.html"
    Write-Host "Para encerrar a aplicação, pressione Ctrl+C."

    mvn spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "A aplicação terminou com erro. Confira as mensagens acima."
    }
}
catch {
    Write-Host "Erro: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
finally {
    Pop-Location
}