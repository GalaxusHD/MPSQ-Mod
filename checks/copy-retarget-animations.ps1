$ErrorActionPreference='Stop'
$source=Join-Path $PSScriptRoot '../src/client/resources/assets/mpsqcamera/models/pets'
$output=Join-Path $PSScriptRoot '../../Animation-Copies'
New-Item -ItemType Directory -Force $output | Out-Null
function ReadModel($name){Get-Content -LiteralPath (Join-Path $source "$name.bbmodel") -Raw | ConvertFrom-Json -Depth 100}
function BoneMap($nodes,$map){foreach($node in $nodes){if($node -isnot [string]){$map[$node.name]=$node;BoneMap $node.children $map}}}
function Transfer($model,$donor,$clipName,$names,$positionScale){
    $map=@{};BoneMap $model.outliner $map
    $clip=$donor.animations | Where-Object name -eq $clipName
    if(@($clip).Count -ne 1){throw "Expected one $clipName clip"}
    $clip.uuid=[guid]::NewGuid().ToString();$clip.selected=$true
    $animators=[ordered]@{}
    foreach($property in $clip.animators.PSObject.Properties){
        $animator=$property.Value
        if($animator.type -ne 'bone'){throw "Unexpected effect animator in $clipName"}
        $name=$animator.name;if($names.ContainsKey($name)){$name=$names[$name]}
        if(!$map.ContainsKey($name)){throw "Missing target bone: $name"}
        $animator.name=$name
        foreach($frame in $animator.keyframes){
            $frame.uuid=[guid]::NewGuid().ToString()
            if($frame.channel -eq 'position'){
                foreach($point in $frame.data_points){foreach($axis in 'x','y','z'){$point.$axis=[double]::Parse([string]$point.$axis,[Globalization.CultureInfo]::InvariantCulture)*$positionScale}}
                foreach($key in 'bezier_left_value','bezier_right_value'){if($frame.PSObject.Properties[$key]){$frame.$key=@($frame.$key|ForEach-Object{[double]$_*$positionScale})}}
            }
        }
        $animator.keyframes=@($animator.keyframes|Sort-Object time)
        $animators[$map[$name].uuid]=$animator
    }
    $clip.animators=[pscustomobject]$animators
    foreach($existing in $model.animations){$existing.selected=$false}
    $model.animations=@($model.animations)+@($clip)
    return $model
}
$names='nm_tiger','nm_tiger_baby','nm_mini_you_1','nm_exo_the_enderman'
$hashes=@{};foreach($name in $names){$hashes[$name]=(Get-FileHash (Join-Path $source "$name.bbmodel")).Hash}
$tiger=ReadModel 'nm_tiger';$baby=ReadModel 'nm_tiger_baby'
$tiger=Transfer $tiger $baby 'roll' @{'nm_tiger_baby'='nm_tiger'} ([double]$tiger.outliner[0].origin[1]/[double]$baby.outliner[0].origin[1])
$mini=ReadModel 'nm_mini_you_1';$enderman=ReadModel 'nm_exo_the_enderman'
$root=$mini.outliner[0]
# A neutral helper gives torso/head/arms the donor's torso rotation without
# rotating the legs twice. Existing animations retain their original bone IDs.
$upper=@($root.children|Where-Object{$_ -is [string] -or $_.name -in 'right_arm','left_arm','h_head'})
$lower=@($root.children|Where-Object{$_ -isnot [string] -and $_.name -notin 'right_arm','left_arm','h_head'})
$torso=[pscustomobject]@{name='teleport_torso';uuid=[guid]::NewGuid().ToString();origin=@(0,4.1,0);rotation=@(0,0,0);color=0;export=$true;visibility=$true;children=$upper}
$root.children=@($torso)+$lower
$mini=Transfer $mini $enderman 'teleport' @{'torso'='teleport_torso'} (16.0/18.0)
$models=@{'nm_tiger_roll_copy'=$tiger;'nm_mini_you_teleport_copy'=$mini}
foreach($entry in $models.GetEnumerator()){
    $path=Join-Path $output ($entry.Key+'.bbmodel')
    [IO.File]::WriteAllText($path,($entry.Value|ConvertTo-Json -Depth 100 -Compress),[Text.UTF8Encoding]::new($false))
    $check=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json -Depth 100
    $bones=@{};BoneMap $check.outliner $bones
    $ids=@($bones.Values|ForEach-Object{$_.uuid})
    foreach($clip in @($check.animations | Where-Object { $_.name -in 'roll','teleport' })){foreach($anim in $clip.animators.PSObject.Properties){if($anim.Value.type -eq 'bone' -and $anim.Name -notin $ids){throw "Unresolved bone in $path"}}}
    foreach($texture in $check.textures){if(!$texture.source.StartsWith('data:image/')){throw "Nonembedded texture in $path"}}
    "PASS $path : $($check.animations.Count) animations; bone references and embedded textures valid"
}
foreach($name in $names){if((Get-FileHash (Join-Path $source "$name.bbmodel")).Hash -ne $hashes[$name]){throw "Original changed: $name"}}
'PASS: all four original files unchanged'
