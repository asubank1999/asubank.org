/**
 * ASU Bank accounts, chat and admin API (used by asubank.org, the iOS app and the Android app).
 *
 * Setup (once):
 *  1. https://script.google.com > New project, name it "ASU Bank Accounts", paste this file.
 *  2. Run `setup` once from the editor and approve the permissions. It creates the "ASU Bank Accounts"
 *     spreadsheet in your Drive (users, sessions, messages, log) and remembers it.
 *  3. Deploy > New deployment > Web app: Execute as "Me", Who has access "Anyone". Put the /exec URL in
 *     assets/app/app-config.json ("accountsApi") so the site and both apps pick it up.
 *  After editing, Deploy > Manage deployments > edit > New version, so the URL stays the same.
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
var ROOMS = [
  { id: 'general', name: 'General', emoji: '💬', about: 'Say hi, ask anything about ASU' },
  { id: 'help', name: 'Study help', emoji: '🆘', about: 'Stuck on something? Ask other students' },
  { id: 'materials', name: 'Materials', emoji: '📚', about: 'Ask for or share study files' },
  { id: 'medical', name: 'Pharmacy & health', emoji: '💊', about: 'Pharmacy, nutrition, physiotherapy' },
  { id: 'tech', name: 'IT & engineering', emoji: '💻', about: 'IT, computer science, engineering' },
  { id: 'business', name: 'Business & arts', emoji: '📈', about: 'Business, finance, law, arts' },
  { id: 'announcements', name: 'Announcements', emoji: '📣', about: 'News from the ASU Bank team', adminOnly: true }
];
var SESSION_DAYS = 120;
var MAX_TEXT = 1000;
var CACHE_MSGS = 150;

var SHEETS = {
  Users: ['id', 'username', 'email', 'uniId', 'displayName', 'salt', 'hash', 'role', 'banned', 'banReason', 'created', 'lastSeen', 'color', 'mustReset'],
  Sessions: ['token', 'userId', 'created', 'lastUsed', 'platform'],
  Messages: ['id', 'room', 'userId', 'username', 'displayName', 'role', 'text', 'created', 'deleted', 'replyTo', 'color'],
  Log: ['time', 'adminId', 'action', 'target', 'detail']
};

// MARK: - entry points

function doPost(e) {
  var body;
  try { body = JSON.parse(e.postData.contents || '{}'); } catch (err) { return out({ ok: false, error: 'Bad request.' }); }
  try {
    var fn = ACTIONS[body.action];
    if (!fn) return out({ ok: false, error: 'Unknown action.' });
    return out(fn(body));
  } catch (err) {
    return out({ ok: false, error: err.userMessage || ('Something went wrong: ' + err.message) });
  }
}

function doGet(e) {
  // read-only status for health checks
  return out({ ok: true, service: 'asubank-accounts', rooms: publicRooms() });
}

function out(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }

function fail(msg) { var e = new Error(msg); e.userMessage = msg; throw e; }

// Run once from the editor
function setup() {
  var p = PropertiesService.getScriptProperties();
  var id = p.getProperty('SHEET_ID');
  var ss = id ? SpreadsheetApp.openById(id) : SpreadsheetApp.create('ASU Bank Accounts');
  p.setProperty('SHEET_ID', ss.getId());
  Object.keys(SHEETS).forEach(function (name) {
    var sh = ss.getSheetByName(name) || ss.insertSheet(name);
    if (sh.getLastRow() === 0) { sh.appendRow(SHEETS[name]); sh.setFrozenRows(1); }
  });
  var first = ss.getSheetByName('Sheet1');
  if (first && ss.getSheets().length > 1) ss.deleteSheet(first);
  DriveApp.getFolderById(UPLOAD_FOLDER_ID).getName(); // asks for Drive access now
  MailApp.getRemainingDailyQuota(); // asks for email access now
  Logger.log('Ready: ' + ss.getUrl());
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

function publicUser(u, full) {
  var o = { id: u.id, username: u.username, displayName: u.displayName || u.username, role: u.role || 'user', color: u.color || '#8C1D40' };
  if (full) {
    o.email = u.email || ''; o.uniId = u.uniId ? String(u.uniId) : '';
    o.banned = u.banned === true || u.banned === 'TRUE'; o.banReason = u.banReason || '';
    o.created = u.created; o.lastSeen = u.lastSeen; o.mustReset = u.mustReset === true || u.mustReset === 'TRUE';
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
function validPassword(s) { return String(s || '').length >= 6 && String(s).length <= 100; }

function tooMany(key, max) {
  var c = CacheService.getScriptCache();
  var n = Number(c.get(key) || 0) + 1;
  c.put(key, String(n), 900);
  return n > max;
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
          banned: full.banned === true || full.banned === 'TRUE', banReason: full.banReason, _row: full._row };
    cache.put('s:' + t, JSON.stringify(u), 60);
    if (!full.lastSeen || new Date() - new Date(full.lastSeen) > 10 * 60000) update('Users', full._row, { lastSeen: new Date().toISOString() });
  }
  if (u.banned) fail('This account is banned' + (u.banReason ? ': ' + u.banReason : '.'));
  if (opts && opts.admin && u.role !== 'admin') fail('Only admins can do that.');
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

function forgetUser(userId) {
  var cache = CacheService.getScriptCache();
  rows('Sessions').filter(function (s) { return s.userId === userId; }).forEach(function (s) { cache.remove('s:' + s.token); });
}

// MARK: - chat cache

function publicRooms() {
  return ROOMS.map(function (r) { return { id: r.id, name: r.name, emoji: r.emoji, about: r.about, adminOnly: !!r.adminOnly }; });
}

function roomMessages(room) {
  var cache = CacheService.getScriptCache();
  var c = cache.get('m:' + room);
  if (c) return JSON.parse(c);
  var list = rows('Messages').filter(function (m) { return m.room === room && !(m.deleted === true || m.deleted === 'TRUE'); })
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
           role: m.role || 'user', text: m.text, created: m.created, replyTo: m.replyTo || '', color: m.color || '#8C1D40' };
}

function latestMeta() {
  var c = CacheService.getScriptCache().get('latest');
  return c ? JSON.parse(c) : null;
}

// MARK: - actions

var ACTIONS = {
  rooms: function () { return { ok: true, rooms: publicRooms() }; },

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
                created: new Date().toISOString(), lastSeen: new Date().toISOString(), color: COLORS[all.length % COLORS.length], mustReset: false };
      append('Users', u);
      u.uniId = uniId;
      return { ok: true, token: newSession(u, b.platform), user: publicUser(u, true) };
    });
  },

  login: function (b) {
    var key = 'login:' + norm(b.login);
    if (tooMany(key, 8)) fail('Too many tries. Wait 15 minutes and try again.');
    var u = findUser(b.login);
    if (!u || hashPassword(b.password, u.salt) !== u.hash) fail('Wrong username or password.');
    if (u.banned === true || u.banned === 'TRUE') fail('This account is banned' + (u.banReason ? ': ' + u.banReason : '.'));
    CacheService.getScriptCache().remove(key);
    if (isAdminIdentity(u) && u.role !== 'admin') { update('Users', u._row, { role: 'admin' }); u.role = 'admin'; }
    return { ok: true, token: newSession(u, b.platform), user: publicUser(u, true) };
  },

  me: function (b) {
    var s = auth(b);
    var u = userById(s.id);
    return { ok: true, user: publicUser(u, true), rooms: publicRooms(), latest: latestMeta() };
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
      if (!validEmail(email)) fail('That email does not look right.');
      if (!validUniId(uniId)) fail('University IDs are numbers only.');
      var others = rows('Users').filter(function (x) { return x.id !== u.id; });
      if (email && others.some(function (x) { return norm(x.email) === norm(email); })) fail('An account already uses that email.');
      if (uniId && others.some(function (x) { return norm(x.uniId) === norm(uniId); })) fail('An account already uses that university ID.');
      // nobody can add an admin email or ID to an existing account
      if (u.role !== 'admin' && isAdminIdentity({ email: email, uniId: uniId })) fail('That email or ID is reserved.');
      var ch = { displayName: String(b.displayName || u.displayName).trim().slice(0, 40) || u.username, email: email, uniId: uniId ? "'" + uniId : '' };
      update('Users', u._row, ch);
      forgetUser(u.id);
      u.displayName = ch.displayName; u.email = email; u.uniId = uniId;
      return { ok: true, user: publicUser(u, true) };
    });
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
      sheet('Users').deleteRow(userById(u.id)._row);
      return { ok: true };
    });
  },

  // MARK: chat

  /** New messages in a room after a message id (or the latest ones) */
  chat: function (b) {
    auth(b);
    var room = String(b.room || 'general');
    if (!ROOMS.some(function (r) { return r.id === room; })) fail('Unknown room.');
    var list = roomMessages(room);
    if (b.after) {
      var i = -1;
      for (var k = list.length - 1; k >= 0; k--) if (list[k].id === b.after) { i = k; break; }
      list = i >= 0 ? list.slice(i + 1) : list.slice(-60);
    } else list = list.slice(-60);
    var gone = CacheService.getScriptCache().get('d:' + room);
    return { ok: true, messages: list, deleted: gone ? JSON.parse(gone) : [], latest: latestMeta() };
  },

  send: function (b) {
    var u = auth(b);
    var room = String(b.room || 'general');
    var r = ROOMS.filter(function (x) { return x.id === room; })[0];
    if (!r) fail('Unknown room.');
    if (r.adminOnly && u.role !== 'admin') fail('Only admins can post here.');
    var text = String(b.text || '').trim().slice(0, MAX_TEXT);
    if (!text) fail('Type a message first.');
    if (u.role !== 'admin' && tooMany('send:' + u.id, 20)) fail('Slow down a little.');
    return withLock(function () {
      var m = { id: newId(), room: room, userId: u.id, username: u.username, displayName: u.displayName || u.username, role: u.role,
                text: text, created: new Date().toISOString(), deleted: false, replyTo: String(b.replyTo || '').slice(0, 20), color: u.color };
      append('Messages', m);
      var list = roomMessages(room);
      list.push(publicMessage(m));
      putRoom(room, list.slice(-CACHE_MSGS));
      CacheService.getScriptCache().put('latest', JSON.stringify({ id: m.id, room: room, roomName: r.name, displayName: m.displayName,
        username: m.username, userId: m.userId, text: text.slice(0, 120), created: m.created, announcement: !!r.adminOnly }), 21600);
      return { ok: true, message: publicMessage(m) };
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

  /** What the apps check in the background for notifications */
  latest: function (b) {
    auth(b);
    return { ok: true, latest: latestMeta() };
  },

  // MARK: admin

  adminStats: function (b) {
    auth(b, { admin: true });
    var users = rows('Users');
    var day = new Date(Date.now() - 864e5).toISOString();
    var week = new Date(Date.now() - 7 * 864e5).toISOString();
    var msgs = rows('Messages');
    return { ok: true, stats: {
      users: users.length,
      admins: users.filter(function (u) { return u.role === 'admin'; }).length,
      banned: users.filter(function (u) { return u.banned === true || u.banned === 'TRUE'; }).length,
      activeToday: users.filter(function (u) { return String(u.lastSeen) > day; }).length,
      activeWeek: users.filter(function (u) { return String(u.lastSeen) > week; }).length,
      newWeek: users.filter(function (u) { return String(u.created) > week; }).length,
      messages: msgs.length,
      messagesToday: msgs.filter(function (m) { return String(m.created) > day; }).length
    } };
  },

  adminUsers: function (b) {
    auth(b, { admin: true });
    var q = norm(b.q);
    var list = rows('Users').filter(function (u) {
      return !q || [u.username, u.email, u.uniId, u.displayName].some(function (v) { return norm(v).indexOf(q) >= 0; });
    }).map(function (u) { return publicUser(u, true); });
    list.sort(function (a, c) { return String(c.created).localeCompare(String(a.created)); });
    return { ok: true, users: list };
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

  adminDeleteUser: function (b) {
    var a = auth(b, { admin: true });
    var u = userById(b.userId);
    if (!u) fail('User not found.');
    if (isAdminIdentity(u) || u.id === a.id) fail('This account cannot be deleted here.');
    return withLock(function () {
      dropUserSessions(u.id);
      sheet('Users').deleteRow(userById(u.id)._row);
      log(a, 'deleteUser', u.username, '');
      return { ok: true };
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
