(function (global) {
  const NF = new Intl.NumberFormat('en-IN', {
    maximumFractionDigits: 2,
    minimumFractionDigits: 0,
  });

  function parseAmount(str) {
    if (str == null || str === '') {
      return null;
    }
    const cleaned = String(str).replace(/,/g, '').trim();
    if (cleaned === '') {
      return null;
    }
    const v = parseFloat(cleaned);
    return Number.isFinite(v) && v >= 0 ? v : null;
  }

  function formatAmount(n) {
    if (n == null) {
      return '';
    }
    return NF.format(n);
  }

  function formatRupee(n) {
    if (n == null) {
      return '\u20B9 \u2014';
    }
    return '\u20B9 ' + NF.format(n);
  }

  function syncDisplayToHidden(displayEl) {
    const hiddenId = displayEl.dataset.amountHidden;
    const hidden = hiddenId ? document.getElementById(hiddenId) : null;
    if (!hidden) {
      return null;
    }
    const n = parseAmount(displayEl.value);
    hidden.value = n != null ? String(n) : '';
    return n;
  }

  function formatDisplayInput(displayEl) {
    const n = parseAmount(displayEl.value);
    displayEl.value = n != null ? formatAmount(n) : '';
    return syncDisplayToHidden(displayEl);
  }

  function initDisplayInput(displayEl) {
    if (!displayEl || displayEl.dataset.amountBound === '1') {
      return;
    }
    displayEl.dataset.amountBound = '1';
    const hiddenId = displayEl.dataset.amountHidden;
    const hidden = hiddenId ? document.getElementById(hiddenId) : null;
    if (hidden && hidden.value) {
      const n = parseFloat(hidden.value);
      if (Number.isFinite(n)) {
        displayEl.value = formatAmount(n);
      }
    }
    displayEl.addEventListener('blur', function () {
      formatDisplayInput(displayEl);
    });
    displayEl.addEventListener('input', function () {
      syncDisplayToHidden(displayEl);
    });
  }

  function bindAmountForm(form) {
    if (!form || form.dataset.amountFormBound === '1') {
      return;
    }
    form.dataset.amountFormBound = '1';
    form.querySelectorAll('.js-amount-input').forEach(initDisplayInput);
    form.addEventListener('submit', function () {
      form.querySelectorAll('.js-amount-input').forEach(formatDisplayInput);
    });
  }

  global.Floor21Amount = {
    parseAmount: parseAmount,
    formatAmount: formatAmount,
    formatRupee: formatRupee,
    initDisplayInput: initDisplayInput,
    bindAmountForm: bindAmountForm,
    syncDisplayToHidden: syncDisplayToHidden,
    formatDisplayInput: formatDisplayInput,
  };
})(window);
