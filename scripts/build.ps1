<#
.SYNOPSIS
Build and test the Java demo, downloading checksum-verified Maven if needed.
#>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($maven) {
        $executable = $maven.Source
    } else {
        $version = '3.9.11'
        $tools = Join-Path $root '.tools'
        $relative = if ($IsWindows) { "apache-maven-$version\bin\mvn.cmd" } else { "apache-maven-$version/bin/mvn" }
        $executable = Join-Path $tools $relative
        if (-not (Test-Path $executable)) {
            New-Item -ItemType Directory -Force $tools | Out-Null
            $archive = Join-Path $tools 'maven.zip'
            $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
            Invoke-WebRequest $url -OutFile $archive
            $expected = ((Invoke-WebRequest "$url.sha512").Content.Trim() -split '\s+')[0]
            if ((Get-FileHash $archive -Algorithm SHA512).Hash -ne $expected) {
                throw 'Maven SHA512 mismatch; archive will not be executed.'
            }
            Expand-Archive $archive -DestinationPath $tools -Force
            if (-not $IsWindows) {
                & chmod +x $executable
                if ($LASTEXITCODE -ne 0) { throw 'Cannot make Maven executable.' }
            }
        }
    }
    & $executable --batch-mode --no-transfer-progress -q package
    if ($LASTEXITCODE -ne 0) { throw 'Maven build/test failed.' }
    Write-Host 'Built target\migration-demo.jar'
} finally {
    Pop-Location
}
