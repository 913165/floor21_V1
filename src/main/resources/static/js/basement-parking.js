(function () {
  "use strict";

  function appRoot() {
    var r = document.body.getAttribute("data-app-root") || "";
    return r.replace(/\/$/, "");
  }

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (!token || !header) return {};
    var out = {};
    out[header.getAttribute("content")] = token.getAttribute("content");
    return out;
  }

  function isPlatformAdminEdit() {
    var grid = document.getElementById("flat-grid");
    return !!(grid && grid.getAttribute("data-platform-admin-edit") === "true");
  }

  function buildingId() {
    var grid = document.getElementById("flat-grid");
    return grid ? grid.getAttribute("data-building-id") : "";
  }

  function basementsContainer() {
    return document.getElementById("flat-basements-container");
  }

  function basementSections() {
    var container = basementsContainer();
    return container
      ? container.querySelectorAll(".flat-basement-section[data-floor-number]")
      : [];
  }

  function basementPanelForFloor(floorNumber) {
    return document.querySelector(
      '.flat-basement-section__panel[data-floor-number="' + floorNumber + '"]'
    );
  }

  function escapeHtml(text) {
    return String(text || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/"/g, "&quot;");
  }

  function buildBasementSectionHtml(b) {
    var floor = b.floorNumber;
    var label = b.label || (floor === -1 ? "Basement" : "Basement " + Math.abs(floor));
    var meta = b.slotCount + " slots · " + (b.rangeLabel || "");
    var layoutLinks =
      '<div class="flat-parking-section__layout-links d-flex flex-wrap align-items-center gap-2">' +
      (isPlatformAdminEdit()
        ? '<button type="button" class="flat-parking-layout-upload-link btn btn-link btn-sm px-0">Upload layout</button>'
        : "") +
      (b.hasLayoutImage
        ? '<button type="button" class="flat-parking-layout-view-link btn btn-link btn-sm px-0">View layout</button>'
        : "") +
      "</div>";
    return (
      '<div class="flat-basement-section flat-floor-row mb-3" data-floor-number="' +
      floor +
      '">' +
      '<div class="flat-floor-row__rail">' +
      '<span class="floor-label text-nowrap">' +
      escapeHtml(label) +
      "</span>" +
      '<span class="flat-floor-row__divider" aria-hidden="true"></span>' +
      (isPlatformAdminEdit()
        ? '<button type="button" class="basement-remove-btn btn btn-sm btn-outline-danger ms-auto" data-floor-number="' +
          floor +
          '">Remove basement</button>'
        : "") +
      "</div>" +
      '<div class="flat-basement-section__panel flat-parking-section flat-parking-section--configured flat-parking-section--split"' +
      ' data-floor-number="' +
      floor +
      '"' +
      ' data-slot-count="' +
      (b.slotCount != null ? b.slotCount : 0) +
      '"' +
      ' data-car-size-percent="' +
      (b.parkingCarSizePercent != null ? b.parkingCarSizePercent : 180) +
      '"' +
      ' data-grid-rows="' +
      (b.gridRows != null ? b.gridRows : 1) +
      '"' +
      ' data-min-grid-rows="' +
      (b.minGridRows != null ? b.minGridRows : 1) +
      '"' +
      (b.rangeLabel ? ' data-range-label="' + escapeHtml(b.rangeLabel) + '"' : "") +
      ' data-configured="true"' +
      ' data-car-lift-count="' +
      (b.carLiftCount != null ? b.carLiftCount : 0) +
      '"' +
      ' data-passenger-lift-count="' +
      (b.passengerLiftCount != null ? b.passengerLiftCount : 0) +
      '"' +
      ' data-gate-count="' +
      (b.gateCount != null ? b.gateCount : 0) +
      '"' +
      (b.firstFlatId ? ' data-first-flat-id="' + b.firstFlatId + '"' : "") +
      (b.areaSqft != null ? ' data-area="' + b.areaSqft + '"' : "") +
      (b.basePrice != null ? ' data-price="' + b.basePrice + '"' : "") +
      ' data-layout-image="' +
      (b.hasLayoutImage ? "true" : "false") +
      '"' +
      ' data-is-basement="true"' +
      ' data-basement-label="' +
      escapeHtml(label) +
      '">' +
      '<div class="flat-parking-section__layout">' +
      '<div class="flat-parking-section__summary">' +
      '<div class="flat-parking-section__head">' +
      '<span class="flat-parking-section__title">' +
      escapeHtml(label) +
      "</span>" +
      '<span class="flat-parking-section__meta">' +
      escapeHtml(meta) +
      "</span>" +
      "</div>" +
      (isPlatformAdminEdit()
        ? '<button type="button" class="flat-parking-configure-link btn btn-link btn-sm px-0">Configure</button>'
        : "") +
      layoutLinks +
      "</div>" +
      '<div class="flat-parking-section__plan" aria-hidden="false">' +
      '<div class="parking-plan flat-parking-section__plan-root" data-floor-number="' +
      floor +
      '"></div>' +
      "</div></div></div></div>"
    );
  }

  function renderBasements(basements) {
    var container = basementsContainer();
    if (!container) return;
    var addRow = container.querySelector(".flat-basement-add-row");
    container.querySelectorAll(".flat-basement-section").forEach(function (el) {
      el.remove();
    });
    var html = "";
    (basements || []).forEach(function (b) {
      if (b && b.configured !== false) {
        html += buildBasementSectionHtml(b);
      }
    });
    if (addRow) {
      addRow.insertAdjacentHTML("beforebegin", html);
    } else {
      container.innerHTML = html;
    }
  }

  async function parseErrorResponse(res) {
    try {
      var body = await res.json();
      return body.error || "Request failed (" + res.status + ").";
    } catch (e) {
      return "Request failed (" + res.status + ").";
    }
  }

  async function fetchNextBasementFloor() {
    var id = buildingId();
    if (!id) return null;
    var res = await fetch(appRoot() + "/buildings/" + id + "/basements/next-floor", {
      headers: csrfHeaders(),
    });
    if (!res.ok) {
      window.alert(await parseErrorResponse(res));
      return null;
    }
    return res.json();
  }

  async function openAddBasementConfig() {
    if (!isPlatformAdminEdit()) return;
    var next = await fetchNextBasementFloor();
    if (!next || next.floorNumber == null) return;
    if (window.floor21OpenParkingConfigModalForBasement) {
      window.floor21OpenParkingConfigModalForBasement(next.floorNumber, next.label);
    }
  }

  function openBasementConfigModal(panel) {
    if (!panel || !isPlatformAdminEdit()) return;
    var floorNumber = panel.dataset.floorNumber;
    var label = panel.dataset.basementLabel || "Basement";
    if (window.floor21OpenParkingConfigModalForBasement) {
      window.floor21OpenParkingConfigModalForBasement(floorNumber, label, panel);
      return;
    }
    if (window.openParkingConfigModal) {
      window.openParkingConfigModal(panel);
    }
  }

  async function removeBasement(floorNumber) {
    if (!isPlatformAdminEdit() || floorNumber == null || floorNumber === "") return;
    var panel = basementPanelForFloor(floorNumber);
    var label =
      (panel && panel.dataset.basementLabel) ||
      (Number(floorNumber) === -1 ? "Basement" : "Basement " + Math.abs(Number(floorNumber)));
    if (
      !window.confirm(
        "Remove " + label + "? All slots and layout on this level will be deleted."
      )
    ) {
      return;
    }
    var id = buildingId();
    if (!id) return;
    var res = await fetch(
      appRoot() + "/buildings/" + id + "/basement/" + encodeURIComponent(floorNumber),
      { method: "DELETE", headers: csrfHeaders() }
    );
    if (!res.ok) {
      window.alert(await parseErrorResponse(res));
      return;
    }
    var basements = await res.json();
    window.floor21SyncBasements(basements);
  }

  window.floor21SyncBasements = function (basements) {
    renderBasements(basements || []);
    if (window.loadAllConfiguredParkingPlans) {
      void window.loadAllConfiguredParkingPlans();
    }
  };

  window.floor21SyncBasement = function (basement) {
    if (Array.isArray(basement)) {
      window.floor21SyncBasements(basement);
      return;
    }
    if (basement && basement.floorNumber != null) {
      var existing = [];
      basementSections().forEach(function (section) {
        var fn = section.getAttribute("data-floor-number");
        if (fn && fn !== String(basement.floorNumber)) {
          var panel = basementPanelForFloor(fn);
          if (panel) {
            existing.push({
              floorNumber: Number(fn),
              label: panel.dataset.basementLabel || "Basement",
              configured: true,
              slotCount: Number(panel.dataset.slotCount || 0),
              rangeLabel: panel.dataset.rangeLabel || "",
              parkingCarSizePercent: Number(panel.dataset.carSizePercent || 180),
              gridRows: Number(panel.dataset.gridRows || 1),
              minGridRows: Number(panel.dataset.minGridRows || 1),
              carLiftCount: Number(panel.dataset.carLiftCount || 0),
              passengerLiftCount: Number(panel.dataset.passengerLiftCount || 0),
              gateCount: Number(panel.dataset.gateCount || 0),
              hasLayoutImage: panel.getAttribute("data-layout-image") === "true",
              firstFlatId: panel.dataset.firstFlatId || null,
              areaSqft: panel.dataset.area || null,
              basePrice: panel.dataset.price || null,
            });
          }
        }
      });
      if (basement.configured !== false) {
        existing.push(basement);
      }
      existing.sort(function (a, b) {
        return b.floorNumber - a.floorNumber;
      });
      window.floor21SyncBasements(existing);
      return;
    }
    window.floor21SyncBasements([]);
  };

  function onPageReady(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn);
    } else {
      fn();
    }
    document.addEventListener("turbo:load", fn);
  }

  onPageReady(function () {
    var container = basementsContainer();
    if (!container || container.dataset.f21BasementInit === "true") return;
    container.dataset.f21BasementInit = "true";

    document.addEventListener("click", function (e) {
      var addBtn = e.target.closest(".basement-add-btn");
      if (addBtn) {
        e.preventDefault();
        void openAddBasementConfig();
        return;
      }
      var removeBtn = e.target.closest(".basement-remove-btn");
      if (removeBtn) {
        e.preventDefault();
        e.stopPropagation();
        void removeBasement(removeBtn.getAttribute("data-floor-number"));
        return;
      }
      var configure = e.target.closest(
        ".flat-basement-section .flat-parking-configure-link"
      );
      if (configure) {
        e.preventDefault();
        var panel = configure.closest(".flat-basement-section__panel");
        openBasementConfigModal(panel);
      }
    });

    if (basementSections().length && window.loadAllConfiguredParkingPlans) {
      void window.loadAllConfiguredParkingPlans();
    }
  });
})();
