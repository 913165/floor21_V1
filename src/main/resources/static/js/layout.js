/**
 * Profile dropdown must render above main content (tables/cards stack on top otherwise).
 */
(function () {
  function menuPanel() {
    return document.querySelector('[aria-labelledby="profileMenu"]');
  }

  function raiseMenu() {
    var menu = menuPanel();
    if (!menu) {
      return;
    }
    menu.classList.add('floor21-profile-menu__panel');
    menu.style.zIndex = '2000';
    menu.style.position = 'fixed';
  }

  function initProfileDropdown() {
    var toggle = document.getElementById('profileMenu');
    if (!toggle || typeof bootstrap === 'undefined' || !bootstrap.Dropdown) {
      return;
    }

    var existing = bootstrap.Dropdown.getInstance(toggle);
    if (existing) {
      existing.dispose();
    }

    new bootstrap.Dropdown(toggle, {
      autoClose: true,
      boundary: 'viewport',
      container: document.body,
      popperConfig: {
        strategy: 'fixed',
        modifiers: [{ name: 'offset', options: { offset: [0, 8] } }],
      },
    });

    toggle.addEventListener('shown.bs.dropdown', raiseMenu);
  }

  function onPageReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
    document.addEventListener('turbo:load', fn);
  }

  /** Bootstrap backdrop is on body; modals inside turbo-frame cannot receive clicks. */
  function mountTurboFrameModalsOnBody() {
    var frame = document.getElementById("floor21-main");
    if (!frame) {
      return;
    }
    frame.querySelectorAll(".modal[id]").forEach(function (modal) {
      var id = modal.id;
      if (!id) {
        return;
      }
      document.querySelectorAll('[id="' + id + '"]').forEach(function (el) {
        if (el !== modal) {
          el.remove();
        }
      });
      if (modal.parentElement !== document.body) {
        document.body.appendChild(modal);
      }
    });
  }

  function onModalShow(event) {
    var modal = event.target;
    if (!modal || !modal.classList || !modal.classList.contains("modal")) {
      return;
    }
    if (modal.parentElement !== document.body) {
      document.body.appendChild(modal);
    }
  }

  onPageReady(function () {
    initProfileDropdown();
    mountTurboFrameModalsOnBody();
  });
  document.addEventListener("turbo:render", mountTurboFrameModalsOnBody);
  document.addEventListener("show.bs.modal", onModalShow, true);
})();
