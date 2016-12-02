= <#list pathwayitems?reverse as pathwayitem><#if pathwayitem.isreaction><#else>${pathwayitem.name}<#sep>${"  <-  "}</#if></#list> =

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