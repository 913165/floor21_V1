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

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initProfileDropdown);
  } else {
    initProfileDropdown();
  }
})();
