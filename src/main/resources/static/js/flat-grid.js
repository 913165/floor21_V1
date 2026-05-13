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

  function syncBuyerTooltip(el, text) {
    var t = text || "";
    el.setAttribute("data-buyer-tooltip", t);
    el.setAttribute("title", t);
    var tip = el.querySelector(".flat-card-buyertip");
    if (t) {
      if (!tip) {
        tip = document.createElement("div");
        tip.className = "flat-card-buyertip";
        el.insertBefore(tip, el.firstChild);
      }
      tip.textContent = t;
    } else if (tip) {
      tip.remove();
    }
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
        if (flat.clientId) {
          el.dataset.clientId = flat.clientId;
        } else {
          delete el.dataset.clientId;
        }
        syncBuyerTooltip(el, flat.buyerTooltip || "");
        syncCardOwner(el, flat);
        el.classList.remove("flat-available", "flat-booked", "flat-hold", "flat-parking");
        if (flat.parking) el.classList.add("flat-parking");
        else if (flat.status === "AVAILABLE") el.classList.add("flat-available");
        else if (flat.status === "BOOKED") el.classList.add("flat-booked");
        else el.classList.add("flat-hold");
      });
    });
    applyBookingSelectionHighlight();
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

  function openFloorPlanModal(url, title) {
    var img = document.getElementById("floor-plan-modal-img");
    var modalEl = document.getElementById("floor-plan-modal");
    var titleEl = document.getElementById("floor-plan-modal-title");
    if (!img || !modalEl || typeof bootstrap === "undefined" || !bootstrap.Modal) return;
    img.src = url;
    img.alt = title || "Floor plan";
    if (titleEl) titleEl.textContent = title || "Floor plan";
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  window.floor21SelectFlat = function (el) {
    if (el.dataset.parking === "true") return;
    selectedFlatId = el.dataset.flatId;
    var panel = document.getElementById("booking-panel");
    if (!panel) return;
    panel.classList.remove("d-none");
    document.getElementById("panel-type").textContent = el.dataset.type || "";
    document.getElementById("panel-floor").textContent = el.dataset.floor || "";
    document.getElementById("panel-area").textContent = el.dataset.area || "";
    document.getElementById("panel-price").textContent = el.dataset.price || "";
    var book = document.getElementById("book-btn");
    if (book) {
      book.href = appRoot() + "/bookings/new?flatId=" + encodeURIComponent(selectedFlatId);
      book.classList.toggle("disabled", el.dataset.status !== "AVAILABLE" && el.dataset.status !== "HOLD");
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
      if (slot && bid) {
        fpBtn.setAttribute("data-floor-plan-url", appRoot() + "/buildings/" + bid + "/floor-plan/" + encodeURIComponent(slot));
        fpBtn.classList.remove("d-none");
      } else {
        fpBtn.classList.add("d-none");
        fpBtn.removeAttribute("data-floor-plan-url");
      }
    }
    applyBookingSelectionHighlight();
  };

  document.addEventListener("DOMContentLoaded", function () {
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
        var url = panelFp.getAttribute("data-floor-plan-url");
        if (!url) return;
        var typeEl = document.getElementById("panel-type");
        var sub = typeEl && typeEl.textContent ? typeEl.textContent.trim() + " — Floor plan" : "Floor plan";
        openFloorPlanModal(url, sub);
      });
    }
    var hold = document.getElementById("hold-btn");
    if (hold) {
      hold.addEventListener("click", async function () {
        if (!selectedFlatId) return;
        var el = document.getElementById("flat-" + selectedFlatId);
        if (!el || el.dataset.parking === "true") return;
        var next = el.dataset.status === "HOLD" ? "AVAILABLE" : "HOLD";
        await postStatus(selectedFlatId, next);
        await refreshGrid();
      });
    }
    var grid = document.getElementById("flat-grid");
    if (grid) {
      grid.addEventListener("click", function (e) {
        var fp = e.target.closest(".flat-floor-plan-trigger");
        if (fp) {
          e.preventDefault();
          e.stopPropagation();
          var url = fp.getAttribute("data-floor-plan-url");
          if (url) {
            var c = fp.closest(".flat-card");
            var typ = c && c.dataset.type ? c.dataset.type + " — Floor plan" : "Floor plan";
            openFloorPlanModal(url, typ);
          }
          return;
        }
        var card = e.target.closest(".flat-card");
        if (!card || !grid.contains(card)) return;
        if (card.dataset.parking === "true") return;
        window.floor21SelectFlat(card);
      });
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
