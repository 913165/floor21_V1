/**
 * Single-panel resize: drag right / bottom / corner edges; inner content scales to fit.
 */
(function () {
  "use strict";

  var drag = null;

  function modeFromHandle(handle) {
    var mode = handle.getAttribute("data-resize-mode");
    if (mode === "right" || mode === "bottom" || mode === "corner") {
      return mode;
    }
    if (handle.classList.contains("floor21-resizable-panel__handle--edge-right")) {
      return "right";
    }
    if (handle.classList.contains("floor21-resizable-panel__handle--edge-bottom")) {
      return "bottom";
    }
    return "corner";
  }

  function panelOptions(panel) {
    return panel && panel._floor21PanelResizeOptions ? panel._floor21PanelResizeOptions : {};
  }

  function isEnabled() {
    if (typeof window.floor21PanelResizeIsEnabled === "function") {
      return window.floor21PanelResizeIsEnabled();
    }
    return true;
  }

  function loadSize(key, natural, defaultScale) {
    if (!key) {
      return null;
    }
    try {
      var raw = localStorage.getItem(key);
      if (raw) {
        var parsed = JSON.parse(raw);
        if (parsed && parsed.w > 0 && parsed.h > 0) {
          return { w: parsed.w, h: parsed.h };
        }
      }
    } catch (e) {
      /* ignore */
    }
    var scale = defaultScale > 0 ? defaultScale : 1;
    return {
      w: Math.ceil(natural.w * scale),
      h: Math.ceil(natural.h * scale),
    };
  }

  function saveSize(key, w, h) {
    if (!key) {
      return;
    }
    try {
      localStorage.setItem(key, JSON.stringify({ w: Math.round(w), h: Math.round(h) }));
    } catch (e) {
      /* ignore */
    }
  }

  function wrapLayout(panel, layoutSelector) {
    if (!panel || panel.querySelector(".floor21-resizable-panel__scaler")) {
      return;
    }
    var selectors = (layoutSelector || ".flat-parking-section__layout, .flat-ground-floor-section__layout").split(
      ","
    );
    var layout = null;
    var i;
    for (i = 0; i < selectors.length; i++) {
      layout = panel.querySelector(selectors[i].trim());
      if (layout) {
        break;
      }
    }
    if (!layout) {
      return;
    }
    var scaler = document.createElement("div");
    scaler.className = "floor21-resizable-panel__scaler";
    layout.parentNode.insertBefore(scaler, layout);
    scaler.appendChild(layout);
    panel.classList.add("floor21-resizable-panel");
  }

  function ensureHandles(panel) {
    if (!panel || panel.querySelector(".floor21-resizable-panel__handle--corner")) {
      return;
    }
    var right = document.createElement("div");
    right.className =
      "floor21-resizable-panel__handle floor21-resizable-panel__handle--edge-right";
    right.setAttribute("data-resize-mode", "right");
    right.title = "Drag to resize width";
    panel.appendChild(right);

    var bottom = document.createElement("div");
    bottom.className =
      "floor21-resizable-panel__handle floor21-resizable-panel__handle--edge-bottom";
    bottom.setAttribute("data-resize-mode", "bottom");
    bottom.title = "Drag to resize height";
    panel.appendChild(bottom);

    var corner = document.createElement("div");
    corner.className =
      "floor21-resizable-panel__handle floor21-resizable-panel__handle--corner";
    corner.setAttribute("data-resize-mode", "corner");
    corner.title = "Drag to resize panel";
    panel.appendChild(corner);
  }

  function measureNatural(panel) {
    var scaler = panel.querySelector(".floor21-resizable-panel__scaler");
    if (!scaler) {
      return null;
    }
    var opts = panelOptions(panel);
    if (typeof opts.resetContent === "function") {
      opts.resetContent(panel);
    }
    scaler.style.transform = "none";
    scaler.style.width = "";
    scaler.style.height = "";
    panel.classList.add("floor21-resizable-panel--measuring");
    panel.style.width = "auto";
    panel.style.height = "auto";
    void scaler.offsetWidth;
    var measured = {
      w: Math.max(scaler.offsetWidth, 1),
      h: Math.max(scaler.offsetHeight, 1),
    };
    panel.classList.remove("floor21-resizable-panel--measuring");
    return measured;
  }

  function applyContentScale(panel) {
    var scaler = panel.querySelector(".floor21-resizable-panel__scaler");
    if (!scaler) {
      return;
    }
    scaler.style.transform = "none";
    scaler.style.width = "100%";
    scaler.style.height = "100%";
  }

  function setPanelSize(panel, w, h) {
    var minW = panel._floor21PanelMinW || 280;
    var minH = panel._floor21PanelMinH || 120;
    panel.style.width = Math.max(minW, Math.round(w)) + "px";
    panel.style.height = Math.max(minH, Math.round(h)) + "px";
    applyContentScale(panel);
  }

  function resolveStorageKey(panel, opts) {
    if (!opts.storageKey) {
      return "";
    }
    if (typeof opts.storageKey === "function") {
      return opts.storageKey(panel) || "";
    }
    return opts.storageKey;
  }

  function resolveDefaultScale(panel, opts) {
    if (typeof opts.defaultScale === "function") {
      return opts.defaultScale(panel);
    }
    if (typeof opts.defaultScale === "number") {
      return opts.defaultScale;
    }
    return 1;
  }

  function remeasure(panel, options) {
    if (!panel) {
      return;
    }
    if (options) {
      panel._floor21PanelResizeOptions = options;
    }
    var opts = panelOptions(panel);
    wrapLayout(panel, opts.layoutSelector);
    ensureHandles(panel);

    var natural = measureNatural(panel);
    if (!natural) {
      return;
    }
    panel._floor21PanelNatural = natural;

    var key = resolveStorageKey(panel, opts);
    var defaultScale = resolveDefaultScale(panel, opts);
    var stored = loadSize(key, natural, defaultScale);
    var minW = opts.minWidth || 280;
    var minH = opts.minHeight || 120;
    panel._floor21PanelStorageKey = key;
    panel._floor21PanelMinW = minW;
    panel._floor21PanelMinH = minH;

    if (stored) {
      setPanelSize(panel, stored.w, stored.h);
    } else {
      setPanelSize(panel, natural.w * defaultScale, natural.h * defaultScale);
    }
  }

  function init(panel, options) {
    if (!panel) {
      return;
    }
    options = options || {};
    panel._floor21PanelResizeOptions = options;
    wrapLayout(panel, options.layoutSelector);
    ensureHandles(panel);
    if (panel.dataset.floor21PanelResizeInit === "true") {
      remeasure(panel);
      return;
    }
    panel.dataset.floor21PanelResizeInit = "true";
    remeasure(panel);
  }

  function uniformScale(panel) {
    var natural = panel._floor21PanelNatural;
    if (!natural) {
      return 1;
    }
    return panel.offsetWidth / natural.w;
  }

  function bindGlobal() {
    if (window.__floor21PanelResizeBound) {
      return;
    }
    window.__floor21PanelResizeBound = true;

    document.addEventListener("pointerdown", function (e) {
      var handle = e.target.closest(".floor21-resizable-panel__handle");
      if (!handle || !isEnabled()) {
        return;
      }
      var panel = handle.closest(".floor21-resizable-panel");
      if (!panel) {
        return;
      }
      e.preventDefault();
      drag = {
        panel: panel,
        mode: modeFromHandle(handle),
        startX: e.clientX,
        startY: e.clientY,
        startW: panel.offsetWidth,
        startH: panel.offsetHeight,
        pointerId: e.pointerId,
      };
      panel.classList.add("floor21-resizable-panel--resizing");
      if (handle.setPointerCapture) {
        handle.setPointerCapture(e.pointerId);
      }
    });

    document.addEventListener("pointermove", function (e) {
      if (!drag || e.pointerId !== drag.pointerId) {
        return;
      }
      var dx = e.clientX - drag.startX;
      var dy = e.clientY - drag.startY;
      var panel = drag.panel;
      var minW = panel._floor21PanelMinW || 280;
      var minH = panel._floor21PanelMinH || 120;
      var w = drag.startW;
      var h = drag.startH;

      if (drag.mode === "right" || drag.mode === "corner") {
        w = Math.max(minW, drag.startW + dx);
      }
      if (drag.mode === "bottom" || drag.mode === "corner") {
        h = Math.max(minH, drag.startH + dy);
      }
      setPanelSize(panel, w, h);
    });

    function endDrag(e) {
      if (!drag) {
        return;
      }
      if (e.pointerId != null && e.pointerId !== drag.pointerId) {
        return;
      }
      var panel = drag.panel;
      var opts = panelOptions(panel);
      panel.classList.remove("floor21-resizable-panel--resizing");
      saveSize(panel._floor21PanelStorageKey, panel.offsetWidth, panel.offsetHeight);
      drag = null;
      if (typeof opts.onResizeEnd === "function") {
        opts.onResizeEnd(panel);
      }
    }

    document.addEventListener("pointerup", endDrag);
    document.addEventListener("pointercancel", endDrag);
  }

  window.floor21PanelResize = {
    init: init,
    remeasure: remeasure,
    bind: bindGlobal,
    uniformScale: uniformScale,
  };
})();
