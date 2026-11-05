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
var SCHEMA = 8;
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
/** Everyone who signs up from this moment until the end of November 2026 (Jordan time) gets the early supporter badge */
var EARLY_START = Date.parse('2026-11-04T00:00:00+03:00');
var EARLY_END = Date.parse('2026-12-01T00:00:00+03:00');
function isEarlySupporter(when) { var t = new Date(when).getTime(); return t >= EARLY_START && t < EARLY_END; }
var SESSION_DAYS = 120;
var MAX_TEXT = 1000;
var CACHE_MSGS = 150;
var ONLINE_MS = 3 * 60000;

var SHEETS = {
  Users: ['id', 'username', 'email', 'uniId', 'displayName', 'salt', 'hash', 'role', 'banned', 'banReason', 'created', 'lastSeen', 'color', 'mustReset',
          'avatar', 'phone', 'showPhone', 'bio', 'badges', 'major', 'college', 'blocked', 'year', 'interests', 'cover'],
  Sessions: ['token', 'userId', 'created', 'lastUsed', 'platform'],
  Messages: ['id', 'room', 'userId', 'username', 'displayName', 'role', 'text', 'created', 'deleted', 'replyTo', 'color', 'edited'],
  Rooms: ['id', 'name', 'emoji', 'about', 'adminOnly', 'locked', 'archived', 'pinned', 'order', 'created'],
  Log: ['time', 'adminId', 'action', 'target', 'detail'],
  Reports: ['id', 'created', 'reporterId', 'reporterName', 'contact', 'kind', 'targetId', 'targetName', 'targetUser', 'reason', 'details', 'status', 'resolvedBy', 'resolvedAt'],
  GroupRequests: ['id', 'created', 'userId', 'username', 'title', 'category', 'url', 'notes', 'status', 'resolvedBy', 'resolvedAt'],
  Friends: ['id', 'from', 'to', 'status', 'created', 'updated'],
  Pokes: ['id', 'from', 'to', 'kind', 'place', 'created', 'read'],
  Posts: ['id', 'userId', 'text', 'created', 'deleted', 'likes', 'image', 'audience'],
  Comments: ['id', 'postId', 'userId', 'text', 'created', 'deleted'],
  Stories: ['id', 'userId', 'image', 'text', 'bg', 'created', 'views', 'audience', 'video'],
  DMs: ['id', 'pair', 'from', 'to', 'text', 'created', 'deleted', 'fileId', 'fileName', 'fileType', 'fileSize', 'read'],
  Suggestions: ['id', 'created', 'userId', 'username', 'note', 'contact', 'status', 'resolvedBy', 'resolvedAt'],
  DeletedFiles: ['id', 'name', 'time', 'adminId', 'trashed']
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

// Run once from the editor: gives the badge to accounts made earlier on the first day, before it was switched on
function backfillEarlySupporters() {
  var n = 0;
  rows('Users').forEach(function (u) {
    if (!isEarlySupporter(u.created)) return;
    var list = String(u.badges || '').split(',').map(function (s) { return s.trim(); }).filter(String);
    if (list.indexOf('early_supporter') >= 0) return;
    list.push('early_supporter');
    update('Users', u._row, { badges: list.join(',') });
    n++;
  });
  CacheService.getScriptCache().remove('people');
  Logger.log('Early supporter badge given to ' + n + ' accounts');
}

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

// Opened once per request: opening the spreadsheet again for every read was a big part of why lists were slow
var openBook = null, openSheets = {};
function book() { return openBook || (openBook = SpreadsheetApp.openById(PropertiesService.getScriptProperties().getProperty('SHEET_ID'))); }
function sheet(name) { return openSheets[name] || (openSheets[name] = book().getSheetByName(name)); }

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

// MARK: - moderation

var REPORT_REASONS = ['spam', 'harassment', 'hate', 'sexual', 'violence', 'privacy', 'copyright', 'wrong', 'other'];

/** Whole words only, so "class" or "عكس" never match. Add words here and publish a new version. */
var BANNED_WORDS = ['fuck', 'fucking', 'fucker', 'motherfucker', 'bitch', 'cunt', 'asshole', 'whore', 'slut', 'nigger', 'nigga', 'faggot',
                    'شرموطة', 'شرموطه', 'قحبة', 'قحبه', 'منيوك', 'منيك', 'متناك', 'كسمك', 'كسختك', 'زبي', 'عرص', 'عرصة', 'خول'];

function assertClean(text) {
  var words = String(text || '').toLowerCase().split(/[^a-z\u0600-\u06FF]+/);
  if (words.some(function (w) { return BANNED_WORDS.indexOf(w) >= 0; })) fail('Please keep it respectful. That has a word that is not allowed here.');
}

/** A deleted account's messages stay in the chat, with nothing that points to the person */
function anonymizeMessages(userId) {
  var name = 'Deleted user';
  rows('Messages').forEach(function (m) {
    if (m.userId === userId) update('Messages', m._row, { userId: '', username: 'deleted', displayName: name, color: '#8e8e93' });
  });
  allRooms().forEach(function (r) {
    putRoom(r.id, roomMessages(r.id).map(function (m) {
      if (m.userId === userId) { m.userId = ''; m.username = 'deleted'; m.displayName = name; m.color = '#8e8e93'; }
      return m;
    }));
  });
}

function blockedIds(u) { return String((u && u.blocked) || '').split(',').filter(String); }

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
  var o = { id: u.id, username: String(u.username), displayName: String(u.displayName || u.username), role: u.role || 'user', color: u.color || '#8C1D40',
            avatar: u.avatar || '', bio: String(u.bio || ''), major: String(u.major || ''), college: String(u.college || ''), year: String(u.year || ''), interests: String(u.interests || ''), badges: badgeList(u), lastSeen: lastActive(u) };
  o.cover = String(u.cover || '');
  o.online = !!o.lastSeen && (new Date() - new Date(o.lastSeen)) < ONLINE_MS;
  if (truthy(u.showPhone)) o.phone = u.phone ? String(u.phone) : '';
  if (full) {
    o.email = u.email || ''; o.uniId = u.uniId ? String(u.uniId) : '';
    o.phone = u.phone ? String(u.phone) : ''; o.showPhone = truthy(u.showPhone);
    o.banned = truthy(u.banned); o.banReason = u.banReason || '';
    o.created = u.created; o.mustReset = truthy(u.mustReset);
    o.blocked = blockedIds(u);
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

// Read once per request: building a list of users used to ask the cache again for every user
var presenceNow = null;
function presence() { return presenceNow || (presenceNow = JSON.parse(CacheService.getScriptCache().get('presence') || '{}')); }

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

/** The signed-in user for a request (cached for 10 minutes; changes clear it), or throws */
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
    cache.put('s:' + t, JSON.stringify(u), 600);
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

/** The upload folder's newest files from the last week (cached for 10 minutes: searching Drive is slow) */
function recentUploads() {
  var c = CacheService.getScriptCache();
  var j = c.get('recentUploads');
  if (j) return JSON.parse(j);
  var list = [];
  try {
    var since = new Date(Date.now() - 7 * 864e5).toISOString().slice(0, 19);
    var it = DriveApp.getFolderById(UPLOAD_FOLDER_ID).searchFiles('createdDate > "' + since + '" and trashed = false');
    while (it.hasNext() && list.length < 200) {
      var f = it.next();
      if (/upload-test/i.test(f.getName())) continue;
      list.push({ id: f.getId(), name: f.getName().slice(0, 120), created: f.getDateCreated().toISOString(), url: 'https://drive.google.com/file/d/' + f.getId() + '/view' });
    }
  } catch (e) { return []; }
  list.sort(function (a, b) { return b.created.localeCompare(a.created); });
  list = list.slice(0, 20);
  c.put('recentUploads', JSON.stringify(list), 600);
  return list;
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

// MARK: - friends, pokes, posts

var POKE_KINDS = ['poke', 'meet', 'study', 'question', 'notes', 'coffee'];

function cleanRow(r) { var o = {}; Object.keys(r).forEach(function (k) { if (k !== '_row') o[k] = String(r[k]); }); return o; }

/** Keeps what people type as plain text in the sheet (never a formula) */
function cell(s) { return /^[=+\-@]/.test(s) ? "'" + s : s; }

function usersById() { var m = {}; rows('Users').forEach(function (u) { m[u.id] = u; }); return m; }

/** A private event: only that person sees it (or only admins) */
function privateEvent(to, type, from, text, kind) {
  pushEvent({ id: newId(), type: type, toUserId: to.toUserId || '', toAdmin: !!to.toAdmin, kind: kind || '', room: '', roomName: '',
              userId: from.id, displayName: from.displayName || from.username, text: text || '', created: new Date().toISOString() });
}

function friendRow(a, b) {
  return rows('Friends').filter(function (f) { return (f.from === a && f.to === b) || (f.from === b && f.to === a); })[0] || null;
}

function relation(f, me) {
  if (!f) return 'none';
  if (f.status === 'accepted') return 'friends';
  return f.from === me ? 'outgoing' : 'incoming';
}

/** The person I want to add or poke. Blocks work both ways, and they don't learn about it. */
function friendTarget(s, id) {
  var t = userById(String(id || ''));
  if (!t || truthy(t.banned)) fail('User not found.');
  if (t.id === s.id) fail("That's you!");
  if (blockedIds(userById(s.id)).indexOf(t.id) >= 0 || blockedIds(t).indexOf(s.id) >= 0) fail("You can't do this with this person.");
  return t;
}

// MARK: - private messages

function dmPair(a, b) { return a < b ? a + ':' + b : b + ':' + a; }

/** Friends can write to each other, admins to anyone. Someone an admin wrote to can answer. Blocks count, except for admins. */
function dmAllowed(s, t) {
  if (!t || t.id === s.id || truthy(t.banned)) return false;
  if (s.role === 'admin') return true;
  if (blockedIds(userById(s.id)).indexOf(t.id) >= 0 || blockedIds(t).indexOf(s.id) >= 0) return false;
  if (friendIds(s.id)[t.id]) return true;
  if (t.role !== 'admin') return false;
  var pair = dmPair(s.id, t.id);
  return rows('DMs').some(function (m) { return m.pair === pair && m.from === t.id; });
}

function dmPublic(m) {
  return { id: m.id, from: m.from, to: m.to, text: String(m.text || ''), created: m.created, fileName: String(m.fileName || ''),
           fileType: String(m.fileType || ''), fileSize: Number(m.fileSize) || 0, hasFile: !!m.fileId };
}

function dmCached(pair) {
  var c = CacheService.getScriptCache(), hit = c.get('dm:' + pair);
  if (hit) return JSON.parse(hit);
  var list = rows('DMs').filter(function (m) { return m.pair === pair && !truthy(m.deleted); }).slice(-80).map(dmPublic);
  dmStore(pair, list);
  return list;
}

function dmStore(pair, list) {
  var j = JSON.stringify(list);
  while (j.length > 95000 && list.length > 10) { list = list.slice(10); j = JSON.stringify(list); }
  CacheService.getScriptCache().put('dm:' + pair, j, 21600);
}

/** Checks what kind of file this really is. Private chats take PDF, photos and Word (DOCX) files only. */
function dmFileKind(bytes, name) {
  var ext = String(name).toLowerCase().split('.').pop();
  var b0 = bytes[0] & 255, b1 = bytes[1] & 255, b2 = bytes[2] & 255, b3 = bytes[3] & 255;
  if (ext === 'pdf' && b0 === 0x25 && b1 === 0x50 && b2 === 0x44 && b3 === 0x46) return { type: 'pdf', mime: 'application/pdf' };
  if ((ext === 'jpg' || ext === 'jpeg') && b0 === 0xFF && b1 === 0xD8) return { type: 'photo', mime: 'image/jpeg' };
  if (ext === 'png' && b0 === 0x89 && b1 === 0x50) return { type: 'photo', mime: 'image/png' };
  if (ext === 'docx' && b0 === 0x50 && b1 === 0x4B) return { type: 'docx', mime: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' };
  fail('Only PDF, photos and DOCX files can be sent.');
}

/** The people someone is friends with, as a map of ids */
function friendIds(id) {
  var ids = {};
  rows('Friends').forEach(function (f) {
    if (f.status !== 'accepted') return;
    if (f.from === id) ids[f.to] = 1;
    if (f.to === id) ids[f.from] = 1;
  });
  return ids;
}

/** public: anyone, also without an account. students: signed in. friends: only friends. Older posts have none, and stay with students. */
function audienceOf(b) {
  var a = String(b.audience || '');
  return a === 'public' || a === 'friends' ? a : 'students';
}

/** What people without an account may see about someone */
function guestUser(u) {
  return { id: u.id, username: String(u.username), displayName: String(u.displayName || u.username), role: u.role || 'user',
           color: u.color || '#8C1D40', avatar: u.avatar || '', badges: badgeList(u) };
}

function publicPosts(s, wanted, limit, offset) {
  var counts = {};
  var pals = friendIds(s.id);
  rows('Comments').forEach(function (c) { if (!truthy(c.deleted)) counts[c.postId] = (counts[c.postId] || 0) + 1; });
  var me = userById(s.id);
  var map = usersById();
  var mine = blockedIds(me);
  return rows('Posts').filter(function (p) {
    if (truthy(p.deleted) || !wanted(p)) return false;
    if (p.audience === 'friends' && p.userId !== s.id && !pals[p.userId] && s.role !== 'admin') return false;
    var u = map[p.userId];
    return u && !truthy(u.banned) && mine.indexOf(p.userId) < 0 && blockedIds(u).indexOf(s.id) < 0;
  }).reverse().slice(offset || 0, (offset || 0) + limit).map(function (p) {
    var likes = String(p.likes || '').split(',').filter(String);
    return { id: p.id, text: String(p.text), image: String(p.image || ''), audience: audienceOf(p), comments: counts[p.id] || 0, created: p.created, likes: likes.length, liked: likes.indexOf(s.id) >= 0, user: publicUser(map[p.userId], false) };
  });
}

/** Saves a photo the apps sent as base64 and shares it by link */
function savePhoto(folder, data, name, maxLength, width) {
  var d = String(data || '').replace(/^data:[^,]+,/, '');
  if (d.length < 100 || d.length > maxLength) fail('That photo is too big. Pick another one.');
  var f = folder.createFile(Utilities.newBlob(Utilities.base64Decode(d), 'image/jpeg', name + '.jpg'));
  f.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  return 'https://drive.google.com/thumbnail?sz=w' + width + '&id=' + f.getId();
}

/** Saves a story video the apps sent as base64 and shares it by link */
function saveVideo(folder, data, name) {
  var d = String(data || '').replace(/^data:[^,]+,/, '');
  if (d.length < 100 || d.length > 17000000) fail('That video is too big. Keep it short.');
  var bytes = Utilities.base64Decode(d);
  if (String.fromCharCode(bytes[4] & 255, bytes[5] & 255, bytes[6] & 255, bytes[7] & 255) !== 'ftyp') fail('That video type is not supported.');
  var f = folder.createFile(Utilities.newBlob(bytes, 'video/mp4', name + '.mp4'));
  f.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  return 'https://drive.google.com/uc?export=download&id=' + f.getId();
}

function namedFolder(key, name) {
  var p = PropertiesService.getScriptProperties();
  var id = p.getProperty(key);
  if (id) { try { return DriveApp.getFolderById(id); } catch (e) {} }
  var f = DriveApp.createFolder(name);
  p.setProperty(key, f.getId());
  return f;
}

var STORY_SHOWN_MS = 24 * 3600000;
var STORY_KEPT_MS = 48 * 3600000;

/** Deletes stories older than two days, with their photos. Runs at most once an hour, when someone uses the app. */
function purgeStories(force) {
  var c = CacheService.getScriptCache();
  if (!force && c.get('storyPurge')) return 0;
  c.put('storyPurge', '1', 3600);
  var now = Date.now(), n = 0;
  withLock(function () {
    rows('Stories').filter(function (r) { return now - new Date(r.created).getTime() > STORY_KEPT_MS; }).reverse().forEach(function (r) {
      removeAvatarFile(r.image);
      removeAvatarFile(r.video);
      sheet('Stories').deleteRow(r._row);
      n++;
    });
  });
  return n;
}

/** Removes someone from friends, pokes and posts when the account goes away */
function purgeSocial(id) {
  [['Friends', function (r) { return r.from === id || r.to === id; }],
   ['Pokes', function (r) { return r.from === id || r.to === id; }],
   ['Comments', function (r) { return r.userId === id; }],
   ['Stories', function (r) { return r.userId === id; }],
   ['DMs', function (r) { return r.from === id || r.to === id; }],
   ['Posts', function (r) { return r.userId === id; }]].forEach(function (x) {
    rows(x[0]).filter(x[1]).reverse().forEach(function (r) {
      if (r.image) removeAvatarFile(r.image);
      if (r.video) removeAvatarFile(r.video);
      if (r.fileId) { try { DriveApp.getFileById(r.fileId).setTrashed(true); } catch (e) {} }
      sheet(x[0]).deleteRow(r._row);
    });
  });
}

function submissionFolder() {
  var p = PropertiesService.getScriptProperties();
  var id = p.getProperty('SUBMISSION_FOLDER');
  if (id) { try { return DriveApp.getFolderById(id); } catch (e) {} }
  var f = DriveApp.createFolder('ASU Bank Submissions');
  p.setProperty('SUBMISSION_FOLDER', f.getId());
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
      var early = isEarlySupporter(new Date());
      var startBadges = (admin ? ['founder'] : []).concat(early ? ['early_supporter'] : []);
      var u = { id: newId(), username: username, email: email, uniId: uniId ? "'" + uniId : '', displayName: displayName, salt: salt,
                hash: hashPassword(b.password, salt), role: admin ? 'admin' : 'user', banned: false, banReason: '',
                created: new Date().toISOString(), lastSeen: new Date().toISOString(), color: COLORS[all.length % COLORS.length], mustReset: false,
                avatar: '', phone: '', showPhone: false, bio: '', badges: startBadges.join(',') };
      append('Users', u);
      u.uniId = uniId;
      CacheService.getScriptCache().remove('people');
      return { ok: true, token: newSession(u, b.platform), user: publicUser(u, true), earlySupporter: early };
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
    assertClean(b.displayName); assertClean(b.bio); assertClean(b.interests);
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
                 college: b.college === undefined ? (u.college || '') : String(b.college).trim().slice(0, 60),
                 year: b.year === undefined ? (u.year || '') : String(b.year).trim().slice(0, 30),
                 interests: b.interests === undefined ? (u.interests || '') : String(b.interests).trim().slice(0, 160) };
      update('Users', u._row, ch);
      forgetUser(u.id);
      u.displayName = ch.displayName; u.email = email; u.uniId = uniId; u.phone = phone; u.showPhone = ch.showPhone; u.bio = ch.bio; u.major = ch.major; u.college = ch.college; u.year = ch.year; u.interests = ch.interests;
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
    if (data.length < 100 || data.length > 900000) fail('Pick a photo under 500 KB.');
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

  /** Cover photo on the profile (the apps shrink it first) */
  setCover: function (b) {
    var s = auth(b);
    var u = userById(s.id);
    if (b.remove) {
      removeAvatarFile(u.cover);
      update('Users', u._row, { cover: '' });
      forgetUser(u.id); u.cover = '';
      return { ok: true, user: publicUser(u, true) };
    }
    if (tooMany('cover:' + u.id, 10)) fail('Too many photo changes. Try again later.');
    var url = savePhoto(avatarFolder(), b.data, u.username + '-cover', 1500000, 1280);
    removeAvatarFile(u.cover);
    update('Users', u._row, { cover: url });
    forgetUser(u.id);
    u.cover = url;
    return { ok: true, user: publicUser(u, true) };
  },

  /** Someone's public profile (admins also see the private fields) */
  profile: function (b) {
    var s = auth(b);
    var u = userById(String(b.userId || ''));
    if (!u) fail('User not found.');
    var o = { ok: true, user: publicUser(u, s.role === 'admin' || s.id === u.id) };
    if (truthy(b.social)) {
      var fr = rows('Friends');
      o.friendship = u.id === s.id ? 'self' : relation(fr.filter(function (f) { return (f.from === s.id && f.to === u.id) || (f.to === s.id && f.from === u.id); })[0], s.id);
      o.friendCount = fr.filter(function (f) { return f.status === 'accepted' && (f.from === u.id || f.to === u.id); }).length;
      o.posts = publicPosts(s, function (p) { return p.userId === u.id; }, 20);
      o.postCount = rows('Posts').filter(function (p) { return p.userId === u.id && !truthy(p.deleted); }).length;
      o.messageCount = rows('Messages').filter(function (m) { return m.userId === u.id && !truthy(m.deleted); }).length;
      o.canMessage = dmAllowed(s, u);
    }
    return o;
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
      removeAvatarFile(u.cover);
      anonymizeMessages(u.id);
      purgeSocial(u.id);
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
    assertClean(text);
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
    assertClean(text);
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
    var key = 'tr2:' + Utilities.base64EncodeWebSafe(Utilities.computeDigest(Utilities.DigestAlgorithm.MD5, text.toLowerCase(), Utilities.Charset.UTF_8));
    var hit = c.get(key);
    if (hit !== null) return { ok: true, text: hit, to: to };
    if (tooMany('translate', 600)) return { ok: true, text: '', to: to };
    var out = '';
    try { out = LanguageApp.translate(text, '', to); } catch (err) { return { ok: true, text: '', to: to }; }
    if (norm(out) === norm(text) || /^[\s?؟]*$/.test(out)) out = '';
    c.put(key, out, 86400);
    return { ok: true, text: out, to: to };
  },

  // MARK: reports and blocking

  /** Anyone can report something, signed in or not. The owner gets an email and it shows in the admin Reports screen. */
  report: function (b) {
    var me = b.token ? auth(b) : null;
    var kind = String(b.kind || '');
    if (['message', 'user', 'file', 'post', 'comment', 'story', 'dm', 'other'].indexOf(kind) < 0) fail('Bad report.');
    var reason = String(b.reason || '');
    if (REPORT_REASONS.indexOf(reason) < 0) fail('Choose what is wrong first.');
    if (tooMany(me ? 'report:' + me.id : 'report:guest', me ? 15 : 40)) fail('Too many reports. Try again later.');
    var targetId = String(b.targetId || '').slice(0, 300);
    var targetName = String(b.targetName || '').trim().slice(0, 300);
    var targetUser = '';
    // the stored copy comes from our own data, so an edit or delete later can't hide what was reported
    if (kind === 'message') {
      var m = rows('Messages').filter(function (x) { return x.id === targetId; })[0];
      if (!m) fail('Message not found.');
      targetName = String(m.text).slice(0, 300);
      targetUser = m.username;
    } else if (kind === 'user') {
      var t = userById(targetId);
      if (!t) fail('User not found.');
      targetName = t.displayName || t.username;
      targetUser = t.username;
    } else if (kind === 'dm') {
      var dm = rows('DMs').filter(function (x) { return x.id === targetId; })[0];
      if (!dm || !me || dm.to !== me.id) fail('Message not found.');
      var du = userById(dm.from);
      targetName = String(dm.text || dm.fileName).slice(0, 300);
      targetUser = du ? du.username : '';
    } else if (kind === 'post') {
      var po = rows('Posts').filter(function (x) { return x.id === targetId; })[0];
      if (!po) fail('Post not found.');
      var pu = userById(po.userId);
      targetName = String(po.text).slice(0, 300);
      targetUser = pu ? pu.username : '';
    } else if (kind === 'comment' || kind === 'story') {
      var co = rows(kind === 'comment' ? 'Comments' : 'Stories').filter(function (x) { return x.id === targetId; })[0];
      if (!co) fail('Not found.');
      var cu = userById(co.userId);
      targetName = (kind === 'story' && co.image ? String(co.image) + ' ' : '') + String(co.text).slice(0, 200);
      targetUser = cu ? cu.username : '';
    }
    return withLock(function () {
      if (me && rows('Reports').some(function (r) { return r.reporterId === me.id && r.kind === kind && r.targetId === targetId && r.status === 'open' && kind !== 'other'; })) return { ok: true };
      var rep = { id: newId(), created: new Date().toISOString(), reporterId: me ? me.id : '', reporterName: me ? me.username : 'Guest',
                  contact: String(b.contact || '').trim().slice(0, 120), kind: kind, targetId: targetId, targetName: targetName, targetUser: targetUser,
                  reason: reason, details: String(b.details || '').trim().slice(0, 1000), status: 'open', resolvedBy: '', resolvedAt: '' };
      append('Reports', rep);
      try {
        MailApp.sendEmail(OWNER_EMAIL, 'ASU Bank report: ' + reason + ' (' + kind + ')',
          'Reported by: ' + rep.reporterName + (rep.contact ? ' (' + rep.contact + ')' : '') + '\n' +
          'What: ' + kind + (targetUser ? ' by @' + targetUser : '') + '\n' + targetName + '\n' + (kind === 'file' ? targetId + '\n' : '') +
          '\nDetails: ' + (rep.details || 'none') + '\n\nReview it in the app: Admin > Reports, or https://asubank.org/admin/');
      } catch (err) { /* the report is saved even when the daily email limit is used up */ }
      return { ok: true };
    });
  },

  block: function (b) {
    var s = auth(b);
    var target = String(b.userId || '');
    if (target === s.id) fail("You can't block yourself.");
    var t = userById(target);
    if (b.on !== false) {
      if (!t) fail('User not found.');
      if (t.role === 'admin') fail("You can't block an admin. Report the message instead.");
    }
    return withLock(function () {
      var u = userById(s.id);
      var list = blockedIds(u).filter(function (x) { return x !== target; });
      if (b.on !== false) list.push(target);
      update('Users', u._row, { blocked: list.join(',') });
      if (b.on !== false) { var f = friendRow(s.id, target); if (f) sheet('Friends').deleteRow(f._row); }
      return { ok: true, blocked: list };
    });
  },

  blockedList: function (b) {
    var s = auth(b);
    var people = [];
    blockedIds(userById(s.id)).forEach(function (id) {
      var t = userById(id);
      if (t) people.push({ id: t.id, username: t.username, displayName: t.displayName || t.username });
    });
    return { ok: true, people: people };
  },

  adminReports: function (b) {
    auth(b, { admin: true });
    var list = rows('Reports').filter(function (r) { return truthy(b.all) || b.all === true || r.status === 'open'; });
    return { ok: true, reports: list.slice(-200).reverse().map(function (r) { delete r._row; return r; }) };
  },

  adminReportSet: function (b) {
    var a = auth(b, { admin: true });
    var status = String(b.status || '');
    if (['open', 'done', 'dismissed'].indexOf(status) < 0) fail('Bad status.');
    return withLock(function () {
      var r = rows('Reports').filter(function (x) { return x.id === b.id; })[0];
      if (!r) fail('Report not found.');
      update('Reports', r._row, { status: status, resolvedBy: a.username, resolvedAt: new Date().toISOString() });
      log(a, 'report ' + status, r.kind, String(r.targetName).slice(0, 80));
      return { ok: true };
    });
  },

  /**
   * The Notifications tab: friend requests, pokes, group answers, announcements and new uploads, plus reports,
   * suggestions and group requests for admins. Built from the sheets, because the events feed only keeps the last 60 events.
   */
  notifications: function (b) {
    var u = auth(b);
    var map = usersById();
    var blocked = blockedIds(map[u.id]);
    var out = [];
    function add(type, when, id, fromId, text, extra) {
      var t = new Date(when);
      if (isNaN(t.getTime())) return;
      if (fromId && blocked.indexOf(fromId) >= 0) return;
      var o = { id: type + ':' + id, type: type, created: t.toISOString(), text: String(text || '') };
      var p = fromId ? map[fromId] : null;
      if (p) { o.userId = p.id; o.name = p.displayName || p.username; o.avatar = p.avatar || ''; o.color = p.color || ''; }
      Object.keys(extra || {}).forEach(function (k) { o[k] = extra[k]; });
      out.push(o);
    }
    rows('Friends').forEach(function (f) {
      if (f.status === 'accepted') { if (f.from === u.id) add('friend_accept', f.updated || f.created, f.id, f.to, ''); }
      else if (f.to === u.id) add('friend_request', f.created, f.id, f.from, '');
    });
    rows('Pokes').filter(function (p) { return p.to === u.id; }).slice(-30).forEach(function (p) {
      add('poke', p.created, p.id, p.from, p.place, { kind: String(p.kind || 'poke') });
    });
    rows('GroupRequests').forEach(function (r) {
      if (r.userId === u.id && (r.status === 'approved' || r.status === 'rejected')) add('group_' + r.status, r.resolvedAt || r.created, r.id, '', r.title);
      else if (u.role === 'admin' && r.status === 'pending') add('group_request', r.created, r.id, r.userId, r.title);
    });
    if (truthy(b.social2)) {
      var myPosts = {};
      rows('Posts').forEach(function (p) { if (p.userId === u.id && !truthy(p.deleted)) myPosts[p.id] = 1; });
      rows('Comments').filter(function (c) { return myPosts[c.postId] && c.userId !== u.id && !truthy(c.deleted); }).slice(-30).forEach(function (c) {
        add('post_comment', c.created, c.id, c.userId, c.text);
      });
    }
    roomMessages('announcements').slice(-5).forEach(function (m) { add('announcement', m.created, m.id, '', m.text); });
    var gone = {};
    rows('DeletedFiles').forEach(function (r) { gone[String(r.id)] = 1; });
    recentUploads().forEach(function (f) { if (!gone[f.id]) add('upload', f.created, f.id, '', f.name, { url: f.url }); });
    if (u.role === 'admin') {
      rows('Reports').filter(function (r) { return r.status === 'open'; }).slice(-20).forEach(function (r) {
        add('report', r.created, r.id, '', r.reason + ' · ' + r.kind + (r.targetName ? ': ' + String(r.targetName).slice(0, 80) : ''));
      });
      rows('Suggestions').filter(function (r) { return r.status === 'open'; }).slice(-20).forEach(function (r) {
        add('suggestion', r.created, r.id, '', String(r.note).slice(0, 140));
      });
    }
    out.sort(function (x, y) { return y.created.localeCompare(x.created); });
    return { ok: true, items: out.slice(0, 60) };
  },

  latest: function (b) {
    auth(b);
    return { ok: true, latest: latestMeta() };
  },

  /** Everything newer than `since` that the apps may notify about; each device picks what it shows */
  events: function (b) {
    var u = auth(b);
    try { scanUploads(); } catch (e) {}
    try { purgeStories(); } catch (e) {}
    var since = String(b.since || '');
    var hidden = {};
    allRooms().forEach(function (r) { if (r.archived && u.role !== 'admin') hidden[r.id] = 1; });
    var list = JSON.parse(CacheService.getScriptCache().get('events') || '[]').filter(function (e) {
      if (e.toUserId || e.toAdmin) {
        // private events, only for apps that understand them
        if (!truthy(b.social) || (since && e.created <= since)) return false;
        if (e.type === 'post_comment' && !truthy(b.social2)) return false;
        if (e.type === 'dm' && !truthy(b.social3)) return false;
        return e.toAdmin ? u.role === 'admin' : e.toUserId === u.id;
      }
      return (!since || e.created > since) && !hidden[e.room];
    });
    return { ok: true, events: list.slice(-30), now: new Date().toISOString() };
  },

  /** A file for a student submission (base64). Kept apart from the public uploads list. No account needed, like the submission itself. */
  submissionFile: function (b) {
    var me = b.token ? auth(b) : null;
    var name = String(b.name || 'file').replace(/[\\/:*?"<>|]+/g, ' ').trim().slice(0, 120);
    var ext = (name.split('.').pop() || '').toLowerCase();
    if (['pdf', 'pptx', 'ppt', 'docx', 'doc', 'png', 'jpg', 'jpeg', 'webp', 'heic', 'mp4', 'mov', 'm4v', 'webm'].indexOf(ext) < 0) fail("This file type isn't allowed.");
    var data = String(b.data || '').replace(/^data:[^,]+,/, '');
    if (data.length < 8) fail('This file is empty.');
    if (data.length > 34000000) fail('Bigger than 25 MB.');
    if (tooMany(me ? 'subfile:' + me.id : 'subfile:guest', me ? 15 : 60)) fail('Too many uploads. Try again later.');
    var blob = Utilities.newBlob(Utilities.base64Decode(data), String(b.type || 'application/octet-stream'), name);
    var f = submissionFolder().createFile(blob);
    f.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
    return { ok: true, url: 'https://drive.google.com/file/d/' + f.getId() + '/view' };
  },

  // MARK: group links people ask to add

  /** Someone asks for a group to be listed. The owner accepts or rejects it in the admin section. */
  groupRequest: function (b) {
    var s = auth(b);
    var title = String(b.title || '').trim().slice(0, 80);
    var category = String(b.category || '').trim().slice(0, 60);
    var url = String(b.url || '').trim().slice(0, 300);
    var notes = String(b.notes || '').trim().slice(0, 500);
    if (title.length < 3) fail('Write the name of the group.');
    if (!/^https:\/\/[^\s]+\.[^\s]+$/.test(url)) fail('Paste the full link, starting with https://');
    assertClean(title); assertClean(notes); assertClean(category);
    if (tooMany('group:' + s.id, 5)) fail('Too many requests. Try again later.');
    return withLock(function () {
      var same = rows('GroupRequests').filter(function (r) { return r.url === url && r.status !== 'rejected'; })[0];
      if (same) fail(same.status === 'approved' ? 'This group is already listed.' : 'This group is already waiting for approval.');
      var r = { id: newId(), created: new Date().toISOString(), userId: s.id, username: s.username, title: cell(title), category: cell(category), url: url,
                notes: cell(notes), status: 'pending', resolvedBy: '', resolvedAt: '' };
      append('GroupRequests', r);
      privateEvent({ toAdmin: true }, 'group_request', s, title);
      try {
        MailApp.sendEmail(OWNER_EMAIL, 'ASU Bank group request: ' + title,
          'From: @' + s.username + '\nGroup: ' + title + '\nCategory: ' + (category || 'none') + '\nLink: ' + url + '\nNotes: ' + (notes || 'none') +
          '\n\nAccept or reject it in the app: Admin > Group requests.');
      } catch (err) { /* the request is saved even when the daily email limit is used up */ }
      return { ok: true, request: r };
    });
  },

  /** Groups the owner accepted. Anyone can read these. */
  groupsApproved: function (b) {
    var list = rows('GroupRequests').filter(function (r) { return r.status === 'approved'; }).map(function (r) {
      return { id: r.id, title: String(r.title), category: String(r.category), url: String(r.url) };
    });
    return { ok: true, groups: list };
  },

  /** What this person asked for, and what happened to it */
  myGroupRequests: function (b) {
    var s = auth(b);
    return { ok: true, requests: rows('GroupRequests').filter(function (r) { return r.userId === s.id; }).reverse().slice(0, 20).map(cleanRow) };
  },

  adminGroupRequests: function (b) {
    auth(b, { admin: true });
    var all = rows('GroupRequests');
    var pending = all.filter(function (r) { return r.status === 'pending'; }).length;
    var list = truthy(b.all) ? all : all.filter(function (r) { return r.status === 'pending'; });
    return { ok: true, pending: pending, requests: list.reverse().slice(0, 100).map(cleanRow) };
  },

  /** status: approved, rejected. Tells the person who asked. */
  adminGroupSet: function (b) {
    var a = auth(b, { admin: true });
    var status = String(b.status || '');
    if (['approved', 'rejected'].indexOf(status) < 0) fail('Bad status.');
    return withLock(function () {
      var r = rows('GroupRequests').filter(function (x) { return x.id === b.id; })[0];
      if (!r) fail('Request not found.');
      update('GroupRequests', r._row, { status: status, resolvedBy: a.username, resolvedAt: new Date().toISOString() });
      log(a, 'group:' + status, r.url, r.title);
      privateEvent({ toUserId: r.userId }, status === 'approved' ? 'group_approved' : 'group_rejected', a, r.title);
      return { ok: true };
    });
  },

  // MARK: friends and pokes

  friendRequest: function (b) {
    var s = auth(b);
    var t = friendTarget(s, b.userId);
    if (tooMany('friend:' + s.id, 40)) fail('Too many requests. Try again later.');
    return withLock(function () {
      var f = friendRow(s.id, t.id);
      if (f && (f.status === 'accepted' || f.from === s.id)) return { ok: true, friendship: f.status === 'accepted' ? 'friends' : 'outgoing' };
      var now = new Date().toISOString();
      if (f) { // they already asked me, so we are friends now
        update('Friends', f._row, { status: 'accepted', updated: now });
        privateEvent({ toUserId: t.id }, 'friend_accept', s, '');
        return { ok: true, friendship: 'friends' };
      }
      append('Friends', { id: newId(), from: s.id, to: t.id, status: 'pending', created: now, updated: now });
      privateEvent({ toUserId: t.id }, 'friend_request', s, '');
      return { ok: true, friendship: 'outgoing' };
    });
  },

  /** Accepts or declines a request someone sent me */
  friendRespond: function (b) {
    var s = auth(b);
    var other = String(b.userId || '');
    return withLock(function () {
      var f = friendRow(s.id, other);
      if (!f || f.status !== 'pending' || f.to !== s.id) fail('This request is no longer there.');
      if (b.accept === false) { sheet('Friends').deleteRow(f._row); return { ok: true, friendship: 'none' }; }
      update('Friends', f._row, { status: 'accepted', updated: new Date().toISOString() });
      privateEvent({ toUserId: other }, 'friend_accept', s, '');
      return { ok: true, friendship: 'friends' };
    });
  },

  /** Unfriends someone, or cancels a request I sent */
  friendRemove: function (b) {
    var s = auth(b);
    return withLock(function () {
      var f = friendRow(s.id, String(b.userId || ''));
      if (f) sheet('Friends').deleteRow(f._row);
      return { ok: true, friendship: 'none' };
    });
  },

  /** My friends, requests both ways and the latest pokes */
  friends: function (b) {
    var s = auth(b);
    var map = usersById();
    var card = function (id) { var u = map[id]; return u && !truthy(u.banned) ? publicUser(u, false) : null; };
    var friends = [], incoming = [], outgoing = [];
    rows('Friends').forEach(function (f) {
      if (f.from !== s.id && f.to !== s.id) return;
      var c = card(f.from === s.id ? f.to : f.from);
      if (!c) return;
      if (f.status === 'accepted') friends.push(c);
      else if (f.to === s.id) incoming.push(c);
      else outgoing.push(c);
    });
    friends.sort(function (x, y) { return (y.online ? 1 : 0) - (x.online ? 1 : 0) || String(x.displayName).localeCompare(String(y.displayName)); });
    var pokes = rows('Pokes').filter(function (p) { return p.to === s.id; }).reverse().slice(0, 50).map(function (p) {
      return { id: p.id, kind: p.kind, place: String(p.place || ''), created: p.created, read: truthy(p.read), user: card(p.from) };
    }).filter(function (p) { return p.user; });
    return { ok: true, friends: friends, incoming: incoming, outgoing: outgoing, pokes: pokes };
  },

  /** Sends a friend a poke or a short message like "wants to meet you at the library" */
  poke: function (b) {
    var s = auth(b);
    var t = friendTarget(s, b.userId);
    var kind = String(b.kind || 'poke');
    if (POKE_KINDS.indexOf(kind) < 0) fail('Bad poke.');
    var place = String(b.place || '').trim().slice(0, 120);
    assertClean(place);
    var f = friendRow(s.id, t.id);
    if (!f || f.status !== 'accepted') fail('You can only do this with friends.');
    if (tooMany('poke:' + s.id, 40) || tooMany('poke:' + s.id + ':' + t.id, 8)) fail('Slow down a little. Try again later.');
    return withLock(function () {
      append('Pokes', { id: newId(), from: s.id, to: t.id, kind: kind, place: cell(place), created: new Date().toISOString(), read: false });
      privateEvent({ toUserId: t.id }, 'poke', s, place, kind);
      return { ok: true };
    });
  },

  pokesRead: function (b) {
    var s = auth(b);
    return withLock(function () {
      rows('Pokes').forEach(function (p) { if (p.to === s.id && !truthy(p.read)) update('Pokes', p._row, { read: true }); });
      return { ok: true };
    });
  },

  /** Finds people by name, username, major or college. With no words: people from the same college or major. */
  searchUsers: function (b) {
    var s = auth(b);
    var me = userById(s.id);
    var q = norm(b.q);
    var mine = blockedIds(me);
    var fr = rows('Friends');
    var relationOf = function (id) {
      var f = fr.filter(function (x) { return (x.from === s.id && x.to === id) || (x.to === s.id && x.from === id); })[0];
      return relation(f, s.id);
    };
    var found = rows('Users').filter(function (u) {
      if (u.id === s.id || truthy(u.banned) || mine.indexOf(u.id) >= 0 || blockedIds(u).indexOf(s.id) >= 0) return false;
      if (q) return [u.username, u.displayName, u.major, u.college].some(function (x) { return norm(x).indexOf(q) >= 0; });
      if (relationOf(u.id) !== 'none') return false;
      return (me.college && norm(u.college) === norm(me.college)) || (me.major && norm(u.major) === norm(me.major));
    }).slice(0, 25).map(function (u) {
      var c = publicUser(u, false);
      c.friendship = relationOf(u.id);
      return c;
    });
    return { ok: true, people: found };
  },

  // MARK: posts

  postCreate: function (b) {
    var s = auth(b);
    var text = String(b.text || '').trim().slice(0, 1000);
    if (!text && !b.image) fail('Write something first.');
    assertClean(text);
    if (tooMany('post:' + s.id, 10)) fail('Too many posts. Try again later.');
    var image = b.image ? savePhoto(namedFolder('FEED_FOLDER', 'ASU Bank Feed'), b.image, s.id + '-' + Date.now(), 2500000, 1280) : '';
    return withLock(function () {
      var p = { id: newId(), userId: s.id, text: cell(text), created: new Date().toISOString(), deleted: false, likes: '', image: image, audience: audienceOf(b) };
      append('Posts', p);
      return { ok: true, id: p.id };
    });
  },

  postDelete: function (b) {
    var s = auth(b);
    return withLock(function () {
      var p = rows('Posts').filter(function (x) { return x.id === b.id; })[0];
      if (!p) fail('Post not found.');
      if (p.userId !== s.id && s.role !== 'admin') fail('You can only delete your own posts.');
      if (p.image) removeAvatarFile(p.image);
      update('Posts', p._row, { deleted: true, text: '', image: '' });
      if (p.userId !== s.id) log(s, 'deletePost', p.userId, '');
      return { ok: true };
    });
  },

  postLike: function (b) {
    var s = auth(b);
    return withLock(function () {
      var p = rows('Posts').filter(function (x) { return x.id === b.id; })[0];
      if (!p || truthy(p.deleted)) fail('Post not found.');
      var list = String(p.likes || '').split(',').filter(String).filter(function (x) { return x !== s.id; });
      if (b.on !== false) list.push(s.id);
      update('Posts', p._row, { likes: list.join(',') });
      return { ok: true, likes: list.length, liked: b.on !== false };
    });
  },

  /** Latest posts from me and my friends */
  feed: function (b) {
    var s = auth(b);
    var ids = {};
    ids[s.id] = 1;
    rows('Friends').forEach(function (f) {
      if (f.status !== 'accepted') return;
      if (f.from === s.id) ids[f.to] = 1;
      if (f.to === s.id) ids[f.from] = 1;
    });
    return { ok: true, posts: publicPosts(s, function (p) { return ids[p.userId]; }, 50) };
  },

  /** The feed: everyone's posts, or scope "friends". `offset` loads more. */
  feedAll: function (b) {
    var s = auth(b);
    var offset = Math.max(0, Number(b.offset) || 0);
    if (b.scope === 'guest') return { ok: true, posts: publicPosts(s, function (p) { return p.audience === 'public'; }, 25, offset) };
    if (b.scope !== 'friends' && b.scope !== 'strangers') return { ok: true, posts: publicPosts(s, function () { return true; }, 25, offset) };
    var ids = friendIds(s.id);
    ids[s.id] = 1;
    var want = b.scope === 'friends';
    return { ok: true, posts: publicPosts(s, function (p) { return !!ids[p.userId] === want; }, 25, offset) };
  },

  /** My private conversations, newest first */
  dmList: function (b) {
    var s = auth(b);
    var map = usersById(), mine = blockedIds(map[s.id]), found = {};
    rows('DMs').forEach(function (m) {
      if (truthy(m.deleted) || (m.from !== s.id && m.to !== s.id)) return;
      var other = m.from === s.id ? m.to : m.from;
      var g = found[other] || (found[other] = { unread: 0 });
      g.last = m;
      if (m.to === s.id && !truthy(m.read)) g.unread++;
    });
    var list = [], unread = 0;
    Object.keys(found).forEach(function (id) {
      var u = map[id], g = found[id];
      if (!u || truthy(u.banned) || mine.indexOf(id) >= 0) return;
      unread += g.unread;
      list.push({ user: publicUser(u, false), last: dmPublic(g.last), unread: g.unread });
    });
    list.sort(function (x, y) { return String(y.last.created).localeCompare(String(x.last.created)); });
    return { ok: true, conversations: list, unread: unread };
  },

  /** One conversation. With `after`, only what is new. Opening it marks the other person's messages as read. */
  dmThread: function (b) {
    var s = auth(b);
    var t = userById(String(b.userId || ''));
    if (!t) fail('User not found.');
    var pair = dmPair(s.id, t.id), c = CacheService.getScriptCache();
    var all = dmCached(pair), list = all;
    if (b.after) {
      var i = -1;
      for (var k = all.length - 1; k >= 0; k--) if (all[k].id === b.after) { i = k; break; }
      list = i >= 0 ? all.slice(i + 1) : all.slice(-60);
    } else list = all.slice(-60);
    var unreadKey = 'dmu:' + s.id + ':' + t.id;
    if (c.get(unreadKey) !== '0') {
      withLock(function () {
        rows('DMs').forEach(function (m) {
          if (m.pair === pair && m.to === s.id && !truthy(m.read)) update('DMs', m._row, { read: true });
        });
      });
      c.put(unreadKey, '0', 21600);
    }
    var lastFromThem = null;
    all.forEach(function (m) { if (m.from === t.id) lastFromThem = m; });
    if (lastFromThem) c.put('dmr:' + pair + ':' + s.id, String(lastFromThem.created), 21600);
    var o = { ok: true, messages: list, deleted: JSON.parse(c.get('dmd:' + pair) || '[]'), seenUpTo: c.get('dmr:' + pair + ':' + t.id) || '' };
    if (!b.after) { o.user = publicUser(t, false); o.canSend = dmAllowed(s, t); }
    return o;
  },

  dmSend: function (b) {
    var s = auth(b);
    var t = userById(String(b.userId || ''));
    if (!dmAllowed(s, t)) fail('You can only send private messages to friends.');
    var text = String(b.text || '').trim().slice(0, 2000);
    if (!text && !b.file) fail('Type a message first.');
    assertClean(text);
    if (s.role !== 'admin' && tooMany('dm:' + s.id, 40)) fail('Slow down a little.');
    var fileId = '', fileName = '', fileType = '', fileSize = 0;
    if (b.file) {
      var d = String(b.file).replace(/^data:[^,]+,/, '');
      if (d.length > 11000000) fail('That file is too big. The limit is 8 MB.');
      var bytes = Utilities.base64Decode(d);
      if (bytes.length < 8) fail('That file is empty.');
      fileName = String(b.fileName || 'file').replace(/[\\/:*?"<>|\r\n]/g, '_').slice(0, 120);
      var kind = dmFileKind(bytes, fileName);
      // never shared by link: the only way to it is dmFile, which checks who is asking
      fileId = namedFolder('DM_FOLDER', 'ASU Bank Private Files').createFile(Utilities.newBlob(bytes, kind.mime, newId() + '-' + fileName)).getId();
      fileType = kind.type;
      fileSize = bytes.length;
    }
    return withLock(function () {
      var pair = dmPair(s.id, t.id);
      var m = { id: newId(), pair: pair, from: s.id, to: t.id, text: cell(text), created: new Date().toISOString(), deleted: false,
                fileId: fileId, fileName: fileName, fileType: fileType, fileSize: fileSize, read: false };
      var list = dmCached(pair);
      append('DMs', m);
      m.text = text;
      list.push(dmPublic(m));
      dmStore(pair, list.slice(-80));
      CacheService.getScriptCache().put('dmu:' + t.id + ':' + s.id, '1', 21600);
      privateEvent({ toUserId: t.id }, 'dm', s, (text || fileName).slice(0, 120));
      return { ok: true, message: dmPublic(m) };
    });
  },

  /** A file in a private chat, only for the two people in it */
  dmFile: function (b) {
    var s = auth(b);
    var m = rows('DMs').filter(function (x) { return x.id === b.id; })[0];
    if (!m || truthy(m.deleted) || !m.fileId || (m.from !== s.id && m.to !== s.id)) fail('File not found.');
    var blob = DriveApp.getFileById(m.fileId).getBlob();
    return { ok: true, data: Utilities.base64Encode(blob.getBytes()), name: String(m.fileName), type: String(m.fileType) };
  },

  dmDelete: function (b) {
    var s = auth(b);
    return withLock(function () {
      var m = rows('DMs').filter(function (x) { return x.id === b.id; })[0];
      if (!m) return { ok: true };
      if (m.from !== s.id) fail('You can only delete your own messages.');
      if (m.fileId) { try { DriveApp.getFileById(m.fileId).setTrashed(true); } catch (e) {} }
      update('DMs', m._row, { deleted: true, text: '', fileId: '', fileName: '', fileType: '', fileSize: 0 });
      var c = CacheService.getScriptCache();
      dmStore(m.pair, dmCached(m.pair).filter(function (x) { return x.id !== m.id; }));
      var gone = JSON.parse(c.get('dmd:' + m.pair) || '[]');
      gone.push(m.id);
      c.put('dmd:' + m.pair, JSON.stringify(gone.slice(-40)), 21600);
      return { ok: true };
    });
  },

  /** Posts anyone may read, no account needed */
  publicFeed: function (b) {
    var offset = Math.max(0, Number(b.offset) || 0);
    var c = CacheService.getScriptCache(), key = 'pf:' + offset, hit = c.get(key);
    if (hit) return JSON.parse(hit);
    var counts = {}, map = usersById();
    rows('Comments').forEach(function (x) { if (!truthy(x.deleted)) counts[x.postId] = (counts[x.postId] || 0) + 1; });
    var posts = rows('Posts').filter(function (p) {
      var u = map[p.userId];
      return p.audience === 'public' && !truthy(p.deleted) && u && !truthy(u.banned);
    }).reverse().slice(offset, offset + 25).map(function (p) {
      return { id: p.id, text: String(p.text), image: String(p.image || ''), audience: 'public', comments: counts[p.id] || 0, created: p.created,
               likes: String(p.likes || '').split(',').filter(String).length, liked: false, user: guestUser(map[p.userId]) };
    });
    var o = { ok: true, posts: posts };
    var j = JSON.stringify(o);
    if (j.length < 95000) c.put(key, j, 60);
    return o;
  },

  /** Stories anyone may see, no account needed */
  publicStories: function (b) {
    var c = CacheService.getScriptCache(), hit = c.get('ps');
    if (hit) return JSON.parse(hit);
    var map = usersById(), now = Date.now(), groups = {};
    rows('Stories').forEach(function (r) {
      var u = map[r.userId];
      if (r.audience !== 'public' || !u || truthy(u.banned) || now - new Date(r.created).getTime() > STORY_SHOWN_MS) return;
      var g = groups[r.userId] || (groups[r.userId] = { user: guestUser(u), stories: [], seen: false, newest: '' });
      var when = new Date(r.created).toISOString();
      g.stories.push({ id: r.id, image: String(r.image || ''), video: String(r.video || ''), text: String(r.text || ''), bg: String(r.bg || ''), audience: 'public', created: when, seen: false });
      if (when > g.newest) g.newest = when;
    });
    var list = Object.keys(groups).map(function (k) { return groups[k]; });
    list.sort(function (x, y) { return y.newest.localeCompare(x.newest); });
    var o = { ok: true, groups: list };
    var j = JSON.stringify(o);
    if (j.length < 95000) c.put('ps', j, 60);
    return o;
  },

  comments: function (b) {
    var s = auth(b);
    var map = usersById();
    var mine = blockedIds(map[s.id]);
    var list = rows('Comments').filter(function (c) {
      var u = map[c.userId];
      return c.postId === b.postId && !truthy(c.deleted) && u && !truthy(u.banned) && mine.indexOf(c.userId) < 0 && blockedIds(u).indexOf(s.id) < 0;
    }).map(function (c) { return { id: c.id, text: String(c.text), created: c.created, user: publicUser(map[c.userId], false) }; });
    return { ok: true, comments: list.slice(-200) };
  },

  commentAdd: function (b) {
    var s = auth(b);
    var text = String(b.text || '').trim().slice(0, 500);
    if (!text) fail('Write something first.');
    assertClean(text);
    if (tooMany('comment:' + s.id, 30)) fail('Too many comments. Try again later.');
    return withLock(function () {
      var p = rows('Posts').filter(function (x) { return x.id === b.postId; })[0];
      if (!p || truthy(p.deleted)) fail('Post not found.');
      var owner = userById(p.userId);
      if (owner && (blockedIds(owner).indexOf(s.id) >= 0 || blockedIds(userById(s.id)).indexOf(owner.id) >= 0)) fail("You can't do this with this person.");
      var c = { id: newId(), postId: p.id, userId: s.id, text: cell(text), created: new Date().toISOString(), deleted: false };
      append('Comments', c);
      if (p.userId !== s.id) privateEvent({ toUserId: p.userId }, 'post_comment', s, text.slice(0, 140));
      return { ok: true, id: c.id };
    });
  },

  commentDelete: function (b) {
    var s = auth(b);
    return withLock(function () {
      var c = rows('Comments').filter(function (x) { return x.id === b.id; })[0];
      if (!c) fail('Not found.');
      var p = rows('Posts').filter(function (x) { return x.id === c.postId; })[0];
      if (c.userId !== s.id && s.role !== 'admin' && !(p && p.userId === s.id)) fail('You can only delete your own comments.');
      update('Comments', c._row, { deleted: true, text: '' });
      return { ok: true };
    });
  },

  // MARK: stories

  /** Stories from the last 24 hours, one group per person: mine first, then ones not seen yet, newest first */
  stories: function (b) {
    var s = auth(b);
    try { purgeStories(); } catch (e) {}
    var map = usersById(), mine = blockedIds(map[s.id]), now = Date.now(), groups = {}, pals = friendIds(s.id);
    rows('Stories').forEach(function (r) {
      var u = map[r.userId];
      if (!u || truthy(u.banned) || mine.indexOf(r.userId) >= 0 || blockedIds(u).indexOf(s.id) >= 0) return;
      if (r.audience === 'friends' && r.userId !== s.id && !pals[r.userId] && s.role !== 'admin') return;
      if (now - new Date(r.created).getTime() > STORY_SHOWN_MS) return;
      var views = String(r.views || '').split(',').filter(String);
      var g = groups[r.userId] || (groups[r.userId] = { user: publicUser(u, false), stories: [], seen: true, newest: '' });
      var seen = r.userId === s.id || views.indexOf(s.id) >= 0;
      var o = { id: r.id, image: String(r.image || ''), video: String(r.video || ''), audience: audienceOf(r), text: String(r.text || ''), bg: String(r.bg || ''), created: new Date(r.created).toISOString(), seen: seen };
      if (r.userId === s.id) o.views = views.length;
      g.stories.push(o);
      if (!seen) g.seen = false;
      if (o.created > g.newest) g.newest = o.created;
    });
    var list = Object.keys(groups).map(function (k) { return groups[k]; });
    list.sort(function (a, c) {
      var am = a.user.id === s.id, cm = c.user.id === s.id;
      if (am !== cm) return am ? -1 : 1;
      if (a.seen !== c.seen) return a.seen ? 1 : -1;
      return c.newest.localeCompare(a.newest);
    });
    return { ok: true, groups: list };
  },

  storyCreate: function (b) {
    var s = auth(b);
    var text = String(b.text || '').trim().slice(0, 300);
    if (!text && !b.image && !b.video) fail('Add a photo or some text first.');
    assertClean(text);
    if (tooMany('story:' + s.id, 15)) fail('Too many stories. Try again later.');
    var image = b.image ? savePhoto(namedFolder('STORY_FOLDER', 'ASU Bank Stories'), b.image, s.id + '-' + Date.now(), 2500000, 1280) : '';
    var video = b.video ? saveVideo(namedFolder('STORY_FOLDER', 'ASU Bank Stories'), b.video, s.id + '-' + Date.now()) : '';
    var bg = /^#[0-9a-fA-F]{6}$/.test(String(b.bg || '')) ? String(b.bg) : '#8C1D40';
    return withLock(function () {
      var r = { id: newId(), userId: s.id, image: image, text: cell(text), bg: bg, created: new Date().toISOString(), views: '', audience: audienceOf(b), video: video };
      append('Stories', r);
      return { ok: true, id: r.id };
    });
  },

  storySeen: function (b) {
    var s = auth(b);
    return withLock(function () {
      var r = rows('Stories').filter(function (x) { return x.id === b.id; })[0];
      if (!r || r.userId === s.id) return { ok: true };
      var views = String(r.views || '').split(',').filter(String);
      if (views.indexOf(s.id) < 0) { views.push(s.id); update('Stories', r._row, { views: views.join(',') }); }
      return { ok: true };
    });
  },

  storyDelete: function (b) {
    var s = auth(b);
    return withLock(function () {
      var r = rows('Stories').filter(function (x) { return x.id === b.id; })[0];
      if (!r) return { ok: true };
      if (r.userId !== s.id && s.role !== 'admin') fail('You can only delete your own stories.');
      removeAvatarFile(r.image);
      removeAvatarFile(r.video);
      sheet('Stories').deleteRow(r._row);
      if (r.userId !== s.id) log(s, 'deleteStory', r.userId, '');
      return { ok: true };
    });
  },

  /** Every registered person: online first, then by last seen. Someone who never came back shows last. */
  online: function (b) {
    var s = auth(b);
    var p = presence(), now = Date.now(), users = rows('Users');
    var me = users.filter(function (u) { return u.id === s.id; })[0];
    var mine = blockedIds(me);
    var list = users.filter(function (u) {
      return !truthy(u.banned) && mine.indexOf(u.id) < 0 && blockedIds(u).indexOf(s.id) < 0;
    }).map(function (u) {
      return { id: u.id, displayName: String(u.displayName || u.username), avatar: u.avatar || '', color: u.color || '', role: u.role || 'user', badges: badgeList(u),
               lastSeen: lastActive(u) || '', online: !!p[u.id] && now - new Date(p[u.id]) < ONLINE_MS };
    });
    list.sort(function (a, c) {
      if (a.online !== c.online) return a.online ? -1 : 1;
      return String(c.lastSeen).localeCompare(String(a.lastSeen)) || a.displayName.localeCompare(c.displayName);
    });
    return { ok: true, users: list, total: list.length, onlineCount: list.filter(function (x) { return x.online; }).length };
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
      rooms: allRooms().length,
      openReports: rows('Reports').filter(function (r) { return r.status === 'open'; }).length
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
      removeAvatarFile(u.cover);
      purgeSocial(u.id);
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

  /**
   * Removes a file from ASU Bank for everyone and moves it to the Drive trash when this account may do that.
   * The file's id goes on the deleted list that the apps and the site hide, because the other indexes keep listing a file for a while.
   * Folders, sheets, forms and scripts are only hidden, never trashed: the backend's own files live in this Drive.
   */
  adminDeleteUpload: function (b) {
    var a = auth(b, { admin: true });
    var id = String(b.fileId || '');
    if (!/^[-\w]{10,}$/.test(id)) fail('This is not a Google Drive file.');
    if (id === PropertiesService.getScriptProperties().getProperty('SHEET_ID')) fail('This file cannot be deleted from the app.');
    var f = null, name = id, trashed = false, ids = [id];
    try { f = DriveApp.getFileById(id); name = f.getName(); } catch (e) { f = null; }
    if (f) {
      var type = String(f.getMimeType());
      var keep = ['folder', 'spreadsheet', 'form', 'script'].some(function (k) { return type === 'application/vnd.google-apps.' + k; });
      if (!keep) {
        var target = null;
        try { target = f.getTargetId(); } catch (e) { target = null; }
        try { f.setTrashed(true); trashed = true; } catch (e) { trashed = false; }
        if (target) {
          ids.push(target);
          try { DriveApp.getFileById(target).setTrashed(true); trashed = true; } catch (e) { /* the real file is in someone else's Drive */ }
        }
      }
    }
    return withLock(function () {
      var known = {};
      rows('DeletedFiles').forEach(function (r) { known[r.id] = 1; });
      ids.forEach(function (x) {
        if (!known[x]) append('DeletedFiles', { id: x, name: cell(String(name).slice(0, 300)), time: new Date().toISOString(), adminId: a.id, trashed: trashed });
      });
      CacheService.getScriptCache().remove('deletedFiles');
      log(a, 'deleteFile', String(name).slice(0, 120), id + (trashed ? ' (in the Drive trash)' : ' (hidden only)'));
      return { ok: true, trashed: trashed, ids: ids };
    });
  },

  /** Files admins removed; the apps and the site hide them everywhere. No sign-in: these are ids only. */
  deletedFiles: function () {
    var c = CacheService.getScriptCache();
    var j = c.get('deletedFiles');
    if (j) return { ok: true, ids: JSON.parse(j) };
    var ids = rows('DeletedFiles').map(function (r) { return String(r.id); });
    try { c.put('deletedFiles', JSON.stringify(ids), 21600); } catch (e) { /* too big for the cache: read the sheet each time */ }
    return { ok: true, ids: ids };
  },

  /** "Suggest materials" from the apps and the website; admins read them in Admin > Suggestions. No sign-in needed. */
  suggest: function (b) {
    var me = b.token ? auth(b) : null;
    var note = String(b.note || '').trim().slice(0, 2000);
    var contact = String(b.contact || '').trim().slice(0, 120);
    if (note.length < 20) fail('Please give more details (at least 20 characters).');
    assertClean(note);
    if (tooMany(me ? 'suggest:' + me.id : 'suggest:guest', me ? 10 : 40)) fail('Too many suggestions. Try again later.');
    return withLock(function () {
      var who = me ? me.username : 'Guest';
      append('Suggestions', { id: newId(), created: new Date().toISOString(), userId: me ? me.id : '', username: who, note: cell(note), contact: cell(contact),
                              status: 'open', resolvedBy: '', resolvedAt: '' });
      try {
        MailApp.sendEmail(OWNER_EMAIL, 'ASU Bank suggestion from ' + who, note + '\n\nContact: ' + (contact || 'none') + '\n\nIn the app: Admin > Suggestions, or https://asubank.org/admin/');
      } catch (err) { /* it is saved even when the daily email limit is used up */ }
      return { ok: true };
    });
  },

  adminSuggestions: function (b) {
    auth(b, { admin: true });
    var all = rows('Suggestions');
    var list = all.filter(function (r) { return truthy(b.all) || b.all === true || r.status === 'open'; });
    return { ok: true, pending: all.filter(function (r) { return r.status === 'open'; }).length,
             suggestions: list.slice(-200).reverse().map(function (r) { delete r._row; return r; }) };
  },

  adminSuggestionSet: function (b) {
    var a = auth(b, { admin: true });
    var status = String(b.status || '');
    if (['open', 'done', 'dismissed'].indexOf(status) < 0) fail('Bad status.');
    return withLock(function () {
      var r = rows('Suggestions').filter(function (x) { return x.id === b.id; })[0];
      if (!r) fail('Suggestion not found.');
      update('Suggestions', r._row, { status: status, resolvedBy: a.username, resolvedAt: new Date().toISOString() });
      log(a, 'suggestion ' + status, r.username, String(r.note).slice(0, 80));
      return { ok: true };
    });
  },

  adminLog: function (b) {
    auth(b, { admin: true });
    return { ok: true, log: rows('Log').slice(-100).reverse().map(function (r) { delete r._row; return r; }) };
  }
};
