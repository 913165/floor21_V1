/**
 * Searchable multi-select with removable chips (booking co-owners).
 */
(function () {
  var MAX_VISIBLE = 50;
  var outsideClickBound = false;
  var activeWidgets = [];

  function normalize(text) {
    return (text || "").toLowerCase();
  }

  function bindOutsideClickOnce() {
    if (outsideClickBound) {
      return;
    }
    outsideClickBound = true;
    document.addEventListener("click", function (e) {
      if (e.target.closest(".client-multi-search-select")) {
        return;
      }
      activeWidgets.forEach(function (entry) {
        entry.closeMenu();
      });
    });
  }

  function enhanceSelect(select) {
    if (!select || select.dataset.clientMultiSearchEnhanced === "true") {
      return;
    }
    select.dataset.clientMultiSearchEnhanced = "true";

    var excludeSelector = select.dataset.excludeSelect || "";
    var placeholder = select.dataset.searchPlaceholder || "Search co-owners…";
    var options = Array.prototype.slice.call(select.options).filter(function (opt) {
      return opt.value;
    });

    var wrap = document.createElement("div");
    wrap.className = "client-multi-search-select";
    select.parentNode.insertBefore(wrap, select);
    wrap.appendChild(select);

    select.classList.add("client-multi-search-select__native");
    select.tabIndex = -1;
    select.setAttribute("aria-hidden", "true");
    select.removeAttribute("size");

    var box = document.createElement("div");
    box.className = "client-multi-search-select__box form-control";
    box.setAttribute("role", "combobox");
    box.setAttribute("aria-expanded", "false");

    var chips = document.createElement("div");
    chips.className = "client-multi-search-select__chips";

    var search = document.createElement("input");
    search.type = "text";
    search.className = "client-multi-search-select__input";
    search.autocomplete = "off";
    search.spellcheck = false;
    search.placeholder = placeholder;
    search.setAttribute("aria-autocomplete", "list");
    search.setAttribute("aria-controls", select.id ? select.id + "-menu" : "");
    if (select.id) {
      search.id = select.id + "-search";
      var label = document.querySelector('label[for="' + select.id + '"]');
      if (label) {
        label.setAttribute("for", search.id);
      }
    }

    var menu = document.createElement("ul");
    menu.className = "client-multi-search-select__menu list-unstyled mb-0";
    menu.id = select.id ? select.id + "-menu" : "";
    menu.setAttribute("role", "listbox");
    menu.hidden = true;

    box.appendChild(chips);
    box.appendChild(search);
    wrap.insertBefore(box, select);
    wrap.insertBefore(menu, select);

    var activeIndex = -1;
    var picking = false;

    function excludeId() {
      if (!excludeSelector) {
        return "";
      }
      var excludeEl = document.querySelector(excludeSelector);
      return excludeEl ? excludeEl.value : "";
    }

    function selectedIds() {
      return Array.prototype.slice
        .call(select.options)
        .filter(function (opt) {
          return opt.selected && opt.value;
        })
        .map(function (opt) {
          return opt.value;
        });
    }

    function optionById(id) {
      return options.find(function (opt) {
        return opt.value === id;
      });
    }

    function syncNativeSelect(ids) {
      Array.prototype.forEach.call(select.options, function (opt) {
        opt.selected = ids.indexOf(opt.value) !== -1;
      });
      select.dispatchEvent(new Event("change", { bubbles: true }));
    }

    function renderChips() {
      chips.innerHTML = "";
      selectedIds().forEach(function (id) {
        var opt = optionById(id);
        if (!opt) {
          return;
        }
        var chip = document.createElement("span");
        chip.className = "client-multi-search-select__chip";
        chip.setAttribute("role", "option");
        chip.setAttribute("aria-selected", "true");

        var label = document.createElement("span");
        label.className = "client-multi-search-select__chip-label";
        label.textContent = opt.textContent;

        var remove = document.createElement("button");
        remove.type = "button";
        remove.className = "client-multi-search-select__chip-remove";
        remove.setAttribute("aria-label", "Remove " + opt.textContent);
        remove.innerHTML = "&times;";
        remove.addEventListener("click", function (e) {
          e.preventDefault();
          e.stopPropagation();
          removeId(id);
        });

        chip.appendChild(label);
        chip.appendChild(remove);
        chips.appendChild(chip);
      });
    }

    function removeId(id) {
      syncNativeSelect(
        selectedIds().filter(function (value) {
          return value !== id;
        })
      );
      renderChips();
      renderMenu();
    }

    function addId(id) {
      if (!id || selectedIds().indexOf(id) !== -1 || id === excludeId()) {
        return;
      }
      syncNativeSelect(selectedIds().concat([id]));
      renderChips();
      search.value = "";
      renderMenu();
      search.focus();
    }

    function closeMenu() {
      menu.hidden = true;
      box.setAttribute("aria-expanded", "false");
      activeIndex = -1;
      Array.prototype.forEach.call(menu.querySelectorAll(".is-active"), function (el) {
        el.classList.remove("is-active");
      });
    }

    function openMenu() {
      menu.hidden = false;
      box.setAttribute("aria-expanded", "true");
    }

    function availableOptions() {
      var excluded = excludeId();
      var chosen = selectedIds();
      var query = normalize(search.value);
      return options.filter(function (opt) {
        if (!opt.value || opt.value === excluded || chosen.indexOf(opt.value) !== -1) {
          return false;
        }
        return !query || normalize(opt.textContent).indexOf(query) !== -1;
      });
    }

    function renderMenu() {
      var filtered = availableOptions();
      menu.innerHTML = "";
      activeIndex = -1;

      if (!filtered.length) {
        var empty = document.createElement("li");
        empty.className = "client-multi-search-select__empty";
        empty.textContent = search.value ? "No matching clients" : "No more clients to add";
        menu.appendChild(empty);
        openMenu();
        return;
      }

      filtered.slice(0, MAX_VISIBLE).forEach(function (opt) {
        var item = document.createElement("li");
        item.className = "client-multi-search-select__option";
        item.setAttribute("role", "option");
        item.dataset.value = opt.value;
        item.textContent = opt.textContent;
        item.addEventListener("mousedown", function (e) {
          e.preventDefault();
          picking = true;
        });
        item.addEventListener("click", function () {
          addId(opt.value);
          picking = false;
        });
        menu.appendChild(item);
      });

      openMenu();
    }

    function setActiveIndex(next) {
      var items = menu.querySelectorAll(".client-multi-search-select__option");
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

    function chooseActive() {
      var items = menu.querySelectorAll(".client-multi-search-select__option");
      if (activeIndex < 0 || !items[activeIndex]) {
        return;
      }
      addId(items[activeIndex].dataset.value);
    }

    box.addEventListener("click", function () {
      search.focus();
      renderMenu();
    });

    search.addEventListener("focus", function () {
      box.classList.add("is-focused");
      renderMenu();
    });

    search.addEventListener("blur", function () {
      if (picking) {
        return;
      }
      box.classList.remove("is-focused");
      window.setTimeout(function () {
        if (!picking) {
          closeMenu();
        }
      }, 120);
    });

    search.addEventListener("input", function () {
      renderMenu();
    });

    search.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        if (menu.hidden) {
          renderMenu();
        }
        setActiveIndex(activeIndex + 1);
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        setActiveIndex(activeIndex - 1);
      } else if (e.key === "Enter") {
        if (!menu.hidden) {
          e.preventDefault();
          chooseActive();
        }
      } else if (e.key === "Escape") {
        e.preventDefault();
        closeMenu();
      } else if (e.key === "Backspace" && !search.value) {
        var ids = selectedIds();
        if (ids.length) {
          removeId(ids[ids.length - 1]);
        }
      }
    });

    function handleExcludeChange() {
      var excluded = excludeId();
      if (!excluded) {
        renderChips();
        renderMenu();
        return;
      }
      if (selectedIds().indexOf(excluded) !== -1) {
        removeId(excluded);
        return;
      }
      renderMenu();
    }

    if (excludeSelector) {
      var excludeEl = document.querySelector(excludeSelector);
      if (excludeEl) {
        excludeEl.addEventListener("change", handleExcludeChange);
      }
    }

    renderChips();

    var widget = { closeMenu: closeMenu };
    activeWidgets.push(widget);
    bindOutsideClickOnce();
  }

  function init() {
    document.querySelectorAll("[data-client-multi-search-select]").forEach(enhanceSelect);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
