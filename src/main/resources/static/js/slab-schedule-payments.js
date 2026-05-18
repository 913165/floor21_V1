/**
 * Per-slab payment rows on Slabs — payment schedule.
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
    return String(n);
  }

  function appRoot() {
    var r = document.body.getAttribute('data-app-root') || '';
    return r.replace(/\/$/, '');
  }

  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (!token || !header) {
      return {};
    }
    var h = {};
    h[header.getAttribute('content')] = token.getAttribute('content');
    return h;
  }

  function syncRowAmount(row) {
    var display = row.querySelector('.js-slab-pay-amount-display');
    if (display && window.Floor21Amount) {
      Floor21Amount.formatDisplayInput(display);
    }
  }

  function readRowPayload(row, panel) {
    var form = document.getElementById('scheduleSaveForm');
    var bookingId = form && form.getAttribute('data-booking-id');
    var slabId = panel.getAttribute('data-slab-id');
    var dateInput = row.querySelector('input[type="date"]');
    var hidden = row.querySelector('.js-slab-pay-amount-hidden');
    var refInput = row.querySelector('input[name$=".reference"]');
    var idInput = row.querySelector('.js-slab-pay-id');
    var amount = hidden && hidden.value ? parseFloat(hidden.value) : null;
    if (amount == null || !Number.isFinite(amount)) {
      var display = row.querySelector('.js-slab-pay-amount-display');
      amount = display ? parseAmt(display.value) : null;
    }
    return {
      bookingId: bookingId,
      slabId: slabId,
      id: idInput && idInput.value ? idInput.value : null,
      paymentDate: dateInput ? dateInput.value : null,
      amount: amount,
      reference: refInput ? refInput.value.trim() : '',
    };
  }

  function applySaveResponse(panel, data) {
    var idx = panel.getAttribute('data-slab-index');
    if (idx == null || !data) {
      return;
    }
    var paidEl = document.getElementById('slabPaidDisplay_' + idx);
    var balEl = document.getElementById('slabBalanceDisplay_' + idx);
    var summaryPaid = panel.querySelector('.slab-tree-summary-paid');
    var summaryBal = panel.querySelector('.slab-tree-summary-balance');
    var summaryDue = panel.querySelector('.slab-tree-summary-due');
    var paid = data.slabPaid != null ? Number(data.slabPaid) : 0;
    var balance = data.slabBalance != null ? Number(data.slabBalance) : 0;
    var due = data.slabDue != null ? Number(data.slabDue) : 0;
    if (paidEl) {
      paidEl.textContent = formatAmt(paid);
    }
    if (balEl) {
      balEl.textContent = formatAmt(balance);
      balEl.classList.toggle('text-danger', balance > 0);
      balEl.classList.toggle('slab-amt--balance', balance > 0);
      balEl.classList.toggle('text-muted', balance <= 0);
    }
    if (summaryPaid) {
      summaryPaid.textContent = formatAmt(paid);
    }
    if (summaryBal) {
      summaryBal.textContent = formatAmt(balance);
      summaryBal.classList.toggle('text-danger', balance > 0);
      summaryBal.classList.toggle('text-muted', balance <= 0);
    }
    if (summaryDue) {
      summaryDue.textContent = formatAmt(due);
    }
    var elPaid = document.getElementById('scheduleTotalPaid');
    var elBal = document.getElementById('scheduleTotalBalance');
    if (elPaid && data.totalPaid != null) {
      elPaid.textContent = formatAmt(Number(data.totalPaid));
    }
    if (elBal && data.totalBalance != null) {
      elBal.textContent = formatAmt(Number(data.totalBalance));
    }
  }

  function ensurePaymentIdInput(row, slabIndex, payIndex, id) {
    var existing = row.querySelector('.js-slab-pay-id');
    if (existing) {
      existing.value = id;
      return existing;
    }
    var dateCell = row.querySelector('.slab-payment-entry-date');
    if (!dateCell) {
      return null;
    }
    var input = document.createElement('input');
    input.type = 'hidden';
    input.className = 'js-slab-pay-id';
    input.name = 'lines[' + slabIndex + '].payments[' + payIndex + '].id';
    input.value = id;
    dateCell.insertBefore(input, dateCell.firstChild);
    return input;
  }

  function setSaveButtonState(btn, state) {
    if (!btn) {
      return;
    }
    btn.classList.remove('slab-save-payment-btn--saved', 'slab-save-payment-btn--error');
    btn.disabled = false;
    if (state === 'saving') {
      btn.disabled = true;
      btn.textContent = '…';
    } else if (state === 'saved') {
      btn.classList.add('slab-save-payment-btn--saved');
      btn.textContent = '✓';
    } else if (state === 'error') {
      btn.classList.add('slab-save-payment-btn--error');
      btn.textContent = '!';
    } else {
      btn.textContent = '✓';
    }
  }

  function savePaymentRow(row, panel) {
    syncRowAmount(row);
    var payload = readRowPayload(row, panel);
    if (!payload.bookingId || !payload.slabId) {
      alert('Reload the booking and try again.');
      return Promise.reject();
    }
    if (!payload.paymentDate) {
      alert('Enter a payment date.');
      return Promise.reject();
    }
    if (payload.amount == null || payload.amount <= 0) {
      alert('Enter a payment amount greater than zero.');
      return Promise.reject();
    }
    var saveBtn = row.querySelector('.slab-save-payment-btn');
    setSaveButtonState(saveBtn, 'saving');
    return fetch(appRoot() + '/bookings/payment-schedule/payments/save', {
      method: 'POST',
      headers: Object.assign({ 'Content-Type': 'application/json', Accept: 'application/json' }, csrfHeaders()),
      body: JSON.stringify(payload),
    })
      .then(function (res) {
        if (!res.ok) {
          return res.text().then(function (t) {
            throw new Error(t || 'Save failed');
          });
        }
        return res.json();
      })
      .then(function (data) {
        var slabIndex = panel.getAttribute('data-slab-index');
        var payIndex = row.getAttribute('data-pay-index');
        if (data.id && slabIndex != null && payIndex != null) {
          ensurePaymentIdInput(row, slabIndex, payIndex, data.id);
          row.classList.add('slab-payment-entry-row--saved');
        }
        applySaveResponse(panel, data);
        setSaveButtonState(saveBtn, 'saved');
        setTimeout(function () {
          setSaveButtonState(saveBtn, 'idle');
        }, 1500);
        return data;
      })
      .catch(function (err) {
        setSaveButtonState(saveBtn, 'error');
        alert(err.message || 'Could not save payment.');
        setTimeout(function () {
          setSaveButtonState(saveBtn, 'idle');
        }, 2000);
        throw err;
      });
  }

  function deletePaymentRow(row, panel) {
    var payload = readRowPayload(row, panel);
    if (!payload.id) {
      row.remove();
      reindexPayments(panel);
      recalcSlab(panel);
      recalcAllSlabs();
      return Promise.resolve();
    }
    if (!payload.bookingId || !payload.slabId) {
      row.remove();
      reindexPayments(panel);
      recalcSlab(panel);
      recalcAllSlabs();
      return Promise.resolve();
    }
    var url =
      appRoot() +
      '/bookings/payment-schedule/payments/' +
      encodeURIComponent(payload.id) +
      '?bookingId=' +
      encodeURIComponent(payload.bookingId) +
      '&slabId=' +
      encodeURIComponent(payload.slabId);
    return fetch(url, {
      method: 'DELETE',
      headers: Object.assign({ Accept: 'application/json' }, csrfHeaders()),
    })
      .then(function (res) {
        if (!res.ok) {
          throw new Error('Delete failed');
        }
        return res.json();
      })
      .then(function (data) {
        row.remove();
        reindexPayments(panel);
        applySaveResponse(panel, data);
        return data;
      });
  }

  function findSlabParts(toggleBtn) {
    const dataRow = toggleBtn.closest('tr.slab-schedule-data-row');
    if (!dataRow) {
      return null;
    }
    const treeRow = dataRow.nextElementSibling;
    if (!treeRow || !treeRow.classList.contains('slab-payment-tree-row')) {
      return null;
    }
    const collapse = treeRow.querySelector('.slab-payment-collapse');
    const panel = treeRow.querySelector('.slab-payment-tree-panel');
    if (!collapse || !panel) {
      return null;
    }
    return { dataRow: dataRow, treeRow: treeRow, collapse: collapse, panel: panel };
  }

  function slabDueAmount(panel) {
    const idx = panel.getAttribute('data-slab-index');
    if (idx == null) {
      return 0;
    }
    const agreedEl = document.getElementById('slabAgreedDisplay_' + idx);
    const extraEl = document.getElementById('slabExtraDisplay_' + idx);
    const agreed = agreedEl ? parseAmt(agreedEl.value) : null;
    const extra = extraEl ? parseAmt(extraEl.value) : null;
    return (agreed != null ? agreed : 0) + (extra != null ? extra : 0);
  }

  function sumPayments(panel) {
    let sum = 0;
    panel.querySelectorAll('.slab-payment-entry-row').forEach(function (row) {
      const display = row.querySelector('.js-slab-pay-amount-display');
      const n = display ? parseAmt(display.value) : null;
      if (n != null && n > 0) {
        sum += n;
      }
    });
    return sum;
  }

  function recalcSlab(panel) {
    const idx = panel.getAttribute('data-slab-index');
    if (idx == null) {
      return null;
    }
    const due = slabDueAmount(panel);
    const paid = sumPayments(panel);
    const balance = Math.max(due - paid, 0);
    const paidEl = document.getElementById('slabPaidDisplay_' + idx);
    const balEl = document.getElementById('slabBalanceDisplay_' + idx);
    const summaryPaid = panel.querySelector('.slab-tree-summary-paid');
    const summaryBal = panel.querySelector('.slab-tree-summary-balance');
    const summaryDue = panel.querySelector('.slab-tree-summary-due');
    if (paidEl) {
      paidEl.textContent = formatAmt(paid);
    }
    if (balEl) {
      balEl.textContent = formatAmt(balance);
      balEl.classList.toggle('text-danger', balance > 0);
      balEl.classList.toggle('slab-amt--balance', balance > 0);
      balEl.classList.toggle('text-muted', balance <= 0);
    }
    if (summaryPaid) {
      summaryPaid.textContent = formatAmt(paid);
    }
    if (summaryBal) {
      summaryBal.textContent = formatAmt(balance);
      summaryBal.classList.toggle('text-danger', balance > 0);
      summaryBal.classList.toggle('text-muted', balance <= 0);
    }
    if (summaryDue) {
      summaryDue.textContent = formatAmt(due);
    }
    return { paid: paid, balance: balance };
  }

  function recalcAllSlabs() {
    const form = document.getElementById('scheduleSaveForm');
    if (!form) {
      return;
    }
    let totalPaid = 0;
    let totalBalance = 0;
    form.querySelectorAll('.slab-payment-tree-panel').forEach(function (panel) {
      const r = recalcSlab(panel);
      if (r) {
        totalPaid += r.paid;
        totalBalance += r.balance;
      }
    });
    const elPaid = document.getElementById('scheduleTotalPaid');
    const elBal = document.getElementById('scheduleTotalBalance');
    if (elPaid) {
      elPaid.textContent = formatAmt(totalPaid);
    }
    if (elBal) {
      elBal.textContent = formatAmt(totalBalance);
    }
  }

  function nextPaymentIndex(panel) {
    let max = -1;
    panel.querySelectorAll('.slab-payment-entry-row').forEach(function (row) {
      const n = parseInt(row.getAttribute('data-pay-index'), 10);
      if (Number.isFinite(n) && n > max) {
        max = n;
      }
    });
    return max + 1;
  }

  function buildPaymentRow(slabIndex, payIndex) {
    const tr = document.createElement('tr');
    tr.className = 'slab-payment-entry-row';
    tr.setAttribute('data-pay-index', String(payIndex));
    const prefix = 'lines[' + slabIndex + '].payments[' + payIndex + ']';
    const hiddenId = 'slabPayAmountHidden_' + slabIndex + '_' + payIndex;
    const displayId = 'slabPayAmountDisplay_' + slabIndex + '_' + payIndex;
    tr.innerHTML =
      '<td class="slab-payment-entry-date p-1">' +
      '<input type="date" class="form-control form-control-sm" name="' +
      prefix +
      '.paymentDate" required />' +
      '</td>' +
      '<td class="slab-payment-entry-amt p-1">' +
      '<input type="hidden" name="' +
      prefix +
      '.amount" class="js-slab-pay-amount-hidden" id="' +
      hiddenId +
      '" />' +
      '<input type="text" class="form-control form-control-sm text-end js-amount-input js-slab-pay-amount-display slab-amt-input--paid" ' +
      'id="' +
      displayId +
      '" inputmode="decimal" autocomplete="off" placeholder="0" ' +
      'data-amount-hidden="' +
      hiddenId +
      '" />' +
      '</td>' +
      '<td class="slab-payment-entry-ref p-1">' +
      '<input type="text" class="form-control form-control-sm" name="' +
      prefix +
      '.reference" maxlength="200" placeholder="Cheque / NEFT / note" />' +
      '</td>' +
      '<td class="slab-payment-entry-actions p-1 text-end text-nowrap">' +
      '<button type="button" class="btn btn-sm btn-outline-success slab-save-payment-btn" title="Save this payment">✓</button> ' +
      '<button type="button" class="btn btn-sm btn-outline-danger slab-remove-payment-btn" title="Remove">×</button>' +
      '</td>';
    return tr;
  }

  function bindPaymentRow(panel, row) {
    const display = row.querySelector('.js-slab-pay-amount-display');
    if (display && window.Floor21Amount) {
      display.dataset.amountBound = '';
      Floor21Amount.initDisplayInput(display);
    }
    row.querySelectorAll('input').forEach(function (inp) {
      inp.addEventListener('input', function () {
        recalcSlab(panel);
        recalcAllSlabs();
      });
    });
    const saveBtn = row.querySelector('.slab-save-payment-btn');
    if (saveBtn) {
      saveBtn.addEventListener('click', function () {
        savePaymentRow(row, panel);
      });
    }
    const removeBtn = row.querySelector('.slab-remove-payment-btn');
    if (removeBtn) {
      removeBtn.addEventListener('click', function () {
        deletePaymentRow(row, panel).catch(function () {
          /* alert shown in save */
        });
      });
    }
  }

  function reindexPayments(panel) {
    const slabIndex = panel.getAttribute('data-slab-index');
    const tbody = panel.querySelector('.slab-payment-rows');
    if (!tbody || slabIndex == null) {
      return;
    }
    tbody.querySelectorAll('.slab-payment-entry-row').forEach(function (row, payIndex) {
      row.setAttribute('data-pay-index', String(payIndex));
      row.querySelectorAll('[name]').forEach(function (el) {
        const name = el.getAttribute('name');
        if (!name) {
          return;
        }
        el.setAttribute(
            'name',
            name.replace(/lines\[\d+\]\.payments\[\d+\]/, 'lines[' + slabIndex + '].payments[' + payIndex + ']'));
      });
      const display = row.querySelector('.js-slab-pay-amount-display');
      const hidden = row.querySelector('.js-slab-pay-amount-hidden');
      if (display && hidden) {
        hidden.id = 'slabPayAmountHidden_' + slabIndex + '_' + payIndex;
        display.id = 'slabPayAmountDisplay_' + slabIndex + '_' + payIndex;
        display.setAttribute('data-amount-hidden', hidden.id);
        if (window.Floor21Amount) {
          display.dataset.amountBound = '';
          Floor21Amount.initDisplayInput(display);
        }
      }
    });
  }

  function setToggleOpen(btn, open) {
    const ch = btn.querySelector('.slab-tree-chevron');
    if (ch) {
      ch.textContent = open ? '−' : '+';
    }
    btn.setAttribute('aria-expanded', open ? 'true' : 'false');
  }

  function showPanel(collapse, btn) {
    collapse.classList.add('show');
    if (window.bootstrap && bootstrap.Collapse) {
      try {
        bootstrap.Collapse.getOrCreateInstance(collapse, { toggle: false }).show();
      } catch (err) {
        /* use class only */
      }
    }
    if (btn) {
      setToggleOpen(btn, true);
    }
  }

  function hidePanel(collapse, btn) {
    collapse.classList.remove('show');
    if (window.bootstrap && bootstrap.Collapse) {
      try {
        bootstrap.Collapse.getOrCreateInstance(collapse, { toggle: false }).hide();
      } catch (err) {
        /* use class only */
      }
    }
    if (btn) {
      setToggleOpen(btn, false);
    }
  }

  function addPaymentRow(panel) {
    const slabIndex = panel.getAttribute('data-slab-index');
    const tbody = panel.querySelector('.slab-payment-rows');
    if (!tbody || slabIndex == null) {
      return null;
    }
    const row = buildPaymentRow(slabIndex, nextPaymentIndex(panel));
    tbody.appendChild(row);
    bindPaymentRow(panel, row);
    const dateInput = row.querySelector('input[type="date"]');
    if (dateInput && !dateInput.value) {
      dateInput.value = new Date().toISOString().slice(0, 10);
    }
    if (dateInput) {
      dateInput.focus();
    }
    recalcSlab(panel);
    recalcAllSlabs();
    return row;
  }

  function hasPaymentRows(panel) {
    const tbody = panel.querySelector('.slab-payment-rows');
    return tbody != null && tbody.querySelectorAll('.slab-payment-entry-row').length > 0;
  }

  function handlePlusClick(btn) {
    const parts = findSlabParts(btn);
    if (!parts) {
      return;
    }
    const isOpen = parts.collapse.classList.contains('show');
    const ch = btn.querySelector('.slab-tree-chevron');
    if (isOpen && ch && ch.textContent === '−') {
      hidePanel(parts.collapse, btn);
      return;
    }
    showPanel(parts.collapse, btn);
    if (!hasPaymentRows(parts.panel)) {
      addPaymentRow(parts.panel);
    }
  }

  function init() {
    const form = document.getElementById('scheduleSaveForm');
    if (!form) {
      return;
    }

    form.querySelectorAll('.slab-payment-tree-panel').forEach(function (panel) {
      panel.querySelectorAll('.slab-payment-entry-row').forEach(function (row) {
        bindPaymentRow(panel, row);
      });
      recalcSlab(panel);
    });

    form.querySelectorAll('.slab-payment-tree-row .slab-payment-collapse.show').forEach(function (collapse) {
      const treeRow = collapse.closest('.slab-payment-tree-row');
      const dataRow = treeRow && treeRow.previousElementSibling;
      if (dataRow) {
        const btn = dataRow.querySelector('.slab-tree-toggle');
        if (btn) {
          setToggleOpen(btn, true);
        }
      }
    });

    form.addEventListener('click', function (e) {
      const plusBtn = e.target.closest('.slab-tree-toggle');
      if (plusBtn && form.contains(plusBtn)) {
        e.preventDefault();
        e.stopPropagation();
        handlePlusClick(plusBtn);
        return;
      }
      const addBtn = e.target.closest('.slab-add-payment-btn');
      if (addBtn && form.contains(addBtn)) {
        e.preventDefault();
        const panel = addBtn.closest('.slab-payment-tree-panel');
        if (!panel) {
          return;
        }
        const treeRow = panel.closest('.slab-payment-tree-row');
        const dataRow = treeRow && treeRow.previousElementSibling;
        const toggleBtn = dataRow && dataRow.querySelector('.slab-tree-toggle');
        const parts = toggleBtn ? findSlabParts(toggleBtn) : null;
        if (parts) {
          showPanel(parts.collapse, toggleBtn);
        }
        addPaymentRow(panel);
      }
    });

    form.addEventListener('input', function (e) {
      if (
        e.target.matches('[id^="slabAgreedDisplay_"], [id^="slabExtraDisplay_"]') ||
        e.target.closest('.slab-payment-entry-row')
      ) {
        recalcAllSlabs();
      }
    });

    if (window.Floor21Amount && !form.dataset.amountFormBound) {
      Floor21Amount.bindAmountForm(form);
    }

    recalcAllSlabs();
  }

  window.Floor21SlabPayments = { init: init, handlePlusClick: handlePlusClick };
})();
