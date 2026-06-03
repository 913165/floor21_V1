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
  var parkingConfigFloorNumber = null;

  /** Bootstrap backdrop is on body; modals must be too or backdrop blocks clicks. */
  function mountModalsOnBody() {
    [
      "flat-details-modal",
      "floor-plan-modal",
      "flat-add-modal",
      "parking-config-modal",
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

  function setParkingSectionMode(on) {
    var modal = document.getElementById("flat-details-modal");
    if (modal) modal.classList.toggle("modal--parking-section", on);
    var note = document.getElementById("panel-parking-note");
    var actions = document.getElementById("panel-booking-actions");
    var adminPanel = document.getElementById("flat-admin-panel");
    var saveRow = document.getElementById("admin-save-row");
    var saveBtn = document.getElementById("admin-save-btn");
    if (note) note.classList.toggle("d-none", !on);
    if (actions) actions.classList.toggle("d-none", on);
    if (adminPanel) adminPanel.classList.toggle("d-none", on);
    if (saveRow) {
      saveRow.classList.toggle("d-none", !on || !isPlatformAdminEdit());
    }
    if (saveBtn) saveBtn.classList.toggle("d-none", on);
    if (on) {
      setAdminEditModeVisible(isPlatformAdminEdit());
    }
  }

  function syncParkingSectionAdminFields(sectionEl) {
    if (!sectionEl) return;
    var area = document.getElementById("admin-area");
    var price = document.getElementById("admin-price");
    var bhk = document.getElementById("admin-bhk");
    if (area) area.value = sectionEl.dataset.area || "";
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

  function readAdminForm() {
    var bhk = document.getElementById("admin-bhk");
    var area = document.getElementById("admin-area");
    var price = document.getElementById("admin-price");
    return {
      bhkType: bhk ? bhk.value : "",
      areaSqft: area && area.value !== "" ? Number(area.value) : null,
      basePrice: price && price.value !== "" ? Number(price.value) : null,
    };
  }

  function parseBhkSize(type) {
    if (!type) return 0;
    var unit = String(type).trim().toUpperCase();
    if (unit === "STUDIO") return 0.5;
    if (unit === "PENTHOUSE") return 8;
    var numeric = unit.replace(/BHK/i, "").trim();
    var value = parseFloat(numeric);
    return isNaN(value) ? 0 : value;
  }

  function resolveFloorPlanSlot(bhkType) {
    var grid = document.getElementById("flat-grid");
    if (!grid || !bhkType) return null;
    var size = parseBhkSize(bhkType);
    if (size <= 1.5 && grid.getAttribute("data-floor-plan-1bhk") === "true") {
      return "1bhk";
    }
    if (size <= 2.5 && grid.getAttribute("data-floor-plan-2bhk") === "true") {
      return "2bhk";
    }
    if (size <= 3.5 && grid.getAttribute("data-floor-plan-3bhk") === "true") {
      return "3bhk";
    }
    return null;
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
    ["panel-type", "panel-area", "panel-price"].forEach(function (id) {
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
    if (flat.areaSqft != null) cardEl.dataset.area = String(flat.areaSqft);
    if (flat.basePrice != null) cardEl.dataset.price = String(flat.basePrice);
    if (flat.status != null) cardEl.dataset.status = flat.status;
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
    var displayType = flat.gridTypeLabel || flat.bhkType;
    if (displayType) {
      cardEl.dataset.gridType = displayType;
    }
    var typeSpan = cardEl.querySelector(".flat-type");
    if (typeSpan && displayType) typeSpan.textContent = displayType;
    if (parking || amenity || duplexSecondary || mergeSecondary) {
      delete cardEl.dataset.floorPlanSlot;
      stripFloorPlanTriggers(cardEl);
      return;
    }
    var slot = resolveFloorPlanSlot(flat.bhkType);
    if (slot) {
      cardEl.dataset.floorPlanSlot = slot;
    } else {
      delete cardEl.dataset.floorPlanSlot;
    }
    syncFloorPlanLink(cardEl);
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
    var head = cardEl.querySelector(".flat-card-head");
    if (!head) return;
    var tag = cardEl.querySelector(".flat-partner-tag");
    var label = partnerName ? String(partnerName).trim() : "";
    if (!label) {
      if (tag) tag.remove();
      return;
    }
    if (!tag) {
      tag = document.createElement("span");
      tag.className = "flat-partner-tag small";
      var typeSpan = head.querySelector(".flat-type");
      if (typeSpan) head.insertBefore(tag, typeSpan);
      else head.appendChild(tag);
    }
    tag.textContent = label;
  }

  function syncDeactivatedTag(cardEl) {
    if (!cardEl) return;
    var head = cardEl.querySelector(".flat-card-head");
    if (!head) return;
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
      var typeSpan = head.querySelector(".flat-type");
      if (typeSpan) head.insertBefore(tag, typeSpan);
      else head.appendChild(tag);
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
      var area = document.getElementById("admin-area");
      var price = document.getElementById("admin-price");
      var partner = document.getElementById("admin-partner");
      var currentType = cardEl.dataset.type || "2BHK";
      if (bhk) ensureAdminBhkOption(bhk, currentType);
      if (area) area.value = cardEl.dataset.area || "";
      if (price) price.value = cardEl.dataset.price || "";
      if (partner) partner.value = cardEl.dataset.partnerId || "";
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
    el.dataset.area = flat.areaSqft;
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
      delete el.dataset.floorPlanSlot;
    } else {
      el.classList.remove("flat-card--other-partner");
    }
    stripFloorPlanTriggers(el);
    stripNonBookableHover(el);
    syncFloorPlanLink(el);
  }

  function createFlatCardFromData(flat) {
    var card = document.createElement("div");
    card.id = "flat-" + flat.id;
    card.className = flat.cardClass || "flat-card flat-available";
    card.dataset.flatId = flat.id;
    var inner = document.createElement("div");
    inner.className = "flat-card-inner";
    var head = document.createElement("div");
    head.className = "flat-card-head";
    var num = document.createElement("span");
    num.className = "flat-number";
    num.textContent = flat.flatNumber || "";
    head.appendChild(num);
    var typeSpan = document.createElement("span");
    typeSpan.className = "flat-type";
    typeSpan.textContent = flat.gridTypeLabel || flat.bhkType || "";
    head.appendChild(typeSpan);
    inner.appendChild(head);
    var owner = document.createElement("div");
    owner.className = "flat-card-owner is-blank";
    owner.innerHTML =
      '<span class="flat-owner-name"></span><span class="flat-owner-detail"></span>';
    inner.appendChild(owner);
    if (flat.bookableByCurrentUser !== false || isPlatformAdminEdit()) {
      var quick = document.createElement("button");
      quick.type = "button";
      quick.className = "flat-quick-link";
      quick.dataset.flatId = flat.id;
      quick.textContent = "Flat details";
      inner.appendChild(quick);
    }
    card.appendChild(inner);
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

  function parkingSectionMetaText(floor) {
    var count = floor.parkingSlotCount || (floor.flats ? floor.flats.length : 0);
    var range = floor.parkingRangeLabel || "";
    return count + " slots" + (range ? " · " + range : "");
  }

  function parkingSectionMetaDisplay(floor) {
    return parkingSectionConfigured(floor) ? parkingSectionMetaText(floor) : "Not configured";
  }

  function buildParkingSectionInnerHtml(floor) {
    var note = parkingSectionConfigured(floor)
      ? "Shared section — allocated across flats, not booked individually."
      : "Set the number of parking slots for this floor.";
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
      '<p class="flat-parking-section__note small text-muted mb-0">' +
      note +
      "</p>" +
      configureLink +
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

  function showParkingPlanInSection(plan) {
    if (!plan) return;
    var section = parkingSectionForFloor(plan.floorNumber);
    if (!section) return;
    var root = parkingPlanRootForSection(section);
    renderParkingPlan(plan, root);
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

  function slotFlatNumber(plan, slotNumber) {
    if (!plan || !plan.slots) return "";
    for (var i = 0; i < plan.slots.length; i++) {
      if (plan.slots[i].slotNumber === slotNumber) {
        return plan.slots[i].flatNumber || "";
      }
    }
    return "";
  }

  function renderParkingPlanSlot(slotNumber, flatNumber) {
    return (
      '<div class="parking-plan__slot" title="' +
      (flatNumber ? "Unit " + flatNumber : "Slot " + slotNumber) +
      '">' +
      '<span class="parking-plan__slot-no">' +
      slotNumber +
      "</span>" +
      '<span class="parking-plan__car" aria-hidden="true"></span>' +
      "</div>"
    );
  }

  function renderParkingPlan(plan, rootEl) {
    if (!rootEl || !plan) return;
    var topHtml = (plan.topRow || [])
      .map(function (n) {
        return renderParkingPlanSlot(n, slotFlatNumber(plan, n));
      })
      .join("");
    var bottomHtml = (plan.bottomRow || [])
      .map(function (n) {
        return renderParkingPlanSlot(n, slotFlatNumber(plan, n));
      })
      .join("");
    rootEl.innerHTML =
      '<div class="parking-plan__sheet">' +
      '<div class="parking-plan__row parking-plan__row--top">' +
      topHtml +
      "</div>" +
      '<div class="parking-plan__aisle">' +
      '<span class="parking-plan__arrow" aria-hidden="true"></span>' +
      '<span class="parking-plan__aisle-label">Drive aisle</span>' +
      '<span class="parking-plan__arrow parking-plan__arrow--reverse" aria-hidden="true"></span>' +
      "</div>" +
      '<div class="parking-plan__row parking-plan__row--bottom">' +
      bottomHtml +
      "</div>" +
      '<div class="parking-plan__ramps">' +
      '<div class="parking-plan__ramp parking-plan__ramp--out"><span>OUT</span><small>SLOPE 15%</small></div>' +
      '<div class="parking-plan__road">Secondary road</div>' +
      '<div class="parking-plan__ramp parking-plan__ramp--in"><span>IN</span><small>SLOPE 15%</small></div>' +
      "</div>" +
      "</div>";
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

  function openParkingConfigModal(sectionEl) {
    if (!sectionEl || !isPlatformAdminEdit()) return;
    mountModalsOnBody();
    parkingConfigFloorNumber = sectionEl.dataset.floorNumber;
    var modalEl = document.getElementById("parking-config-modal");
    var label = document.getElementById("parking-config-floor-label");
    var slots = document.getElementById("parking-config-slots");
    if (!modalEl || typeof bootstrap === "undefined" || !bootstrap.Modal) return;
    if (label) label.textContent = "Floor " + parkingConfigFloorNumber;
    if (slots) {
      var current = sectionEl.dataset.slotCount || "4";
      slots.value = current;
    }
    showParkingConfigError("");
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  async function saveParkingConfig() {
    var grid = document.getElementById("flat-grid");
    var buildingId = grid ? grid.getAttribute("data-building-id") : null;
    var slotsEl = document.getElementById("parking-config-slots");
    if (!buildingId || !parkingConfigFloorNumber || !slotsEl) return;
    var slotCount = Number(slotsEl.value);
    if (!slotCount || slotCount < 1 || slotCount > 200) {
      showParkingConfigError("Enter a slot count between 1 and 200.");
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
        body: JSON.stringify({ slotCount: slotCount }),
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
    await refreshGrid();
  }

  function updateParkingSectionElement(el, floor) {
    if (!el || !floor) return;
    var first = floor.flats && floor.flats[0];
    var configured = parkingSectionConfigured(floor);
    el.setAttribute("data-slot-count", String(floor.parkingSlotCount || 0));
    el.setAttribute("data-range-label", floor.parkingRangeLabel || "");
    el.setAttribute("data-configured", configured ? "true" : "false");
    el.classList.toggle("flat-parking-section--configured", configured);
    el.classList.toggle("flat-parking-section--pending", !configured);
    el.classList.toggle("flat-parking-section--split", configured);
    if (first) {
      el.setAttribute("data-first-flat-id", String(first.id));
      el.setAttribute("data-area", first.areaSqft != null ? String(first.areaSqft) : "");
      el.setAttribute("data-price", first.basePrice != null ? String(first.basePrice) : "");
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

  function syncFloorPlanLink(cardEl) {
    if (!cardEl) return;
    var bookable = isFlatBookable(cardEl);
    var fp = cardEl.querySelector(".flat-floor-plan-trigger");
    if (!fp) return;
    fp.disabled = !bookable;
    if (!bookable) {
      fp.classList.remove("flat-floor-plan-trigger");
      fp.classList.add("flat-floor-plan-link--disabled");
      fp.removeAttribute("data-floor-plan-url");
      fp.setAttribute("title", "Floor plan not available — flat not assigned to you");
    }
  }

  function stripFloorPlanTriggers(cardEl) {
    if (!cardEl || isFlatBookable(cardEl)) return;
    cardEl.querySelectorAll(".flat-floor-plan-trigger").forEach(function (btn) {
      var label = document.createElement("span");
      label.className = "flat-floor-plan-muted";
      label.setAttribute("aria-hidden", "true");
      label.textContent = "Floor plan";
      btn.replaceWith(label);
    });
    delete cardEl.dataset.floorPlanSlot;
  }

  function initAllFlatCards() {
    document.querySelectorAll("#flat-grid .flat-card").forEach(function (card) {
      if (card.dataset.bookable !== "true" && card.dataset.bookable !== "false") {
        card.dataset.bookable = card.classList.contains("flat-card--other-partner") ? "false" : "true";
      }
      stripNonBookableHover(card);
      stripFloorPlanTriggers(card);
      syncFloorPlanLink(card);
    });
  }

  function floorPlanUrlForCard(cardEl) {
    if (!cardEl || !isFlatBookable(cardEl)) return null;
    var grid = document.getElementById("flat-grid");
    var bid = grid ? grid.getAttribute("data-building-id") : null;
    var slot = cardEl.dataset.floorPlanSlot;
    var flatId = cardEl.dataset.flatId || cardEl.getAttribute("data-flat-id");
    if (!bid || !slot || !flatId) return null;
    return (
      appRoot() +
      "/buildings/" +
      bid +
      "/floor-plan/" +
      encodeURIComponent(slot) +
      "?flatId=" +
      encodeURIComponent(flatId)
    );
  }

  function syncActionButtons(cardEl) {
    var bookable = isFlatBookable(cardEl);
    var nonBookable = isNonBookableUnit(cardEl);
    var hold = document.getElementById("hold-btn");
    var book = document.getElementById("book-btn");
    var panelFp = document.getElementById("panel-floor-plan-btn");
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
    if (panelFp) {
      panelFp.disabled = !bookable || nonBookable;
      panelFp.classList.toggle("disabled", !bookable || nonBookable);
      panelFp.title =
        bookable && !nonBookable ? "" : nonBookable ? "Not a residential unit" : "Floor plan not available — flat not assigned to you";
    }
    syncFloorPlanLink(cardEl);
  }

  function openFlatDetailsModal() {
    var modalEl = document.getElementById("flat-details-modal");
    if (!modalEl || typeof bootstrap === "undefined" || !bootstrap.Modal) return;
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

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
    document.getElementById("panel-area").textContent = el.dataset.area || "";
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
    setParkingSectionMode(false);
    selectedFlatId = el.dataset.flatId;
    var titleEl = document.getElementById("panel-title");
    var flatNumEl = el.querySelector(".flat-number");
    var flatLabel = flatNumEl ? flatNumEl.textContent.trim() : "";
    if (titleEl) {
      titleEl.textContent = flatLabel ? "Flat " + flatLabel : "Flat details";
    }
    document.getElementById("panel-type").textContent =
      el.dataset.gridType || el.dataset.type || "";
    document.getElementById("panel-floor").textContent = el.dataset.floor || "";
    document.getElementById("panel-area").textContent = el.dataset.area || "";
    document.getElementById("panel-price").textContent = el.dataset.price || "";
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
    var fpBtn = document.getElementById("panel-floor-plan-btn");
    if (fpBtn) {
      var gridEl = document.getElementById("flat-grid");
      var bid = gridEl ? gridEl.getAttribute("data-building-id") : null;
      var slot = el.dataset.floorPlanSlot;
      var bookable = isFlatBookable(el);
      if (slot && bid && bookable) {
        fpBtn.setAttribute("data-floor-plan-url", floorPlanUrlForCard(el));
        fpBtn.classList.remove("d-none");
        fpBtn.disabled = false;
        fpBtn.classList.remove("disabled");
        fpBtn.removeAttribute("title");
      } else if (slot && bid) {
        fpBtn.classList.remove("d-none");
        fpBtn.removeAttribute("data-floor-plan-url");
        fpBtn.disabled = true;
        fpBtn.classList.add("disabled");
        fpBtn.title = "Floor plan not available — flat not assigned to you";
      } else {
        fpBtn.classList.add("d-none");
        fpBtn.removeAttribute("data-floor-plan-url");
        fpBtn.disabled = true;
      }
    }
    applyBookingSelectionHighlight();
    syncActionButtons(el);
    syncAdminPanel(el);
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
    var grid = document.getElementById("flat-grid");
    if (!grid) {
      return;
    }
    mountModalsOnBody();
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
    var panelFp = document.getElementById("panel-floor-plan-btn");
    if (panelFp) {
      panelFp.addEventListener("click", function () {
        if (panelFp.disabled) return;
        var url = panelFp.getAttribute("data-floor-plan-url");
        if (!url) return;
        if (selectedFlatId) {
          var sel = document.getElementById("flat-" + selectedFlatId);
          if (sel && !isFlatBookable(sel)) {
            window.alert("Floor plan is not available for flats not assigned to you.");
            return;
          }
        }
        var typeEl = document.getElementById("panel-type");
        var sub = typeEl && typeEl.textContent ? typeEl.textContent.trim() + " — Floor plan" : "Floor plan";
        var sel = selectedFlatId ? document.getElementById("flat-" + selectedFlatId) : null;
        openFloorPlanModal(url, sub, sel);
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
        var form = readAdminForm();
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
        var card = document.getElementById("flat-" + selectedFlatId);
        applyFlatDataToCard(card, flat);
        document.getElementById("panel-type").textContent = flat.bhkType || "";
        document.getElementById("panel-area").textContent = flat.areaSqft != null ? String(flat.areaSqft) : "";
        document.getElementById("panel-price").textContent = flat.basePrice != null ? String(flat.basePrice) : "";
        if (card) {
          syncAdminPanel(card);
          syncActionButtons(card);
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
        var areaInput = document.getElementById("flat-add-area");
        if (areaInput) {
          areaInput.value = "";
        }
        var priceInput = document.getElementById("flat-add-price");
        if (priceInput) {
          priceInput.value = "";
        }
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
        var areaInput = document.getElementById("flat-add-area");
        var priceInput = document.getElementById("flat-add-price");
        var payload = {
          floorNumber: parseInt(addFloorNumber, 10),
          bhkType: bhkSel ? bhkSel.value : "2BHK",
        };
        if (areaInput && areaInput.value.trim()) {
          payload.areaSqft = parseFloat(areaInput.value);
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
          if (e.target.closest(".flat-floor-plan-muted")) {
            e.preventDefault();
            e.stopPropagation();
            return;
          }
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
          var parkingConfigure = e.target.closest(".flat-parking-configure-link");
          if (parkingConfigure) {
            e.preventDefault();
            e.stopPropagation();
            var sectionCfg = parkingConfigure.closest(".flat-parking-section");
            if (sectionCfg) openParkingConfigModal(sectionCfg);
            return;
          }
          var cardForFp = e.target.closest(".flat-card");
          var fp = e.target.closest(".flat-floor-plan-trigger");
          if (fp) {
            e.preventDefault();
            e.stopPropagation();
            if (!cardForFp || isNonBookableUnit(cardForFp)) return;
            if (!isFlatBookable(cardForFp)) return;
            var url = fp.getAttribute("data-floor-plan-url") || floorPlanUrlForCard(cardForFp);
            if (url) {
              var typ = cardForFp.dataset.type ? cardForFp.dataset.type + " — Floor plan" : "Floor plan";
              openFloorPlanModal(url, typ, cardForFp);
            }
            return;
          }
          var card = e.target.closest(".flat-card");
          if (!card || !grid.contains(card)) return;
          if (!canOpenFlatPanel(card)) return;
          window.floor21SelectFlat(card);
        }
      );
      setInterval(refreshGrid, 20000);
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
