<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="shortcut icon" href="/assets/favicon.ico">
    <!-- The above 3 meta tags *must* come first in the head; any other head content must come *after* these tags -->
    <link href="assets/css/bootstrap.min.css" rel="stylesheet">
    <!--[if lt IE 9]>
      <script src="https://oss.maxcdn.com/html5shiv/3.7.3/html5shiv.min.js"></script>
      <script src="https://oss.maxcdn.com/respond/1.4.2/respond.min.js"></script>
    <![endif]-->

    <title>Order Pathways and Designs</title>
  </head>
  <body>
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/jquery.min.js"></script>
    <script src="assets/js/bootstrap.min.js"></script>
    <div class="container">
      <div class="row">
        <div class="col-md-3"><img src="assets/img/20n_small.png" width="128px"/></div>
        <div class="col-md-6 text-center"><h1>Order Pathways And Designs</h1></div>
        <div class="col-md-3"></div>
      </div>
    <#if errorMsg??>
      <div class="row">
        <div class="col-md-2"></div>
        <div class="col-md-8">
          <div class="alert alert-danger" role="alert">
            <span class="glyphicon glyphicon-exclamation-sign" aria-hidden="true"></span>
            Error: ${errorMsg}
          </div>
        </div>
        <div class="col-md-2"></div>
      </div>
    </#if>
      <div class="row">
        <div class="col-md-2"></div>
        <div class="col-md-8">
          <p>
          This form allows users to make pathways and genetic design purchase requests for molecules that appear in the
          reachables repository.  Requests may be queued until you have reached a minimum number of requests as
          stipulated in your contract, or until payment can be arranged and settled.  If you have questions, please
          <a href="mailto:${adminEmail}">email the site administrator</a>.
          </p>
        </div>
        <div class="col-md-2"></div>
      </div>
      <div class="row">
        <div class="col-md-2"></div>
        <div class="col-md-8">
          <table class="table table-bordered">
          <col
            <thead><tr><th class="col-md-4">Image</th><th>Name</th></tr></thead>
            <tbody>
              <tr>
                <td class="col-md-4"><img src="${imageLink}" aria-hidden="true" width="400px" /></td>
                <td style="vertical-align: middle">${name}</td>
              </tr>
            <tbody>
          </table>
        </div>
        <div class="col-md-2"></div>
      </div>
      <div class="row">
        <div class="col-md-2"></div>
        <div class="col-md-8">
          <form action="/order" method="POST">
            <input type="hidden" name="inchi_key" value="${inchiKey}" aria-hidden="true" />
            <input type="hidden" name="order_id" value="${orderId}" aria-hidden="true" />
            <div class="form-group">
              <label for="email">Contact email</label>
              <input type="email" class="form-control" id="email" placeholder="Email" name="email" /><br />
            </div>
            <div class="text-center">
              <button type="submit" class="btn btn-default">Submit</button>
            </div>
          </form>
        </div>
        <div class="col-md-2"></div>
      </div>
    </div> <!-- end container -->
  </body>
</html>
