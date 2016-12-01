= ${pageTitle} =

<#list pathwayitems as pathwayitem>
  <#if pathwayitem.isreaction>
    '''Reaction''':
    {| class='wikitable'
     |-
     ! EC number
     | ${pathwayitem.ecnum}
     ! Organisms
     <#list pathwayitem.organisms as org>
     | $org
     </#list>
  <#else>
    '''Chemical''':
    [[pathwayitem.inchiKey|pathwayitem.name]]
  </#if>
</#list>