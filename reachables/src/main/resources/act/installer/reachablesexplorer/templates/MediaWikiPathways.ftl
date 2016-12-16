= ${pageTitle} =

'''Chemical Intermediates''':
{| class='wikitable'
 |-
  <#list pathwayitems?reverse as pathwayitem>
    <#if pathwayitem.isreaction>
    <#else>
      |<#if pathwayitem.structureRendering??>[[File:${pathwayitem.structureRendering}|200px]]</#if>
    </#if>
  </#list>
  |-
  <#list pathwayitems?reverse as pathwayitem>
    <#if pathwayitem.isreaction>
    <#else>
      |${pathwayitem.name}
    </#if>
  </#list>
|}

'''Reaction Steps''':
{| class='wikitable'
 |-
 ! Step
 ! EC numbers
 ! Organisms
<#list pathwayitems?reverse as pathwayitem>
  <#if pathwayitem.isreaction>
  |-
  |${pathwayitem?counter / 2}
  |${pathwayitem.ecnums?join(", ")}
  |${pathwayitem.organisms?join(", ")}
  </#if>
</#list>
|}


<#if dna??>
{| class='wikitable'
 |-
 !
 ! DNA design
 ! Proteins used for DNA design
     <#list dna as design>
 |-
 | ${design.num}
 | [[:File:${design.file}|...${design.sample}...]]
 | <#list design.org_ec as protein>Protein ${protein?counter}: ${protein}<br></#list>
     </#list>
 |}

<#else>
'''No DNA constructs available at this time.'''
</#if>
