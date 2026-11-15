param(
    [string]$SourceDirectory = "$env:TEMP",
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'
$bundleDirectory = Join-Path $ProjectRoot 'src/main/resources/assets/mpsqcamera/custom_models'
$sourceDirectoryOut = Join-Path $ProjectRoot 'src/client/resources/assets/mpsqcamera/models/pets'
New-Item -ItemType Directory -Path $bundleDirectory,$sourceDirectoryOut -Force | Out-Null

function Model-Vector($value) {
    if ($null -eq $value -or @($value).Count -eq 0) { return @(0.0,0.0,0.0) }
    if (@($value).Count -ne 3) { throw 'Invalid model vector' }
    return @($value | ForEach-Object { [double]$_ })
}

function Convert-Bone($node, $elementRefs, $groups) {
    $group = $node
    if ($groups.ContainsKey([string]$node.uuid)) { $group = $groups[[string]$node.uuid] }
    $elements = [System.Collections.Generic.List[int]]::new()
    $children = [System.Collections.Generic.List[object]]::new()
    foreach ($child in $node.children) {
        if ($child -is [string]) {
            if ($elementRefs.ContainsKey($child)) { $elements.Add([int]$elementRefs[$child]) }
        } elseif ($null -ne $child) {
            $children.Add((Convert-Bone $child $elementRefs $groups))
        }
    }
    return [ordered]@{
        name = [string]$group.name
        origin = @(Model-Vector $group.origin)
        rotation = @(Model-Vector $group.rotation)
        elements = @($elements.ToArray())
        children = @($children.ToArray())
    }
}

foreach ($stem in @('bs_vm_crab','bs_vm_penguin','baby_happy_ghast_mount','nm_colin_the_creeper','nm_exo_the_enderman','nm_slak_the_skeleton','nm_zekk_the_zombie','nm_tiger','nm_tiger_baby')) {
    $sourcePath = Join-Path $SourceDirectory ($stem + '.bbmodel')
    if (-not (Test-Path -LiteralPath $sourcePath)) { throw "Missing model: $sourcePath" }
    $model = Get-Content -LiteralPath $sourcePath -Raw | ConvertFrom-Json
    $elements = [System.Collections.Generic.List[object]]::new()
    $elementRefs = @{}
    foreach ($element in $model.elements) {
        if ($element.type -and $element.type -ne 'cube') { throw "Unsupported element type in ${stem}: $($element.type)" }
        $faces = [ordered]@{}
        foreach ($faceProperty in $element.faces.PSObject.Properties) {
            $face = $faceProperty.Value
            if ($null -eq $face.texture) { continue }
            $textureIndex = [int]$face.texture
            $texture = $model.textures[$textureIndex]
            $uvWidth = if ($texture.uv_width) { $texture.uv_width } elseif ($model.resolution.width) { $model.resolution.width } else { $texture.width }
            $uvHeight = if ($texture.uv_height) { $texture.uv_height } elseif ($model.resolution.height) { $model.resolution.height } else { $texture.height }
            if ($uvWidth -le 0 -or $uvHeight -le 0) { throw "Missing UV resolution: $stem" }
            $uv = @($face.uv)
            $faces[$faceProperty.Name] = [ordered]@{
                texture = [string]$textureIndex
                uv = @(([double]$uv[0] / [double]$uvWidth),([double]$uv[1] / [double]$uvHeight),([double]$uv[2] / [double]$uvWidth),([double]$uv[3] / [double]$uvHeight))
                rotation = [int]$face.rotation
            }
        }
        $elementRefs[[string]$element.uuid] = $elements.Count
        $elements.Add([ordered]@{
            from = @($element.from | ForEach-Object { [double]$_ - [double]$element.inflate }); to = @($element.to | ForEach-Object { [double]$_ + [double]$element.inflate })
            origin = @(Model-Vector $element.origin); rotation = @(Model-Vector $element.rotation)
            faces = $faces
        })
    }
    $groups = @{}
    foreach ($group in $model.groups) { $groups[[string]$group.uuid] = $group }
    $bones = [System.Collections.Generic.List[object]]::new()
    foreach ($node in $model.outliner) { if ($node -isnot [string]) { $bones.Add((Convert-Bone $node $elementRefs $groups)) } }
    $animations = [System.Collections.Generic.List[object]]::new()
    foreach ($animation in $model.animations) {
        $animators = [ordered]@{}
        foreach ($animatorProperty in $animation.animators.PSObject.Properties) {
            $source = $animatorProperty.Value
            if ($source.type -ne 'bone') { continue }
            $tracks = [ordered]@{}
            foreach ($keyframe in $source.keyframes) {
                if ($keyframe.channel -notin @('position','rotation','scale')) { continue }
                $point = $keyframe.data_points[0]
                $values = @(([double]$point.x),([double]$point.y),([double]$point.z))
                # Blockbench animation X/Y angles use the opposite sign to the
                # positive-Y model-space rotations used by our renderer.
                if ($keyframe.channel -eq 'rotation') { $values[0] = -$values[0]; $values[1] = -$values[1] }
                if (-not $tracks.Contains($keyframe.channel)) { $tracks[$keyframe.channel] = [System.Collections.Generic.List[object]]::new() }
                $tracks[$keyframe.channel].Add([ordered]@{ time = [double]$keyframe.time; values = $values; interpolation = [string]$keyframe.interpolation })
            }
            foreach ($key in @($tracks.Keys)) { $tracks[$key] = @($tracks[$key].ToArray() | Sort-Object { [double]$_['time'] }) }
            $animators[[string]$source.name] = $tracks
        }
        $loops = $animation.loop -eq $true -or [string]$animation.loop -eq 'loop'
        $animations.Add([ordered]@{ name = [string]$animation.name; length = [double]$animation.length; loop = $(if($loops){'loop'}else{'once'}); animators = $animators })
    }
    $textures = [ordered]@{}
    for ($i=0; $i -lt $model.textures.Count; $i++) { $textures[[string]$i] = [string]$model.textures[$i].source }
    $bundle = [ordered]@{ elements=@($elements.ToArray()); bones=@($bones.ToArray()); animations=@($animations.ToArray()); meshes=@(); textures=$textures }
    $json = ConvertTo-Json -InputObject $bundle -Depth 100 -Compress
    [IO.File]::WriteAllText((Join-Path $bundleDirectory ($stem + '.json')),$json,[Text.UTF8Encoding]::new($false))
    $destination = Join-Path $sourceDirectoryOut ($stem + '.bbmodel')
    if((Resolve-Path -LiteralPath $sourcePath).Path -ne (Resolve-Path -LiteralPath $destination -ErrorAction SilentlyContinue).Path){Copy-Item -LiteralPath $sourcePath -Destination $destination -Force}
    Write-Output "$stem imported ($($elements.Count) cubes, $($animations.Count) animations)"
}
