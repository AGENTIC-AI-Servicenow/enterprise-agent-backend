try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/incidents" -Method GET
    Write-Host "Status: $($response.StatusCode)"
    Write-Host "Body: $($response.Content)"
} catch {
    Write-Host "Exception type: $($_.Exception.GetType().FullName)"
    Write-Host "Message: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        Write-Host "Status: $statusCode"
        Write-Host "Error Body: $body"
    } else {
        Write-Host "No HTTP response (connection refused or backend not running)"
    }
}
