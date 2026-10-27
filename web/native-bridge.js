/* Native helpers for the Android app. Loaded before the page script (see scripts/prepare-web.js). */
(function () {
  var cap = window.Capacitor;
  var native = !!(cap && cap.isNativePlatform && cap.isNativePlatform());
  var P = function (n) { return cap && cap.Plugins && cap.Plugins[n]; };

  window.NT = window.NT || { msg: 'Tap the button below so notifications, alarms and car tracking can work.' };

  /* Daily reminders: end-of-day check and plan tomorrow (not on Friday). Re-run whenever the app opens. */
  window.ntSchedule = window.ntSchedule || async function () {
    var LN = P('LocalNotifications');
    if (!native || !LN) return;
    try { await LN.createChannel({ id: 'reminders', name: 'Reminders', importance: 4, vibration: true }); } catch (e) {}
    try {
      var ids = [];
      for (var i = 0; i < 40; i++) ids.push({ id: 100 + i });
      await LN.cancel({ notifications: ids });
    } catch (e) {}
    var out = [], id = 100, now = Date.now();
    var defs = [
      ['check', 'End-of-day check', 'Tap to see the goals you have not finished and move them.'],
      ['plan', 'Plan tomorrow', "Tap to open tomorrow's plan."]
    ];
    for (var j = 0; j < 10; j++) {
      var d = new Date(mk().getTime() + j * 864e5);
      if (info(d).off) continue;
      defs.forEach(function (x) {
        var hm = String(S.rem[x[0]] || '').split(':');
        var at = new Date(d);
        at.setHours(+hm[0] || 0, +hm[1] || 0, 0, 0);
        if (at.getTime() > now && out.length < 38) {
          out.push({ id: id++, title: x[1], body: x[2], schedule: { at: at, allowWhileIdle: true }, channelId: 'reminders', smallIcon: 'ic_stat_icon', extra: { act: x[0] } });
        }
      });
    }
    try { if (out.length) await LN.schedule({ notifications: out }); } catch (e) {}
  };

  /* Alarm buttons in Settings: daily alarm (Saturday to Thursday) and one-time alarm. */
  document.addEventListener('click', async function (e) {
    var b = e.target.closest('[data-nt]');
    if (!b || !native) return;
    var CA = P('ClockAlarm');
    if (!CA || !CA.setAlarm) return;
    var daily = b.dataset.nt === 'am';
    var input = document.getElementById(daily ? 'ntam' : 'ntat');
    var v = (input && input.value) || (daily ? '06:30' : '05:30');
    try { localStorage.setItem(daily ? 'fdp-am' : 'fdp-at', v); } catch (x) {}
    var p = v.split(':');
    var args = { hour: +p[0], minute: +p[1], message: 'Work Tracker' };
    if (daily) args.days = [7, 1, 2, 3, 4, 5]; // Saturday, Sunday ... Thursday
    try { await CA.setAlarm(args); if (window.toast) toast('Alarm set for ' + v); }
    catch (err) { if (window.toast) toast('Could not set the alarm'); }
  });
})();
