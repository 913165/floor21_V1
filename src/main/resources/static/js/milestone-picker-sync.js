/**
 * Keeps milestone-area picker selects aligned with server-rendered selection after Turbo navigation.
 */
(function () {
  'use strict';

  function hasOption(select, value) {
    if (!select || !value) {
      return false;
    }
    return Array.prototype.some.call(select.options, function (opt) {
      return opt.value === value;
    });
  }

  function syncPicker(form) {
    var buildingId = form.getAttribute('data-selected-building-id') || '';
    var bookingId = form.getAttribute('data-selected-booking-id') || '';
    var building = form.querySelector('[name="buildingId"]');
    var booking = form.querySelector('[name="bookingId"]');
    if (building && buildingId && hasOption(building, buildingId)) {
      building.value = buildingId;
    }
    if (booking && bookingId && hasOption(booking, bookingId)) {
      booking.value = bookingId;
    }
  }

  function init() {
    document.querySelectorAll('[data-milestone-picker]').forEach(syncPicker);
  }

  document.addEventListener('turbo:load', init);
  document.addEventListener('turbo:frame-render', init);
  if (document.readyState !== 'loading') {
    init();
  } else {
    document.addEventListener('DOMContentLoaded', init);
  }
})();
