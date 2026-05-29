/**
 * Session handling: activity tracking, login redirect, fetch/Turbo auth failures.
 */
(function () {
  var ACTIVITY_KEY = "floor21_last_activity";

  function appRoot() {
    var body = document.body;
    var root = body && body.getAttribute("data-app-root");
    if (root == null || root === "") {
      return "";
    }
    return root.replace(/\/+$/, "");
  }

  function appPath(pathAndQuery) {
    var path = pathAndQuery || "";
    if (path.charAt(0) !== "/") {
      path = "/" + path;
    }
    return appRoot() + path;
  }

  function sessionTimeoutMs() {
    var meta = document.querySelector('meta[name="floor21-session-timeout-seconds"]');
    var secs = meta ? parseInt(meta.getAttribute("content"), 10) : 3600;
    if (!secs || secs < 60) {
      secs = 3600;
    }
    return secs * 1000;
  }

  function touchActivity() {
    try {
      localStorage.setItem(ACTIVITY_KEY, String(Date.now()));
    } catch (e) {
      /* ignore private browsing */
    }
  }

  function wasIdleTooLong() {
    try {
      var last = parseInt(localStorage.getItem(ACTIVITY_KEY) || "0", 10);
      return last > 0 && Date.now() - last >= sessionTimeoutMs();
    } catch (e) {
      return false;
    }
  }

  function loginUrlForInvalidSession() {
    var param = wasIdleTooLong() ? "expired=true" : "relogin=true";
    return appPath("/login?" + param);
  }

  function redirectToLogin(invalidSession) {
    var url = invalidSession ? loginUrlForInvalidSession() : appPath("/login");
    window.location.assign(url);
  }

  function isLoginPageUrl(url) {
    return url != null && /\/login(\?|$)/i.test(url);
  }

  function sessionInvalidFromResponse(response) {
    if (!response) {
      return false;
    }
    if (response.headers && response.headers.get("X-Floor21-Session-Expired") === "true") {
      return true;
    }
    if (response.url && isLoginPageUrl(response.url)) {
      return /[?&](relogin|expired)=true/.test(response.url);
    }
    return false;
  }

  function normalizeUrl(url) {
    if (!url) {
      return url;
    }
    return url.replace(/([^:]\/)\/+/g, "$1");
  }

  function handleAuthFailure(response) {
    if (response && response.url && isLoginPageUrl(response.url)) {
      window.location.assign(normalizeUrl(response.url));
      return;
    }
    redirectToLogin(sessionInvalidFromResponse(response));
  }

  window.floor21RedirectToLogin = redirectToLogin;
  window.floor21TouchActivity = touchActivity;
  window.floor21AppPath = appPath;

  document.addEventListener("turbo:load", touchActivity);
  document.addEventListener("click", touchActivity, true);
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", touchActivity);
  } else {
    touchActivity();
  }

  var nativeFetch = window.fetch;
  if (typeof nativeFetch === "function") {
    window.fetch = function () {
      return nativeFetch.apply(this, arguments).then(function (response) {
        if (response.status === 401) {
          handleAuthFailure(response);
          return Promise.reject(new Error("Session expired or not signed in"));
        }
        if (response.redirected && isLoginPageUrl(response.url)) {
          handleAuthFailure(response);
          return Promise.reject(new Error("Session expired or not signed in"));
        }
        return response;
      });
    };
  }

  document.addEventListener("turbo:before-fetch-response", function (event) {
    var fetchResponse = event.detail && event.detail.fetchResponse;
    if (!fetchResponse) {
      return;
    }
    var response = fetchResponse.response;
    if (!response) {
      return;
    }
    if (response.status === 401) {
      event.preventDefault();
      handleAuthFailure(response);
      return;
    }
    if (isLoginPageUrl(response.url)) {
      event.preventDefault();
      handleAuthFailure(response);
    }
  });
})();
