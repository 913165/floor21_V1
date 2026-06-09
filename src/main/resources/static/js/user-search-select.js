/**
 * Server-backed user search for Add owner/partner (scales to large user lists).
 */
(function () {
  var DEBOUNCE_MS = 280;

  function appRoot() {
    return (document.body.getAttribute("data-app-root") || "").replace(/\/+$/, "");
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function enhanceSelect(select) {
    if (!select || select.dataset.userSearchEnhanced === "true") {
      return;
    }
    var searchUrl = select.dataset.searchUrl;
    if (!searchUrl) {
      return;
    }
    select.dataset.userSearchEnhanced = "true";

    var wrap = document.createElement("div");
    wrap.className = "project-search-select user-search-select";
    select.parentNode.insertBefore(wrap, select);
    wrap.appendChild(select);

    select.classList.add("project-search-select__native");
    select.tabIndex = -1;
    select.setAttribute("aria-hidden", "true");
    select.innerHTML = '<option value="">—</option>';

    var search = document.createElement("input");
    search.type = "text";
    search.className = "form-control project-search-select__input";
    search.autocomplete = "off";
    search.spellcheck = false;
    search.placeholder = "Search by name or email…";
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
    var results = [];
    var timer;
    var requestId = 0;
    var picking = false;
    var selectedLabel = "";

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

    function renderResults(items, query) {
      results = items || [];
      menu.innerHTML = "";
      activeIndex = -1;

      if (!results.length) {
        var empty = document.createElement("li");
        empty.className = "project-search-select__empty";
        empty.textContent = query ? "No matching users" : "No users available to add";
        menu.appendChild(empty);
        hint.hidden = true;
        openMenu();
        return;
      }

      results.forEach(function (user) {
        var item = document.createElement("li");
        item.className = "project-search-select__option";
        item.setAttribute("role", "option");
        item.dataset.value = user.id;
        item.textContent = user.label || user.fullName + " (" + user.email + ")";
        item.addEventListener("mousedown", function (e) {
          e.preventDefault();
          picking = true;
        });
        item.addEventListener("click", function () {
          chooseUser(user);
          picking = false;
        });
        menu.appendChild(item);
      });

      if (query) {
        hint.textContent =
          results.length === 1 ? "1 match — keep typing to narrow" : results.length + " matches";
        hint.hidden = false;
      } else {
        hint.textContent = "Showing first " + results.length + " users — type to search";
        hint.hidden = false;
      }
      openMenu();
    }

    function chooseUser(user) {
      if (!user) {
        return;
      }
      select.innerHTML = "";
      var opt = document.createElement("option");
      opt.value = user.id;
      opt.textContent = user.label || user.fullName + " (" + user.email + ")";
      opt.selected = true;
      select.appendChild(opt);
      selectedLabel = opt.textContent;
      search.value = selectedLabel;
      closeMenu();
      select.dispatchEvent(new Event("change", { bubbles: true }));
    }

    function fetchUsers(query) {
      var current = ++requestId;
      menu.innerHTML =
        '<li class="project-search-select__empty">Searching…</li>';
      openMenu();
      var url =
        searchUrl +
        (searchUrl.indexOf("?") >= 0 ? "&" : "?") +
        "q=" +
        encodeURIComponent(query || "") +
        "&limit=50";
      fetch(appRoot() + url, { headers: { Accept: "application/json" } })
        .then(function (res) {
          if (!res.ok) {
            throw new Error("search failed");
          }
          return res.json();
        })
        .then(function (data) {
          if (current !== requestId) {
            return;
          }
          renderResults(data, query);
        })
        .catch(function () {
          if (current !== requestId) {
            return;
          }
          menu.innerHTML =
            '<li class="project-search-select__empty">Could not load users</li>';
          hint.hidden = true;
          openMenu();
        });
    }

    function scheduleFetch(query) {
      clearTimeout(timer);
      timer = setTimeout(function () {
        fetchUsers(query);
      }, DEBOUNCE_MS);
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

    search.addEventListener("focus", function () {
      if (!search.value && !selectedLabel) {
        scheduleFetch("");
      } else if (menu.hidden) {
        scheduleFetch(search.value.trim());
      }
    });

    search.addEventListener("input", function () {
      if (search.value !== selectedLabel) {
        select.innerHTML = '<option value="">—</option>';
        selectedLabel = "";
      }
      scheduleFetch(search.value.trim());
    });

    search.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        if (menu.hidden) {
          scheduleFetch(search.value.trim());
        }
        setActiveIndex(activeIndex + 1);
        return;
      }
      if (e.key === "ArrowUp") {
        e.preventDefault();
        if (menu.hidden) {
          scheduleFetch(search.value.trim());
        }
        setActiveIndex(activeIndex <= 0 ? 0 : activeIndex - 1);
        return;
      }
      if (e.key === "Enter") {
        if (!menu.hidden && activeIndex >= 0 && results[activeIndex]) {
          e.preventDefault();
          chooseUser(results[activeIndex]);
        }
        return;
      }
      if (e.key === "Escape") {
        e.preventDefault();
        search.value = selectedLabel;
        closeMenu();
      }
    });

    search.addEventListener("blur", function () {
      setTimeout(function () {
        if (picking) {
          return;
        }
        if (!select.value) {
          search.value = "";
          selectedLabel = "";
        } else {
          search.value = selectedLabel;
        }
        closeMenu();
      }, 120);
    });

    document.addEventListener("click", function (e) {
      if (!wrap.contains(e.target)) {
        if (!select.value) {
          search.value = "";
          selectedLabel = "";
        } else {
          search.value = selectedLabel;
        }
        closeMenu();
      }
    });

    var form = select.form;
    if (form) {
      form.addEventListener(
        "submit",
        function (e) {
          if (!select.value) {
            e.preventDefault();
            search.focus();
            hint.textContent = "Choose a user before submitting.";
            hint.hidden = false;
          }
        },
        true
      );
    }
  }

  function initUserSearchSelects() {
    document.querySelectorAll("[data-user-search-select]").forEach(enhanceSelect);
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

  onPageReady(initUserSearchSelects);
})();
