/**
 * Excel-style column resize for .slab-schedule-table (payment schedule, milestone setup).
 */
(function () {
  "use strict";

  var MIN_COL_PX = 52;

  function ensureColGroup(table, colCount) {
    var colgroup = table.querySelector("colgroup");
    if (!colgroup) {
      colgroup = document.createElement("colgroup");
      table.insertBefore(colgroup, table.firstChild);
    }
    while (colgroup.children.length < colCount) {
      colgroup.appendChild(document.createElement("col"));
    }
    while (colgroup.children.length > colCount) {
      colgroup.removeChild(colgroup.lastChild);
    }
    return colgroup.querySelectorAll("col");
  }

  function defaultWidthRatios(table, colCount) {
    if (table.classList.contains("slab-schedule-ledger-table") && colCount === 11) {
      // Date, Slab, Check No, Amount, Receipt, GST, Balance, Days, Interest, Info, Remark
      return [0.07, 0.18, 0.07, 0.07, 0.07, 0.06, 0.07, 0.05, 0.07, 0.21, 0.08];
    }
    if (table.classList.contains("slab-schedule-ledger-table") && colCount === 10) {
      // Date, Slab, Check No, Amount, Receipt, Balance, Days, Interest, Info, Remark
      return [0.07, 0.2, 0.07, 0.08, 0.08, 0.08, 0.05, 0.07, 0.22, 0.08];
    }
    if (table.classList.contains("slab-schedule-ledger-table") && colCount === 8) {
      return [0.1, 0.28, 0.1, 0.1, 0.1, 0.06, 0.08, 0.18];
    }
    var each = 1 / colCount;
    var ratios = [];
    for (var i = 0; i < colCount; i++) {
      ratios.push(each);
    }
    return ratios;
  }

  function applyInitialWidths(table, ths, cols) {
    var tableWidth = table.getBoundingClientRect().width;
    if (!tableWidth || tableWidth < 100) {
      tableWidth = table.parentElement ? table.parentElement.clientWidth : 900;
    }
    var ratios = defaultWidthRatios(table, ths.length);
    ths.forEach(function (th, index) {
      var px = Math.max(MIN_COL_PX, Math.round(tableWidth * ratios[index]));
      cols[index].style.width = px + "px";
      th.style.width = px + "px";
    });
  }

  function initTable(table) {
    if (!table || table.dataset.slabColsInit === "1") {
      return;
    }
    var headerRow = table.querySelector("thead tr");
    if (!headerRow) {
      return;
    }
    var ths = headerRow.querySelectorAll("th");
    if (!ths.length) {
      return;
    }

    table.dataset.slabColsInit = "1";
    table.classList.add("slab-schedule-table--resizable");
    table.style.tableLayout = "fixed";
    table.style.width = "100%";

    var cols = ensureColGroup(table, ths.length);
    var hasWidth = false;
    cols.forEach(function (col) {
      if (col.style.width) {
        hasWidth = true;
      }
    });
    if (!hasWidth) {
      applyInitialWidths(table, ths, cols);
    }

    ths.forEach(function (th, index) {
      if (index === ths.length - 1) {
        return;
      }
      if (th.querySelector(".slab-schedule-col-resize")) {
        return;
      }
      th.classList.add("slab-schedule-th--resizable");

      var grip = document.createElement("span");
      grip.className = "slab-schedule-col-resize";
      grip.setAttribute("role", "separator");
      grip.setAttribute("aria-orientation", "vertical");
      grip.setAttribute("aria-label", "Resize column");
      grip.title = "Drag to resize column";
      th.appendChild(grip);

      grip.addEventListener("mousedown", function (e) {
        e.preventDefault();
        e.stopPropagation();
        var startX = e.pageX;
        var startW = th.offsetWidth;
        var col = cols[index];
        var nextTh = ths[index + 1];
        var nextCol = cols[index + 1];
        var nextStartW = nextTh ? nextTh.offsetWidth : 0;

        function onMove(ev) {
          var delta = ev.pageX - startX;
          var newW = Math.max(MIN_COL_PX, startW + delta);
          var appliedDelta = newW - startW;
          col.style.width = newW + "px";
          th.style.width = newW + "px";
          if (nextTh && nextCol) {
            var nextW = Math.max(MIN_COL_PX, nextStartW - appliedDelta);
            nextCol.style.width = nextW + "px";
            nextTh.style.width = nextW + "px";
          }
        }

        function onUp() {
          document.removeEventListener("mousemove", onMove);
          document.removeEventListener("mouseup", onUp);
          document.body.classList.remove("slab-schedule-col-resizing");
        }

        document.body.classList.add("slab-schedule-col-resizing");
        document.addEventListener("mousemove", onMove);
        document.addEventListener("mouseup", onUp);
      });
    });
  }

  function initAll(root) {
    var scope = root && root.querySelectorAll ? root : document;
    scope.querySelectorAll("table.slab-schedule-table").forEach(initTable);
  }

  document.addEventListener("DOMContentLoaded", function () {
    initAll(document);
  });
  document.addEventListener("turbo:load", function () {
    initAll(document);
  });
  document.addEventListener("turbo:frame-load", function (e) {
    if (e.target) {
      initAll(e.target);
    }
  });
})();
