#!/usr/bin/env node
// ============================================================================
// dryRunValidation.js — Validación de SOLO LECTURA de backend/data/months.json
// ----------------------------------------------------------------------------
// Objetivo: comprobar la integridad de los datos antes de una eventual
// migración a Firestore. Este script:
//   * NO se conecta a Firebase ni a ninguna red;
//   * NO escribe ni modifica months.json ni ningún otro archivo;
//   * NO instala ni importa dependencias externas (solo módulos de Node).
//
// Uso:
//   node backend/scripts/dryRunValidation.js [ruta/a/months.json]
//
// Salida: informe legible por consola + código de salida 0 (sin hallazgos) o
//         1 (con hallazgos). Las inconsistencias se informan, nunca se corrigen.
// ============================================================================
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const defaultDataFile = resolve(here, '..', 'data', 'months.json');
const dataFile = process.argv[2] ? resolve(process.argv[2]) : defaultDataFile;

// ---------------------------------------------------------------- conteos esperados
// Referencia registrada antes de la migración (Fase 0).
const EXPECTED = {
  months: 3,
  outings: 144,
  drivers: 29,
  houses: 32,
  territories: 30,
  types: 5,
};

// Días y turnos según shared/availability.js (no se importa para mantener el
// script aislado y sin dependencias): rango mañana 06:00–12:00, tarde 13:00–21:00.
const WEEKDAYS = [0, 1, 2, 3, 4, 5, 6];
const TURNS = ['morning', 'afternoon'];

// ---------------------------------------------------------------- utilidades
const findings = [];
const notes = [];

function finding(category, message, detail) {
  findings.push({ category, message, detail });
}
function note(message) {
  notes.push(message);
}
function sha256(buffer) {
  return createHash('sha256').update(buffer).digest('hex');
}
function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
function toMinutes(time) {
  const match = /^(\d{1,2}):(\d{2})$/.exec(String(time ?? '').trim());
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (hour > 23 || minute > 59) return null;
  return hour * 60 + minute;
}
function turnOfTime(time) {
  const minutes = toMinutes(time);
  if (minutes === null) return null;
  if (minutes >= 6 * 60 && minutes <= 12 * 60) return 'morning';
  if (minutes >= 13 * 60 && minutes <= 21 * 60) return 'afternoon';
  return null;
}
function weekdayOf(isoDate) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(isoDate ?? '').trim());
  if (!match) return null;
  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
  if (Number.isNaN(date.getTime())) return null;
  return date.getUTCDay();
}
// Formato de slotKey: "<weekday>:<turn>" p. ej. "2:morning".
function isValidSlotKey(key) {
  const [weekday, turn] = String(key).split(':');
  return TURNS.includes(turn) && WEEKDAYS.includes(Number(weekday)) && String(Number(weekday)) === weekday;
}
// Clave de documento admisible en Firestore: no puede contener "/" ni ser "." o "..".
function isFirestoreSafeId(id) {
  if (typeof id !== 'string' || id.length === 0) return false;
  if (id.includes('/')) return false;
  if (id === '.' || id === '..') return false;
  if (id !== id.trim()) return false;
  return true;
}
function duplicates(ids) {
  const seen = new Set();
  const dup = new Set();
  for (const id of ids) {
    if (seen.has(id)) dup.add(id);
    else seen.add(id);
  }
  return [...dup];
}

// ---------------------------------------------------------------- carga
const raw = await readFile(dataFile);
const originalHash = sha256(raw);
const data = JSON.parse(raw.toString('utf8'));

// ---------------------------------------------------------------- entidades raíz
const types = Array.isArray(data.types) ? data.types : [];
const drivers = Array.isArray(data.drivers) ? data.drivers : [];
const houses = Array.isArray(data.houses) ? data.houses : [];
const locations = Array.isArray(data.locations) ? data.locations : [];
const territoryLocations = Array.isArray(data.territoryLocations) ? data.territoryLocations : [];
const territories = Array.isArray(data.territories) ? data.territories : [];
const months = isPlainObject(data.months) ? data.months : {};

const monthKeys = Object.keys(months);

// ---------------------------------------------------------------- 1. conteos vs esperado
const actual = {
  months: monthKeys.length,
  outings: monthKeys.reduce((t, k) => t + ((months[k].outings || []).length), 0),
  drivers: drivers.length,
  houses: houses.length,
  territories: territories.length,
  types: types.length,
};
for (const [key, expected] of Object.entries(EXPECTED)) {
  if (actual[key] !== expected) {
    finding('conteos', `${key}: se esperaban ${expected} y hay ${actual[key]}`, null);
  }
}

// ---------------------------------------------------------------- 2. IDs únicos y seguros
const driverIds = drivers.map((d) => d?.id);
const houseIds = houses.map((h) => h?.id);
const locationIds = locations.map((l) => l?.id);
const territoryIds = territories.map((t) => t?.id);
const typeIds = types.map((t) => t?.id);
const territoryLocationIds = territoryLocations.map((t) => t?.id);

const idGroups = [
  ['drivers', driverIds],
  ['houses', houseIds],
  ['locations', locationIds],
  ['territories', territoryIds],
  ['types', typeIds],
  ['territoryLocations', territoryLocationIds],
];
for (const [name, ids] of idGroups) {
  const missing = ids.filter((id) => !isFirestoreSafeId(id));
  if (missing.length) {
    finding('ids', `${name}: ${missing.length} id(s) vacíos o no válidos como clave de Firestore`, missing.slice(0, 10));
  }
  const dup = duplicates(ids);
  if (dup.length) {
    finding('ids', `${name}: ids duplicados`, dup.slice(0, 10));
  }
}

// El conjunto de lugares (casas + ubicaciones) es el espacio de placeId de las salidas.
const placeById = new Map();
for (const h of houses) placeById.set(h?.id, 'house');
for (const l of locations) placeById.set(l?.id, 'location');
const placeDupAcross = houses
  .map((h) => h?.id)
  .filter((id) => locations.some((l) => l?.id === id));
if (placeDupAcross.length) {
  finding('ids', 'ids compartidos entre houses y locations (placeId ambiguo)', placeDupAcross.slice(0, 10));
}

// ---------------------------------------------------------------- 3. meses: claves, outings y reglas
const allOutingIds = [];
const perMonthOutings = {};
const typeNameById = new Map(types.map((t) => [t?.id, t?.name]));

// Reglas recurrentes (configuration) y su relación con las salidas.
const rules = [];
const ruleIndex = new Map(); // id de regla -> regla
const ruleTypeKeys = new Map(); // clave tipo+turno+día -> regla

for (const mk of monthKeys) {
  const month = months[mk];
  if (!/^\d{4}-\d{2}$/.test(mk)) {
    finding('meses', `clave de mes con formato inesperado: "${mk}"`, null);
  }
  const [yearStr, monthStr] = mk.split('-');
  const year = Number(yearStr);
  const monthNum = Number(monthStr);
  if (monthNum < 1 || monthNum > 12) {
    finding('meses', `mes fuera de rango en "${mk}"`, null);
  }
  const outings = Array.isArray(month.outings) ? month.outings : [];
  perMonthOutings[mk] = outings.length;

  const configuration = Array.isArray(month.configuration) ? month.configuration : [];

  for (const rule of configuration) {
    const id = rule?.id;
    if (id !== undefined) {
      if (ruleIndex.has(id)) {
        finding('reglas', `id de regla duplicado: "${id}" (${mk})`, null);
      } else {
        ruleIndex.set(id, { ...rule, _month: mk });
      }
    }
    const typeName = String(rule?.type ?? '').trim();
    const weekdays = Array.isArray(rule?.weekdays) ? rule.weekdays : [];
    const time = rule?.time;
    const turn = turnOfTime(time);
    for (const wd of weekdays) {
      if (!WEEKDAYS.includes(Number(wd))) {
        finding('reglas', `día inválido (${wd}) en regla "${id ?? '(sin id)'}" de ${mk}`, null);
      }
      if (turn === null) {
        finding('reglas', `hora "${time}" fuera de rango (sin turno) en regla "${id ?? '(sin id)'}" de ${mk}`, null);
      } else {
        const key = `${typeName}|${wd}|${turn}`;
        if (ruleTypeKeys.has(key)) {
          finding('reglas', `regla duplicada para tipo/día/turno "${key}" en ${mk}`, [ruleTypeKeys.get(key), id ?? '(sin id)']);
        } else {
          ruleTypeKeys.set(key, id ?? '(sin id)');
        }
      }
    }
    rules.push({ id, type: typeName, weekdays, time, turn, _month: mk });
  }

  for (const outing of outings) {
    const id = outing?.id;
    allOutingIds.push(id);

    // Fecha válida y dentro del mes de su clave.
    const wd = weekdayOf(outing?.date);
    if (wd === null) {
      finding('salidas', `fecha inválida ("${outing?.date}") en salida "${id}" de ${mk}`, null);
    } else {
      const prefix = `${yearStr}-${monthStr}`;
      if (!String(outing.date).startsWith(prefix)) {
        finding('salidas', `fecha "${outing.date}" no pertenece al mes "${mk}" (salida "${id}")`, null);
      }
    }

    // Tipo declarado y turno coherente con la hora.
    const typeName = String(outing?.type ?? '').trim();
    if (!typeName) {
      finding('salidas', `salida "${id}" de ${mk} sin tipo`, null);
    }
    const turn = turnOfTime(outing?.time);
    if (turn === null) {
      finding('salidas', `salida "${id}" de ${mk} con hora "${outing?.time}" fuera de rango (sin turno)`, null);
    }

    // Regla recurrente que respalda la salida: mismo tipo + día + turno.
    if (wd !== null && turn !== null) {
      const key = `${typeName}|${wd}|${turn}`;
      if (!ruleTypeKeys.has(key)) {
        finding('salidas-reglas', `salida "${id}" (${mk}) sin regla recurrente que la respalde [${key}]`, {
          date: outing.date, time: outing.time, type: typeName,
        });
      }
    }

    // Lugar: placeType coherente con placeId existente.
    const placeType = outing?.placeType;
    const placeId = outing?.placeId;
    if (!['house', 'location'].includes(placeType)) {
      finding('salidas', `salida "${id}" de ${mk} con placeType desconocido: "${placeType}"`, null);
    }
    if (placeId !== null && placeId !== undefined && !placeById.has(placeId)) {
      finding('salidas', `salida "${id}" de ${mk} referencia placeId inexistente: "${placeId}"`, null);
    } else if (placeId && placeType && placeById.get(placeId) !== placeType) {
      finding('salidas', `salida "${id}" de ${mk}: placeType "${placeType}" no coincide con el tipo real de "${placeId}" (${placeById.get(placeId)})`, null);
    }
    if (typeName.toLowerCase().includes('congregacional') === false && placeType === 'house' && !placeId) {
      // Una salida a casa sin placeId no puede resolverse.
      finding('salidas', `salida "${id}" de ${mk} marcada como house pero sin placeId`, null);
    }

    // Conductores asignados: deben existir.
    for (const field of ['driverId', 'driver2Id', 'companionId']) {
      const value = outing?.[field];
      if (value && !driverIds.includes(value)) {
        finding('salidas', `salida "${id}" de ${mk}: ${field} inexistente ("${value}")`, null);
      }
    }

    // Territorio declarado: si hay uno, debe existir en el catálogo.
    const territory = String(outing?.territory ?? '').trim();
    if (territory && !territoryIds.some((tid) => String(tid).trim() === territory)) {
      note(`salida "${id}" de ${mk}: territorio "${territory}" no figura con ese id en territories (puede ser texto libre)`);
    }
  }
}

// ---------------------------------------------------------------- 4. outings: ids únicos
const dupOutingIds = duplicates(allOutingIds);
if (dupOutingIds.length) {
  finding('ids', `salidas: ids duplicados`, dupOutingIds.slice(0, 20));
}
const badOutingIds = allOutingIds.filter((id) => !isFirestoreSafeId(id));
if (badOutingIds.length) {
  finding('ids', `salidas: ${badOutingIds.length} id(s) no válidos como clave de Firestore`, badOutingIds.slice(0, 10));
}

// ---------------------------------------------------------------- 5. disponibilidad: claves de slot
const slotKeyStats = { driver: 0, house: 0, location: 0 };
const invalidSlotKeys = [];
for (const mk of monthKeys) {
  const month = months[mk];
  for (const [field, label] of [
    ['driverAvailability', 'driver'],
    ['houseAvailability', 'house'],
    ['locationAvailability', 'location'],
  ]) {
    const map = month[field];
    if (map === undefined) continue;
    if (!isPlainObject(map)) {
      finding('disponibilidad', `${field} de ${mk} no es un objeto`, null);
      continue;
    }
    for (const [entityId, availability] of Object.entries(map)) {
      if (!isFirestoreSafeId(entityId)) {
        finding('disponibilidad', `${field} de ${mk}: id de entidad no válido "${entityId}"`, null);
      }
      // Cada disponibilidad por conductor tiene "slots" con claves "d:turno".
      const slots = isPlainObject(availability) && isPlainObject(availability.slots)
        ? availability.slots
        : null;
      if (slots === null) {
        finding('disponibilidad', `${field}["${entityId}"] de ${mk} sin objeto "slots"`, null);
        continue;
      }
      slotKeyStats[label] += Object.keys(slots).length;
      for (const key of Object.keys(slots)) {
        if (!isValidSlotKey(key)) invalidSlotKeys.push(`${mk}/${field}/${entityId}/${key}`);
      }
    }
  }
}
if (invalidSlotKeys.length) {
  finding('disponibilidad', `${invalidSlotKeys.length} clave(s) de slot inválidas (esperado "d:turno")`, invalidSlotKeys.slice(0, 15));
}

// ---------------------------------------------------------------- 6. rotaciones y confirmaciones
for (const mk of monthKeys) {
  const month = months[mk];
  const rotations = month.driverRotations;
  if (isPlainObject(rotations)) {
    for (const [groupKey, map] of Object.entries(rotations)) {
      if (!isPlainObject(map)) {
        finding('rotaciones', `driverRotations.${groupKey} de ${mk} no es un objeto`, null);
        continue;
      }
      for (const [weekday, driverId] of Object.entries(map)) {
        if (!WEEKDAYS.includes(Number(weekday))) {
          finding('rotaciones', `día inválido "${weekday}" en driverRotations.${groupKey} de ${mk}`, null);
        }
        if (driverId && !driverIds.includes(driverId)) {
          finding('rotaciones', `driverRotations.${groupKey}.${weekday} de ${mk} apunta a conductor inexistente ("${driverId}")`, null);
        }
      }
    }
  }
  const houseRotations = month.houseRotations;
  if (isPlainObject(houseRotations)) {
    for (const [weekday, houseId] of Object.entries(houseRotations)) {
      if (!WEEKDAYS.includes(Number(weekday))) {
        finding('rotaciones', `día inválido "${weekday}" en houseRotations de ${mk}`, null);
      }
      if (houseId && !houseIds.includes(houseId)) {
        finding('rotaciones', `houseRotations.${weekday} de ${mk} apunta a casa inexistente ("${houseId}")`, null);
      }
    }
  }
  const confirmations = month.houseAvailabilityConfirmations;
  if (isPlainObject(confirmations)) {
    for (const [entityId, value] of Object.entries(confirmations)) {
      if (!isFirestoreSafeId(entityId)) {
        finding('confirmaciones', `houseAvailabilityConfirmations de ${mk}: clave no válida "${entityId}"`, null);
      }
      const slots = isPlainObject(value) && isPlainObject(value.slots) ? value.slots : null;
      if (slots === null) {
        finding('confirmaciones', `houseAvailabilityConfirmations["${entityId}"] de ${mk} sin objeto "slots"`, null);
        continue;
      }
      for (const key of Object.keys(slots)) {
        if (!isValidSlotKey(key)) {
          finding('confirmaciones', `clave de slot inválida "${key}" en houseAvailabilityConfirmations de ${mk}`, null);
        }
      }
    }
  }
}

// ---------------------------------------------------------------- 7. cobertura tipo -> id
// El tipo "congregacional" es el único con id fijo; los demás dependen del nombre.
for (const type of types) {
  if (!isFirestoreSafeId(type?.id)) {
    finding('tipos', `tipo con id no válido: ${JSON.stringify(type?.id)}`, null);
  }
}
const congregational = types.find((t) => String(t?.name ?? '').trim().toLowerCase() === 'congregacional');
if (!congregational) {
  note('no se encontró el tipo "Congregacional" en el catálogo de tipos');
}
for (const outing of Object.values(months).flatMap((m) => m.outings || [])) {
  const typeName = String(outing?.type ?? '').trim();
  if (typeName && !types.some((t) => String(t?.name ?? '').trim() === typeName)) {
    finding('salidas-tipos', `salida "${outing?.id}" usa un tipo que no está en el catálogo: "${typeName}"`, null);
  }
}

// ---------------------------------------------------------------- informe
const line = '='.repeat(78);
console.log(line);
console.log('DRY RUN — Validación de integridad de months.json (SOLO LECTURA)');
console.log(line);
console.log(`Archivo        : ${dataFile}`);
console.log(`SHA-256        : ${originalHash}`);
console.log(`Conecta a red  : NO`);
console.log(`Escribe datos  : NO`);
console.log(`Dependencias   : ninguna (solo módulos nativos de Node)`);
console.log(line);
console.log('CONTEO ACTUAL vs ESPERADO');
console.log('-'.repeat(78));
for (const key of Object.keys(EXPECTED)) {
  const ok = actual[key] === EXPECTED[key];
  console.log(`  ${ok ? 'OK ' : 'DIF'}  ${key.padEnd(12)} actual=${String(actual[key]).padStart(5)}  esperado=${String(EXPECTED[key]).padStart(5)}`);
}
console.log('-'.repeat(78));
console.log(`  Salidas por mes: ${monthKeys.map((k) => `${k}=${perMonthOutings[k]}`).join('  ')}`);
console.log(`  Reglas recurrentes: ${rules.length}  (indexadas por tipo+día+turno: ${ruleTypeKeys.size})`);
console.log(`  Claves de slot: driver=${slotKeyStats.driver} house=${slotKeyStats.house} location=${slotKeyStats.location}`);
console.log(line);
console.log(`HALLAZGOS: ${findings.length}`);
console.log(line);
if (findings.length === 0) {
  console.log('  Ninguno. No se detectaron inconsistencias.');
} else {
  const byCat = {};
  for (const f of findings) byCat[f.category] = (byCat[f.category] || 0) + 1;
  console.log('  Por categoría:');
  for (const [cat, n] of Object.entries(byCat)) console.log(`    - ${cat}: ${n}`);
  console.log('');
  findings.forEach((f, i) => {
    console.log(`  [${i + 1}] (${f.category}) ${f.message}`);
    if (f.detail !== null && f.detail !== undefined) {
      console.log(`      detalle: ${JSON.stringify(f.detail)}`);
    }
  });
}
if (notes.length) {
  console.log(line);
  console.log(`NOTAS (informativo, no son errores): ${notes.length}`);
  console.log(line);
  for (const n of notes.slice(0, 25)) console.log(`  - ${n}`);
  if (notes.length > 25) console.log(`  ... y ${notes.length - 25} más`);
}
console.log(line);
console.log(findings.length === 0 ? 'RESULTADO: OK — sin hallazgos.' : 'RESULTADO: se encontraron hallazgos (ver arriba). No se corrigió nada.');
console.log(line);

process.exit(findings.length === 0 ? 0 : 1);
