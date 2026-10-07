package io.github.vandosketch.camgrid.transfer

/**
 * The page a phone or computer sees at the TV's transfer address: a PIN field, a file picker
 * that sends the raw file to `/upload`, and a button that fetches `/download`. Styled like the
 * app (dark Material 3 colors) and self-contained (inline style and script, nothing loaded from
 * anywhere), so it works without internet.
 *
 * The TV's QR code links to `/#key=` plus 32 hex digits. The script takes the key from that
 * fragment (which browsers never send to the server), removes it from the address bar and history,
 * and sends it in place of the PIN, so scanning the code is enough. Typing the PIN by hand still
 * works, and the PIN field comes back when the key stops working (the TV started a new transfer).
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
<meta name="color-scheme" content="dark">
<title>CamGrid backup</title>
<style>
:root { --bg: #000; --surface: #121212; --card: #1E2329; --tonal: #2A3440; --text: #E6E6E6; --muted: #B8C0CA; --primary: #8AB4F8; --on-primary: #002A5C; --error: #FF8A80; --ok: #81C995; }
* { box-sizing: border-box; }
[hidden] { display: none !important; }
html { background: var(--bg); color-scheme: dark; }
body { margin: 0; background: var(--bg); color: var(--text); font: 16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; -webkit-text-size-adjust: 100%; }
main { max-width: 34rem; margin: 0 auto; padding: 1.25rem 1rem 2.5rem; }
header { display: flex; align-items: center; gap: 0.85rem; margin: 0.5rem 0 1.25rem; }
.logo { flex: none; width: 2.75rem; height: 2.75rem; border-radius: 12px; background: var(--tonal); display: grid; place-items: center; }
.logo svg { width: 1.6rem; height: 1.6rem; fill: var(--primary); }
h1 { margin: 0; font-size: 1.5rem; font-weight: 600; line-height: 1.2; }
.sub { margin: 0; color: var(--muted); font-size: 0.95rem; }
.card { background: var(--card); border-radius: 16px; padding: 1.1rem; margin-bottom: 1rem; }
h2 { margin: 0 0 0.35rem; font-size: 1.1rem; font-weight: 600; }
p { margin: 0 0 0.9rem; }
.hint { color: var(--muted); font-size: 0.93rem; }
label.field { display: block; color: var(--muted); font-size: 0.9rem; margin-bottom: 0.4rem; }
#pin { display: block; width: 100%; padding: 0.7rem 0.5rem; border: 2px solid var(--tonal); border-radius: 12px; background: var(--surface); color: var(--text); font: 600 1.9rem/1.2 ui-monospace, "SF Mono", Menlo, Consolas, monospace; letter-spacing: 0.4em; text-indent: 0.4em; text-align: center; outline: none; }
#pin:focus { border-color: var(--primary); }
#linked { display: flex; align-items: center; gap: 0.6rem; margin: 0; color: var(--ok); font-weight: 600; }
#linked svg { flex: none; width: 1.5rem; height: 1.5rem; fill: var(--ok); }
#linked span { flex: 1; }
button.link { display: inline; width: auto; min-height: 0; padding: 0.4rem 0; border: 0; border-radius: 0; background: none; color: var(--primary); font-size: 0.9rem; font-weight: 500; }
.drop { display: flex; flex-direction: column; align-items: center; gap: 0.35rem; padding: 1.4rem 1rem; margin-bottom: 1rem; border: 2px dashed #4A5663; border-radius: 14px; background: var(--surface); text-align: center; cursor: pointer; transition: border-color .15s, background .15s; }
.drop svg { width: 2rem; height: 2rem; fill: var(--muted); }
.drop:hover, .drop.over, .drop:focus-within { border-color: var(--primary); }
.drop.over { background: var(--tonal); }
.drop.chosen { border-style: solid; border-color: var(--primary); }
.drop.chosen svg { fill: var(--primary); }
#fileName { font-weight: 600; word-break: break-all; }
.drop small { color: var(--muted); font-size: 0.85rem; }
.sr { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0 0 0 0); border: 0; }
button { display: block; width: 100%; min-height: 3.25rem; padding: 0.75rem 1.5rem; border-radius: 999px; font-family: inherit; font-size: 1.05rem; font-weight: 600; line-height: 1.2; cursor: pointer; transition: filter .15s, opacity .15s; }
button:focus-visible { outline: 3px solid var(--text); outline-offset: 2px; }
button:disabled { opacity: 0.45; cursor: default; }
.filled { border: 0; background: var(--primary); color: var(--on-primary); }
.tonal { border: 1px solid #4A5663; background: var(--tonal); color: var(--primary); }
button:not(:disabled):hover { filter: brightness(1.1); }
#status { display: none; margin-bottom: 1rem; padding: 0.85rem 1rem; border-radius: 12px; border-left: 4px solid var(--muted); background: var(--tonal); }
#status.show { display: block; }
#status.ok { border-color: var(--ok); background: #1B3326; color: #CDEFD7; }
#status.err { border-color: var(--error); background: #3A1F1F; color: #FFD7D2; }
</style>
</head>
<body>
<main>
<header>
<div class="logo" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 3h8v8H3zm10 0h8v8h-8zM3 13h8v8H3zm10 0h8v8h-8z"/></svg></div>
<div><h1>CamGrid</h1><p class="sub">Backup transfer</p></div>
</header>

<section class="card">
<p id="linked" hidden><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg><span>Connected with the QR code</span><button id="usePin" class="link" type="button">Use PIN</button></p>
<div id="pinBox">
<label class="field" for="pin">PIN shown on the TV</label>
<input id="pin" inputmode="numeric" pattern="[0-9]*" autocomplete="off" maxlength="6" placeholder="------">
</div>
</section>

<div id="status" role="status" aria-live="polite"></div>

<section class="card">
<h2>Send a backup to the TV</h2>
<p class="hint">Choose a CamGrid backup file (.json). The TV asks for its password, if it has one, and asks before it replaces the current settings.</p>
<label class="drop" id="drop">
<input class="sr" id="file" type="file" accept=".json,application/json,text/plain">
<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zm-1 7V3.5L18.5 9zM8 15h3v3h2v-3h3l-4-4z"/></svg>
<span id="fileName">Choose backup file</span>
<small id="fileHint">Tap to pick a file, or drop it here</small>
</label>
<button id="upload" class="filled" type="button">Send to TV</button>
</section>

<section class="card">
<h2>Get a backup from the TV</h2>
<p class="hint">Export on the TV with a password first, then download the file here.</p>
<button id="download" class="tonal" type="button">Download backup</button>
</section>
</main>
<script>
"use strict";
var PIN_HEADER = "X-CamGrid-Pin";
var OFFLINE = "Could not reach the TV. Is the backup screen still open?";
function byId(id) { return document.getElementById(id); }
var pin = byId("pin"), fileInput = byId("file"), drop = byId("drop"), statusBox = byId("status");
var chosen = null;
var key = "";

function show(text, kind) {
  statusBox.textContent = text;
  statusBox.className = "show" + (kind ? " " + kind : "");
}
function busy(on) { byId("upload").disabled = on; byId("download").disabled = on; }
function headers() { var h = {}; h[PIN_HEADER] = key || pin.value.trim(); return h; }
function usePin() {
  key = "";
  byId("linked").hidden = true;
  byId("pinBox").hidden = false;
  pin.focus();
}
function finish(r) {
  if (key && (r.status === 401 || r.status === 403)) {
    usePin();
    show("This QR code is no longer valid. Enter the PIN shown on the TV, or scan the code again.", "err");
    return Promise.resolve();
  }
  return r.text().then(function (t) { show(t || ("Error " + r.status), r.ok ? "ok" : "err"); });
}
function offline() { show(OFFLINE, "err"); }

(function () {
  var m = /^#key=([0-9a-f]{32})(?![^&])/i.exec(location.hash || "");
  if (location.hash && window.history && history.replaceState) {
    history.replaceState(null, "", location.pathname + location.search);
  }
  if (m) { key = m[1].toLowerCase(); byId("linked").hidden = false; byId("pinBox").hidden = true; }
  else { pin.focus(); }
})();

byId("usePin").onclick = usePin;
pin.oninput = function () {
  var digits = pin.value.replace(/[^0-9]/g, "").slice(0, 6);
  if (digits !== pin.value) pin.value = digits;
};

function pick(file) {
  chosen = file || null;
  drop.className = chosen ? "drop chosen" : "drop";
  byId("fileName").textContent = chosen ? chosen.name : "Choose backup file";
  byId("fileHint").textContent = chosen ? "Tap to choose a different file" : "Tap to pick a file, or drop it here";
}
fileInput.onchange = function () { pick(fileInput.files[0]); };
drop.ondragover = function (e) { e.preventDefault(); drop.classList.add("over"); };
drop.ondragleave = function () { drop.classList.remove("over"); };
drop.ondrop = function (e) {
  e.preventDefault();
  drop.classList.remove("over");
  if (e.dataTransfer && e.dataTransfer.files.length) pick(e.dataTransfer.files[0]);
};

byId("upload").onclick = function () {
  if (!chosen) { show("Choose a file first.", "err"); return; }
  show("Sending…");
  busy(true);
  var h = headers();
  h["Content-Type"] = "application/octet-stream";
  fetch("/upload", { method: "POST", headers: h, body: chosen })
    .then(finish, offline)
    .then(function () { busy(false); });
};

byId("download").onclick = function () {
  show("Downloading…");
  busy(true);
  fetch("/download", { headers: headers() }).then(function (r) {
    if (!r.ok) return finish(r);
    var match = /filename="([^"]+)"/.exec(r.headers.get("Content-Disposition") || "");
    return r.blob().then(function (blob) {
      var link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = match ? match[1] : "camgrid-backup.json";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setTimeout(function () { URL.revokeObjectURL(link.href); }, 10000);
      show("Downloaded " + link.download + ".", "ok");
    });
  }).catch(offline).then(function () { busy(false); });
};
</script>
</body>
</html>
""".trimStart()
}
