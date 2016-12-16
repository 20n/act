= ${pageTitle} =

'''Chemical Intermediates''':
{| class='wikitable' style='width: 1000px;'
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
{| class='wikitable' style='width: 1000px;'
 |-
 ! Step
 ! EC numbers
 ! Organisms
<#list pathwayitems?reverse as pathwayitem>
  <#if pathwayitem.isreaction>
  |-
  |${pathwayitem?counter / 2}
  |${pathwayitem.ecnums?join(", ")}
  <#assign extra = pathwayitem.organisms?size - 5>
  <#if extra gt 0>
  |${pathwayitem.organisms[0..4]?join(", ")}, and ${extra} others organisms.
  <#else>
  |${pathwayitem.organisms?join(", ")}
  </#if>
  </#if>
</#list>
|}


<#if dna??>
{| class='wikitable' style='width: 1000px;'
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
