# IRL Tunes - Windows media session reader.
#
# Reads the "now playing" session that Windows itself tracks (the same data the
# volume flyout / lock screen shows) through the WinRT GlobalSystemMediaTransportControls
# API, and prints one compact JSON object per line on stdout.
#
# The Minecraft client starts this script once, reads its stdout, and kills it on exit.
# It also exits on its own when the parent process (Minecraft) disappears.

param(
    [int]$ParentPid = 0,
    [int]$IntervalMs = 1000
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Line([hashtable]$obj) {
    [Console]::Out.WriteLine(($obj | ConvertTo-Json -Compress))
    [Console]::Out.Flush()
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $asTaskGeneric = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1

    function Await($op, [Type]$type) {
        $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
        if (-not $task.Wait(3000)) { throw 'WinRT call timed out' }
        return $task.Result
    }

    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]
    $propsType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]
    $streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType]
    $asStreamForRead = [System.IO.WindowsRuntimeStreamExtensions].GetMethod('AsStreamForRead', [Type[]]@([Windows.Storage.Streams.IInputStream]))

    $manager = Await ($managerType::RequestAsync()) $managerType
} catch {
    Write-Line @{ error = "init: $($_.Exception.Message)" }
    exit 2
}

$lastKey = $null

while ($true) {
    if ($ParentPid -gt 0 -and -not (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)) { exit 0 }

    try {
        # Prefer a session that is actually playing; fall back to the one Windows considers current.
        $session = $null
        foreach ($s in $manager.GetSessions()) {
            if ($s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { $session = $s; break }
        }
        if ($null -eq $session) { $session = $manager.GetCurrentSession() }

        if ($null -eq $session) {
            Write-Line @{ active = $false }
        } else {
            $props = Await ($session.TryGetMediaPropertiesAsync()) $propsType
            $info = $session.GetPlaybackInfo()
            $tl = $session.GetTimelineProperties()

            $playing = ($info.PlaybackStatus -eq 'Playing')
            $posMs = [long]$tl.Position.TotalMilliseconds
            $durMs = [long]($tl.EndTime - $tl.StartTime).TotalMilliseconds
            # Windows only refreshes Position occasionally; extrapolate while playing.
            if ($playing -and $tl.LastUpdatedTime.Year -gt 1601) {
                $posMs += [long]([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds
            }
            if ($durMs -gt 0) { $posMs = [Math]::Max(0, [Math]::Min($posMs, $durMs)) }

            $out = @{
                active   = $true
                playing  = $playing
                app      = [string]$session.SourceAppUserModelId
                title    = [string]$props.Title
                artist   = [string]$props.Artist
                album    = [string]$props.AlbumTitle
                position = $posMs
                duration = $durMs
            }

            # Only ship the cover art when the track changes (it can be ~100 KB of base64).
            $key = "$($out.app)|$($out.title)|$($out.artist)|$($out.album)"
            if ($key -ne $lastKey -and $null -ne $props.Thumbnail) {
                try {
                    $ras = Await ($props.Thumbnail.OpenReadAsync()) $streamType
                    # PowerShell's COM wrapper hides the WinRT interface, so resolve the overload explicitly.
                    $stream = $asStreamForRead.Invoke($null, @($ras))
                    $ms = New-Object System.IO.MemoryStream
                    $stream.CopyTo($ms)
                    $stream.Dispose()
                    if ($ms.Length -gt 0 -and $ms.Length -lt 4MB) {
                        $out.cover = [Convert]::ToBase64String($ms.ToArray())
                    }
                } catch { }
            }
            $lastKey = $key
            Write-Line $out
        }
    } catch {
        Write-Line @{ active = $false; error = $_.Exception.Message }
    }

    Start-Sleep -Milliseconds $IntervalMs
}
