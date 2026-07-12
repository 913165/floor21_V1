(function () {
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
    return document.querySelector("[data-booking-broker-picker]");
  }

  function brokerSelect() {
    var form = bookingForm();
    return form ? form.querySelector("#bookingBrokerSelect") : null;
  }

  function brokerModal() {
    return document.getElementById("bookingBrokerModal");
  }

  function appendBrokerOption(select, id, fullName) {
    if (!select) {
      return;
    }
    if (select.querySelector('option[value="' + id + '"]')) {
      return;
    }
    var opt = document.createElement("option");
    opt.value = id;
    opt.textContent = fullName;
    select.appendChild(opt);
  }

  function resetModalForm() {
    var modal = brokerModal();
    if (modal) {
      modal.querySelectorAll("[data-api-field]").forEach(function (el) {
        el.value = "";
      });
    }
    var err = document.getElementById("bookingBrokerModalError");
    if (err) {
      err.textContent = "";
      err.classList.add("d-none");
    }
  }

  function collectBrokerPayload() {
    var modal = brokerModal();
    var payload = {};
    if (!modal) {
      return payload;
    }
    modal.querySelectorAll("[data-api-field]").forEach(function (el) {
      var key = el.getAttribute("data-api-field");
      var val = (el.value || "").trim();
      if (el.type === "number") {
        payload[key] = val === "" ? null : val;
      } else {
        payload[key] = val;
      }
    });
    return payload;
  }

  function showModalError(message) {
    var err = document.getElementById("bookingBrokerModalError");
    if (!err) {
      return;
    }
    err.textContent = message;
    err.classList.remove("d-none");
  }

  function openBrokerModal() {
    var modal = brokerModal();
    if (!modal || typeof bootstrap === "undefined") {
      return;
    }
    resetModalForm();
    if (!modalInstance) {
      modalInstance = bootstrap.Modal.getOrCreateInstance(modal);
    }
    modalInstance.show();
    var first = document.getElementById("quickBrokerFullName");
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

  function createBroker() {
    var saveBtn = document.getElementById("bookingBrokerModalSave");
    if (saveBtn) {
      saveBtn.disabled = true;
    }
    var payload = collectBrokerPayload();
    if (!payload.fullName) {
      showModalError("Full name is required.");
      if (saveBtn) {
        saveBtn.disabled = false;
      }
      return;
    }
    fetch(appRoot() + "/brokers/quick", {
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
          showModalError(result.body && result.body.error ? result.body.error : "Could not create broker.");
          return;
        }
        var select = brokerSelect();
        appendBrokerOption(select, result.body.id, result.body.fullName);
        if (select) {
          select.value = result.body.id;
        }
        if (modalInstance) {
          modalInstance.hide();
        }
      })
      .catch(function () {
        showModalError("Could not create broker. Please try again.");
      })
      .finally(function () {
        if (saveBtn) {
          saveBtn.disabled = false;
        }
      });
  }

  function init() {
    var form = bookingForm();
    if (!form || form.dataset.brokerPickerReady === "1") {
      return;
    }
    if (!brokerModal()) {
      return;
    }
    form.dataset.brokerPickerReady = "1";

    var newBtn = document.getElementById("bookingBrokerNew");
    if (newBtn && newBtn.dataset.bound !== "1") {
      newBtn.dataset.bound = "1";
      newBtn.addEventListener("click", openBrokerModal);
    }

    var saveBtn = document.getElementById("bookingBrokerModalSave");
    if (saveBtn && saveBtn.dataset.bound !== "1") {
      saveBtn.dataset.bound = "1";
      saveBtn.addEventListener("click", createBroker);
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
  document.addEventListener("turbo:load", init);
})();
