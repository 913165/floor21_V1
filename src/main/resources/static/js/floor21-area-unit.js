(function (global) {
  var SQFT_PER_SQM = 10.763910416709722;
  var AREA_UNIT_STORAGE_KEY = "floor21.areaUnit.prefs";

  function loadUnitPreferences() {
    try {
      var raw = localStorage.getItem(AREA_UNIT_STORAGE_KEY);
      if (!raw) {
        return {};
      }
      var parsed = JSON.parse(raw);
      return parsed && typeof parsed === "object" ? parsed : {};
    } catch (e) {
      return {};
    }
  }

  function saveUnitPreference(pairId, unit) {
    if (!pairId) {
      return;
    }
    try {
      var prefs = loadUnitPreferences();
      prefs[pairId] = unit === "sqm" ? "sqm" : "sqft";
      localStorage.setItem(AREA_UNIT_STORAGE_KEY, JSON.stringify(prefs));
    } catch (e) {
      // ignore quota / private browsing
    }
  }

  function loadUnitPreference(pairId) {
    if (!pairId) {
      return null;
    }
    var prefs = loadUnitPreferences();
    if (!Object.prototype.hasOwnProperty.call(prefs, pairId)) {
      return null;
    }
    return prefs[pairId] === "sqm" ? "sqm" : "sqft";
  }

  function applyUnitPreference(pairId, els) {
    var parts = els || pairElements(pairId);
    if (!parts.unit) {
      return;
    }
    var unit = loadUnitPreference(pairId);
    if (unit == null) {
      return;
    }
    setUnitOnControl(parts.unit, unit);
    updateInputStep(parts.input, unit);
  }

  function persistUnitPreferenceFromControl(pairId) {
    var els = pairElements(pairId);
    if (!els.unit) {
      return;
    }
    saveUnitPreference(pairId, readUnitFromControl(els.unit));
  }

  function sqftToSqmNumber(sqft) {
    var num = Number(sqft);
    if (isNaN(num)) {
      return null;
    }
    return Math.round((num / SQFT_PER_SQM) * 100) / 100;
  }

  function sqmToSqftNumber(sqm) {
    var num = Number(sqm);
    if (isNaN(num)) {
      return null;
    }
    return Math.round(num * SQFT_PER_SQM * 100) / 100;
  }

  function formatSqft(sqft) {
    var num = Number(sqft);
    if (isNaN(num)) {
      return "";
    }
    return String(Math.round(num * 100) / 100);
  }

  function formatSqmFromSqft(sqft) {
    var sqm = sqftToSqmNumber(sqft);
    return sqm == null ? "" : sqm.toFixed(2);
  }

  function formatDualDisplay(sqftValue) {
    if (sqftValue == null || sqftValue === "") {
      return "—";
    }
    var num = Number(sqftValue);
    if (isNaN(num) || num <= 0) {
      return "—";
    }
    return formatSqft(num) + " sq ft · " + formatSqmFromSqft(num) + " sq m";
  }

  function pairElements(pairId) {
    return {
      field: document.querySelector('.floor21-area-field[data-area-pair="' + pairId + '"]'),
      display:
        document.querySelector('[data-area-display-for="' + pairId + '"]') ||
        document.getElementById(pairId + "-display"),
      input: document.getElementById(pairId),
      unit: document.getElementById(pairId + "-unit"),
    };
  }

  function readUnitFromControl(control) {
    if (!control) {
      return "sqft";
    }
    var active = control.querySelector(".floor21-area-unit-toggle__btn.is-active");
    if (active) {
      return active.getAttribute("data-area-unit-value") === "sqm" ? "sqm" : "sqft";
    }
    return control.getAttribute("data-area-unit-value") === "sqm" ? "sqm" : "sqft";
  }

  function setUnitOnControl(control, unit) {
    if (!control) {
      return;
    }
    var normalized = unit === "sqm" ? "sqm" : "sqft";
    control.setAttribute("data-area-unit-value", normalized);
    control.querySelectorAll(".floor21-area-unit-toggle__btn").forEach(function (btn) {
      var btnUnit = btn.getAttribute("data-area-unit-value");
      var isActive = btnUnit === normalized;
      btn.classList.toggle("is-active", isActive);
      btn.setAttribute("aria-pressed", isActive ? "true" : "false");
    });
  }

  function formatValueForUnit(sqftValue, unit) {
    if (sqftValue == null || sqftValue === "" || isNaN(Number(sqftValue))) {
      return "";
    }
    return unit === "sqm" ? formatSqmFromSqft(sqftValue) : formatSqft(sqftValue);
  }

  function readSqftFromInputValue(raw, unit) {
    if (raw === "" || raw == null) {
      return null;
    }
    var num = Number(String(raw).trim());
    if (isNaN(num)) {
      return null;
    }
    if (unit === "sqm") {
      return sqmToSqftNumber(num);
    }
    return num;
  }

  function updateInputStep(input, unit) {
    if (!input) {
      return;
    }
    input.step = unit === "sqm" ? "0.01" : "1";
  }

  function updateDisplayForPair(pairId, sqftValue) {
    var els = pairElements(pairId);
    if (!els.display) {
      return;
    }
    var hasValue = sqftValue != null && sqftValue !== "" && !isNaN(Number(sqftValue));
    els.display.textContent = hasValue ? formatDualDisplay(sqftValue) : "—";
    if (hasValue) {
      els.display.setAttribute("data-sqft-value", String(sqftValue));
    } else {
      els.display.removeAttribute("data-sqft-value");
    }
  }

  function setPairFromSqft(pairId, sqftValue, options) {
    var opts = options || {};
    var els = pairElements(pairId);
    applyUnitPreference(pairId, els);
    var hasValue = sqftValue != null && sqftValue !== "" && !isNaN(Number(sqftValue));
    var unit = readUnitFromControl(els.unit);
    if (els.display && opts.updateDisplay !== false) {
      updateDisplayForPair(pairId, hasValue ? sqftValue : null);
    }
    if (els.input && opts.updateInputs !== false) {
      els.input.value = hasValue ? formatValueForUnit(sqftValue, unit) : "";
      updateInputStep(els.input, unit);
    }
  }

  function readSqftFromPair(pairId) {
    var els = pairElements(pairId);
    if (!els.input) {
      return null;
    }
    return readSqftFromInputValue(els.input.value.trim(), readUnitFromControl(els.unit));
  }

  function syncDisplayFromInput(pairId) {
    updateDisplayForPair(pairId, readSqftFromPair(pairId));
  }

  function convertPairInputUnit(pairId, nextUnit) {
    var els = pairElements(pairId);
    if (!els.input || !els.unit) {
      return;
    }
    var prevUnit = readUnitFromControl(els.unit);
    if (prevUnit === nextUnit) {
      return;
    }
    var sqft = readSqftFromInputValue(els.input.value.trim(), prevUnit);
    setUnitOnControl(els.unit, nextUnit);
    saveUnitPreference(pairId, nextUnit);
    updateInputStep(els.input, nextUnit);
    if (sqft != null) {
      els.input.value = formatValueForUnit(sqft, nextUnit);
    }
    updateDisplayForPair(pairId, sqft);
  }

  function bindAreaToggleDelegation() {
    if (global.__f21AreaToggleDelegated) {
      return;
    }
    global.__f21AreaToggleDelegated = true;
    document.addEventListener("click", function (event) {
      var btn = event.target.closest(".floor21-area-field .floor21-area-unit-toggle__btn");
      if (!btn) {
        return;
      }
      var control = btn.closest(".floor21-area-unit-toggle");
      var field = control ? control.closest(".floor21-area-field[data-area-pair]") : null;
      if (!control || !field) {
        return;
      }
      event.preventDefault();
      var nextUnit = btn.getAttribute("data-area-unit-value") === "sqm" ? "sqm" : "sqft";
      if (readUnitFromControl(control) === nextUnit) {
        return;
      }
      var pairId = field.getAttribute("data-area-pair");
      convertPairInputUnit(pairId, nextUnit);
      var input = document.getElementById(pairId);
      if (input && typeof input.focus === "function") {
        window.setTimeout(function () {
          input.focus();
        }, 0);
      }
    });
  }

  function bindAreaFields(root) {
    bindAreaToggleDelegation();
    var scope = root && root.querySelectorAll ? root : document;
    scope.querySelectorAll("[data-area-input]").forEach(function (input) {
      if (input.dataset.areaFieldBound === "1") {
        return;
      }
      input.dataset.areaFieldBound = "1";
      var pairId = input.getAttribute("data-area-pair");
      var els = pairElements(pairId);
      applyUnitPreference(pairId, els);
      updateInputStep(input, readUnitFromControl(els.unit));
      input.addEventListener("input", function () {
        syncDisplayFromInput(pairId);
      });
    });
  }

  bindAreaToggleDelegation();

  global.Floor21AreaUnit = {
    SQFT_PER_SQM: SQFT_PER_SQM,
    formatDualDisplay: formatDualDisplay,
    setPairFromSqft: setPairFromSqft,
    readSqftFromPair: readSqftFromPair,
    bindDualAreaInputs: bindAreaFields,
    bindAreaFields: bindAreaFields,
    readUnitFromControl: readUnitFromControl,
    setUnitOnControl: setUnitOnControl,
    loadUnitPreference: loadUnitPreference,
    saveUnitPreference: saveUnitPreference,
    applyUnitPreference: applyUnitPreference,
    persistUnitPreferenceFromControl: persistUnitPreferenceFromControl,
    sqmToSqftNumber: sqmToSqftNumber,
    sqftToSqmNumber: sqftToSqmNumber,
  };
})(window);
