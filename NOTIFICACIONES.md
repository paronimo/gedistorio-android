# Notificaciones de las salidas — Geditorio (Etapa 2)

Sistema que avisa al conductor asignado a cada salida del cronograma real:

1. **Un día antes**, a la misma hora de la salida: "tenés una salida mañana",
   con fecha, hora y tipo de salida.
2. **Una hora después** de la hora de la salida: exactamente
   **«¡No te olvides de enviar el informe!»**.

El backend web calcula, programa y envía; la app Android recibe aunque esté
cerrada. Las notificaciones llegan con **Firebase Cloud Messaging (FCM)**.

---

## 1. Cómo funciona

```
cronograma (data/months.json)
      │  alta / edición / borrado / regeneración de salidas
      ▼
models/scheduleModel.js  ──►  data.notifications.outbox  (avisos persistidos)
                                     │  cada 30 s (o al arrancar)
                                     ▼
services/notifications/dispatcher.js ──► sender.js ──► FCM ──► celular del conductor
```

### Horarios
La salida guarda `date` (`YYYY-MM-DD`) y `time` (`HH:mm`) **locales**, sin zona
horaria. `services/scheduleTime.js` las convierte a un instante UTC usando la
zona configurada en `NOTIFICATIONS_TIME_ZONE` (por defecto
`America/Argentina/Buenos_Aires`), nunca la del servidor. Si la zona está mal
escrita, el servidor no arranca (antes que calcular mal los horarios).

| Salida | Aviso del día anterior | Recordatorio del informe |
|---|---|---|
| 10/11/2026 09:00 (-03) | 09/11/2026 09:00 (-03) | 10/11/2026 10:00 (-03) |

### Persistencia y anti-duplicados
Cada aviso es una fila de `data.notifications.outbox` con clave única
`"<salidaId>:<tipo>"` y estado `pending` → `sent` / `cancelled` / `failed`.
No hay temporizadores en memoria:

- **Reinicio del servidor**: al arrancar se sincroniza el cronograma y se
  envía lo que quedó pendiente y ya venció (tolera hasta 10 minutos de atraso;
  más viejo se descarta para no mandar avisos fuera de hora).
- **Dos despachadores / dos procesos**: `claimNotification` toma el aviso dentro
  de la cola de escritura del modelo; solo uno lo envía.
- **Proceso que muere enviando**: el aviso queda "en envío"; pasados 5 minutos
  sin confirmación se vuelve a tomar.
- **Cambio de fecha/hora**: el aviso pendiente se reprograma (`dueAt` nuevo).
- **Cambio de conductor**: los pendientes del anterior se **cancelan** y se crea
  un aviso nuevo para el nuevo (`"<salida>:<tipo>#<conductor>"`).
- **Borrado / salida eliminada al regenerar el mes**: sus pendientes se cancelan.
- **Aviso ya enviado**: nunca se revive ni se reprograma.
- **Errores temporales de FCM** (red, cuota, servicio caído): reintento con
  espera exponencial (`NOTIFICATIONS_MAX_ATTEMPTS`, por defecto 6).
- **Token inválido / celular desinstalado**: ese dispositivo se da de baja; si
  el conductor tiene otro, el aviso se envía ahí.
- **Conductor sin celular registrado**: el aviso queda pendiente y se envía
  cuando la app se registre.

### A quién se le envía
El nombre del conductor **no** se usa para adivinar cuentas.

- `data.drivers[i].email`: correo de la cuenta de Firebase del conductor. Se
  carga en **Administración → Conductores → "Correo de su cuenta"**.
- `data.devices`: tokens FCM registrados por la app con sesión iniciada. Cada
  uno guarda el `uid` y el correo **tomados del token de Firebase verificado**
  por el backend (el celular no puede elegir quién es).
- Un dispositivo se liga a un conductor solo si **exactamente un** registro de
  conductor tiene ese correo. Si ninguno o varios lo tienen, no se liga y se
  informa en `GET /api/notifications/admin` (`status: sin-cuenta` /
  `cuenta-repetida`): no se envía a nadie equivocado.

---

## 2. Qué modifiqué

### Backend (`gestor-t/backend`)
| Archivo | Cambio |
|---|---|
| `models/scheduleModel.js` | cola persistente `notifications.outbox`, `devices`, `drivers[].email`; `syncNotifications`, `claim/sent/failed`, `registerDevice`, `getNotificationOverview`, `scheduleTestNotification`; enganches en alta/edición/borrado/regeneración de salidas y en `updateDriver`. Normaliza `notifications`/`devices`/`email` al leer. |
| `services/scheduleTime.js` | **nuevo**: fecha/hora del cronograma ↔ instante UTC con zona IANA. |
| `services/notifications/reminderRules.js` | **nuevo**: reglas puras (cuándo, texto exacto, destinatario). |
| `services/notifications/sender.js` | **nuevo**: FCM HTTP v1 vía Admin SDK; clasificación de errores. |
| `services/notifications/dispatcher.js` | **nuevo**: ciclo persistente, reintentos, reinicio. |
| `services/notifications/config.js` | **nuevo**: zona, intervalos, reintentos, URL pública. |
| `routes/notificationRoutes.js`, `controllers/notificationsController.js` | **nuevos**: rutas (abajo). |
| `middleware/auth.js` | exporta `firebaseApp()` (lo comparten verificación y FCM; mismas credenciales). |
| `controllers/scheduleController.js` | valida/normaliza `email` del conductor. |
| `app.js`, `server.js` | monta las rutas y arranca el despachador. |
| `notifications.test.js`, `services/notifications/notificationRules.test.js` | **nuevos**: 35 pruebas. |

### Web (`gestor-t/frontend/src/components/DriversPanel.jsx`)
Un campo "Correo de su cuenta (app Android)" en el formulario del conductor y
el estado de vínculo en la lista. Es lo único que se toca de la web.

### Android (`geditorio`)
`app/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`
(google-services, firebase-bom/auth/messaging, coroutines-play-services),
`AndroidManifest.xml` (permiso POST_NOTIFICATIONS, canal por defecto, LoginActivity
como inicio), `MainActivity.kt`, `ui/login/LoginActivity.kt`,
`push/NotificacionesPush.kt`, `push/GeditorioMessagingService.kt`,
`push/RegistroDispositivoHttp.kt` (**nuevos**), `drawable/ic_notificacion.xml`,
`values/strings.xml`, `app/src/test/.../RegistroDispositivoHttpTest.kt` (**nuevo**).

> **Bloqueo preexistente resuelto (mínimo)**: el commit del repo Android tenía
> marcadores de conflicto en `app/build.gradle.kts`, `build.gradle.kts` y
> `MainActivity.kt`, y `MainActivity` llamaba a una `LoginActivity` que no
> existía: **no compilaba**. Resolví solo eso (unifiqué los dos lados: la
> pantalla de login y el cierre de sesión) y traje `LoginActivity.kt` +
> `activity_login.xml` desde la rama de respaldo `backup/firebase-aa570e8`. El
> exportador PDF y el resto no se tocaron.

---

## 3. Rutas

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/notifications/devices` | sesión Firebase (`{ token, platform }`) |
| DELETE | `/api/notifications/devices` | sesión Firebase (`{ token }`) |
| GET | `/api/notifications/me` | sesión Firebase (programación propia) |
| GET | `/api/notifications/admin` | `X-Admin-Token` (estado, pendientes, errores) |
| PUT | `/api/notifications/admin/settings` | `X-Admin-Token` (`{ enabled }`) |
| POST | `/api/notifications/admin/sync` | `X-Admin-Token` |
| POST | `/api/notifications/admin/tick` | `X-Admin-Token` (despachar ahora) |
| POST | `/api/notifications/admin/test` | `X-Admin-Token` (`{ outingId, kind }`) |

La web del gestor no tiene inicio de sesión, así que las rutas de
administración se protegen con `NOTIFICATIONS_ADMIN_TOKEN`. **Si esa variable no
está definida, responden 503** (no quedan abiertas).

---

## 4. Configuración manual pendiente (no la puedo hacer yo)

1. **Google Cloud / Firebase** — en el proyecto `gedistorio`:
   - Habilitar Cloud Messaging.
   - Cuenta de servicio (la de `backend/credentials/serviceAccount.json`):
     rol *Firebase Cloud Messaging API Admin* (o *Editor* en el proyecto).
   - En la API HTTP v1 de FCM (Google Cloud Console), asegurarse de que esa
     cuenta puede invocar `projects/*/messages:send`.
   - Comprobar que existe la app Android `com.example.geditorio` en Firebase
     (debe coincidir con `app/src/google-services.json`).
2. **Archivo de credenciales**: `backend/credentials/serviceAccount.json` debe
   estar presente y estar en `.gitignore` (nunca subirlo). En producción se
   puede usar `GOOGLE_APPLICATION_CREDENTIALS` en su lugar.
3. **Variables del backend** (`backend/.env` o entorno):
   ```
   NOTIFICATIONS_TIME_ZONE=America/Argentina/Buenos_Aires
   NOTIFICATIONS_ADMIN_TOKEN=<cadena larga y secreta>
   GEDITORIO_PUBLIC_URL=http://TU-IP-O-DOMINIO:3000   # para Android en la red
   # opcionales: NOTIFICATIONS_TICK_MS, NOTIFICATIONS_MAX_ATTEMPTS,
   #             NOTIFICATIONS_RETRY_MS, NOTIFICATIONS_STALE_SENDING_MS
   ```
4. **Cuenta de conductor**: en Administración, cargar el correo de Firebase en
   cada conductor que deba recibir avisos.
5. **Firebase Authentication**: la app usa las mismas cuentas que el reporte de
   salidas (correo/contraseña). Cada conductor debe tener su cuenta creada
   (en Firebase Console → Authentication o con el flujo de alta que ya usan).
6. **Celular**: instalar la app, iniciar sesión y **aceptar el permiso de
   notificaciones** (Android 13+). La app registra el token sola.
7. **URL del backend en Android**: `BuildConfig.BACKEND_URL` (ver
   `app/build.gradle.kts`), alcanzable desde el celular.
8. **Java para compilar**: el entorno usa un JDK sin `jlink` en Gradle; compilé
   con `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`. En Android Studio no hace
   falta.

> Estado verificado de FCM: la prueba con un token inexistente devolvió
> `Unable to detect a Project Id in the current environment` al invocar al SDK
> desde un script suelto, y **no hay token real de ningún celular registrado**.
> Por eso **no puedo afirmar que el envío real ya funcione**: falta registrar un
> dispositivo y completar los puntos 1–6.

---

## 5. Cómo probar ambas notificaciones hoy, sin esperar un día

### A. Con datos reales (hora programada)
```bash
# 1) estado y pendientes (reemplazá TU_TOKEN)
curl -s -H "X-Admin-Token: TU_TOKEN" http://localhost:3000/api/notifications/admin | python3 -m json.tool
#    Buscá tu salida y su id en months → outings[].id

# 2) aviso del día anterior: enviarlo ahora a tu celular
curl -s -X POST http://localhost:3000/api/notifications/admin/test \
  -H "Content-Type: application/json" -H "X-Admin-Token: TU_TOKEN" \
  -d '{"outingId":"TU-SALIDA-ID","kind":"day-before"}'

# 3) recordatorio del informe
curl -s -X POST http://localhost:3000/api/notifications/admin/test \
  -H "Content-Type: application/json" -H "X-Admin-Token: TU_TOKEN" \
  -d '{"outingId":"TU-SALIDA-ID","kind":"one-hour-after"}'
```

### B. Con el reloj del sistema (prueba real de programación)
1. Creá una salida **para dentro de ~3 minutos** (Administración → Cronograma).
2. Para el aviso del día anterior: creá otra salida **para mañana a la misma
   hora actual** (el aviso del día anterior vence justo a esta hora). Si estás
   dentro de los 10 minutos de gracia, sale en el siguiente ciclo.
3. Para el recordatorio: la salida de dentro de 3 minutos envía el informe una
   hora después (o adelantá el reloj del equipo una hora, con el servidor
   detenido, y volvé a arrancarlo: al arrancar despacha lo vencido).
4. Apretar **Cerrar sesión no es necesario**; probá con la app cerrada para
   confirmar la entrega en segundo plano.

La respuesta de `/admin/test` dice `queued`, `status` (`sent` o `pending`) y el
texto que se envió. Si `status` no es `sent`, revisá `lastError`.

---

## 6. Pruebas ejecutadas

| Suite | Resultado |
|---|---|
| `backend`: `npm test` (existentes + nuevas) | **132/132 OK** |
| `services/notifications/notificationRules.test.js` | 12/12 (horarios, zona, textos, vencidos) |
| `backend/notifications.test.js` | 23/23 (programación, destinatario, cambios, reinicio, reintentos, auth, reportes/cronograma intactos) |
| Web `npm run build` | OK |
| Android `:app:compileDebugKotlin` | OK |
| Android `:app:testDebugUnitTest` | **39/39 OK** (6 suites; incluye `RegistroDispositivoHttpTest` 6/6) |

Los tests del despachador usan un enviador falso: **verifican el sistema, no que
llegue un push a un celular físico**. Esa verificación es la sección 5.
