$out       = "project_dump.txt"
$repoRoot  = (Get-Location).Path
$separator = '=' * 80

# Loud delete. No SilentlyContinue. If the file cannot be removed, halt.
if (Test-Path $out) {
    Remove-Item $out -Force
    if (Test-Path $out) {
        throw "Could not delete $out. Close whatever is holding it open and retry."
    }
}

# Build the whole dump in memory, write once at the end.
$parts = [System.Collections.Generic.List[string]]::new()

Get-ChildItem -Path backend, frontend -Recurse -File `
    -Include *.java, *.ts, *.html, *.scss, *.xml, *.properties, *.json, *.md `
  | Where-Object { $_.FullName -notmatch '\\(target|node_modules|dist|\.angular|build)\\' } `
  | Sort-Object FullName -Unique `
  | ForEach-Object {
    $relativePath = $_.FullName.Substring($repoRoot.Length + 1) -replace '\\', '/'
    $parts.Add($separator)
    $parts.Add("FILE: $relativePath")
    $parts.Add($separator)
    $parts.Add((Get-Content $_.FullName -Raw))
    $parts.Add('')
}

# Set-Content overwrites. Even if a future me removes the Remove-Item block,
# this still does not append.
Set-Content -Path $out -Value ($parts -join "`r`n") -Encoding UTF8 -NoNewline

$fileCount = ($parts | Where-Object { $_ -like 'FILE:*' }).Count
$sizeMB    = [math]::Round((Get-Item $out).Length / 1MB, 2)
Write-Host "Dump complete. Files: $fileCount. Size: $sizeMB MB."