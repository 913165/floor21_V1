/**
 * Demand letter sent toggle on payment schedule milestone rows.
 */
(function () {
  "use strict";

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

  function wireToggle(input) {
    if (!input || input.dataset.dlSentWired === "true") {
      return;
    }
    input.dataset.dlSentWired = "true";
    input.addEventListener("change", function () {
      var bookingId = input.getAttribute("data-booking-id");
      var slabId = input.getAttribute("data-slab-id");
      if (!bookingId || !slabId) {
        return;
      }
      var sent = input.checked;
      input.disabled = true;
      fetch(appRoot() + "/bookings/payment-schedule/demand-letter-sent", {
        method: "POST",
        headers: Object.assign(
          { "Content-Type": "application/json", Accept: "application/json" },
          csrfHeaders()
        ),
        body: JSON.stringify({
          bookingId: bookingId,
          slabId: slabId,
          sent: sent,
        }),
      })
        .then(function (res) {
          if (!res.ok) {
            return res.text().then(function (text) {
              throw new Error(text || "Could not save DL sent status.");
            });
          }
          return res.json();
        })
        .then(function (data) {
          input.checked = !!(data && data.sent);
        })
        .catch(function (err) {
          input.checked = !sent;
          window.alert(
            err && err.message ? err.message : "Could not save DL sent status."
          );
        })
        .finally(function () {
          input.disabled = false;
        });
    });
  }

  function init(root) {
    var scope = root && root.querySelectorAll ? root : document;
    scope.querySelectorAll(".js-dl-sent-toggle").forEach(wireToggle);
  }

  document.addEventListener("DOMContentLoaded", function () {
    init(document);
  });
  document.addEventListener("turbo:load", function () {
    init(document);
  });
  document.addEventListener("turbo:frame-render", function (event) {
    if (!event.target || event.target.id === "floor21-main") {
      init(document);
    }
  });
})();
