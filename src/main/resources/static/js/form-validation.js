/**
 * Bootstrap 5 client-side validation for forms with .needs-validation
 */
(function () {
  function initBootstrapValidation(root) {
    var scope = root || document;
    scope.querySelectorAll(".needs-validation").forEach(function (form) {
      if (form.dataset.f21ValidationBound === "true") {
        return;
      }
      form.dataset.f21ValidationBound = "true";
      form.addEventListener(
        "submit",
        function (event) {
          if (!form.checkValidity()) {
            event.preventDefault();
            event.stopPropagation();
          }
          form.classList.add("was-validated");
        },
        false,
      );
    });
  }

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
    initBootstrapValidation(document);
  });
})();
