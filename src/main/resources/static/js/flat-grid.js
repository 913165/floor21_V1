(function () {
  function appRoot() {
    var r = document.body.getAttribute("data-app-root") || "";
    return r.replace(/\/$/, "");
  }

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (!token || !header) return {};
    var h = {};
    h[header.getAttribute("content")] = token.getAttribute("content");
    return h;
  }

  var selectedFlatId = null;
  var selectedParkingSection = false;
  var selectedParkingFloorNumber = null;
  var selectedParkingSlot = false;
  var selectedParkingSlotElement = null;
  var parkingConfigFloorNumber = null;
  var parkingLinkParkingFlatId = null;
  var parkingLinkFloorNumber = null;
  var parkingLinkSlotNumber = null;
  var parkingResidentialCache = null;
  var parkingSlotsCache = null;
  var unitTypeDefaultsCache = null;
  var columnTypeDefaultsCache = null;
  var DEFAULT_PARKING_CAR_SIZE_PERCENT = 180;

  /** Bootstrap backdrop is on body; modals must be too or backdrop blocks clicks. */
  function mountModalsOnBody() {
    [
      "flat-details-modal",
      "floor-plan-modal",
      "flat-add-modal",
      "unit-type-defaults-modal",
      "column-type-defaults-modal",
      "parking-config-modal",
      "parking-link-modal",
    ].forEach(function (id) {
      var el = document.getElementById(id);
      if (el && el.parentElement !== document.body) {
        document.body.appendChild(el);
      }
    });
  }

  function isPlatformAdminEdit() {
    var grid = document.getElementById("flat-grid");
    return grid && grid.getAttribute("data-platform-admin-edit") === "true";
  }

  function amenityTypeCodes() {
    var grid = document.getElementById("flat-grid");
    if (!grid) return [];
    return (grid.getAttribute("data-amenity-types") || "")
      .split(",")
      .map(function (s) {
        return s.trim().toUpperCase();
      })
      .filter(Boolean);
  }

  function isAmenityType(type) {
    if (!type) return false;
    return amenityTypeCodes().indexOf(String(type).trim().toUpperCase()) >= 0;
  }

  function isNonBookableUnit(cardEl) {
    if (!cardEl) return false;
    if (cardEl.dataset.parking === "true") return true;
    if (cardEl.dataset.amenity === "true") return true;
    if (cardEl.dataset.duplexSecondary === "true") return true;
    if (cardEl.dataset.mergeSecondary === "true") return true;
    return isAmenityType(cardEl.dataset.type);
  }

  function canOpenFlatPanel(cardEl) {
    if (!cardEl) return false;
    if (cardEl.dataset.parking === "true") return false;
    if (isPlatformAdminEdit()) return true;
    return isFlatBookable(cardEl) && !isNonBookableUnit(cardEl);
  }

  function clearParkingSectionHighlight() {
    document.querySelectorAll(".flat-parking-section--selected").forEach(function (el) {
      el.classList.remove("flat-parking-section--selected");
    });
  }

  function clearParkingSlotHighlight() {
    document.querySelectorAll(".parking-plan__slot--selected").forEach(function (el) {
      el.classList.remove("parking-plan__slot--selected");
    });
    selectedParkingSlotElement = null;
  }

  function setParkingExtraAreaFieldsVisible(visible) {
    document.querySelectorAll(".flat-details-extra-area-fields").forEach(function (el) {
      el.classList.toggle("d-none", !visible);
    });
  }

  function isResidentialFlatForParkingLinks(cardEl) {
    if (!cardEl) return false;
    if (cardEl.dataset.duplexPrimary === "true" || cardEl.dataset.mergePrimary === "true") {
      return true;
    }
    return !isNonBookableUnit(cardEl);
  }

  function syncParkingDetailsPanelMode() {
    var section = selectedParkingSection;
    var slot = selectedParkingSlot;
    var modal = document.getElementById("flat-details-modal");
    if (modal) {
      modal.classList.toggle("modal--parking-section", section);
      modal.classList.toggle("modal--parking-slot", slot);
    }
    var note = document.getElementById("panel-parking-note");
    var actions = document.getElementById("panel-booking-actions");
    var parkingLinks = document.getElementById("panel-parking-links");
    var parkingSlotActions = document.getElementById("panel-parking-slot-actions");
    var flatLayoutActions = document.getElementById("panel-flat-layout-actions");
    var adminPanel = document.getElementById("flat-admin-panel");
    var saveRow = document.getElementById("admin-save-row");
    var saveBtn = document.getElementById("admin-save-btn");
    var applyFloor = document.getElementById("admin-apply-floor-btn");
    var saveHint = document.getElementById("admin-save-hint");
    var parkingMode = section || slot;
    if (note) note.classList.toggle("d-none", !parkingMode);
    if (actions) actions.classList.toggle("d-none", parkingMode);
    if (parkingLinks) parkingLinks.classList.toggle("d-none", parkingMode);
    if (parkingSlotActions) parkingSlotActions.classList.toggle("d-none", !slot);
    if (flatLayoutActions) flatLayoutActions.classList.toggle("d-none", parkingMode);
    if (adminPanel) adminPanel.classList.toggle("d-none", parkingMode);
    if (saveRow) {
      saveRow.classList.toggle("d-none", !(section || slot) || !isPlatformAdminEdit());
    }
    if (saveBtn) {
      saveBtn.classList.toggle("d-none", section);
      saveBtn.textContent = slot
        ? "Save slot area / price"
        : section
          ? "Save unit type / area / price"
          : "Save unit type / area / price";
    }
    if (applyFloor) applyFloor.classList.toggle("d-none", !(section || slot));
    if (saveHint) {
      if (slot) {
        saveHint.textContent =
          "Set a different size per slot here. Configure on the floor sets the default for newly added slots.";
      } else if (section) {
        saveHint.textContent =
          "Platform admin only. Apply to whole floor sets the same type/area on every slot.";
      }
    }
    setParkingExtraAreaFieldsVisible(!parkingMode);
    ["panel-column-number-col", "panel-column-type-col"].forEach(function (id) {
      var col = document.getElementById(id);
      if (col) col.classList.toggle("d-none", parkingMode);
    });
    var areaLabel = document.getElementById("panel-super-builder-area-label");
    if (areaLabel) {
      if (parkingMode) {
        areaLabel.textContent = "Parking slot area (" + areaUnitSuffix() + ")";
      } else {
        updateAreaUnitLabels();
      }
    }
    if (section || slot) {
      setAdminEditModeVisible(isPlatformAdminEdit());
    }
    var bhk = document.getElementById("admin-bhk");
    if (bhk) {
      if (slot) {
        bhk.value = "PKG";
        bhk.disabled = true;
      } else if (!section) {
        bhk.disabled = false;
      }
    }
  }

  function setParkingSectionMode(on) {
    selectedParkingSection = on;
    if (on) {
      selectedParkingSlot = false;
      clearParkingSlotHighlight();
    }
    syncParkingDetailsPanelMode();
  }

  function setParkingSlotMode(on) {
    selectedParkingSlot = on;
    if (on) {
      selectedParkingSection = false;
      clearParkingSectionHighlight();
    } else {
      clearParkingSlotHighlight();
    }
    syncParkingDetailsPanelMode();
  }

  function syncParkingSectionAdminFields(sectionEl) {
    if (!sectionEl) return;
    var superBuilder = document.getElementById("admin-super-builder-area");
    var carpet = document.getElementById("admin-carpet-area");
    var balcony = document.getElementById("admin-balcony-area");
    var price = document.getElementById("admin-price");
    var bhk = document.getElementById("admin-bhk");
    if (superBuilder) superBuilder.value = formatAreaForDisplay(sectionEl.dataset.area);
    if (carpet) carpet.value = "";
    if (balcony) balcony.value = "";
    if (price) price.value = sectionEl.dataset.price || "";
    if (bhk) bhk.value = "PKG";
    showAdminError("");
  }

  function applyCardTypeClasses(cardEl, opts) {
    if (!cardEl || !opts) return;
    var parking = !!opts.parking;
    var amenity = !!opts.amenity;
    var duplexSecondary = !!opts.duplexSecondary;
    var duplexPrimary = !!opts.duplexPrimary;
    var mergeSecondary = !!opts.mergeSecondary;
    var mergePrimary = !!opts.mergePrimary;
    cardEl.dataset.parking = parking ? "true" : "false";
    cardEl.dataset.amenity = amenity ? "true" : "false";
    cardEl.dataset.duplexSecondary = duplexSecondary ? "true" : "false";
    cardEl.dataset.duplexPrimary = duplexPrimary ? "true" : "false";
    cardEl.dataset.mergeSecondary = mergeSecondary ? "true" : "false";
    cardEl.dataset.mergePrimary = mergePrimary ? "true" : "false";
    cardEl.classList.remove(
      "flat-available",
      "flat-booked",
      "flat-hold",
      "flat-deactivated",
      "flat-parking",
      "flat-amenity",
      "flat-duplex",
      "flat-duplex-part",
      "flat-duplex-primary",
      "flat-merge",
      "flat-merge-part",
      "flat-merge-primary"
    );
    if (parking) {
      cardEl.classList.add("flat-parking");
    } else if (amenity) {
      cardEl.classList.add("flat-amenity");
    } else if (duplexSecondary) {
      cardEl.classList.add("flat-duplex-part");
    } else if (mergeSecondary) {
      cardEl.classList.add("flat-merge-part");
    } else if (duplexPrimary) {
      if (opts.status === "BOOKED") cardEl.classList.add("flat-booked");
      else if (opts.status === "CANCELLED") cardEl.classList.add("flat-deactivated");
      else if (opts.status === "HOLD") cardEl.classList.add("flat-hold");
      else cardEl.classList.add("flat-duplex-primary");
      cardEl.classList.add("flat-duplex");
    } else if (mergePrimary) {
      if (opts.status === "BOOKED") cardEl.classList.add("flat-booked");
      else if (opts.status === "CANCELLED") cardEl.classList.add("flat-deactivated");
      else if (opts.status === "HOLD") cardEl.classList.add("flat-hold");
      else cardEl.classList.add("flat-merge-primary");
      cardEl.classList.add("flat-merge");
    } else if (opts.status === "AVAILABLE") {
      cardEl.classList.add("flat-available");
    } else if (opts.status === "BOOKED") {
      cardEl.classList.add("flat-booked");
    } else if (opts.status === "CANCELLED") {
      cardEl.classList.add("flat-deactivated");
    } else {
      cardEl.classList.add("flat-hold");
    }
    syncFlatCardStatusLabel(cardEl, opts.status);
  }

  function statusLabelForFlat(status) {
    if (status === "BOOKED") return "BOOKED";
    if (status === "HOLD") return "ON HOLD";
    if (status === "CANCELLED") return "DEACTIVATED";
    return "AVAILABLE";
  }

  function syncFlatCardStatusLabel(cardEl, status) {
    if (!cardEl) return;
    var label = cardEl.querySelector(".flat-card-status__label");
    if (!label) return;
    label.textContent = statusLabelForFlat(status || cardEl.dataset.status);
  }

  function appendFlatCardRoof(cardEl, bhkType) {
    var roof = document.createElement("div");
    roof.className = "flat-card-roof";
    roof.setAttribute("aria-hidden", "true");
    var peak = document.createElement("div");
    peak.className = "flat-card-roof__peak";
    peak.innerHTML =
      '<div class="flat-card-roof__shape"></div>' +
      '<div class="flat-card-roof__windows"><span></span><span></span></div>';
    roof.appendChild(peak);
    var bricks = document.createElement("div");
    bricks.className = "flat-card-roof__bricks";
    if (bhkType && /^[34]/.test(String(bhkType))) {
      bricks.classList.add("flat-card-roof__bricks--three");
    }
    bricks.innerHTML = "<span></span><span></span><span class=\"flat-card-roof__brick-extra\"></span>";
    roof.appendChild(bricks);
    cardEl.appendChild(roof);
  }

  function appendFlatCardStatus(cardEl, status) {
    var statusEl = document.createElement("div");
    statusEl.className = "flat-card-status";
    var label = document.createElement("span");
    label.className = "flat-card-status__label";
    label.textContent = statusLabelForFlat(status);
    statusEl.appendChild(label);
    cardEl.appendChild(statusEl);
  }

  function buildFlatCardContent(flat) {
    var body = document.createElement("div");
    body.className = "flat-card-body";
    var inner = document.createElement("div");
    inner.className = "flat-card-inner";
    var num = document.createElement("span");
    num.className = "flat-number";
    num.textContent = flat.flatNumber || "";
    inner.appendChild(num);
    var typeSpan = document.createElement("span");
    typeSpan.className = "flat-type";
    typeSpan.textContent = flat.gridTypeLabel || flat.bhkType || "";
    inner.appendChild(typeSpan);
    var owner = document.createElement("div");
    owner.className = "flat-card-owner is-blank";
    owner.innerHTML =
      '<span class="flat-owner-name"></span><span class="flat-owner-detail"></span>';
    inner.appendChild(owner);
    if (flat.bookableByCurrentUser !== false || isPlatformAdminEdit()) {
      var quick = document.createElement("button");
      quick.type = "button";
      quick.className = "flat-quick-link flat-quick-link--sr";
      quick.dataset.flatId = flat.id;
      quick.textContent = "Flat details";
      inner.appendChild(quick);
    }
    body.appendChild(inner);
    return body;
  }

  function showAdminError(message) {
    var el = document.getElementById("admin-error");
    if (!el) return;
    if (!message) {
      el.textContent = "";
      el.classList.add("d-none");
      return;
    }
    el.textContent = message;
    el.classList.remove("d-none");
  }

  var AREA_UNIT_STORAGE_KEY = "floor21.areaUnit";
  var SQFT_PER_SQM = 10.763910416709722;

  function getAreaUnit() {
    var sel = document.getElementById("panel-area-unit");
    if (sel) {
      return sel.value === "sqm" ? "sqm" : "sqft";
    }
    try {
      return localStorage.getItem(AREA_UNIT_STORAGE_KEY) === "sqm" ? "sqm" : "sqft";
    } catch (e) {
      return "sqft";
    }
  }

  function setAreaUnit(unit) {
    var normalized = unit === "sqm" ? "sqm" : "sqft";
    try {
      localStorage.setItem(AREA_UNIT_STORAGE_KEY, normalized);
    } catch (e) {
      /* ignore storage errors */
    }
    var panelSel = document.getElementById("panel-area-unit");
    if (panelSel && panelSel.value !== normalized) {
      panelSel.value = normalized;
    }
    var addSel = document.getElementById("flat-add-area-unit");
    if (addSel && addSel.value !== normalized) {
      addSel.value = normalized;
    }
    var parkingSel = document.getElementById("parking-config-area-unit");
    if (parkingSel && parkingSel.value !== normalized) {
      parkingSel.value = normalized;
    }
    updateAreaUnitLabels();
    refreshParkingSectionMetaDisplays();
  }

  function areaUnitSuffix() {
    return getAreaUnit() === "sqm" ? "sq m" : "sq ft";
  }

  function formatAreaForDisplay(sqftValue) {
    if (sqftValue == null || sqftValue === "") {
      return "";
    }
    var num = Number(sqftValue);
    if (isNaN(num)) {
      return String(sqftValue);
    }
    if (getAreaUnit() === "sqm") {
      return (num / SQFT_PER_SQM).toFixed(2);
    }
    var rounded = Math.round(num * 100) / 100;
    return String(rounded);
  }

  function parseAreaInputToSqft(inputValue) {
    if (inputValue === "" || inputValue == null) {
      return null;
    }
    var num = Number(String(inputValue).trim());
    if (isNaN(num)) {
      return null;
    }
    if (getAreaUnit() === "sqm") {
      return Math.round(num * SQFT_PER_SQM * 100) / 100;
    }
    return num;
  }

  function readAreaInputSqft(inputEl) {
    if (!inputEl) return null;
    var raw = inputEl.value.trim();
    if (raw === "") return null;
    return parseAreaInputToSqft(raw);
  }

  function convertAllAreaInputDisplays(fromUnit, toUnit) {
    if (fromUnit === toUnit) return;
    [
      "admin-super-builder-area",
      "admin-carpet-area",
      "admin-balcony-area",
      "flat-add-super-builder-area",
      "flat-add-carpet-area",
      "flat-add-balcony-area",
      "unit-type-defaults-super-builder-area",
      "unit-type-defaults-carpet-area",
      "unit-type-defaults-balcony-area",
      "parking-config-area",
    ].forEach(function (id) {
      var input = document.getElementById(id);
      if (!input) return;
      var raw = input.value.trim();
      if (raw === "") return;
      var num = Number(raw);
      if (isNaN(num)) return;
      if (fromUnit === "sqm" && toUnit === "sqft") {
        input.value = String(Math.round(num * SQFT_PER_SQM * 100) / 100);
      } else if (fromUnit === "sqft" && toUnit === "sqm") {
        input.value = (num / SQFT_PER_SQM).toFixed(2);
      }
    });
  }

  function refreshAreaPanelDisplayOnly() {
    if (selectedParkingSection && selectedParkingFloorNumber) {
      var section = document.querySelector(
        '.flat-parking-section[data-floor-number="' + selectedParkingFloorNumber + '"]'
      );
      if (section) setAreaPanelFromDataset(section);
      return;
    }
    if (selectedFlatId) {
      var card = document.getElementById("flat-" + selectedFlatId);
      if (card) setAreaPanelFromDataset(card);
    }
  }

  function syncAdminAreaInputsFromFlat(flat) {
    if (!flat) return;
    var superBuilder = document.getElementById("admin-super-builder-area");
    var carpet = document.getElementById("admin-carpet-area");
    var balcony = document.getElementById("admin-balcony-area");
    var price = document.getElementById("admin-price");
    if (superBuilder) superBuilder.value = formatAreaForDisplay(flat.areaSqft);
    if (carpet) carpet.value = formatAreaForDisplay(flat.carpetAreaSqft);
    if (balcony) balcony.value = formatAreaForDisplay(flat.balconyAreaSqft);
    if (price) price.value = flat.basePrice != null ? String(flat.basePrice) : "";
    syncColumnTypePanelFromFlat(flat);
  }

  function formatColumnTypeDisplay(typeLabel) {
    return typeLabel != null && String(typeLabel).trim() !== "" ? String(typeLabel).trim() : "—";
  }

  function syncColumnTypePanelFromCard(cardEl) {
    if (!cardEl) return;
    var show = isResidentialFlatCard(cardEl);
    ["panel-column-number-col", "panel-column-type-col"].forEach(function (id) {
      var el = document.getElementById(id);
      if (el) el.classList.toggle("d-none", !show);
    });
    var columnNumber = document.getElementById("panel-column-number");
    if (columnNumber) {
      columnNumber.textContent = show && cardEl.dataset.columnNumber ? cardEl.dataset.columnNumber : "—";
    }
    var panelColumnType = document.getElementById("panel-column-type");
    if (panelColumnType) {
      panelColumnType.textContent = show ? formatColumnTypeDisplay(cardEl.dataset.columnType) : "—";
    }
    var columnTypeInput = document.getElementById("admin-column-type");
    if (columnTypeInput) {
      columnTypeInput.value = show && cardEl.dataset.columnType ? cardEl.dataset.columnType : "";
    }
  }

  function syncColumnTypePanelFromFlat(flat) {
    var panelColumnType = document.getElementById("panel-column-type");
    if (panelColumnType) {
      panelColumnType.textContent = formatColumnTypeDisplay(flat.layoutColumnType);
    }
    var columnTypeInput = document.getElementById("admin-column-type");
    if (columnTypeInput) {
      columnTypeInput.value = flat.layoutColumnType != null ? String(flat.layoutColumnType) : "";
    }
  }

  function updateAreaUnitLabels() {
    var suffix = areaUnitSuffix();
    document.querySelectorAll("[data-area-label-base]").forEach(function (label) {
      label.textContent = label.getAttribute("data-area-label-base") + " (" + suffix + ")";
    });
    var step = getAreaUnit() === "sqm" ? "0.01" : "1";
    document.querySelectorAll("[data-area-input]").forEach(function (input) {
      input.step = step;
      var base = input.getAttribute("aria-label") || input.id || "Area";
      input.setAttribute("aria-label", base + " in " + suffix);
    });
  }

  function syncAdminAreaInputsFromDataset(cardEl) {
    if (!cardEl) return;
    var superBuilder = document.getElementById("admin-super-builder-area");
    var carpet = document.getElementById("admin-carpet-area");
    var balcony = document.getElementById("admin-balcony-area");
    if (superBuilder) superBuilder.value = formatAreaForDisplay(cardEl.dataset.area);
    if (carpet) carpet.value = formatAreaForDisplay(cardEl.dataset.carpetArea);
    if (balcony) balcony.value = formatAreaForDisplay(cardEl.dataset.balconyArea);
  }

  function refreshAreaPanelForCurrentSelection() {
    if (selectedParkingSection && selectedParkingFloorNumber) {
      var section = document.querySelector(
        '.flat-parking-section[data-floor-number="' + selectedParkingFloorNumber + '"]'
      );
      if (section) {
        setAreaPanelFromDataset(section);
        if (isPlatformAdminEdit()) {
          syncParkingSectionAdminFields(section);
        }
      }
      return;
    }
    if (selectedFlatId) {
      var card = document.getElementById("flat-" + selectedFlatId);
      if (card) {
        setAreaPanelFromDataset(card);
        if (isPlatformAdminEdit()) {
          syncAdminAreaInputsFromDataset(card);
        }
      }
    }
  }

  function initAreaUnitSelector() {
    var unit = "sqft";
    try {
      unit = localStorage.getItem(AREA_UNIT_STORAGE_KEY) === "sqm" ? "sqm" : "sqft";
    } catch (e) {
      unit = "sqft";
    }
    var panelSel = document.getElementById("panel-area-unit");
    if (panelSel) {
      panelSel.value = unit;
      panelSel.addEventListener("change", function () {
        var nextUnit = panelSel.value === "sqm" ? "sqm" : "sqft";
        var prevUnit = nextUnit === "sqm" ? "sqft" : "sqm";
        convertAllAreaInputDisplays(prevUnit, nextUnit);
        setAreaUnit(nextUnit);
        refreshAreaPanelDisplayOnly();
      });
    }
    var addSel = document.getElementById("flat-add-area-unit");
    if (addSel) {
      addSel.value = unit;
      addSel.addEventListener("change", function () {
        var nextUnit = addSel.value === "sqm" ? "sqm" : "sqft";
        var prevUnit = nextUnit === "sqm" ? "sqft" : "sqm";
        convertAllAreaInputDisplays(prevUnit, nextUnit);
        setAreaUnit(nextUnit);
        var panelSelSync = document.getElementById("panel-area-unit");
        if (panelSelSync && panelSelSync.value !== nextUnit) {
          panelSelSync.value = nextUnit;
        }
        refreshAreaPanelDisplayOnly();
      });
    }
    var parkingSel = document.getElementById("parking-config-area-unit");
    if (parkingSel) {
      parkingSel.value = unit;
      parkingSel.addEventListener("change", function () {
        var nextUnit = parkingSel.value === "sqm" ? "sqm" : "sqft";
        var prevUnit = nextUnit === "sqm" ? "sqft" : "sqm";
        convertAllAreaInputDisplays(prevUnit, nextUnit);
        setAreaUnit(nextUnit);
        refreshAreaPanelDisplayOnly();
      });
    }
    updateAreaUnitLabels();
  }

  function readParkingConfigAreaSqft() {
    var input = document.getElementById("parking-config-area");
    var unitSel = document.getElementById("parking-config-area-unit");
    if (!input) return null;
    var raw = input.value.trim();
    if (raw === "") return null;
    var num = Number(raw);
    if (isNaN(num)) return null;
    var unit = unitSel && unitSel.value === "sqm" ? "sqm" : "sqft";
    if (unit === "sqm") {
      return Math.round(num * SQFT_PER_SQM * 100) / 100;
    }
    return num;
  }

  function formatParkingAreaRange(areas) {
    if (!areas || !areas.length) return "";
    var nums = areas
      .map(function (value) {
        return Number(value);
      })
      .filter(function (n) {
        return !isNaN(n) && n > 0;
      });
    if (!nums.length) return "";
    var min = Math.min.apply(null, nums);
    var max = Math.max.apply(null, nums);
    if (min === max) {
      return formatAreaForDisplay(min) + " " + areaUnitSuffix() + "/slot";
    }
    return formatAreaForDisplay(min) + "–" + formatAreaForDisplay(max) + " " + areaUnitSuffix() + "/slot";
  }

  function collectParkingSlotAreas(floor) {
    if (!floor || !floor.flats) return [];
    return floor.flats
      .map(function (flat) {
        return flat.areaSqft;
      })
      .filter(function (value) {
        return value != null && value !== "";
      });
  }

  function parkingAreaMetaSuffix(floorOrSection) {
    var areas = [];
    if (floorOrSection && floorOrSection.flats) {
      areas = collectParkingSlotAreas(floorOrSection);
    } else if (floorOrSection && floorOrSection.dataset) {
      var section = floorOrSection;
      var root = parkingPlanRootForSection(section);
      if (root && root._parkingLayoutState && root._parkingLayoutState.slots) {
        areas = root._parkingLayoutState.slots
          .map(function (slot) {
            return slot.areaSqft;
          })
          .filter(function (value) {
            return value != null && value !== "";
          });
      }
      if (!areas.length && section.dataset.area) {
        areas = [section.dataset.area];
      }
    }
    var formatted = formatParkingAreaRange(areas);
    return formatted ? " · " + formatted : "";
  }
  function parkingSectionMetaFromSectionEl(sectionEl) {
    if (!sectionEl) return "";
    var count = sectionEl.dataset.slotCount || "0";
    var range = sectionEl.dataset.rangeLabel || "";
    var areaPart =
      sectionEl.dataset.configured === "true" ? parkingAreaMetaSuffix(sectionEl) : "";
    var carLifts = Number(sectionEl.dataset.carLiftCount != null ? sectionEl.dataset.carLiftCount : 0);
    var passengerLifts = Number(
      sectionEl.dataset.passengerLiftCount != null ? sectionEl.dataset.passengerLiftCount : 0
    );
    var gates = Number(sectionEl.dataset.gateCount != null ? sectionEl.dataset.gateCount : 0);
    var fixtureParts = [];
    if (carLifts > 0) {
      fixtureParts.push(carLifts + (carLifts === 1 ? " car lift" : " car lifts"));
    }
    if (passengerLifts > 0) {
      fixtureParts.push(
        passengerLifts + (passengerLifts === 1 ? " passenger lift" : " passenger lifts")
      );
    }
    if (gates > 0) {
      fixtureParts.push(gates + (gates === 1 ? " gate" : " gates"));
    }
    var fixtureSuffix = fixtureParts.length ? " · " + fixtureParts.join(" · ") : "";
    return (
      count +
      " slots" +
      (range ? " · " + range : "") +
      areaPart +
      fixtureSuffix
    );
  }

  function refreshParkingSectionMetaDisplays() {
    document.querySelectorAll('.flat-parking-section[data-configured="true"]').forEach(function (section) {
      var meta = section.querySelector(".flat-parking-section__meta");
      if (meta) {
        meta.textContent = parkingSectionMetaFromSectionEl(section);
      }
    });
  }

  function setAreaPanelFromDataset(el) {
    if (!el) return;
    var superBuilder = document.getElementById("panel-super-builder-area");
    var carpet = document.getElementById("panel-carpet-area");
    var balcony = document.getElementById("panel-balcony-area");
    if (superBuilder) {
      superBuilder.textContent = formatAreaForDisplay(
        getEffectiveFlatDatasetValue(el, "area", "areaSqft") || el.dataset.area
      );
    }
    if (carpet) {
      carpet.textContent = formatAreaForDisplay(
        getEffectiveFlatDatasetValue(el, "carpetArea", "carpetAreaSqft") || el.dataset.carpetArea
      );
    }
    if (balcony) {
      balcony.textContent = formatAreaForDisplay(
        getEffectiveFlatDatasetValue(el, "balconyArea", "balconyAreaSqft") || el.dataset.balconyArea
      );
    }
  }

  function setAreaPanelFromFlat(flat) {
    if (!flat) return;
    var superBuilder = document.getElementById("panel-super-builder-area");
    var carpet = document.getElementById("panel-carpet-area");
    var balcony = document.getElementById("panel-balcony-area");
    if (superBuilder) {
      superBuilder.textContent = formatAreaForDisplay(flat.areaSqft);
    }
    if (carpet) {
      carpet.textContent = formatAreaForDisplay(flat.carpetAreaSqft);
    }
    if (balcony) {
      balcony.textContent = formatAreaForDisplay(flat.balconyAreaSqft);
    }
  }

  function readAdminForm(options) {
    var opts = options || {};
    var bhk = document.getElementById("admin-bhk");
    var superBuilder = document.getElementById("admin-super-builder-area");
    var carpet = document.getElementById("admin-carpet-area");
    var balcony = document.getElementById("admin-balcony-area");
    var price = document.getElementById("admin-price");
    var payload = {
      bhkType: bhk ? bhk.value : "",
      areaSqft: readAreaInputSqft(superBuilder),
      carpetAreaSqft: readAreaInputSqft(carpet),
      balconyAreaSqft: readAreaInputSqft(balcony),
      basePrice: price && price.value.trim() !== "" ? Number(price.value.trim()) : null,
    };
    if (opts.includeColumnType) {
      var columnType = document.getElementById("admin-column-type");
      payload.layoutColumnType = columnType ? columnType.value.trim() : "";
    }
    return payload;
  }

  async function loadUnitTypeDefaults(force) {
    if (!isPlatformAdminEdit()) {
      return {};
    }
    if (!force && unitTypeDefaultsCache) {
      return unitTypeDefaultsCache;
    }
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    if (!buildingId) {
      return {};
    }
    var res = await fetch(appRoot() + "/buildings/" + buildingId + "/unit-type-defaults", {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) {
      return unitTypeDefaultsCache || {};
    }
    unitTypeDefaultsCache = await res.json();
    return unitTypeDefaultsCache;
  }

  function getConfiguredTypeDefaults(bhkType) {
    if (!unitTypeDefaultsCache || !bhkType) return null;
    return unitTypeDefaultsCache[bhkType] || null;
  }

  function isResidentialFlatCard(el) {
    return (
      el &&
      el.dataset &&
      el.dataset.flatId &&
      el.dataset.parking !== "true" &&
      el.dataset.amenity !== "true"
    );
  }

  function getConfiguredColumnDefaults(columnNumber) {
    if (!columnTypeDefaultsCache || columnNumber == null || columnNumber === "") return null;
    var key = String(columnNumber);
    return columnTypeDefaultsCache[key] || null;
  }

  function getEffectiveFlatDatasetValue(cardEl, datasetKey, defaultsKey) {
    if (!cardEl) return "";
    var value = cardEl.dataset[datasetKey];
    if (value != null && value !== "") return value;
    if (!isResidentialFlatCard(cardEl)) return "";
    var columnDefaults = getConfiguredColumnDefaults(cardEl.dataset.columnNumber);
    if (columnDefaults && columnDefaults[defaultsKey] != null) {
      return String(columnDefaults[defaultsKey]);
    }
    var defaults = getConfiguredTypeDefaults(cardEl.dataset.type);
    if (!defaults || defaults[defaultsKey] == null) return "";
    return String(defaults[defaultsKey]);
  }

  function updateFlatAddPlaceholders() {
    var bhkSel = document.getElementById("flat-add-bhk");
    if (!bhkSel) return;
    var defaults = getConfiguredTypeDefaults(bhkSel.value);
    var superBuilderInput = document.getElementById("flat-add-super-builder-area");
    var carpetInput = document.getElementById("flat-add-carpet-area");
    var balconyInput = document.getElementById("flat-add-balcony-area");
    var priceInput = document.getElementById("flat-add-price");
    if (superBuilderInput) {
      superBuilderInput.placeholder =
        defaults && defaults.areaSqft != null
          ? formatAreaForDisplay(defaults.areaSqft)
          : "Leave blank for default";
    }
    if (carpetInput) {
      carpetInput.placeholder =
        defaults && defaults.carpetAreaSqft != null
          ? formatAreaForDisplay(defaults.carpetAreaSqft)
          : "Leave blank for default";
    }
    if (balconyInput) {
      balconyInput.placeholder =
        defaults && defaults.balconyAreaSqft != null
          ? formatAreaForDisplay(defaults.balconyAreaSqft)
          : "Leave blank for default";
    }
    if (priceInput) {
      priceInput.placeholder =
        defaults && defaults.basePrice != null ? String(defaults.basePrice) : "Leave blank for default";
    }
  }

  function populateUnitTypeDefaultsModal(bhkType) {
    var bhkSel = document.getElementById("unit-type-defaults-bhk");
    if (bhkSel) {
      if (bhkType) {
        bhkSel.value = bhkType;
      } else if (!bhkSel.value && bhkSel.options.length > 0) {
        bhkSel.value = bhkSel.options[0].value;
      }
    }
    var type = bhkSel ? bhkSel.value : bhkType;
    var entry = getConfiguredTypeDefaults(type);
    var superBuilder = document.getElementById("unit-type-defaults-super-builder-area");
    var carpet = document.getElementById("unit-type-defaults-carpet-area");
    var balcony = document.getElementById("unit-type-defaults-balcony-area");
    var price = document.getElementById("unit-type-defaults-price");
    if (superBuilder) {
      superBuilder.value = entry && entry.areaSqft != null ? formatAreaForDisplay(entry.areaSqft) : "";
    }
    if (carpet) {
      carpet.value =
        entry && entry.carpetAreaSqft != null ? formatAreaForDisplay(entry.carpetAreaSqft) : "";
    }
    if (balcony) {
      balcony.value =
        entry && entry.balconyAreaSqft != null ? formatAreaForDisplay(entry.balconyAreaSqft) : "";
    }
    if (price) {
      price.value = entry && entry.basePrice != null ? String(entry.basePrice) : "";
    }
    updateUnitTypeDefaultsModalTitle(type);
  }

  function updateUnitTypeDefaultsModalTitle(type) {
    var title = document.getElementById("unit-type-defaults-modal-title");
    if (!title) return;
    title.textContent = type ? "Unit type defaults — " + type : "Unit type defaults";
  }

  function showUnitTypeDefaultsError(message) {
    var el = document.getElementById("unit-type-defaults-error");
    if (!el) return;
    if (!message) {
      el.textContent = "";
      el.classList.add("d-none");
      return;
    }
    el.textContent = message;
    el.classList.remove("d-none");
  }

  async function openUnitTypeDefaultsModal(bhkType) {
    if (!isPlatformAdminEdit()) return;
    mountModalsOnBody();
    await loadUnitTypeDefaults(false);
    populateUnitTypeDefaultsModal(bhkType || null);
    var unitSel = document.getElementById("unit-type-defaults-area-unit");
    if (unitSel) {
      unitSel.value = getAreaUnit();
    }
    updateAreaUnitLabels();
    showUnitTypeDefaultsError("");
    var modalEl = document.getElementById("unit-type-defaults-modal");
    if (modalEl && typeof bootstrap !== "undefined" && bootstrap.Modal) {
      bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
  }

  function readUnitTypeDefaultsFormPayload() {
    var bhkSel = document.getElementById("unit-type-defaults-bhk");
    var superBuilder = document.getElementById("unit-type-defaults-super-builder-area");
    var carpet = document.getElementById("unit-type-defaults-carpet-area");
    var balcony = document.getElementById("unit-type-defaults-balcony-area");
    var price = document.getElementById("unit-type-defaults-price");
    if (!bhkSel) return null;
    return {
      bhkType: bhkSel.value,
      areaSqft: readAreaInputSqft(superBuilder),
      carpetAreaSqft: readAreaInputSqft(carpet),
      balconyAreaSqft: readAreaInputSqft(balcony),
      basePrice: price && price.value.trim() !== "" ? Number(price.value.trim()) : null,
    };
  }

  function applyUnitTypeDefaultsResultToGrid(updatedFlats) {
    if (!updatedFlats || !updatedFlats.length) return;
    updatedFlats.forEach(function (flat) {
      var card = document.getElementById("flat-" + flat.id);
      applyFlatDataToCard(card, flat);
      if (selectedFlatId && String(selectedFlatId) === String(flat.id)) {
        document.getElementById("panel-price").textContent =
          flat.basePrice != null ? String(flat.basePrice) : "";
        setAreaPanelFromFlat(flat);
        syncAdminAreaInputsFromFlat(flat);
        if (card) syncAdminPanel(card);
      }
    });
  }

  async function saveUnitTypeDefaults() {
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    var bhkSel = document.getElementById("unit-type-defaults-bhk");
    var payload = readUnitTypeDefaultsFormPayload();
    if (!buildingId || !bhkSel || !payload) return;
    showUnitTypeDefaultsError("");
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    var res = await fetch(appRoot() + "/buildings/" + buildingId + "/unit-type-defaults", {
      method: "POST",
      headers: headers,
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      showUnitTypeDefaultsError(await parseErrorResponse(res));
      return;
    }
    unitTypeDefaultsCache = await res.json();
    updateFlatAddPlaceholders();
    populateUnitTypeDefaultsModal(bhkSel.value);
    var modalEl = document.getElementById("unit-type-defaults-modal");
    if (modalEl && bootstrap.Modal.getInstance(modalEl)) {
      bootstrap.Modal.getInstance(modalEl).hide();
    }
    showGridToast("Saved defaults for " + bhkSel.value, "success");
  }

  async function applyUnitTypeDefaultsToFlats() {
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    var bhkSel = document.getElementById("unit-type-defaults-bhk");
    var payload = readUnitTypeDefaultsFormPayload();
    if (!buildingId || !bhkSel || !payload) return;
    showUnitTypeDefaultsError("");
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    var res = await fetch(appRoot() + "/buildings/" + buildingId + "/unit-type-defaults/apply", {
      method: "POST",
      headers: headers,
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      showUnitTypeDefaultsError(await parseErrorResponse(res));
      return;
    }
    var data = await res.json();
    if (data.defaults) {
      unitTypeDefaultsCache = data.defaults;
      updateFlatAddPlaceholders();
    }
    var updatedFlats = data.updatedFlats || [];
    applyUnitTypeDefaultsResultToGrid(updatedFlats);
    var countMsg = updatedFlats.length > 0 ? String(updatedFlats.length) : "0";
    showGridToast("Applied to " + countMsg + " " + bhkSel.value + " flat(s)", "success");
  }

  async function loadColumnTypeDefaults(force) {
    if (!isPlatformAdminEdit()) {
      return {};
    }
    if (!force && columnTypeDefaultsCache) {
      return columnTypeDefaultsCache;
    }
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    if (!buildingId) {
      return {};
    }
    var res = await fetch(appRoot() + "/buildings/" + buildingId + "/column-type-defaults", {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) {
      return columnTypeDefaultsCache || {};
    }
    columnTypeDefaultsCache = await res.json();
    return columnTypeDefaultsCache;
  }

  function populateColumnTypeDefaultsModal(columnNumber) {
    var colSel = document.getElementById("column-type-defaults-column");
    if (colSel) {
      if (columnNumber) {
        colSel.value = String(columnNumber);
      } else if (!colSel.value && colSel.options.length > 0) {
        colSel.value = colSel.options[0].value;
      }
    }
    var col = colSel ? colSel.value : columnNumber;
    var entry = getConfiguredColumnDefaults(col);
    var typeLabel = document.getElementById("column-type-defaults-type-label");
    var superBuilder = document.getElementById("column-type-defaults-super-builder-area");
    var carpet = document.getElementById("column-type-defaults-carpet-area");
    var balcony = document.getElementById("column-type-defaults-balcony-area");
    var price = document.getElementById("column-type-defaults-price");
    if (typeLabel) {
      typeLabel.value =
        entry && entry.layoutColumnType != null ? String(entry.layoutColumnType) : "";
    }
    if (superBuilder) {
      superBuilder.value = entry && entry.areaSqft != null ? formatAreaForDisplay(entry.areaSqft) : "";
    }
    if (carpet) {
      carpet.value =
        entry && entry.carpetAreaSqft != null ? formatAreaForDisplay(entry.carpetAreaSqft) : "";
    }
    if (balcony) {
      balcony.value =
        entry && entry.balconyAreaSqft != null ? formatAreaForDisplay(entry.balconyAreaSqft) : "";
    }
    if (price) {
      price.value = entry && entry.basePrice != null ? String(entry.basePrice) : "";
    }
    updateColumnTypeDefaultsModalTitle(col);
  }

  function updateColumnTypeDefaultsModalTitle(columnNumber) {
    var title = document.getElementById("column-type-defaults-modal-title");
    var hint = document.getElementById("column-type-defaults-apply-hint");
    if (hint && columnNumber) {
      hint.textContent = String(columnNumber);
    }
    if (!title) return;
    title.textContent = columnNumber
      ? "Column defaults — column " + columnNumber
      : "Column defaults";
  }

  function showColumnTypeDefaultsError(message) {
    var el = document.getElementById("column-type-defaults-error");
    if (!el) return;
    if (!message) {
      el.textContent = "";
      el.classList.add("d-none");
      return;
    }
    el.textContent = message;
    el.classList.remove("d-none");
  }

  async function openColumnTypeDefaultsModal(columnNumber) {
    if (!isPlatformAdminEdit()) return;
    mountModalsOnBody();
    await loadColumnTypeDefaults(false);
    populateColumnTypeDefaultsModal(columnNumber || null);
    var unitSel = document.getElementById("column-type-defaults-area-unit");
    if (unitSel) {
      unitSel.value = getAreaUnit();
    }
    updateAreaUnitLabels();
    showColumnTypeDefaultsError("");
    var modalEl = document.getElementById("column-type-defaults-modal");
    if (modalEl && typeof bootstrap !== "undefined" && bootstrap.Modal) {
      bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
  }

  function readColumnTypeDefaultsFormPayload() {
    var colSel = document.getElementById("column-type-defaults-column");
    var typeLabel = document.getElementById("column-type-defaults-type-label");
    var superBuilder = document.getElementById("column-type-defaults-super-builder-area");
    var carpet = document.getElementById("column-type-defaults-carpet-area");
    var balcony = document.getElementById("column-type-defaults-balcony-area");
    var price = document.getElementById("column-type-defaults-price");
    if (!colSel) return null;
    return {
      columnNumber: Number(colSel.value),
      layoutColumnType: typeLabel ? typeLabel.value.trim() : "",
      areaSqft: readAreaInputSqft(superBuilder),
      carpetAreaSqft: readAreaInputSqft(carpet),
      balconyAreaSqft: readAreaInputSqft(balcony),
      basePrice: price && price.value.trim() !== "" ? Number(price.value.trim()) : null,
    };
  }

  async function saveColumnTypeDefaults() {
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    var colSel = document.getElementById("column-type-defaults-column");
    var payload = readColumnTypeDefaultsFormPayload();
    if (!buildingId || !colSel || !payload) return;
    showColumnTypeDefaultsError("");
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    var res = await fetch(appRoot() + "/buildings/" + buildingId + "/column-type-defaults", {
      method: "POST",
      headers: headers,
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      showColumnTypeDefaultsError(await parseErrorResponse(res));
      return;
    }
    columnTypeDefaultsCache = await res.json();
    populateColumnTypeDefaultsModal(colSel.value);
    var modalEl = document.getElementById("column-type-defaults-modal");
    if (modalEl && bootstrap.Modal.getInstance(modalEl)) {
      bootstrap.Modal.getInstance(modalEl).hide();
    }
    showGridToast("Saved column defaults for column " + colSel.value, "success");
  }

  async function applyColumnTypeDefaultsToFlats() {
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    var colSel = document.getElementById("column-type-defaults-column");
    var payload = readColumnTypeDefaultsFormPayload();
    if (!buildingId || !colSel || !payload) return;
    showColumnTypeDefaultsError("");
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    var res = await fetch(appRoot() + "/buildings/" + buildingId + "/column-type-defaults/apply", {
      method: "POST",
      headers: headers,
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      showColumnTypeDefaultsError(await parseErrorResponse(res));
      return;
    }
    var data = await res.json();
    if (data.defaults) {
      columnTypeDefaultsCache = data.defaults;
    }
    var updatedFlats = data.updatedFlats || [];
    applyUnitTypeDefaultsResultToGrid(updatedFlats);
    var countMsg = updatedFlats.length > 0 ? String(updatedFlats.length) : "0";
    showGridToast("Applied to " + countMsg + " flat(s) in column " + colSel.value, "success");
  }

  function ensureAdminBhkOption(selectEl, value) {
    if (!selectEl || !value) return;
    var exists = false;
    for (var i = 0; i < selectEl.options.length; i++) {
      if (selectEl.options[i].value === value) {
        exists = true;
        break;
      }
    }
    if (!exists) {
      var opt = document.createElement("option");
      opt.value = value;
      opt.textContent = value;
      selectEl.appendChild(opt);
    }
    selectEl.value = value;
  }

  function setAdminEditModeVisible(show) {
    ["panel-type", "panel-column-type", "panel-column-number", "panel-super-builder-area", "panel-carpet-area", "panel-balcony-area", "panel-price"].forEach(function (id) {
      var el = document.getElementById(id);
      if (el) el.classList.toggle("d-none", show);
    });
    document.querySelectorAll(".admin-edit-field").forEach(function (el) {
      el.classList.toggle("d-none", !show);
    });
    var saveRow = document.getElementById("admin-save-row");
    if (saveRow) saveRow.classList.toggle("d-none", !show);
  }

  function applyFlatDataToCard(cardEl, flat) {
    if (!cardEl || !flat) return;
    if (flat.bhkType != null) cardEl.dataset.type = flat.bhkType;
    if (Object.prototype.hasOwnProperty.call(flat, "layoutColumnType")) {
      if (flat.layoutColumnType != null && flat.layoutColumnType !== "") {
        cardEl.dataset.columnType = flat.layoutColumnType;
      } else {
        delete cardEl.dataset.columnType;
      }
    }
    if (flat.areaSqft != null) cardEl.dataset.area = String(flat.areaSqft);
    if (flat.carpetAreaSqft != null) cardEl.dataset.carpetArea = String(flat.carpetAreaSqft);
    else delete cardEl.dataset.carpetArea;
    if (flat.balconyAreaSqft != null) cardEl.dataset.balconyArea = String(flat.balconyAreaSqft);
    else delete cardEl.dataset.balconyArea;
    if (flat.basePrice != null) cardEl.dataset.price = String(flat.basePrice);
    if (flat.status != null) {
      cardEl.dataset.status = flat.status;
      syncFlatCardStatusLabel(cardEl, flat.status);
    }
    if (flat.floorNumber != null) cardEl.dataset.floor = String(flat.floorNumber);
    var parking = flat.parking === true || flat.parking === "true";
    var amenity = flat.amenity === true || flat.amenity === "true" || isAmenityType(flat.bhkType);
    var duplexSecondary = flat.duplexSecondary === true || flat.duplexSecondary === "true";
    var duplexPrimary = flat.duplexPrimary === true || flat.duplexPrimary === "true";
    var mergeSecondary = flat.mergeSecondary === true || flat.mergeSecondary === "true";
    var mergePrimary = flat.mergePrimary === true || flat.mergePrimary === "true";
    applyCardTypeClasses(cardEl, {
      parking: parking,
      amenity: amenity,
      duplexSecondary: duplexSecondary,
      duplexPrimary: duplexPrimary,
      mergeSecondary: mergeSecondary,
      mergePrimary: mergePrimary,
      status: flat.status || cardEl.dataset.status,
    });
    var displayType =
      flat.gridTypeLabel ||
      (flat.bhkType && flat.layoutColumnType
        ? flat.bhkType + " · " + flat.layoutColumnType
        : flat.bhkType);
    if (displayType) {
      cardEl.dataset.gridType = displayType;
    }
    var typeSpan = cardEl.querySelector(".flat-type");
    if (typeSpan && displayType) typeSpan.textContent = displayType;
    if (parking || amenity || duplexSecondary || mergeSecondary) {
      delete cardEl.dataset.hasLayoutImage;
      return;
    }
    if (flat.hasLayoutImage === true || flat.hasLayoutImage === "true") {
      cardEl.dataset.hasLayoutImage = "true";
    } else {
      delete cardEl.dataset.hasLayoutImage;
    }
  }

  async function loadMergeCandidates(flatId) {
    var select = document.getElementById("admin-merge-remove");
    if (!select) return;
    select.innerHTML = '<option value="">— Select flat to link —</option>';
    var res = await fetch(appRoot() + "/flats/" + flatId + "/merge-candidates", {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return;
    var list = await res.json();
    list.forEach(function (c) {
      var opt = document.createElement("option");
      opt.value = c.id;
      opt.dataset.verticalDuplex = c.verticalDuplex ? "true" : "false";
      opt.textContent =
        (c.flatNumber || c.id) +
        " · " +
        (c.bhkType || "") +
        " · " +
        (c.status || "") +
        (c.verticalDuplex ? " · adjacent duplex" : " · same floor");
      select.appendChild(opt);
    });
  }

  async function parseErrorResponse(res) {
    try {
      var data = await res.json();
      if (data && data.error) return String(data.error);
    } catch (e) {
      /* ignore */
    }
    return "Request failed (" + res.status + ")";
  }

  function syncPartnerTag(cardEl, partnerId, partnerName) {
    if (!cardEl) return;
    if (partnerId) {
      cardEl.dataset.partnerId = partnerId;
    } else {
      delete cardEl.dataset.partnerId;
    }
    if (partnerName) {
      cardEl.dataset.partnerName = partnerName;
    } else {
      delete cardEl.dataset.partnerName;
    }
    var inner = cardEl.querySelector(".flat-card-inner");
    if (!inner) return;
    var tag = cardEl.querySelector(".flat-partner-tag");
    var label = partnerName ? String(partnerName).trim() : "";
    if (!label) {
      if (tag) tag.remove();
      return;
    }
    if (!tag) {
      tag = document.createElement("span");
      tag.className = "flat-partner-tag small";
      var typeSpan = inner.querySelector(".flat-type");
      if (typeSpan) inner.insertBefore(tag, typeSpan);
      else inner.appendChild(tag);
    }
    tag.textContent = label;
  }

  function syncDeactivatedTag(cardEl) {
    if (!cardEl) return;
    var inner = cardEl.querySelector(".flat-card-inner");
    if (!inner) return;
    var tag = cardEl.querySelector(".flat-status-tag");
    var isDeactivated = cardEl.dataset.status === "CANCELLED";
    if (!isDeactivated) {
      if (tag) tag.remove();
      return;
    }
    if (!tag) {
      tag = document.createElement("span");
      tag.className = "flat-status-tag small";
      tag.textContent = "Deactivated";
      var typeSpan = inner.querySelector(".flat-type");
      if (typeSpan) inner.insertBefore(tag, typeSpan);
      else inner.appendChild(tag);
    }
  }

  function syncAdminPanel(cardEl) {
    var panel = document.getElementById("flat-admin-panel");
    var adminMode = isPlatformAdminEdit() && !!document.getElementById("admin-bhk");
    if (adminMode) {
      setAdminEditModeVisible(true);
      if (panel) panel.classList.remove("d-none");
      showAdminError("");
      var bhk = document.getElementById("admin-bhk");
      var superBuilder = document.getElementById("admin-super-builder-area");
      var carpet = document.getElementById("admin-carpet-area");
      var balcony = document.getElementById("admin-balcony-area");
      var price = document.getElementById("admin-price");
      var partner = document.getElementById("admin-partner");
      var currentType = cardEl.dataset.type || "2BHK";
      if (bhk) ensureAdminBhkOption(bhk, currentType);
      if (superBuilder) {
        superBuilder.value = formatAreaForDisplay(
          getEffectiveFlatDatasetValue(cardEl, "area", "areaSqft") || cardEl.dataset.area
        );
      }
      if (carpet) {
        carpet.value = formatAreaForDisplay(
          getEffectiveFlatDatasetValue(cardEl, "carpetArea", "carpetAreaSqft") ||
            cardEl.dataset.carpetArea
        );
      }
      if (balcony) {
        balcony.value = formatAreaForDisplay(
          getEffectiveFlatDatasetValue(cardEl, "balconyArea", "balconyAreaSqft") ||
            cardEl.dataset.balconyArea
        );
      }
      if (price) {
        price.value =
          getEffectiveFlatDatasetValue(cardEl, "price", "basePrice") || cardEl.dataset.price || "";
      }
      syncColumnTypePanelFromCard(cardEl);
      if (partner) partner.value = cardEl.dataset.partnerId || "";
      var isBooked = cardEl.dataset.status === "BOOKED";
      if (bhk) {
        bhk.disabled = isBooked;
        if (isBooked) {
          bhk.title = "Unit type cannot change while this flat is booked";
        } else {
          bhk.removeAttribute("title");
        }
      }
      [superBuilder, carpet, balcony, price].forEach(function (input) {
        if (input) input.disabled = false;
      });
      var adminApplyFloor = document.getElementById("admin-apply-floor-btn");
      if (adminApplyFloor) {
        adminApplyFloor.disabled = isBooked;
        if (isBooked) {
          adminApplyFloor.title = "Cannot apply floor-wide changes while this flat is booked";
        } else {
          adminApplyFloor.removeAttribute("title");
        }
      }
      var adminSaveBtn = document.getElementById("admin-save-btn");
      if (adminSaveBtn) {
        adminSaveBtn.textContent = isBooked
          ? "Save areas / price"
          : "Save unit type / area / price";
      }
      var saveHint = document.getElementById("admin-save-hint");
      if (saveHint) {
        saveHint.textContent = isBooked
          ? "Booked flat — unit type and floor-wide apply are locked; leave area/price blank to keep current values. Column type can still be edited."
          : "Leave area/price blank to keep this unit only. Column type is optional per flat. Building-wide defaults are set above the grid.";
      }
      var nonBookable = isNonBookableUnit(cardEl);
      var isDuplexLinked =
        cardEl.dataset.duplexPrimary === "true" || cardEl.dataset.duplexSecondary === "true";
      var isMergeLinked =
        cardEl.dataset.mergePrimary === "true" || cardEl.dataset.mergeSecondary === "true";
      var splitBtn = document.getElementById("admin-split-duplex-btn");
      var splitRow = document.getElementById("admin-split-row");
      var splitMergeRow = document.getElementById("admin-split-merge-row");
      var splitMergeLabel = document.getElementById("admin-split-merge-label");
      var mergeRow = document.getElementById("admin-merge-row");
      if (splitRow) {
        splitRow.classList.toggle("d-none", !isDuplexLinked);
      }
      if (splitBtn) {
        splitBtn.disabled = nonBookable;
      }
      if (splitMergeRow) {
        splitMergeRow.classList.toggle("d-none", !isMergeLinked);
      }
      if (splitMergeLabel) {
        var absorbedNo = cardEl.dataset.mergeAbsorbedNumber || "merged unit";
        splitMergeLabel.textContent =
          "Same-floor merge — restore " + absorbedNo + " as a separate flat";
      }
      if (mergeRow) {
        mergeRow.classList.toggle("d-none", nonBookable || isDuplexLinked || isMergeLinked);
      }
      ["admin-partner", "admin-partner-save", "admin-delete-btn", "admin-remove-flat-btn"].forEach(function (id) {
        var el = document.getElementById(id);
        if (!el) return;
        var row = el.closest(".row");
        if (row) row.classList.toggle("d-none", nonBookable);
      });
      var adminSaveRow = document.getElementById("admin-save-row");
      if (adminSaveRow) {
        adminSaveRow.classList.toggle(
          "d-none",
          cardEl.dataset.duplexSecondary === "true" || cardEl.dataset.mergeSecondary === "true"
        );
      }
      var adminDelete = document.getElementById("admin-delete-btn");
      if (adminDelete) {
        var isInactive = cardEl.dataset.status === "CANCELLED";
        var isBooked = cardEl.dataset.status === "BOOKED";
        adminDelete.textContent = isInactive ? "Activate this flat" : "Deactivate this flat";
        adminDelete.classList.toggle("btn-outline-danger", !isInactive);
        adminDelete.classList.toggle("btn-outline-success", isInactive);
        adminDelete.classList.toggle("d-none", isBooked);
      }
      var adminRemove = document.getElementById("admin-remove-flat-btn");
      if (adminRemove) {
        var canRemove = cardEl.dataset.status !== "BOOKED";
        adminRemove.classList.toggle("d-none", !canRemove);
        adminRemove.disabled = !canRemove;
      }
      if (selectedFlatId) loadMergeCandidates(selectedFlatId);
      return;
    }
    setAdminEditModeVisible(false);
    if (panel) panel.classList.add("d-none");
  }

  function applyBookingSelectionHighlight() {
    var grid = document.getElementById("flat-grid");
    if (!grid) return;
    grid.querySelectorAll(".flat-card").forEach(function (card) {
      card.classList.remove("flat-card--booking-selected");
    });
    if (selectedFlatId) {
      var sel = document.getElementById("flat-" + selectedFlatId);
      if (sel) {
        sel.classList.add("flat-card--booking-selected");
      }
    }
  }

  function tipRow(label, value) {
    if (!value) return null;
    var row = document.createElement("div");
    row.className = "flat-card-buyertip__row";
    var lbl = document.createElement("span");
    lbl.textContent = label;
    var val = document.createElement("span");
    val.textContent = value;
    if (label === "Email") val.className = "flat-card-buyertip__email";
    row.appendChild(lbl);
    row.appendChild(val);
    return row;
  }

  function buildBuyerTip(flat) {
    var name = flat.ownerDisplay == null ? "" : String(flat.ownerDisplay).trim();
    if (!name || flat.status !== "BOOKED") return null;
    var tip = document.createElement("div");
    tip.className = "flat-card-buyertip";
    var title = document.createElement("div");
    title.className = "flat-card-buyertip__title";
    title.textContent = "Buyer";
    var nameEl = document.createElement("div");
    nameEl.className = "flat-card-buyertip__name";
    nameEl.textContent = name;
    tip.appendChild(title);
    tip.appendChild(nameEl);
    var code = flat.bookingCode == null ? "" : String(flat.bookingCode).trim();
    var phone = flat.buyerPhone == null ? "" : String(flat.buyerPhone).trim();
    var email = flat.buyerEmail == null ? "" : String(flat.buyerEmail).trim();
    var r;
    if (code) {
      r = tipRow("Booking", code);
      if (r) tip.appendChild(r);
    }
    if (phone) {
      r = tipRow("Phone", phone);
      if (r) tip.appendChild(r);
    }
    if (email) {
      r = tipRow("Email", email);
      if (r) tip.appendChild(r);
    }
    return tip;
  }

  function stripNonBookableHover(cardEl) {
    if (!cardEl || cardEl.dataset.parking === "true" || isFlatBookable(cardEl)) return;
    cardEl.classList.remove("flat-card--has-buyer");
    var tip = cardEl.querySelector(".flat-card-buyertip");
    if (tip) tip.remove();
  }

  function syncBuyerTooltip(el, flat) {
    if (!isFlatBookableFromData(flat, el)) {
      stripNonBookableHover(el);
      return;
    }
    var name = flat.ownerDisplay == null ? "" : String(flat.ownerDisplay).trim();
    var hasBuyer = flat.status === "BOOKED" && name;
    el.classList.toggle("flat-card--has-buyer", !!hasBuyer);
    el.removeAttribute("title");
    var tip = el.querySelector(".flat-card-buyertip");
    if (!hasBuyer) {
      if (tip) tip.remove();
      return;
    }
    var built = buildBuyerTip(flat);
    if (!built) return;
    if (tip) tip.replaceWith(built);
    else el.insertBefore(built, el.firstChild);
  }

  function syncCardOwner(el, flat) {
    var inner = el.querySelector(".flat-card-inner");
    if (!inner) return;
    var on = inner.querySelector(".flat-owner-name");
    var od = inner.querySelector(".flat-owner-detail");
    var owner = inner.querySelector(".flat-card-owner");
    var lines = ownerLinesForCard(flat, el);
    var bookable = isFlatBookableFromData(flat, el);
    if (on) on.textContent = lines.display;
    if (od) {
      od.textContent = lines.detail;
      od.classList.toggle("d-none", !bookable || !lines.detail);
    }
    if (owner) {
      owner.classList.toggle("is-blank", !lines.display && !lines.detail);
    }
    if (!bookable) {
      delete el.dataset.clientId;
      delete el.dataset.ownerDisplay;
      delete el.dataset.ownerDetail;
      delete el.dataset.bookingCode;
      delete el.dataset.buyerPhone;
      delete el.dataset.buyerEmail;
    }
  }

  function drawFlatPairLinks() {
    var grid = document.getElementById("flat-grid");
    var svg = document.getElementById("flat-grid-duplex-links");
    if (!grid || !svg) return;

    while (svg.firstChild) {
      svg.removeChild(svg.firstChild);
    }

    var width = grid.offsetWidth;
    var height = grid.offsetHeight;
    if (width <= 0 || height <= 0) return;

    svg.setAttribute("width", String(width));
    svg.setAttribute("height", String(height));
    svg.setAttribute("viewBox", "0 0 " + width + " " + height);

    var gridRect = grid.getBoundingClientRect();
    var drawn = {};

    grid.querySelectorAll('.flat-card[data-duplex-primary="true"]').forEach(function (primary) {
      var partnerId = primary.dataset.duplexPartnerId;
      if (!partnerId) return;
      var secondary = document.getElementById("flat-" + partnerId);
      if (!secondary) return;

      var pairKey = "duplex:" + [primary.dataset.flatId, partnerId].sort().join(":");
      if (drawn[pairKey]) return;
      drawn[pairKey] = true;

      var pRect = primary.getBoundingClientRect();
      var sRect = secondary.getBoundingClientRect();
      var padX = 5;
      var padY = 4;
      var left = pRect.left - gridRect.left - padX;
      var right = pRect.right - gridRect.left + padX;
      var top = Math.min(pRect.top, sRect.top) - gridRect.top - padY;
      var bottom = Math.max(pRect.bottom, sRect.bottom) - gridRect.top + padY;

      appendLinkRect(svg, left, top, right, bottom, "flat-duplex-link-outline");
    });

    grid.querySelectorAll('.flat-card[data-merge-primary="true"]').forEach(function (primary) {
      var partnerId = primary.dataset.mergePartnerId;
      if (!partnerId) return;
      var secondary = document.getElementById("flat-" + partnerId);
      if (!secondary) return;

      var pairKey = "merge:" + [primary.dataset.flatId, partnerId].sort().join(":");
      if (drawn[pairKey]) return;
      drawn[pairKey] = true;

      var pRect = primary.getBoundingClientRect();
      var sRect = secondary.getBoundingClientRect();
      var padX = 5;
      var padY = 4;
      var left = Math.min(pRect.left, sRect.left) - gridRect.left - padX;
      var right = Math.max(pRect.right, sRect.right) - gridRect.left + padX;
      var top = Math.min(pRect.top, sRect.top) - gridRect.top - padY;
      var bottom = Math.max(pRect.bottom, sRect.bottom) - gridRect.top + padY;

      appendLinkRect(svg, left, top, right, bottom, "flat-merge-link-outline");
    });
  }

  function appendLinkRect(svg, left, top, right, bottom, className) {
    var rect = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    rect.setAttribute("x", String(left));
    rect.setAttribute("y", String(top));
    rect.setAttribute("width", String(Math.max(0, right - left)));
    rect.setAttribute("height", String(Math.max(0, bottom - top)));
    rect.setAttribute("rx", "10");
    rect.setAttribute("class", className);
    svg.appendChild(rect);
  }

  function drawDuplexLinks() {
    drawFlatPairLinks();
  }

  function scheduleDuplexLinks() {
    window.requestAnimationFrame(function () {
      drawDuplexLinks();
    });
  }

  function closeFlatDetailsModal() {
    var modalEl = document.getElementById("flat-details-modal");
    if (modalEl && typeof bootstrap !== "undefined" && bootstrap.Modal) {
      bootstrap.Modal.getOrCreateInstance(modalEl).hide();
    }
  }

  async function afterLayoutChange(keepFlatId, removeFlatId, options) {
    options = options || {};
    await refreshGrid();
    if (removeFlatId) {
      var removed = document.getElementById("flat-" + removeFlatId);
      if (removed) removed.remove();
    }
    closeFlatDetailsModal();
    if (keepFlatId) {
      selectedFlatId = keepFlatId;
      var updated = document.getElementById("flat-" + keepFlatId);
      if (updated) window.floor21SelectFlat(updated, options.showModal !== false);
    }
    scheduleDuplexLinks();
  }

  function showGridToast(message, tone) {
    var el = document.getElementById("flat-grid-toast");
    if (!el || !message) return;
    el.textContent = message;
    el.classList.remove("d-none", "alert-success", "alert-danger");
    el.classList.add(tone === "error" ? "alert-danger" : "alert-success");
    clearTimeout(el._hideTimer);
    el._hideTimer = setTimeout(function () {
      el.classList.add("d-none");
    }, 9000);
  }

  function highlightFlatCard(cardEl) {
    if (!cardEl) return;
    cardEl.classList.add("flat-card--focused");
    cardEl.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
    setTimeout(function () {
      cardEl.classList.remove("flat-card--focused");
    }, 5000);
  }

  function findFloorRow(floorNumber) {
    var grid = document.getElementById("flat-grid");
    if (!grid) return null;
    var rows = grid.querySelectorAll("[data-floor-number]");
    for (var i = 0; i < rows.length; i++) {
      if (String(rows[i].getAttribute("data-floor-number")) === String(floorNumber)) {
        return rows[i];
      }
    }
    return null;
  }

  function syncFlatCardFromData(el, flat) {
    if (!el || !flat) return;
    el.dataset.status = flat.status;
    el.dataset.type = flat.bhkType;
    el.dataset.floor = flat.floorNumber;
    el.dataset.price = flat.basePrice;
    el.dataset.area = flat.areaSqft != null ? flat.areaSqft : "";
    el.dataset.carpetArea = flat.carpetAreaSqft != null ? flat.carpetAreaSqft : "";
    el.dataset.balconyArea = flat.balconyAreaSqft != null ? flat.balconyAreaSqft : "";
    el.dataset.parking = flat.parking;
    el.dataset.amenity = isAmenityType(flat.bhkType);
    el.dataset.duplexPrimary = flat.duplexPrimary ? "true" : "false";
    el.dataset.duplexSecondary = flat.duplexSecondary ? "true" : "false";
    el.dataset.mergePrimary = flat.mergePrimary ? "true" : "false";
    el.dataset.mergeSecondary = flat.mergeSecondary ? "true" : "false";
    if (flat.mergePartnerFlatId) {
      el.dataset.mergePartnerId = flat.mergePartnerFlatId;
    } else {
      delete el.dataset.mergePartnerId;
    }
    if (flat.mergeAbsorbedFlatId) {
      el.dataset.mergeAbsorbedId = flat.mergeAbsorbedFlatId;
    } else {
      delete el.dataset.mergeAbsorbedId;
    }
    if (flat.mergeAbsorbedFlatNumber) {
      el.dataset.mergeAbsorbedNumber = flat.mergeAbsorbedFlatNumber;
    } else {
      delete el.dataset.mergeAbsorbedNumber;
    }
    if (flat.duplexPartnerFlatId) {
      el.dataset.duplexPartnerId = flat.duplexPartnerFlatId;
    } else {
      delete el.dataset.duplexPartnerId;
    }
    if (flat.gridTypeLabel) {
      el.dataset.gridType = flat.gridTypeLabel;
    }
    el.dataset.bookable = flat.bookableByCurrentUser === false ? "false" : "true";
    if (flat.assignedPartnerId) {
      el.dataset.partnerId = flat.assignedPartnerId;
    } else {
      delete el.dataset.partnerId;
    }
    syncPartnerTag(el, flat.assignedPartnerId, flat.assignedPartnerName);
    syncDeactivatedTag(el);
    if (flat.clientId) {
      el.dataset.clientId = flat.clientId;
    } else {
      delete el.dataset.clientId;
    }
    syncBuyerTooltip(el, flat);
    syncCardOwner(el, flat);
    var typeSpan = el.querySelector(".flat-type");
    if (typeSpan && flat.gridTypeLabel) typeSpan.textContent = flat.gridTypeLabel;
    else if (typeSpan && flat.bhkType) typeSpan.textContent = flat.bhkType;
    if (flat.cardClass) {
      el.className = flat.cardClass;
    }
    applyCardTypeClasses(el, {
      parking: flat.parking === true,
      amenity: isAmenityType(flat.bhkType),
      duplexSecondary: flat.duplexSecondary === true,
      duplexPrimary: flat.duplexPrimary === true,
      mergeSecondary: flat.mergeSecondary === true,
      mergePrimary: flat.mergePrimary === true,
      status: flat.status,
    });
    if (
      flat.bookableByCurrentUser === false &&
      !flat.parking &&
      !isAmenityType(flat.bhkType) &&
      !flat.duplexSecondary &&
      !flat.mergeSecondary
    ) {
      el.classList.add("flat-card--other-partner");
      delete el.dataset.hasLayoutImage;
    } else {
      el.classList.remove("flat-card--other-partner");
    }
    if (flat.hasLayoutImage) {
      el.dataset.hasLayoutImage = "true";
    } else {
      delete el.dataset.hasLayoutImage;
    }
    stripNonBookableHover(el);
  }

  function createFlatCardFromData(flat) {
    var card = document.createElement("div");
    card.id = "flat-" + flat.id;
    card.className = flat.cardClass || "flat-card flat-available";
    card.dataset.flatId = flat.id;
    appendFlatCardRoof(card, flat.bhkType);
    card.appendChild(buildFlatCardContent(flat));
    appendFlatCardStatus(card, flat.status);
    syncFlatCardFromData(card, flat);
    return card;
  }

  function insertFlatCardInRow(floorRow, cardEl, flatNumber) {
    var row = floorRow.querySelector(".flat-card-row");
    if (!row) return;
    var cards = row.querySelectorAll(".flat-card");
    var inserted = false;
    for (var i = 0; i < cards.length; i++) {
      var n = cards[i].querySelector(".flat-number");
      var existing = n ? n.textContent.trim() : "";
      if (flatNumber && existing && String(flatNumber) < String(existing)) {
        row.insertBefore(cardEl, cards[i]);
        inserted = true;
        break;
      }
    }
    if (!inserted) row.appendChild(cardEl);
  }

  function parkingSectionConfigured(floor) {
    return floor.parkingConfigured === true || floor.parkingConfigured === "true";
  }

  function parkingFixtureMetaSuffix(floor) {
    if (!parkingSectionConfigured(floor)) return "";
    var carLifts = Number(floor.parkingCarLiftCount != null ? floor.parkingCarLiftCount : 0);
    var passengerLifts = Number(
      floor.parkingPassengerLiftCount != null ? floor.parkingPassengerLiftCount : 0
    );
    var gates = Number(floor.parkingGateCount != null ? floor.parkingGateCount : 0);
    var parts = [];
    if (carLifts > 0) {
      parts.push(carLifts + (carLifts === 1 ? " car lift" : " car lifts"));
    }
    if (passengerLifts > 0) {
      parts.push(
        passengerLifts + (passengerLifts === 1 ? " passenger lift" : " passenger lifts")
      );
    }
    if (gates > 0) parts.push(gates + (gates === 1 ? " gate" : " gates"));
    return parts.length ? " · " + parts.join(" · ") : "";
  }

  function parkingSectionMetaText(floor) {
    var count = floor.parkingSlotCount || (floor.flats ? floor.flats.length : 0);
    var range = floor.parkingRangeLabel || "";
    var areaPart = parkingSectionConfigured(floor) ? parkingAreaMetaSuffix(floor) : "";
    return count + " slots" + (range ? " · " + range : "") + areaPart + parkingFixtureMetaSuffix(floor);
  }

  function parkingFixtureDragKey(kind, index) {
    return "fixture:" + kind + ":" + index;
  }

  function parseParkingDragKey(raw) {
    if (!raw) return null;
    if (String(raw).indexOf("fixture:") === 0) {
      var rest = String(raw).substring(8);
      var lastColon = rest.lastIndexOf(":");
      if (lastColon < 0) return null;
      return {
        type: "fixture",
        kind: rest.substring(0, lastColon),
        index: Number(rest.substring(lastColon + 1)),
      };
    }
    var slotNumber = Number(raw);
    if (!slotNumber) return null;
    return { type: "slot", slotNumber: slotNumber };
  }

  function findFixturePlacement(plan, kind, index) {
    if (!plan || !plan.fixtures) return null;
    var i;
    for (i = 0; i < plan.fixtures.length; i++) {
      var f = plan.fixtures[i];
      if (f.kind === kind && Number(f.index) === Number(index)) return f;
    }
    return null;
  }

  function parkingFixtureUiMeta(kind) {
    var k = kind === "LIFT" ? "CAR_LIFT" : kind;
    if (k === "GATE") {
      return { kind: "GATE", label: "G", title: "Gate", css: " parking-plan__fixture--gate" };
    }
    if (k === "PASSENGER_LIFT") {
      return {
        kind: "PASSENGER_LIFT",
        label: "PL",
        subtitle: "Passenger",
        title: "Passenger lift",
        css: " parking-plan__fixture--passenger-lift",
      };
    }
    return {
      kind: k === "CAR_LIFT" ? "CAR_LIFT" : "CAR_LIFT",
      label: "CL",
      subtitle: "Car lift",
      title: "Car lift",
      css: " parking-plan__fixture--car-lift",
    };
  }

  function renderParkingFixtureIcon(kind) {
    if (kind === "PASSENGER_LIFT") {
      return (
        '<svg class="parking-plan__fixture-icon" viewBox="0 0 24 24" aria-hidden="true">' +
        '<path d="M6 4h12v16H6z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>' +
        '<path d="M9 7h6M9 10.5h6M9 14h6M9 17.5h4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>' +
        '<path d="M12 2.5v2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>' +
        "</svg>"
      );
    }
    if (kind === "CAR_LIFT") {
      return (
        '<svg class="parking-plan__fixture-icon" viewBox="0 0 24 24" aria-hidden="true">' +
        '<rect x="7" y="4.5" width="10" height="15" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.5"/>' +
        '<path d="M9.5 8h5M9.5 11.5h5M9.5 15h5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>' +
        '<path d="M12 2.2v2.1M9.4 4.3l1.8 1M14.6 4.3l-1.8 1" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>' +
        "</svg>"
      );
    }
    return "";
  }

  function renderParkingPlanFixture(placement, canEdit) {
    if (!placement) return "";
    var ui = parkingFixtureUiMeta(placement.kind);
    var orientClass =
      placement.orientation === "horizontal"
        ? " parking-plan__fixture--horizontal"
        : " parking-plan__fixture--vertical";
    var dragClass = canEdit ? " parking-plan__fixture--draggable" : "";
    var gridStyle =
      ' style="grid-column:' +
      (placement.col + 1) +
      ";grid-row:" +
      (placement.row + 1) +
      '"';
    return (
      '<div class="parking-plan__fixture' +
      ui.css +
      orientClass +
      dragClass +
      '" data-fixture-kind="' +
      ui.kind +
      '" data-fixture-index="' +
      placement.index +
      '"' +
      (canEdit ? ' draggable="true"' : "") +
      ' title="' +
      ui.title +
      " " +
      placement.index +
      " (shared)" +
      '"' +
      gridStyle +
      ">" +
      renderParkingFixtureIcon(ui.kind) +
      '<span class="parking-plan__fixture-label">' +
      ui.label +
      placement.index +
      "</span>" +
      (ui.subtitle
        ? '<span class="parking-plan__fixture-sub">' + ui.subtitle + "</span>"
        : "") +
      "</div>"
    );
  }

  function parkingSectionMetaDisplay(floor) {
    return parkingSectionConfigured(floor) ? parkingSectionMetaText(floor) : "Not configured";
  }

  function parkingLayoutImageUrl(floorNumber) {
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : "";
    if (!buildingId || !floorNumber) return "";
    return (
      appRoot() +
      "/buildings/" +
      buildingId +
      "/parking-layout-image/" +
      encodeURIComponent(floorNumber) +
      "?t=" +
      Date.now()
    );
  }

  function buildParkingLayoutLinksHtml(floor) {
    var admin = isPlatformAdminEdit();
    var hasImage = !!(floor && floor.parkingHasLayoutImage);
    if (!admin && !hasImage) return "";
    var upload = admin
      ? '<button type="button" class="flat-parking-layout-upload-link btn btn-link btn-sm px-0">Upload layout</button>'
      : "";
    var view = hasImage
      ? '<button type="button" class="flat-parking-layout-view-link btn btn-link btn-sm px-0">View layout</button>'
      : "";
    if (!upload && !view) return "";
    return (
      '<div class="flat-parking-section__layout-links d-flex flex-wrap align-items-center gap-2">' +
      upload +
      view +
      "</div>"
    );
  }

  function refreshParkingLayoutLinks(sectionEl) {
    if (!sectionEl) return;
    var floorNumber = sectionEl.getAttribute("data-floor-number");
    var hasImage = sectionEl.getAttribute("data-layout-image") === "true";
    var floor = {
      floorNumber: floorNumber,
      parkingHasLayoutImage: hasImage,
    };
    var html = buildParkingLayoutLinksHtml(floor);
    var existing = sectionEl.querySelector(".flat-parking-section__layout-links");
    if (existing) {
      if (html) {
        existing.outerHTML = html;
      } else {
        existing.remove();
      }
      return;
    }
    if (!html) return;
    var configure = sectionEl.querySelector(".flat-parking-configure-link");
    if (configure && configure.parentNode) {
      configure.insertAdjacentHTML("afterend", html);
    } else {
      var summary = sectionEl.querySelector(".flat-parking-section__summary");
      if (summary) summary.insertAdjacentHTML("beforeend", html);
    }
  }

  function buildParkingSectionInnerHtml(floor) {
    var note = parkingSectionConfigured(floor)
      ? ""
      : '<p class="flat-parking-section__note small text-muted mb-0">Set the number of parking slots for this floor.</p>';
    var configureLink = isPlatformAdminEdit()
      ? '<button type="button" class="flat-parking-configure-link btn btn-link btn-sm px-0">Configure</button>'
      : "";
    return (
      '<div class="flat-parking-section__layout">' +
      '<div class="flat-parking-section__summary">' +
      '<div class="flat-parking-section__head">' +
      '<span class="flat-parking-section__title">Parking</span>' +
      '<span class="flat-parking-section__meta">' +
      parkingSectionMetaDisplay(floor) +
      "</span>" +
      "</div>" +
      note +
      configureLink +
      buildParkingLayoutLinksHtml(floor) +
      "</div>" +
      '<div class="flat-parking-section__plan" aria-hidden="true">' +
      '<div class="parking-plan flat-parking-section__plan-root" data-floor-number="' +
      floor.floorNumber +
      '"></div>' +
      "</div>" +
      "</div>"
    );
  }

  function parkingSectionForFloor(floorNumber) {
    return document.querySelector('.flat-parking-section[data-floor-number="' + floorNumber + '"]');
  }

  function parkingPlanRootForSection(sectionEl) {
    if (!sectionEl) return null;
    return sectionEl.querySelector(".flat-parking-section__plan-root");
  }

  function parkingFloorSnapshot(floor) {
    var first = floor.flats && floor.flats[0];
    return [
      floor.floorNumber,
      floor.parkingSlotCount || 0,
      floor.parkingRangeLabel || "",
      parkingSectionConfigured(floor) ? "1" : "0",
      floor.parkingCarSizePercent != null ? floor.parkingCarSizePercent : DEFAULT_PARKING_CAR_SIZE_PERCENT,
      String(floor.parkingCarLiftCount != null ? floor.parkingCarLiftCount : 0),
      String(floor.parkingPassengerLiftCount != null ? floor.parkingPassengerLiftCount : 0),
      String(floor.parkingGateCount != null ? floor.parkingGateCount : 0),
      floor.parkingGridRows != null ? floor.parkingGridRows : parkingMinGridRowsForSlotCount(floor.parkingSlotCount || 1),
      first ? first.id : "",
      first && first.areaSqft != null ? first.areaSqft : "",
      first && first.basePrice != null ? first.basePrice : "",
      floor.parkingHasLayoutImage ? "1" : "0",
    ].join("|");
  }

  function parkingPlanLinkSignature(plan) {
    if (!plan || !plan.slots) return "";
    var links = plan.slots
      .map(function (s) {
        return s.slotNumber + ":" + (s.linkedResidentialFlatId || "");
      })
      .join("|");
    var layout = (plan.placements || [])
      .map(function (p) {
        return (
          p.slotNumber +
          "@" +
          p.col +
          "," +
          p.row +
          ":" +
          (p.orientation || "vertical")
        );
      })
      .join("|");
    return links + "||" + layout + "||" + (plan.gridCols || "") + "x" + (plan.gridRows || "") + "||" + (plan.carSizePercent || DEFAULT_PARKING_CAR_SIZE_PERCENT);
  }

  function findPlanPlacement(plan, slotNumber) {
    if (!plan || !plan.placements) return null;
    for (var i = 0; i < plan.placements.length; i++) {
      if (plan.placements[i].slotNumber === slotNumber) return plan.placements[i];
    }
    return null;
  }

  function cloneParkingPlan(plan) {
    return {
      floorNumber: plan.floorNumber,
      slotCount: plan.slotCount,
      topRow: plan.topRow ? plan.topRow.slice() : [],
      bottomRow: plan.bottomRow ? plan.bottomRow.slice() : [],
      slots: plan.slots ? plan.slots.slice() : [],
      gridCols: plan.gridCols,
      gridRows: plan.gridRows,
      gridLayout: plan.gridLayout,
      carSizePercent: plan.carSizePercent != null ? plan.carSizePercent : DEFAULT_PARKING_CAR_SIZE_PERCENT,
      minGridRows: plan.minGridRows != null ? plan.minGridRows : parkingMinGridRowsForSlotCount(plan.slotCount || 0),
      placements: (plan.placements || []).map(function (p) {
        return {
          slotNumber: p.slotNumber,
          col: p.col,
          row: p.row,
          orientation: p.orientation || "vertical",
        };
      }),
      fixtures: (plan.fixtures || []).map(function (f) {
        var kind = f.kind === "LIFT" ? "CAR_LIFT" : f.kind;
        return {
          kind: kind,
          index: f.index,
          col: f.col,
          row: f.row,
          orientation: f.orientation || "vertical",
        };
      }),
      carLiftCount: plan.carLiftCount != null ? plan.carLiftCount : 0,
      passengerLiftCount: plan.passengerLiftCount != null ? plan.passengerLiftCount : 0,
      liftCount: plan.liftCount != null ? plan.liftCount : 0,
      gateCount: plan.gateCount != null ? plan.gateCount : 0,
    };
  }

  function renderParkingPlanSlot(slot, canLink, placement) {
    if (!slot) return "";
    var slotNumber = slot.slotNumber;
    var flatNumber = slot.flatNumber || "";
    var linked = slot.linkedResidentialFlatNumber || "";
    var linkedClass = linked ? " parking-plan__slot--linked" : "";
    var clickable = canLink ? " parking-plan__slot--clickable" : "";
    var orientClass =
      placement && placement.orientation === "horizontal"
        ? " parking-plan__slot--horizontal"
        : " parking-plan__slot--vertical";
    var dragClass = canLink ? " parking-plan__slot--draggable" : "";
    var areaLabel =
      slot.areaSqft != null && slot.areaSqft !== ""
        ? " · " + formatAreaForDisplay(slot.areaSqft) + " " + areaUnitSuffix()
        : "";
    var title = linked
      ? "Slot " + slotNumber + " — linked to flat " + linked + areaLabel
      : canLink
        ? "Slot " + slotNumber + " — click to edit area or link" + areaLabel
        : flatNumber
          ? "Unit " + flatNumber + areaLabel
          : "Slot " + slotNumber + areaLabel;
    var gridStyle = placement
      ? ' style="grid-column:' +
        (placement.col + 1) +
        ";grid-row:" +
        (placement.row + 1) +
        '"'
      : "";
    return (
      '<div class="parking-plan__slot' +
      linkedClass +
      clickable +
      orientClass +
      dragClass +
      '" data-parking-flat-id="' +
      (slot.flatId || "") +
      '" data-slot-number="' +
      slotNumber +
      '" data-flat-number="' +
      (flatNumber || "") +
      '" data-area="' +
      (slot.areaSqft != null ? slot.areaSqft : "") +
      '"' +
      (slot.linkedResidentialFlatId
        ? ' data-linked-flat-id="' + slot.linkedResidentialFlatId + '"'
        : "") +
      (linked ? ' data-linked-flat-number="' + linked + '"' : "") +
      ' title="' +
      title.replace(/"/g, "&quot;") +
      '"' +
      (canLink ? ' draggable="true"' : "") +
      gridStyle +
      ">" +
      '<div class="parking-plan__bay">' +
      renderParkingCarSvg(linked) +
      (linked ? '<span class="parking-plan__slot-flat visually-hidden">' + linked + "</span>" : "") +
      "</div>" +
      '<span class="parking-plan__slot-no' +
      (linked ? " parking-plan__slot-no--booked" : "") +
      '">' +
      slotNumber +
      "</span>" +
      "</div>"
    );
  }

  function parkingCarScale(plan) {
    var pct = plan && plan.carSizePercent != null ? plan.carSizePercent : DEFAULT_PARKING_CAR_SIZE_PERCENT;
    return Math.max(0.5, Math.min(2, pct / 100));
  }

  function renderParkingPlanGrid(plan, rootEl, canEdit) {
    var cols = plan.gridCols || 14;
    var rows = plan.gridRows || 8;
    var cellsHtml = "";
    var r;
    var c;
    for (r = 0; r < rows; r++) {
      for (c = 0; c < cols; c++) {
        cellsHtml +=
          '<div class="parking-plan__cell' +
          (canEdit ? " parking-plan__cell--drop" : "") +
          '" data-col="' +
          c +
          '" data-row="' +
          r +
          '" style="grid-column:' +
          (c + 1) +
          ";grid-row:" +
          (r + 1) +
          '"></div>';
      }
    }
    var slotsHtml = (plan.placements || [])
      .map(function (p) {
        return renderParkingPlanSlot(findPlanSlot(plan, p.slotNumber), canEdit, p);
      })
      .join("");
    var fixturesHtml = (plan.fixtures || [])
      .map(function (f) {
        return renderParkingPlanFixture(f, canEdit);
      })
      .join("");
    var toolbar = canEdit
      ? '<div class="parking-plan__layout-toolbar">' +
        '<div class="parking-plan__grid-toolbar">' +
        '<div class="parking-plan__row-toolbar btn-group btn-group-sm" role="group" aria-label="Grid rows">' +
        '<button type="button" class="btn btn-outline-secondary parking-plan__grid-btn" data-parking-row-action="INSERT_TOP" title="Insert row at top">+ Top</button>' +
        '<button type="button" class="btn btn-outline-secondary parking-plan__grid-btn" data-parking-row-action="INSERT_BOTTOM" title="Insert row at bottom">+ Bottom</button>' +
        '<button type="button" class="btn btn-outline-secondary parking-plan__grid-btn" data-parking-row-action="REMOVE_TOP" title="Remove empty row from top">− Top</button>' +
        '<button type="button" class="btn btn-outline-secondary parking-plan__grid-btn" data-parking-row-action="REMOVE_BOTTOM" title="Remove empty row from bottom">− Bottom</button>' +
        "</div>" +
        '<div class="parking-plan__col-toolbar btn-group btn-group-sm" role="group" aria-label="Grid columns">' +
        '<button type="button" class="btn btn-outline-secondary parking-plan__grid-btn" data-parking-col-action="INSERT_LEFT" title="Insert column on left">+ Left</button>' +
        '<button type="button" class="btn btn-outline-secondary parking-plan__grid-btn" data-parking-col-action="INSERT_RIGHT" title="Insert column on right">+ Right</button>' +
        '<button type="button" class="btn btn-outline-secondary parking-plan__grid-btn" data-parking-col-action="REMOVE_LEFT" title="Remove empty column from left">− Left</button>' +
        '<button type="button" class="btn btn-outline-secondary parking-plan__grid-btn" data-parking-col-action="REMOVE_RIGHT" title="Remove empty column from right">− Right</button>' +
        "</div>" +
        "</div>" +
        '<span class="text-muted small parking-plan__layout-hint">Drag cars, car lifts (CL), passenger lifts (PL), and gates (G) to grid cells. Click a car to link or rotate its bay. Fixtures are shared (not bookable). Only empty rows and columns can be removed.</span>' +
        '<span class="text-danger small parking-plan__layout-error d-none"></span>' +
        "</div>"
      : "";
    rootEl.innerHTML =
      '<div class="parking-plan__sheet parking-plan__sheet--grid" style="--parking-car-scale:' +
      parkingCarScale(plan) +
      '">' +
      toolbar +
      '<div class="parking-plan__grid" style="--parking-grid-cols:' +
      cols +
      ";--parking-grid-rows:" +
      rows +
      '">' +
      cellsHtml +
      slotsHtml +
      fixturesHtml +
      "</div></div>";
    rootEl._parkingLayoutState = {
      floorNumber: plan.floorNumber,
      gridCols: cols,
      gridRows: rows,
      placements: (plan.placements || []).map(function (p) {
        return {
          slotNumber: p.slotNumber,
          col: p.col,
          row: p.row,
          orientation: p.orientation || "vertical",
        };
      }),
      fixtures: (plan.fixtures || []).map(function (f) {
        var kind = f.kind === "LIFT" ? "CAR_LIFT" : f.kind;
        return {
          kind: kind,
          index: f.index,
          col: f.col,
          row: f.row,
          orientation: f.orientation || "vertical",
        };
      }),
      saving: false,
      plan: cloneParkingPlan(plan),
    };
    if (canEdit) updateParkingGridToolbar(rootEl);
  }

  function showParkingLayoutError(rootEl, message) {
    var el = rootEl ? rootEl.querySelector(".parking-plan__layout-error") : null;
    if (!el) return;
    if (!message) {
      el.textContent = "";
      el.classList.add("d-none");
      return;
    }
    el.textContent = message;
    el.classList.remove("d-none");
  }

  function rerenderParkingPlanFromState(rootEl) {
    var state = rootEl && rootEl._parkingLayoutState;
    if (!state || !state.plan) return;
    var plan = cloneParkingPlan(state.plan);
    plan.placements = state.placements.map(function (p) {
      return {
        slotNumber: p.slotNumber,
        col: p.col,
        row: p.row,
        orientation: p.orientation || "vertical",
      };
    });
    plan.fixtures = (state.fixtures || []).map(function (f) {
      return {
        kind: f.kind,
        index: f.index,
        col: f.col,
        row: f.row,
        orientation: f.orientation || "vertical",
      };
    });
    renderParkingPlanGrid(plan, rootEl, isPlatformAdminEdit());
  }

  function findGridOccupant(state, col, row, excludeDrag) {
    var i;
    excludeDrag = excludeDrag || {};
    for (i = 0; i < state.placements.length; i++) {
      var p = state.placements[i];
      if (Number(p.col) === col && Number(p.row) === row) {
        if (excludeDrag.type === "slot" && Number(excludeDrag.slotNumber) === Number(p.slotNumber)) {
          continue;
        }
        return { type: "slot", item: p };
      }
    }
    var fixtures = state.fixtures || [];
    for (i = 0; i < fixtures.length; i++) {
      var f = fixtures[i];
      if (Number(f.col) === col && Number(f.row) === row) {
        if (
          excludeDrag.type === "fixture" &&
          excludeDrag.kind === f.kind &&
          Number(excludeDrag.index) === Number(f.index)
        ) {
          continue;
        }
        return { type: "fixture", item: f };
      }
    }
    return null;
  }

  function findMovingItem(state, drag) {
    var i;
    if (drag.type === "slot") {
      for (i = 0; i < state.placements.length; i++) {
        if (Number(state.placements[i].slotNumber) === Number(drag.slotNumber)) {
          return { type: "slot", item: state.placements[i] };
        }
      }
      return null;
    }
    var fixtures = state.fixtures || [];
    for (i = 0; i < fixtures.length; i++) {
      if (fixtures[i].kind === drag.kind && Number(fixtures[i].index) === Number(drag.index)) {
        return { type: "fixture", item: fixtures[i] };
      }
    }
    return null;
  }

  function moveParkingItemOnGrid(state, drag, toCol, toRow) {
    toCol = Number(toCol);
    toRow = Number(toRow);
    var movingWrap = findMovingItem(state, drag);
    if (!movingWrap) return false;
    var moving = movingWrap.item;
    if (Number(moving.col) === toCol && Number(moving.row) === toRow) return false;
    var occupantWrap = findGridOccupant(state, toCol, toRow, drag);
    if (occupantWrap) {
      var oldCol = moving.col;
      var oldRow = moving.row;
      moving.col = toCol;
      moving.row = toRow;
      occupantWrap.item.col = oldCol;
      occupantWrap.item.row = oldRow;
    } else {
      moving.col = toCol;
      moving.row = toRow;
    }
    return true;
  }

  function parkingDropTargetFromEvent(e) {
    var cell = e.target.closest(".parking-plan__cell--drop");
    if (cell) {
      return {
        root: cell.closest(".flat-parking-section__plan-root"),
        col: Number(cell.dataset.col),
        row: Number(cell.dataset.row),
        highlightEl: cell,
      };
    }
    var grid = e.target.closest(".parking-plan__grid");
    if (grid) {
      var cells = grid.querySelectorAll(".parking-plan__cell--drop");
      var i;
      for (i = 0; i < cells.length; i++) {
        var rect = cells[i].getBoundingClientRect();
        if (
          e.clientX >= rect.left &&
          e.clientX <= rect.right &&
          e.clientY >= rect.top &&
          e.clientY <= rect.bottom
        ) {
          return {
            root: grid.closest(".flat-parking-section__plan-root"),
            col: Number(cells[i].dataset.col),
            row: Number(cells[i].dataset.row),
            highlightEl: cells[i],
          };
        }
      }
    }
    var slot = e.target.closest(".parking-plan__slot--draggable");
    if (slot) {
      var rootSlot = slot.closest(".flat-parking-section__plan-root");
      var stateSlot = rootSlot && rootSlot._parkingLayoutState;
      if (!stateSlot) return null;
      var targetSlot = Number(slot.getAttribute("data-slot-number"));
      for (i = 0; i < stateSlot.placements.length; i++) {
        if (Number(stateSlot.placements[i].slotNumber) === targetSlot) {
          return {
            root: rootSlot,
            col: Number(stateSlot.placements[i].col),
            row: Number(stateSlot.placements[i].row),
            highlightEl: null,
          };
        }
      }
    }
    var fixture = e.target.closest(".parking-plan__fixture--draggable");
    if (fixture) {
      var rootFx = fixture.closest(".flat-parking-section__plan-root");
      var stateFx = rootFx && rootFx._parkingLayoutState;
      if (!stateFx) return null;
      var kind = fixture.getAttribute("data-fixture-kind");
      var index = Number(fixture.getAttribute("data-fixture-index"));
      var fixtures = stateFx.fixtures || [];
      for (i = 0; i < fixtures.length; i++) {
        if (fixtures[i].kind === kind && Number(fixtures[i].index) === index) {
          return {
            root: rootFx,
            col: Number(fixtures[i].col),
            row: Number(fixtures[i].row),
            highlightEl: null,
          };
        }
      }
    }
    return null;
  }

  function clearParkingCellDragOver() {
    document.querySelectorAll(".parking-plan__cell--drag-over").forEach(function (cell) {
      cell.classList.remove("parking-plan__cell--drag-over");
    });
  }

  function setParkingGridDragActive(root, active) {
    if (!root) return;
    var grid = root.querySelector(".parking-plan__grid");
    if (grid) grid.classList.toggle("parking-plan__grid--dragging", !!active);
  }

  function toggleParkingSlotOrientation(state, slotNumber) {
    var i;
    for (i = 0; i < state.placements.length; i++) {
      if (state.placements[i].slotNumber === slotNumber) {
        state.placements[i].orientation =
          state.placements[i].orientation === "horizontal" ? "vertical" : "horizontal";
        return;
      }
    }
  }

  async function autoSaveParkingLayout(rootEl) {
    var state = rootEl && rootEl._parkingLayoutState;
    if (!state || state.saving) return;
    state.saving = true;
    showParkingLayoutError(rootEl, "");
    var result = await persistParkingLayout(rootEl);
    state.saving = false;
    if (!result.ok) {
      showParkingLayoutError(rootEl, result.error);
    }
    return result;
  }

  async function persistParkingLayout(rootEl) {
    var state = rootEl && rootEl._parkingLayoutState;
    if (!state) return { ok: false, error: "Layout is not available." };
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    if (!buildingId) return { ok: false, error: "Building not found." };
    showParkingLayoutError(rootEl, "");
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    var res = await fetch(
      appRoot() +
        "/buildings/" +
        buildingId +
        "/flats/floor/" +
        encodeURIComponent(state.floorNumber) +
        "/parking-layout",
      {
        method: "POST",
        headers: headers,
        body: JSON.stringify({
          gridCols: state.gridCols,
          gridRows: state.gridRows,
          placements: state.placements,
          fixtures: state.fixtures || [],
        }),
      }
    );
    if (!res.ok) {
      return { ok: false, error: await parseErrorResponse(res) };
    }
    var plan = await res.json();
    state.plan = cloneParkingPlan(plan);
    state.placements = (plan.placements || []).map(function (p) {
      return {
        slotNumber: p.slotNumber,
        col: p.col,
        row: p.row,
        orientation: p.orientation || "vertical",
      };
    });
    state.fixtures = (plan.fixtures || []).map(function (f) {
      return {
        kind: f.kind,
        index: f.index,
        col: f.col,
        row: f.row,
        orientation: f.orientation || "vertical",
      };
    });
    invalidateParkingPlanCache(state.floorNumber);
    updateParkingGridToolbar(rootEl);
    return { ok: true };
  }

  function ensureParkingGridDelegation() {
    if (window.__f21ParkingGridBound) return;
    window.__f21ParkingGridBound = true;

    document.addEventListener("dragstart", function (e) {
      var slot = e.target.closest(".parking-plan__slot--draggable");
      var fixture = e.target.closest(".parking-plan__fixture--draggable");
      var dragEl = slot || fixture;
      if (!dragEl) return;
      var payload = slot
        ? slot.getAttribute("data-slot-number") || ""
        : parkingFixtureDragKey(
            fixture.getAttribute("data-fixture-kind"),
            fixture.getAttribute("data-fixture-index")
          );
      e.dataTransfer.setData("text/plain", payload);
      e.dataTransfer.effectAllowed = "move";
      dragEl.classList.add(
        slot ? "parking-plan__slot--dragging" : "parking-plan__fixture--dragging"
      );
      setParkingGridDragActive(dragEl.closest(".flat-parking-section__plan-root"), true);
    });

    document.addEventListener("dragend", function (e) {
      var slot = e.target.closest(".parking-plan__slot--draggable");
      var fixture = e.target.closest(".parking-plan__fixture--draggable");
      if (slot) {
        slot.classList.remove("parking-plan__slot--dragging");
        setParkingGridDragActive(slot.closest(".flat-parking-section__plan-root"), false);
      }
      if (fixture) {
        fixture.classList.remove("parking-plan__fixture--dragging");
        setParkingGridDragActive(fixture.closest(".flat-parking-section__plan-root"), false);
      }
      clearParkingCellDragOver();
    });

    document.addEventListener("dragover", function (e) {
      var target = parkingDropTargetFromEvent(e);
      if (!target || !target.highlightEl) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = "move";
      clearParkingCellDragOver();
      target.highlightEl.classList.add("parking-plan__cell--drag-over");
    });

    document.addEventListener("dragleave", function (e) {
      var cell = e.target.closest(".parking-plan__cell--drop");
      if (cell) cell.classList.remove("parking-plan__cell--drag-over");
    });

    document.addEventListener("drop", function (e) {
      var target = parkingDropTargetFromEvent(e);
      if (!target) return;
      e.preventDefault();
      clearParkingCellDragOver();
      var root = target.root;
      var state = root && root._parkingLayoutState;
      if (!state) return;
      var drag = parseParkingDragKey(e.dataTransfer.getData("text/plain"));
      if (!drag) return;
      if (!moveParkingItemOnGrid(state, drag, target.col, target.row)) {
        return;
      }
      rerenderParkingPlanFromState(root);
      setParkingGridDragActive(root, false);
      void autoSaveParkingLayout(root);
    });

    document.addEventListener("click", function (e) {
      var rowBtn = e.target.closest("[data-parking-row-action]");
      if (rowBtn && !rowBtn.disabled) {
        var rowRoot = rowBtn.closest(".flat-parking-section__plan-root");
        if (!rowRoot || !isPlatformAdminEdit()) return;
        var rowAction = rowBtn.getAttribute("data-parking-row-action");
        if (!rowAction) return;
        void adjustParkingGridRow(rowRoot, rowAction);
        return;
      }
      var colBtn = e.target.closest("[data-parking-col-action]");
      if (!colBtn || colBtn.disabled) return;
      var colRoot = colBtn.closest(".flat-parking-section__plan-root");
      if (!colRoot || !isPlatformAdminEdit()) return;
      var colAction = colBtn.getAttribute("data-parking-col-action");
      if (!colAction) return;
      void adjustParkingGridCol(colRoot, colAction);
    });
  }

  function renderParkingPlan(plan, rootEl) {
    if (!rootEl || !plan) return;
    var canEdit = isPlatformAdminEdit();
    if (plan.gridLayout && plan.placements && plan.placements.length) {
      renderParkingPlanGrid(plan, rootEl, canEdit);
      return;
    }
    var topHtml = (plan.topRow || [])
      .map(function (n) {
        return renderParkingPlanSlot(findPlanSlot(plan, n), canEdit, findPlanPlacement(plan, n));
      })
      .join("");
    var bottomHtml = (plan.bottomRow || [])
      .map(function (n) {
        return renderParkingPlanSlot(findPlanSlot(plan, n), canEdit, findPlanPlacement(plan, n));
      })
      .join("");
    rootEl.innerHTML =
      '<div class="parking-plan__sheet" style="--parking-car-scale:' +
      parkingCarScale(plan) +
      '">' +
      '<div class="parking-plan__row parking-plan__row--top">' +
      topHtml +
      "</div>" +
      '<div class="parking-plan__aisle" aria-hidden="true"></div>' +
      '<div class="parking-plan__row parking-plan__row--bottom">' +
      bottomHtml +
      "</div></div>";
  }

  function showParkingPlanInSection(plan, force) {
    if (!plan) return;
    var section = parkingSectionForFloor(plan.floorNumber);
    if (!section) return;
    var root = parkingPlanRootForSection(section);
    var linkSig = parkingPlanLinkSignature(plan);
    if (
      !force &&
      root &&
      root.dataset.loadedSlots === String(plan.slotCount) &&
      root.dataset.loadedLinks === linkSig &&
      root.querySelector(".parking-plan__sheet")
    ) {
      section.classList.add("flat-parking-section--split");
      var planPane = section.querySelector(".flat-parking-section__plan");
      if (planPane) planPane.setAttribute("aria-hidden", "false");
      return;
    }
    renderParkingPlan(plan, root);
    if (root) {
      root.dataset.loadedSlots = String(plan.slotCount);
      root.dataset.loadedLinks = linkSig;
    }
    section.dataset.carSizePercent = String(plan.carSizePercent != null ? plan.carSizePercent : DEFAULT_PARKING_CAR_SIZE_PERCENT);
    section.dataset.gridRows = String(plan.gridRows != null ? plan.gridRows : parkingMinGridRowsForSlotCount(plan.slotCount || 0));
    section.dataset.minGridRows = String(
      plan.minGridRows != null ? plan.minGridRows : parkingMinGridRowsForSlotCount(plan.slotCount || 0)
    );
    section.classList.add("flat-parking-section--split");
    var planPane = section.querySelector(".flat-parking-section__plan");
    if (planPane) planPane.setAttribute("aria-hidden", "false");
  }

  async function loadAllConfiguredParkingPlans() {
    var sections = document.querySelectorAll('.flat-parking-section[data-configured="true"]');
    var tasks = [];
    sections.forEach(function (section) {
      var fn = section.dataset.floorNumber;
      if (!fn) return;
      var root = parkingPlanRootForSection(section);
      if (
        root &&
        root.dataset.loadedSlots === section.dataset.slotCount &&
        root.querySelector(".parking-plan__sheet")
      ) {
        return;
      }
      tasks.push(
        fetchParkingPlan(fn).then(function (plan) {
          if (plan) showParkingPlanInSection(plan);
        })
      );
    });
    await Promise.all(tasks);
  }

  function showParkingConfigError(message) {
    var el = document.getElementById("parking-config-error");
    if (!el) return;
    if (!message) {
      el.textContent = "";
      el.classList.add("d-none");
      return;
    }
    el.textContent = message;
    el.classList.remove("d-none");
  }

  function findPlanSlot(plan, slotNumber) {
    if (!plan || !plan.slots) return null;
    for (var i = 0; i < plan.slots.length; i++) {
      if (plan.slots[i].slotNumber === slotNumber) return plan.slots[i];
    }
    return null;
  }

  function slotFlatNumber(plan, slotNumber) {
    if (!plan || !plan.slots) return "";
    for (var i = 0; i < plan.slots.length; i++) {
      if (plan.slots[i].slotNumber === slotNumber) {
        return plan.slots[i].flatNumber || "";
      }
    }
    return "";
  }

  function parkingEscapeXml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderParkingCarSvg(linkedFlat) {
    var labelSvg = "";
    if (linkedFlat) {
      var label = parkingEscapeXml(linkedFlat);
      var fontSize = String(linkedFlat).length > 4 ? "6.5" : "8";
      labelSvg =
        '<rect class="parking-plan__car-label-bg" x="13" y="45" width="24" height="14" rx="3"/>' +
        '<text class="parking-plan__car-label-text" x="25" y="55" text-anchor="middle" font-size="' +
        fontSize +
        '" font-weight="700">' +
        label +
        "</text>";
    }
    return (
      '<svg class="parking-plan__car-svg" viewBox="0 0 50 108" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
      '<ellipse class="parking-plan__car-wheel" cx="7" cy="28" rx="7" ry="9"/>' +
      '<ellipse class="parking-plan__car-wheel" cx="43" cy="28" rx="7" ry="9"/>' +
      '<ellipse class="parking-plan__car-wheel" cx="7" cy="82" rx="7" ry="9"/>' +
      '<ellipse class="parking-plan__car-wheel" cx="43" cy="82" rx="7" ry="9"/>' +
      '<rect class="parking-plan__car-shell" x="6" y="10" width="38" height="88" rx="10"/>' +
      '<ellipse class="parking-plan__car-hood" cx="25" cy="14" rx="16" ry="7"/>' +
      '<rect class="parking-plan__car-glass-front" x="10" y="18" width="30" height="20" rx="5"/>' +
      '<rect class="parking-plan__car-cabin" x="11" y="40" width="28" height="26" rx="4"/>' +
      '<rect class="parking-plan__car-glass-rear" x="10" y="68" width="30" height="16" rx="5"/>' +
      '<rect class="parking-plan__car-light-front" x="8" y="11" width="9" height="5" rx="2"/>' +
      '<rect class="parking-plan__car-light-front" x="33" y="11" width="9" height="5" rx="2"/>' +
      '<rect class="parking-plan__car-light-rear" x="8" y="90" width="9" height="5" rx="2"/>' +
      '<rect class="parking-plan__car-light-rear" x="33" y="90" width="9" height="5" rx="2"/>' +
      '<rect class="parking-plan__car-bumper" x="15" y="97" width="20" height="4" rx="1"/>' +
      labelSvg +
      "</svg>"
    );
  }

  async function fetchParkingPlan(floorNumber) {
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    if (!buildingId || !floorNumber) return null;
    var res = await fetch(
      appRoot() + "/buildings/" + buildingId + "/flats/floor/" + encodeURIComponent(floorNumber) + "/parking-plan",
      { headers: { Accept: "application/json" } }
    );
    if (!res.ok) return null;
    return res.json();
  }

  function parkingMinGridRowsForSlotCount(slotCount) {
    slotCount = Number(slotCount) || 0;
    if (slotCount <= 0) return 1;
    var bottomCount = Math.ceil(slotCount / 2);
    var topCount = slotCount - bottomCount;
    if (topCount > 0 && bottomCount > 0) return 3;
    return 1;
  }

  var PARKING_MAX_GRID_ROWS = 24;
  var PARKING_MAX_GRID_COLS = 40;

  function parkingMinGridColsForSlotCount(slotCount) {
    slotCount = Number(slotCount) || 0;
    if (slotCount <= 0) return 1;
    var bottomCount = Math.ceil(slotCount / 2);
    var topCount = slotCount - bottomCount;
    return Math.max(1, Math.max(bottomCount, topCount));
  }

  function parkingRowHasPlacement(state, row) {
    if (!state) return false;
    var i;
    if (state.placements) {
      for (i = 0; i < state.placements.length; i++) {
        if (state.placements[i].row === row) return true;
      }
    }
    if (state.fixtures) {
      for (i = 0; i < state.fixtures.length; i++) {
        if (state.fixtures[i].row === row) return true;
      }
    }
    return false;
  }

  function parkingColHasPlacement(state, col) {
    if (!state) return false;
    var i;
    if (state.placements) {
      for (i = 0; i < state.placements.length; i++) {
        if (state.placements[i].col === col) return true;
      }
    }
    if (state.fixtures) {
      for (i = 0; i < state.fixtures.length; i++) {
        if (state.fixtures[i].col === col) return true;
      }
    }
    return false;
  }

  function updateParkingGridToolbar(rootEl) {
    if (!rootEl) return;
    var state = rootEl._parkingLayoutState;
    if (!state) return;
    var slotCount = state.plan && state.plan.slotCount ? state.plan.slotCount : 0;
    var minRows = parkingMinGridRowsForSlotCount(slotCount);
    if (state.plan && state.plan.minGridRows != null) {
      minRows = state.plan.minGridRows;
    }
    var minCols = parkingMinGridColsForSlotCount(slotCount);
    var rows = state.gridRows;
    var cols = state.gridCols;
    var removeTop = rootEl.querySelector('[data-parking-row-action="REMOVE_TOP"]');
    var removeBottom = rootEl.querySelector('[data-parking-row-action="REMOVE_BOTTOM"]');
    var insertTop = rootEl.querySelector('[data-parking-row-action="INSERT_TOP"]');
    var insertBottom = rootEl.querySelector('[data-parking-row-action="INSERT_BOTTOM"]');
    var removeLeft = rootEl.querySelector('[data-parking-col-action="REMOVE_LEFT"]');
    var removeRight = rootEl.querySelector('[data-parking-col-action="REMOVE_RIGHT"]');
    var insertLeft = rootEl.querySelector('[data-parking-col-action="INSERT_LEFT"]');
    var insertRight = rootEl.querySelector('[data-parking-col-action="INSERT_RIGHT"]');
    var canRemoveTop = rows > minRows && !parkingRowHasPlacement(state, 0);
    var canRemoveBottom = rows > minRows && !parkingRowHasPlacement(state, rows - 1);
    var canInsertRow = rows < PARKING_MAX_GRID_ROWS;
    var canRemoveLeft = cols > minCols && !parkingColHasPlacement(state, 0);
    var canRemoveRight = cols > minCols && !parkingColHasPlacement(state, cols - 1);
    var canInsertCol = cols < PARKING_MAX_GRID_COLS;
    if (removeTop) removeTop.disabled = !canRemoveTop;
    if (removeBottom) removeBottom.disabled = !canRemoveBottom;
    if (insertTop) insertTop.disabled = !canInsertRow;
    if (insertBottom) insertBottom.disabled = !canInsertRow;
    if (removeLeft) removeLeft.disabled = !canRemoveLeft;
    if (removeRight) removeRight.disabled = !canRemoveRight;
    if (insertLeft) insertLeft.disabled = !canInsertCol;
    if (insertRight) insertRight.disabled = !canInsertCol;
  }

  async function adjustParkingGridRow(rootEl, action) {
    var state = rootEl && rootEl._parkingLayoutState;
    if (!state || state.saving) return;
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    if (!buildingId) return;
    state.saving = true;
    showParkingLayoutError(rootEl, "");
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    var res = await fetch(
      appRoot() +
        "/buildings/" +
        buildingId +
        "/flats/floor/" +
        encodeURIComponent(state.floorNumber) +
        "/parking-grid-row",
      {
        method: "POST",
        headers: headers,
        body: JSON.stringify({ action: action }),
      }
    );
    state.saving = false;
    if (!res.ok) {
      showParkingLayoutError(rootEl, await parseErrorResponse(res));
      return;
    }
    var plan = await res.json();
    invalidateParkingPlanCache(state.floorNumber);
    showParkingPlanInSection(plan, true);
  }

  async function adjustParkingGridCol(rootEl, action) {
    var state = rootEl && rootEl._parkingLayoutState;
    if (!state || state.saving) return;
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    if (!buildingId) return;
    state.saving = true;
    showParkingLayoutError(rootEl, "");
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    var res = await fetch(
      appRoot() +
        "/buildings/" +
        buildingId +
        "/flats/floor/" +
        encodeURIComponent(state.floorNumber) +
        "/parking-grid-col",
      {
        method: "POST",
        headers: headers,
        body: JSON.stringify({ action: action }),
      }
    );
    state.saving = false;
    if (!res.ok) {
      showParkingLayoutError(rootEl, await parseErrorResponse(res));
      return;
    }
    var plan = await res.json();
    invalidateParkingPlanCache(state.floorNumber);
    showParkingPlanInSection(plan, true);
  }

  function syncParkingConfigCarSizeLabel(value) {
    var label = document.getElementById("parking-config-car-size-value");
    if (label) label.textContent = String(value);
  }

  function openParkingConfigModal(sectionEl) {
    if (!sectionEl || !isPlatformAdminEdit()) return;
    mountModalsOnBody();
    parkingConfigFloorNumber = sectionEl.dataset.floorNumber;
    var modalEl = document.getElementById("parking-config-modal");
    var label = document.getElementById("parking-config-floor-label");
    var slots = document.getElementById("parking-config-slots");
    var carSize = document.getElementById("parking-config-car-size");
    var carLiftCount = document.getElementById("parking-config-car-lift-count");
    var passengerLiftCount = document.getElementById("parking-config-passenger-lift-count");
    var gateCount = document.getElementById("parking-config-gate-count");
    if (!modalEl || typeof bootstrap === "undefined" || !bootstrap.Modal) return;
    if (label) label.textContent = "Floor " + parkingConfigFloorNumber;
    var slotValue = sectionEl.dataset.slotCount || "4";
    if (slots) {
      slots.value = slotValue;
    }
    if (carSize) {
      var sizeValue = sectionEl.dataset.carSizePercent || String(DEFAULT_PARKING_CAR_SIZE_PERCENT);
      carSize.value = sizeValue;
      syncParkingConfigCarSizeLabel(sizeValue);
    }
    if (carLiftCount) {
      carLiftCount.value =
        sectionEl.dataset.carLiftCount != null ? sectionEl.dataset.carLiftCount : "1";
    }
    if (passengerLiftCount) {
      passengerLiftCount.value =
        sectionEl.dataset.passengerLiftCount != null
          ? sectionEl.dataset.passengerLiftCount
          : "0";
    }
    if (gateCount) {
      gateCount.value = sectionEl.dataset.gateCount != null ? sectionEl.dataset.gateCount : "1";
    }
    var parkingAreaUnit = document.getElementById("parking-config-area-unit");
    if (parkingAreaUnit) {
      parkingAreaUnit.value = getAreaUnit();
    }
    var parkingArea = document.getElementById("parking-config-area");
    if (parkingArea) {
      var sqftArea = sectionEl.dataset.area || "150";
      parkingArea.value = formatAreaForDisplay(sqftArea);
    }
    updateAreaUnitLabels();
    showParkingConfigError("");
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  async function saveParkingConfig() {
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    var slotsEl = document.getElementById("parking-config-slots");
    var carSizeEl = document.getElementById("parking-config-car-size");
    var carLiftCountEl = document.getElementById("parking-config-car-lift-count");
    var passengerLiftCountEl = document.getElementById("parking-config-passenger-lift-count");
    var gateCountEl = document.getElementById("parking-config-gate-count");
    if (!buildingId || !parkingConfigFloorNumber || !slotsEl) return;
    var slotCount = Number(slotsEl.value);
    if (!slotCount || slotCount < 1 || slotCount > 200) {
      showParkingConfigError("Enter a slot count between 1 and 200.");
      return;
    }
    var carSizePercent = carSizeEl ? Number(carSizeEl.value) : DEFAULT_PARKING_CAR_SIZE_PERCENT;
    if (!carSizePercent || carSizePercent < 50 || carSizePercent > 200) {
      showParkingConfigError("Car size must be between 50% and 200%.");
      return;
    }
    var carLifts = carLiftCountEl ? Number(carLiftCountEl.value) : 1;
    var passengerLifts = passengerLiftCountEl ? Number(passengerLiftCountEl.value) : 0;
    var gates = gateCountEl ? Number(gateCountEl.value) : 1;
    if (isNaN(carLifts) || carLifts < 0 || carLifts > 8) {
      showParkingConfigError("Car lift count must be between 0 and 8.");
      return;
    }
    if (isNaN(passengerLifts) || passengerLifts < 0 || passengerLifts > 8) {
      showParkingConfigError("Passenger lift count must be between 0 and 8.");
      return;
    }
    if (isNaN(gates) || gates < 0 || gates > 8) {
      showParkingConfigError("Gate count must be between 0 and 8.");
      return;
    }
    var slotAreaSqft = readParkingConfigAreaSqft();
    if (slotAreaSqft == null || slotAreaSqft <= 0) {
      showParkingConfigError("Enter a parking slot area greater than zero.");
      return;
    }
    showParkingConfigError("");
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    var res = await fetch(
      appRoot() +
        "/buildings/" +
        buildingId +
        "/flats/floor/" +
        encodeURIComponent(parkingConfigFloorNumber) +
        "/parking-config",
      {
        method: "POST",
        headers: headers,
        body: JSON.stringify({
          slotCount: slotCount,
          carSizePercent: carSizePercent,
          carLiftCount: carLifts,
          passengerLiftCount: passengerLifts,
          gateCount: gates,
          slotAreaSqft: slotAreaSqft,
        }),
      }
    );
    if (!res.ok) {
      showParkingConfigError(await parseErrorResponse(res));
      return;
    }
    var plan = await res.json();
    var configModal = document.getElementById("parking-config-modal");
    if (configModal && bootstrap.Modal.getInstance(configModal)) {
      bootstrap.Modal.getInstance(configModal).hide();
    }
    invalidateParkingPlanCache(parkingConfigFloorNumber);
    await refreshGrid();
    showParkingPlanInSection(plan, true);
  }

  function showParkingLinkError(message) {
    var el = document.getElementById("parking-link-error");
    if (!el) return;
    if (!message) {
      el.textContent = "";
      el.classList.add("d-none");
      return;
    }
    el.textContent = message;
    el.classList.remove("d-none");
  }

  function invalidateParkingPlanCache(floorNumber) {
    var section = parkingSectionForFloor(floorNumber);
    if (!section) return;
    var root = parkingPlanRootForSection(section);
    if (!root) return;
    delete root.dataset.loadedSlots;
    delete root.dataset.loadedLinks;
  }

  async function loadResidentialFlatOptions(force) {
    if (parkingResidentialCache && !force) return parkingResidentialCache;
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    if (!buildingId) return [];
    var res = await fetch(
      appRoot() + "/buildings/" + buildingId + "/flats/residential-for-parking-link",
      { headers: { Accept: "application/json" } }
    );
    if (!res.ok) return [];
    parkingResidentialCache = await res.json();
    return parkingResidentialCache;
  }

  function syncParkingOrientationLabels(state, slotEl) {
    var orient = "vertical";
    var slotNumber = parkingLinkSlotNumber;
    if (!slotNumber && selectedParkingSlotElement) {
      slotNumber = selectedParkingSlotElement.getAttribute("data-slot-number");
    }
    if (state && slotNumber) {
      for (var i = 0; i < state.placements.length; i++) {
        if (state.placements[i].slotNumber === Number(slotNumber)) {
          orient = state.placements[i].orientation || "vertical";
          break;
        }
      }
    } else if (slotEl) {
      orient = slotEl.classList.contains("parking-plan__slot--horizontal") ? "horizontal" : "vertical";
    }
    var text = orient === "horizontal" ? "Horizontal" : "Vertical";
    ["parking-link-orientation-value", "panel-parking-slot-orientation-value"].forEach(function (id) {
      var label = document.getElementById(id);
      if (label) label.textContent = text;
    });
  }

  function getParkingSlotRotateContext() {
    var floorNumber = parkingLinkFloorNumber || selectedParkingFloorNumber;
    var slotNumber = parkingLinkSlotNumber;
    if (!slotNumber && selectedParkingSlotElement) {
      slotNumber = selectedParkingSlotElement.getAttribute("data-slot-number");
    }
    if (!floorNumber || !slotNumber) return null;
    var section = parkingSectionForFloor(floorNumber);
    var root = section && parkingPlanRootForSection(section);
    var state = root && root._parkingLayoutState;
    if (!state) return null;
    return { slotNumber: Number(slotNumber), root: root, state: state };
  }

  function refreshSelectedParkingSlotElement(flatId) {
    if (!flatId) return null;
    var slotEl = findParkingSlotElement(flatId);
    if (slotEl) {
      clearParkingSlotHighlight();
      slotEl.classList.add("parking-plan__slot--selected");
      selectedParkingSlotElement = slotEl;
    }
    return slotEl;
  }

  async function rotateParkingSlotFromModal() {
    var ctx = getParkingSlotRotateContext();
    if (!ctx) {
      if (selectedParkingSlot) showAdminError("Layout is not available for this floor.");
      else showParkingLinkError("Layout is not available for this floor.");
      return;
    }
    if (selectedParkingSlot) showAdminError("");
    else showParkingLinkError("");
    toggleParkingSlotOrientation(ctx.state, ctx.slotNumber);
    syncParkingOrientationLabels(ctx.state, null);
    rerenderParkingPlanFromState(ctx.root);
    var flatId =
      selectedParkingSlotElement && selectedParkingSlotElement.getAttribute("data-parking-flat-id");
    if (flatId) refreshSelectedParkingSlotElement(flatId);
    var result = await autoSaveParkingLayout(ctx.root);
    if (!result.ok) {
      if (selectedParkingSlot) showAdminError(result.error);
      else showParkingLinkError(result.error);
    }
  }

  async function openParkingLinkModal(slotEl) {
    if (!slotEl || !isPlatformAdminEdit()) return;
    mountModalsOnBody();
    parkingLinkParkingFlatId = slotEl.getAttribute("data-parking-flat-id");
    var section = slotEl.closest(".flat-parking-section");
    parkingLinkFloorNumber = section ? section.dataset.floorNumber : null;
    parkingLinkSlotNumber = slotEl.getAttribute("data-slot-number");
    var linkedId = slotEl.getAttribute("data-linked-flat-id") || "";
    var modalEl = document.getElementById("parking-link-modal");
    var label = document.getElementById("parking-link-slot-label");
    var select = document.getElementById("parking-link-flat");
    var root = section && parkingPlanRootForSection(section);
    var state = root && root._parkingLayoutState;
    if (!modalEl || !parkingLinkParkingFlatId || typeof bootstrap === "undefined" || !bootstrap.Modal) {
      return;
    }
    if (label) {
      label.textContent =
        "Parking slot " +
        (parkingLinkSlotNumber || "") +
        (parkingLinkFloorNumber ? " · Floor " + parkingLinkFloorNumber : "");
    }
    syncParkingOrientationLabels(state, slotEl);
    showParkingLinkError("");
    if (select) {
      select.innerHTML = '<option value="">— Not linked —</option>';
      select.disabled = true;
    }
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
    var options = await loadResidentialFlatOptions(false);
    if (!select) return;
    options.forEach(function (opt) {
      var o = document.createElement("option");
      o.value = opt.id;
      o.textContent =
        opt.flatNumber + " (Floor " + opt.floorNumber + ", " + opt.bhkType + ")";
      if (linkedId && opt.id === linkedId) o.selected = true;
      select.appendChild(o);
    });
    select.disabled = false;
  }

  async function postParkingLink(parkingFlatId, residentialFlatId) {
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    return fetch(appRoot() + "/flats/" + parkingFlatId + "/parking-link", {
      method: "POST",
      headers: headers,
      body: JSON.stringify({ residentialFlatId: residentialFlatId }),
    });
  }

  function refreshParkingPlansForFloors(floorNumbers) {
    var seen = {};
    (floorNumbers || []).forEach(function (fn) {
      if (!fn || seen[fn]) return;
      seen[fn] = true;
      invalidateParkingPlanCache(fn);
      fetchParkingPlan(fn).then(function (plan) {
        if (plan) showParkingPlanInSection(plan, true);
      });
    });
  }

  async function afterParkingLinkChanged(affectedFloors) {
    parkingSlotsCache = null;
    refreshParkingPlansForFloors(affectedFloors);
    if (selectedFlatId && !selectedParkingSection) {
      await loadFlatParkingLinks(selectedFlatId);
    }
  }

  function showFlatParkingLinksError(message) {
    var el = document.getElementById("panel-parking-links-error");
    if (!el) return;
    if (!message) {
      el.textContent = "";
      el.classList.add("d-none");
      return;
    }
    el.textContent = message;
    el.classList.remove("d-none");
  }

  function renderFlatParkingLinksList(slots, canEdit) {
    var list = document.getElementById("panel-parking-links-list");
    var empty = document.getElementById("panel-parking-links-empty");
    if (!list) return;
    list.innerHTML = "";
    if (!slots || !slots.length) {
      if (empty) empty.classList.remove("d-none");
      return;
    }
    if (empty) empty.classList.add("d-none");
    slots.forEach(function (slot) {
      var li = document.createElement("li");
      li.className = "flat-parking-links-list__item";
      var label = document.createElement("span");
      label.textContent =
        "Floor " +
        slot.floorNumber +
        " · Slot " +
        slot.slotNumber +
        " (" +
        slot.flatNumber +
        ")";
      li.appendChild(label);
      if (canEdit) {
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "btn btn-link btn-sm text-danger px-1 py-0 flat-parking-links-unlink";
        btn.setAttribute("data-parking-flat-id", slot.parkingFlatId);
        btn.setAttribute("data-floor-number", String(slot.floorNumber));
        btn.textContent = "Remove";
        li.appendChild(btn);
      }
      list.appendChild(li);
    });
  }

  async function loadFlatParkingLinks(flatId) {
    if (!flatId) return;
    showFlatParkingLinksError("");
    var res = await fetch(appRoot() + "/flats/" + flatId + "/linked-parking", {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) {
      showFlatParkingLinksError(await parseErrorResponse(res));
      renderFlatParkingLinksList([], false);
      return;
    }
    var slots = await res.json();
    renderFlatParkingLinksList(slots, isPlatformAdminEdit());
    if (isPlatformAdminEdit()) {
      await populateParkingSlotAddSelect(flatId, slots);
    }
  }

  async function loadParkingSlotOptions(force) {
    if (parkingSlotsCache && !force) return parkingSlotsCache;
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    if (!buildingId) return [];
    var res = await fetch(appRoot() + "/buildings/" + buildingId + "/parking-slots-for-link", {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return [];
    parkingSlotsCache = await res.json();
    return parkingSlotsCache;
  }

  async function populateParkingSlotAddSelect(residentialFlatId, linkedSlots) {
    var select = document.getElementById("panel-parking-add");
    if (!select) return;
    var linkedIds = {};
    (linkedSlots || []).forEach(function (s) {
      linkedIds[s.parkingFlatId] = true;
    });
    select.innerHTML = '<option value="">— Select slot —</option>';
    select.disabled = true;
    var options = await loadParkingSlotOptions(false);
    options.forEach(function (opt) {
      if (opt.linkedResidentialFlatId && opt.linkedResidentialFlatId !== residentialFlatId) {
        return;
      }
      if (linkedIds[opt.id]) {
        return;
      }
      var o = document.createElement("option");
      o.value = opt.id;
      o.textContent =
        "Floor " +
        opt.floorNumber +
        " · Slot " +
        opt.slotNumber +
        " (" +
        opt.flatNumber +
        ")";
      select.appendChild(o);
    });
    select.disabled = false;
  }

  function syncFlatParkingLinksPanel(cardEl) {
    var section = document.getElementById("panel-parking-links");
    var admin = document.getElementById("panel-parking-links-admin");
    if (!section) return;
    var show = isResidentialFlatForParkingLinks(cardEl);
    section.classList.toggle("d-none", !show);
    if (!show) {
      showFlatParkingLinksError("");
      return;
    }
    if (admin) {
      admin.classList.toggle("d-none", !isPlatformAdminEdit());
    }
    if (cardEl.dataset.flatId) {
      loadFlatParkingLinks(cardEl.dataset.flatId);
    }
  }

  async function linkParkingFromFlatPanel() {
    if (!selectedFlatId || !isPlatformAdminEdit()) return;
    var select = document.getElementById("panel-parking-add");
    if (!select || !select.value) {
      showFlatParkingLinksError("Select a parking slot to link.");
      return;
    }
    showFlatParkingLinksError("");
    var parkingFlatId = select.value;
    var floorNumber = null;
    var options = await loadParkingSlotOptions(false);
    options.forEach(function (opt) {
      if (opt.id === parkingFlatId) floorNumber = opt.floorNumber;
    });
    var res = await postParkingLink(parkingFlatId, selectedFlatId);
    if (!res.ok) {
      showFlatParkingLinksError(await parseErrorResponse(res));
      return;
    }
    select.value = "";
    await afterParkingLinkChanged(floorNumber != null ? [floorNumber] : []);
  }

  async function unlinkParkingFromFlatPanel(parkingFlatId, floorNumber) {
    if (!parkingFlatId || !isPlatformAdminEdit()) return;
    showFlatParkingLinksError("");
    var res = await postParkingLink(parkingFlatId, null);
    if (!res.ok) {
      showFlatParkingLinksError(await parseErrorResponse(res));
      return;
    }
    await afterParkingLinkChanged(floorNumber != null ? [floorNumber] : []);
  }

  async function saveParkingLink() {
    if (!parkingLinkParkingFlatId) return;
    var select = document.getElementById("parking-link-flat");
    if (!select) return;
    showParkingLinkError("");
    var residentialFlatId = select.value || null;
    var res = await postParkingLink(parkingLinkParkingFlatId, residentialFlatId);
    if (!res.ok) {
      showParkingLinkError(await parseErrorResponse(res));
      return;
    }
    var linkModal = document.getElementById("parking-link-modal");
    if (linkModal && bootstrap.Modal.getInstance(linkModal)) {
      bootstrap.Modal.getInstance(linkModal).hide();
    }
    await afterParkingLinkChanged(
      parkingLinkFloorNumber ? [parkingLinkFloorNumber] : []
    );
  }

  function updateParkingSectionElement(el, floor) {
    if (!el || !floor) return;
    var first = floor.flats && floor.flats[0];
    var configured = parkingSectionConfigured(floor);
    var snapshot = parkingFloorSnapshot(floor);
    var unchanged = el.dataset.parkingSnapshot === snapshot;
    el.setAttribute("data-slot-count", String(floor.parkingSlotCount || 0));
    el.setAttribute(
      "data-car-size-percent",
      String(floor.parkingCarSizePercent != null ? floor.parkingCarSizePercent : DEFAULT_PARKING_CAR_SIZE_PERCENT)
    );
    el.setAttribute(
      "data-grid-rows",
      String(
        floor.parkingGridRows != null
          ? floor.parkingGridRows
          : parkingMinGridRowsForSlotCount(floor.parkingSlotCount || 1)
      )
    );
    el.setAttribute(
      "data-min-grid-rows",
      String(
        floor.parkingMinGridRows != null
          ? floor.parkingMinGridRows
          : parkingMinGridRowsForSlotCount(floor.parkingSlotCount || 1)
      )
    );
    el.setAttribute("data-range-label", floor.parkingRangeLabel || "");
    el.setAttribute("data-configured", configured ? "true" : "false");
    el.setAttribute("data-layout-image", floor.parkingHasLayoutImage ? "true" : "false");
    el.setAttribute(
      "data-car-lift-count",
      String(floor.parkingCarLiftCount != null ? floor.parkingCarLiftCount : 0)
    );
    el.setAttribute(
      "data-passenger-lift-count",
      String(floor.parkingPassengerLiftCount != null ? floor.parkingPassengerLiftCount : 0)
    );
    el.setAttribute(
      "data-gate-count",
      String(floor.parkingGateCount != null ? floor.parkingGateCount : 0)
    );
    el.classList.toggle("flat-parking-section--configured", configured);
    el.classList.toggle("flat-parking-section--pending", !configured);
    el.classList.toggle("flat-parking-section--split", configured);
    if (first) {
      el.setAttribute("data-first-flat-id", String(first.id));
      el.setAttribute("data-area", first.areaSqft != null ? String(first.areaSqft) : "");
      el.setAttribute("data-price", first.basePrice != null ? String(first.basePrice) : "");
    }
    el.dataset.parkingSnapshot = snapshot;
    if (unchanged) {
      return;
    }
    el.innerHTML = buildParkingSectionInnerHtml(floor);
    var planPane = el.querySelector(".flat-parking-section__plan");
    if (planPane) planPane.setAttribute("aria-hidden", configured ? "false" : "true");
  }

  function createParkingSectionElement(floor) {
    var el = document.createElement("div");
    el.className = "flat-parking-section";
    el.setAttribute("data-floor-number", String(floor.floorNumber));
    updateParkingSectionElement(el, floor);
    return el;
  }

  function upsertParkingSection(floor) {
    var floorRow = findFloorRow(floor.floorNumber);
    if (!floorRow) return;
    var row = floorRow.querySelector(".flat-card-row");
    if (!row) return;
    row.querySelectorAll(".flat-card").forEach(function (card) {
      card.remove();
    });
    var selector = '.flat-parking-section[data-floor-number="' + floor.floorNumber + '"]';
    var el = row.querySelector(selector);
    if (!el) {
      el = createParkingSectionElement(floor);
      row.appendChild(el);
    } else {
      updateParkingSectionElement(el, floor);
    }
    var addBtn = floorRow.querySelector(".flat-add-unit-btn");
    if (addBtn) addBtn.classList.add("d-none");
  }

  function syncResidentialFloor(floor) {
    var floorRow = findFloorRow(floor.floorNumber);
    if (!floorRow) return;
    var row = floorRow.querySelector(".flat-card-row");
    if (row) {
      var section = row.querySelector(".flat-parking-section");
      if (section) section.remove();
    }
    var addBtn = floorRow.querySelector(".flat-add-unit-btn");
    if (addBtn) addBtn.classList.remove("d-none");
  }

  function syncGridFromData(floors) {
    var liveIds = new Set();
    floors.forEach(function (floor) {
      (floor.flats || []).forEach(function (flat) {
        liveIds.add(String(flat.id));
      });
      if (floor.parkingSection) {
        upsertParkingSection(floor);
        return;
      }
      syncResidentialFloor(floor);
      (floor.flats || []).forEach(function (flat) {
        var el = document.getElementById("flat-" + flat.id);
        if (!el) {
          var floorRow = findFloorRow(floor.floorNumber);
          if (!floorRow) return;
          el = createFlatCardFromData(flat);
          insertFlatCardInRow(floorRow, el, flat.flatNumber);
        }
        syncFlatCardFromData(el, flat);
      });
    });

    var grid = document.getElementById("flat-grid");
    if (grid) {
      grid.querySelectorAll(".flat-card[data-flat-id]").forEach(function (card) {
        var id = card.getAttribute("data-flat-id");
        if (id && !liveIds.has(id)) {
          card.remove();
        }
      });
      var liveParkingFloors = new Set(
        floors.filter(function (f) {
          return f.parkingSection;
        }).map(function (f) {
          return String(f.floorNumber);
        })
      );
      grid.querySelectorAll(".flat-parking-section").forEach(function (section) {
        var fn = section.getAttribute("data-floor-number");
        if (fn && !liveParkingFloors.has(fn)) {
          section.remove();
        }
      });
    }
  }

  async function refreshGrid() {
    var grid = document.getElementById("flat-grid");
    if (!grid) return;
    parkingResidentialCache = null;
    parkingSlotsCache = null;
    var buildingId = grid.getAttribute("data-building-id");
    if (!buildingId) return;
    var res = await fetch(appRoot() + "/buildings/" + buildingId + "/flats/data", {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return;
    var floors = await res.json();
    syncGridFromData(floors);
    initAllFlatCards();
    applyBookingSelectionHighlight();
    if (selectedParkingSection && selectedParkingFloorNumber) {
      var section = document.querySelector(
        '.flat-parking-section[data-floor-number="' + selectedParkingFloorNumber + '"]'
      );
      if (section) window.floor21SelectParkingSection(section, false);
    } else if (selectedParkingSlot && selectedFlatId) {
      var slot = findParkingSlotElement(selectedFlatId);
      if (slot) window.floor21SelectParkingSlot(slot, false);
    } else if (selectedFlatId) {
      var selected = document.getElementById("flat-" + selectedFlatId);
      if (selected) syncActionButtons(selected);
    }
    scheduleDuplexLinks();
    await loadAllConfiguredParkingPlans();
  }

  async function postStatus(flatId, status) {
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    var res = await fetch(appRoot() + "/flats/" + flatId + "/status", {
      method: "POST",
      headers: headers,
      body: JSON.stringify({ status: status }),
    });
    if (!res.ok) {
      window.alert("Could not update status");
    }
  }

  function openFloorPlanModal(url, title, cardEl) {
    if (cardEl && !isFlatBookable(cardEl)) {
      return;
    }
    var img = document.getElementById("floor-plan-modal-img");
    var modalEl = document.getElementById("floor-plan-modal");
    var titleEl = document.getElementById("floor-plan-modal-title");
    if (!img || !modalEl || typeof bootstrap === "undefined" || !bootstrap.Modal) return;
    img.src = url;
    img.alt = title || "Floor plan";
    if (titleEl) titleEl.textContent = title || "Floor plan";
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  function openParkingLayoutModal(floorNumber) {
    var url = parkingLayoutImageUrl(floorNumber);
    if (!url) return;
    openFloorPlanModal(url, "Parking layout — Floor " + floorNumber, null);
  }

  var parkingLayoutUploadSection = null;

  async function uploadParkingLayoutImage(sectionEl, file) {
    if (!sectionEl || !file || !isPlatformAdminEdit()) return;
    var floorNumber = sectionEl.getAttribute("data-floor-number");
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : "";
    if (!buildingId || !floorNumber) return;
    var formData = new FormData();
    formData.append("image", file);
    var res = await fetch(
      appRoot() + "/buildings/" + buildingId + "/parking-layout-image/" + encodeURIComponent(floorNumber),
      {
        method: "POST",
        headers: csrfHeaders(),
        body: formData,
      }
    );
    if (!res.ok) {
      var message = "Could not upload parking layout image.";
      try {
        var err = await res.json();
        if (err && err.error) message = err.error;
      } catch (ignore) {
        /* use default message */
      }
      window.alert(message);
      return;
    }
    sectionEl.setAttribute("data-layout-image", "true");
    refreshParkingLayoutLinks(sectionEl);
  }

  function isFlatBookable(cardEl) {
    return !!(cardEl && cardEl.dataset.bookable === "true");
  }

  function isFlatBookableFromData(flat, cardEl) {
    if (flat && flat.bookableByCurrentUser !== undefined && flat.bookableByCurrentUser !== null) {
      return flat.bookableByCurrentUser !== false;
    }
    return isFlatBookable(cardEl);
  }

  function ownerLinesForCard(flat, cardEl) {
    if (flat && flat.status === "CANCELLED") {
      return {
        display: "Deactivated",
        detail: "",
      };
    }
    if (!isFlatBookableFromData(flat, cardEl)) {
      return {
        display: flat && flat.status === "BOOKED" ? "Booked" : "",
        detail: "",
      };
    }
    return {
      display: flat.ownerDisplay == null ? "" : String(flat.ownerDisplay).trim(),
      detail: flat.ownerDetail == null ? "" : String(flat.ownerDetail).trim(),
    };
  }

  function flatLayoutImageUrl(flatId) {
    if (!flatId) return "";
    return appRoot() + "/flats/" + encodeURIComponent(flatId) + "/layout-image?t=" + Date.now();
  }

  function cardHasLayoutImage(cardEl) {
    return !!(cardEl && cardEl.dataset.hasLayoutImage === "true");
  }

  function canViewFlatLayout(cardEl) {
    if (!cardEl || !cardHasLayoutImage(cardEl)) return false;
    if (isPlatformAdminEdit()) return true;
    return isFlatBookable(cardEl) && !isNonBookableUnit(cardEl);
  }

  function syncFlatLayoutPanel(cardEl) {
    var actions = document.getElementById("panel-flat-layout-actions");
    var upload = document.getElementById("panel-flat-layout-upload");
    var view = document.getElementById("panel-flat-layout-view");
    var hint = document.getElementById("panel-flat-layout-hint");
    if (!actions) return;
    if (!cardEl || isNonBookableUnit(cardEl)) {
      actions.classList.add("d-none");
      return;
    }
    actions.classList.remove("d-none");
    var canUpload = isPlatformAdminEdit();
    var canView = canViewFlatLayout(cardEl);
    if (upload) upload.classList.toggle("d-none", !canUpload);
    if (view) view.classList.toggle("d-none", !canView);
    if (hint) hint.classList.toggle("d-none", !canUpload);
  }

  function openFlatLayoutModal(flatId, title) {
    var url = flatLayoutImageUrl(flatId);
    if (!url) return;
    openFloorPlanModal(url, title || "Flat layout", null);
  }

  var flatLayoutUploadFlatId = null;

  async function uploadFlatLayoutImage(flatId, file) {
    if (!flatId || !file || !isPlatformAdminEdit()) return;
    var formData = new FormData();
    formData.append("image", file);
    var res = await fetch(appRoot() + "/flats/" + encodeURIComponent(flatId) + "/layout-image", {
      method: "POST",
      headers: csrfHeaders(),
      body: formData,
    });
    if (!res.ok) {
      var message = "Could not upload flat layout image.";
      try {
        var err = await res.json();
        if (err && err.error) message = err.error;
      } catch (ignore) {
        /* use default message */
      }
      window.alert(message);
      return;
    }
    var card = document.getElementById("flat-" + flatId);
    if (card) {
      card.dataset.hasLayoutImage = "true";
      syncFlatLayoutPanel(card);
    }
  }

  function initAllFlatCards() {
    document.querySelectorAll("#flat-grid .flat-card").forEach(function (card) {
      if (card.dataset.bookable !== "true" && card.dataset.bookable !== "false") {
        card.dataset.bookable = card.classList.contains("flat-card--other-partner") ? "false" : "true";
      }
      stripNonBookableHover(card);
    });
  }

  function syncActionButtons(cardEl) {
    var bookable = isFlatBookable(cardEl);
    var nonBookable = isNonBookableUnit(cardEl);
    var hold = document.getElementById("hold-btn");
    var book = document.getElementById("book-btn");
    if (hold) {
      hold.disabled = !bookable || nonBookable;
      hold.classList.toggle("disabled", !bookable || nonBookable);
      hold.title = bookable && !nonBookable ? "" : nonBookable ? "Not a bookable unit" : "Not assigned to you";
    }
    if (book) {
      var statusOk =
          cardEl.dataset.status === "AVAILABLE" || cardEl.dataset.status === "HOLD";
      book.classList.toggle("disabled", !bookable || !statusOk || nonBookable);
      if (!bookable) {
        book.title = "Not assigned to you";
      } else if (nonBookable) {
        book.title = "Not a bookable unit";
      } else {
        book.removeAttribute("title");
      }
    }
  }

  function openFlatDetailsModal() {
    var modalEl = document.getElementById("flat-details-modal");
    if (!modalEl || typeof bootstrap === "undefined" || !bootstrap.Modal) return;
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  function syncParkingSlotAdminFields(slotEl) {
    if (!slotEl) return;
    var superBuilder = document.getElementById("admin-super-builder-area");
    var price = document.getElementById("admin-price");
    var bhk = document.getElementById("admin-bhk");
    if (superBuilder) superBuilder.value = formatAreaForDisplay(slotEl.dataset.area);
    if (price) price.value = slotEl.dataset.price || "0";
    if (bhk) bhk.value = "PKG";
    showAdminError("");
  }

  function syncParkingSlotLinkedLabel(slotEl) {
    var label = document.getElementById("panel-parking-slot-linked");
    if (!label || !slotEl) return;
    var linked = slotEl.getAttribute("data-linked-flat-number") || "";
    label.textContent = linked
      ? "Linked to residential flat " + linked + "."
      : "Not linked to a residential flat.";
  }

  function applyFlatDataToParkingSlot(flatId, flat) {
    if (!flatId || !flat) return;
    var selector = '.parking-plan__slot[data-parking-flat-id="' + flatId + '"]';
    document.querySelectorAll(selector).forEach(function (slotEl) {
      if (flat.areaSqft != null) slotEl.dataset.area = String(flat.areaSqft);
      if (flat.basePrice != null) slotEl.dataset.price = String(flat.basePrice);
      if (flat.flatNumber != null) slotEl.dataset.flatNumber = flat.flatNumber;
    });
    var slotEl = findParkingSlotElement(flatId);
    var section =
      (slotEl && slotEl.closest(".flat-parking-section")) ||
      (selectedParkingFloorNumber ? parkingSectionForFloor(selectedParkingFloorNumber) : null);
    if (section) {
      var root = parkingPlanRootForSection(section);
      if (root && root._parkingLayoutState && root._parkingLayoutState.slots) {
        root._parkingLayoutState.slots.forEach(function (slot) {
          if (String(slot.flatId) === String(flatId)) {
            slot.areaSqft = flat.areaSqft;
          }
        });
      }
      refreshParkingSectionMetaDisplays();
    }
    if (
      selectedParkingSlotElement &&
      String(selectedParkingSlotElement.getAttribute("data-parking-flat-id")) === String(flatId)
    ) {
      syncParkingSlotAdminFields(selectedParkingSlotElement);
      setAreaPanelFromDataset(selectedParkingSlotElement);
    }
  }

  function findParkingSlotElement(flatId) {
    if (!flatId) return null;
    return document.querySelector('.parking-plan__slot[data-parking-flat-id="' + flatId + '"]');
  }

  window.floor21SelectParkingSlot = function (slotEl, showModal) {
    if (!slotEl || !isPlatformAdminEdit()) return;
    var flatId = slotEl.getAttribute("data-parking-flat-id");
    if (!flatId) return;
    var section = slotEl.closest(".flat-parking-section");
    selectedFlatId = flatId;
    selectedParkingFloorNumber = section ? section.dataset.floorNumber : null;
    clearParkingSectionHighlight();
    clearParkingSlotHighlight();
    slotEl.classList.add("parking-plan__slot--selected");
    selectedParkingSlotElement = slotEl;
    var titleEl = document.getElementById("panel-title");
    if (titleEl) {
      titleEl.textContent =
        "Parking slot " +
        (slotEl.getAttribute("data-slot-number") || "") +
        (selectedParkingFloorNumber ? " · Floor " + selectedParkingFloorNumber : "");
    }
    document.getElementById("panel-type").textContent =
      "PKG · " + (slotEl.dataset.flatNumber || slotEl.getAttribute("data-flat-number") || "");
    document.getElementById("panel-floor").textContent = selectedParkingFloorNumber || "";
    setAreaPanelFromDataset(slotEl);
    document.getElementById("panel-price").textContent = slotEl.dataset.price || "0";
    setParkingSlotMode(true);
    syncParkingSlotAdminFields(slotEl);
    syncParkingSlotLinkedLabel(slotEl);
    var root = section && parkingPlanRootForSection(section);
    syncParkingOrientationLabels(root && root._parkingLayoutState, slotEl);
    if (showModal !== false) {
      openFlatDetailsModal();
    }
  };

  window.floor21SelectParkingSection = function (el, showModal) {
    if (!el || !isPlatformAdminEdit()) return;
    selectedParkingSection = true;
    selectedParkingFloorNumber = el.dataset.floorNumber || null;
    selectedFlatId = el.dataset.firstFlatId || null;
    clearParkingSectionHighlight();
    el.classList.add("flat-parking-section--selected");
    var titleEl = document.getElementById("panel-title");
    if (titleEl) {
      titleEl.textContent = "Parking — Floor " + (el.dataset.floorNumber || "");
    }
    var slotCount = el.dataset.slotCount || "0";
    var range = el.dataset.rangeLabel || "";
    document.getElementById("panel-type").textContent =
      "Shared parking (" +
      slotCount +
      " slot" +
      (Number(slotCount) === 1 ? "" : "s") +
      (range ? ": " + range : "") +
      ")";
    document.getElementById("panel-floor").textContent = el.dataset.floorNumber || "";
    setAreaPanelFromDataset(el);
    document.getElementById("panel-price").textContent = el.dataset.price || "";
    setParkingSectionMode(true);
    syncParkingSectionAdminFields(el);
    if (showModal !== false) {
      openFlatDetailsModal();
    }
  };

  window.floor21SelectFlat = function (el, showModal) {
    if (!canOpenFlatPanel(el)) return;
    selectedParkingSection = false;
    selectedParkingFloorNumber = null;
    clearParkingSectionHighlight();
    setParkingSlotMode(false);
    setParkingSectionMode(false);
    selectedFlatId = el.dataset.flatId;
    var titleEl = document.getElementById("panel-title");
    var flatNumEl = el.querySelector(".flat-number");
    var flatLabel = flatNumEl ? flatNumEl.textContent.trim() : "";
    if (titleEl) {
      titleEl.textContent = flatLabel ? "Flat " + flatLabel : "Flat details";
    }
    document.getElementById("panel-type").textContent = el.dataset.type || "";
    syncColumnTypePanelFromCard(el);
    document.getElementById("panel-floor").textContent = el.dataset.floor || "";
    setAreaPanelFromDataset(el);
    document.getElementById("panel-price").textContent =
      getEffectiveFlatDatasetValue(el, "price", "basePrice") || el.dataset.price || "";
    var book = document.getElementById("book-btn");
    if (book) {
      book.href = appRoot() + "/bookings/new?flatId=" + encodeURIComponent(selectedFlatId);
      var statusOk = el.dataset.status === "AVAILABLE" || el.dataset.status === "HOLD";
      var bookable = isFlatBookable(el);
      book.classList.toggle("disabled", !bookable || !statusOk);
      if (!bookable) {
        book.title = "Not assigned to you";
      } else {
        book.removeAttribute("title");
      }
    }
    var clientInfo = document.getElementById("client-info-btn");
    if (clientInfo) {
      var cid = el.dataset.clientId;
      if (el.dataset.status === "BOOKED" && cid) {
        clientInfo.href = appRoot() + "/clients/" + encodeURIComponent(cid);
        clientInfo.classList.remove("d-none");
      } else {
        clientInfo.classList.add("d-none");
        clientInfo.setAttribute("href", "#");
      }
    }
    syncFlatLayoutPanel(el);
    applyBookingSelectionHighlight();
    syncActionButtons(el);
    syncAdminPanel(el);
    syncFlatParkingLinksPanel(el);
    if (showModal !== false) {
      openFlatDetailsModal();
    }
  };

  async function loadSalesPartnersIntoSelect() {
    var select = document.getElementById("admin-partner");
    var grid = document.getElementById("flat-grid");
    if (!select || !grid || !isPlatformAdminEdit()) return;
    var buildingId = grid.getAttribute("data-building-id");
    if (!buildingId) return;
    var res = await fetch(appRoot() + "/buildings/" + buildingId + "/sales-partners", {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return;
    var partners = await res.json();
    var current = select.value;
    select.innerHTML = '<option value="">— Unassigned —</option>';
    partners.forEach(function (p) {
      var opt = document.createElement("option");
      opt.value = p.id;
      opt.textContent = p.fullName || p.id;
      select.appendChild(opt);
    });
    if (current) select.value = current;
    if (selectedFlatId) {
      var card = document.getElementById("flat-" + selectedFlatId);
      if (card) syncAdminPanel(card);
    }
  }

  function onPageReady(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn);
    } else {
      fn();
    }
    document.addEventListener("turbo:load", fn);
  }

  onPageReady(function () {
    mountModalsOnBody();
    var grid = document.getElementById("flat-grid");
    if (!grid) {
      return;
    }
    ensureParkingGridDelegation();
    if (grid.dataset.f21Init === "true") {
      return;
    }
    grid.dataset.f21Init = "true";
    initAllFlatCards();
    scheduleDuplexLinks();
    loadAllConfiguredParkingPlans();
    if (!window.__f21DuplexResizeBound) {
      window.__f21DuplexResizeBound = true;
      window.addEventListener("resize", scheduleDuplexLinks);
    }
    loadSalesPartnersIntoSelect();
    initAreaUnitSelector();
    if (isPlatformAdminEdit()) {
      loadUnitTypeDefaults(true);
      loadColumnTypeDefaults(true);
    }
    var flatAddBhk = document.getElementById("flat-add-bhk");
    if (flatAddBhk) {
      flatAddBhk.addEventListener("change", updateFlatAddPlaceholders);
    }
    var gridConfigureDefaults = document.getElementById("grid-configure-type-defaults-btn");
    if (gridConfigureDefaults) {
      gridConfigureDefaults.addEventListener("click", function () {
        openUnitTypeDefaultsModal(null);
      });
    }
    var gridConfigureColumnDefaults = document.getElementById("grid-configure-column-type-defaults-btn");
    if (gridConfigureColumnDefaults) {
      gridConfigureColumnDefaults.addEventListener("click", function () {
        openColumnTypeDefaultsModal(null);
      });
    }
    var unitDefaultsBhk = document.getElementById("unit-type-defaults-bhk");
    if (unitDefaultsBhk) {
      unitDefaultsBhk.addEventListener("change", function () {
        populateUnitTypeDefaultsModal(unitDefaultsBhk.value);
        updateUnitTypeDefaultsModalTitle(unitDefaultsBhk.value);
      });
    }
    var unitDefaultsAreaUnit = document.getElementById("unit-type-defaults-area-unit");
    if (unitDefaultsAreaUnit) {
      unitDefaultsAreaUnit.addEventListener("change", function () {
        var nextUnit = unitDefaultsAreaUnit.value === "sqm" ? "sqm" : "sqft";
        var prevUnit = nextUnit === "sqm" ? "sqft" : "sqm";
        convertAllAreaInputDisplays(prevUnit, nextUnit);
        setAreaUnit(nextUnit);
        var panelSel = document.getElementById("panel-area-unit");
        if (panelSel) panelSel.value = nextUnit;
        var addSel = document.getElementById("flat-add-area-unit");
        if (addSel) addSel.value = nextUnit;
        updateAreaUnitLabels();
        refreshAreaPanelDisplayOnly();
      });
    }
    var unitDefaultsSave = document.getElementById("unit-type-defaults-save");
    if (unitDefaultsSave) {
      unitDefaultsSave.addEventListener("click", function () {
        saveUnitTypeDefaults();
      });
    }
    var unitDefaultsApply = document.getElementById("unit-type-defaults-apply");
    if (unitDefaultsApply) {
      unitDefaultsApply.addEventListener("click", function () {
        applyUnitTypeDefaultsToFlats();
      });
    }
    var columnDefaultsCol = document.getElementById("column-type-defaults-column");
    if (columnDefaultsCol) {
      columnDefaultsCol.addEventListener("change", function () {
        populateColumnTypeDefaultsModal(columnDefaultsCol.value);
        updateColumnTypeDefaultsModalTitle(columnDefaultsCol.value);
      });
    }
    var columnDefaultsAreaUnit = document.getElementById("column-type-defaults-area-unit");
    if (columnDefaultsAreaUnit) {
      columnDefaultsAreaUnit.addEventListener("change", function () {
        var nextUnit = columnDefaultsAreaUnit.value === "sqm" ? "sqm" : "sqft";
        var prevUnit = nextUnit === "sqm" ? "sqft" : "sqm";
        convertAllAreaInputDisplays(prevUnit, nextUnit);
        setAreaUnit(nextUnit);
        var panelSel = document.getElementById("panel-area-unit");
        if (panelSel) panelSel.value = nextUnit;
        var addSel = document.getElementById("flat-add-area-unit");
        if (addSel) addSel.value = nextUnit;
        var unitDefaultsAreaUnit = document.getElementById("unit-type-defaults-area-unit");
        if (unitDefaultsAreaUnit) unitDefaultsAreaUnit.value = nextUnit;
        updateAreaUnitLabels();
        refreshAreaPanelDisplayOnly();
      });
    }
    var columnDefaultsSave = document.getElementById("column-type-defaults-save");
    if (columnDefaultsSave) {
      columnDefaultsSave.addEventListener("click", function () {
        saveColumnTypeDefaults();
      });
    }
    var columnDefaultsApply = document.getElementById("column-type-defaults-apply");
    if (columnDefaultsApply) {
      columnDefaultsApply.addEventListener("click", function () {
        applyColumnTypeDefaultsToFlats();
      });
    }
    var modalEl = document.getElementById("floor-plan-modal");
    if (modalEl) {
      modalEl.addEventListener("hidden.bs.modal", function () {
        var img = document.getElementById("floor-plan-modal-img");
        if (img) img.removeAttribute("src");
      });
    }
    var parkingConfigSave = document.getElementById("parking-config-save");
    if (parkingConfigSave) {
      parkingConfigSave.addEventListener("click", function () {
        saveParkingConfig();
      });
    }
    var parkingLayoutFileInput = document.getElementById("parking-layout-file-input");
    if (parkingLayoutFileInput) {
      parkingLayoutFileInput.addEventListener("change", function () {
        var file = parkingLayoutFileInput.files && parkingLayoutFileInput.files[0];
        if (!file || !parkingLayoutUploadSection) return;
        void uploadParkingLayoutImage(parkingLayoutUploadSection, file);
        parkingLayoutUploadSection = null;
        parkingLayoutFileInput.value = "";
      });
    }
    var parkingConfigCarSize = document.getElementById("parking-config-car-size");
    if (parkingConfigCarSize) {
      parkingConfigCarSize.addEventListener("input", function () {
        syncParkingConfigCarSizeLabel(parkingConfigCarSize.value);
      });
    }
    var parkingLinkSave = document.getElementById("parking-link-save");
    if (parkingLinkSave) {
      parkingLinkSave.addEventListener("click", function () {
        saveParkingLink();
      });
    }
    var parkingLinkRotate = document.getElementById("parking-link-rotate");
    if (parkingLinkRotate) {
      parkingLinkRotate.addEventListener("click", function () {
        rotateParkingSlotFromModal();
      });
    }
    var parkingSlotRotate = document.getElementById("panel-parking-slot-rotate");
    if (parkingSlotRotate) {
      parkingSlotRotate.addEventListener("click", function () {
        rotateParkingSlotFromModal();
      });
    }
    var parkingSlotLinkBtn = document.getElementById("panel-parking-slot-link-btn");
    if (parkingSlotLinkBtn) {
      parkingSlotLinkBtn.addEventListener("click", function () {
        if (selectedParkingSlotElement) {
          openParkingLinkModal(selectedParkingSlotElement);
        }
      });
    }
    var parkingAddBtn = document.getElementById("panel-parking-add-btn");
    if (parkingAddBtn) {
      parkingAddBtn.addEventListener("click", function () {
        linkParkingFromFlatPanel();
      });
    }
    var parkingLinksList = document.getElementById("panel-parking-links-list");
    if (parkingLinksList) {
      parkingLinksList.addEventListener("click", function (e) {
        var unlink = e.target.closest(".flat-parking-links-unlink");
        if (!unlink) return;
        e.preventDefault();
        unlinkParkingFromFlatPanel(
          unlink.getAttribute("data-parking-flat-id"),
          unlink.getAttribute("data-floor-number")
        );
      });
    }
    var flatLayoutFileInput = document.getElementById("flat-layout-file-input");
    if (flatLayoutFileInput) {
      flatLayoutFileInput.addEventListener("change", function () {
        var file = flatLayoutFileInput.files && flatLayoutFileInput.files[0];
        if (!file || !flatLayoutUploadFlatId) return;
        void uploadFlatLayoutImage(flatLayoutUploadFlatId, file);
        flatLayoutUploadFlatId = null;
        flatLayoutFileInput.value = "";
      });
    }
    var flatLayoutUpload = document.getElementById("panel-flat-layout-upload");
    if (flatLayoutUpload) {
      flatLayoutUpload.addEventListener("click", function () {
        if (!selectedFlatId || !isPlatformAdminEdit()) return;
        var fileInput = document.getElementById("flat-layout-file-input");
        if (!fileInput) return;
        flatLayoutUploadFlatId = selectedFlatId;
        fileInput.value = "";
        fileInput.click();
      });
    }
    var flatLayoutView = document.getElementById("panel-flat-layout-view");
    if (flatLayoutView) {
      flatLayoutView.addEventListener("click", function () {
        if (!selectedFlatId) return;
        var card = document.getElementById("flat-" + selectedFlatId);
        if (!card || !canViewFlatLayout(card)) return;
        var titleEl = document.getElementById("panel-title");
        var title = titleEl && titleEl.textContent ? titleEl.textContent.trim() : "Flat layout";
        openFlatLayoutModal(selectedFlatId, title);
      });
    }
    var hold = document.getElementById("hold-btn");
    if (hold) {
      hold.addEventListener("click", async function () {
        if (!selectedFlatId || selectedParkingSection) return;
        var el = document.getElementById("flat-" + selectedFlatId);
        if (!el || isNonBookableUnit(el)) return;
        if (!isFlatBookable(el)) {
          window.alert("This flat is not assigned to you.");
          return;
        }
        var next = el.dataset.status === "HOLD" ? "AVAILABLE" : "HOLD";
        await postStatus(selectedFlatId, next);
        await refreshGrid();
        var updated = document.getElementById("flat-" + selectedFlatId);
        if (updated) window.floor21SelectFlat(updated, false);
      });
    }

    var bookBtn = document.getElementById("book-btn");
    if (bookBtn) {
      bookBtn.addEventListener("click", function (e) {
        if (bookBtn.classList.contains("disabled")) {
          e.preventDefault();
          if (selectedFlatId) {
            var card = document.getElementById("flat-" + selectedFlatId);
            if (card && !isFlatBookable(card)) {
              window.alert("This flat is not assigned to you.");
            }
          }
        }
      });
    }

    var adminSave = document.getElementById("admin-save-btn");
    if (adminSave) {
      adminSave.addEventListener("click", async function () {
        if (!selectedFlatId) return;
        showAdminError("");
        var form = readAdminForm({ includeColumnType: !selectedParkingSlot && !selectedParkingSection });
        var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
        var res = await fetch(appRoot() + "/flats/" + selectedFlatId + "/details", {
          method: "POST",
          headers: headers,
          body: JSON.stringify(form),
        });
        if (!res.ok) {
          showAdminError(await parseErrorResponse(res));
          return;
        }
        var flat = await res.json();
        applyFlatDataToParkingSlot(selectedFlatId, flat);
        if (selectedParkingSlot) {
          document.getElementById("panel-price").textContent =
            flat.basePrice != null ? String(flat.basePrice) : "";
          setAreaPanelFromFlat(flat);
          syncParkingSlotAdminFields(selectedParkingSlotElement);
          refreshParkingSectionMetaDisplays();
          return;
        }
        var card = document.getElementById("flat-" + selectedFlatId);
        applyFlatDataToCard(card, flat);
        document.getElementById("panel-type").textContent = flat.bhkType || "";
        syncColumnTypePanelFromFlat(flat);
        setAreaPanelFromFlat(flat);
        syncAdminAreaInputsFromFlat(flat);
        document.getElementById("panel-price").textContent = flat.basePrice != null ? String(flat.basePrice) : "";
        if (card) {
          syncAdminPanel(card);
          syncActionButtons(card);
          syncFlatLayoutPanel(card);
        }
      });
    }

    var adminApplyFloor = document.getElementById("admin-apply-floor-btn");
    if (adminApplyFloor) {
      adminApplyFloor.addEventListener("click", async function () {
        if (!selectedFlatId && !selectedParkingSection) return;
        var card = selectedFlatId ? document.getElementById("flat-" + selectedFlatId) : null;
        var grid = document.getElementById("flat-grid");
        var buildingId = grid ? grid.getAttribute("data-building-id") : null;
        var floorNumber = selectedParkingSection
          ? selectedParkingFloorNumber
          : card
            ? card.dataset.floor
            : null;
        if (!buildingId || !floorNumber) return;
        var form = readAdminForm();
        if (
          !window.confirm(
            "Apply " +
              (form.bhkType || "this type") +
              " to every unit on floor " +
              floorNumber +
              "?"
          )
        ) {
          return;
        }
        showAdminError("");
        var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
        var res = await fetch(
          appRoot() + "/buildings/" + buildingId + "/flats/floor/" + encodeURIComponent(floorNumber) + "/details",
          {
            method: "POST",
            headers: headers,
            body: JSON.stringify(form),
          }
        );
        if (!res.ok) {
          showAdminError(await parseErrorResponse(res));
          return;
        }
        await refreshGrid();
        if (selectedParkingSection && selectedParkingFloorNumber) {
          var section = document.querySelector(
            '.flat-parking-section[data-floor-number="' + selectedParkingFloorNumber + '"]'
          );
          if (section) window.floor21SelectParkingSection(section, false);
        } else {
          var updated = document.getElementById("flat-" + selectedFlatId);
          if (updated) window.floor21SelectFlat(updated, false);
        }
      });
    }

    var adminDelete = document.getElementById("admin-delete-btn");
    if (adminDelete) {
      adminDelete.addEventListener("click", async function () {
        if (!selectedFlatId) return;
        var card = document.getElementById("flat-" + selectedFlatId);
        var label = card && card.querySelector(".flat-number") ? card.querySelector(".flat-number").textContent : selectedFlatId;
        var isInactive = card && card.dataset.status === "CANCELLED";
        var confirmMsg = isInactive
          ? "Activate flat " + label + " and make it available again?"
          : "Deactivate flat " + label + "? You can activate it later.";
        if (!window.confirm(confirmMsg)) return;
        showAdminError("");
        var headers = Object.assign({}, csrfHeaders());
        var res = await fetch(appRoot() + "/flats/" + selectedFlatId + "/activation", {
          method: "POST",
          headers: headers,
        });
        if (!res.ok) {
          showAdminError(await parseErrorResponse(res));
          return;
        }
        await refreshGrid();
        var updated = document.getElementById("flat-" + selectedFlatId);
        if (updated) {
          window.floor21SelectFlat(updated, false);
        }
      });
    }

    var adminRemove = document.getElementById("admin-remove-flat-btn");
    if (adminRemove) {
      adminRemove.addEventListener("click", async function () {
        if (!selectedFlatId) return;
        var card = document.getElementById("flat-" + selectedFlatId);
        if (card && card.dataset.status === "BOOKED") {
          showAdminError("Cannot delete a booked flat. Cancel the booking first.");
          return;
        }
        var label = card && card.querySelector(".flat-number") ? card.querySelector(".flat-number").textContent : selectedFlatId;
        if (
          !window.confirm(
            "Permanently delete flat " + label + "? This removes the unit from the grid and cannot be undone."
          )
        ) {
          return;
        }
        showAdminError("");
        var headers = Object.assign({}, csrfHeaders());
        var res = await fetch(appRoot() + "/flats/" + selectedFlatId, {
          method: "DELETE",
          headers: headers,
        });
        if (!res.ok) {
          showAdminError(await parseErrorResponse(res));
          return;
        }
        var modalEl = document.getElementById("flat-details-modal");
        if (modalEl && window.bootstrap && bootstrap.Modal) {
          bootstrap.Modal.getOrCreateInstance(modalEl).hide();
        }
        if (card) {
          card.remove();
        }
        selectedFlatId = null;
        await refreshGrid();
      });
    }

    var addFloorNumber = null;
    grid.querySelectorAll(".flat-add-unit-btn").forEach(function (btn) {
      btn.addEventListener("click", function () {
        addFloorNumber = btn.getAttribute("data-floor-number");
        var title = document.getElementById("flat-add-modal-title");
        if (title) {
          title.textContent = "Add unit to floor " + addFloorNumber;
        }
        var err = document.getElementById("flat-add-error");
        if (err) {
          err.textContent = "";
          err.classList.add("d-none");
        }
        var bhkSel = document.getElementById("flat-add-bhk");
        if (bhkSel) {
          bhkSel.value = "2BHK";
        }
        var superBuilderInput = document.getElementById("flat-add-super-builder-area");
        var carpetInput = document.getElementById("flat-add-carpet-area");
        var balconyInput = document.getElementById("flat-add-balcony-area");
        if (superBuilderInput) {
          superBuilderInput.value = "";
        }
        if (carpetInput) {
          carpetInput.value = "";
        }
        if (balconyInput) {
          balconyInput.value = "";
        }
        var priceInput = document.getElementById("flat-add-price");
        if (priceInput) {
          priceInput.value = "";
        }
        var addUnitSel = document.getElementById("flat-add-area-unit");
        if (addUnitSel) {
          addUnitSel.value = getAreaUnit();
        }
        updateAreaUnitLabels();
        updateFlatAddPlaceholders();
        var addModal = document.getElementById("flat-add-modal");
        if (addModal && window.bootstrap && bootstrap.Modal) {
          bootstrap.Modal.getOrCreateInstance(addModal).show();
        }
      });
    });

    var flatAddSubmit = document.getElementById("flat-add-submit");
    if (flatAddSubmit) {
      flatAddSubmit.addEventListener("click", async function () {
        if (!addFloorNumber) return;
        var buildingId = grid.getAttribute("data-building-id");
        if (!buildingId) return;
        var err = document.getElementById("flat-add-error");
        var bhkSel = document.getElementById("flat-add-bhk");
        var superBuilderInput = document.getElementById("flat-add-super-builder-area");
        var carpetInput = document.getElementById("flat-add-carpet-area");
        var balconyInput = document.getElementById("flat-add-balcony-area");
        var priceInput = document.getElementById("flat-add-price");
        var payload = {
          floorNumber: parseInt(addFloorNumber, 10),
          bhkType: bhkSel ? bhkSel.value : "2BHK",
        };
        if (superBuilderInput && superBuilderInput.value.trim()) {
          payload.areaSqft = parseAreaInputToSqft(superBuilderInput.value);
        }
        if (carpetInput && carpetInput.value.trim()) {
          payload.carpetAreaSqft = parseAreaInputToSqft(carpetInput.value);
        }
        if (balconyInput && balconyInput.value.trim()) {
          payload.balconyAreaSqft = parseAreaInputToSqft(balconyInput.value);
        }
        if (priceInput && priceInput.value.trim()) {
          payload.basePrice = parseFloat(priceInput.value);
        }
        if (err) {
          err.textContent = "";
          err.classList.add("d-none");
        }
        flatAddSubmit.disabled = true;
        try {
          var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
          var res = await fetch(appRoot() + "/buildings/" + buildingId + "/flats/add-to-floor", {
            method: "POST",
            headers: headers,
            body: JSON.stringify(payload),
          });
          if (!res.ok) {
            if (err) {
              err.textContent = await parseErrorResponse(res);
              err.classList.remove("d-none");
            }
            return;
          }
          var data = await res.json();
          var addModal = document.getElementById("flat-add-modal");
          if (addModal && window.bootstrap && bootstrap.Modal) {
            bootstrap.Modal.getOrCreateInstance(addModal).hide();
          }
          await refreshGrid();
          showGridToast(
            "Added unit " + (data.flatNumber || "") + " (" + (data.bhkType || "") + ") on floor " + addFloorNumber,
            "success",
          );
          if (data.id) {
            var newCard = document.getElementById("flat-" + data.id);
            if (newCard) {
              newCard.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
              newCard.classList.add("flat-card--focused");
              setTimeout(function () {
                newCard.classList.remove("flat-card--focused");
              }, 4000);
            }
          }
        } finally {
          flatAddSubmit.disabled = false;
        }
      });
    }

    var adminPartnerSave = document.getElementById("admin-partner-save");
    if (adminPartnerSave) {
      adminPartnerSave.addEventListener("click", async function () {
        if (!selectedFlatId) return;
        var partnerSel = document.getElementById("admin-partner");
        var partnerUserId = partnerSel && partnerSel.value ? partnerSel.value : null;
        showAdminError("");
        var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
        var res = await fetch(appRoot() + "/flats/" + selectedFlatId + "/partner", {
          method: "POST",
          headers: headers,
          body: JSON.stringify({ partnerUserId: partnerUserId }),
        });
        if (!res.ok) {
          showAdminError(await parseErrorResponse(res));
          return;
        }
        var data = await res.json();
        var card = document.getElementById("flat-" + selectedFlatId);
        var pid = data.partnerUserId ? String(data.partnerUserId) : "";
        var pname = data.partnerName ? String(data.partnerName) : "";
        syncPartnerTag(card, pid || null, pname || null);
        if (partnerSel) partnerSel.value = pid;
      });
    }

    var adminMerge = document.getElementById("admin-merge-btn");
    if (adminMerge) {
      adminMerge.addEventListener("click", async function () {
        if (!selectedFlatId) return;
        var removeSel = document.getElementById("admin-merge-remove");
        var removeId = removeSel ? removeSel.value : "";
        if (!removeId) {
          showAdminError("Choose which flat to link.");
          return;
        }
        var form = readAdminForm();
        var selectedOpt = removeSel.options[removeSel.selectedIndex];
        var verticalDuplex = selectedOpt && selectedOpt.dataset.verticalDuplex === "true";
        var confirmMsg = verticalDuplex
          ? "Create vertical duplex? The lower-floor unit stays bookable; the upper unit is linked (not deleted). Continue?"
          : "Merge will hide the selected unit on this floor and keep this flat with the details above. You can restore it later. Continue?";
        if (!window.confirm(confirmMsg)) {
          return;
        }
        showAdminError("");
        var body = {
          removeFlatId: removeId,
          bhkType: form.bhkType,
          areaSqft: form.areaSqft,
          carpetAreaSqft: form.carpetAreaSqft,
          balconyAreaSqft: form.balconyAreaSqft,
          basePrice: form.basePrice,
        };
        var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
        var res = await fetch(appRoot() + "/flats/" + selectedFlatId + "/merge", {
          method: "POST",
          headers: headers,
          body: JSON.stringify(body),
        });
        if (!res.ok) {
          showAdminError(await parseErrorResponse(res));
          return;
        }
        var flat = await res.json();
        var keepId = flat.id ? String(flat.id) : selectedFlatId;
        await afterLayoutChange(keepId, null);
      });
    }

    var adminSplitMerge = document.getElementById("admin-split-merge-btn");
    if (adminSplitMerge) {
      adminSplitMerge.addEventListener("click", async function () {
        if (!selectedFlatId) return;
        var card = document.getElementById("flat-" + selectedFlatId);
        var absorbedNo =
          card && card.dataset.mergeAbsorbedNumber ? card.dataset.mergeAbsorbedNumber : "the merged unit";
        if (
          !window.confirm(
            "Restore " +
              absorbedNo +
              " as a separate flat? This flat will revert to its pre-merge type, area, and price."
          )
        ) {
          return;
        }
        showAdminError("");
        var headers = Object.assign({}, csrfHeaders());
        var res = await fetch(appRoot() + "/flats/" + selectedFlatId + "/split-merge", {
          method: "POST",
          headers: headers,
        });
        if (!res.ok) {
          showAdminError(await parseErrorResponse(res));
          return;
        }
        var result = await res.json();
        var keepId = result.id ? String(result.id) : selectedFlatId;
        var restoredId = result.restoredFlatId ? String(result.restoredFlatId) : null;
        await afterLayoutChange(keepId, null, { showModal: false });
        if (result.message) {
          showGridToast(result.message);
        }
        var restoredCard = restoredId ? document.getElementById("flat-" + restoredId) : null;
        var keepCard = document.getElementById("flat-" + keepId);
        if (restoredCard) {
          restoredCard.classList.add("flat-card--focused");
          restoredCard.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
        }
        if (keepCard) {
          keepCard.classList.add("flat-card--focused");
        }
        setTimeout(function () {
          if (restoredCard) restoredCard.classList.remove("flat-card--focused");
          if (keepCard) keepCard.classList.remove("flat-card--focused");
        }, 5000);
      });
    }

    var adminSplitDuplex = document.getElementById("admin-split-duplex-btn");
    if (adminSplitDuplex) {
      adminSplitDuplex.addEventListener("click", async function () {
        if (!selectedFlatId) return;
        if (
          !window.confirm(
            "Split this duplex back into two separate units? Bookings must be cleared from the primary flat first."
          )
        ) {
          return;
        }
        showAdminError("");
        var headers = Object.assign({}, csrfHeaders());
        var res = await fetch(appRoot() + "/flats/" + selectedFlatId + "/split-duplex", {
          method: "POST",
          headers: headers,
        });
        if (!res.ok) {
          showAdminError(await parseErrorResponse(res));
          return;
        }
        var flat = await res.json();
        var keepId = flat.id ? String(flat.id) : selectedFlatId;
        await afterLayoutChange(keepId, null);
      });
    }

    if (grid) {
      grid.addEventListener(
        "click",
        function (e) {
          var quick = e.target.closest(".flat-quick-link");
          if (quick) {
            e.preventDefault();
            e.stopPropagation();
            var cardFromQuick = quick.closest(".flat-card");
            if (cardFromQuick && canOpenFlatPanel(cardFromQuick)) {
              window.floor21SelectFlat(cardFromQuick);
            }
            return;
          }
          var parkingSlot = e.target.closest(".parking-plan__slot--clickable");
          if (parkingSlot) {
            e.preventDefault();
            e.stopPropagation();
            window.floor21SelectParkingSlot(parkingSlot);
            return;
          }
          var parkingConfigure = e.target.closest(".flat-parking-configure-link");
          if (parkingConfigure) {
            e.preventDefault();
            e.stopPropagation();
            var sectionCfg = parkingConfigure.closest(".flat-parking-section");
            if (sectionCfg) openParkingConfigModal(sectionCfg);
            return;
          }
          var parkingLayoutUpload = e.target.closest(".flat-parking-layout-upload-link");
          if (parkingLayoutUpload) {
            e.preventDefault();
            e.stopPropagation();
            if (!isPlatformAdminEdit()) return;
            var sectionUpload = parkingLayoutUpload.closest(".flat-parking-section");
            var fileInput = document.getElementById("parking-layout-file-input");
            if (!sectionUpload || !fileInput) return;
            parkingLayoutUploadSection = sectionUpload;
            fileInput.value = "";
            fileInput.click();
            return;
          }
          var parkingLayoutView = e.target.closest(".flat-parking-layout-view-link");
          if (parkingLayoutView) {
            e.preventDefault();
            e.stopPropagation();
            var sectionView = parkingLayoutView.closest(".flat-parking-section");
            if (!sectionView) return;
            openParkingLayoutModal(sectionView.getAttribute("data-floor-number"));
            return;
          }
          var card = e.target.closest(".flat-card");
          if (!card || !grid.contains(card)) return;
          if (!canOpenFlatPanel(card)) return;
          window.floor21SelectFlat(card);
        }
      );
      var focusId = grid.getAttribute("data-focus-flat-id");
      if (focusId) {
        var card = document.getElementById("flat-" + focusId);
        if (card) {
          card.classList.add("flat-card--focused");
          card.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
          setTimeout(function () {
            card.classList.remove("flat-card--focused");
          }, 4000);
        }
      }
    }
  });
})();
