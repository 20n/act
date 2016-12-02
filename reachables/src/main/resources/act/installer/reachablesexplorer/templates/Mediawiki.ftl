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
[[File:${cascade}.png|800px]]
</#if>

<#if pathways??>
''' Pathways
{| class='wikitable'
!
! Pathway
  <#list pathways as pathway>
|-
| ${pathway?counter}
| [[${pathway.link}|${pathway.name}]]
  </#list>
|}
</#if>

<#if patents??>
'''Patents''':<br />
{| class='wikitable'
! Id
! Title
  <#list patents as patent>
|-
| [${patent.link} ${patent.id}]
| ''${patent.title}''
  </#list>
|}
<#else>
'''Patents''': none
</#if>
