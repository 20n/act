= ${pageTitle} =

<#list pathwayitems as pathwayitem>
  <#if pathwayitem.isreaction>
'''Reaction''':
{| class='wikitable'
 |-
 ! EC numbers
   <#list pathwayitem.ecnums as ecnum>
 |-
 | ${ecnum}
   </#list>
 |}
{|

{| class='wikitable'
 |-
 ! Organisms
     <#list pathwayitem.organisms as org>
 |-
 | ${org}
     </#list>
 |}
  <#else>
'''Chemical''':
    <#if pathwayitem.link??>
[[${pathwayitem.link}|${pathwayitem.name}]]
    <#else>
${pathwayitem.name}
    </#if>
    <#if pathwayitem.structureRendering??>
[[File:${pathwayitem.structureRendering}|400px]]
    </#if>

  </#if>


</#list>
