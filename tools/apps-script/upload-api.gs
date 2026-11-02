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
 * The page sends one POST per file with Content-Type text/plain (avoids a CORS preflight):
 *   { name, mimeType, data (base64, no data: prefix), subject, kind, year, note }
 * and expects JSON back: { success: true, id, url } or { success: false, error }.
 */
var FOLDER_ID = '1ubxr47KfFwm7ocn0ikCpJLp79-dxe8aB';
var MAX_BYTES = 25 * 1024 * 1024;
var ALLOWED = /\.(pdf|docx?|pptx?|xlsx?|txt|jpe?g|png|heic|webp|zip|rar)$/i;

function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents);
    var name = String(body.name || '').replace(/[\\/:*?"<>|]+/g, ' ').trim().slice(0, 180);
    if (!name || !ALLOWED.test(name)) return reply({ success: false, error: 'This file type is not accepted.' });
    var bytes = Utilities.base64Decode(String(body.data || ''));
    if (!bytes.length) return reply({ success: false, error: 'The file is empty.' });
    if (bytes.length > MAX_BYTES) return reply({ success: false, error: 'Files must be 25 MB or smaller.' });

    // "[Subject] [Final 2025] original name.pdf" keeps the Drive folder sorted and searchable
    var tags = [body.subject, [body.kind, body.year].filter(String).join(' ')].filter(function (t) { return t && String(t).trim(); });
    var finalName = tags.map(function (t) { return '[' + String(t).trim().slice(0, 60) + ']'; }).join(' ') + (tags.length ? ' ' : '') + name;

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
