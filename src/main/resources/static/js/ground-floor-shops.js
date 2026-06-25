(function () {
  "use strict";

  var DEFAULT_PARKING_CAR_SIZE_PERCENT = 180;
  var DEFAULT_SHOP_SIZE_PERCENT = 140;
  var DEFAULT_PANEL_WIDTH_SCALE = 0.82;
  var DEFAULT_GROUND_PANEL_HEIGHT_SCALE = 1.25;

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

  function isPlatformAdminEdit() {
    var grid = document.getElementById("flat-grid");
    return grid && grid.getAttribute("data-platform-admin-edit") === "true";
  }

  function buildingId() {
    var grid = document.getElementById("flat-grid");
    return grid ? grid.getAttribute("data-building-id") : null;
  }

  function formatAreaDualDisplay(sqft) {
    return window.Floor21AreaUnit && window.Floor21AreaUnit.formatDualDisplay
      ? window.Floor21AreaUnit.formatDualDisplay(sqft)
      : "—";
  }

  function setAreaPair(pairId, sqftValue) {
    if (window.Floor21AreaUnit && window.Floor21AreaUnit.setPairFromSqft) {
      window.Floor21AreaUnit.setPairFromSqft(pairId, sqftValue);
    }
  }

  function readAreaPair(pairId) {
    return window.Floor21AreaUnit && window.Floor21AreaUnit.readSqftFromPair
      ? window.Floor21AreaUnit.readSqftFromPair(pairId)
      : null;
  }

  function readConfigAreaSqft(pairId) {
    var modal = configModalEl();
    var areaUnit = window.Floor21AreaUnit;
    if (!modal || !areaUnit) return readAreaPair(pairId);
    var input = modal.querySelector("#" + pairId);
    if (!input) return readAreaPair(pairId);
    var unit = areaUnit.readUnitFromControl(modal.querySelector("#" + pairId + "-unit"));
    var raw = input.value.trim();
    if (raw === "") return null;
    var num = Number(raw);
    if (isNaN(num)) return null;
    return unit === "sqm" ? areaUnit.sqmToSqftNumber(num) : num;
  }

  var GROUND_CONFIG_SUCCESS_MSG = "Saved values";

  function configModalEl() {
    var matches = document.querySelectorAll("#ground-floor-config-modal");
    if (!matches.length) return null;
    return matches[matches.length - 1];
  }

  function configEl(id) {
    var modal = configModalEl();
    if (!modal) return document.getElementById(id);
    return modal.querySelector("#" + id);
  }

  function showConfigStatus(message, tone) {
    var el = configEl("ground-floor-config-status");
    if (!el) return;
    clearTimeout(window._groundConfigSuccessHideTimer);
    window._groundConfigSuccessHideTimer = null;
    if (!message) {
      el.textContent = "";
      el.classList.add("d-none");
      el.classList.remove("alert-danger");
      el.classList.add("alert-info");
      return;
    }
    el.textContent = message;
    el.classList.remove("d-none");
    el.classList.remove("alert-info", "alert-danger");
    el.classList.add(tone === "error" ? "alert-danger" : "alert-info");
    if (typeof el.scrollIntoView === "function") {
      el.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }
    if (tone !== "error") {
      window._groundConfigSuccessHideTimer = setTimeout(function () {
        showConfigStatus("", "success");
      }, 8000);
    }
  }

  function showConfigError(message) {
    if (!message) {
      showConfigStatus("", "success");
      return;
    }
    showConfigStatus(message, "error");
  }

  function showConfigSuccess(message) {
    showConfigStatus(message, "success");
  }

  function closeGroundFloorConfigModal() {
    var modal = configModalEl();
    if (!modal || typeof bootstrap === "undefined" || !bootstrap.Modal) return;
    var instance = bootstrap.Modal.getInstance(modal);
    if (instance) {
      instance.hide();
    }
  }

  function showLayoutError(rootEl, message) {
    var el = rootEl ? rootEl.querySelector(".shop-plan__layout-error") : null;
    if (!el) return;
    if (!message) {
      el.textContent = "";
      el.classList.add("d-none");
      return;
    }
    el.textContent = message;
    el.classList.remove("d-none");
  }

  function groundFloorPanel() {
    return document.querySelector(".flat-ground-floor-section__panel");
  }

  function groundFloorPlanRoot() {
    var panel = groundFloorPanel();
    return panel ? panel.querySelector(".flat-ground-floor-section__plan-root") : null;
  }

  function shopStatusClass(status) {
    if (status === "BOOKED") return "shop-plan__slot--booked";
    if (status === "HOLD") return "shop-plan__slot--hold";
    if (status === "CANCELLED") return "shop-plan__slot--deactivated";
    return "shop-plan__slot--available";
  }

  function findShopSlot(plan, slotNumber) {
    var list = plan.shops || plan.slots || [];
    for (var i = 0; i < list.length; i++) {
      if (list[i].slotNumber === slotNumber) return list[i];
    }
    return null;
  }

  function findParkingSlot(plan, slotNumber) {
    var list = plan.parkingSlots || [];
    for (var i = 0; i < list.length; i++) {
      if (list[i].slotNumber === slotNumber) return list[i];
    }
    return null;
  }

  function findShopPlacement(plan, slotNumber) {
    var list = plan.shopPlacements || plan.placements || [];
    for (var i = 0; i < list.length; i++) {
      if (list[i].slotNumber === slotNumber) return list[i];
    }
    return null;
  }

  function findParkingPlacement(plan, slotNumber) {
    var list = plan.parkingPlacements || [];
    for (var i = 0; i < list.length; i++) {
      if (list[i].slotNumber === slotNumber) return list[i];
    }
    return null;
  }

  function fixtureUiMeta(kind) {
    var k = kind === "LIFT" ? "CAR_LIFT" : kind;
    if (k === "GATE") {
      return { kind: "GATE", label: "G", title: "Gate", css: " shop-plan__fixture--gate" };
    }
    if (k === "PASSENGER_LIFT") {
      return {
        kind: "PASSENGER_LIFT",
        label: "PL",
        subtitle: "Passenger",
        title: "Passenger lift",
        css: " shop-plan__fixture--passenger-lift",
      };
    }
    return {
      kind: "CAR_LIFT",
      label: "CL",
      subtitle: "Car lift",
      title: "Car lift",
      css: " shop-plan__fixture--car-lift",
    };
  }

  function renderFixtureIcon(kind) {
    if (kind === "PASSENGER_LIFT") {
      return (
        '<svg class="shop-plan__fixture-icon" viewBox="0 0 24 24" aria-hidden="true">' +
        '<path d="M6 4h12v16H6z" fill="none" stroke="currentColor" stroke-width="1.5"/>' +
        '<path d="M9 7h6M9 10.5h6M9 14h6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>' +
        "</svg>"
      );
    }
    if (kind === "CAR_LIFT") {
      return (
        '<svg class="shop-plan__fixture-icon" viewBox="0 0 24 24" aria-hidden="true">' +
        '<rect x="7" y="4.5" width="10" height="15" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.5"/>' +
        '<path d="M9.5 8h5M9.5 11.5h5M9.5 15h5" stroke="currentColor" stroke-width="1.3" stroke-linecap="round"/>' +
        "</svg>"
      );
    }
    return "";
  }

  function renderPlanFixture(placement, canEdit, snapshotReadOnly) {
    if (!placement) return "";
    var ui = fixtureUiMeta(placement.kind);
    var orientClass =
      placement.orientation === "horizontal" ? " shop-plan__fixture--horizontal" : " shop-plan__fixture--vertical";
    var dragClass = canEdit && !snapshotReadOnly ? " shop-plan__fixture--draggable" : "";
    var gridStyle =
      ' style="grid-column:' +
      (placement.col + 1) +
      ";grid-row:" +
      (placement.row + 1) +
      '"';
    return (
      '<div class="shop-plan__fixture' +
      ui.css +
      orientClass +
      dragClass +
      '" data-fixture-kind="' +
      ui.kind +
      '" data-fixture-index="' +
      placement.index +
      '"' +
      (canEdit && !snapshotReadOnly ? ' draggable="true"' : "") +
      ' title="' +
      ui.title +
      " " +
      placement.index +
      " (shared)" +
      '"' +
      gridStyle +
      ">" +
      renderFixtureIcon(ui.kind) +
      '<span class="shop-plan__fixture-label">' +
      ui.label +
      placement.index +
      "</span>" +
      (ui.subtitle ? '<span class="shop-plan__fixture-sub">' + ui.subtitle + "</span>" : "") +
      "</div>"
    );
  }

  function renderParkingCarSvg() {
    return (
      '<svg class="shop-plan__car-svg" viewBox="0 0 50 108" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
      '<ellipse class="shop-plan__car-wheel" cx="7" cy="28" rx="7" ry="9"/>' +
      '<ellipse class="shop-plan__car-wheel" cx="43" cy="28" rx="7" ry="9"/>' +
      '<ellipse class="shop-plan__car-wheel" cx="7" cy="80" rx="7" ry="9"/>' +
      '<ellipse class="shop-plan__car-wheel" cx="43" cy="80" rx="7" ry="9"/>' +
      '<rect class="shop-plan__car-body" x="10" y="18" width="30" height="72" rx="6"/>' +
      '<rect class="shop-plan__car-roof" x="16" y="26" width="18" height="28" rx="3"/>' +
      "</svg>"
    );
  }

  function renderShopPlanSlot(slot, placement, canEdit) {
    if (!slot) return "";
    var statusClass = shopStatusClass(slot.status);
    var dragClass = canEdit ? " shop-plan__slot--draggable" : "";
    var gridStyle = placement
      ? ' style="grid-column:' +
        (placement.col + 1) +
        ";grid-row:" +
        (placement.row + 1) +
        '"'
      : "";
    var areaLabel =
      slot.areaSqft != null && slot.areaSqft !== ""
        ? " · " + formatAreaDualDisplay(slot.areaSqft)
        : "";
    var title = "Shop " + slot.slotNumber + (slot.flatNumber ? " — " + slot.flatNumber : "") + areaLabel;
    return (
      '<div class="shop-plan__slot shop-plan__slot--clickable shop-plan__slot--shop ' +
      statusClass +
      dragClass +
      '" data-drag-kind="shop" data-shop-flat-id="' +
      (slot.flatId || "") +
      '" data-slot-number="' +
      slot.slotNumber +
      '" data-flat-number="' +
      (slot.flatNumber || "") +
      '" data-status="' +
      (slot.status || "AVAILABLE") +
      '" data-area="' +
      (slot.areaSqft != null ? slot.areaSqft : "") +
      '" data-price="' +
      (slot.basePrice != null ? slot.basePrice : "") +
      '" data-bookable="' +
      (slot.bookableByCurrentUser !== false ? "true" : "false") +
      '" data-client-id="' +
      (slot.clientId || "") +
      '" data-payment-received="' +
      (slot.paymentReceived != null ? slot.paymentReceived : "") +
      '" data-remaining-balance="' +
      (slot.remainingBalance != null ? slot.remainingBalance : "") +
      '" data-partner-id="' +
      (slot.assignedPartnerId || "") +
      '" data-partner-name="' +
      (slot.assignedPartnerName || "").replace(/"/g, "&quot;") +
      '" title="' +
      title.replace(/"/g, "&quot;") +
      '"' +
      (canEdit ? ' draggable="true"' : "") +
      gridStyle +
      ">" +
      '<div class="shop-plan__bay"><span class="shop-plan__label">Shop</span></div>' +
      '<span class="shop-plan__slot-no">' +
      slot.slotNumber +
      "</span>" +
      (slot.flatNumber ? '<span class="shop-plan__unit-no">' + slot.flatNumber + "</span>" : "") +
      (slot.assignedPartnerName
        ? '<span class="shop-plan__partner-tag">' + slot.assignedPartnerName + "</span>"
        : "") +
      "</div>"
    );
  }

  function renderGroundParkingSlot(slot, placement, canEdit, snapshotReadOnly) {
    if (!slot) return "";
    var linked = slot.linkedResidentialFlatNumber || "";
    var linkedClass = linked ? " shop-plan__slot--linked" : "";
    var interactive = canEdit || snapshotReadOnly;
    var clickable = interactive ? " shop-plan__slot--clickable" : "";
    var orientClass =
      placement && placement.orientation === "horizontal"
        ? " shop-plan__slot--horizontal"
        : " shop-plan__slot--vertical";
    var dragClass = canEdit && !snapshotReadOnly ? " shop-plan__slot--draggable" : "";
    var gridStyle = placement
      ? ' style="grid-column:' +
        (placement.col + 1) +
        ";grid-row:" +
        (placement.row + 1) +
        '"'
      : "";
    var title = linked
      ? "Parking " + slot.slotNumber + " — linked to flat " + linked
      : canEdit
        ? "Parking " + slot.slotNumber + " — guest parking (click to link to a flat)"
        : "Parking " + slot.slotNumber + " — guest parking";
    return (
      '<div class="shop-plan__slot shop-plan__slot--parking shop-plan__slot--parking-shared' +
      linkedClass +
      clickable +
      orientClass +
      dragClass +
      '" data-drag-kind="parking" data-parking-flat-id="' +
      (slot.flatId || "") +
      '" data-slot-number="' +
      slot.slotNumber +
      '" data-flat-number="' +
      (slot.flatNumber || "") +
      '"' +
      (slot.linkedResidentialFlatId
        ? ' data-linked-flat-id="' + slot.linkedResidentialFlatId + '"'
        : "") +
      (linked ? ' data-linked-flat-number="' + linked + '"' : "") +
      ' title="' +
      title.replace(/"/g, "&quot;") +
      '"' +
      (canEdit && !snapshotReadOnly ? ' draggable="true"' : "") +
      gridStyle +
      ">" +
      '<div class="shop-plan__bay shop-plan__bay--parking">' +
      renderParkingCarSvg() +
      (linked ? '<span class="shop-plan__linked-flat">' + linked + "</span>" : "") +
      "</div>" +
      '<span class="shop-plan__slot-no' +
      (linked ? " shop-plan__slot-no--linked" : "") +
      '">' +
      slot.slotNumber +
      "</span>" +
      "</div>"
    );
  }

  function parkingCarScale(plan) {
    var pct =
      plan && plan.parkingCarSizePercent != null
        ? plan.parkingCarSizePercent
        : DEFAULT_PARKING_CAR_SIZE_PERCENT;
    return Math.max(0.5, Math.min(2, pct / 100));
  }

  function shopCarScale(plan) {
    var pct =
      plan && plan.shopSizePercent != null ? plan.shopSizePercent : DEFAULT_SHOP_SIZE_PERCENT;
    return Math.max(0.5, Math.min(2, pct / 100));
  }

  function clampPanelPercent(pct) {
    return Math.max(50, Math.min(200, Math.round(pct)));
  }

  function snapPanelPercent(pct) {
    return Math.round(clampPanelPercent(pct) / 5) * 5;
  }

  function groundPanelPercents(panel) {
    return {
      shop: Number(panel.dataset.shopSizePercent || DEFAULT_SHOP_SIZE_PERCENT),
      parking: Number(panel.dataset.parkingCarSizePercent || DEFAULT_PARKING_CAR_SIZE_PERCENT),
    };
  }

  function resetGroundPanelContent(panel) {
    panel.querySelectorAll(".shop-plan__sheet").forEach(function (sheet) {
      sheet.style.setProperty("--shop-car-scale", "1");
      sheet.style.setProperty("--shop-parking-car-scale", "1");
    });
  }

  function groundPanelStorageKey(panel) {
    var id = buildingId();
    return "floor21:panel:" + (id || "") + ":ground";
  }

  function groundPanelResizeOptions(panel) {
    return {
      layoutSelector: ".flat-ground-floor-section__layout",
      storageKey: function () {
        return groundPanelStorageKey(panel);
      },
      defaultWidthScale: DEFAULT_PANEL_WIDTH_SCALE,
      defaultHeightScale: DEFAULT_GROUND_PANEL_HEIGHT_SCALE,
      minWidth: 280,
      minHeight: 120,
      resetContent: resetGroundPanelContent,
      onResizeEnd: function (p) {
        if (!window.floor21PanelResize) {
          return;
        }
        var current = groundPanelPercents(p);
        var scale = window.floor21PanelResize.uniformScale(p);
        var finalShop = snapPanelPercent(scale * 100);
        var parkingRatio = current.shop > 0 ? current.parking / current.shop : 1;
        var finalParking = snapPanelPercent(finalShop * parkingRatio);
        void persistGroundPanelScale(p, finalShop, finalParking);
      },
    };
  }

  function applyGroundPanelScale(panel, shopPct, parkingPct) {
    if (!panel) {
      return;
    }
    shopPct = clampPanelPercent(shopPct);
    parkingPct = clampPanelPercent(parkingPct);
    panel.dataset.shopSizePercent = String(shopPct);
    panel.dataset.parkingCarSizePercent = String(parkingPct);

    var root = groundFloorPlanRoot();
    if (root && root._shopLayoutState && root._shopLayoutState.plan) {
      root._shopLayoutState.plan.shopSizePercent = shopPct;
      root._shopLayoutState.plan.parkingCarSizePercent = parkingPct;
    }

    if (panel.classList.contains("flat-ground-floor-section__panel--resizable") && window.floor21PanelResize) {
      window.floor21PanelResize.remeasure(panel, groundPanelResizeOptions(panel));
      return;
    }

    panel.style.width = "";
    panel.style.height = "";
    var shopScale = shopPct / 100;
    var parkingScale = parkingPct / 100;
    panel.querySelectorAll(".shop-plan__sheet").forEach(function (sheet) {
      sheet.style.setProperty("--shop-car-scale", String(shopScale));
      sheet.style.setProperty("--shop-parking-car-scale", String(parkingScale));
    });
  }

  function syncGroundResizablePanel(panel) {
    if (!panel || !window.floor21PanelResize) {
      return;
    }
    window.floor21PanelResize.remeasure(panel, groundPanelResizeOptions(panel));
  }

  function ensureGroundFloorResizeHandle(panel) {
    if (!panel || !isPlatformAdminEdit()) {
      return;
    }
    if (panel.dataset.configured !== "true") {
      return;
    }
    if (!panel.classList.contains("flat-ground-floor-section--split")) {
      return;
    }
    panel.classList.add("flat-ground-floor-section__panel--resizable");
    if (window.floor21PanelResize) {
      window.floor21PanelResize.init(panel, groundPanelResizeOptions(panel));
    }
  }

  async function persistGroundPanelScale(panel, shopPct, parkingPct) {
    var id = buildingId();
    if (!id || !panel) {
      return { ok: false };
    }
    var shopCount = Number(panel.dataset.shopCount);
    var shopAreaSqft = Number(panel.dataset.shopArea);
    var parkingSlotCount = Number(panel.dataset.parkingSlotCount || "0");
    var parkingSlotAreaSqft = Number(panel.dataset.parkingSlotArea || "0");
    if (!shopCount || shopCount < 1 || !shopAreaSqft || shopAreaSqft <= 0) {
      return { ok: false };
    }
    var shopSizePercent = snapPanelPercent(shopPct);
    var parkingCarSizePercent = snapPanelPercent(parkingPct);
    var res = await fetch(appRoot() + "/buildings/" + id + "/ground-floor-config", {
      method: "POST",
      headers: Object.assign({ "Content-Type": "application/json" }, csrfHeaders()),
      body: JSON.stringify({
        shopCount: shopCount,
        shopAreaSqft: shopAreaSqft,
        shopSizePercent: shopSizePercent,
        parkingSlotCount: parkingSlotCount,
        parkingSlotAreaSqft: parkingSlotCount > 0 ? parkingSlotAreaSqft : null,
        parkingCarSizePercent: parkingCarSizePercent,
        carLiftCount: Number(panel.dataset.carLiftCount || "1"),
        passengerLiftCount: Number(panel.dataset.passengerLiftCount || "0"),
        gateCount: Number(panel.dataset.gateCount || "1"),
      }),
    });
    if (!res.ok) {
      return { ok: false, error: await parseErrorResponse(res) };
    }
    var groundFloor = await res.json();
    var planRes = await fetch(appRoot() + "/buildings/" + id + "/ground-floor/plan", {
      headers: { Accept: "application/json" },
    });
    var plan = planRes.ok ? await planRes.json() : null;
    updatePanelMeta(groundFloor, plan);
    if (plan) {
      renderShopPlanGrid(plan, groundFloorPlanRoot());
      ensureGroundFloorResizeHandle(panel);
    }
    return { ok: true };
  }

  function clonePlan(plan) {
    return {
      shopCount: plan.shopCount,
      parkingSlotCount: plan.parkingSlotCount || 0,
      gridCols: plan.gridCols,
      gridRows: plan.gridRows,
      minGridRows: plan.minGridRows,
      shopPlacements: (plan.shopPlacements || plan.placements || []).map(copyPlacement),
      parkingPlacements: (plan.parkingPlacements || []).map(copyPlacement),
      shops: plan.shops || plan.slots || [],
      parkingSlots: plan.parkingSlots || [],
      fixtures: (plan.fixtures || []).map(function (f) {
        return {
          kind: f.kind === "LIFT" ? "CAR_LIFT" : f.kind,
          index: f.index,
          col: f.col,
          row: f.row,
          orientation: f.orientation || "vertical",
        };
      }),
      carLiftCount: plan.carLiftCount,
      passengerLiftCount: plan.passengerLiftCount,
      gateCount: plan.gateCount,
      parkingCarSizePercent: plan.parkingCarSizePercent,
      shopSizePercent: plan.shopSizePercent,
    };
  }

  function copyPlacement(p) {
    return {
      slotNumber: p.slotNumber,
      col: p.col,
      row: p.row,
      orientation: p.orientation || "vertical",
    };
  }

  function renderShopPlanGrid(plan, rootEl) {
    if (!rootEl || !plan) return;
    var canEdit = isPlatformAdminEdit();
    var cols = plan.gridCols || 14;
    var rows = plan.gridRows || 8;
    var cellsHtml = "";
    var r;
    var c;
    for (r = 0; r < rows; r++) {
      for (c = 0; c < cols; c++) {
        cellsHtml +=
          '<div class="shop-plan__cell' +
          (canEdit ? " shop-plan__cell--drop" : "") +
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
    var shopPlacements = plan.shopPlacements || plan.placements || [];
    var shopsHtml = shopPlacements
      .map(function (p) {
        return renderShopPlanSlot(findShopSlot(plan, p.slotNumber), p, canEdit);
      })
      .join("");
    var parkingHtml = (plan.parkingPlacements || [])
      .map(function (p) {
        return renderGroundParkingSlot(findParkingSlot(plan, p.slotNumber), p, canEdit);
      })
      .join("");
    var fixturesHtml = (plan.fixtures || [])
      .map(function (f) {
        return renderPlanFixture(f, canEdit);
      })
      .join("");
    var toolbar = canEdit
      ? '<div class="shop-plan__layout-toolbar">' +
        '<div class="shop-plan__grid-toolbar">' +
        '<div class="shop-plan__row-toolbar btn-group btn-group-sm" role="group">' +
        '<button type="button" class="btn btn-outline-secondary shop-plan__grid-btn" data-shop-row-action="INSERT_TOP">+ Top</button>' +
        '<button type="button" class="btn btn-outline-secondary shop-plan__grid-btn" data-shop-row-action="INSERT_BOTTOM">+ Bottom</button>' +
        '<button type="button" class="btn btn-outline-secondary shop-plan__grid-btn" data-shop-row-action="REMOVE_TOP">− Top</button>' +
        '<button type="button" class="btn btn-outline-secondary shop-plan__grid-btn" data-shop-row-action="REMOVE_BOTTOM">− Bottom</button>' +
        "</div>" +
        '<div class="shop-plan__col-toolbar btn-group btn-group-sm ms-2" role="group">' +
        '<button type="button" class="btn btn-outline-secondary shop-plan__grid-btn" data-shop-col-action="INSERT_LEFT">+ Left</button>' +
        '<button type="button" class="btn btn-outline-secondary shop-plan__grid-btn" data-shop-col-action="INSERT_RIGHT">+ Right</button>' +
        '<button type="button" class="btn btn-outline-secondary shop-plan__grid-btn" data-shop-col-action="REMOVE_LEFT">− Left</button>' +
        '<button type="button" class="btn btn-outline-secondary shop-plan__grid-btn" data-shop-col-action="REMOVE_RIGHT">− Right</button>' +
        "</div></div>" +
        '<span class="text-muted small shop-plan__layout-hint">Drag shops, parking bays, car lifts (CL), passenger lifts (PL), and gates (G). Click a shop to book or edit. Click a parking bay to link it to a residential flat or leave as guest parking.</span>' +
        '<span class="text-danger small shop-plan__layout-error d-none"></span></div>'
      : "";
    rootEl.innerHTML =
      '<div class="shop-plan__sheet shop-plan__sheet--grid" style="--shop-car-scale:' +
      shopCarScale(plan) +
      ";--shop-parking-car-scale:" +
      parkingCarScale(plan) +
      '">' +
      toolbar +
      '<div class="shop-plan__grid" style="--shop-grid-cols:' +
      cols +
      ";--shop-grid-rows:" +
      rows +
      '">' +
      cellsHtml +
      shopsHtml +
      parkingHtml +
      fixturesHtml +
      "</div></div>";
    rootEl._shopLayoutState = {
      gridCols: cols,
      gridRows: rows,
      shopPlacements: shopPlacements.map(copyPlacement),
      parkingPlacements: (plan.parkingPlacements || []).map(copyPlacement),
      fixtures: (plan.fixtures || []).map(function (f) {
        return {
          kind: f.kind === "LIFT" ? "CAR_LIFT" : f.kind,
          index: f.index,
          col: f.col,
          row: f.row,
          orientation: f.orientation || "vertical",
        };
      }),
      saving: false,
      plan: clonePlan(plan),
    };
    if (canEdit) updateShopGridToolbar(rootEl);
    var panel = groundFloorPanel();
    if (panel && panel.classList.contains("flat-ground-floor-section__panel--resizable")) {
      syncGroundResizablePanel(panel);
    }
  }

  function dragKeyFromEl(el) {
    if (!el) return null;
    var kind = el.getAttribute("data-drag-kind");
    if (kind === "parking") {
      return "parking:" + (el.getAttribute("data-slot-number") || "");
    }
    if (el.classList.contains("shop-plan__fixture--draggable")) {
      return (
        "fixture:" +
        el.getAttribute("data-fixture-kind") +
        ":" +
        el.getAttribute("data-fixture-index")
      );
    }
    if (kind === "shop" || el.classList.contains("shop-plan__slot--shop")) {
      return "shop:" + (el.getAttribute("data-slot-number") || "");
    }
    return null;
  }

  function parseDragKey(raw) {
    if (!raw) return null;
    if (raw.indexOf("fixture:") === 0) {
      var rest = raw.substring(8);
      var lastColon = rest.lastIndexOf(":");
      if (lastColon < 0) return null;
      return {
        type: "fixture",
        kind: rest.substring(0, lastColon),
        index: Number(rest.substring(lastColon + 1)),
      };
    }
    if (raw.indexOf("parking:") === 0) {
      return { type: "parking", slotNumber: Number(raw.substring(8)) };
    }
    if (raw.indexOf("shop:") === 0) {
      return { type: "shop", slotNumber: Number(raw.substring(5)) };
    }
    return null;
  }

  function findMovingItem(state, drag) {
    var i;
    if (drag.type === "shop") {
      for (i = 0; i < state.shopPlacements.length; i++) {
        if (Number(state.shopPlacements[i].slotNumber) === Number(drag.slotNumber)) {
          return { type: "shop", item: state.shopPlacements[i] };
        }
      }
      return null;
    }
    if (drag.type === "parking") {
      for (i = 0; i < state.parkingPlacements.length; i++) {
        if (Number(state.parkingPlacements[i].slotNumber) === Number(drag.slotNumber)) {
          return { type: "parking", item: state.parkingPlacements[i] };
        }
      }
      return null;
    }
    for (i = 0; i < state.fixtures.length; i++) {
      if (state.fixtures[i].kind === drag.kind && Number(state.fixtures[i].index) === Number(drag.index)) {
        return { type: "fixture", item: state.fixtures[i] };
      }
    }
    return null;
  }

  function findGridOccupant(state, col, row, excludeDrag) {
    var i;
    excludeDrag = excludeDrag || {};
    for (i = 0; i < state.shopPlacements.length; i++) {
      var s = state.shopPlacements[i];
      if (Number(s.col) === col && Number(s.row) === row) {
        if (excludeDrag.type === "shop" && Number(excludeDrag.slotNumber) === Number(s.slotNumber)) continue;
        return { type: "shop", item: s };
      }
    }
    for (i = 0; i < state.parkingPlacements.length; i++) {
      var p = state.parkingPlacements[i];
      if (Number(p.col) === col && Number(p.row) === row) {
        if (excludeDrag.type === "parking" && Number(excludeDrag.slotNumber) === Number(p.slotNumber)) continue;
        return { type: "parking", item: p };
      }
    }
    for (i = 0; i < state.fixtures.length; i++) {
      var f = state.fixtures[i];
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

  function moveItemOnGrid(state, drag, toCol, toRow) {
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

  function dropTargetFromEvent(e) {
    var cell = e.target.closest(".shop-plan__cell--drop");
    if (cell) {
      return {
        root: cell.closest(".flat-ground-floor-section__plan-root"),
        col: Number(cell.dataset.col),
        row: Number(cell.dataset.row),
        highlightEl: cell,
      };
    }
    return null;
  }

  function clearCellDragOver() {
    document.querySelectorAll(".shop-plan__cell--drag-over").forEach(function (cell) {
      cell.classList.remove("shop-plan__cell--drag-over");
    });
  }

  function setGridDragActive(root, active) {
    if (!root) return;
    var grid = root.querySelector(".shop-plan__grid");
    if (grid) grid.classList.toggle("shop-plan__grid--dragging", !!active);
  }

  function rerenderFromState(rootEl) {
    var state = rootEl && rootEl._shopLayoutState;
    if (!state || !state.plan) return;
    var plan = clonePlan(state.plan);
    plan.shopPlacements = state.shopPlacements.map(copyPlacement);
    plan.parkingPlacements = state.parkingPlacements.map(copyPlacement);
    plan.fixtures = state.fixtures.map(function (f) {
      return {
        kind: f.kind,
        index: f.index,
        col: f.col,
        row: f.row,
        orientation: f.orientation || "vertical",
      };
    });
    plan.gridCols = state.gridCols;
    plan.gridRows = state.gridRows;
    renderShopPlanGrid(plan, rootEl);
  }

  async function parseErrorResponse(res) {
    try {
      var body = await res.json();
      if (body && body.error) return body.error;
      if (body && body.message) return body.message;
      if (body && body.errors && body.errors.length) {
        var first = body.errors[0];
        return (first && (first.defaultMessage || first.message)) || "Request failed.";
      }
      return "Request failed.";
    } catch (e) {
      return "Request failed.";
    }
  }

  async function persistGroundFloorLayout(rootEl) {
    var state = rootEl && rootEl._shopLayoutState;
    if (!state) return { ok: false, error: "Layout is not available." };
    var id = buildingId();
    if (!id) return { ok: false, error: "Building not found." };
    var res = await fetch(appRoot() + "/buildings/" + id + "/ground-floor/layout", {
      method: "POST",
      headers: Object.assign({ "Content-Type": "application/json" }, csrfHeaders()),
      body: JSON.stringify({
        gridCols: state.gridCols,
        gridRows: state.gridRows,
        shopPlacements: state.shopPlacements,
        parkingPlacements: state.parkingPlacements,
        fixtures: state.fixtures,
      }),
    });
    if (!res.ok) {
      return { ok: false, error: await parseErrorResponse(res) };
    }
    var plan = await res.json();
    state.plan = clonePlan(plan);
    state.shopPlacements = (plan.shopPlacements || plan.placements || []).map(copyPlacement);
    state.parkingPlacements = (plan.parkingPlacements || []).map(copyPlacement);
    state.fixtures = (plan.fixtures || []).map(function (f) {
      return {
        kind: f.kind === "LIFT" ? "CAR_LIFT" : f.kind,
        index: f.index,
        col: f.col,
        row: f.row,
        orientation: f.orientation || "vertical",
      };
    });
    state.gridCols = plan.gridCols;
    state.gridRows = plan.gridRows;
    return { ok: true, plan: plan };
  }

  async function autoSaveLayout(rootEl) {
    var state = rootEl && rootEl._shopLayoutState;
    if (!state || state.saving) return;
    state.saving = true;
    showLayoutError(rootEl, "");
    var result = await persistGroundFloorLayout(rootEl);
    state.saving = false;
    if (!result.ok) {
      showLayoutError(rootEl, result.error);
    }
    return result;
  }

  async function adjustGridRow(rootEl, action) {
    var id = buildingId();
    if (!id || !rootEl) return;
    showLayoutError(rootEl, "");
    var res = await fetch(appRoot() + "/buildings/" + id + "/ground-floor/grid-row", {
      method: "POST",
      headers: Object.assign({ "Content-Type": "application/json" }, csrfHeaders()),
      body: JSON.stringify({ action: action }),
    });
    if (!res.ok) {
      showLayoutError(rootEl, await parseErrorResponse(res));
      return;
    }
    var plan = await res.json();
    renderShopPlanGrid(plan, rootEl);
  }

  async function adjustGridCol(rootEl, action) {
    var id = buildingId();
    if (!id || !rootEl) return;
    showLayoutError(rootEl, "");
    var res = await fetch(appRoot() + "/buildings/" + id + "/ground-floor/grid-col", {
      method: "POST",
      headers: Object.assign({ "Content-Type": "application/json" }, csrfHeaders()),
      body: JSON.stringify({ action: action }),
    });
    if (!res.ok) {
      showLayoutError(rootEl, await parseErrorResponse(res));
      return;
    }
    var plan = await res.json();
    renderShopPlanGrid(plan, rootEl);
  }

  var SHOP_MAX_GRID_ROWS = 24;
  var SHOP_MAX_GRID_COLS = 40;

  function shopRowHasPlacement(state, row) {
    if (!state) return false;
    var lists = [state.shopPlacements, state.parkingPlacements];
    var i;
    var j;
    for (i = 0; i < lists.length; i++) {
      if (!lists[i]) continue;
      for (j = 0; j < lists[i].length; j++) {
        if (lists[i][j].row === row) return true;
      }
    }
    if (state.fixtures) {
      for (i = 0; i < state.fixtures.length; i++) {
        if (state.fixtures[i].row === row) return true;
      }
    }
    return false;
  }

  function shopColHasPlacement(state, col) {
    if (!state) return false;
    var lists = [state.shopPlacements, state.parkingPlacements];
    var i;
    var j;
    for (i = 0; i < lists.length; i++) {
      if (!lists[i]) continue;
      for (j = 0; j < lists[i].length; j++) {
        if (lists[i][j].col === col) return true;
      }
    }
    if (state.fixtures) {
      for (i = 0; i < state.fixtures.length; i++) {
        if (state.fixtures[i].col === col) return true;
      }
    }
    return false;
  }

  function shopMinGridCols(state) {
    if (!state || !state.plan) return 1;
    var shopCount = state.plan.shopCount || 0;
    var parkingCount = state.plan.parkingSlotCount || 0;
    var combined = shopCount + parkingCount;
    if (combined <= 0) return 1;
    return Math.max(1, Math.ceil(combined / Math.max(state.gridRows, 1)));
  }

  function updateShopGridToolbar(rootEl) {
    if (!rootEl) return;
    var state = rootEl._shopLayoutState;
    if (!state) return;
    var minRows = state.plan && state.plan.minGridRows != null ? state.plan.minGridRows : 1;
    var minCols = shopMinGridCols(state);
    var rows = state.gridRows;
    var cols = state.gridCols;
    var removeTop = rootEl.querySelector('[data-shop-row-action="REMOVE_TOP"]');
    var removeBottom = rootEl.querySelector('[data-shop-row-action="REMOVE_BOTTOM"]');
    var insertTop = rootEl.querySelector('[data-shop-row-action="INSERT_TOP"]');
    var insertBottom = rootEl.querySelector('[data-shop-row-action="INSERT_BOTTOM"]');
    var removeLeft = rootEl.querySelector('[data-shop-col-action="REMOVE_LEFT"]');
    var removeRight = rootEl.querySelector('[data-shop-col-action="REMOVE_RIGHT"]');
    var insertLeft = rootEl.querySelector('[data-shop-col-action="INSERT_LEFT"]');
    var insertRight = rootEl.querySelector('[data-shop-col-action="INSERT_RIGHT"]');
    var canRemoveTop = rows > minRows && !shopRowHasPlacement(state, 0);
    var canRemoveBottom = rows > minRows && !shopRowHasPlacement(state, rows - 1);
    var canInsertRow = rows < SHOP_MAX_GRID_ROWS;
    var canRemoveLeft = cols > minCols && !shopColHasPlacement(state, 0);
    var canRemoveRight = cols > minCols && !shopColHasPlacement(state, cols - 1);
    var canInsertCol = cols < SHOP_MAX_GRID_COLS;
    if (removeTop) removeTop.disabled = !canRemoveTop;
    if (removeBottom) removeBottom.disabled = !canRemoveBottom;
    if (insertTop) insertTop.disabled = !canInsertRow;
    if (insertBottom) insertBottom.disabled = !canInsertRow;
    if (removeLeft) removeLeft.disabled = !canRemoveLeft;
    if (removeRight) removeRight.disabled = !canRemoveRight;
    if (insertLeft) insertLeft.disabled = !canInsertCol;
    if (insertRight) insertRight.disabled = !canInsertCol;
  }

  function ensureShopGridDelegation() {
    if (window.__f21ShopGridBound) return;
    window.__f21ShopGridBound = true;

    document.addEventListener("dragstart", function (e) {
      var dragEl =
        e.target.closest(".shop-plan__slot--draggable") ||
        e.target.closest(".shop-plan__fixture--draggable");
      if (!dragEl || !isPlatformAdminEdit()) return;
      var key = dragKeyFromEl(dragEl);
      if (!key) return;
      e.dataTransfer.setData("text/plain", key);
      e.dataTransfer.effectAllowed = "move";
      dragEl.classList.add(
        dragEl.classList.contains("shop-plan__fixture--draggable")
          ? "shop-plan__fixture--dragging"
          : "shop-plan__slot--dragging"
      );
      setGridDragActive(dragEl.closest(".flat-ground-floor-section__plan-root"), true);
    });

    document.addEventListener("dragend", function (e) {
      var slot = e.target.closest(".shop-plan__slot--draggable");
      var fixture = e.target.closest(".shop-plan__fixture--draggable");
      if (slot) {
        slot.classList.remove("shop-plan__slot--dragging");
        setGridDragActive(slot.closest(".flat-ground-floor-section__plan-root"), false);
      }
      if (fixture) {
        fixture.classList.remove("shop-plan__fixture--dragging");
        setGridDragActive(fixture.closest(".flat-ground-floor-section__plan-root"), false);
      }
      clearCellDragOver();
    });

    document.addEventListener("dragover", function (e) {
      var target = dropTargetFromEvent(e);
      if (!target || !target.highlightEl || !isPlatformAdminEdit()) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = "move";
      clearCellDragOver();
      target.highlightEl.classList.add("shop-plan__cell--drag-over");
    });

    document.addEventListener("dragleave", function (e) {
      var cell = e.target.closest(".shop-plan__cell--drop");
      if (cell) cell.classList.remove("shop-plan__cell--drag-over");
    });

    document.addEventListener("drop", function (e) {
      var target = dropTargetFromEvent(e);
      if (!target || !isPlatformAdminEdit()) return;
      e.preventDefault();
      clearCellDragOver();
      var root = target.root;
      var state = root && root._shopLayoutState;
      if (!state) return;
      var drag = parseDragKey(e.dataTransfer.getData("text/plain"));
      if (!drag) return;
      if (!moveItemOnGrid(state, drag, target.col, target.row)) return;
      rerenderFromState(root);
      setGridDragActive(root, false);
      void autoSaveLayout(root);
    });
  }

  function fixtureMetaSuffix(plan) {
    if (!plan) return "";
    var parts = [];
    if (plan.carLiftCount > 0) parts.push(plan.carLiftCount + " car lift" + (plan.carLiftCount === 1 ? "" : "s"));
    if (plan.passengerLiftCount > 0) {
      parts.push(plan.passengerLiftCount + " passenger lift" + (plan.passengerLiftCount === 1 ? "" : "s"));
    }
    if (plan.gateCount > 0) parts.push(plan.gateCount + " gate" + (plan.gateCount === 1 ? "" : "s"));
    return parts.length ? " · " + parts.join(" · ") : "";
  }

  function selectGroundParkingSlot(slotEl) {
    if (!slotEl || !window.floor21SelectParkingSlot) return;
    window.floor21SelectParkingSlot(slotEl);
  }

  function groundFloorLayoutImageUrl() {
    var id = buildingId();
    if (!id) return "";
    return (
      appRoot() + "/buildings/" + id + "/ground-floor-layout-image?t=" + Date.now()
    );
  }

  function buildGroundFloorLayoutLinksHtml(hasImage) {
    var admin = isPlatformAdminEdit();
    if (!admin && !hasImage) return "";
    var upload = admin
      ? '<button type="button" class="ground-floor-layout-upload-link btn btn-link btn-sm px-0">Upload layout</button>'
      : "";
    var view = hasImage
      ? '<button type="button" class="ground-floor-layout-view-link btn btn-link btn-sm px-0">View layout</button>'
      : "";
    if (!upload && !view) return "";
    return (
      '<div class="flat-ground-floor-section__layout-links d-flex flex-wrap align-items-center gap-2">' +
      upload +
      view +
      "</div>"
    );
  }

  function ensureGroundFloorConfigureLink(panel) {
    if (!panel || !isPlatformAdminEdit()) return;
    if (panel.querySelector(".ground-floor-configure-link")) return;
    var summary = panel.querySelector(".flat-ground-floor-section__summary");
    if (!summary) return;
    var html =
      '<button type="button" class="ground-floor-configure-link btn btn-link btn-sm px-0">Configure</button>';
    var layoutLinks = panel.querySelector(".flat-ground-floor-section__layout-links");
    if (layoutLinks) {
      layoutLinks.insertAdjacentHTML("beforebegin", html);
    } else {
      summary.insertAdjacentHTML("beforeend", html);
    }
  }

  function refreshGroundFloorLayoutLinks(panel) {
    if (!panel) panel = groundFloorPanel();
    if (!panel) return;
    var hasImage = panel.getAttribute("data-has-layout-image") === "true";
    var html = buildGroundFloorLayoutLinksHtml(hasImage);
    var existing = panel.querySelector(".flat-ground-floor-section__layout-links");
    if (existing) {
      if (html) {
        existing.outerHTML = html;
      } else {
        existing.remove();
      }
      return;
    }
    if (!html) return;
    var configure = panel.querySelector(".ground-floor-configure-link");
    if (configure && configure.parentNode) {
      configure.insertAdjacentHTML("afterend", html);
    } else {
      var summary = panel.querySelector(".flat-ground-floor-section__summary");
      if (summary) summary.insertAdjacentHTML("beforeend", html);
    }
  }

  var groundFloorLayoutUploadPanel = null;

  async function uploadGroundFloorLayoutImage(panel, file) {
    if (!panel || !file || !isPlatformAdminEdit()) return;
    var id = buildingId();
    if (!id) return;
    var formData = new FormData();
    formData.append("image", file);
    var res = await fetch(appRoot() + "/buildings/" + id + "/ground-floor-layout-image", {
      method: "POST",
      headers: csrfHeaders(),
      body: formData,
    });
    if (!res.ok) {
      var message = "Could not upload ground floor layout image.";
      try {
        var err = await res.json();
        if (err && err.error) message = err.error;
      } catch (ignore) {
        /* use default message */
      }
      window.alert(message);
      return;
    }
    panel.setAttribute("data-has-layout-image", "true");
    refreshGroundFloorLayoutLinks(panel);
  }

  function openGroundFloorLayoutModal() {
    var url = groundFloorLayoutImageUrl();
    if (!url || typeof bootstrap === "undefined" || !bootstrap.Modal) return;
    var img = document.getElementById("floor-plan-modal-img");
    var modalEl = document.getElementById("floor-plan-modal");
    var titleEl = document.getElementById("floor-plan-modal-title");
    if (!img || !modalEl) return;
    img.src = url;
    img.alt = "Ground floor layout";
    if (titleEl) titleEl.textContent = "Ground floor layout";
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  function canOpenShopSlot(slotEl) {
    if (!slotEl) return false;
    if (isPlatformAdminEdit()) return true;
    return slotEl.getAttribute("data-bookable") === "true";
  }

  function selectShopSlot(slotEl) {
    if (!slotEl || !window.floor21SelectShop) return;
    if (slotEl.classList.contains("shop-plan__slot--parking")) return;
    if (!canOpenShopSlot(slotEl)) return;
    window.floor21SelectShop(slotEl);
  }

  function syncPanelConfigDataset(panel, groundFloor, plan) {
    if (!panel) return;
    var gf = groundFloor || {};
    var parkingCount =
      plan && plan.parkingSlotCount != null
        ? plan.parkingSlotCount
        : gf.parkingSlotCount != null
          ? gf.parkingSlotCount
          : 0;
    panel.dataset.parkingSlotCount = String(parkingCount);
    if (gf.parkingSlotAreaSqft != null) {
      panel.dataset.parkingSlotArea = String(gf.parkingSlotAreaSqft);
    } else if (plan && plan.parkingSlots && plan.parkingSlots[0] && plan.parkingSlots[0].areaSqft != null) {
      panel.dataset.parkingSlotArea = String(plan.parkingSlots[0].areaSqft);
    }
    panel.dataset.shopSizePercent = String(
      plan && plan.shopSizePercent != null
        ? plan.shopSizePercent
        : gf.shopSizePercent != null
          ? gf.shopSizePercent
          : DEFAULT_SHOP_SIZE_PERCENT
    );
    panel.dataset.parkingCarSizePercent = String(
      plan && plan.parkingCarSizePercent != null
        ? plan.parkingCarSizePercent
        : gf.parkingCarSizePercent != null
          ? gf.parkingCarSizePercent
          : DEFAULT_PARKING_CAR_SIZE_PERCENT
    );
    panel.dataset.carLiftCount = String(
      plan && plan.carLiftCount != null
        ? plan.carLiftCount
        : gf.carLiftCount != null
          ? gf.carLiftCount
          : 1
    );
    panel.dataset.passengerLiftCount = String(
      plan && plan.passengerLiftCount != null
        ? plan.passengerLiftCount
        : gf.passengerLiftCount != null
          ? gf.passengerLiftCount
          : 0
    );
    panel.dataset.gateCount = String(
      plan && plan.gateCount != null
        ? plan.gateCount
        : gf.gateCount != null
          ? gf.gateCount
          : 1
    );
  }

  function updatePanelMeta(groundFloor, plan) {
    var panel = groundFloorPanel();
    if (!panel || !groundFloor) return;
    var configured = !!groundFloor.configured;
    panel.classList.toggle("flat-ground-floor-section--configured", configured);
    panel.classList.toggle("flat-ground-floor-section--split", configured);
    panel.classList.toggle("flat-ground-floor-section--pending", !configured);
    panel.classList.toggle("d-none", !configured);
    panel.dataset.configured = configured ? "true" : "false";
    panel.dataset.shopCount = String(groundFloor.shopCount != null ? groundFloor.shopCount : 0);
    panel.dataset.shopArea =
      groundFloor.shopAreaSqft != null ? String(groundFloor.shopAreaSqft) : "350";
    if (groundFloor.rangeLabel) {
      panel.dataset.rangeLabel = groundFloor.rangeLabel;
    }
    if (groundFloor.hasLayoutImage != null) {
      panel.dataset.hasLayoutImage = groundFloor.hasLayoutImage ? "true" : "false";
      refreshGroundFloorLayoutLinks(panel);
    }
    syncPanelConfigDataset(panel, groundFloor, plan);
    var meta = panel.querySelector(".flat-ground-floor-section__meta");
    if (meta) {
      var parkingPart =
        plan && plan.parkingSlotCount > 0 ? " · " + plan.parkingSlotCount + " parking" : "";
      meta.textContent = configured
        ? groundFloor.shopCount +
          " shops" +
          parkingPart +
          (groundFloor.rangeLabel ? " · " + groundFloor.rangeLabel : "") +
          fixtureMetaSuffix(plan)
        : "Not configured";
    }
    var planPane = panel.querySelector(".flat-ground-floor-section__plan");
    if (planPane) planPane.setAttribute("aria-hidden", configured ? "false" : "true");
    var addBtn = document.querySelector(".ground-floor-add-btn");
    if (addBtn) addBtn.classList.toggle("d-none", configured);
    if (configured) {
      ensureGroundFloorConfigureLink(panel);
      ensureGroundFloorResizeHandle(panel);
    }
  }

  async function loadShopPlan() {
    var panel = groundFloorPanel();
    if (!panel || panel.dataset.configured !== "true") return;
    var id = buildingId();
    if (!id) return;
    var res = await fetch(appRoot() + "/buildings/" + id + "/ground-floor/plan", {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return;
    var plan = await res.json();
    renderShopPlanGrid(plan, groundFloorPlanRoot());
    ensureGroundFloorResizeHandle(panel);
    if (plan.hasLayoutImage) {
      panel.setAttribute("data-has-layout-image", "true");
    }
    updatePanelMeta(
      {
        configured: true,
        shopCount: plan.shopCount,
        rangeLabel: panel.dataset.rangeLabel || "",
        shopAreaSqft: panel.dataset.shopArea,
        hasLayoutImage: !!plan.hasLayoutImage || panel.getAttribute("data-has-layout-image") === "true",
      },
      plan
    );
  }

  function ensureConfigModalMounted() {
    if (typeof window.floor21MountGridModals === "function") {
      window.floor21MountGridModals();
      return;
    }
    mountModal();
  }

  function mountModal() {
    var matches = document.querySelectorAll("#ground-floor-config-modal");
    if (!matches.length) return;
    var main = document.getElementById("floor21-main");
    var keep = matches[matches.length - 1];
    if (main && matches.length > 1) {
      Array.prototype.forEach.call(matches, function (node) {
        if (main.contains(node)) keep = node;
      });
    }
    Array.prototype.forEach.call(matches, function (node) {
      if (node !== keep) node.remove();
    });
    if (keep.parentElement !== document.body) {
      document.body.appendChild(keep);
    }
  }

  function groundPanelSizePercentsForConfigSave() {
    var panel = groundFloorPanel();
    return {
      shop:
        panel && panel.dataset.shopSizePercent
          ? clampPanelPercent(Number(panel.dataset.shopSizePercent))
          : DEFAULT_SHOP_SIZE_PERCENT,
      parking:
        panel && panel.dataset.parkingCarSizePercent
          ? clampPanelPercent(Number(panel.dataset.parkingCarSizePercent))
          : DEFAULT_PARKING_CAR_SIZE_PERCENT,
    };
  }

  function populateConfigForm(panel) {
    var countEl = configEl("ground-floor-config-shop-count");
    var parkingCountEl = configEl("ground-floor-config-parking-count");
    var carLiftEl = configEl("ground-floor-config-car-lift-count");
    var passengerLiftEl = configEl("ground-floor-config-passenger-lift-count");
    var gateEl = configEl("ground-floor-config-gate-count");
    if (countEl) {
      var existingCount = panel && panel.dataset.shopCount ? Number(panel.dataset.shopCount) : 0;
      countEl.value = existingCount > 0 ? String(existingCount) : "4";
    }
    setAreaPair(
      "ground-floor-config-area",
      panel && panel.dataset.shopArea ? panel.dataset.shopArea : "350"
    );
    if (parkingCountEl) {
      parkingCountEl.value =
        panel && panel.dataset.parkingSlotCount != null
          ? panel.dataset.parkingSlotCount
          : "0";
    }
    setAreaPair(
      "ground-floor-config-parking-area",
      panel && panel.dataset.parkingSlotArea ? panel.dataset.parkingSlotArea : "150"
    );
    if (carLiftEl) {
      carLiftEl.value =
        panel && panel.dataset.carLiftCount != null ? panel.dataset.carLiftCount : "1";
    }
    if (passengerLiftEl) {
      passengerLiftEl.value =
        panel && panel.dataset.passengerLiftCount != null
          ? panel.dataset.passengerLiftCount
          : "0";
    }
    if (gateEl) {
      gateEl.value = panel && panel.dataset.gateCount != null ? panel.dataset.gateCount : "1";
    }
    if (window.Floor21AreaUnit && window.Floor21AreaUnit.bindDualAreaInputs) {
      window.Floor21AreaUnit.bindDualAreaInputs(configModalEl() || document);
    }
  }

  function openConfigModal() {
    if (!isPlatformAdminEdit()) return;
    ensureConfigModalMounted();
    var modalEl = configModalEl();
    var panel = groundFloorPanel();
    var countEl = configEl("ground-floor-config-shop-count");
    if (!modalEl || typeof bootstrap === "undefined" || !bootstrap.Modal) return;
    populateConfigForm(panel);
    showConfigError("");
    showConfigSuccess("");
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
    window.setTimeout(function () {
      if (countEl) countEl.focus();
    }, 150);
  }

  async function saveConfig(saveBtn) {
    ensureConfigModalMounted();
    var id = buildingId();
    var countEl = configEl("ground-floor-config-shop-count");
    var parkingCountEl = configEl("ground-floor-config-parking-count");
    var carLiftEl = configEl("ground-floor-config-car-lift-count");
    var passengerLiftEl = configEl("ground-floor-config-passenger-lift-count");
    var gateEl = configEl("ground-floor-config-gate-count");
    if (!id || !countEl) {
      showConfigError("Could not save — building not found. Refresh the page and try again.");
      return;
    }
    var shopCount = Number(countEl.value);
    if (!shopCount || shopCount < 1 || shopCount > 50) {
      showConfigError("Enter a shop count between 1 and 50.");
      return;
    }
    var shopAreaSqft = readConfigAreaSqft("ground-floor-config-area");
    if (shopAreaSqft == null || shopAreaSqft <= 0) {
      showConfigError("Enter a shop area greater than zero.");
      return;
    }
    var parkingSlotCount = parkingCountEl ? Number(parkingCountEl.value) : 0;
    if (isNaN(parkingSlotCount) || parkingSlotCount < 0 || parkingSlotCount > 50) {
      showConfigError("Enter a parking slot count between 0 and 50.");
      return;
    }
    var parkingSlotAreaSqft = readConfigAreaSqft("ground-floor-config-parking-area");
    if (parkingSlotCount > 0 && (parkingSlotAreaSqft == null || parkingSlotAreaSqft <= 0)) {
      showConfigError("Enter a parking slot area greater than zero.");
      return;
    }
    showConfigError("");
    showConfigStatus("Saving…", "success");
    var sizePercents = groundPanelSizePercentsForConfigSave();
    var saveLabel = saveBtn ? saveBtn.textContent : "Save";
    if (saveBtn) {
      saveBtn.disabled = true;
      saveBtn.textContent = "Saving…";
    }
    var res;
    try {
      res = await fetch(appRoot() + "/buildings/" + id + "/ground-floor-config", {
        method: "POST",
        headers: Object.assign({ "Content-Type": "application/json" }, csrfHeaders()),
        body: JSON.stringify({
          shopCount: shopCount,
          shopAreaSqft: shopAreaSqft,
          carLiftCount: carLiftEl ? Number(carLiftEl.value) : 1,
          passengerLiftCount: passengerLiftEl ? Number(passengerLiftEl.value) : 0,
          gateCount: gateEl ? Number(gateEl.value) : 1,
          parkingSlotCount: parkingSlotCount,
          parkingSlotAreaSqft: parkingSlotCount > 0 ? parkingSlotAreaSqft : null,
          parkingCarSizePercent: sizePercents.parking,
          shopSizePercent: sizePercents.shop,
        }),
      });
    } catch (err) {
      showConfigError("Could not save — check your connection and try again.");
      return;
    } finally {
      if (saveBtn) {
        saveBtn.disabled = false;
        saveBtn.textContent = saveLabel;
      }
    }
    if (!res || !res.ok) {
      showConfigError(await parseErrorResponse(res));
      return;
    }
    var groundFloor = await res.json();
    showConfigSuccess(GROUND_CONFIG_SUCCESS_MSG);
    window.floor21SyncGroundFloor(groundFloor);
    if (typeof window.floor21RefreshGrid === "function") {
      await window.floor21RefreshGrid();
    }
    closeGroundFloorConfigModal();
  }

  function ensureGroundFloorConfigSaveBinding() {
    if (window.__f21GroundConfigSaveHandler) {
      document.removeEventListener("click", window.__f21GroundConfigSaveHandler, true);
    }
    window.__f21GroundConfigSaveHandler = function (e) {
      var btn = e.target.closest("#ground-floor-config-save");
      if (!btn || btn.disabled) return;
      var modal = configModalEl();
      if (modal && !modal.contains(btn)) return;
      e.preventDefault();
      e.stopPropagation();
      void saveConfig(btn);
    };
    document.addEventListener("click", window.__f21GroundConfigSaveHandler, true);
  }

  window.floor21ReloadGroundFloorPlan = loadShopPlan;
  window.floor21AdjustGroundFloorGridRow = adjustGridRow;
  window.floor21AdjustGroundFloorGridCol = adjustGridCol;

  function renderSnapshotShopChip(slot) {
    if (!slot) return "";
    var statusClass = shopStatusClass(slot.status);
    var title = "Shop " + slot.slotNumber + (slot.flatNumber ? " — " + slot.flatNumber : "");
    return (
      '<span class="bld-gf-compact__chip shop-plan__slot shop-plan__slot--shop shop-plan__slot--clickable ' +
      statusClass +
      '" data-shop-flat-id="' +
      (slot.flatId || "") +
      '" data-slot-number="' +
      slot.slotNumber +
      '" title="' +
      title.replace(/"/g, "&quot;") +
      '">' +
      slot.slotNumber +
      "</span>"
    );
  }

  function renderSnapshotGroundParkingChip(slot) {
    if (!slot) return "";
    var linked = slot.linkedResidentialFlatNumber || "";
    var booked = !!(linked || slot.status === "BOOKED");
    var title = linked
      ? "Parking " + slot.slotNumber + " — " + linked
      : "Parking " + slot.slotNumber;
    return (
      '<button type="button" class="bld-parking-seat bld-parking-seat--clickable bld-parking-strip__slot shop-plan__slot shop-plan__slot--parking ' +
      (booked ? "bld-parking-seat--booked bld-parking-strip__slot--set" : "bld-parking-seat--available bld-parking-strip__slot--open") +
      '" data-parking-flat-id="' +
      (slot.flatId || "") +
      '" data-slot-number="' +
      slot.slotNumber +
      '" title="' +
      title.replace(/"/g, "&quot;") +
      '" aria-label="' +
      title.replace(/"/g, "&quot;") +
      '">' +
      slot.slotNumber +
      "</button>"
    );
  }

  function renderSnapshotGroundFloorPlanHtml(plan, rowSelected) {
    if (!plan) return "";
    var shops = (plan.shops || plan.slots || [])
      .slice()
      .sort(function (a, b) {
        return (a.slotNumber || 0) - (b.slotNumber || 0);
      });
    var parking = (plan.parkingSlots || [])
      .slice()
      .sort(function (a, b) {
        return (a.slotNumber || 0) - (b.slotNumber || 0);
      });
    if (!shops.length && !parking.length) return "";
    var selectedCls = rowSelected ? " bld-parking-floor--selected" : "";
    var shopsHtml = shops.map(renderSnapshotShopChip).join("");
    var parkingHtml = parking.map(renderSnapshotGroundParkingChip).join("");
    var rowsHtml = "";
    if (shopsHtml) {
      rowsHtml +=
        '<div class="bld-gf-compact__row bld-gf-compact__row--shops">' +
        '<span class="bld-gf-compact__row-label">Shops</span>' +
        '<div class="bld-gf-compact__chips">' +
        shopsHtml +
        "</div></div>";
    }
    if (parkingHtml) {
      rowsHtml +=
        '<div class="bld-gf-compact__row bld-gf-compact__row--parking">' +
        '<span class="bld-gf-compact__row-label">Parking</span>' +
        '<div class="bld-gf-compact__chips">' +
        parkingHtml +
        "</div></div>";
    }
    return (
      '<div class="bld-row bld-row--ground' +
      selectedCls +
      '" data-floor-number="0" title="Ground floor">' +
      '<div class="bld-cell bld-cell--ground-block">' +
      rowsHtml +
      "</div></div>"
    );
  }

  window.floor21RenderSnapshotGroundFloorPlanHtml = renderSnapshotGroundFloorPlanHtml;

  window.floor21SyncGroundFloor = function (groundFloor) {
    updatePanelMeta(groundFloor || { configured: false, shopCount: 0 }, null);
    if (groundFloor && groundFloor.configured) {
      void loadShopPlan();
    }
  };

  function onPageReady(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn);
    } else {
      fn();
    }
    document.addEventListener("turbo:load", fn);
    document.addEventListener("turbo:frame-render", function (event) {
      if (event.target && event.target.id === "floor21-main") {
        fn();
      }
    });
  }

  onPageReady(function () {
    ensureShopGridDelegation();
    window.floor21PanelResizeIsEnabled = isPlatformAdminEdit;
    if (window.floor21PanelResize) {
      window.floor21PanelResize.bind();
    }
    ensureConfigModalMounted();
    ensureGroundFloorConfigSaveBinding();

    var section = document.getElementById("flat-ground-floor-section");
    if (!section) {
      return;
    }

    var panel = groundFloorPanel();
    if (panel && panel.dataset.configured === "true") {
      ensureGroundFloorConfigureLink(panel);
      void loadShopPlan();
    }

    if (section.dataset.f21GroundInit === "true") {
      return;
    }
    section.dataset.f21GroundInit = "true";

    document.addEventListener("click", function (e) {
      var addBtn = e.target.closest(".ground-floor-add-btn");
      if (addBtn) {
        e.preventDefault();
        openConfigModal();
        return;
      }
      var configure = e.target.closest(".ground-floor-configure-link");
      if (configure) {
        e.preventDefault();
        openConfigModal();
        return;
      }
      var slot = e.target.closest(".shop-plan__slot--shop.shop-plan__slot--clickable");
      if (slot) {
        e.preventDefault();
        e.stopPropagation();
        selectShopSlot(slot);
        return;
      }
      var parkingSlot = e.target.closest(".shop-plan__slot--parking.shop-plan__slot--clickable");
      if (parkingSlot) {
        e.preventDefault();
        e.stopPropagation();
        selectGroundParkingSlot(parkingSlot);
        return;
      }
      var layoutUpload = e.target.closest(".ground-floor-layout-upload-link");
      if (layoutUpload) {
        e.preventDefault();
        e.stopPropagation();
        if (!isPlatformAdminEdit()) return;
        var uploadPanel = layoutUpload.closest(".flat-ground-floor-section__panel");
        var fileInput = document.getElementById("ground-floor-layout-file-input");
        if (!uploadPanel || !fileInput) return;
        groundFloorLayoutUploadPanel = uploadPanel;
        fileInput.value = "";
        fileInput.click();
        return;
      }
      var layoutView = e.target.closest(".ground-floor-layout-view-link");
      if (layoutView) {
        e.preventDefault();
        e.stopPropagation();
        openGroundFloorLayoutModal();
      }
    });

    var groundFloorLayoutFileInput = document.getElementById("ground-floor-layout-file-input");
    if (groundFloorLayoutFileInput) {
      groundFloorLayoutFileInput.addEventListener("change", function () {
        var file = groundFloorLayoutFileInput.files && groundFloorLayoutFileInput.files[0];
        if (!file || !groundFloorLayoutUploadPanel) return;
        void uploadGroundFloorLayoutImage(groundFloorLayoutUploadPanel, file);
        groundFloorLayoutUploadPanel = null;
        groundFloorLayoutFileInput.value = "";
      });
    }

  });

  ensureGroundFloorConfigSaveBinding();
})();
