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
    if (exclude && path.startsWith(exclude)) {
      return false;
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

  function syncSidebarActive() {
    var sidebar = document.getElementById("floor21-sidebar");
    if (!sidebar) {
      return;
    }
    var path = currentNavPath();
    sidebar.querySelectorAll(".floor21-nav__link[data-nav-prefix]").forEach(function (link) {
      link.classList.toggle("is-active", linkIsActive(path, link));
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

  onPageReady(syncSidebarActive);
})();
