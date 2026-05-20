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

  /** Bootstrap backdrop is on body; modals must be too or backdrop blocks clicks. */
  function mountModalsOnBody() {
    ["flat-details-modal", "floor-plan-modal"].forEach(function (id) {
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

  function applyFlatDataToCard(cardEl, flat) {
    if (!cardEl || !flat) return;
    if (flat.bhkType != null) cardEl.dataset.type = flat.bhkType;
    if (flat.areaSqft != null) cardEl.dataset.area = String(flat.areaSqft);
    if (flat.basePrice != null) cardEl.dataset.price = String(flat.basePrice);
    if (flat.status != null) cardEl.dataset.status = flat.status;
    if (flat.floorNumber != null) cardEl.dataset.floor = String(flat.floorNumber);
    var typeSpan = cardEl.querySelector(".flat-type");
    if (typeSpan && flat.bhkType) typeSpan.textContent = flat.bhkType;
  }

  async function loadMergeCandidates(flatId) {
    var select = document.getElementById("admin-merge-remove");
    if (!select) return;
    select.innerHTML = '<option value="">— Select flat to remove —</option>';
    var res = await fetch(appRoot() + "/flats/" + flatId + "/merge-candidates", {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) return;
    var list = await res.json();
    list.forEach(function (c) {
      var opt = document.createElement("option");
      opt.value = c.id;
      opt.textContent =
        (c.flatNumber || c.id) + " · " + (c.bhkType || "") + " · " + (c.status || "");
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

  function syncAdminPanel(cardEl) {
    var panel = document.getElementById("flat-admin-panel");
    if (!panel || !isPlatformAdminEdit()) return;
    panel.classList.remove("d-none");
    showAdminError("");
    var bhk = document.getElementById("admin-bhk");
    var area = document.getElementById("admin-area");
    var price = document.getElementById("admin-price");
    var partner = document.getElementById("admin-partner");
    if (bhk) bhk.value = cardEl.dataset.type || "2BHK";
    if (area) area.value = cardEl.dataset.area || "";
    if (price) price.value = cardEl.dataset.price || "";
    if (partner) partner.value = cardEl.dataset.partnerId || "";
    if (selectedFlatId) loadMergeCandidates(selectedFlatId);
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

  function syncBuyerTooltip(el, flat) {
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
    var odisp = flat.ownerDisplay == null ? "" : String(flat.ownerDisplay).trim();
    var odet = flat.ownerDetail == null ? "" : String(flat.ownerDetail).trim();
    if (on) on.textContent = odisp;
    if (od) od.textContent = odet;
    if (owner) {
      owner.classList.toggle("is-blank", !odisp && !odet);
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
    floors.forEach(function (floor) {
      floor.flats.forEach(function (flat) {
        var el = document.getElementById("flat-" + flat.id);
        if (!el) return;
        el.dataset.status = flat.status;
        el.dataset.type = flat.bhkType;
        el.dataset.floor = flat.floorNumber;
        el.dataset.price = flat.basePrice;
        el.dataset.area = flat.areaSqft;
        el.dataset.parking = flat.parking;
        el.dataset.bookable = flat.bookableByCurrentUser === false ? "false" : "true";
        if (flat.assignedPartnerId) {
          el.dataset.partnerId = flat.assignedPartnerId;
        } else {
          delete el.dataset.partnerId;
        }
        syncPartnerTag(el, flat.assignedPartnerId, flat.assignedPartnerName);
        if (flat.clientId) {
          el.dataset.clientId = flat.clientId;
        } else {
          delete el.dataset.clientId;
        }
        syncBuyerTooltip(el, flat);
        syncCardOwner(el, flat);
        var typeSpan = el.querySelector(".flat-type");
        if (typeSpan && flat.bhkType) typeSpan.textContent = flat.bhkType;
        el.classList.remove(
          "flat-available",
          "flat-booked",
          "flat-hold",
          "flat-parking",
          "flat-card--other-partner"
        );
        if (flat.parking) el.classList.add("flat-parking");
        else if (flat.status === "AVAILABLE") el.classList.add("flat-available");
        else if (flat.status === "BOOKED") el.classList.add("flat-booked");
        else el.classList.add("flat-hold");
        if (flat.bookableByCurrentUser === false && !flat.parking) {
          el.classList.add("flat-card--other-partner");
          delete el.dataset.floorPlanSlot;
        }
        stripFloorPlanTriggers(el);
        syncFloorPlanLink(el);
      });
    });
    applyBookingSelectionHighlight();
    if (selectedFlatId) {
      var selected = document.getElementById("flat-" + selectedFlatId);
      if (selected) syncActionButtons(selected);
    }
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
    var hold = document.getElementById("hold-btn");
    var book = document.getElementById("book-btn");
    var panelFp = document.getElementById("panel-floor-plan-btn");
    if (hold) {
      hold.disabled = !bookable;
      hold.classList.toggle("disabled", !bookable);
      hold.title = bookable ? "" : "Not assigned to you";
    }
    if (book) {
      var statusOk =
          cardEl.dataset.status === "AVAILABLE" || cardEl.dataset.status === "HOLD";
      book.classList.toggle("disabled", !bookable || !statusOk);
      if (!bookable) {
        book.title = "Not assigned to you";
      } else {
        book.removeAttribute("title");
      }
    }
    if (panelFp) {
      panelFp.disabled = !bookable;
      panelFp.classList.toggle("disabled", !bookable);
      panelFp.title = bookable ? "" : "Floor plan not available — flat not assigned to you";
    }
    syncFloorPlanLink(cardEl);
  }

  function openFlatDetailsModal() {
    var modalEl = document.getElementById("flat-details-modal");
    if (!modalEl || typeof bootstrap === "undefined" || !bootstrap.Modal) return;
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  window.floor21SelectFlat = function (el, showModal) {
    if (el.dataset.parking === "true") return;
    if (!isFlatBookable(el)) return;
    selectedFlatId = el.dataset.flatId;
    var titleEl = document.getElementById("panel-title");
    var flatNumEl = el.querySelector(".flat-number");
    var flatLabel = flatNumEl ? flatNumEl.textContent.trim() : "";
    if (titleEl) {
      titleEl.textContent = flatLabel ? "Flat " + flatLabel : "Flat details";
    }
    document.getElementById("panel-type").textContent = el.dataset.type || "";
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

  document.addEventListener("DOMContentLoaded", function () {
    mountModalsOnBody();
    initAllFlatCards();
    loadSalesPartnersIntoSelect();
    var modalEl = document.getElementById("floor-plan-modal");
    if (modalEl) {
      modalEl.addEventListener("hidden.bs.modal", function () {
        var img = document.getElementById("floor-plan-modal-img");
        if (img) img.removeAttribute("src");
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
        if (!selectedFlatId) return;
        var el = document.getElementById("flat-" + selectedFlatId);
        if (!el || el.dataset.parking === "true") return;
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
        if (card) syncAdminPanel(card);
      });
    }

    var adminDelete = document.getElementById("admin-delete-btn");
    if (adminDelete) {
      adminDelete.addEventListener("click", async function () {
        if (!selectedFlatId) return;
        var card = document.getElementById("flat-" + selectedFlatId);
        var label = card && card.querySelector(".flat-number") ? card.querySelector(".flat-number").textContent : selectedFlatId;
        if (!window.confirm("Remove flat " + label + "? This cannot be undone.")) return;
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
        window.location.reload();
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
          showAdminError("Choose which flat to remove on this floor.");
          return;
        }
        var form = readAdminForm();
        if (!window.confirm("Merge will delete the selected unit and keep this flat with the details above. Continue?")) {
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
        window.location.reload();
      });
    }

    var grid = document.getElementById("flat-grid");
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
            if (cardFromQuick && cardFromQuick.dataset.parking !== "true") {
              window.floor21SelectFlat(cardFromQuick);
            }
            return;
          }
          var cardForFp = e.target.closest(".flat-card");
          var fp = e.target.closest(".flat-floor-plan-trigger");
          if (fp) {
            e.preventDefault();
            e.stopPropagation();
            if (!cardForFp || cardForFp.dataset.parking === "true") return;
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
          if (card.dataset.parking === "true") return;
          if (!isFlatBookable(card)) return;
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
