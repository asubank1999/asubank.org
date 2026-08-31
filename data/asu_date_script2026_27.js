const events = [
  // ===== الفصل الأول 2026/2027 =====
  { date: "2026-10-04", day: "الأحد - الخميس", name: "بدء العام الجامعي وبدء دوام أعضاء هيئة التدريس", week: 0 },
  { date: "2026-10-04", day: "الأحد - الخميس", name: "الفترة الثانية للإرشاد والتسجيل والانسحاب والإضافة لمواد الفصل الأول", week: 0 },
  { date: "2026-10-11", day: "الأحد - الخميس", name: "بدء الدراسة في الفصل الأول", week: 1 },
  { date: "2026-10-11", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الأولى (50 دينار)", week: 1 },
  { date: "2026-10-15", day: "الأحد - الخميس", name: "انتهاء فترة الانسحاب والإضافة للفصل الأول", week: 1 },
  { date: "2026-10-18", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الثانية (100 دينار)", week: 2 },
  { date: "2026-10-25", day: "الأحد - الخميس", name: "الأسبوع 3", week: 3 },
  { date: "2026-11-01", day: "الأحد - الخميس", name: "الأسبوع 4", week: 4 },
  { date: "2026-11-08", day: "الأحد - الخميس", name: "الأسبوع 5", week: 5 },
  { date: "2026-11-15", day: "الأحد - الخميس", name: "الأسبوع 6", week: 6 },
  { date: "2026-11-22", day: "الأحد - الخميس", name: "الأسبوع 7", week: 7 },
  { date: "2026-11-29", day: "الأحد - الخميس", name: "الأسبوع 8", week: 8 },
  { date: "2026-12-06", day: "الأحد - الخميس", name: "فترة الامتحانات الفصلية للفصل الأول", week: 9 },
  { date: "2026-12-14", day: "الاثنين", name: "بدء التسجيل على مواد الفصل الثاني", week: 0 },
  { date: "2026-12-16", day: "الأربعاء", name: "الامتحان الشامل (الجلسة الأولى)", week: 0 },
  { date: "2026-12-16", day: "الأربعاء", name: "الامتحان الشامل (الجلسة الثانية)", week: 0 },
  { date: "2026-12-25", day: "الجمعة", name: "عيد الميلاد المجيد", week: 0 },
  { date: "2027-01-01", day: "الجمعة", name: "رأس السنة الميلادية", week: 0 },
  { date: "2027-01-05", day: "الثلاثاء", name: "عيد الإسراء والمعراج الشريف (بدون تعطيل)", week: 0 },
  { date: "2027-01-14", day: "الخميس", name: "انتهاء فترة الانسحاب من مواد الفصل الأول", week: 0 },
  { date: "2027-01-21", day: "الخميس", name: "آخر موعد لمناقشة رسائل الماجستير", week: 0 },
  { date: "2027-01-23", day: "السبت - الخميس", name: "فترة الامتحانات النهائية للفصل الأول", week: 0 },
  { date: "2027-01-30", day: "السبت", name: "عيد ميلاد جلالة الملك عبدالله الثاني المعظم", week: 0 },
  { date: "2027-02-07", day: "الأحد", name: "بدء إجازة الطلبة", week: 0 },
  { date: "2027-02-07", day: "الأحد", name: "آخر موعد لتقديم نتائج الفصل الأول", week: 0 },
  { date: "2027-02-14", day: "الأحد", name: "آخر موعد لتسليم رسائل الماجستير لعمادة الدراسات العليا", week: 0 },
  { date: "2027-02-14", day: "الأحد", name: "آخر موعد لتقديم تنسيبات مجالس الكليات منح درجة البكالوريوس والماجستير والدبلوم للطلبة الخريجين في الفصل الأول", week: 0 },
  { date: "2027-02-17", day: "الأربعاء", name: "قرار مجلس العمداء منح درجة البكالوريوس والماجستير والدبلوم للطلبة الخريجين في الفصل الأول", week: 0 },

  // ===== الفصل الثاني 2026/2027 =====
  { date: "2027-02-21", day: "الأحد", name: "الفترة الثانية للإرشاد والتسجيل والانسحاب والإضافة لمواد الفصل الثاني", week: 0 },
  { date: "2027-02-28", day: "الأحد - الخميس", name: "بدء الدراسة في الفصل الثاني", week: 1 },
  { date: "2027-02-28", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الأولى (50 دينار)", week: 1 },
  { date: "2027-03-04", day: "الخميس", name: "انتهاء فترة الانسحاب والإضافة للفصل الثاني", week: 1 },
  { date: "2027-03-07", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الثانية (100 دينار)", week: 2 },
  { date: "2027-03-09", day: "الثلاثاء - الجمعة", name: "عيد الفطر المبارك (تقديراً)", week: 0 },
  { date: "2027-03-14", day: "الأحد - الخميس", name: "الأسبوع 3", week: 3 },
  { date: "2027-03-21", day: "الأحد - الخميس", name: "الأسبوع 4", week: 4 },
  { date: "2027-03-28", day: "الأحد - الخميس", name: "الأسبوع 5", week: 5 },
  { date: "2027-04-04", day: "الأحد - الخميس", name: "الأسبوع 6", week: 6 },
  { date: "2027-04-11", day: "الأحد - الخميس", name: "الأسبوع 7", week: 7 },
  { date: "2027-04-18", day: "الأحد", name: "بدء التسجيل على مواد الفصل الصيفي", week: 0 },
  { date: "2027-04-18", day: "الأحد - الخميس", name: "الأسبوع 8", week: 8 },
  { date: "2027-04-25", day: "الأحد - الخميس", name: "فترة الامتحانات الفصلية لمواد الفصل الثاني", week: 9 },
  { date: "2027-04-26", day: "الإثنين", name: "الامتحان الشامل (الجلسة الأولى)", week: 0 },
  { date: "2027-04-28", day: "الأربعاء", name: "الامتحان الشامل (الجلسة الثانية)", week: 0 },
  { date: "2027-05-01", day: "السبت", name: "عيد العمال", week: 0 },
  { date: "2027-05-02", day: "الأحد - الخميس", name: "الأسبوع 10", week: 10 },
  { date: "2027-05-09", day: "الأحد - الخميس", name: "الأسبوع 11", week: 11 },
  { date: "2027-05-16", day: "السبت - الخميس", name: "عطلة عيد الأضحى المبارك (تقديراً)", week: 0 },
  { date: "2027-05-23", day: "الأحد - الخميس", name: "الأسبوع 12", week: 12 },
  { date: "2027-05-25", day: "الإثنين", name: "عيد الاستقلال", week: 0 },
  { date: "2027-06-06", day: "الأحد", name: "رأس السنة الهجرية (تقديراً)", week: 0 },
  { date: "2027-06-09", day: "الأربعاء", name: "عيد جلوس جلالة الملك عبدالله الثاني المعظم (بدون تعطيل)", week: 0 },
  { date: "2027-06-10", day: "الخميس", name: "عيد الجيش والثورة العربية الكبرى (بدون تعطيل)", week: 0 },
  { date: "2027-06-10", day: "الخميس", name: "انتهاء فترة الانسحاب من مواد الفصل الثاني", week: 0 },
  { date: "2027-06-17", day: "الخميس", name: "آخر موعد لمناقشة رسائل الماجستير", week: 0 },
  { date: "2027-06-19", day: "السبت - الخميس", name: "فترة الامتحانات النهائية للفصل الثاني", week: 0 },
  { date: "2027-07-04", day: "الأحد", name: "بدء إجازة الطلبة", week: 0 },
  { date: "2027-07-04", day: "الأحد", name: "آخر موعد لتقديم نتائج الفصل الثاني", week: 0 },
  { date: "2027-07-08", day: "الخميس", name: "آخر موعد لتسليم رسائل الماجستير لعمادة الدراسات العليا", week: 0 },
  { date: "2027-07-08", day: "الخميس", name: "آخر موعد لتقديم تنسيبات مجالس الكليات منح درجة البكالوريوس والماجستير والدبلوم للطلبة الخريجين في الفصل الثاني", week: 0 },
  { date: "2027-07-12", day: "الإثنين", name: "قرار مجلس العمداء منح درجة البكالوريوس والماجستير للطلبة الخريجين للفصل الثاني", week: 0 },
  { date: "2027-07-14", day: "الأحد", name: "تنظيم إجازة أعضاء هيئة التدريس", week: 0 },

  // ===== الفصل الصيفي 2026/2027 =====
  { date: "2027-07-11", day: "الأحد - الخميس", name: "الفترة الثانية للإرشاد والتسجيل والانسحاب والإضافة لمواد الفصل الصيفي", week: 0 },
  { date: "2027-07-18", day: "الأحد - الخميس", name: "بدء الدراسة في الفصل الصيفي", week: 1 },
  { date: "2027-07-18", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الأولى (50 دينار)", week: 1 },
  { date: "2027-07-20", day: "الثلاثاء", name: "انتهاء فترة الانسحاب والإضافة للفصل الصيفي", week: 1 },
  { date: "2027-07-25", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الثانية (100 دينار)", week: 2 },
  { date: "2027-08-01", day: "الأحد", name: "بدء التسجيل على مواد الفصل الأول 2027/2028", week: 0 },
  { date: "2027-08-08", day: "الأحد - الخميس", name: "الأسبوع 3", week: 3 },
  { date: "2027-08-14", day: "السبت", name: "عيد المولد النبوي الشريف (تقديراً)", week: 0 },
  { date: "2027-08-15", day: "الأحد - الخميس", name: "فترة الامتحانات الفصلية للفصل الصيفي", week: 4 },
  { date: "2027-08-22", day: "الأحد - الخميس", name: "الأسبوع 5", week: 5 },
  { date: "2027-08-26", day: "الخميس", name: "انتهاء فترة الانسحاب من مواد الفصل الصيفي", week: 0 },
  { date: "2027-08-29", day: "الأحد - الخميس", name: "الأسبوع 6", week: 6 },
  { date: "2027-09-02", day: "الخميس", name: "آخر موعد لمناقشة رسائل الماجستير", week: 0 },
  { date: "2027-09-06", day: "الإثنين - الخميس", name: "فترة الامتحانات النهائية للفصل الصيفي", week: 0 },
  { date: "2027-09-12", day: "الأحد", name: "آخر موعد لتقديم نتائج الفصل الصيفي", week: 0 },
  { date: "2027-09-16", day: "الخميس", name: "آخر موعد لتقديم تنسيبات مجالس الكليات منح درجة البكالوريوس والماجستير والدبلوم للطلبة الخريجين في الفصل الصيفي", week: 0 },
  { date: "2027-09-16", day: "الخميس", name: "آخر موعد لتسليم رسائل الماجستير لعمادة الدراسات العليا والبحث العلمي", week: 0 },
  { date: "2027-09-19", day: "الأحد", name: "قرار مجلس العمداء منح درجة البكالوريوس والماجستير والدبلوم للطلبة الخريجين على الفصل الصيفي", week: 0 },
  { date: "2027-09-20", day: "الإثنين", name: "تنظيم إجازة أعضاء هيئة التدريس", week: 0 },

  // ===== الفصل الأول 2027/2028 =====
  { date: "2027-10-03", day: "الأحد", name: "بدء العام الجامعي وبدء دوام أعضاء هيئة التدريس", week: 0 },
  { date: "2027-10-10", day: "الأحد", name: "بدء الدراسة في الفصل الأول 2027/2028", week: 1 },
];

const eventsBody = document.getElementById("events-body");
let updateIntervals = [];

function formatCountdown(timeDifference) {
  if (timeDifference <= 0) {
    return '✅ Ended';
  }

  const days = Math.floor(timeDifference / (1000 * 60 * 60 * 24));
  const hours = Math.floor((timeDifference % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
  const minutes = Math.floor((timeDifference % (1000 * 60 * 60)) / (1000 * 60));
  const seconds = Math.floor((timeDifference % (1000 * 60)) / 1000);

  // Build parts in order: Days → Hours → Minutes → Seconds
  let parts = [];
  
  if (days > 0) {
    parts.push(`${days}d`);
  }
  
  if (hours > 0 || days > 0) {
    parts.push(`${String(hours).padStart(2, '0')}h`);
  }
  
  if (minutes > 0 || hours > 0 || days > 0) {
    parts.push(`${String(minutes).padStart(2, '0')}m`);
  }
  
  parts.push(`${String(seconds).padStart(2, '0')}s`);
  
  return parts.join(' ');
}

function updateCountdown(row, event) {
  const now = new Date();
  const eventDate = new Date(event.date);
  const timeDifference = eventDate - now;

  const countdownEl = row.querySelector(".countdown");
  if (countdownEl) {
    countdownEl.innerHTML = formatCountdown(timeDifference);
    if (timeDifference <= 0) {
      countdownEl.style.color = '#2e7d32';
    } else {
      countdownEl.style.color = '#8b1e3f';
    }
  }
}

function createSeasonDivider(seasonName) {
  const row = document.createElement("tr");
  row.className = "season-divider";
  row.innerHTML = `
    <td colspan="5">✦ ${seasonName} ✦</td>
  `;
  return row;
}

function getWeekDisplay(week) {
  if (week === 0) return '-';
  return `الأسبوع ${week}`;
}

function initialize() {
  const seasonNames = [
    "الفصل الأول 2026/2027",
    "الفصل الثاني 2026/2027",
    "الفصل الصيفي 2026/2027",
    "الفصل الأول 2027/2028"
  ];
  
  // Find season boundaries based on keywords
  const boundaries = [];
  let seasonIndex = 0;
  
  events.forEach((event, index) => {
    if (event.name.includes("بدء الدراسة في الفصل الأول") && !event.name.includes("2027/2028")) {
      if (seasonIndex === 0) boundaries.push(index);
    } else if (event.name.includes("بدء الدراسة في الفصل الثاني")) {
      if (seasonIndex === 1) boundaries.push(index);
    } else if (event.name.includes("بدء الدراسة في الفصل الصيفي")) {
      if (seasonIndex === 2) boundaries.push(index);
    } else if (event.name.includes("بدء الدراسة في الفصل الأول 2027/2028")) {
      if (seasonIndex === 3) boundaries.push(index);
    }
  });

  // If boundaries not found, use hardcoded indices
  if (boundaries.length < 4) {
    boundaries.length = 0;
    boundaries.push(0, 29, 61, 83);
  }

  events.forEach((event, index) => {
    // Insert season divider
    if (boundaries.includes(index)) {
      const seasonIdx = boundaries.indexOf(index);
      const seasonName = seasonNames[seasonIdx] || `الفصل ${seasonIdx + 1}`;
      const divider = createSeasonDivider(seasonName);
      eventsBody.appendChild(divider);
    }

    const row = document.createElement("tr");
    const weekDisplay = getWeekDisplay(event.week || 0);
    
    row.innerHTML = `
      <td>${event.date}</td>
      <td>${event.day}</td>
      <td>${weekDisplay}</td>
      <td>${event.name}</td>
      <td class="countdown">--:--:--</td>
    `;
    eventsBody.appendChild(row);

    // Initial update
    updateCountdown(row, event);
    
    // Store interval reference for cleanup
    const intervalId = setInterval(() => updateCountdown(row, event), 1000);
    updateIntervals.push(intervalId);
  });
}

// Cleanup intervals on page unload
window.addEventListener('beforeunload', () => {
  updateIntervals.forEach(interval => clearInterval(interval));
  updateIntervals = [];
});

// Wait for DOM to load
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initialize);
} else {
  initialize();
}
