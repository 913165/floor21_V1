/**
 * Admin list pages — live search (filters on each keystroke) and turbo-friendly submits.
 */
(function () {
  var DEBOUNCE_MS = 280;

  function submitForm(form) {
    if (typeof form.requestSubmit === "function") {
      form.requestSubmit();
    } else {
      form.submit();
    }
  }

  function wireLiveSearchForm(form, searchInputSelector) {
    if (!form || form.dataset.liveSearchEnhanced === "true") {
      return;
    }
    form.dataset.liveSearchEnhanced = "true";
    form.setAttribute("data-turbo-frame", "floor21-main");
    form.setAttribute("data-turbo-action", "advance");

    if (!searchInputSelector) {
      return;
    }
    var searchInput = form.querySelector(searchInputSelector);
    if (!searchInput) {
      return;
    }
    var timer;
    searchInput.addEventListener("input", function () {
      clearTimeout(timer);
      timer = setTimeout(function () {
        submitForm(form);
      }, DEBOUNCE_MS);
    });
  }

  function initAdminListFilters() {
    wireLiveSearchForm(document.getElementById("buildings-filter-form"), "#building-search-q");
    wireLiveSearchForm(document.getElementById("buildings-page-size-form"), null);
    wireLiveSearchForm(document.getElementById("users-filter-form"), "#user-search-q");
    wireLiveSearchForm(document.getElementById("users-page-size-form"), null);
  }

  function onPageReady(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn);
    } else {
      fn();
    }
    document.addEventListener("turbo:load", fn);
    document.addEventListener("turbo:frame-render", function (event) {
      if (!event.target || event.target.id === "floor21-main") {
        fn();
      }
    });
  }

  onPageReady(initAdminListFilters);
})();
