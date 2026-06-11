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

  function pad2(n) {
    return n < 10 ? '0' + n : String(n);
  }

  function syncDatePartsToHidden(partsEl) {
    const row = partsEl.closest('tr');
    if (!row) {
      return;
    }
    const hidden = row.querySelector('.js-due-date-hidden');
    if (!hidden) {
      return;
    }
    const dd = partsEl.querySelector('.milestone-date-dd');
    const mm = partsEl.querySelector('.milestone-date-mm');
    const yyyy = partsEl.querySelector('.milestone-date-yyyy');
    const d = dd && dd.value ? parseInt(dd.value, 10) : NaN;
    const m = mm && mm.value ? parseInt(mm.value, 10) : NaN;
    const y = yyyy && yyyy.value ? parseInt(yyyy.value, 10) : NaN;
    if (!Number.isFinite(d) || !Number.isFinite(m) || !Number.isFinite(y)) {
      hidden.value = '';
      return;
    }
    if (d < 1 || d > 31 || m < 1 || m > 12 || y < 1900 || y > 2100) {
      hidden.value = '';
      return;
    }
    hidden.value = y + '-' + pad2(m) + '-' + pad2(d);
  }

  function initDateParts(partsEl) {
    const dd = partsEl.querySelector('.milestone-date-dd');
    const mm = partsEl.querySelector('.milestone-date-mm');
    const yyyy = partsEl.querySelector('.milestone-date-yyyy');
    if (dd && dd.dataset.initial) {
      dd.value = dd.dataset.initial;
    }
    if (mm && mm.dataset.initial) {
      mm.value = mm.dataset.initial;
    }
    if (yyyy && yyyy.dataset.initial) {
      yyyy.value = yyyy.dataset.initial;
    }
    [dd, mm, yyyy].forEach(function (inp) {
      if (!inp) {
        return;
      }
      inp.addEventListener('change', function () {
        syncDatePartsToHidden(partsEl);
      });
      inp.addEventListener('blur', function () {
        syncDatePartsToHidden(partsEl);
      });
    });
    syncDatePartsToHidden(partsEl);
  }

  function rowPercent(row) {
    const hidden = row.querySelector('input[name$=".percent"]');
    if (!hidden) {
      return null;
    }
    return parseAmt(hidden.value);
  }

  function rowAgreedHidden(row) {
    return row.querySelector('input[name$=".agreedAmount"]');
  }

  function recalcAgreedFromPercent(row) {
    const pct = rowPercent(row);
    const agreedHidden = rowAgreedHidden(row);
    const agreedDisplay = row.querySelector('.js-ms-agreed-display');
    const base = baseAmount();
    if (pct == null || base <= 0 || !agreedHidden) {
      return;
    }
    const agreed = Math.round((base * pct) / 100 * 100) / 100;
    agreedHidden.value = String(agreed);
    if (agreedDisplay) {
      agreedDisplay.textContent = formatAmt(agreed);
    }
  }

  function sumColumnFromHidden(suffix) {
    let total = 0;
    document.querySelectorAll('input[name$=".' + suffix + '"]').forEach(function (el) {
      const n = parseAmt(el.value);
      if (n != null) {
        total += n;
      }
    });
    return total;
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

  function setRowEditing(row, editing) {
    row.classList.toggle('milestone-slab-row--editing', editing);
    row.querySelectorAll('.milestone-date-dd, .milestone-date-mm, .milestone-date-yyyy, .js-ms-extra').forEach(function (el) {
      el.readOnly = !editing;
    });
    const btn = row.querySelector('.js-ms-edit-row');
    if (btn) {
      btn.textContent = editing ? 'Done' : 'Edit';
      btn.classList.toggle('btn-primary', editing);
      btn.classList.toggle('btn-outline-primary', !editing);
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

    document.querySelectorAll('.milestone-date-parts').forEach(initDateParts);

    form.querySelectorAll('.milestone-slab-row').forEach(function (row) {
      recalcAgreedFromPercent(row);
      setRowEditing(row, false);

      const editBtn = row.querySelector('.js-ms-edit-row');
      if (editBtn) {
        editBtn.addEventListener('click', function () {
          const editing = !row.classList.contains('milestone-slab-row--editing');
          setRowEditing(row, editing);
          if (editing) {
            const first = row.querySelector('.milestone-date-dd');
            if (first) {
              first.focus();
            }
          }
        });
      }

      row.querySelectorAll('.js-ms-extra').forEach(function (inp) {
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
        const parts = iso.split('-');
        if (parts.length !== 3) {
          return;
        }
        document.querySelectorAll('.milestone-slab-row').forEach(function (row) {
          setRowEditing(row, true);
          const dd = row.querySelector('.milestone-date-dd');
          const mm = row.querySelector('.milestone-date-mm');
          const yyyy = row.querySelector('.milestone-date-yyyy');
          if (dd) {
            dd.value = parts[2];
          }
          if (mm) {
            mm.value = parts[1];
          }
          if (yyyy) {
            yyyy.value = parts[0];
          }
          const partsEl = row.querySelector('.milestone-date-parts');
          if (partsEl) {
            syncDatePartsToHidden(partsEl);
          }
        });
      });
    }

    form.addEventListener('submit', function () {
      document.querySelectorAll('.milestone-date-parts').forEach(syncDatePartsToHidden);
      form.querySelectorAll('.milestone-slab-row').forEach(recalcAgreedFromPercent);
      if (window.Floor21Amount) {
        form.querySelectorAll('.js-ms-extra').forEach(function (el) {
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
