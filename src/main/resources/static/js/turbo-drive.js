/**
 * Frame-based in-app navigation: sidebar/top bar stay put; only #floor21-main updates.
 */
(function () {
  var FRAME_ID = "floor21-main";

  if (typeof Turbo !== "undefined" && Turbo.config && Turbo.config.drive) {
    Turbo.config.drive.progressBarDelay = 1e9;
  }

  function isFileDownloadLink(link) {
    if (!link) {
      return false;
    }
    if (link.getAttribute("data-turbo") === "false") {
      return true;
    }
    if (link.hasAttribute("download")) {
      return true;
    }
    var href = link.getAttribute("href");
    if (!href) {
      return false;
    }
    return (
      /\/(export\/|demand-draft|download)(\/|\?|$)/i.test(href) ||
      /\.(csv|xlsx|xls|pdf|docx)(\?|$)/i.test(href)
    );
  }

  function wireDownloadLinks(root) {
    if (!root) {
      return;
    }
    root.querySelectorAll("a[href]").forEach(function (link) {
      if (!isFileDownloadLink(link)) {
        return;
      }
      link.setAttribute("data-turbo", "false");
      link.removeAttribute("data-turbo-frame");
    });
  }

  function wireFrameLinks(root) {
    if (!root) {
      return;
    }
    root.querySelectorAll('a[href]:not([data-turbo="false"])').forEach(function (link) {
      if (isFileDownloadLink(link)) {
        return;
      }
      var href = link.getAttribute("href");
      if (!href || href.charAt(0) !== "/" || href.indexOf("/logout") !== -1) {
        return;
      }
      if (link.getAttribute("target") === "_blank") {
        return;
      }
      link.setAttribute("data-turbo-frame", FRAME_ID);
      link.setAttribute("data-turbo-action", "advance");
    });
  }

  function syncBodyChrome() {
    var areaMeta = document.querySelector('meta[name="floor21-nav-area"]');
    if (areaMeta) {
      var area = areaMeta.getAttribute("content");
      if (area) {
        document.body.setAttribute("data-nav-area", area);
      }
    }
    var titleEl = document.querySelector("title");
    if (titleEl && titleEl.textContent) {
      document.title = titleEl.textContent;
    }
  }

  function onReady() {
    wireFrameLinks(document.getElementById("floor21-sidebar"));
    wireFrameLinks(document.getElementById("floor21-topbar"));
    wireDownloadLinks(document.getElementById(FRAME_ID));
    syncBodyChrome();
  }

  function onPageReady(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn);
    } else {
      fn();
    }
    document.addEventListener("turbo:load", fn);
    document.addEventListener("turbo:frame-render", function (event) {
      if (event.target && event.target.id === FRAME_ID) {
        fn();
      }
    });
  }

  onPageReady(onReady);

  document.addEventListener("turbo:click", function (event) {
    var link = event.target.closest("a[href]");
    if (!link || link.getAttribute("data-turbo") === "false") {
      return;
    }
    if (isFileDownloadLink(link)) {
      return;
    }
    if (!link.getAttribute("data-turbo-frame")) {
      link.setAttribute("data-turbo-frame", FRAME_ID);
    }
    if (!link.getAttribute("data-turbo-action")) {
      link.setAttribute("data-turbo-action", "advance");
    }
  });
})();
