/**
 * Keeps sidebar highlight in sync when Turbo preserves the sidebar across visits.
 */
(function () {
  function servletPathFromLocation() {
    var path = window.location.pathname || "";
    var body = document.body;
    if (!body) {
      return path;
    }
    var root = body.getAttribute("data-app-root");
    if (root) {
      root = root.replace(/\/+$/, "");
      if (root && path.indexOf(root) === 0) {
        path = path.slice(root.length) || "/";
      }
    }
    return path;
  }

  function currentNavPath() {
    var fromLocation = servletPathFromLocation();
    if (fromLocation && fromLocation !== "/") {
      return fromLocation;
    }
    var meta = document.querySelector('meta[name="floor21-nav-path"]');
    if (meta) {
      var value = meta.getAttribute("content");
      if (value) {
        return value;
      }
    }
    return fromLocation || "";
  }

  function linkIsActive(path, link) {
    var prefix = link.getAttribute("data-nav-prefix");
    if (!prefix) {
      return false;
    }
    var exclude = link.getAttribute("data-nav-exclude");
    if (exclude) {
      var excluded = exclude.split(",");
      for (var i = 0; i < excluded.length; i++) {
        var part = excluded[i].trim();
        if (part && path.startsWith(part)) {
          return false;
        }
      }
    }
    var excludeContains = link.getAttribute("data-nav-exclude-contains");
    if (excludeContains && path.indexOf(excludeContains) !== -1) {
      return false;
    }
    if (link.getAttribute("data-nav-exact") === "true") {
      return path === prefix;
    }
    return path.startsWith(prefix);
  }

  function syncSectionHeaders(sidebar) {
    sidebar.querySelectorAll(".floor21-nav__section").forEach(function (section) {
      section.classList.remove("is-active");
    });
    var activeLink = sidebar.querySelector(".floor21-nav__link.is-active");
    if (!activeLink) {
      return;
    }
    var node = activeLink.previousElementSibling;
    while (node) {
      if (node.classList && node.classList.contains("floor21-nav__section")) {
        node.classList.add("is-active");
        return;
      }
      node = node.previousElementSibling;
    }
  }

  function syncSidebarActive() {
    var sidebar = document.getElementById("floor21-sidebar");
    if (!sidebar) {
      return;
    }
    var path = currentNavPath();
    sidebar.querySelectorAll(".floor21-nav__link[data-nav-prefix]").forEach(function (link) {
      link.classList.toggle("is-active", linkIsActive(path, link));
    });
    syncSectionHeaders(sidebar);
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

  onPageReady(syncSidebarActive);
})();
