= ${pageTitle} =
<#if structureRendering??>
[[File:${structureRendering}|400px]]
</#if>

<#if wordcloud??>
'''Word Cloud''':<br />
[[File:${wordcloudRendering}|800px]]
</#if>

'''Inchi''': ${inchi}

<#if smiles??>
'''Smiles''': ${smiles}
</#if>


'''Precursor Molecules''':<br />
{| class='wikitable'
! Substrates
! Organism
! Sequence
<#list precursors as precursor>
  <#list precursor.sequences as sequence>
|-
| <#list precursor.molecules as molecule> <#if molecule.inchiKey??> [[${molecule.inchiKey} ${molecule.name}]] <#else> ${molecule.name} </#if> </#list>
| ${sequence.organism}
| ${sequence.sequence}
  <#else>
|-
| <#list precursor.molecules as molecule> <#if molecule.inchiKey??> [[${molecule.inchiKey} ${molecule.name}]] <#else> ${molecule.name} </#if> </#list>
|
|
  </#list>
</#list>
|}

<#if cascade??>
[[File:${cascade}.png]]
</#if>
