param(
    [Parameter(Mandatory=$true)][string]$Manifest,
    [string]$Output = 'work/course-audio',
    [string]$Voice = 'Microsoft Huihui Desktop'
)
# Run with Windows PowerShell 5.1 for System.Speech; no text leaves this computer.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$items = Get-Content -LiteralPath $Manifest -Raw -Encoding UTF8 | ConvertFrom-Json
if ($items -isnot [array] -and $null -ne $items.data) { $items = $items.data }
$preparedDir = Join-Path $Output 'prepared'
New-Item -ItemType Directory -Force -Path $preparedDir | Out-Null
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$sha = [Security.Cryptography.SHA256]::Create()
try {
    $synth.SelectVoice($Voice)
    $index = 0
    foreach ($item in $items) {
        $bytes = [Text.Encoding]::UTF8.GetBytes($item.text)
        $key = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
        if ($key -ne $item.key) { throw 'Manifest hash mismatch' }
        $file = Join-Path $preparedDir ($key + '.wav')
        if (!(Test-Path -LiteralPath $file)) {
            $temporary = $file + '.tmp'
            $synth.SetOutputToWaveFile($temporary)
            $synth.Speak($item.text)
            $synth.SetOutputToNull()
            Move-Item -LiteralPath $temporary -Destination $file
        }
        $index++
        Write-Output "$index/$($items.Count) $key"
    }
    Copy-Item -LiteralPath $Manifest -Destination (Join-Path $Output 'manifest.json')
} finally { $synth.Dispose(); $sha.Dispose() }
