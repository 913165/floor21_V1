/**
 * Project snapshot — multiple building elevations side by side on the Projects list.
 */
(function () {
  function appRoot() {
    return (document.body.getAttribute("data-app-root") || "").replace(/\/+$/, "");
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  async function fetchProjectBuildings(projectId) {
    var res = await fetch(appRoot() + "/admin/projects/" + encodeURIComponent(projectId) + "/snapshot-buildings", {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) {
      throw new Error("Could not load project buildings");
    }
    return res.json();
  }

  function mountProjectSnapshotModalOnBody() {
    var modalEl = document.getElementById("project-snapshot-modal");
    if (modalEl && modalEl.parentElement !== document.body) {
      document.body.appendChild(modalEl);
    }
  }

  async function openProjectSnapshot(projectId, projectName) {
    var modalEl = document.getElementById("project-snapshot-modal");
    var root = document.getElementById("project-snapshot-root");
    var title = document.getElementById("project-snapshot-modal-title");
    if (!modalEl || !root) {
      return;
    }
    mountProjectSnapshotModalOnBody();
    if (title) {
      title.textContent = "Project snapshot — " + (projectName || "Project");
    }
    root.innerHTML = '<p class="text-muted small mb-0 text-center py-3">Loading snapshots…</p>';
    bootstrap.Modal.getOrCreateInstance(modalEl).show();

    var snapshotApi = window.floor21BuildingSnapshot;
    if (!snapshotApi || typeof snapshotApi.load !== "function" || typeof snapshotApi.render !== "function") {
      root.innerHTML =
        '<p class="text-danger small mb-0 text-center py-3">Snapshot viewer failed to load. Refresh the page and try again.</p>';
      return;
    }

    try {
      var buildings = await fetchProjectBuildings(projectId);
      if (!buildings.length) {
        root.innerHTML =
          '<p class="text-muted small mb-0 text-center py-3">No buildings in this project yet.</p>';
        return;
      }

      var manyClass = buildings.length > 2 ? " project-snapshot--compact" : "";
      root.innerHTML = '<div class="project-snapshot' + manyClass + '"></div>';
      var container = root.querySelector(".project-snapshot");

      buildings.forEach(function (building) {
        var item = document.createElement("div");
        item.className = "project-snapshot__item";
        item.innerHTML =
          '<div class="project-snapshot__label">' +
          escapeHtml(building.buildingName) +
          '</div><div class="project-snapshot__tower building-snapshot"><p class="text-muted small mb-0 text-center py-2">Loading…</p></div>';
        container.appendChild(item);
      });

      await Promise.all(
        buildings.map(function (building, index) {
          var towerRoot = container.children[index].querySelector(".project-snapshot__tower");
          return snapshotApi
            .load(building.id)
            .then(function (data) {
              towerRoot.innerHTML = "";
              snapshotApi.render(towerRoot, data.payload, data.parkingPlans, data.groundPlan);
            })
            .catch(function () {
              towerRoot.innerHTML =
                '<p class="text-danger small mb-0 text-center py-2">Could not load this building.</p>';
            });
        })
      );
    } catch (err) {
      root.innerHTML =
        '<p class="text-danger small mb-0 text-center py-3">Could not load project snapshot.</p>';
    }
  }

  function initProjectSnapshotButtons() {
    mountProjectSnapshotModalOnBody();
    document.querySelectorAll(".project-snapshot-btn").forEach(function (btn) {
      if (btn.dataset.snapshotBound === "true") {
        return;
      }
      btn.dataset.snapshotBound = "true";
      btn.addEventListener("click", function () {
        openProjectSnapshot(btn.getAttribute("data-project-id"), btn.getAttribute("data-project-name"));
      });
    });
  }

  function onPageReady(fn) {
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", fn);
    } else {
      fn();
    }
    document.addEventListener("turbo:load", fn);
    document.addEventListener("turbo:frame-render", function (event) {
      if (!event.target || event.target.id === "floor21-main") {
        fn();
      }
    });
  }

  onPageReady(initProjectSnapshotButtons);
})();
