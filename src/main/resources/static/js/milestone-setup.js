/**
 * Milestone setup (Clients) — building / client picker and editable slab grid.
 */
(function () {
  'use strict';

  function parseAmt(val) {
    if (window.Floor21Amount) {
      return Floor21Amount.parseAmount(val);
    }
    if (val == null || String(val).trim() === '') {
      return null;
    }
    const n = parseFloat(String(val).replace(/,/g, ''));
    return Number.isFinite(n) ? n : null;
  }

  function formatAmt(n) {
    if (window.Floor21Amount) {
      return Floor21Amount.formatAmount(n);
    }
    return n == null ? '' : String(n);
  }

  function formatPercent(n) {
    if (n == null || !Number.isFinite(n)) {
      return '';
    }
    return String(n).replace(/(\.\d*?)0+$/, '$1').replace(/\.$/, '');
  }

  function pageRoot() {
    return document.getElementById('milestoneSetupPage');
  }

  function baseAmount() {
    const root = pageRoot();
    if (!root) {
      return 0;
    }
    const raw = root.getAttribute('data-base-amount');
    const n = parseFloat(raw);
    return Number.isFinite(n) ? n : 0;
  }

  function sumInputs(selector) {
    let total = 0;
    document.querySelectorAll(selector).forEach(function (el) {
      const n = parseAmt(el.value);
      if (n != null) {
        total += n;
      }
    });
    return total;
  }

  function sumColumnFromHidden(suffix) {
    if (suffix === 'agreedAmount' || suffix === 'extraAmount') {
      return sumInputs('.js-ms-' + (suffix === 'agreedAmount' ? 'agreed' : 'extra'));
    }
    return sumInputs('input[name$=".' + suffix + '"]');
  }

  function recalcTotals() {
    const totalPercent = sumColumnFromHidden('percent');
    const totalAgreed = sumColumnFromHidden('agreedAmount');
    const totalExtra = sumColumnFromHidden('extraAmount');
    const base = baseAmount();
    const balance = base > 0 ? base - totalAgreed : null;

    const elPct = document.getElementById('msTotalPercent');
    const elAgreed = document.getElementById('msTotalAgreed');
    const elExtra = document.getElementById('msTotalExtra');
    const elAgreedSum = document.getElementById('msAgreedSum');
    const elBalance = document.getElementById('msBalance');

    if (elPct) {
      elPct.textContent = formatPercent(totalPercent);
    }
    if (elAgreed) {
      elAgreed.textContent = formatAmt(totalAgreed);
    }
    if (elExtra) {
      elExtra.textContent = formatAmt(totalExtra);
    }
    if (elAgreedSum) {
      elAgreedSum.textContent = formatAmt(totalAgreed);
    }
    if (elBalance) {
      elBalance.textContent = balance != null ? formatAmt(balance) : '—';
      elBalance.classList.toggle('text-danger', balance != null && Math.abs(balance) > 0.01);
      elBalance.classList.toggle('text-success', balance != null && Math.abs(balance) <= 0.01);
      elBalance.classList.toggle('fw-semibold', true);
    }
  }

  function initPickerForm() {
    const building = document.getElementById('msBuilding');
    const booking = document.getElementById('msBooking');
    const form = document.getElementById('milestonePickerForm');
    if (!building || !form) {
      return;
    }
    building.addEventListener('change', function () {
      if (booking) {
        booking.value = '';
      }
      form.submit();
    });
    if (booking) {
      booking.addEventListener('change', function () {
        if (booking.value) {
          form.submit();
        }
      });
    }
  }

  function initSlabForm() {
    const form = document.getElementById('scheduleSaveForm');
    if (!form) {
      return;
    }

    form.querySelectorAll('.milestone-slab-row').forEach(function (row) {
      row.querySelectorAll('.js-ms-agreed, .js-ms-extra').forEach(function (inp) {
        inp.addEventListener('input', recalcTotals);
      });
    });

    if (window.Floor21Amount) {
      form.querySelectorAll('.js-amount-input').forEach(function (el) {
        Floor21Amount.initDisplayInput(el);
      });
    }

    const bulkBtn = document.getElementById('msBulkApply');
    const bulkDate = document.getElementById('msBulkDate');
    if (bulkBtn && bulkDate) {
      bulkBtn.addEventListener('click', function () {
        const iso = bulkDate.value;
        if (!iso) {
          return;
        }
        document.querySelectorAll('.js-ms-due-date').forEach(function (dateInput) {
          dateInput.value = iso;
        });
      });
    }

    form.addEventListener('submit', function () {
      if (window.Floor21Amount) {
        form.querySelectorAll('.js-ms-agreed, .js-ms-extra').forEach(function (el) {
          Floor21Amount.syncDisplayToHidden(el);
        });
      }
    });

    recalcTotals();
  }

  function init() {
    initPickerForm();
    initSlabForm();
  }

  document.addEventListener('turbo:load', init);
  if (document.readyState !== 'loading') {
    init();
  } else {
    document.addEventListener('DOMContentLoaded', init);
  }
})();
