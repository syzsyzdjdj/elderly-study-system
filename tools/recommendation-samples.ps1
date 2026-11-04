[CmdletBinding()]
param(
    [ValidateSet('inspect', 'seed', 'export', 'run')]
    [string]$Mode = 'inspect',
    [string]$InputDirectory
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    if (-not (Test-Path -LiteralPath 'backend/target/classes/com/elderly/util/ToolConfiguration.class')) {
        throw 'Build the backend first: mvn -f backend/pom.xml package -DskipTests'
    }
    if ($Mode -eq 'run' -and -not $InputDirectory) { throw 'run requires -InputDirectory <mock snapshot in directory>' }
    & mvn.cmd -q -f backend/pom.xml dependency:build-classpath '-Dmdep.outputFile=target/recommendation-samples-classpath.txt' '-DincludeScope=test'
    if ($LASTEXITCODE -ne 0) { throw 'Failed to resolve dependencies' }
    $sampleClasspath = (Get-Content -Raw -LiteralPath 'backend/target/recommendation-samples-classpath.txt').Trim()
    $javaCommand = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe).Source }
    $javacCommand = Join-Path (Split-Path -Parent $javaCommand) 'javac.exe'
    New-Item -ItemType Directory -Force -Path 'work/recommendation-samples' | Out-Null
    & $javacCommand -encoding UTF-8 -cp "backend/target/classes;$sampleClasspath" -d work/recommendation-samples tools/RecommendationSamples.java
    if ($LASTEXITCODE -ne 0) { throw 'Failed to compile sample utility' }
    $sampleArguments = @($Mode)
    if ($Mode -eq 'run') { $sampleArguments += $InputDirectory }
    & $javaCommand '-Dfile.encoding=UTF-8' -cp "work/recommendation-samples;backend/target/classes;$sampleClasspath" RecommendationSamples @sampleArguments
    if ($LASTEXITCODE -ne 0) { throw 'Sample utility failed; see output above' }
} finally {
    Pop-Location
}
