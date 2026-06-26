(function () {
  var pendingTargetSelect = null;
  var modalInstance = null;

  function appRoot() {
    var r = document.body.getAttribute("data-app-root") || "";
    return r.replace(/\/$/, "");
  }

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (!token || !header) {
      return {};
    }
    var h = {};
    h[header.getAttribute("content")] = token.getAttribute("content");
    return h;
  }

  function bookingForm() {
    return document.querySelector("[data-booking-client-picker]");
  }

  function primarySelect() {
    var form = bookingForm();
    return form ? form.querySelector("#bookingPrimaryClient") : null;
  }

  function coOwnerRows() {
    var form = bookingForm();
    return form ? form.querySelector("#coOwnerRows") : null;
  }

  function clientModal() {
    return document.getElementById("bookingClientModal");
  }

  function getPrimaryClientId() {
    var sel = primarySelect();
    return sel && sel.value ? sel.value : "";
  }

  function clientSelects() {
    var form = bookingForm();
    if (!form) {
      return [];
    }
    var list = [];
    var primary = form.querySelector("#bookingPrimaryClient");
    if (primary) {
      list.push(primary);
    }
    form.querySelectorAll('select[name="coOwnerIds"]').forEach(function (sel) {
      list.push(sel);
    });
    return list;
  }

  function appendClientOption(select, id, displayName, addressMeta) {
    if (!select) {
      return;
    }
    var existing = select.querySelector('option[value="' + id + '"]');
    if (existing) {
      if (addressMeta) {
        existing.dataset.address1 = addressMeta.address1 || "";
        existing.dataset.address2 = addressMeta.address2 || "";
        existing.dataset.city = addressMeta.city || "";
      }
      return;
    }
    var opt = document.createElement("option");
    opt.value = id;
    opt.textContent = displayName;
    if (addressMeta) {
      opt.dataset.address1 = addressMeta.address1 || "";
      opt.dataset.address2 = addressMeta.address2 || "";
      opt.dataset.city = addressMeta.city || "";
    }
    select.appendChild(opt);
  }

  function addClientToAllSelects(id, displayName, addressMeta) {
    clientSelects().forEach(function (sel) {
      appendClientOption(sel, id, displayName, addressMeta);
    });
    var tpl = document.getElementById("coOwnerRowTemplate");
    if (tpl && tpl.content) {
      var tplSelect = tpl.content.querySelector('select[name="coOwnerIds"]');
      appendClientOption(tplSelect, id, displayName, addressMeta);
    }
  }

  function isCoOwnerModal() {
    return pendingTargetSelect && pendingTargetSelect.name === "coOwnerIds";
  }

  function primaryAddressFromOption() {
    var sel = primarySelect();
    if (!sel || !sel.value) {
      return null;
    }
    var opt = sel.options[sel.selectedIndex];
    if (!opt) {
      return null;
    }
    return {
      address1: opt.dataset.address1 || "",
      address2: opt.dataset.address2 || "",
      city: opt.dataset.city || "",
    };
  }

  function addressInputs() {
    return {
      address1: document.getElementById("quickClientAddress1"),
      address2: document.getElementById("quickClientAddress2"),
      city: document.getElementById("quickClientCity"),
    };
  }

  function readAddressFromInputs() {
    var fields = addressInputs();
    return {
      address1: fields.address1 ? fields.address1.value : "",
      address2: fields.address2 ? fields.address2.value : "",
      city: fields.city ? fields.city.value : "",
    };
  }

  function setAddressOnInputs(values, locked) {
    var fields = addressInputs();
    Object.keys(fields).forEach(function (key) {
      var el = fields[key];
      if (!el) {
        return;
      }
      el.value = values[key] || "";
      el.readOnly = !!locked;
      el.classList.toggle("bg-secondary-subtle", !!locked);
    });
  }

  function sameAddressCheckbox() {
    return document.getElementById("bookingClientSameAddress");
  }

  function applySamePrimaryAddress(usePrimary) {
    var modal = clientModal();
    var checkbox = sameAddressCheckbox();
    if (!modal || !checkbox) {
      return;
    }
    if (usePrimary) {
      modal.dataset.savedAddress = JSON.stringify(readAddressFromInputs());
      var primaryAddress = primaryAddressFromOption();
      if (primaryAddress) {
        setAddressOnInputs(primaryAddress, true);
      }
      return;
    }
    var saved = { address1: "", address2: "", city: "" };
    try {
      saved = JSON.parse(modal.dataset.savedAddress || "{}");
    } catch (e) {
      saved = { address1: "", address2: "", city: "" };
    }
    setAddressOnInputs(saved, false);
    delete modal.dataset.savedAddress;
  }

  function updateSameAddressCheckbox() {
    var wrap = document.getElementById("bookingClientSameAddressWrap");
    var checkbox = sameAddressCheckbox();
    if (!wrap || !checkbox) {
      return;
    }
    var show = isCoOwnerModal() && !!getPrimaryClientId();
    wrap.classList.toggle("d-none", !show);
    if (!show) {
      checkbox.checked = false;
      setAddressOnInputs({ address1: "", address2: "", city: "" }, false);
      return;
    }
    checkbox.checked = true;
    applySamePrimaryAddress(true);
  }

  function refreshCoOwnerOptions() {
    var primaryId = getPrimaryClientId();
    var rows = coOwnerRows();
    if (!rows) {
      return;
    }

    var selects = Array.from(rows.querySelectorAll('select[name="coOwnerIds"]'));
    var seen = new Set();
    if (primaryId) {
      seen.add(primaryId);
    }

    selects.forEach(function (sel) {
      if (sel.value && seen.has(sel.value)) {
        sel.value = "";
      } else if (sel.value) {
        seen.add(sel.value);
      }
    });

    var usedCoOwnerIds = new Set();
    selects.forEach(function (sel) {
      if (sel.value) {
        usedCoOwnerIds.add(sel.value);
      }
    });

    selects.forEach(function (sel) {
      Array.from(sel.options).forEach(function (opt) {
        if (!opt.value) {
          return;
        }
        if (opt.value === primaryId) {
          opt.disabled = true;
          if (sel.value === primaryId) {
            sel.value = "";
          }
          return;
        }
        opt.disabled = usedCoOwnerIds.has(opt.value) && sel.value !== opt.value;
      });
    });
  }

  function bindCoOwnerRow(row) {
    if (row.dataset.bound === "1") {
      return;
    }
    row.dataset.bound = "1";
    var removeBtn = row.querySelector(".co-owner-remove");
    if (removeBtn) {
      removeBtn.addEventListener("click", function () {
        row.remove();
        refreshCoOwnerOptions();
      });
    }
    var newBtn = row.querySelector(".co-owner-new-client");
    var select = row.querySelector('select[name="coOwnerIds"]');
    if (newBtn && select) {
      newBtn.addEventListener("click", function () {
        openClientModal(select);
      });
    }
    if (select) {
      select.addEventListener("change", refreshCoOwnerOptions);
    }
  }

  function addCoOwnerRow(selectedId) {
    var tpl = document.getElementById("coOwnerRowTemplate");
    var rows = coOwnerRows();
    if (!tpl || !rows) {
      return;
    }
    var row = tpl.content.firstElementChild.cloneNode(true);
    var select = row.querySelector('select[name="coOwnerIds"]');
    if (selectedId && select) {
      select.value = selectedId;
    }
    bindCoOwnerRow(row);
    rows.appendChild(row);
    refreshCoOwnerOptions();
  }

  function resetModalForm() {
    var modal = clientModal();
    if (modal) {
      modal.querySelectorAll("[data-api-field]").forEach(function (el) {
        el.value = "";
        el.readOnly = false;
        el.classList.remove("bg-secondary-subtle");
      });
      delete modal.dataset.savedAddress;
    }
    var wrap = document.getElementById("bookingClientSameAddressWrap");
    if (wrap) {
      wrap.classList.add("d-none");
    }
    var checkbox = sameAddressCheckbox();
    if (checkbox) {
      checkbox.checked = false;
    }
    var err = document.getElementById("bookingClientModalError");
    if (err) {
      err.textContent = "";
      err.classList.add("d-none");
    }
  }

  function collectClientPayload() {
    var modal = clientModal();
    var payload = {};
    if (!modal) {
      return payload;
    }
    modal.querySelectorAll("[data-api-field]").forEach(function (el) {
      var key = el.getAttribute("data-api-field");
      var val = (el.value || "").trim();
      if (el.type === "date") {
        payload[key] = val || null;
      } else {
        payload[key] = val;
      }
    });
    return payload;
  }

  function showModalError(message) {
    var err = document.getElementById("bookingClientModalError");
    if (!err) {
      return;
    }
    err.textContent = message;
    err.classList.remove("d-none");
  }

  function openClientModal(targetSelect) {
    var modal = clientModal();
    if (!modal || typeof bootstrap === "undefined") {
      return;
    }
    pendingTargetSelect = targetSelect || null;
    resetModalForm();
    updateSameAddressCheckbox();
    if (!modalInstance) {
      modalInstance = bootstrap.Modal.getOrCreateInstance(modal);
    }
    modalInstance.show();
    var first = document.getElementById("quickClientFirstName");
    if (first) {
      modal.addEventListener(
        "shown.bs.modal",
        function focusFirst() {
          first.focus();
          modal.removeEventListener("shown.bs.modal", focusFirst);
        },
        { once: true }
      );
    }
  }

  function createClient() {
    var saveBtn = document.getElementById("bookingClientModalSave");
    if (saveBtn) {
      saveBtn.disabled = true;
    }
    var checkbox = sameAddressCheckbox();
    if (checkbox && checkbox.checked && isCoOwnerModal()) {
      applySamePrimaryAddress(true);
    }
    var payload = collectClientPayload();
    fetch(appRoot() + "/clients/quick", {
      method: "POST",
      headers: Object.assign({ "Content-Type": "application/json" }, csrfHeaders()),
      body: JSON.stringify(payload),
    })
      .then(function (res) {
        return res.json().then(function (body) {
          return { ok: res.ok, body: body };
        });
      })
      .then(function (result) {
        if (!result.ok) {
          showModalError(result.body && result.body.error ? result.body.error : "Could not create client.");
          return;
        }
        addClientToAllSelects(result.body.id, result.body.displayName, {
          address1: payload.address1 || "",
          address2: payload.address2 || "",
          city: payload.city || "",
        });
        if (pendingTargetSelect) {
          pendingTargetSelect.value = result.body.id;
        }
        refreshCoOwnerOptions();
        if (modalInstance) {
          modalInstance.hide();
        }
        pendingTargetSelect = null;
      })
      .catch(function () {
        showModalError("Could not create client. Please try again.");
      })
      .finally(function () {
        if (saveBtn) {
          saveBtn.disabled = false;
        }
      });
  }

  function init() {
    var form = bookingForm();
    if (!form || form.dataset.pickerReady === "1") {
      return;
    }
    if (!clientModal()) {
      return;
    }
    form.dataset.pickerReady = "1";

    var rows = coOwnerRows();
    if (rows) {
      rows.querySelectorAll(".co-owner-row").forEach(bindCoOwnerRow);
    }

    var addBtn = document.getElementById("addCoOwnerRow");
    if (addBtn) {
      addBtn.addEventListener("click", function () {
        addCoOwnerRow("");
      });
    }

    var primaryNewBtn = document.getElementById("bookingPrimaryNewClient");
    var primary = primarySelect();
    if (primaryNewBtn && primary) {
      primaryNewBtn.addEventListener("click", function () {
        openClientModal(primary);
      });
      primary.addEventListener("change", refreshCoOwnerOptions);
    }

    var saveBtn = document.getElementById("bookingClientModalSave");
    if (saveBtn && saveBtn.dataset.bound !== "1") {
      saveBtn.dataset.bound = "1";
      saveBtn.addEventListener("click", createClient);
    }

    var sameAddress = sameAddressCheckbox();
    if (sameAddress && sameAddress.dataset.bound !== "1") {
      sameAddress.dataset.bound = "1";
      sameAddress.addEventListener("change", function () {
        applySamePrimaryAddress(sameAddress.checked);
      });
    }

    form.addEventListener("submit", function () {
      refreshCoOwnerOptions();
    });

    refreshCoOwnerOptions();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
  document.addEventListener("turbo:load", init);
})();
