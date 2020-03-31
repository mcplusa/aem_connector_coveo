<%@include file="/libs/foundation/global.jsp"%>
<cq:includeClientLib categories="jquerysamples" />
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Cpvep Search Token</title>
  <script>
    document.addEventListener("DOMContentLoaded", function(event) {
      document.getElementById("submit").addEventListener("click", function () {
        //Set the Request to query.json   
        var url = location.pathname.replace(".html", "/_jcr_content.query.json");

        fetch(url, {
          method: 'get'
        })
          .then(function (response) {
            return response.json();
          })
          .then(function (response) {
            var json = JSON.parse(response.json);
            document.getElementById("token").value = json.token;
          }).catch(err => {
            console.error("Unable to retrive data ", err);
          });
      });
    });
  </script>
</head>

<body>
  <div class="wrapper">
    <div class="main">
      <h1>Coveo search token</h1>

      <form name="signup" id="signup">
        <table>
          <tr>
            <td>
              <label for="last">Token</label>
            </td>
            <td>
              <input type="text" id="token" name="token" value="" />
            </td>
          </tr>
        </table>
        <div>
          <input type="button" value="Get Search Token" name="submit" id="submit" value="Submit">
        </div>
      </form>
    </div>
  </div>
</body>

</html>