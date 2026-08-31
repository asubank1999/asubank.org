const events = [
  // ===== الفصل الأول 2026/2027 =====
  { date: "2026-10-04", day: "الأحد - الخميس", name: "بدء العام الجامعي 2026/2027 وبدء دوام أعضاء هيئة التدريس" },
  { date: "2026-10-04", day: "الأحد - الخميس", name: "الفترة الثانية للإرشاد والتسجيل والانسحاب والإضافة لمواد الفصل الأول 2026/2027" },
  { date: "2026-10-11", day: "الأحد - الخميس", name: "بدء الدراسة في الفصل الأول 2026/2027" },
  { date: "2026-10-11", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الأولى (50 دينار)" },
  { date: "2026-10-15", day: "الأحد - الخميس", name: "انتهاء فترة الانسحاب والإضافة للفصل الأول 2026/2027" },
  { date: "2026-10-18", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الثانية (100 دينار)" },
  { date: "2026-12-10", day: "الأحد - الخميس", name: "فترة الامتحانات الفصلية للفصل الأول 2026/2027" },
  { date: "2026-12-16", day: "الاثنين", name: "بدء التسجيل على مواد الفصل الثاني 2026/2027" },
  { date: "2026-12-16", day: "الأربعاء", name: "الامتحان الشامل (الجلسة الأولى)" },
  { date: "2026-12-16", day: "الأربعاء", name: "الامتحان الشامل (الجلسة الثانية)" },
  { date: "2026-12-25", day: "الجمعة", name: "عيد الميلاد المجيد" },
  { date: "2027-01-01", day: "الجمعة", name: "رأس السنة الميلادية" },
  { date: "2027-01-05", day: "الثلاثاء", name: "عيد الإسراء والمعراج الشريف (بدون تعطيل)" },
  { date: "2027-01-14", day: "الخميس", name: "انتهاء فترة الانسحاب من مواد الفصل الأول 2026/2027" },
  { date: "2027-01-21", day: "الخميس", name: "آخر موعد لمناقشة رسائل الماجستير" },
  { date: "2027-02-04", day: "السبت - الخميس", name: "فترة الامتحانات النهائية للفصل الأول 2026/2027" },
  { date: "2027-02-07", day: "الأحد", name: "عيد ميلاد جلالة الملك عبدالله الثاني المعظم" },
  { date: "2027-02-07", day: "الأحد", name: "بدء إجازة الطلبة" },
  { date: "2027-02-14", day: "الأحد", name: "آخر موعد لتقديم نتائج الفصل الأول 2026/2027" },
  { date: "2027-02-14", day: "الأحد", name: "آخر موعد لتسليم رسائل الماجستير لعمادة الدراسات العليا" },
  { date: "2027-02-14", day: "الأحد", name: "آخر موعد لتقديم تنسيبات مجالس الكليات منح درجة البكالوريوس والماجستير والدبلوم للطلبة الخريجين في الفصل الأول 2026/2027" },
  { date: "2027-02-17", day: "الأربعاء", name: "قرار مجلس العمداء منح درجة البكالوريوس والماجستير والدبلوم للطلبة الخريجين في الفصل الأول 2026/2027" },

  // ===== الفصل الثاني 2026/2027 =====
  { date: "2027-02-21", day: "الأحد", name: "الفترة الثانية للإرشاد والتسجيل والانسحاب والإضافة لمواد الفصل الثاني 2026/2027" },
  { date: "2027-02-28", day: "الأحد - الخميس", name: "بدء الدراسة في الفصل الثاني 2026/2027" },
  { date: "2027-03-04", day: "الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الأولى (50 دينار)" },
  { date: "2027-03-04", day: "الخميس", name: "انتهاء فترة الانسحاب والإضافة للفصل الثاني 2026/2027" },
  { date: "2027-03-11", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الثانية (100 دينار)" },
  { date: "2027-03-12", day: "الثلاثاء - الجمعة", name: "عيد الفطر المبارك (تقديراً)" },
  { date: "2027-04-18", day: "الأحد", name: "بدء التسجيل على مواد الفصل الصيفي 2026/2027" },
  { date: "2027-04-26", day: "الإثنين", name: "الامتحان الشامل (الجلسة الأولى)" },
  { date: "2027-04-28", day: "الأربعاء", name: "الامتحان الشامل (الجلسة الثانية)" },
  { date: "2027-05-01", day: "السبت", name: "عيد العمال" },
  { date: "2027-05-06", day: "الأحد - الخميس", name: "فترة الامتحانات الفصلية لمواد الفصل الثاني 2026/2027" },
  { date: "2027-05-16", day: "السبت - الخميس", name: "عطلة عيد الأضحى المبارك (تقديراً)" },
  { date: "2027-05-25", day: "الإثنين", name: "عيد الاستقلال" },
  { date: "2027-06-06", day: "الأحد", name: "رأس السنة الهجرية (تقديراً)" },
  { date: "2027-06-09", day: "الأربعاء", name: "عيد جلوس جلالة الملك عبدالله الثاني المعظم (بدون تعطيل)" },
  { date: "2027-06-10", day: "الخميس", name: "عيد الجيش والثورة العربية الكبرى (بدون تعطيل)" },
  { date: "2027-06-10", day: "الخميس", name: "انتهاء فترة الانسحاب من مواد الفصل الثاني 2026/2027" },
  { date: "2027-06-17", day: "الخميس", name: "آخر موعد لمناقشة رسائل الماجستير" },
  { date: "2027-07-01", day: "السبت - الخميس", name: "فترة الامتحانات النهائية للفصل الثاني 2026/2027" },
  { date: "2027-07-04", day: "الأحد", name: "بدء إجازة الطلبة" },
  { date: "2027-07-08", day: "الخميس", name: "آخر موعد لتقديم نتائج الفصل الثاني 2026/2027" },
  { date: "2027-07-08", day: "الخميس", name: "آخر موعد لتسليم رسائل الماجستير لعمادة الدراسات العليا" },
  { date: "2027-07-08", day: "الخميس", name: "آخر موعد لتقديم تنسيبات مجالس الكليات منح درجة البكالوريوس والماجستير والدبلوم للطلبة الخريجين في الفصل الثاني 2026/2027" },
  { date: "2027-07-12", day: "الإثنين", name: "قرار مجلس العمداء منح درجة البكالوريوس والماجستير للطلبة الخريجين للفصل الثاني 2026/2027" },
  { date: "2027-07-14", day: "الأحد", name: "تنظيم إجازة أعضاء هيئة التدريس بالتنسيق مع رؤوساء الأقسام والعمداء وفقاً لمتطلبات العمل" },

  // ===== الفصل الصيفي 2026/2027 =====
  { date: "2027-07-15", day: "الأحد - الخميس", name: "الفترة الثانية للإرشاد والتسجيل والانسحاب والإضافة لمواد الفصل الصيفي 2026/2027" },
  { date: "2027-07-18", day: "الأحد - الخميس", name: "بدء الدراسة في الفصل الصيفي 2026/2027" },
  { date: "2027-07-18", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الأولى (50 دينار)" },
  { date: "2027-07-20", day: "الثلاثاء", name: "انتهاء فترة الانسحاب والإضافة للفصل الصيفي 2026/2027" },
  { date: "2027-07-25", day: "الأحد - الخميس", name: "غرامة التسجيل المتأخر للطلبة القدامى الفترة الثانية (100 دينار)" },
  { date: "2027-08-01", day: "الأحد", name: "بدء التسجيل على مواد الفصل الأول 2027/2028" },
  { date: "2027-08-15", day: "الأحد - الخميس", name: "فترة الامتحانات الفصلية للفصل الصيفي 2026/2027" },
  { date: "2027-08-26", day: "الخميس", name: "انتهاء فترة الانسحاب من مواد الفصل الصيفي 2026/2027" },
  { date: "2027-09-02", day: "الخميس", name: "آخر موعد لمناقشة رسائل الماجستير" },
  { date: "2027-09-06", day: "الإثنين - الخميس", name: "فترة الامتحانات النهائية للفصل الصيفي 2026/2027" },
  { date: "2027-09-12", day: "الأحد", name: "آخر موعد لتقديم نتائج الفصل الصيفي 2026/2027" },
  { date: "2027-09-14", day: "السبت", name: "عيد المولد النبوي الشريف (تقديراً)" },
  { date: "2027-09-16", day: "الخميس", name: "آخر موعد لتقديم تنسيبات مجالس الكليات منح درجة البكالوريوس والماجستير والدبلوم للطلبة الخريجين في الفصل الصيفي 2026/2027" },
  { date: "2027-09-16", day: "الخميس", name: "آخر موعد لتسليم رسائل الماجستير لعمادة الدراسات العليا والبحث العلمي" },
  { date: "2027-09-20", day: "الإثنين", name: "قرار مجلس العمداء منح درجة البكالوريوس والماجستير والدبلوم للطلبة الخريجين على الفصل الصيفي 2026/2027" },
  { date: "2027-09-20", day: "الإثنين", name: "تنظيم إجازة أعضاء هيئة التدريس بالتنسيق مع رؤوساء الأقسام والعمداء وفقاً لمتطلبات العمل" },

  // ===== الفصل الأول 2027/2028 =====
  { date: "2027-10-03", day: "الأحد", name: "بدء العام الجامعي وبدء دوام أعضاء هيئة التدريس" },
  { date: "2027-10-10", day: "الأحد", name: "بدء الدراسة في الفصل الأول 2027/2028" },
];

const eventsBody = document.getElementById("events-body");

function updateCountdown(row, event) {
  const now = new Date();
  const eventDate = new Date(event.date);
  const timeDifference = eventDate - now;

  if (timeDifference <= 0) {
    row.querySelector(".countdown").textContent = "✅ انتهى";
    row.querySelector(".countdown").style.color = "#2e7d32";
    return;
  }

  const days = Math.floor(timeDifference / (1000 * 60 * 60 * 24));
  const hours = Math.floor((timeDifference % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
  const minutes = Math.floor((timeDifference % (1000 * 60 * 60)) / (1000 * 60));
  const seconds = Math.floor((timeDifference % (1000 * 60)) / 1000);

  const countdownEl = row.querySelector(".countdown");
  countdownEl.textContent = `${days} يوم ${hours} ساعة ${minutes} دقيقة ${seconds} ثانية`;
  countdownEl.style.color = "#8b1e3f";
}

function createSeasonDivider(seasonName) {
  const row = document.createElement("tr");
  row.className = "season-divider";
  row.innerHTML = `
    <td colspan="4">✦ ${seasonName} ✦</td>
  `;
  return row;
}

function initialize() {
  const seasonNames = ["الفصل الأول 2026/2027", "الفصل الثاني 2026/2027", "الفصل الصيفي 2026/2027", "الفصل الأول 2027/2028"];
  let seasonIndex = 0;
  let seasonStartIndex = 0;

  // Find season start indices
  const seasonStarts = [];
  events.forEach((event, index) => {
    if (event.name.includes(seasonNames[seasonIndex]) && seasonIndex < seasonNames.length) {
      seasonStarts.push(index);
      seasonIndex++;
    }
  });

  // If season names aren't in the events, use known boundaries
  if (seasonStarts.length === 0) {
    // Hardcoded boundaries based on the image data
    const boundaries = [0, 22, 44, 67];
    boundaries.forEach((start, idx) => {
      if (start < events.length) {
        seasonStarts.push(start);
      }
    });
  }

  events.forEach((event, index) => {
    // Insert season divider
    if (seasonStarts.includes(index)) {
      const seasonIdx = seasonStarts.indexOf(index);
      let seasonName = seasonNames[seasonIdx] || `الفصل ${seasonIdx + 1}`;
      const divider = createSeasonDivider(seasonName);
      eventsBody.appendChild(divider);
    }

    const row = document.createElement("tr");
    row.innerHTML = `
      <td>${event.date}</td>
      <td>${event.day}</td>
      <td>${event.name}</td>
      <td class="countdown">--:--:--</td>
    `;
    eventsBody.appendChild(row);

    // Update countdown every second
    setInterval(() => updateCountdown(row, event), 1000);
  });

  // Force immediate update
  const rows = eventsBody.querySelectorAll("tr:not(.season-divider)");
  rows.forEach((row, index) => {
    const eventIndex = seasonStarts.reduce((acc, start) => {
      return start <= index ? acc + 1 : acc;
    }, 0);
    // Find the event for this row
    let eventIdx = index;
    for (let i = 0; i < seasonStarts.length; i++) {
      if (index >= seasonStarts[i]) {
        eventIdx = index + i; // Adjust for dividers
      }
    }
    // Actually, simpler: use the events array directly
    const actualIndex = index - seasonStarts.filter(s => s <= index).length;
    if (actualIndex < events.length && events[actualIndex]) {
      updateCountdown(row, events[actualIndex]);
    }
  });
}

// Wait for DOM to load
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initialize);
} else {
  initialize();
}