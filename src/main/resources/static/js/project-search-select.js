/**
 * Searchable combobox for long project lists (admin building layout form).
 * Filters incrementally on each keystroke.
 */
(function () {
  var MAX_VISIBLE = 50;

  function normalize(text) {
    return (text || "").toLowerCase();
  }

  function enhanceSelect(select) {
    if (!select || select.dataset.projectSearchEnhanced === "true") {
      return;
    }
    select.dataset.projectSearchEnhanced = "true";

    var navigateOnSelect = select.dataset.navigateOnSelect === "true";
    var submitOnSelect = select.dataset.submitOnSelect === "true";
    var options = Array.prototype.slice.call(select.options);

    var wrap = document.createElement("div");
    wrap.className = "project-search-select";
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

    wrap.insertBefore(search, select);
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

      if (filtered.length > MAX_VISIBLE) {
        hint.textContent =
          "Showing " +
          MAX_VISIBLE +
          " of " +
          filtered.length +
          " matches — keep typing to narrow results.";
        hint.hidden = false;
      } else if (query) {
        hint.textContent =
          filtered.length === 1
            ? "1 match"
            : filtered.length + " matches";
        hint.hidden = false;
      } else {
        hint.hidden = true;
      }

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

    function beginSearch() {
      searching = true;
      search.value = "";
      search.placeholder = committedLabel
        ? "Type to search — selected: " + committedLabel
        : "Search projects…";
      renderMenu();
    }

    function endSearch() {
      searching = false;
      search.placeholder = "Search projects…";
      syncInputFromSelect();
      closeMenu();
    }

    search.addEventListener("focus", beginSearch);

    search.addEventListener("blur", function () {
      setTimeout(function () {
        if (picking) {
          return;
        }
        endSearch();
      }, 120);
    });

    search.addEventListener("input", function () {
      if (!searching) {
        searching = true;
      }
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

    document.addEventListener("click", function (e) {
      if (!wrap.contains(e.target)) {
        endSearch();
      }
    });

    if (navigateOnSelect) {
      select.addEventListener("change", function () {
        var id = select.value;
        if (!id) {
          return;
        }
        var root = (document.body.getAttribute("data-app-root") || "").replace(/\/+$/, "");
        window.location.href =
          root + "/admin/buildings/new?builderId=" + encodeURIComponent(id);
      });
    }

    if (submitOnSelect) {
      select.addEventListener("change", function () {
        var form = select.form;
        if (!form) {
          return;
        }
        if (typeof form.requestSubmit === "function") {
          form.requestSubmit();
        } else {
          form.submit();
        }
      });
    }

    syncInputFromSelect();
  }

  function initProjectSearchSelects() {
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
