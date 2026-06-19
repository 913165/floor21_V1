/**
 * Floor21 date fields: dd-Mon-yyyy display + custom calendar (horizontal month/year nav).
 */
(function () {
  'use strict';

  var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  var MONTHS_LONG = [
    'January',
    'February',
    'March',
    'April',
    'May',
    'June',
    'July',
    'August',
    'September',
    'October',
    'November',
    'December',
  ];
  var WEEKDAYS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

  var activePicker = null;
  var activeAnchor = null;
  var activeContext = null;

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

  function parseIso(iso) {
    if (!iso || iso.length < 10) {
      return null;
    }
    var parts = iso.split('-');
    if (parts.length !== 3) {
      return null;
    }
    var y = parseInt(parts[0], 10);
    var m = parseInt(parts[1], 10) - 1;
    var d = parseInt(parts[2], 10);
    if (!y || m < 0 || m > 11 || !d) {
      return null;
    }
    return new Date(y, m, d);
  }

  function toIsoDate(date) {
    return date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate());
  }

  function sameDay(a, b) {
    return (
      a &&
      b &&
      a.getFullYear() === b.getFullYear() &&
      a.getMonth() === b.getMonth() &&
      a.getDate() === b.getDate()
    );
  }

  function hideNative(input) {
    input.classList.add('f21-date-input__native');
    input.tabIndex = -1;
    input.setAttribute('aria-hidden', 'true');
  }

  function ensurePickerShell() {
    var shell = document.getElementById('f21-date-picker');
    if (shell) {
      return shell;
    }
    shell = document.createElement('div');
    shell.id = 'f21-date-picker';
    shell.className = 'f21-date-picker';
    shell.hidden = true;
    shell.setAttribute('role', 'dialog');
    shell.setAttribute('aria-modal', 'true');
    shell.setAttribute('aria-label', 'Choose date');
    shell.innerHTML =
      '<div class="f21-date-picker__panel">' +
      '  <div class="f21-date-picker__head">' +
      '    <div class="f21-date-picker__nav-group">' +
      '      <button type="button" class="f21-date-picker__nav" data-step="year-prev" aria-label="Previous year">&laquo;</button>' +
      '      <button type="button" class="f21-date-picker__nav" data-step="month-prev" aria-label="Previous month">&lsaquo;</button>' +
      '    </div>' +
      '    <div class="f21-date-picker__title" aria-live="polite"></div>' +
      '    <div class="f21-date-picker__nav-group">' +
      '      <button type="button" class="f21-date-picker__nav" data-step="month-next" aria-label="Next month">&rsaquo;</button>' +
      '      <button type="button" class="f21-date-picker__nav" data-step="year-next" aria-label="Next year">&raquo;</button>' +
      '    </div>' +
      '  </div>' +
      '  <div class="f21-date-picker__weekdays"></div>' +
      '  <div class="f21-date-picker__grid" role="grid"></div>' +
      '  <div class="f21-date-picker__foot">' +
      '    <button type="button" class="f21-date-picker__foot-btn" data-action="today">Today</button>' +
      '    <button type="button" class="f21-date-picker__foot-btn" data-action="clear">Clear</button>' +
      '  </div>' +
      '</div>';
    document.body.appendChild(shell);

    var weekdays = shell.querySelector('.f21-date-picker__weekdays');
    WEEKDAYS.forEach(function (label) {
      var cell = document.createElement('span');
      cell.className = 'f21-date-picker__weekday';
      if (label === 'Su' || label === 'Sa') {
        cell.classList.add('f21-date-picker__weekday--weekend');
      }
      cell.textContent = label;
      weekdays.appendChild(cell);
    });

    shell.addEventListener('click', onPickerClick);
    return shell;
  }

  function isDisabledDate(date, input) {
    if (!date || !input) {
      return false;
    }
    var iso = toIsoDate(date);
    if (input.min && iso < input.min) {
      return true;
    }
    if (input.max && iso > input.max) {
      return true;
    }
    return false;
  }

  function renderPickerMonth(shell, ctx) {
    var title = shell.querySelector('.f21-date-picker__title');
    var grid = shell.querySelector('.f21-date-picker__grid');
    title.textContent = MONTHS_LONG[ctx.viewMonth] + ' ' + ctx.viewYear;

    grid.innerHTML = '';
    var first = new Date(ctx.viewYear, ctx.viewMonth, 1);
    var startOffset = first.getDay();
    var cursor = new Date(ctx.viewYear, ctx.viewMonth, 1 - startOffset);
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var selected = parseIso(ctx.input.value);

    for (var i = 0; i < 42; i++) {
      var cellDate = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate());
      var iso = toIsoDate(cellDate);
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'f21-date-picker__day';
      btn.textContent = String(cellDate.getDate());
      btn.setAttribute('data-iso', iso);
      btn.setAttribute('role', 'gridcell');
      btn.setAttribute('aria-label', isoToDisplay(iso));

      if (cellDate.getMonth() !== ctx.viewMonth) {
        btn.classList.add('f21-date-picker__day--muted');
      }
      var dayOfWeek = cellDate.getDay();
      if (dayOfWeek === 0 || dayOfWeek === 6) {
        btn.classList.add('f21-date-picker__day--weekend');
      }
      if (sameDay(cellDate, today)) {
        btn.classList.add('f21-date-picker__day--today');
      }
      if (sameDay(cellDate, selected)) {
        btn.classList.add('f21-date-picker__day--selected');
        btn.setAttribute('aria-selected', 'true');
      }
      if (isDisabledDate(cellDate, ctx.input)) {
        btn.disabled = true;
        btn.classList.add('f21-date-picker__day--disabled');
      }
      grid.appendChild(btn);
      cursor.setDate(cursor.getDate() + 1);
    }
  }

  function positionPicker(shell, anchor) {
    var rect = anchor.getBoundingClientRect();
    var panel = shell.querySelector('.f21-date-picker__panel');
    var margin = 6;
    shell.style.left = '0';
    shell.style.top = '0';
    shell.hidden = false;
    var panelRect = panel.getBoundingClientRect();
    var left = rect.left;
    var top = rect.bottom + margin;
    if (left + panelRect.width > window.innerWidth - 8) {
      left = Math.max(8, window.innerWidth - panelRect.width - 8);
    }
    if (top + panelRect.height > window.innerHeight - 8) {
      top = rect.top - panelRect.height - margin;
    }
    shell.style.left = Math.round(left) + 'px';
    shell.style.top = Math.round(top) + 'px';
  }

  function closePicker() {
    if (!activePicker) {
      return;
    }
    activePicker.hidden = true;
    activePicker = null;
    activeAnchor = null;
    activeContext = null;
  }

  function applyDate(ctx, iso) {
    if (iso) {
      ctx.input.value = iso;
      ctx.display.value = isoToDisplay(iso);
      ctx.display.setCustomValidity('');
    } else {
      ctx.input.value = '';
      ctx.display.value = '';
      ctx.display.setCustomValidity('');
    }
    ctx.input.dispatchEvent(new Event('change', { bubbles: true }));
    ctx.input.dispatchEvent(new Event('input', { bubbles: true }));
  }

  function openPicker(ctx) {
    if (ctx.input.readOnly || ctx.input.disabled || ctx.display.readOnly) {
      return;
    }
    ctx.syncToNative();

    var shell = ensurePickerShell();
    var selected = parseIso(ctx.input.value);
    var now = new Date();
    activeContext = ctx;
    activeAnchor = ctx.wrap;
    activePicker = shell;

    ctx.viewYear = selected ? selected.getFullYear() : now.getFullYear();
    ctx.viewMonth = selected ? selected.getMonth() : now.getMonth();

    renderPickerMonth(shell, ctx);
    positionPicker(shell, ctx.wrap);

    window.setTimeout(function () {
      var selectedBtn = shell.querySelector('.f21-date-picker__day--selected');
      if (selectedBtn) {
        selectedBtn.focus({ preventScroll: true });
      }
    }, 0);
  }

  function onPickerClick(event) {
    if (!activeContext || !activePicker) {
      return;
    }
    var target = event.target.closest('[data-step], [data-iso], [data-action]');
    if (!target || !activePicker.contains(target)) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();

    var step = target.getAttribute('data-step');
    if (step === 'month-prev') {
      activeContext.viewMonth -= 1;
      if (activeContext.viewMonth < 0) {
        activeContext.viewMonth = 11;
        activeContext.viewYear -= 1;
      }
      renderPickerMonth(activePicker, activeContext);
      return;
    }
    if (step === 'month-next') {
      activeContext.viewMonth += 1;
      if (activeContext.viewMonth > 11) {
        activeContext.viewMonth = 0;
        activeContext.viewYear += 1;
      }
      renderPickerMonth(activePicker, activeContext);
      return;
    }
    if (step === 'year-prev') {
      activeContext.viewYear -= 1;
      renderPickerMonth(activePicker, activeContext);
      return;
    }
    if (step === 'year-next') {
      activeContext.viewYear += 1;
      renderPickerMonth(activePicker, activeContext);
      return;
    }

    var iso = target.getAttribute('data-iso');
    if (iso) {
      applyDate(activeContext, iso);
      closePicker();
      activeContext.display.focus();
      return;
    }

    var action = target.getAttribute('data-action');
    if (action === 'today') {
      applyDate(activeContext, toIsoDate(new Date()));
      closePicker();
      activeContext.display.focus();
      return;
    }
    if (action === 'clear') {
      applyDate(activeContext, '');
      closePicker();
      activeContext.display.focus();
      return;
    }
  }

  function onDocumentPointerDown(event) {
    if (!activePicker || activePicker.hidden) {
      return;
    }
    if (activePicker.contains(event.target)) {
      return;
    }
    if (activeAnchor && activeAnchor.contains(event.target)) {
      return;
    }
    closePicker();
  }

  function onDocumentKeyDown(event) {
    if (event.key === 'Escape') {
      closePicker();
    }
  }

  document.addEventListener('mousedown', onDocumentPointerDown);
  document.addEventListener('keydown', onDocumentKeyDown);
  document.addEventListener('turbo:before-cache', closePicker);
  window.addEventListener('resize', closePicker);
  window.addEventListener(
    'scroll',
    function () {
      if (activePicker && activeAnchor) {
        positionPicker(activePicker, activeAnchor);
      }
    },
    true,
  );

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

    var ctx = {
      wrap: wrap,
      input: input,
      display: display,
      viewYear: 0,
      viewMonth: 0,
      syncToNative: syncToNative,
    };

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

    ctx.syncToNative = syncToNative;

    display.addEventListener('blur', syncToNative);
    display.addEventListener('change', syncToNative);

    function open() {
      openPicker(ctx);
    }

    btn.addEventListener('click', open);
    display.addEventListener('focus', function () {
      wrap.classList.add('f21-date-field--focused');
    });
    display.addEventListener('blur', function () {
      wrap.classList.remove('f21-date-field--focused');
    });
    display.addEventListener('keydown', function (event) {
      if (event.key === 'ArrowDown' || event.key === 'Enter') {
        event.preventDefault();
        open();
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
    closePicker: closePicker,
  };
})();
