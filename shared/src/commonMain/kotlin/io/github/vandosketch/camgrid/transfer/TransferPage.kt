package io.github.vandosketch.camgrid.transfer

/**
 * The page a phone or computer sees at the TV's transfer address: a PIN field, a file input
 * that sends the raw file to `/upload`, and a button that fetches `/download`. Self-contained
 * (inline style and script, nothing loaded from anywhere), so it works without internet.
 */
internal object TransferPage {

    /** Only the inline style and script run, and they may only talk to the TV itself. */
    const val CONTENT_SECURITY_POLICY =
        "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; " +
            "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

    val HTML: String = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CamGrid backup</title>
<style>
body { font-family: system-ui, sans-serif; max-width: 34rem; margin: 0 auto; padding: 1rem; line-height: 1.4; }
h1 { font-size: 1.4rem; }
h2 { font-size: 1.1rem; margin-top: 1.6rem; }
input, button { font-size: 1rem; }
#pin { width: 7ch; letter-spacing: 0.2em; padding: 0.3rem; }
button { padding: 0.5rem 1rem; margin-top: 0.5rem; }
#status { font-weight: bold; min-height: 1.4em; }
</style>
</head>
<body>
<h1>CamGrid backup</h1>
<p><label>PIN shown on the TV: <input id="pin" inputmode="numeric" autocomplete="off" maxlength="6"></label></p>
<p id="status" role="status"></p>
<h2>Send a backup to the TV</h2>
<p>Choose a CamGrid backup file (.json). The TV asks for its password, if it has one, and asks before it replaces the current settings.</p>
<p><input id="file" type="file" accept=".json,application/json,text/plain"><br>
<button id="upload" type="button">Send to TV</button></p>
<h2>Get a backup from the TV</h2>
<p>Export on the TV first, then download the file here.</p>
<p><button id="download" type="button">Download backup</button></p>
<script>
"use strict";
var PIN_HEADER = "X-CamGrid-Pin";
function byId(id) { return document.getElementById(id); }
function show(text) { byId("status").textContent = text; }
function headers() { var h = {}; h[PIN_HEADER] = byId("pin").value.trim(); return h; }
byId("upload").onclick = function () {
  var file = byId("file").files[0];
  if (!file) { show("Choose a file first."); return; }
  show("Sending…");
  var h = headers();
  h["Content-Type"] = "application/octet-stream";
  fetch("/upload", { method: "POST", headers: h, body: file })
    .then(function (r) { return r.text(); })
    .then(show, function () { show("Could not reach the TV. Is the backup screen still open?"); });
};
byId("download").onclick = function () {
  show("Downloading…");
  fetch("/download", { headers: headers() }).then(function (r) {
    if (!r.ok) { return r.text().then(show); }
    var match = /filename="([^"]+)"/.exec(r.headers.get("Content-Disposition") || "");
    return r.blob().then(function (blob) {
      var link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = match ? match[1] : "camgrid-backup.json";
      document.body.appendChild(link);
      link.click();
      link.remove();
      show("Downloaded.");
    });
  }).catch(function () { show("Could not reach the TV. Is the backup screen still open?"); });
};
</script>
</body>
</html>
""".trimStart()
}
