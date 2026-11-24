# Prints what Windows says is playing right now (Spotify, a browser tab, any app that shows up in the media
# overlay), as one JSON line every half second. The Yahu mod starts this once and reads its output.
# Fields: title, artist, album, durationMs, positionMs (at updatedMs, unix ms), playing, app. "{}" if nothing plays.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1

function Await($operation, [Type]$type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($operation))
    $task.Wait(-1) | Out-Null
    $task.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
$propertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
$manager = Await ($managerType::RequestAsync()) $managerType

$last = ''
$lastSent = [DateTime]::MinValue
# the game that started this script: when it is gone (closed or crashed), stop too instead of running on unseen
$game = (Get-CimInstance Win32_Process -Filter "ProcessId=$PID").ParentProcessId
$checked = [DateTime]::Now
while ($true) {
    if (([DateTime]::Now - $checked).TotalSeconds -ge 3) {
        $checked = [DateTime]::Now
        if (-not (Get-Process -Id $game -ErrorAction SilentlyContinue)) { exit }
    }
    $line = '{}'
    try {
        $session = $manager.GetCurrentSession()
        if ($session) {
            $media = Await ($session.TryGetMediaPropertiesAsync()) $propertiesType
            $timeline = $session.GetTimelineProperties()
            $playback = $session.GetPlaybackInfo()
            $line = [ordered]@{
                title      = [string]$media.Title
                artist     = [string]$media.Artist
                album      = [string]$media.AlbumTitle
                durationMs = [long]$timeline.EndTime.TotalMilliseconds
                positionMs = [long]$timeline.Position.TotalMilliseconds
                updatedMs  = [long]$timeline.LastUpdatedTime.ToUnixTimeMilliseconds()
                playing    = ([string]$playback.PlaybackStatus -eq 'Playing')
                app        = [string]$session.SourceAppUserModelId
            } | ConvertTo-Json -Compress
        }
    } catch {
        $line = '{}'
    }
    # only when something changed, plus a heartbeat every few seconds so the mod knows we are alive
    if ($line -ne $last -or ([DateTime]::UtcNow - $lastSent).TotalSeconds -ge 5) {
        [Console]::Out.WriteLine($line)
        [Console]::Out.Flush()
        $last = $line
        $lastSent = [DateTime]::UtcNow
    }
    Start-Sleep -Milliseconds 500
}
