=== Results ===

{| class='wikitable'
 ! Image
 ! Name
<#list results as r>
 |-
 | <#if r.imageName??>[[File:${r.imageName}|400px]]<#else>No image available</#if>
 | [[${r.inchiKey}|${r.pageName}]]
</#list>
 |}
