/**
 * ASU Bank accounts, chat and admin API (used by asubank.org, the iOS app and the Android app).
 *
 * Setup (once):
 *  1. https://script.google.com > New project, name it "ASU Bank Accounts", paste this file.
 *  2. Run `setup` once from the editor and approve the permissions. It creates the "ASU Bank Accounts"
 *     spreadsheet in your Drive (users, sessions, messages, rooms, log) and remembers it.
 *  3. Deploy > New deployment > Web app: Execute as "Me", Who has access "Anyone". Put the /exec URL in
 *     assets/app/app-config.json ("accountsApi") so the site and both apps pick it up.
 *  After editing, Deploy > Manage deployments > edit > New version, so the URL stays the same.
 *  New columns and sheets are added by themselves on the first request after an update.
 *
 * Every call is a POST with Content-Type text/plain (no CORS preflight) and a JSON body
 *   { action, token?, ...fields }  ->  { ok: true, ... } or { ok: false, error }
 *
 * Admins: the account whose username, email or university ID is in ADMINS becomes an admin. Signing
 * up with one of those needs a code that is emailed to OWNER_EMAIL, so nobody else can take them.
 */
var ADMINS = ['alan144', 'necro144@yahoo.com', '202120132'];
var OWNER_EMAIL = 'necro144@yahoo.com';
var UPLOAD_FOLDER_ID = '1ubxr47KfFwm7ocn0ikCpJLp79-dxe8aB';
var SCHEMA = 3;
/** The rooms the Rooms sheet starts with; after that admins manage rooms from the apps */
var DEFAULT_ROOMS = [
  { id: 'general', name: 'General', emoji: '💬', about: 'Say hi, ask anything about ASU' },
  { id: 'help', name: 'Study help', emoji: '🆘', about: 'Stuck on something? Ask other students' },
  { id: 'materials', name: 'Materials', emoji: '📚', about: 'Ask for or share study files' },
  { id: 'medical', name: 'Pharmacy & health', emoji: '💊', about: 'Pharmacy, nutrition, physiotherapy' },
  { id: 'tech', name: 'IT & engineering', emoji: '💻', about: 'IT, computer science, engineering' },
  { id: 'business', name: 'Business & arts', emoji: '📈', about: 'Business, finance, law, arts' },
  { id: 'announcements', name: 'Announcements', emoji: '📣', about: 'News from the ASU Bank team', adminOnly: true }
];
/** Badges an admin can give; the apps and the site show them next to names */
var BADGES = {
  founder: { name: 'Founder', emoji: '👑' },
  moderator: { name: 'Moderator', emoji: '🛡️' },
  support: { name: 'Support', emoji: '🛟' },
  helper: { name: 'Helper', emoji: '🤝' },
  donor: { name: 'Donor', emoji: '💛' },
  contributor: { name: 'Contributor', emoji: '📚' },
  tutor: { name: 'Tutor', emoji: '🎓' },
  verified: { name: 'Verified', emoji: '✅' },
  vip: { name: 'VIP', emoji: '⭐' },
  developer: { name: 'Developer', emoji: '💻' },
  team: { name: 'ASU Bank team', emoji: '🏦' },
  mentor: { name: 'Mentor', emoji: '🧭' },
  ambassador: { name: 'Ambassador', emoji: '📣' },
  top_student: { name: 'Top student', emoji: '🏅' },
  class_rep: { name: 'Class rep', emoji: '🙋' },
  translator: { name: 'Translator', emoji: '🌐' },
  designer: { name: 'Designer', emoji: '🎨' },
  researcher: { name: 'Researcher', emoji: '🔬' },
  organizer: { name: 'Organizer', emoji: '🎉' },
  early_supporter: { name: 'Early supporter', emoji: '🌱' },
  sponsor: { name: 'Sponsor', emoji: '💎' },
  legend: { name: 'Legend', emoji: '🔥' }
};
var SESSION_DAYS = 120;
var MAX_TEXT = 1000;
var CACHE_MSGS = 150;
var ONLINE_MS = 3 * 60000;

var SHEETS = {
  Users: ['id', 'username', 'email', 'uniId', 'displayName', 'salt', 'hash', 'role', 'banned', 'banReason', 'created', 'lastSeen', 'color', 'mustReset',
          'avatar', 'phone', 'showPhone', 'bio', 'badges', 'major', 'college'],
  Sessions: ['token', 'userId', 'created', 'lastUsed', 'platform'],
  Messages: ['id', 'room', 'userId', 'username', 'displayName', 'role', 'text', 'created', 'deleted', 'replyTo', 'color', 'edited'],
  Rooms: ['id', 'name', 'emoji', 'about', 'adminOnly', 'locked', 'archived', 'pinned', 'order', 'created'],
  Log: ['time', 'adminId', 'action', 'target', 'detail']
};

// MARK: - entry points

function doPost(e) {
  var body;
  try { body = JSON.parse(e.postData.contents || '{}'); } catch (err) { return out({ ok: false, error: 'Bad request.' }); }
  try {
    ensureSchema();
    var fn = ACTIONS[body.action];
    if (!fn) return out({ ok: false, error: 'Unknown action.' });
    return out(fn(body));
  } catch (err) {
    return out({ ok: false, error: err.userMessage || ('Something went wrong: ' + err.message) });
  }
}

function doGet(e) {
  // read-only status for health checks
  return out({ ok: true, service: 'asubank-accounts', schema: SCHEMA });
}

function out(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }

function fail(msg) { var e = new Error(msg); e.userMessage = msg; throw e; }

// Run once from the editor
function setup() {
  var p = PropertiesService.getScriptProperties();
  var id = p.getProperty('SHEET_ID');
  var ss = id ? SpreadsheetApp.openById(id) : SpreadsheetApp.create('ASU Bank Accounts');
  p.setProperty('SHEET_ID', ss.getId());
  p.deleteProperty('SCHEMA');
  ensureSchema();
  var first = ss.getSheetByName('Sheet1');
  if (first && ss.getSheets().length > 1) ss.deleteSheet(first);
  DriveApp.getFolderById(UPLOAD_FOLDER_ID).getName(); // asks for Drive access now
  MailApp.getRemainingDailyQuota(); // asks for email access now
  Logger.log('Ready: ' + ss.getUrl());
}

/** Adds sheets and columns that a newer version of this script needs (once per version) */
function ensureSchema() {
  var p = PropertiesService.getScriptProperties();
  if (Number(p.getProperty('SCHEMA') || 0) >= SCHEMA) return;
  withLock(function () {
    var ss = book();
    Object.keys(SHEETS).forEach(function (name) {
      var sh = ss.getSheetByName(name) || ss.insertSheet(name);
      var head = SHEETS[name];
      if (sh.getLastRow() === 0) { sh.appendRow(head); sh.setFrozenRows(1); }
      else if (sh.getLastColumn() < head.length) sh.getRange(1, 1, 1, head.length).setValues([head]);
    });
    if (rows('Rooms').length === 0) {
      DEFAULT_ROOMS.forEach(function (r, i) {
        append('Rooms', { id: r.id, name: r.name, emoji: r.emoji, about: r.about, adminOnly: !!r.adminOnly, locked: false, archived: false,
                          pinned: '', order: i, created: new Date().toISOString() });
      });
    }
    p.setProperty('SCHEMA', String(SCHEMA));
  });
}

// MARK: - storage

function book() { return SpreadsheetApp.openById(PropertiesService.getScriptProperties().getProperty('SHEET_ID')); }
function sheet(name) { return book().getSheetByName(name); }

function rows(name) {
  var sh = sheet(name);
  var n = sh.getLastRow();
  if (n < 2) return [];
  var head = SHEETS[name];
  return sh.getRange(2, 1, n - 1, head.length).getValues().map(function (r, i) {
    var o = { _row: i + 2 };
    head.forEach(function (h, j) { o[h] = r[j]; });
    return o;
  });
}

function append(name, obj) {
  sheet(name).appendRow(SHEETS[name].map(function (h) { return obj[h] === undefined ? '' : obj[h]; }));
}

function update(name, row, obj) {
  var head = SHEETS[name];
  var sh = sheet(name);
  Object.keys(obj).forEach(function (k) {
    var j = head.indexOf(k);
    if (j >= 0) sh.getRange(row, j + 1).setValue(obj[k]);
  });
}

function withLock(fn) {
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try { return fn(); } finally { lock.releaseLock(); }
}

function log(admin, action, target, detail) {
  append('Log', { time: new Date().toISOString(), adminId: admin.id, action: action, target: target || '', detail: detail || '' });
}

function truthy(v) { return v === true || v === 'TRUE' || v === 'true'; }

// MARK: - users and passwords

function norm(s) { return String(s || '').trim().toLowerCase(); }

function hashPassword(pw, salt) {
  var h = String(pw);
  for (var i = 0; i < 300; i++) {
    h = Utilities.base64Encode(Utilities.computeHmacSha256Signature(h, salt + ':' + i));
  }
  return h;
}

function newId() { return Utilities.getUuid().replace(/-/g, '').slice(0, 16); }

function isAdminIdentity(u) {
  return ADMINS.indexOf(norm(u.username)) >= 0 || ADMINS.indexOf(norm(u.email)) >= 0 || ADMINS.indexOf(norm(u.uniId)) >= 0;
}

var COLORS = ['#8C1D40', '#0a84ff', '#1f8a3e', '#af52de', '#e8590c', '#ff2d55', '#5e5ce6', '#00a3a3'];

function badgeList(u) {
  return String(u.badges || '').split(',').map(function (s) { return s.trim(); }).filter(function (s) { return BADGES[s]; });
}

/** What everyone may see about a user; `full` adds the private fields (the user themself and admins) */
function publicUser(u, full) {
  var o = { id: u.id, username: u.username, displayName: u.displayName || u.username, role: u.role || 'user', color: u.color || '#8C1D40',
            avatar: u.avatar || '', bio: u.bio || '', major: u.major || '', college: u.college || '', badges: badgeList(u), lastSeen: lastActive(u) };
  o.online = !!o.lastSeen && (new Date() - new Date(o.lastSeen)) < ONLINE_MS;
  if (truthy(u.showPhone)) o.phone = u.phone ? String(u.phone) : '';
  if (full) {
    o.email = u.email || ''; o.uniId = u.uniId ? String(u.uniId) : '';
    o.phone = u.phone ? String(u.phone) : ''; o.showPhone = truthy(u.showPhone);
    o.banned = truthy(u.banned); o.banReason = u.banReason || '';
    o.created = u.created; o.mustReset = truthy(u.mustReset);
  }
  return o;
}

function findUser(login) {
  var k = norm(login);
  if (!k) return null;
  return rows('Users').filter(function (u) {
    return norm(u.username) === k || (u.email && norm(u.email) === k) || (u.uniId && norm(u.uniId) === k);
  })[0] || null;
}

function userById(id) { return rows('Users').filter(function (u) { return u.id === id; })[0] || null; }

function validUsername(s) { return /^[a-zA-Z0-9_.]{3,20}$/.test(s); }
function validEmail(s) { return !s || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(s); }
function validUniId(s) { return !s || /^\d{6,12}$/.test(s); }
function validPhone(s) { return !s || /^\+?[0-9 ]{7,18}$/.test(s); }
function validPassword(s) { return String(s || '').length >= 6 && String(s).length <= 100; }

function tooMany(key, max) {
  var c = CacheService.getScriptCache();
  var n = Number(c.get(key) || 0) + 1;
  c.put(key, String(n), 900);
  return n > max;
}

// MARK: - presence (who is online)

function presence() { return JSON.parse(CacheService.getScriptCache().get('presence') || '{}'); }

/** Marks a user as active now (at most every 30 seconds per user) */
function touch(u) {
  var c = CacheService.getScriptCache();
  if (c.get('t:' + u.id)) return;
  c.put('t:' + u.id, '1', 30);
  var p = presence();
  var now = Date.now();
  p[u.id] = new Date(now).toISOString();
  Object.keys(p).forEach(function (k) { if (now - new Date(p[k]) > 864e5) delete p[k]; });
  c.put('presence', JSON.stringify(p), 21600);
}

function lastActive(u) {
  var p = presence()[u.id];
  var s = u.lastSeen ? (u.lastSeen instanceof Date ? u.lastSeen.toISOString() : String(u.lastSeen)) : '';
  return p && p > s ? p : s;
}

// MARK: - sessions

function newSession(u, platform) {
  var token = Utilities.getUuid() + Utilities.getUuid().replace(/-/g, '');
  var now = new Date().toISOString();
  append('Sessions', { token: token, userId: u.id, created: now, lastUsed: now, platform: String(platform || '').slice(0, 20) });
  return token;
}

/** The signed-in user for a request (cached for a minute), or throws */
function auth(b, opts) {
  var t = String(b.token || '');
  if (t.length < 30) fail('Please sign in.');
  var cache = CacheService.getScriptCache();
  var cached = cache.get('s:' + t);
  var u = null;
  if (cached) u = JSON.parse(cached);
  else {
    var s = rows('Sessions').filter(function (x) { return x.token === t; })[0];
    if (!s) fail('Your session has expired. Please sign in again.');
    if (new Date() - new Date(s.created) > SESSION_DAYS * 864e5) fail('Your session has expired. Please sign in again.');
    var full = userById(s.userId);
    if (!full) fail('This account no longer exists.');
    u = { id: full.id, username: full.username, displayName: full.displayName, role: full.role, color: full.color,
          banned: truthy(full.banned), banReason: full.banReason, _row: full._row };
    cache.put('s:' + t, JSON.stringify(u), 60);
    if (!full.lastSeen || new Date() - new Date(full.lastSeen) > 10 * 60000) update('Users', full._row, { lastSeen: new Date().toISOString() });
  }
  if (u.banned) fail('This account is banned' + (u.banReason ? ': ' + u.banReason : '.'));
  if (opts && opts.admin && u.role !== 'admin') fail('Only admins can do that.');
  touch(u);
  return u;
}

function dropUserSessions(userId, except) {
  var sh = sheet('Sessions');
  var cache = CacheService.getScriptCache();
  rows('Sessions').filter(function (s) { return s.userId === userId && s.token !== except; }).reverse().forEach(function (s) {
    cache.remove('s:' + s.token);
    sh.deleteRow(s._row);
  });
}

/** Clears cached copies of a user after their profile, role or ban changed */
function forgetUser(userId) {
  var cache = CacheService.getScriptCache();
  rows('Sessions').filter(function (s) { return s.userId === userId; }).forEach(function (s) { cache.remove('s:' + s.token); });
  cache.remove('people');
}

/** Avatars, badges and roles of everyone, for chat screens (cached for 5 minutes) */
function people() {
  var c = CacheService.getScriptCache();
  var j = c.get('people');
  if (j) return JSON.parse(j);
  var map = {};
  rows('Users').forEach(function (u) {
    map[u.id] = { avatar: u.avatar || '', badges: badgeList(u), role: u.role || 'user', displayName: u.displayName || u.username, color: u.color || '' };
  });
  c.put('people', JSON.stringify(map), 300);
  return map;
}

function peopleFor(ids) {
  var all = people(), p = presence(), now = Date.now(), outp = {};
  ids.forEach(function (id) {
    if (!all[id]) return;
    var o = JSON.parse(JSON.stringify(all[id]));
    o.online = !!p[id] && now - new Date(p[id]) < ONLINE_MS;
    o.lastSeen = p[id] || '';
    outp[id] = o;
  });
  return outp;
}

// MARK: - rooms

function allRooms() {
  var c = CacheService.getScriptCache();
  var j = c.get('rooms');
  if (j) return JSON.parse(j);
  var list = rows('Rooms').map(function (r) {
    return { id: String(r.id), name: r.name, emoji: r.emoji || '💬', about: r.about || '', adminOnly: truthy(r.adminOnly),
             locked: truthy(r.locked), archived: truthy(r.archived), pinned: r.pinned || '', order: Number(r.order) || 0 };
  });
  list.sort(function (a, b) { return a.order - b.order; });
  c.put('rooms', JSON.stringify(list), 21600);
  return list;
}

/** Archived rooms are hidden from students; admins still see them */
function publicRooms(u) {
  var admin = u && u.role === 'admin';
  return allRooms().filter(function (r) { return admin || !r.archived; });
}

function roomById(id) { return allRooms().filter(function (r) { return r.id === id; })[0] || null; }

// MARK: - chat cache

function roomMessages(room) {
  var cache = CacheService.getScriptCache();
  var c = cache.get('m:' + room);
  if (c) return JSON.parse(c);
  var list = rows('Messages').filter(function (m) { return m.room === room && !truthy(m.deleted); })
    .slice(-CACHE_MSGS).map(publicMessage);
  putRoom(room, list);
  return list;
}

function putRoom(room, list) {
  var json = JSON.stringify(list);
  while (json.length > 95000 && list.length > 10) { list = list.slice(10); json = JSON.stringify(list); }
  CacheService.getScriptCache().put('m:' + room, json, 21600);
}

function publicMessage(m) {
  return { id: m.id, room: m.room, userId: m.userId, username: m.username, displayName: m.displayName || m.username,
           role: m.role || 'user', text: m.text, created: m.created, replyTo: m.replyTo || '', color: m.color || '#8C1D40',
           edited: m.edited ? String(m.edited) : '' };
}

function latestMeta() {
  var c = CacheService.getScriptCache().get('latest');
  return c ? JSON.parse(c) : null;
}

/** Recent things the apps can notify about: messages, replies, announcements and new uploads */
function pushEvent(ev) {
  var c = CacheService.getScriptCache();
  var list = JSON.parse(c.get('events') || '[]');
  list.push(ev);
  c.put('events', JSON.stringify(list.slice(-60)), 21600);
}

/** Looks for new files in the upload folder (at most every 3 minutes) and adds an event for each */
function scanUploads() {
  var c = CacheService.getScriptCache();
  if (c.get('upscan')) return;
  c.put('upscan', '1', 180);
  var seen = JSON.parse(c.get('upseen') || 'null');
  var first = seen === null;
  seen = seen || [];
  var since = new Date(Date.now() - 2 * 864e5).toISOString().slice(0, 19);
  var it = DriveApp.getFolderById(UPLOAD_FOLDER_ID).searchFiles('createdDate > "' + since + '" and trashed = false');
  var fresh = [];
  while (it.hasNext()) {
    var f = it.next();
    if (seen.indexOf(f.getId()) >= 0) continue;
    seen.push(f.getId());
    if (!/upload-test/i.test(f.getName())) fresh.push(f);
  }
  c.put('upseen', JSON.stringify(seen.slice(-300)), 21600);
  if (first) return; // the first scan after a restart only remembers what is there
  fresh.sort(function (a, b) { return a.getDateCreated() - b.getDateCreated(); });
  fresh.forEach(function (f) {
    var ev = { id: 'u' + f.getId().slice(0, 15), type: 'upload', room: '', roomName: 'Uploads', userId: '', displayName: 'ASU Bank',
               text: f.getName().slice(0, 120), url: 'https://drive.google.com/file/d/' + f.getId() + '/view', created: f.getDateCreated().toISOString() };
    pushEvent(ev);
  });
}

// MARK: - avatars

function avatarFolder() {
  var p = PropertiesService.getScriptProperties();
  var id = p.getProperty('AVATAR_FOLDER');
  if (id) { try { return DriveApp.getFolderById(id); } catch (e) {} }
  var f = DriveApp.createFolder('ASU Bank Avatars');
  p.setProperty('AVATAR_FOLDER', f.getId());
  return f;
}

function removeAvatarFile(url) {
  var m = String(url || '').match(/id=([-\w]{20,})/);
  if (!m) return;
  try { DriveApp.getFileById(m[1]).setTrashed(true); } catch (e) {}
}

// MARK: - actions

var ACTIONS = {
  rooms: function (b) {
    var u = null;
    try { if (b.token) u = auth(b); } catch (e) {}
    return { ok: true, rooms: publicRooms(u), badges: BADGES };
  },

  /** Sends the admin sign-up code to the owner's email */
  adminCode: function (b) {
    if (!isAdminIdentity(b)) fail('No code is needed for this account.');
    if (tooMany('code', 5)) fail('Too many codes. Try again in 15 minutes.');
    var code = String(Math.floor(100000 + Math.random() * 900000));
    CacheService.getScriptCache().put('code', code, 900);
    MailApp.sendEmail(OWNER_EMAIL, 'ASU Bank admin code: ' + code,
      'Someone is creating the ASU Bank admin account (' + (b.username || '') + ').\n\nYour code: ' + code +
      '\n\nIt works for 15 minutes. If this was not you, ignore this email.');
    return { ok: true, sentTo: OWNER_EMAIL.replace(/^(.).*(@.*)$/, '$1•••$2') };
  },

  register: function (b) {
    var username = String(b.username || '').trim();
    var email = String(b.email || '').trim();
    var uniId = String(b.uniId || '').trim();
    var displayName = String(b.displayName || '').trim().slice(0, 40) || username;
    if (!validUsername(username)) fail('Usernames are 3 to 20 letters, numbers, dots or underscores.');
    if (!validPassword(b.password)) fail('Passwords need at least 6 characters.');
    if (!validEmail(email)) fail('That email does not look right.');
    if (!validUniId(uniId)) fail('University IDs are numbers only, like 202112345.');
    if (tooMany('reg:' + (b.device || 'x'), 6)) fail('Too many sign-ups. Try again later.');
    var admin = isAdminIdentity({ username: username, email: email, uniId: uniId });
    if (admin) {
      var code = CacheService.getScriptCache().get('code');
      if (!b.code) return { ok: false, needCode: true, error: 'This account needs the admin code. Tap "Send code".' };
      if (!code || String(b.code).trim() !== code) fail('That code is wrong or expired.');
      CacheService.getScriptCache().remove('code');
    }
    return withLock(function () {
      var all = rows('Users');
      var taken = function (k, v) { return v && all.some(function (u) { return norm(u[k]) === norm(v) || norm(u.username) === norm(v); }); };
      if (taken('username', username)) fail('That username is taken.');
      if (taken('email', email)) fail('An account already uses that email.');
      if (taken('uniId', uniId)) fail('An account already uses that university ID.');
      var salt = Utilities.getUuid();
      var u = { id: newId(), username: username, email: email, uniId: uniId ? "'" + uniId : '', displayName: displayName, salt: salt,
                hash: hashPassword(b.password, salt), role: admin ? 'admin' : 'user', banned: false, banReason: '',
                created: new Date().toISOString(), lastSeen: new Date().toISOString(), color: COLORS[all.length % COLORS.length], mustReset: false,
                avatar: '', phone: '', showPhone: false, bio: '', badges: admin ? 'founder' : '' };
      append('Users', u);
      u.uniId = uniId;
      CacheService.getScriptCache().remove('people');
      return { ok: true, token: newSession(u, b.platform), user: publicUser(u, true) };
    });
  },

  login: function (b) {
    var key = 'login:' + norm(b.login);
    if (tooMany(key, 8)) fail('Too many tries. Wait 15 minutes and try again.');
    var u = findUser(b.login);
    if (!u || hashPassword(b.password, u.salt) !== u.hash) fail('Wrong username or password.');
    if (truthy(u.banned)) fail('This account is banned' + (u.banReason ? ': ' + u.banReason : '.'));
    CacheService.getScriptCache().remove(key);
    if (isAdminIdentity(u) && u.role !== 'admin') { update('Users', u._row, { role: 'admin' }); u.role = 'admin'; }
    return { ok: true, token: newSession(u, b.platform), user: publicUser(u, true) };
  },

  me: function (b) {
    var s = auth(b);
    var u = userById(s.id);
    return { ok: true, user: publicUser(u, true), rooms: publicRooms(s), badges: BADGES, latest: latestMeta() };
  },

  logout: function (b) {
    var t = String(b.token || '');
    CacheService.getScriptCache().remove('s:' + t);
    var s = rows('Sessions').filter(function (x) { return x.token === t; })[0];
    if (s) sheet('Sessions').deleteRow(s._row);
    return { ok: true };
  },

  updateProfile: function (b) {
    var s = auth(b);
    return withLock(function () {
      var u = userById(s.id);
      var email = b.email === undefined ? u.email : String(b.email).trim();
      var uniId = b.uniId === undefined ? String(u.uniId || '') : String(b.uniId).trim();
      var phone = b.phone === undefined ? String(u.phone || '') : String(b.phone).trim();
      if (!validEmail(email)) fail('That email does not look right.');
      if (!validUniId(uniId)) fail('University IDs are numbers only.');
      if (!validPhone(phone)) fail('Phone numbers are digits only, like +962 79 123 4567.');
      var others = rows('Users').filter(function (x) { return x.id !== u.id; });
      if (email && others.some(function (x) { return norm(x.email) === norm(email); })) fail('An account already uses that email.');
      if (uniId && others.some(function (x) { return norm(x.uniId) === norm(uniId); })) fail('An account already uses that university ID.');
      // nobody can add an admin email or ID to an existing account
      if (u.role !== 'admin' && isAdminIdentity({ email: email, uniId: uniId })) fail('That email or ID is reserved.');
      var ch = { displayName: String(b.displayName || u.displayName).trim().slice(0, 40) || u.username, email: email, uniId: uniId ? "'" + uniId : '',
                 phone: phone ? "'" + phone : '', showPhone: b.showPhone === undefined ? truthy(u.showPhone) : !!b.showPhone,
                 bio: b.bio === undefined ? (u.bio || '') : String(b.bio).trim().slice(0, 160),
                 major: b.major === undefined ? (u.major || '') : String(b.major).trim().slice(0, 60),
                 college: b.college === undefined ? (u.college || '') : String(b.college).trim().slice(0, 60) };
      update('Users', u._row, ch);
      forgetUser(u.id);
      u.displayName = ch.displayName; u.email = email; u.uniId = uniId; u.phone = phone; u.showPhone = ch.showPhone; u.bio = ch.bio; u.major = ch.major; u.college = ch.college;
      return { ok: true, user: publicUser(u, true) };
    });
  },

  /** Profile photo: a small JPEG or PNG as base64 (the apps shrink it to about 256 px first) */
  setAvatar: function (b) {
    var s = auth(b);
    var u = userById(s.id);
    if (b.remove) {
      removeAvatarFile(u.avatar);
      update('Users', u._row, { avatar: '' });
      forgetUser(u.id); u.avatar = '';
      return { ok: true, user: publicUser(u, true) };
    }
    var data = String(b.data || '').replace(/^data:[^,]+,/, '');
    if (data.length < 100 || data.length > 700000) fail('Pick a photo under 500 KB.');
    if (tooMany('avatar:' + u.id, 10)) fail('Too many photo changes. Try again later.');
    var type = /png/i.test(b.type || '') ? 'image/png' : 'image/jpeg';
    var blob = Utilities.newBlob(Utilities.base64Decode(data), type, u.username + (type === 'image/png' ? '.png' : '.jpg'));
    var f = avatarFolder().createFile(blob);
    f.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
    var url = 'https://drive.google.com/thumbnail?sz=w256&id=' + f.getId();
    removeAvatarFile(u.avatar);
    update('Users', u._row, { avatar: url });
    forgetUser(u.id);
    u.avatar = url;
    return { ok: true, user: publicUser(u, true) };
  },

  /** Someone's public profile (admins also see the private fields) */
  profile: function (b) {
    var s = auth(b);
    var u = userById(String(b.userId || ''));
    if (!u) fail('User not found.');
    return { ok: true, user: publicUser(u, s.role === 'admin' || s.id === u.id) };
  },

  changePassword: function (b) {
    var s = auth(b);
    var u = userById(s.id);
    if (hashPassword(b.old, u.salt) !== u.hash) fail('Your current password is wrong.');
    if (!validPassword(b.password)) fail('Passwords need at least 6 characters.');
    var salt = Utilities.getUuid();
    update('Users', u._row, { salt: salt, hash: hashPassword(b.password, salt), mustReset: false });
    dropUserSessions(u.id, b.token);
    return { ok: true };
  },

  deleteAccount: function (b) {
    var s = auth(b);
    var u = userById(s.id);
    if (hashPassword(b.password, u.salt) !== u.hash) fail('Wrong password.');
    return withLock(function () {
      dropUserSessions(u.id);
      removeAvatarFile(u.avatar);
      sheet('Users').deleteRow(userById(u.id)._row);
      CacheService.getScriptCache().remove('people');
      return { ok: true };
    });
  },

  // MARK: chat

  /** New messages in a room after a message id (or the latest ones), plus edits, deletions and who is in them */
  chat: function (b) {
    var u = auth(b);
    var room = roomById(String(b.room || 'general'));
    if (!room || (room.archived && u.role !== 'admin')) fail('This room is closed.');
    var list = roomMessages(room.id);
    if (b.after) {
      var i = -1;
      for (var k = list.length - 1; k >= 0; k--) if (list[k].id === b.after) { i = k; break; }
      list = i >= 0 ? list.slice(i + 1) : list.slice(-60);
    } else list = list.slice(-60);
    var c = CacheService.getScriptCache();
    var gone = c.get('d:' + room.id);
    var edits = JSON.parse(c.get('e:' + room.id) || '[]');
    var ids = {};
    roomMessages(room.id).slice(-60).forEach(function (m) { ids[m.userId] = 1; });
    var pinned = null;
    if (room.pinned) pinned = roomMessages(room.id).filter(function (m) { return m.id === room.pinned; })[0] || null;
    return { ok: true, room: room, messages: list, deleted: gone ? JSON.parse(gone) : [], edited: edits, pinned: pinned,
             people: peopleFor(Object.keys(ids)), latest: latestMeta() };
  },

  send: function (b) {
    var u = auth(b);
    var r = roomById(String(b.room || 'general'));
    if (!r || r.archived) fail('This room is closed.');
    if ((r.adminOnly || r.locked) && u.role !== 'admin') fail(r.locked ? 'This room is locked. Only admins can post here.' : 'Only admins can post here.');
    var text = String(b.text || '').trim().slice(0, MAX_TEXT);
    if (!text) fail('Type a message first.');
    if (u.role !== 'admin' && tooMany('send:' + u.id, 20)) fail('Slow down a little.');
    return withLock(function () {
      var m = { id: newId(), room: r.id, userId: u.id, username: u.username, displayName: u.displayName || u.username, role: u.role,
                text: text, created: new Date().toISOString(), deleted: false, replyTo: String(b.replyTo || '').slice(0, 20), color: u.color, edited: '' };
      append('Messages', m);
      var list = roomMessages(r.id);
      var replyUser = '';
      if (m.replyTo) list.forEach(function (x) { if (x.id === m.replyTo) replyUser = x.userId; });
      list.push(publicMessage(m));
      putRoom(r.id, list.slice(-CACHE_MSGS));
      var meta = { id: m.id, room: r.id, roomName: r.name, displayName: m.displayName, username: m.username, userId: m.userId,
                   text: text.slice(0, 120), created: m.created, announcement: !!r.adminOnly };
      CacheService.getScriptCache().put('latest', JSON.stringify(meta), 21600);
      meta.type = r.adminOnly ? 'announcement' : 'message';
      meta.replyToUserId = replyUser;
      pushEvent(meta);
      return { ok: true, message: publicMessage(m) };
    });
  },

  /** Changes the text of your own message */
  editMessage: function (b) {
    var u = auth(b);
    var text = String(b.text || '').trim().slice(0, MAX_TEXT);
    if (!text) fail('Type a message first.');
    return withLock(function () {
      var m = rows('Messages').filter(function (x) { return x.id === b.id; })[0];
      if (!m || truthy(m.deleted)) fail('Message not found.');
      if (m.userId !== u.id) fail('You can only edit your own messages.');
      var when = new Date().toISOString();
      update('Messages', m._row, { text: text, edited: when });
      putRoom(m.room, roomMessages(m.room).map(function (x) { if (x.id === m.id) { x.text = text; x.edited = when; } return x; }));
      var c = CacheService.getScriptCache();
      var edits = JSON.parse(c.get('e:' + m.room) || '[]').filter(function (e) { return e.id !== m.id; });
      edits.push({ id: m.id, text: text, edited: when });
      c.put('e:' + m.room, JSON.stringify(edits.slice(-100)), 21600);
      return { ok: true };
    });
  },

  deleteMessage: function (b) {
    var u = auth(b);
    return withLock(function () {
      var m = rows('Messages').filter(function (x) { return x.id === b.id; })[0];
      if (!m) fail('Message not found.');
      if (m.userId !== u.id && u.role !== 'admin') fail('You can only delete your own messages.');
      update('Messages', m._row, { deleted: true });
      putRoom(m.room, roomMessages(m.room).filter(function (x) { return x.id !== m.id; }));
      var c = CacheService.getScriptCache();
      var gone = JSON.parse(c.get('d:' + m.room) || '[]');
      gone.push(m.id);
      c.put('d:' + m.room, JSON.stringify(gone.slice(-200)), 21600);
      if (u.role === 'admin' && m.userId !== u.id) log(u, 'deleteMessage', m.username, m.text.slice(0, 80));
      return { ok: true };
    });
  },

  /** What the 1.5 apps check in the background for notifications */
  /** Translates a search between Arabic and English (no sign-in; answers are cached for a day) */
  translate: function (b) {
    var text = String(b.text || '').trim().slice(0, 80);
    if (text.length < 2) return { ok: true, text: '' };
    var to = /[\u0600-\u06FF]/.test(text) ? 'en' : 'ar';
    var c = CacheService.getScriptCache();
    var key = 'tr:' + Utilities.base64EncodeWebSafe(text.toLowerCase()).slice(0, 200);
    var hit = c.get(key);
    if (hit !== null) return { ok: true, text: hit, to: to };
    if (tooMany('translate', 600)) return { ok: true, text: '', to: to };
    var out = '';
    try { out = LanguageApp.translate(text, '', to); } catch (err) { return { ok: true, text: '', to: to }; }
    if (norm(out) === norm(text)) out = '';
    c.put(key, out, 86400);
    return { ok: true, text: out, to: to };
  },

  latest: function (b) {
    auth(b);
    return { ok: true, latest: latestMeta() };
  },

  /** Everything newer than `since` that the apps may notify about; each device picks what it shows */
  events: function (b) {
    var u = auth(b);
    try { scanUploads(); } catch (e) {}
    var since = String(b.since || '');
    var hidden = {};
    allRooms().forEach(function (r) { if (r.archived && u.role !== 'admin') hidden[r.id] = 1; });
    var list = JSON.parse(CacheService.getScriptCache().get('events') || '[]').filter(function (e) {
      return (!since || e.created > since) && !hidden[e.room];
    });
    return { ok: true, events: list.slice(-30), now: new Date().toISOString() };
  },

  /** Who is online now and who was recently */
  online: function (b) {
    auth(b);
    var p = presence(), all = people(), now = Date.now();
    var list = Object.keys(p).filter(function (id) { return all[id]; }).map(function (id) {
      var o = all[id];
      return { id: id, displayName: o.displayName, avatar: o.avatar, color: o.color, role: o.role, badges: o.badges,
               lastSeen: p[id], online: now - new Date(p[id]) < ONLINE_MS };
    });
    list.sort(function (a, c) { return String(c.lastSeen).localeCompare(String(a.lastSeen)); });
    return { ok: true, users: list.slice(0, 100), onlineCount: list.filter(function (x) { return x.online; }).length };
  },

  // MARK: admin

  adminStats: function (b) {
    auth(b, { admin: true });
    var users = rows('Users');
    var day = new Date(Date.now() - 864e5).toISOString();
    var week = new Date(Date.now() - 7 * 864e5).toISOString();
    var msgs = rows('Messages');
    var p = presence(), now = Date.now();
    return { ok: true, stats: {
      users: users.length,
      admins: users.filter(function (u) { return u.role === 'admin'; }).length,
      banned: users.filter(function (u) { return truthy(u.banned); }).length,
      online: Object.keys(p).filter(function (k) { return now - new Date(p[k]) < ONLINE_MS; }).length,
      activeToday: users.filter(function (u) { return String(lastActive(u)) > day; }).length,
      activeWeek: users.filter(function (u) { return String(lastActive(u)) > week; }).length,
      newWeek: users.filter(function (u) { return String(u.created) > week; }).length,
      messages: msgs.length,
      messagesToday: msgs.filter(function (m) { return String(m.created) > day; }).length,
      rooms: allRooms().length
    } };
  },

  adminUsers: function (b) {
    auth(b, { admin: true });
    var q = norm(b.q);
    var list = rows('Users').filter(function (u) {
      return !q || [u.username, u.email, u.uniId, u.displayName, u.phone].some(function (v) { return norm(v).indexOf(q) >= 0; });
    }).map(function (u) { return publicUser(u, true); });
    list.sort(function (a, c) { return String(c.created).localeCompare(String(a.created)); });
    return { ok: true, users: list, badges: BADGES };
  },

  adminBan: function (b) {
    var a = auth(b, { admin: true });
    var u = userById(b.userId);
    if (!u) fail('User not found.');
    if (u.id === a.id) fail('You cannot ban yourself.');
    if (isAdminIdentity(u)) fail('The owner account cannot be banned.');
    var ban = !!b.banned;
    update('Users', u._row, { banned: ban, banReason: ban ? String(b.reason || '').slice(0, 120) : '' });
    if (ban) dropUserSessions(u.id); else forgetUser(u.id);
    log(a, ban ? 'ban' : 'unban', u.username, b.reason);
    return { ok: true };
  },

  /** Sets a temporary password and returns it once, for the admin to pass on */
  adminResetPassword: function (b) {
    var a = auth(b, { admin: true });
    var u = userById(b.userId);
    if (!u) fail('User not found.');
    var temp = Utilities.getUuid().replace(/-/g, '').slice(0, 8);
    var salt = Utilities.getUuid();
    update('Users', u._row, { salt: salt, hash: hashPassword(temp, salt), mustReset: true });
    dropUserSessions(u.id);
    log(a, 'resetPassword', u.username, '');
    return { ok: true, password: temp };
  },

  adminSetRole: function (b) {
    var a = auth(b, { admin: true });
    var u = userById(b.userId);
    if (!u) fail('User not found.');
    if (isAdminIdentity(u)) fail('The owner account always stays an admin.');
    var role = b.role === 'admin' ? 'admin' : 'user';
    update('Users', u._row, { role: role });
    forgetUser(u.id);
    log(a, 'role:' + role, u.username, '');
    return { ok: true };
  },

  /** Replaces a user's badges, e.g. ["helper", "donor"] */
  adminSetBadges: function (b) {
    var a = auth(b, { admin: true });
    var u = userById(b.userId);
    if (!u) fail('User not found.');
    var list = (b.badges || []).map(String).filter(function (k, i, all) { return BADGES[k] && all.indexOf(k) === i; });
    update('Users', u._row, { badges: list.join(',') });
    forgetUser(u.id);
    log(a, 'badges', u.username, list.join(', '));
    return { ok: true, badges: list };
  },

  adminDeleteUser: function (b) {
    var a = auth(b, { admin: true });
    var u = userById(b.userId);
    if (!u) fail('User not found.');
    if (isAdminIdentity(u) || u.id === a.id) fail('This account cannot be deleted here.');
    return withLock(function () {
      dropUserSessions(u.id);
      removeAvatarFile(u.avatar);
      sheet('Users').deleteRow(userById(u.id)._row);
      CacheService.getScriptCache().remove('people');
      log(a, 'deleteUser', u.username, '');
      return { ok: true };
    });
  },

  /** Creates a room or changes one: name, emoji, about, adminOnly, locked, archived, order */
  adminRoomSave: function (b) {
    var a = auth(b, { admin: true });
    var name = String(b.name || '').trim().slice(0, 40);
    if (!name) fail('Give the room a name.');
    return withLock(function () {
      var list = rows('Rooms');
      var r = b.id ? list.filter(function (x) { return String(x.id) === String(b.id); })[0] : null;
      var o = { name: name, emoji: String(b.emoji || '💬').slice(0, 4), about: String(b.about || '').trim().slice(0, 100),
                adminOnly: !!b.adminOnly, locked: !!b.locked, archived: !!b.archived };
      if (b.order !== undefined) o.order = Number(b.order) || 0;
      if (r) update('Rooms', r._row, o);
      else {
        var id = norm(name).replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 24) || 'room';
        while (list.some(function (x) { return String(x.id) === id; })) id += '-' + Math.floor(Math.random() * 90 + 10);
        o.id = id; o.pinned = ''; o.created = new Date().toISOString();
        if (o.order === undefined) o.order = list.length;
        append('Rooms', o);
      }
      CacheService.getScriptCache().remove('rooms');
      log(a, r ? 'editRoom' : 'addRoom', name, [o.locked ? 'locked' : '', o.archived ? 'archived' : '', o.adminOnly ? 'admins post' : ''].filter(String).join(', '));
      return { ok: true, rooms: publicRooms(a) };
    });
  },

  /** Deletes a room and hides its messages */
  adminRoomDelete: function (b) {
    var a = auth(b, { admin: true });
    if (String(b.id) === 'announcements') fail('The announcements room cannot be deleted. Archive it instead.');
    return withLock(function () {
      var r = rows('Rooms').filter(function (x) { return String(x.id) === String(b.id); })[0];
      if (!r) fail('Room not found.');
      sheet('Rooms').deleteRow(r._row);
      var c = CacheService.getScriptCache();
      c.remove('rooms'); c.remove('m:' + r.id);
      log(a, 'deleteRoom', r.name, '');
      return { ok: true, rooms: publicRooms(a) };
    });
  },

  /** Pins a message to the top of its room (or unpins with an empty id) */
  adminPin: function (b) {
    var a = auth(b, { admin: true });
    return withLock(function () {
      var r = rows('Rooms').filter(function (x) { return String(x.id) === String(b.room); })[0];
      if (!r) fail('Room not found.');
      update('Rooms', r._row, { pinned: String(b.id || '') });
      CacheService.getScriptCache().remove('rooms');
      log(a, b.id ? 'pin' : 'unpin', r.name, '');
      return { ok: true };
    });
  },

  /** Removes every message in a room */
  adminClearRoom: function (b) {
    var a = auth(b, { admin: true });
    return withLock(function () {
      var room = String(b.room || '');
      var sh = sheet('Messages');
      var c = CacheService.getScriptCache();
      var ids = [];
      rows('Messages').forEach(function (m) {
        if (m.room === room && !truthy(m.deleted)) { sh.getRange(m._row, SHEETS.Messages.indexOf('deleted') + 1).setValue(true); ids.push(m.id); }
      });
      putRoom(room, []);
      var gone = JSON.parse(c.get('d:' + room) || '[]').concat(ids);
      c.put('d:' + room, JSON.stringify(gone.slice(-200)), 21600);
      log(a, 'clearRoom', room, ids.length + ' messages');
      return { ok: true, removed: ids.length };
    });
  },

  /** Moves an uploaded file to the Drive trash (only files in the upload folder) */
  adminDeleteUpload: function (b) {
    var a = auth(b, { admin: true });
    var id = String(b.fileId || '');
    var f;
    try { f = DriveApp.getFileById(id); } catch (e) { fail('File not found.'); }
    var inFolder = false;
    var parents = f.getParents();
    while (parents.hasNext()) if (parents.next().getId() === UPLOAD_FOLDER_ID) inFolder = true;
    if (!inFolder) fail('Only files in the upload folder can be deleted from the app.');
    f.setTrashed(true);
    log(a, 'deleteUpload', f.getName(), id);
    return { ok: true };
  },

  adminLog: function (b) {
    auth(b, { admin: true });
    return { ok: true, log: rows('Log').slice(-100).reverse().map(function (r) { delete r._row; return r; }) };
  }
};
