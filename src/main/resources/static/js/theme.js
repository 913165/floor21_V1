(function () {
  var KEY = "floor21-theme";

  function updateToggleVisual(theme) {
    var isDark = theme === "dark";
    document.querySelectorAll(".floor21-theme-toggle").forEach(function (btn) {
      var sun = btn.querySelector(".floor21-theme-icon-sun");
      var moon = btn.querySelector(".floor21-theme-icon-moon");
      btn.setAttribute("aria-checked", isDark ? "true" : "false");
      btn.setAttribute("title", isDark ? "Switch to light mode" : "Switch to dark mode");
      btn.setAttribute("aria-label", isDark ? "Switch to light mode" : "Switch to dark mode");
      if (sun) {
        sun.classList.toggle("d-none", isDark);
      }
      if (moon) {
        moon.classList.toggle("d-none", !isDark);
      }
    });
  }

  function apply(theme) {
    if (theme !== "dark" && theme !== "light") {
      theme = "light";
    }
    document.documentElement.setAttribute("data-bs-theme", theme);
    try {
      localStorage.setItem(KEY, theme);
    } catch (e) {
      /* ignore */
    }
    updateToggleVisual(theme);
  }

  window.floor21ApplyTheme = apply;

  document.addEventListener("DOMContentLoaded", function () {
    var current = document.documentElement.getAttribute("data-bs-theme") || "light";
    apply(current);
    document.querySelectorAll(".floor21-theme-toggle").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var cur = document.documentElement.getAttribute("data-bs-theme") || "light";
        apply(cur === "dark" ? "light" : "dark");
      });
    });
  });
})();
