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
<#if physiochemicalProperties.pka??>
* pKa acid: ${physiochemicalProperties.pka}
</#if>
<#if physiochemicalProperties.logp??>
* logP: ${physiochemicalProperties.logp}
</#if>
<#if physiochemicalProperties.hlb??>
* HLB: ${physiochemicalProperties.hlb}
</#if>
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
<tab name="Medical Subject Headings (MeSH)">
<#list meshHeadings as synonymsAndType>
* ${synonymsAndType.synonymType}
<#list synonymsAndType.synonyms as syno>
** ${syno}
</#list>
</#list>
</tab>
</#if>

</tabs>
