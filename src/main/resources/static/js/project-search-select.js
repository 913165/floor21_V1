/**
 * Searchable combobox for long project lists (admin building layout form).
 * Filters incrementally on each keystroke.
 */
(function () {
  var MAX_VISIBLE = 50;
  var outsideClickBound = false;
  var activeSelects = [];

  function normalize(text) {
    return (text || "").toLowerCase();
  }

  function bindOutsideClickOnce() {
    if (outsideClickBound) {
      return;
    }
    outsideClickBound = true;
    document.addEventListener("click", function (e) {
      if (e.target.closest(".project-search-select")) {
        return;
      }
      activeSelects.forEach(function (entry) {
        entry.endSearch();
      });
    });
  }

  function enhanceSelect(select) {
    if (!select || select.dataset.projectSearchEnhanced === "true") {
      return;
    }
    select.dataset.projectSearchEnhanced = "true";

    var submitOnSelect = select.dataset.submitOnSelect === "true";
    var options = Array.prototype.slice.call(select.options);

    var wrap = document.createElement("div");
    wrap.className = "project-search-select";
    wrap.style.position = "relative";
    select.parentNode.insertBefore(wrap, select);
    wrap.appendChild(select);

    select.classList.add("project-search-select__native");
    select.tabIndex = -1;
    select.setAttribute("aria-hidden", "true");

    var search = document.createElement("input");
    search.type = "text";
    search.className = "form-control project-search-select__input";
    search.autocomplete = "off";
    search.spellcheck = false;
    search.placeholder = "Search projects…";
    search.setAttribute("role", "combobox");
    search.setAttribute("aria-expanded", "false");
    search.setAttribute("aria-controls", select.id + "-menu");
    search.setAttribute("aria-autocomplete", "list");
    if (select.id) {
      search.id = select.id + "-search";
      var label = document.querySelector('label[for="' + select.id + '"]');
      if (label) {
        label.setAttribute("for", search.id);
      }
    }

    var menu = document.createElement("ul");
    menu.className = "project-search-select__menu list-unstyled mb-0";
    menu.id = select.id ? select.id + "-menu" : "";
    menu.setAttribute("role", "listbox");
    menu.hidden = true;

    var hint = document.createElement("div");
    hint.className = "project-search-select__hint form-text";
    hint.hidden = true;

    var toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "project-search-select__toggle";
    toggle.setAttribute("aria-label", "Show project list");
    toggle.innerHTML = "&#9662;";
    toggle.style.position = "absolute";
    toggle.style.top = "0.45rem";
    toggle.style.right = "0.5rem";
    toggle.style.zIndex = "2";

    wrap.insertBefore(search, select);
    wrap.insertBefore(toggle, select);
    wrap.insertBefore(menu, select);
    wrap.appendChild(hint);

    var activeIndex = -1;
    var filtered = options.slice();
    var committedLabel = "";
    var searching = false;
    var picking = false;

    function selectedOption() {
      return select.options[select.selectedIndex];
    }

    function syncInputFromSelect() {
      var opt = selectedOption();
      committedLabel = opt && opt.value ? opt.textContent : "";
      search.value = committedLabel;
      search.setAttribute("aria-activedescendant", "");
    }

    function closeMenu() {
      menu.hidden = true;
      search.setAttribute("aria-expanded", "false");
      activeIndex = -1;
      Array.prototype.forEach.call(menu.querySelectorAll(".is-active"), function (el) {
        el.classList.remove("is-active");
      });
    }

    function openMenu() {
      menu.hidden = false;
      search.setAttribute("aria-expanded", "true");
    }

    function currentQuery() {
      return searching ? normalize(search.value) : "";
    }

    function renderMenu() {
      var query = currentQuery();
      filtered = options.filter(function (opt) {
        return !query || normalize(opt.textContent).indexOf(query) !== -1;
      });

      menu.innerHTML = "";
      activeIndex = -1;

      if (filtered.length === 0) {
        var empty = document.createElement("li");
        empty.className = "project-search-select__empty";
        empty.textContent = query ? "No matching projects" : "No projects available";
        menu.appendChild(empty);
        hint.hidden = true;
        openMenu();
        return;
      }

      var visible = filtered.slice(0, MAX_VISIBLE);
      visible.forEach(function (opt) {
        var item = document.createElement("li");
        item.className = "project-search-select__option";
        item.setAttribute("role", "option");
        item.dataset.value = opt.value;
        item.textContent = opt.textContent;
        if (opt.value === select.value) {
          item.setAttribute("aria-selected", "true");
        }
        item.addEventListener("mousedown", function (e) {
          e.preventDefault();
          picking = true;
        });
        item.addEventListener("click", function () {
          chooseOption(opt);
          picking = false;
        });
        menu.appendChild(item);
      });

      hint.hidden = true;
      hint.textContent = "";

      openMenu();
    }

    function setActiveIndex(next) {
      var items = menu.querySelectorAll(".project-search-select__option");
      if (!items.length) {
        activeIndex = -1;
        return;
      }
      if (next < 0) {
        next = items.length - 1;
      }
      if (next >= items.length) {
        next = 0;
      }
      activeIndex = next;
      Array.prototype.forEach.call(items, function (el, i) {
        el.classList.toggle("is-active", i === activeIndex);
      });
      var active = items[activeIndex];
      if (active) {
        active.scrollIntoView({ block: "nearest" });
      }
    }

    function clearDependentFilters(form) {
      if (!form) {
        return;
      }
      ["buildingId", "bookingId"].forEach(function (name) {
        var el = form.querySelector('[name="' + name + '"]');
        if (el) {
          el.value = "";
        }
      });
    }

    function snapshotDependents(form) {
      if (!form || form.dataset.dependentSnapshotsSaved === "true") {
        return;
      }
      form.dataset.dependentSnapshotsSaved = "true";
      ["buildingId", "bookingId"].forEach(function (name) {
        var el = form.querySelector('[name="' + name + '"]');
        if (el && el.tagName === "SELECT") {
          form.dataset["dependentSnapshot" + name] = el.innerHTML;
        }
      });
    }

    function isSearchDiverged() {
      return searching && normalize(search.value) !== normalize(committedLabel);
    }

    function syncDependentDisabled(form) {
      if (!form) {
        return;
      }
      var building = form.querySelector('[name="buildingId"]');
      var booking = form.querySelector('[name="bookingId"]');
      var hasProject = !!select.value;
      var allowBookingWithoutBuilding =
          form.getAttribute("data-booking-without-building") === "true";
      if (building) {
        building.disabled = !hasProject;
      }
      if (booking) {
        booking.disabled =
            !hasProject || (!allowBookingWithoutBuilding && !(building && building.value));
      }
    }

    function resetDependentsForPendingProject(form) {
      if (!form) {
        return;
      }
      snapshotDependents(form);
      clearDependentFilters(form);
      ["buildingId", "bookingId"].forEach(function (name) {
        var el = form.querySelector('[name="' + name + '"]');
        if (!el || el.tagName !== "SELECT") {
          return;
        }
        var placeholder = el.options[0];
        el.innerHTML = "";
        if (placeholder) {
          el.appendChild(placeholder);
        }
        el.value = "";
        el.disabled = true;
      });
    }

    function restoreDependents(form) {
      if (!form) {
        return;
      }
      ["buildingId", "bookingId"].forEach(function (name) {
        var el = form.querySelector('[name="' + name + '"]');
        var html = form.dataset["dependentSnapshot" + name];
        if (!el || !html) {
          return;
        }
        el.innerHTML = html;
      });
      syncDependentDisabled(form);
    }

    function handlePendingProjectSearch(form) {
      if (!form) {
        return;
      }
      if (isSearchDiverged()) {
        resetDependentsForPendingProject(form);
      } else {
        restoreDependents(form);
      }
    }

    function commitIfSingleMatch() {
      var query = normalize(search.value);
      if (!query) {
        return false;
      }
      var exact = options.filter(function (opt) {
        return opt.value && normalize(opt.textContent) === query;
      });
      if (exact.length === 1) {
        chooseOption(exact[0]);
        return true;
      }
      var matches = options.filter(function (opt) {
        return opt.value && normalize(opt.textContent).indexOf(query) !== -1;
      });
      if (matches.length === 1) {
        chooseOption(matches[0]);
        return true;
      }
      return false;
    }

    function chooseOption(opt) {
      if (!opt) {
        return;
      }
      var prev = select.value;
      select.value = opt.value;
      committedLabel = opt.value ? opt.textContent : "";
      searching = false;
      search.value = committedLabel;
      closeMenu();
      if (prev !== opt.value) {
        select.dispatchEvent(new Event("change", { bubbles: true }));
      }
    }

    function endSearch() {
      if (select.form && isSearchDiverged()) {
        restoreDependents(select.form);
      }
      searching = false;
      search.placeholder = "Search projects…";
      syncInputFromSelect();
      closeMenu();
    }

    function beginSearch() {
      searching = true;
      if (committedLabel && normalize(search.value) === normalize(committedLabel)) {
        search.placeholder = committedLabel;
        search.value = "";
      }
      handlePendingProjectSearch(select.form);
      renderMenu();
    }

    activeSelects.push({ wrap: wrap, endSearch: endSearch });
    bindOutsideClickOnce();

    search.addEventListener("focus", beginSearch);

    search.addEventListener("click", function () {
      if (!searching) {
        beginSearch();
        return;
      }
      if (menu.hidden) {
        renderMenu();
      }
    });

    search.addEventListener("blur", function () {
      setTimeout(function () {
        if (picking) {
          return;
        }
        commitIfSingleMatch();
        endSearch();
      }, 120);
    });

    search.addEventListener("input", function () {
      if (!searching) {
        searching = true;
      }
      handlePendingProjectSearch(select.form);
      renderMenu();
    });

    search.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        if (menu.hidden) {
          renderMenu();
        }
        setActiveIndex(activeIndex + 1);
        return;
      }
      if (e.key === "ArrowUp") {
        e.preventDefault();
        if (menu.hidden) {
          renderMenu();
        }
        setActiveIndex(activeIndex <= 0 ? 0 : activeIndex - 1);
        return;
      }
      if (e.key === "Enter") {
        if (!menu.hidden && activeIndex >= 0 && filtered[activeIndex]) {
          e.preventDefault();
          chooseOption(filtered[activeIndex]);
        }
        return;
      }
      if (e.key === "Escape") {
        e.preventDefault();
        endSearch();
      }
    });

    toggle.addEventListener("click", function () {
      if (menu.hidden) {
        search.focus();
      } else {
        endSearch();
      }
    });

    if (submitOnSelect) {
      select.addEventListener("change", function () {
        var form = select.form;
        if (!form) {
          return;
        }
        clearDependentFilters(form);
        if (typeof form.requestSubmit === "function") {
          form.requestSubmit();
        } else {
          form.submit();
        }
      });
    }

    if (select.form) {
      select.form.addEventListener(
        "submit",
        function () {
          commitIfSingleMatch();
        },
        true
      );
    }

    if (select.form) {
      snapshotDependents(select.form);
      syncDependentDisabled(select.form);
    }

    syncInputFromSelect();
  }

  function initProjectSearchSelects() {
    activeSelects = [];
    document.querySelectorAll("[data-project-search-select]").forEach(enhanceSelect);
  }

  function onPageReady(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn);
    } else {
      fn();
    }
    document.addEventListener("turbo:load", fn);
    document.addEventListener("turbo:frame-render", fn);
  }

  onPageReady(initProjectSearchSelects);
})();
