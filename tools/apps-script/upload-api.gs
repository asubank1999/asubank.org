/**
 * ASU Bank upload API for the drag-and-drop uploader on /upload/uploadmethod.
 *
 * Setup (once, about 3 minutes):
 *  1. Open https://script.google.com and create a new project (or open the existing uploader project).
 *  2. Paste this file in, and set FOLDER_ID below to the Drive folder uploads should go to
 *     (the current "Instant upload" folder is 1ubxr47KfFwm7ocn0ikCpJLp79-dxe8aB).
 *  3. Deploy > New deployment > type "Web app": Execute as "Me", Who has access "Anyone".
 *  4. Copy the Web app URL (ends in /exec) and paste it into UPLOAD_API in upload/uploadmethod.html.
 *
 * Deployed: project "ASU Bank Upload API" (necro1442@gmail.com), web app
 *   https://script.google.com/macros/s/AKfycbygD_bFuQN7k9zLKogIip_zEaXmsq9Vw-z6QPfMte1AAuLdq4Vf4-kGIgvy6hgVcOo/exec
 *   After editing, use Deploy > Manage deployments > edit > New version so the URL stays the same.
 *
 * The page sends one POST per file with Content-Type text/plain (avoids a CORS preflight):
 *   { name, mimeType, data (base64, no data: prefix), subject, kind, year, note }
 * and expects JSON back: { success: true, id, url } or { success: false, error }.
 */
var FOLDER_ID = '1ubxr47KfFwm7ocn0ikCpJLp79-dxe8aB';
var MAX_BYTES = 25 * 1024 * 1024;
var ALLOWED = /\.(pdf|docx?|pptx?|xlsx?|txt|jpe?g|png|heic|webp|zip|rar|7z|csv|mp4|mov|m4v|webm|mp3|m4a)$/i;

/** "[Subject] [Final 2025] original name.pdf" keeps the Drive folder sorted and searchable */
function taggedName(body, name) {
  var tags = [body.subject, [body.kind, body.year].filter(String).join(' ')].filter(function (t) { return t && String(t).trim(); });
  return tags.map(function (t) { return '[' + String(t).trim().slice(0, 60) + ']'; }).join(' ') + (tags.length ? ' ' : '') + name;
}

/**
 * Opens a Drive resumable upload for the apps: { action: 'start', name, mimeType, size, subject, kind, year, note }.
 * The app then PUTs the file in chunks straight to the returned uploadUrl, so the size limit of this script doesn't apply.
 * uploadUrl works only for this one file. The script's own Drive token never leaves the server.
 */
function startUpload(body) {
  var name = String(body.name || '').replace(/[\\/:*?"<>|]+/g, ' ').trim().slice(0, 180);
  if (!name || !ALLOWED.test(name)) return reply({ success: false, error: 'This file type is not accepted.' });
  var size = Number(body.size) || 0;
  if (size <= 0) return reply({ success: false, error: 'The file is empty.' });
  var mime = String(body.mimeType || 'application/octet-stream');
  var meta = { name: taggedName(body, name), mimeType: mime, parents: [FOLDER_ID] };
  if (body.note) meta.description = String(body.note).slice(0, 500);
  var r = UrlFetchApp.fetch('https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&fields=id,name', {
    method: 'post', contentType: 'application/json; charset=UTF-8', payload: JSON.stringify(meta), muteHttpExceptions: true,
    headers: { Authorization: 'Bearer ' + ScriptApp.getOAuthToken(), 'X-Upload-Content-Type': mime, 'X-Upload-Content-Length': String(size) }
  });
  var h = r.getAllHeaders();
  var url = h.Location || h.location;
  if (r.getResponseCode() !== 200 || !url) return reply({ success: false, error: 'Upload could not start. Please try again.' });
  return reply({ success: true, uploadUrl: url, name: meta.name });
}

function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents);
    if (body.action === 'start') return startUpload(body);
    var name = String(body.name || '').replace(/[\\/:*?"<>|]+/g, ' ').trim().slice(0, 180);
    if (!name || !ALLOWED.test(name)) return reply({ success: false, error: 'This file type is not accepted.' });
    var bytes = Utilities.base64Decode(String(body.data || ''));
    if (!bytes.length) return reply({ success: false, error: 'The file is empty.' });
    if (bytes.length > MAX_BYTES) return reply({ success: false, error: 'Files must be 25 MB or smaller.' });

    var finalName = taggedName(body, name);

    var blob = Utilities.newBlob(bytes, String(body.mimeType || 'application/octet-stream'), finalName);
    var file = DriveApp.getFolderById(FOLDER_ID).createFile(blob);
    if (body.note) file.setDescription(String(body.note).slice(0, 500));
    return reply({ success: true, id: file.getId(), url: file.getUrl(), name: finalName });
  } catch (err) {
    return reply({ success: false, error: 'Upload failed: ' + err.message });
  }
}

function doGet() {
  return reply({ success: true, service: 'asubank-upload', maxBytes: MAX_BYTES });
}

function reply(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

// Run once from the editor to grant Drive access and confirm the folder is reachable.
// (The apps' big uploads also use UrlFetchApp, which this asks permission for.)
function checkFolder() {
  UrlFetchApp.getRequest('https://www.googleapis.com/');
  var f = DriveApp.getFolderById(FOLDER_ID);
  Logger.log('Upload folder: ' + f.getName() + ' (owner: ' + f.getOwner().getEmail() + ')');
}
