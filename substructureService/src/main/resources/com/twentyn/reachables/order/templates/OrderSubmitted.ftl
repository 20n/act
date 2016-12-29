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
        <div class="col-md-6 text-center"><h1>Order Submitted</h1></div>
        <div class="col-md-3"></div>
      </div>
      <div class="row">
        <div class="col-md-2"></div>
        <div class="col-md-8">
          <p class="text-center">
          Thank you for submitting your order.  Your order id is ${orderId}.<br />
          Please click the link below to return to the wiki.<br />
          <a class="btn btn-default" href="${returnUrl}" role="button">Return to the wiki</a>
          </p>
        </div>
        <div class="col-md-2"></div>
      </div>
    </div> <!-- end container -->
  </body>
</html>
