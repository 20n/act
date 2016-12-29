<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="shortcut icon" href="%PUBLIC_URL%/favicon.ico">
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
      <div class="row">
        <div class="col-md-2"></div>
        <div class="col-md-8">
          <p>
          <#if molNotRecognized??>
            We were unable to recognize the molecule you are attempting to order.
            Please use the order links on individual reachables pages to make order requests.
          <#elseif orderIdInvalid??>
            We were unable to complete your order.  You may have attempted to place your order multiple times
            or your order may have timed out.  Please use the link below to navigate back to the order page and
            try again. <br />
            <div class="text-center">
              <a class="btn btn-default" href="${sourcePageLink}" role="button">Return to the order page</a>
            </div>
            <br />
          <#else>
            An unexpected error occurred.
          </#if>
            If you are having trouble with order links, please
          <a href="mailto:${adminEmail}">email the site administrator</a> for assistance.
          </p>
        </div>
        <div class="col-md-2"></div>
      </div>
    </div>
  </body>
</html>
