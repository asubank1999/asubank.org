/* Home page weather card: campus only, nothing about the visitor is sent. Same forecast as the apps. */
(function () {
    'use strict';
    var box = document.getElementById('weatherCard');
    if (!box || !window.fetch) return;
    var URL = 'https://api.open-meteo.com/v1/forecast?latitude=32.04&longitude=35.90&hourly=temperature_2m,precipitation_probability,weather_code&timezone=Asia%2FAmman&forecast_days=2';
    var ZONE = 'Asia/Amman';

    function emoji(c) {
        if (c === 0) return '☀️';
        if (c === 1 || c === 2) return '🌤️';
        if (c === 45 || c === 48) return '🌫️';
        if (c >= 51 && c <= 57) return '🌦️';
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return '🌧️';
        if ((c >= 71 && c <= 77) || c === 85 || c === 86) return '❄️';
        if (c >= 95 && c <= 99) return '⛈️';
        return '☁️';
    }
    function hourLabel(h) { return h === 12 ? '12 PM' : h > 12 ? (h - 12) + ' PM' : h + ' AM'; }
    function advice(hours) {
        var rain = Math.max.apply(null, hours.map(function (h) { return h.rain; }));
        var temps = hours.map(function (h) { return h.temp; });
        if (rain >= 50) return 'Take an umbrella, rain is likely.';
        if (Math.max.apply(null, temps) >= 32) return "It's hot. Drink water and stay in the shade.";
        if (Math.min.apply(null, temps) <= 8) return "It's cold. Take a jacket.";
        return '';
    }
    function el(tag, css, text) {
        var e = document.createElement(tag);
        if (css) e.style.cssText = css;
        if (text !== undefined) e.textContent = text;
        return e;
    }

    function draw(hours, tomorrow, dateText) {
        var muted = 'color:var(--ab-label-2,#6e6e73);font-size:13px';
        box.style.cssText = 'margin-top:14px;padding:14px;border-radius:16px;background:var(--ab-surface);box-shadow:0 1px 3px rgba(0,0,0,.08)';
        var top = el('div', 'display:flex;justify-content:space-between;gap:12px;align-items:flex-start');
        var left = el('div');
        left.appendChild(el('div', 'font-weight:600;font-size:15px', (tomorrow ? 'Tomorrow on campus' : 'Today on campus')));
        left.appendChild(el('div', muted, dateText));
        var temps = hours.map(function (h) { return h.temp; });
        var right = el('div', 'text-align:right;flex-shrink:0');
        right.appendChild(el('div', muted, '7 AM – 4 PM'));
        right.appendChild(el('div', 'font-weight:600;font-size:13px', 'High ' + Math.max.apply(null, temps) + '° · Low ' + Math.min.apply(null, temps) + '°'));
        top.appendChild(left);
        top.appendChild(right);
        box.appendChild(top);

        var row = el('div', 'display:flex;gap:14px;overflow-x:auto;margin-top:10px;padding-bottom:2px');
        hours.forEach(function (h) {
            var c = el('div', 'min-width:40px;text-align:center;flex-shrink:0');
            c.appendChild(el('div', muted.replace('13px', '11px'), hourLabel(h.hour)));
            c.appendChild(el('div', 'font-size:20px;line-height:1.5', emoji(h.code)));
            c.appendChild(el('div', 'font-weight:600;font-size:14px', h.temp + '°'));
            c.appendChild(el('div', 'font-size:11px;color:' + (h.rain >= 40 ? '#1e88e5' : 'var(--ab-label-2,#6e6e73)'), h.rain + '%'));
            row.appendChild(c);
        });
        box.appendChild(row);
        var a = advice(hours);
        if (a) box.appendChild(el('div', muted + ';margin-top:8px', a));
        box.hidden = false;
    }

    fetch(URL).then(function (r) { return r.json(); }).then(function (o) {
        var now = new Date();
        var hourNow = Number(new Intl.DateTimeFormat('en-GB', { hour: 'numeric', hourCycle: 'h23', timeZone: ZONE }).format(now));
        var tomorrow = hourNow >= 16;
        var day = tomorrow ? new Date(now.getTime() + 864e5) : now;
        var prefix = new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: ZONE }).format(day);
        var dateText = new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: ZONE }).format(day);
        var w = o.hourly;
        var hours = [];
        w.time.forEach(function (t, i) {
            if (t.indexOf(prefix) !== 0) return;
            var h = Number(t.slice(11, 13));
            if (h < 7 || h > 16) return;
            hours.push({ hour: h, temp: Math.round(w.temperature_2m[i] || 0), rain: w.precipitation_probability[i] || 0, code: w.weather_code[i] === null ? 3 : w.weather_code[i] });
        });
        if (hours.length) draw(hours, tomorrow, dateText);
    }).catch(function () {});
})();
