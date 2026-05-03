# Regenerates the Mermaid graph block in memory/overview.md from the JSON
# branches. Reads memory/root.json, walks children recursively (.json =>
# recurse, .md => terminal leaf), and replaces the existing ```mermaid ...```
# block in overview.md in place.
#
# Usage:  pwsh -File tools/regen-overview.ps1
#         (or: . tools/regen-overview.ps1   from any PS session at repo root)
# Idempotent: produces the same output for the same JSON state.

$ErrorActionPreference = 'Stop'

$repoRoot  = Split-Path $PSScriptRoot -Parent
$memoryDir = Join-Path $repoRoot 'memory'
$overview  = Join-Path $memoryDir 'overview.md'
$root      = Join-Path $memoryDir 'root.json'

if (-not (Test-Path $root))     { throw "root.json not found at $root" }
if (-not (Test-Path $overview)) { throw "overview.md not found at $overview" }

$nodes = [ordered]@{}
$edges = New-Object System.Collections.Generic.List[object]

function Add-Node {
    param([string]$id, [string]$name)
    if (-not $nodes.Contains($id)) { $nodes[$id] = $name }
}

function Expand-Branch {
    param([string]$path, [string]$parentId)
    $data = Get-Content $path -Raw | ConvertFrom-Json
    Add-Node $data.id $data.name
    if ($parentId) { $edges.Add(@{ from = $parentId; to = $data.id }) }
    foreach ($child in $data.children) {
        $childId   = [System.IO.Path]::GetFileNameWithoutExtension($child.ref)
        $childPath = Join-Path $memoryDir $child.ref
        if ($child.ref -match '\.json$') {
            Expand-Branch $childPath $data.id
        } else {
            Add-Node $childId $child.name
            $edges.Add(@{ from = $data.id; to = $childId })
        }
    }
}

Expand-Branch $root $null

# --- Emit Mermaid block ---
$lines = @('```mermaid', 'graph TD')
foreach ($id in $nodes.Keys) {
    $name = $nodes[$id]
    if ($name -match '[\(\)\[\]"\\]') {
        $lines += "    $id[`"$id $name`"]"
    } else {
        $lines += "    $id[$id $name]"
    }
}
$lines += ''
foreach ($e in $edges) {
    $lines += "    $($e.from) --> $($e.to)"
}
$lines += '```'
$block = $lines -join "`r`n"

# --- Replace existing Mermaid block by string indexing ---
$content   = [System.IO.File]::ReadAllText($overview)
$start     = $content.IndexOf('```mermaid')
if ($start -lt 0) { throw 'No ```mermaid block found in overview.md' }
$endMarker = $content.IndexOf('```', $start + 10)
if ($endMarker -lt 0) { throw 'Unterminated ```mermaid block in overview.md' }
$end        = $endMarker + 3
$newContent = $content.Substring(0, $start) + $block + $content.Substring($end)

$utf8NoBomLocal = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($overview, $newContent, $utf8NoBomLocal)

Write-Host "Regenerated Mermaid block in $overview"
Write-Host "  Nodes: $($nodes.Count)"
Write-Host "  Edges: $($edges.Count)"