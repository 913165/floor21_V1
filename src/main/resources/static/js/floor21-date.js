/**
 * Display dates as dd-Mon-yyyy (e.g. 28-Jun-2026) while keeping native date inputs on ISO for submit.
 */
(function () {
  'use strict';

  var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  function pad2(n) {
    return n < 10 ? '0' + n : String(n);
  }

  function isoToDisplay(iso) {
    if (!iso || iso.length < 10) {
      return '';
    }
    var parts = iso.split('-');
    if (parts.length !== 3) {
      return '';
    }
    var y = parseInt(parts[0], 10);
    var m = parseInt(parts[1], 10);
    var d = parseInt(parts[2], 10);
    if (!y || !m || !d || m < 1 || m > 12) {
      return '';
    }
    return pad2(d) + '-' + MONTHS[m - 1] + '-' + y;
  }

  function displayToIso(text) {
    if (!text) {
      return '';
    }
    var trimmed = text.trim();
    if (!trimmed) {
      return '';
    }
    if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) {
      return trimmed;
    }
    var match = /^(\d{1,2})-([A-Za-z]{3})-(\d{4})$/.exec(trimmed);
    if (!match) {
      return '';
    }
    var day = parseInt(match[1], 10);
    var year = parseInt(match[3], 10);
    var monthIndex = -1;
    var mon = match[2].toLowerCase();
    for (var i = 0; i < MONTHS.length; i++) {
      if (MONTHS[i].toLowerCase() === mon) {
        monthIndex = i;
        break;
      }
    }
    if (monthIndex < 0 || !day || !year) {
      return '';
    }
    return year + '-' + pad2(monthIndex + 1) + '-' + pad2(day);
  }

  function hideNative(input) {
    input.classList.add('f21-date-input__native');
    input.tabIndex = -1;
    input.setAttribute('aria-hidden', 'true');
  }

  function enhanceDateInput(input) {
    if (!input || input.dataset.f21DateEnhanced === 'true' || input.dataset.f21DateNative === 'true') {
      return;
    }
    input.dataset.f21DateEnhanced = 'true';

    var wrap = document.createElement('div');
    wrap.className = 'f21-date-field';
    input.parentNode.insertBefore(wrap, input);
    wrap.appendChild(input);
    hideNative(input);

    var display = document.createElement('input');
    display.type = 'text';
    display.className = (input.className || 'form-control')
      .replace(/\bf21-date-input__native\b/g, '')
      .trim();
    if (!display.className) {
      display.className = 'form-control';
    }
    display.classList.add('f21-date-input__display');
    display.placeholder = 'dd-Mon-yyyy';
    display.autocomplete = 'off';
    display.spellcheck = false;
    display.inputMode = 'numeric';
    if (input.id) {
      display.id = input.id + '-display';
      var label = document.querySelector('label[for="' + input.id + '"]');
      if (label) {
        label.setAttribute('for', display.id);
      }
    }
    if (input.required) {
      display.required = true;
    }
    if (input.readOnly || input.disabled) {
      display.readOnly = true;
    }
    display.value = isoToDisplay(input.value);

    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'f21-date-input__btn';
    btn.setAttribute('aria-label', 'Open calendar');
    btn.innerHTML =
      '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>';

    wrap.insertBefore(display, input);
    wrap.appendChild(btn);

    function syncToNative() {
      var iso = displayToIso(display.value);
      if (iso) {
        input.value = iso;
        display.value = isoToDisplay(iso);
        display.setCustomValidity('');
      } else if (!display.value.trim()) {
        input.value = '';
        display.setCustomValidity('');
      } else {
        display.setCustomValidity('Use dd-Mon-yyyy (e.g. 28-Jun-2026)');
      }
    }

    function syncFromNative() {
      display.value = isoToDisplay(input.value);
      display.setCustomValidity('');
    }

    display.addEventListener('blur', syncToNative);
    display.addEventListener('change', syncToNative);

    btn.addEventListener('click', function () {
      if (input.readOnly || input.disabled || display.readOnly) {
        return;
      }
      syncToNative();
      if (typeof input.showPicker === 'function') {
        input.showPicker();
      } else {
        input.focus();
      }
    });

    input.addEventListener('change', syncFromNative);
    input.addEventListener('input', syncFromNative);

    var form = input.form;
    if (form) {
      form.addEventListener(
        'submit',
        function () {
          syncToNative();
        },
        true,
      );
    }
  }

  function initFloor21Dates(root) {
    var scope = root || document;
    scope.querySelectorAll('input[type="date"]').forEach(enhanceDateInput);
  }

  function onPageReady(fn) {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn);
    } else {
      fn();
    }
    document.addEventListener('turbo:load', fn);
    document.addEventListener('turbo:frame-render', function (event) {
      if (!event.target || event.target.id === 'floor21-main') {
        fn();
      }
    });
  }

  onPageReady(function () {
    initFloor21Dates(document);
  });

  window.Floor21Date = {
    isoToDisplay: isoToDisplay,
    displayToIso: displayToIso,
    init: initFloor21Dates,
  };
})();
