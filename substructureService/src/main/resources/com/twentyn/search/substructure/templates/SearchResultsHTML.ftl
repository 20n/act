<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <!-- The above 3 meta tags *must* come first in the head; any other head content must come *after* these tags -->
    <link href="assets/css/bootstrap.min.css" rel="stylesheet">
    <!--[if lt IE 9]>
      <script src="https://oss.maxcdn.com/html5shiv/3.7.3/html5shiv.min.js"></script>
      <script src="https://oss.maxcdn.com/respond/1.4.2/respond.min.js"></script>
    <![endif]-->


    <title>Search Results</title>
  </head>
  <body>
    <h1>Search Results</h1>
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/jquery.min.js"></script>
    <script src="assets/js/bootstrap.min.js"></script>

    <form class="form-inline">
      <div class="form-group">
        <div class="input-group input-group-lg">
          <input type="text" class="form-control" id="q" placeholder="<#if query??>${query}<#else>SMILES</#if>" aria-describedby="sizing-addon1">
          <button class="btn btn-primary" type="submit">Search</button>
        </div>
      </div>
    </form>

    <table class="table table-striped">
      <tr><th>Image</th><th>Link</th></tr>
      <#list results as r>
      <tr>
        <td><#if r.imageName??><img src="${assetsUrl}/${r.imageName}" width="200" /><#else>No image available</#if></td>
        <td><a href="${baseUrl}/${r.inchiKey}">${r.pageName}</a></td>
      </tr>
      </#list>
    </table>
  </body>
</html>
