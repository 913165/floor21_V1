(function () {
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
      " · Slot " +
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

  function countRenderedSlots() {
    var list = document.getElementById("booking-parking-links-list");
    if (!list) return 0;
    return list.querySelectorAll(".flat-parking-links-list__item").length;
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
    if (!root || root.getAttribute("data-initialized") === "true") return;
    root.setAttribute("data-initialized", "true");

    var bookingId = root.getAttribute("data-booking-id");
    var flatId = root.getAttribute("data-flat-id");
    var editable = root.getAttribute("data-editable") === "true";

    if (!bookingId || !flatId) {
      return;
    }

    async function loadSlots() {
      var hadServerSlots = countRenderedSlots() > 0;
      var res = await fetch(appRoot() + "/bookings/" + bookingId + "/linked-parking", {
        headers: { Accept: "application/json" },
      });
      if (!res.ok) {
        if (!hadServerSlots) {
          showError(await parseErrorResponse(res));
          renderList([], editable);
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

    async function loadParkingOptions() {
      var res = await fetch(appRoot() + "/bookings/" + bookingId + "/parking-slots-for-link", {
        headers: { Accept: "application/json" },
      });
      if (!res.ok) {
        return { ok: false, options: [], error: await parseErrorResponse(res) };
      }
      return { ok: true, options: await res.json(), error: null };
    }

    async function populateAddSelect(linkedSlots) {
      var select = document.getElementById("booking-parking-add");
      if (!select) return;
      var linkedIds = {};
      (linkedSlots || []).forEach(function (s) {
        if (s.parkingFlatId) linkedIds[s.parkingFlatId] = true;
        if (s.id) linkedIds[s.id] = true;
      });
      select.innerHTML = '<option value="">— Select slot —</option>';
      select.disabled = true;
      var loaded = await loadParkingOptions();
      if (!loaded.ok) {
        showError(loaded.error);
        return;
      }
      var options = loaded.options || [];
      var added = 0;
      options.forEach(function (opt) {
        if (linkedIds[opt.id]) return;
        if (opt.linkedResidentialFlatId && !sameId(opt.linkedResidentialFlatId, flatId)) return;
        var option = document.createElement("option");
        option.value = opt.id;
        option.textContent =
          "Floor " +
          opt.floorNumber +
          " · Slot " +
          opt.slotNumber +
          " (" +
          opt.flatNumber +
          ")";
        select.appendChild(option);
        added += 1;
      });
      select.disabled = added === 0;
      if (added === 0 && options.length > 0) {
        showError("All available parking slots are already linked to this or other flats.");
      } else if (added === 0) {
        showError("No parking slots available to link for this flat.");
      } else {
        showError("");
      }
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
        var select = document.getElementById("booking-parking-add");
        if (!select || !select.value) {
          showError("Select a parking slot to link.");
          return;
        }
        void linkSlot(select.value);
      }
    });

    if (editable) {
      void loadSlots();
    }
  }

  document.addEventListener("turbo:load", initBookingParkingLinks);
  document.addEventListener("DOMContentLoaded", initBookingParkingLinks);
})();
