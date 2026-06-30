(function () {
  var FRAME_ID = "floor21-main";

  function appRoot() {
    var body = document.body;
    return body && body.getAttribute("data-app-root") ? body.getAttribute("data-app-root") : "";
  }

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (!token || !header) return {};
    var h = {};
    h[header.getAttribute("content")] = token.getAttribute("content");
    return h;
  }

  function parseErrorResponse(res) {
    return res
      .json()
      .then(function (body) {
        if (body && body.error && body.error !== "Bad Request") {
          return body.error;
        }
        if (body && body.message) {
          return body.message;
        }
        return "Could not load parking data (HTTP " + res.status + ").";
      })
      .catch(function () {
        return "Could not load parking data (HTTP " + res.status + ").";
      });
  }

  function sameId(a, b) {
    if (!a || !b) return false;
    return String(a).toLowerCase() === String(b).toLowerCase();
  }

  function formatSlot(slot) {
    return (
      "Floor " +
      slot.floorNumber +
      " \u00b7 Slot " +
      slot.slotNumber +
      " (" +
      slot.flatNumber +
      ")"
    );
  }

  function showError(message) {
    var el = document.getElementById("booking-parking-links-error");
    if (!el) return;
    if (!message) {
      el.textContent = "";
      el.classList.add("d-none");
      return;
    }
    el.textContent = message;
    el.classList.remove("d-none");
  }

  function syncNoSlotsHint(count) {
    var hint = document.getElementById("booking-parking-no-slots-hint");
    if (!hint) return;
    hint.classList.toggle("d-none", count > 0);
  }

  function hasServerLinkedParking() {
    var list = document.getElementById("booking-parking-links-list");
    return list && list.querySelector("[data-parking-flat-id]") != null;
  }

  function hasDropdownOptions() {
    return countSelectOptions(document.getElementById("booking-parking-add")) > 0;
  }

  function countSelectOptions(select) {
    if (!select) return 0;
    var count = 0;
    for (var i = 0; i < select.options.length; i++) {
      if (select.options[i].value) count += 1;
    }
    return count;
  }

  function renderList(slots, editable) {
    var list = document.getElementById("booking-parking-links-list");
    var empty = document.getElementById("booking-parking-links-empty");
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
      li.setAttribute("data-parking-flat-id", slot.parkingFlatId);
      li.setAttribute("data-floor-number", String(slot.floorNumber));
      var label = document.createElement("span");
      label.textContent = formatSlot(slot);
      li.appendChild(label);
      if (editable) {
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "btn btn-link btn-sm text-danger px-1 py-0 booking-parking-unlink";
        btn.textContent = "Remove";
        li.appendChild(btn);
      }
      list.appendChild(li);
    });
  }

  async function postParkingLink(parkingFlatId, residentialFlatId) {
    var headers = Object.assign({ "Content-Type": "application/json" }, csrfHeaders());
    return fetch(appRoot() + "/flats/" + parkingFlatId + "/parking-link", {
      method: "POST",
      headers: headers,
      body: JSON.stringify({ residentialFlatId: residentialFlatId }),
    });
  }

  function initBookingParkingLinks() {
    var root = document.getElementById("booking-parking-links");
    if (!root) return;
    if (root.getAttribute("data-initialized") === "true") return;

    var bookingId = root.getAttribute("data-booking-id");
    var flatId = root.getAttribute("data-flat-id");
    var buildingId = root.getAttribute("data-building-id");
    var editable = root.getAttribute("data-editable") === "true";

    if (!bookingId || !flatId) return;

    root.setAttribute("data-initialized", "true");

    async function loadParkingOptions() {
      var bookingUrl = appRoot() + "/bookings/" + bookingId + "/parking-slots-for-link";
      var res = await fetch(bookingUrl, { headers: { Accept: "application/json" } });
      if (res.ok) {
        return { ok: true, options: await res.json(), error: null };
      }
      if (buildingId) {
        var buildingUrl =
          appRoot() +
          "/buildings/" +
          buildingId +
          "/parking-slots-for-link?residentialFlatId=" +
          encodeURIComponent(flatId);
        var buildingRes = await fetch(buildingUrl, { headers: { Accept: "application/json" } });
        if (buildingRes.ok) {
          return { ok: true, options: await buildingRes.json(), error: null };
        }
        return { ok: false, options: [], error: await parseErrorResponse(buildingRes) };
      }
      return { ok: false, options: [], error: await parseErrorResponse(res) };
    }

    async function populateAddSelect(linkedSlots) {
      var select = document.getElementById("booking-parking-add");
      if (!select) return;

      var serverRenderedCount = countSelectOptions(select);
      var linkedIds = {};
      (linkedSlots || []).forEach(function (s) {
        if (s.parkingFlatId) linkedIds[String(s.parkingFlatId).toLowerCase()] = true;
        if (s.id) linkedIds[String(s.id).toLowerCase()] = true;
      });

      var loaded = await loadParkingOptions();
      if (!loaded.ok) {
        if (!hasDropdownOptions()) {
          showError(loaded.error);
        } else {
          showError("");
        }
        syncNoSlotsHint(countSelectOptions(select));
        select.disabled = false;
        return;
      }

      var options = loaded.options || [];
      var toAdd = [];
      options.forEach(function (opt) {
        var optId = opt.id ? String(opt.id).toLowerCase() : "";
        if (optId && linkedIds[optId]) return;
        if (opt.linkedResidentialFlatId && !sameId(opt.linkedResidentialFlatId, flatId)) return;
        toAdd.push(opt);
      });

      if (toAdd.length > 0 || serverRenderedCount === 0) {
        select.innerHTML = '<option value="">\u2014 Select slot \u2014</option>';
        toAdd.forEach(function (opt) {
          var option = document.createElement("option");
          option.value = opt.id;
          option.textContent = formatSlot(opt);
          select.appendChild(option);
        });
      }

      var added = toAdd.length > 0 ? toAdd.length : serverRenderedCount;
      select.disabled = false;
      syncNoSlotsHint(added);
      if (toAdd.length === 0 && options.length > 0 && serverRenderedCount === 0) {
        showError("All available parking slots are already linked to this or other flats.");
      } else if (added === 0) {
        showError("");
      } else {
        showError("");
      }
    }

    async function loadSlots() {
      var res = await fetch(appRoot() + "/bookings/" + bookingId + "/linked-parking", {
        headers: { Accept: "application/json" },
      });
      if (!res.ok) {
        if (!hasServerLinkedParking() && !hasDropdownOptions()) {
          showError(await parseErrorResponse(res));
        }
        return [];
      }
      showError("");
      var slots = await res.json();
      renderList(slots, editable);
      if (editable) {
        await populateAddSelect(slots);
      }
      return slots;
    }

    async function linkSlot(parkingFlatId) {
      var res = await postParkingLink(parkingFlatId, flatId);
      if (!res.ok) {
        showError(await parseErrorResponse(res));
        return;
      }
      await loadSlots();
    }

    async function unlinkSlot(parkingFlatId) {
      var res = await postParkingLink(parkingFlatId, null);
      if (!res.ok) {
        showError(await parseErrorResponse(res));
        return;
      }
      await loadSlots();
    }

    if (!root.getAttribute("data-click-bound")) {
      root.setAttribute("data-click-bound", "true");
      root.addEventListener("click", function (e) {
        var unlinkBtn = e.target.closest(".booking-parking-unlink");
        if (unlinkBtn) {
          e.preventDefault();
          var item = unlinkBtn.closest("[data-parking-flat-id]");
          if (!item) return;
          void unlinkSlot(item.getAttribute("data-parking-flat-id"));
          return;
        }
        if (e.target.closest("#booking-parking-add-btn")) {
          e.preventDefault();
          var addSelect = document.getElementById("booking-parking-add");
          if (!addSelect || !addSelect.value) {
            showError("Select a parking slot to link.");
            return;
          }
          void linkSlot(addSelect.value);
        }
      });
    }

    if (editable) {
      syncNoSlotsHint(countSelectOptions(document.getElementById("booking-parking-add")));
      void loadSlots();
    }
  }

  function onBookingPageReady() {
    initBookingParkingLinks();
  }

  document.addEventListener("turbo:before-cache", function () {
    var root = document.getElementById("booking-parking-links");
    if (root) {
      root.removeAttribute("data-initialized");
    }
  });

  document.addEventListener("turbo:load", onBookingPageReady);
  document.addEventListener("DOMContentLoaded", onBookingPageReady);
  document.addEventListener("turbo:frame-render", function (event) {
    if (event.target && event.target.id === FRAME_ID) {
      onBookingPageReady();
    }
  });
})();
