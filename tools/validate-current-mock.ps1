[CmdletBinding()]
param([string]$JdkHome = 'D:\jdk17', [string]$OutputDirectory = 'work/current-mock-validation', [switch]$ReuseInput)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $env:JAVA_HOME = $JdkHome
    $env:Path = "$JdkHome\bin;$env:Path"
    New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
    & mvn.cmd -q -f spark-analysis/pom.xml compile '-Dspark.version=3.5.1'
    if ($LASTEXITCODE -ne 0) { throw 'Spark compilation failed' }
    & mvn.cmd -q -f backend/pom.xml compile
    if ($LASTEXITCODE -ne 0) { throw 'Backend compilation failed' }
    & mvn.cmd -q -f backend/pom.xml dependency:build-classpath '-Dmdep.outputFile=target/recommendation-samples-classpath.txt' '-DincludeScope=test'
    if ($LASTEXITCODE -ne 0) { throw 'Classpath resolution failed' }
    $validationJars = (Get-Content -Raw backend/target/recommendation-samples-classpath.txt).Trim().Split(';') | Where-Object { $_ -notmatch 'log4j-to-slf4j|logback-classic|logback-core' }
    $validationCp = 'spark-analysis/target/classes;backend/target/classes;' + ($validationJars -join ';')
    & "$JdkHome\bin\javac.exe" -encoding UTF-8 -cp $validationCp -d $OutputDirectory tools/ValidateCurrentMock.java
    if ($LASTEXITCODE -ne 0) { throw 'Validation utility compilation failed' }
    $validationArgs = @($OutputDirectory)
    if ($ReuseInput) { $validationArgs += (Join-Path $OutputDirectory 'in') }
    & "$JdkHome\bin\java.exe" --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED -cp "$OutputDirectory;$validationCp" ValidateCurrentMock @validationArgs
    if ($LASTEXITCODE -ne 0) { throw 'Validation execution failed' }
    & python tools/check_mock_results.py $OutputDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Independent checks failed' }
} finally { Pop-Location }
