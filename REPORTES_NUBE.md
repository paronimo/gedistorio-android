# Reportes en la nube (Cloud Firestore) — Geditorio

Los conductores pueden enviar el reporte de su salida **aunque el backend
Express de la computadora esté apagado**. Los reportes quedan en Cloud
Firestore y el gestor los consulta desde ahí al volver a usarse.

---

## 1. Cómo queda el flujo

```
Celular (app)                                   Nube (Firestore)                 Gestor (backend)
─────────────                                   ─────────────────                ────────────────
formulario + mapa ──► SincronizadorReportes
                       │
                       ├─ Firestore directo ──► reports/<salidaId>__<uid> ──► GET /api/reports/cloud
                       │     (transacción:                             (solo lectura,
                       │      salida real +                                  requiere sesión)
                       │      identidad)
                       └─ sin red: cola en disco ──(vuelve la red)──┘
```

El backend **ya no es necesario para guardar** un reporte. Sigue siendo
necesario para:

- mostrar la lista de salidas en la app (`SalidaRepository`, sin cambios);
- publicar el **catálogo de salidas reales** y las cuentas autorizadas en
  Firestore (sin ese catálogo, la app no puede validar sola que la salida sea
  real; ver §4);
- consultar los reportes desde el gestor.

### Por qué Firestore
- Ya es el proyecto Firebase del proyecto (`gedistorio`) y la app usa Firebase
  Auth: no hace falta otro proveedor ni cuentas.
- Permite escribir desde el cliente con reglas de seguridad del lado del
  servidor, sin exponer credenciales.
- Las reglas pueden comprobar contra documentos (catálogo de salidas) que el
  cliente no puede modificar.
- Las escrituras sin red quedan en caché local de Firestore además de nuestra
  cola; con el backend apagado no hay ningún punto único de falla.

---

## 2. Estructura en Firestore

| Colección | Documento | Lo escribe | Contenido |
|---|---|---|---|
| `reports` | `<outingId>__<uid>` | app / backend | reporte completo |
| `reporting_outings` | `<outingId>` | **solo backend** | `outingId, date, time, type, turn, driverId, driver, updatedAt, schema` |
| `reporting_accounts` | `<uid>` | **solo backend** | `uid, email, role: 'conductor', updatedAt, schema` |

Un reporte:

```json
{
  "outingId": "b3f1…",
  "outingDate": "2026-11-10",
  "outingTime": "09:00",
  "outingType": "Grupo 4",
  "realizada": true,
  "observaciones": "…",
  "territorioTexto": "T 4 : 7250, 7251 (Faltan: 7270)\nManzanas: …",
  "receivedBy": "<uid de Auth>",
  "receivedByEmail": "conductor@ejemplo.com",
  "receivedAt": "<server timestamp>",
  "receivedAtServer": "<server timestamp>",
  "source": "cloud",
  "schema": 1
}
```

- **Id determinístico** `<outingId>__<uid>`: un reporte por salida y usuario.
  Reintentar sobrescribe el mismo documento (nunca duplica).
- `receivedBy`/`receivedByEmail` los pone la sesión; `receivedAt*` usa el
  timestamp del servidor. El cliente no puede imponerlos.
- `territorioTexto` se guarda tal cual, hasta 20 000 caracteres (los cambios
  de manzanas del formulario van incluidos en ese texto; no se leyó ni se
  modificó el mapa).

---

## 3. Identidad y permisos

**Quién escribe:** solo `request.auth.uid` (regla) u `identity.uid` (backend,
desde el token verificado). Un `uid`, correo o `receivedBy` enviado en el
cuerpo se ignora.

**Salida real sin backend:** la regla exige que exista
`reporting_outings/<outingId>` y que su `date` coincida con la del reporte.
Ese catálogo lo publica el backend desde `months.json` y el cliente no puede
escribirlo (`allow write: if false`). Si el backend está apagado, se usa la
caché del catálogo; un id inventado o una salida eliminada se rechaza.

**Cuenta autorizada:** `reporting_accounts/<uid>` con rol `conductor` y el correo
de la sesión. Una cuenta no publicada no puede escribir.

**Lectura:** cada conductor solo lee el documento cuyo id termina en su uid. El
gestor lee todo a través del backend con la cuenta de servicio (rol admin).

**Reglas:** `gestor-t/firestore.rules` (incluye cerrar todo lo demás, incluido
el cronograma que ya exista en Firestore, para el cliente).

---

## 4. Cola de envíos pendientes (Android)

`SincronizadorReportes` guarda el reporte en `ColaReportesDisco` **antes** de
intentar el envío (archivo JSON privado, escritura atómica):

| Situación | Qué hace | Qué ve el usuario |
|---|---|---|
| Firestore confirma | quita de la cola | «Reporte enviado y guardado en la nube» |
| Ya existía | quita de la cola | «Este reporte ya estaba guardado en la nube» |
| Sin Internet / servidor caído | lo deja en la cola, `intentos++` | «Sin conexión: quedó guardado en este celular y se enviará solo…» |
| Salida inexistente, sin sesión o sin cuenta | lo descarta de la cola (no reintenta en bucle) | mensaje de error concreto |

- Reintenta al **abrir la app** y cuando **vuelve la red**
  (`ConnectivityManager`, sin permisos adicionales).
- La clave nunca cambia entre reintentos → mismo documento → sin duplicados.
- La pantalla deshabilita el botón mientras envía (igual que antes).
- El mapa, la selección de manzanas y el texto del territorio no se tocaron.

---

## 5. Compatibilidad y migración

- `backend/data/months.json` **no se modifica ni se borra**. El backend lo sigue
  leyendo; si Firestore no está disponible, guardar un reporte desde el propio
  backend cae al archivo local como antes (red de seguridad).
- Los reportes viejos del archivo se pueden copiar con:

```bash
cd backend
node scripts/migrateReportsToFirestore.js            # vista previa, NO escribe
node scripts/migrateReportsToFirestore.js --write    # escribe (idempotente)
```

La migración usa el mismo id determinístico: reejecutarla no duplica. Omite (y
lista) los reportes sin salida real o sin uid. **No la ejecuté** (requiere tu
autorización), solo la vista previa: hoy hay 4 reportes, que se pueden migrar.

---

## 6. Configuración manual pendiente

1. **Habilitar Firestore** en el proyecto `gedistorio` (consola → Firestore →
   crear base en modo nativo). Mis pruebas con la cuenta de servicio devolvieron
   `NOT_FOUND` al leer colecciones: **no puedo verificar que la base esté
   creada**.
2. **Publicar reglas** (yo no despliego nada):
   ```bash
   cd gestor-t
   firebase login
   firebase deploy --only firestore:rules,firestore:indexes
   ```
   (`firebase.json`, `.firebaserc` y `firestore.indexes.json` ya están.)
3. **Permisos de la cuenta de servicio**: *Cloud Datastore User* (o
   *Firebase Admin SDK Administrator Service Agent*) para leer/escribir.
4. **Publicar el catálogo** desde el backend (una vez por cada cambio de
   cronograma), con `FIRESTORE_PUBLISH_CATALOG=1` al arrancar o desde el script
   de migración. Sin catálogo, la app no puede validar salidas sin backend.
5. **Cuentas**: cada cuenta que reporta desde la app debe estar en
   `reporting_accounts` (se arma con los `uid` de los dispositivos registrados
   para notificaciones y con `reportingAccounts` del archivo del gestor).
6. **`google-services.json`** ya está en `app/src/` y el proyecto coincide
   (`gedistorio`). La app usa correo/contraseña de Firebase Auth (sin cambios).

> **Bloqueo actual**: hasta completar 1–4, la app no puede escribir en la
> nube real. El código y las pruebas están listos; el error real es `NOT_FOUND`
> al acceder a la base (Firestore no habilitado o sin permisos).

---

## 7. Pruebas

| Prueba | Tipo | Resultado |
|---|---|---|
| `backend/reportsCloud.test.js` | automatizada (Firestore en memoria con API real del Admin SDK) | **16/16** |
| `backend/reportsCloud.emulator.test.js` | **real contra emulador** | 1 omitida: no hay `FIRESTORE_EMULATOR_HOST` (el emulador no arrancó en este equipo) |
| `npm test` backend (incluye notificaciones y reportes previos) | automatizadas | **149 + 38 (shared): 0 fallas**, 1 omitida |
| `SincronizadorReportesTest.kt` | automatizada (cliente falso) | **8/8** |
| suíte Android completa | automatizada | **47/47**, 0 fallas |
| compilación Android (`compileDebugKotlin`) / web (`npm run build`) | build | OK ambos |

**No está probado contra la nube real** (Firestore sin habilitar). Para probarlo
vos:

```bash
docker run -d -p 8080:8080 \
  -e FIRESTORE_EMULATOR_HOST=0.0.0.0:8080 \
  gcr.io/google.com/cloudsdktool/cloud-sdk:emulators \
  gcloud emulators firestore start --host-port=0.0.0.0:8080

cd backend
FIRESTORE_EMULATOR_HOST=localhost:8080 GCLOUD_PROJECT=demo-geditorio \
  node --test reportsCloud.emulator.test.js
```

---

## 8. Cómo verificar con el backend local apagado

1. Publicar catálogo y reglas, habilitar Firestore (§6).
2. En el celular: iniciar sesión, elegir una salida y enviar el reporte.
3. **Apagar el backend** (`Ctrl+C` en `node server.js`) y enviar otro reporte
   de otra salida: debe decir «Reporte enviado y guardado en la nube».
4. Con el backend apagado, activar modo avión y enviar un tercero: debe decir
   «Sin conexión: quedó guardado en este celular…». Desactivar modo avión: en
   segundos la app lo sube sola.
5. Encender el backend y consultar:
   ```bash
   TOKEN="<id token del usuario>"
   curl -H "Authorization: Bearer $TOKEN" http://localhost:3000/api/reports/cloud
   ```
   Deben aparecer los tres, incluidos los enviados con el servidor apagado.
6. Comprobar que `backend/data/months.json` no cambió (`git status`).

---

## 9. Archivos de esta etapa

**Backend (`gestor-t/backend`)**
- `services/firestore/cloud.js`, `reportValidation.js`, `reportRepository.js`,
  `gestorBridge.js` — **nuevos**
- `services/firestore/testSupport/memoryFirestore.js` — **nuevo** (pruebas)
- `controllers/reportController.js` — `postReport` escribe en la nube primero y
  cae al archivo local si no está disponible; `getCloudReports` — nuevo
- `routes/reportRoutes.js` — `GET /api/reports/cloud`
- `models/scheduleModel.js` — exporta `readData` (solo lectura; sin cambios de
  comportamiento)
- `scripts/migrateReportsToFirestore.js` — **nuevo** (vista previa por defecto)
- `reportsCloud.test.js`, `reportsCloud.emulator.test.js` — **nuevos**
- `gestor-t/firestore.rules`, `firebase.json`, `firestore.indexes.json`,
  `.firebaserc` — **nuevos**

**Web (`gestor-t/frontend/src/services/api.js`)**
- `getCloudReports()` (sin UI nueva, como pide la etapa)

**Android (`geditorio`)**
- `data/cloud/CuentaNube.kt`, `ReportePendiente.kt`, `FirestoreReportesNube.kt`,
  `SincronizadorReportes.kt`, `ColaReportesDisco.kt`, `ReintentoReportes.kt` —
  **nuevos**
- `ui/reportar/ReportarFragment.kt` — envío a la nube + mensajes
- `MainActivity.kt` — arranque del reintento
- `app/build.gradle.kts`, `gradle/libs.versions.toml` — `firebase-firestore-ktx`
- `app/src/main/AndroidManifest.xml` — `ACCESS_NETWORK_STATE`
- `res/values/strings.xml` — mensajes de nube/pendiente
- `app/src/test/.../cloud/SincronizadorReportesTest.kt` — **nuevo**
