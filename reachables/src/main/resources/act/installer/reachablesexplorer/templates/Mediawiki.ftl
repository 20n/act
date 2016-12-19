= ${pageTitle} =
<#if structureRendering??>
[[File:${structureRendering}|400px]]
</#if>

<#if wordcloudRendering??>
'''Word Cloud''':
[[File:${wordcloudRendering}|800px]]
</#if>

'''Inchi''': ${inchi}

<#if smiles??>
'''Smiles''': ${smiles}
</#if>

<#if physiochemicalProperties??>
'''Physio-chemical properties''':
* pKa: ${physiochemicalProperties.pka}
* logP: ${physiochemicalProperties.logp}
* HLB: ${physiochemicalProperties.hlb}
</#if>


<#if hideCascades??>
To order pathway information for this molecule, please [${orderLink} click here].
<#else>
<#if cascade??>
[[File:${cascade}.png|800px]]
</#if>

<#if pathways??>
''' Pathways
{| class='wikitable'
!
! DNA
! Pathway
  <#list pathways as pathway>
|-
| ${pathway?counter}
| ${pathway.hasDna}
| [[${pathway.link}|${pathway.name}]]
  </#list>
|}
</#if>
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


<tabs>

<#if wikipediaUrl??>
<tab name="Wikipedia">
{{#iDisplay:${wikipediaUrl}|500%}}
</tab>
</#if>

<#if bingUsageTerms??>
<tab name="Bing hits">
<#list bingUsageTerms as usageTerm>
* ${usageTerm.usageTerm}
<#list usageTerm.urls as url>
** [${url} ${url}]
</#list>
</#list>
</tab>
</#if>

<#if pubchemSynonyms??>
<tab name="PubChem synonyms">
<#list pubchemSynonyms as synonymsAndType>
* ${synonymsAndType.synonymType}
<#list synonymsAndType.synonyms as syno>
** ${syno}
</#list>
</#list>
</tab>
</#if>

<#if meshHeadings??>
<tab name="Mesh headings">
<#list meshHeadings as synonymsAndType>
* ${synonymsAndType.synonymType}
<#list synonymsAndType.synonyms as syno>
** ${syno}
</#list>
</#list>
</tab>
</#if>

</tabs>
