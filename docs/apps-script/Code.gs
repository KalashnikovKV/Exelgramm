/**
 * Exelgramm — веб-API для Google Таблицы.
 *
 * Установка:
 * 1. Откройте таблицу → Расширения → Apps Script.
 * 2. Вставьте этот файл, сохраните.
 * 3. Развернуть → Новое развёртывание → Веб-приложение.
 * 4. Выполнять от имени: Я | Доступ: Все.
 * 5. Скопируйте URL (.../exec) в приложение (Профиль → URL веб-приложения).
 *
 * Лист: по умолчанию «Messages»; если нет — используется первый лист.
 * Строка 1: id | timestamp | author | text
 */

var HEADERS = ['id', 'timestamp', 'author', 'text'];

function doGet(e) {
  try {
    var spreadsheetId = e.parameter.id;
    var sheetName = e.parameter.sheet;
    if (!spreadsheetId) {
      return json_({ ok: false, error: 'Missing parameter: id' });
    }
    var sheet = getSheet_(spreadsheetId, sheetName);
    var messages = readMessages_(sheet);
    return json_({ ok: true, messages: messages });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function doPost(e) {
  try {
    if (!e.postData || !e.postData.contents) {
      return json_({ ok: false, error: 'Empty body' });
    }
    var body = JSON.parse(e.postData.contents);
    var spreadsheetId = body.spreadsheetId;
    var sheetName = body.sheet;

    if (!spreadsheetId) {
      return json_({ ok: false, error: 'Required: spreadsheetId' });
    }

    // Чтение чата (POST — без ошибки 405 на редиректах Google)
    if (body.action === 'fetch') {
      var sheetFetch = getSheet_(spreadsheetId, sheetName);
      return json_({ ok: true, messages: readMessages_(sheetFetch) });
    }

    // Отправка сообщения
    var id = body.id;
    var timestamp = body.timestamp;
    var author = body.author;
    var text = body.text;

    if (!id || !author || !text) {
      return json_({ ok: false, error: 'Required: id, author, text' });
    }

    var sheet = getSheet_(spreadsheetId, sheetName);
    ensureHeaders_(sheet);
    sheet.appendRow([
      String(id),
      timestamp || new Date().toISOString(),
      String(author),
      String(text)
    ]);
    return json_({ ok: true });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function getSheet_(spreadsheetId, sheetName) {
  var ss = SpreadsheetApp.openById(spreadsheetId);
  if (sheetName) {
    var named = ss.getSheetByName(sheetName);
    if (named) return named;
  }
  var messages = ss.getSheetByName('Messages');
  if (messages) return messages;
  return ss.getSheets()[0];
}

function ensureHeaders_(sheet) {
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(HEADERS);
    return;
  }
  var first = sheet.getRange(1, 1, 1, HEADERS.length).getValues()[0];
  var empty = first.every(function (c) { return c === '' || c === null; });
  if (empty) {
    sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]);
  }
}

function readMessages_(sheet) {
  ensureHeaders_(sheet);
  var data = sheet.getDataRange().getValues();
  if (data.length < 2) return [];

  var headers = data[0].map(function (h) { return String(h).trim().toLowerCase(); });
  var idx = {
    id: indexOf_(headers, 'id'),
    timestamp: indexOf_(headers, 'timestamp'),
    author: indexOf_(headers, 'author'),
    text: indexOf_(headers, 'text')
  };

  var textCol = idx.text >= 0 ? idx.text : 3;
  var idCol = idx.id >= 0 ? idx.id : 0;
  var timeCol = idx.timestamp >= 0 ? idx.timestamp : 1;
  var authorCol = idx.author >= 0 ? idx.author : 2;

  var out = [];
  for (var i = 1; i < data.length; i++) {
    var row = data[i];
    var text = cell_(row, textCol);
    if (!text) continue;
    out.push({
      id: cell_(row, idCol) || 'row_' + (i + 1),
      timestamp: cell_(row, timeCol) || '',
      author: cell_(row, authorCol) || 'unknown',
      text: text
    });
  }
  return out;
}

function indexOf_(headers, name) {
  var i = headers.indexOf(name);
  return i >= 0 ? i : -1;
}

function cell_(row, index) {
  if (index < 0 || index >= row.length) return '';
  var v = row[index];
  return v === null || v === undefined ? '' : String(v);
}

function json_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
