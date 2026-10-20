// Reportes en la nube (Firestore): pruebas automatizadas.
//
// IMPORTANTE: estas pruebas NO escriben en Firestore real. Usan un doble en
// memoria con la misma API del Admin SDK (colecciones, transacciones, batch).
// Verifican la lógica del repositorio y de la ruta HTTP. La comprobación contra
// Firestore real (emulador o nube) es otra cosa: ver el script
// scripts/reportsCloud.emulator.test.js y el informe.
//
// Cubren:
//  1. guardar un reporte válido sin depender del backend de reportes (ruta cloud);
//  2. identidad verificada (uid/correo de la sesión, no del cuerpo);
//  6. salida inexistente / id inventado rechazado;
//  7. texto del territorio y salidas múltiples se conservan;
//  8. months.json no se modifica; los reportes viejos no se duplican.
import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, test } from 'node:test';

const directory = await mkdtemp(join(tmpdir(), 'gestor-t-cloud-'));
const dataFile = join(directory, 'months.json');
process.env.SCHEDULE_DATA_FILE = dataFile;
process.env.NOTIFICATIONS_DISABLED = '1';

const model = await import('../models/scheduleModel.js');
const cloud = await import('../services/firestore/cloud.js');
const repository = await import('../services/firestore/reportRepository.js');
const bridge = await import('../services/firestore/gestorBridge.js');
const { checkOutingMatch, normalizeReportInput } = await import('../services/firestore/reportValidation.js');
const { MemoryFirestore } = await import('../services/firestore/testSupport/memoryFirestore.js');
const { createApp } = await import('../app.js');

const db = new MemoryFirestore();
cloud.setFirestoreForTests(db);

const SALIDA_A = 'salida-a-2026-11-10';
const SALIDA_B = 'salida-b-2026-11-11';

function datos() {
  return {
    types: [], drivers: [], houses: [], locations: [], territoryLocations: [],
    territories: [], reports: [], devices: [],
    months: {
      '2026-11': {
        configuration: [],
        outings: [
          { id: SALIDA_A, date: '2026-11-10', time: '09:00', type: 'Grupo 4', driver: '', driverId: 'drv-1' },
          { id: SALIDA_B, date: '2026-11-11', time: '18:00', type: 'Grupo 3', driver: '', driverId: 'drv-2' },
        ],
        driverAvailability: {}, driverRotations: { elders: {}, groupConductors: {} },
        houseAvailability: {}, houseRotations: {}, locationAvailability: {},
      },
    },
  };
}

const CONDUCTOR = { uid: 'uid-conductor-1', email: 'Conductor@Example.com' };
const OTRO = { uid: 'uid-conductor-2', email: 'otro@example.com' };

function catalogar() {
  db.seed('reporting_outings', SALIDA_A, { outingId: SALIDA_A, date: '2026-11-10', time: '09:00', type: 'Grupo 4' });
  db.seed('reporting_outings', SALIDA_B, { outingId: SALIDA_B, date: '2026-11-11', time: '18:00', type: 'Grupo 3' });
  db.seed('reporting_accounts', CONDUCTOR.uid, { uid: CONDUCTOR.uid, email: CONDUCTOR.email, role: 'conductor' });
  db.seed('reporting_accounts', OTRO.uid, { uid: OTRO.uid, email: OTRO.email, role: 'conductor' });
}

const TERRITORIO = 'T 4 : 7250, 7251 (Faltan: 7270)\nManzanas: 12, 13';

beforeEach(async () => {
  db.data.clear();
  db.writesPerformed = 0;
  db.failNextTransactions = 0;
  await writeFile(dataFile, JSON.stringify(datos(), null, 2));
  catalogar();
});

after(async () => {
  await rm(directory, { recursive: true, force: true });
});

// node:test no tiene beforeEach global: se usa un envoltorio.
function beforeEach(fn) {
  test.beforeEach(fn);
}

// ------------------------------------------------------------------ 1
test('guarda un reporte válido en la nube con el backend de reportes apagado', async () => {
  // No se llama a ninguna ruta del backend: se usa el repositorio como lo haría
  // la app (Firestore directo).
  const result = await repository.saveReportToCloud({
    outingId: SALIDA_A,
    realizada: true,
    observaciones: 'Participación completa',
    territorioTexto: TERRITORIO,
    fechaSalida: '2026-11-10',
  }, CONDUCTOR);
  assert.equal(result.created, true);
  assert.equal(result.report.outingId, SALIDA_A);
  assert.equal(result.report.realizada, true);
  assert.equal(db.count('reports'), 1);
  const guardado = db.read('reports', `${SALIDA_A}__${CONDUCTOR.uid}`);
  assert.equal(guardado.outingDate, '2026-11-10');
  assert.ok(guardado.receivedAt instanceof Date, 'fecha de recepción del servidor');
});

// ------------------------------------------------------------------ 2
test('la identidad sale de la sesión: el cuerpo no puede imponer uid ni correo', async () => {
  const result = await repository.saveReportToCloud({
    outingId: SALIDA_A,
    realizada: true,
    observaciones: '',
    territorioTexto: '',
  }, CONDUCTOR);
  assert.equal(result.report.receivedBy, CONDUCTOR.uid);
  // Se normaliza a minúsculas el correo de la sesión.
  assert.equal(result.report.receivedByEmail, 'conductor@example.com');
  // Un reporte de OTRO usuario es un documento distinto; no se pisa.
  await repository.saveReportToCloud({ outingId: SALIDA_A, realizada: false }, OTRO);
  assert.equal(db.count('reports'), 2);
  assert.equal(db.read('reports', `${SALIDA_A}__${OTRO.uid}`).receivedBy, OTRO.uid);
});

test('sin sesión no se guarda nada', async () => {
  await assert.rejects(
    repository.saveReportToCloud({ outingId: SALIDA_A, realizada: true }, {}),
    (error) => error.code === 'unauthenticated',
  );
  assert.equal(db.count('reports'), 0);
});

// ------------------------------------------------------------------ 6
test('una salida inexistente no se puede reportar (id inventado)', async () => {
  await assert.rejects(
    repository.saveReportToCloud({ outingId: 'salida-inventada-999', realizada: true }, CONDUCTOR),
    (error) => error.code === 'outing-not-found',
  );
  assert.equal(db.count('reports'), 0);
});

test('una fecha que no coincide con la salida real se rechaza', async () => {
  await assert.rejects(
    repository.saveReportToCloud({
      outingId: SALIDA_A, realizada: true, fechaSalida: '2026-11-12',
    }, CONDUCTOR),
    (error) => error.code === 'outing-mismatch',
  );
  assert.equal(db.count('reports'), 0);
});

test('el identificador de la salida tiene formato controlado', () => {
  assert.ok(normalizeReportInput({ outingId: 'salida-1', realizada: true }).value);
  for (const malo of ['../reports/x', 'a/b', 'salida con espacios', '', null, 'x'.repeat(201)]) {
    assert.ok(normalizeReportInput({ outingId: malo, realizada: true }).error, String(malo));
  }
});

// ------------------------------------------------------------------ 7
test('conserva el texto del territorio y las manzanas tal cual', async () => {
  const result = await repository.saveReportToCloud({
    outingId: SALIDA_A, realizada: true, observaciones: '  Nota  ', territorioTexto: TERRITORIO,
  }, CONDUCTOR);
  assert.equal(result.report.territorioTexto, TERRITORIO);
  assert.match(result.report.territorioTexto, /7250, 7251/);
  assert.match(result.report.territorioTexto, /Manzanas: 12, 13/);
  assert.equal(result.report.observaciones, 'Nota');
});

test('no permite textos gigantes', () => {
  assert.ok(normalizeReportInput({ outingId: SALIDA_A, realizada: true, territorioTexto: 'x'.repeat(20001) }).error);
  assert.ok(normalizeReportInput({ outingId: SALIDA_A, realizada: true, observaciones: 'x'.repeat(4001) }).error);
});

// ------------------------------------------------------------------ 4
test('reintentos con la misma clave no crean duplicados', async () => {
  const args = { outingId: SALIDA_A, realizada: true, observaciones: 'ok', territorioTexto: TERRITORIO };
  const first = await repository.saveReportToCloud(args, CONDUCTOR);
  const second = await repository.saveReportToCloud(args, CONDUCTOR);
  assert.equal(first.created, true);
  assert.equal(second.created, false);
  assert.equal(second.duplicate, true);
  assert.equal(db.count('reports'), 1);
  assert.equal(second.report.docId, first.report.docId);
});

test('si la transacción falla, no queda un reporte a medias', async () => {
  db.failNextTransactions = 1;
  await assert.rejects(repository.saveReportToCloud({ outingId: SALIDA_A, realizada: true }, CONDUCTOR));
  assert.equal(db.count('reports'), 0);
});

// ------------------------------------------------------------------ 3 (consulta del gestor)
test('el gestor consulta los reportes de la nube (incluidos los de otros usuarios)', async () => {
  await repository.saveReportToCloud({ outingId: SALIDA_A, realizada: true }, CONDUCTOR);
  await repository.saveReportToCloud({ outingId: SALIDA_A, realizada: false }, OTRO);
  await repository.saveReportToCloud({ outingId: SALIDA_B, realizada: true }, OTRO);
  const todos = await repository.listCloudReports();
  assert.equal(todos.length, 3);
  const deA = await repository.listCloudReports({ outingId: SALIDA_A });
  assert.equal(deA.length, 2);
  assert.ok(deA.every((item) => item.outingId === SALIDA_A));
});

// ------------------------------------------------------------------ 8
test('si el backend guarda, escribe en la nube y NO modifica months.json', async () => {
  const antes = await readFile(dataFile, 'utf8');
  const app = createApp({ verify: async () => ({ uid: CONDUCTOR.uid, email: CONDUCTOR.email, email_verified: true }) });
  const server = createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const respuesta = await fetch(`${base}/api/reports`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer sesion-ok' },
      body: JSON.stringify({
        salida: { salidaId: SALIDA_A, fecha: '2026-11-10', hora: '09:00', tipo: 'Grupo 4' },
        realizada: true,
        observaciones: 'Desde el gestor',
        territorioTexto: TERRITORIO,
        // Datos de identidad que el cliente NO debe poder imponer:
        receivedBy: OTRO.uid,
        userId: OTRO.uid,
        email: OTRO.email,
      }),
    });
    const cuerpo = await respuesta.json();
    assert.equal(respuesta.status, 201, JSON.stringify(cuerpo));
    assert.equal(cuerpo.storage, 'cloud');
    const guardado = db.read('reports', `${SALIDA_A}__${CONDUCTOR.uid}`);
    assert.ok(guardado, 'se guardó con la identidad de la sesión');
    assert.equal(guardado.receivedBy, CONDUCTOR.uid, 'ignora el uid del cuerpo');
    assert.equal(guardado.receivedByEmail, CONDUCTOR.email);
    const despues = await readFile(dataFile, 'utf8');
    assert.equal(despues, antes, 'months.json queda intacto');
  } finally {
    server.close();
  }
});

test('la consulta del gestor exige sesión y no modifica datos', async () => {
  await repository.saveReportToCloud({ outingId: SALIDA_A, realizada: true }, CONDUCTOR);
  const antes = await readFile(dataFile, 'utf8');
  const app = createApp({
    verify: async (token) => {
      if (token !== 'sesion-ok') throw new Error('inválido');
      return { uid: CONDUCTOR.uid, email: CONDUCTOR.email, email_verified: true };
    }
  });
  const server = createServer(app);
  await new Promise((resolve) => server.listen(0, resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const sinSesion = await fetch(`${base}/api/reports/cloud`);
    assert.equal(sinSesion.status, 401);
    const ok = await fetch(`${base}/api/reports/cloud`, { headers: { Authorization: 'Bearer sesion-ok' } });
    assert.equal(ok.status, 200);
    const cuerpo = await ok.json();
    assert.equal(cuerpo.count, 1);
    assert.equal(cuerpo.reports[0].territorioTexto ?? '', '');
    assert.equal(await readFile(dataFile, 'utf8'), antes);
  } finally {
    server.close();
  }
});

// ------------------------------------------------------------------ 5 / migración
test('el catálogo se publica desde el cronograma real, sin tocar months.json', async () => {
  const antes = await readFile(dataFile, 'utf8');
  const preview = await bridge.catalogPreview();
  assert.equal(preview.outings.length, 2);
  assert.deepEqual(preview.outings.map((item) => item.id).sort(), [SALIDA_A, SALIDA_B]);
  const publicado = await bridge.publishCatalogFromData();
  assert.equal(publicado.outings, 2);
  assert.equal(db.count('reporting_outings'), 2);
  assert.equal(await readFile(dataFile, 'utf8'), antes);
});

test('un reporte de una salida fuera del catálogo se rechaza aunque exista en months.json', async () => {
  // Simula una salida agregada en el gestor sin publicar el catálogo aún:
  // la app no puede reportarla hasta que el backend publique ese catálogo.
  const data = JSON.parse(await readFile(dataFile, 'utf8'));
  data.months['2026-11'].outings.push({ id: 'salida-nueva', date: '2026-11-20', time: '09:00', type: 'X' });
  await writeFile(dataFile, JSON.stringify(data));
  await assert.rejects(
    repository.saveReportToCloud({ outingId: 'salida-nueva', realizada: true }, CONDUCTOR),
    (error) => error.code === 'outing-not-found',
  );
});

test('un reporte existente no se duplica al reenviarlo (idempotencia del documento)', async () => {
  await repository.saveReportToCloud({ outingId: SALIDA_B, realizada: true }, CONDUCTOR);
  const total = db.count('reports');
  for (let intento = 0; intento < 3; intento += 1) {
    await repository.saveReportToCloud({ outingId: SALIDA_B, realizada: true }, CONDUCTOR);
  }
  assert.equal(db.count('reports'), total);
});
