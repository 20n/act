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
| <#list precursor.molecules as molecule> [[${molecule.inchiKey} ${molecule.name}]] </#list>
| ${sequence.organism}
| ${sequence.sequence}
  <#else>
|-
| <#list precursor.molecules as molecule> [[${molecule.inchiKey} ${molecule.name}]] </#list>
|
|
  </#list>
</#list>
|}

<#if cascade??>
[[File:${cascade}.png]]
</#if>
