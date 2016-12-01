= ${pageTitle} =
<#if structureRendering??>
[[File:${structureRendering}|400px]]
</#if>

<#if wordcloudRendering??>
'''Word Cloud''':<br />
[[File:${wordcloudRendering}|800px]]
</#if>

'''Inchi''': ${inchi}

<#if smiles??>
'''Smiles''': ${smiles}
</#if>

<#if cascade??>
[[File:${cascade}.png]]
</#if>

{| class='wikitable'
! Id
! Title
<#if patents??>
'''Patents''':<br />
<#list patents as patent>
|-
| [${patent.link}|${patent.id}]
| ''${patent.title}''
</#list>
|}
<#else>
'''Patents''': none
</#if>

'''Precursor Molecules''':<br />
{| class='wikitable'
! Substrates
<#list precursors as precursor>
|-
| <#list precursor.molecules as molecule> <#if molecule.inchiKey??> [[${molecule.inchiKey}|${molecule.name}]] <#else> ${molecule.name} </#if> </#list>
</#list>
|}