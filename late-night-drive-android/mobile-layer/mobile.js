// Mobile layer for Late Night Drive (Unity WebGL).
// The game reads only the mouse ("mouse = your hand"), so touches did nothing:
// buttons like Play Again / Menu never got a click. Here every finger on the
// game becomes the mouse: touch = move the hand there, then hold the button;
// drag = move the hand while holding; lift = release. A BRAKE button sends Space.
(function () {
  "use strict";
  var canvas = document.getElementById("unity-canvas");

  // ---------------------------------------------------------------- mouse
  var active = null;          // touch identifier we are following
  var lastX = 0, lastY = 0, down = false, pendingDown = 0, pendingUpAfterDown = false;

  function mouse(type, x, y, buttons, mx, my) {
    var ev = new MouseEvent(type, {
      bubbles: true, cancelable: true, view: window, composed: true,
      clientX: x, clientY: y, screenX: x, screenY: y,
      movementX: mx || 0, movementY: my || 0,
      button: 0, buttons: buttons
    });
    canvas.dispatchEvent(ev);
  }

  function isUi(t) { return t && t.closest && t.closest(".lnd-ui, #lnd-start"); }

  function onStart(e) {
    if (isUi(e.target)) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    if (active !== null) return;
    var t = e.changedTouches[0];
    active = t.identifier;
    var dx = t.clientX - lastX, dy = t.clientY - lastY;
    lastX = t.clientX; lastY = t.clientY;
    // Move the hand onto the spot first, give the game a couple of frames to
    // see it hovering there, then press.
    mouse("mousemove", lastX, lastY, 0, dx, dy);
    pendingUpAfterDown = false;
    pendingDown = setTimeout(function () {
      pendingDown = 0;
      down = true;
      mouse("mousedown", lastX, lastY, 1, 0, 0);
      if (pendingUpAfterDown) setTimeout(release, 80);
    }, 50);
  }

  function find(list) {
    for (var i = 0; i < list.length; i++) if (list[i].identifier === active) return list[i];
    return null;
  }

  function onMove(e) {
    if (isUi(e.target) && active === null) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    var t = find(e.changedTouches);
    if (!t) return;
    var dx = t.clientX - lastX, dy = t.clientY - lastY;
    lastX = t.clientX; lastY = t.clientY;
    mouse("mousemove", lastX, lastY, down || pendingDown ? 1 : 0, dx, dy);
  }

  function release() {
    if (!down) return;
    down = false;
    mouse("mouseup", lastX, lastY, 0, 0, 0);
  }

  function onEnd(e) {
    if (isUi(e.target) && active === null) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    var t = find(e.changedTouches);
    if (!t) return;
    active = null;
    lastX = t.clientX; lastY = t.clientY;
    if (pendingDown) pendingUpAfterDown = true; // quick tap: press, then release
    else release();
  }

  var opt = { capture: true, passive: false };
  window.addEventListener("touchstart", onStart, opt);
  window.addEventListener("touchmove", onMove, opt);
  window.addEventListener("touchend", onEnd, opt);
  window.addEventListener("touchcancel", onEnd, opt);
  // No long-press menu, no text selection, no double-tap zoom.
  window.addEventListener("contextmenu", function (e) { e.preventDefault(); }, true);

  // The game may ask to lock the pointer; there is no pointer on a phone.
  try { Element.prototype.requestPointerLock = function () {}; } catch (e) {}

  // ---------------------------------------------------------------- keys
  var KEYS = {
    Space:  { key: " ", code: "Space", keyCode: 32 },
    Escape: { key: "Escape", code: "Escape", keyCode: 27 }
  };
  function key(type, name) {
    var k = KEYS[name];
    var ev = new KeyboardEvent(type, { key: k.key, code: k.code, bubbles: true, cancelable: true });
    try {
      Object.defineProperty(ev, "keyCode", { get: function () { return k.keyCode; } });
      Object.defineProperty(ev, "which", { get: function () { return k.keyCode; } });
      Object.defineProperty(ev, "charCode", { get: function () { return name === "Space" ? 32 : 0; } });
    } catch (e) {}
    (document.activeElement && document.activeElement !== document.body ? document.activeElement : canvas)
      .dispatchEvent(ev);
  }
  window.__lndKey = key;

  // ---------------------------------------------------------------- UI
  var css = document.createElement("style");
  css.textContent =
    "html,body{touch-action:none;-webkit-user-select:none;user-select:none;-webkit-touch-callout:none;-webkit-tap-highlight-color:transparent}" +
    ".lnd-ui{position:fixed;z-index:10;display:flex;align-items:center;justify-content:center;" +
    "font:700 13px/1 ui-monospace,Menlo,Consolas,monospace;letter-spacing:.12em;color:#ece4d2;" +
    "background:rgba(20,18,16,.38);border:2px solid rgba(236,228,210,.45);border-radius:50%;touch-action:none}" +
    ".lnd-ui.on{background:rgba(229,57,53,.55);border-color:#ece4d2}" +
    "#lnd-brake{width:92px;height:92px;left:max(18px,env(safe-area-inset-left));bottom:18px}" +
    "#lnd-toast{position:fixed;z-index:11;left:50%;top:16px;transform:translateX(-50%);padding:8px 14px;border-radius:8px;" +
    "background:rgba(0,0,0,.75);color:#ece4d2;font:600 13px/1.2 ui-monospace,Menlo,monospace;display:none;pointer-events:none}";
  document.head.appendChild(css);

  var brake = document.createElement("div");
  brake.id = "lnd-brake";
  brake.className = "lnd-ui";
  brake.textContent = "FREN";
  brake.style.display = "none";
  document.body.appendChild(brake);

  var braking = false;
  function brakeOn(e) { e.preventDefault(); e.stopPropagation(); if (braking) return; braking = true; brake.classList.add("on"); key("keydown", "Space"); }
  function brakeOff(e) { e.preventDefault(); e.stopPropagation(); if (!braking) return; braking = false; brake.classList.remove("on"); key("keyup", "Space"); }
  brake.addEventListener("touchstart", brakeOn, { passive: false });
  brake.addEventListener("touchend", brakeOff, { passive: false });
  brake.addEventListener("touchcancel", brakeOff, { passive: false });
  brake.addEventListener("mousedown", brakeOn);
  brake.addEventListener("mouseup", brakeOff);
  brake.addEventListener("mouseleave", brakeOff);

  var toast = document.createElement("div");
  toast.id = "lnd-toast";
  document.body.appendChild(toast);
  var toastTimer = 0;
  function showToast(msg) {
    toast.textContent = msg;
    toast.style.display = "block";
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toast.style.display = "none"; }, 1600);
  }

  // Called by the game page once Unity is running.
  window.__lndReady = function () { brake.style.display = "flex"; };

  // Android Back: Escape for the game; press twice quickly to leave the app.
  var lastBack = 0;
  window.__androidBack = function () {
    var now = Date.now();
    if (now - lastBack < 1600) return false;
    lastBack = now;
    key("keydown", "Escape");
    setTimeout(function () { key("keyup", "Escape"); }, 60);
    showToast("Çıkmak için tekrar GERİ'ye bas");
    return true;
  };
})();
