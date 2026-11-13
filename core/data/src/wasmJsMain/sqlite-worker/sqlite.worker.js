// SQLDelight web worker for Good Company: the official SQLite WebAssembly build (@sqlite.org/sqlite-wasm),
// with the database kept in the Origin Private File System so it survives page reloads.
//
// Protocol (SQLDelight's web-worker-driver, which reads responses as `{ id, results: { values }, error }`):
//   in:  { id, action: "exec" | "begin_transaction" | "end_transaction" | "rollback_transaction", sql?, params? }
//   out: { id, results: { values: rows[][] } }  — for statements without result columns, values = [[changedRows]]
//        { id, error: { name, message } }
//
// Storage: the "opfs-sahpool" VFS. Unlike the plain "opfs" VFS it needs no cross-origin isolation (COOP/COEP
// headers), so static hosting and the installed PWA keep working. It allows one connection per origin at a time:
// when OPFS is unavailable (private browsing, old browsers) or the app is already open in another tab, we fall back
// to an in-memory database. The app detects that through `PRAGMA database_list` (empty file name) and tells the user.
import sqlite3InitModule from "@sqlite.org/sqlite-wasm";

const DB_FILE = "/goodcompany.db";

let db = null;

const ready = (async () => {
  const sqlite3 = await sqlite3InitModule();
  try {
    // preserveOnInitFailure: if another tab holds the pool, fail without touching the stored data.
    const pool = await sqlite3.installOpfsSAHPoolVfs({ name: "goodcompany", preserveOnInitFailure: true });
    db = new pool.OpfsSAHPoolDb(DB_FILE);
  } catch (e) {
    console.warn("[sqlite-worker] OPFS storage unavailable; using an in-memory database that is lost on reload.", e);
    db = new sqlite3.oo1.DB(":memory:", "c");
  }
  db.exec("PRAGMA foreign_keys = ON;");
})();

function exec(sql, params) {
  const stmt = db.prepare(sql);
  try {
    if (params && params.length > 0) stmt.bind(params);
    const rows = [];
    while (stmt.step()) rows.push(stmt.get([]));
    return stmt.columnCount > 0 ? rows : [[db.changes()]];
  } finally {
    stmt.finalize();
  }
}

function handle(data) {
  switch (data && data.action) {
    case "exec":
      if (!data.sql) throw new Error("exec: missing SQL");
      return exec(data.sql, data.params);
    case "begin_transaction":
      db.exec("BEGIN TRANSACTION;");
      return [];
    case "end_transaction":
      db.exec("COMMIT;");
      return [];
    case "rollback_transaction":
      db.exec("ROLLBACK;");
      return [];
    default:
      throw new Error(`Unsupported action: ${data && data.action}`);
  }
}

self.onmessage = (event) => {
  const data = event.data;
  ready
    .then(() => postMessage({ id: data.id, results: { values: handle(data) } }))
    .catch((e) => postMessage({ id: data && data.id, error: { name: e?.name ?? "Error", message: String(e?.message ?? e) } }));
};
