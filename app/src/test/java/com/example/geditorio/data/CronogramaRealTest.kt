package com.example.geditorio.data

import com.example.geditorio.data.model.EstadoSalida
import com.example.geditorio.data.model.TipoSalida
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Pruebas de la conexion con el cronograma real del gestor:
 *  - se consulta la ruta autenticada con el token de la sesion;
 *  - cada salida conserva el id REAL del backend, el tipo, la fecha, la hora,
 *    el territorio y el conductor;
 *  - el id real es el mismo que se manda en el reporte;
 *  - los errores de servidor y de conexion se informan, sin datos de ejemplo.
 */
class CronogramaRealTest {

    private lateinit var server: HttpServer
    private var codigo = 200
    private var cuerpo = "[]"
    private var ultimaRuta: String? = null
    private var ultimoToken: String? = null
    private var pedidos = 0

    // Salidas con la MISMA forma que devuelve GET /api/schedule/:year/:month/outings.
    private val salidasJson = JSONArray(
        """
        [
          {
            "id": "bc1bc6b1-039c-4cd0-9218-31034cb5b585",
            "date": "2026-11-01",
            "weekday": "domingo",
            "time": "10:30",
            "type": "Congregacional",
            "driverId": "0c28f11c-a057-4b3a-8d72-ae91c31ad6db",
            "driver": "",
            "placeId": null,
            "placeType": "location",
            "territory": "",
            "source": "recurring",
            "driverName": "Ana Pérez",
            "placeName": "Casa de la familia Gómez",
            "placeMapsUrl": "https://maps.google.com/?q=casa-gomez"
          },
          {
            "id": "4f0d2f8e-77a1-4f3d-9a55-1e2a3c0d9b11",
            "date": "2026-11-03",
            "weekday": "martes",
            "time": "09:45",
            "type": "Grupo 2",
            "driverId": null,
            "driver": "",
            "placeId": null,
            "placeType": "location",
            "territory": "4, 5",
            "source": "recurring",
            "driverName": "",
            "placeName": "",
            "placeMapsUrl": ""
          },
          {
            "id": "sin-tipo",
            "date": "2026-11-03",
            "time": "09:45",
            "type": "",
            "territory": ""
          }
        ]
        """.trimIndent()
    )

    private val hoy = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            pedidos += 1
            ultimaRuta = exchange.requestURI.path
            ultimoToken = exchange.requestHeaders.getFirst("Authorization")
            val bytes = cuerpo.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(codigo, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun fuente(token: String? = "token-de-prueba") = ServidorSalidaDataSource(
        cliente = CronogramaHttp("http://127.0.0.1:${server.address.port}"),
        tokenProvider = object : TokenProvider {
            override suspend fun idToken(): String? = token
        },
        zona = ZoneOffset.UTC,
        hoy = { hoy }
    )

    @Test
    fun obtieneLasSalidasRealesConSuId() = runBlocking {
        cuerpo = salidasJson.toString()

        val salidas = fuente().obtenerSalidas()

        // Dos meses consultados; de las 3 salidas de cada respuesta se
        // descarta la invalida (sin tipo) y un id repetido entre meses no se
        // duplica: quedan 2 salidas reales.
        assertEquals(2, pedidos)
        assertEquals(2, salidas.size)
        assertEquals(1, salidas.count { it.id == "bc1bc6b1-039c-4cd0-9218-31034cb5b585" })
        assertTrue(ultimaRuta!!.startsWith("/api/schedule/2026/"))
        assertEquals("Bearer token-de-prueba", ultimoToken)

        val congregacional = salidas.first { it.id == "bc1bc6b1-039c-4cd0-9218-31034cb5b585" }
        assertEquals("2026-11-01", congregacional.fecha)
        assertEquals("10:30", congregacional.hora)
        assertEquals(TipoSalida.Congregacional, congregacional.tipo)
        assertEquals("Ana Pérez", congregacional.conductor)
        assertEquals("Casa de la familia Gómez", congregacional.lugarDeEncuentro)
        assertEquals("https://maps.google.com/?q=casa-gomez", congregacional.enlaceGoogleMaps)
        assertEquals(EstadoSalida.PENDIENTE, congregacional.estado)

        val grupo = salidas.first { it.id == "4f0d2f8e-77a1-4f3d-9a55-1e2a3c0d9b11" }
        assertEquals(TipoSalida.Grupo(2), grupo.tipo)
        assertEquals(listOf(4, 5), grupo.territorios)

        assertFalse(salidas.any { it.id == "sin-tipo" })
    }

    @Test
    fun elReporteUsaElIdRealDeLaSalida() = runBlocking {
        cuerpo = salidasJson.toString()
        val salida = fuente().obtenerSalidas().first { it.id.startsWith("4f0d2f8e") }

        val json = JSONObject(
            com.example.geditorio.data.reporte.ReporteHttp.cuerpoJson(
                com.example.geditorio.data.model.ReporteSalida(salida.id, true, "ok", "T 4")
            )
        )

        assertEquals("4f0d2f8e-77a1-4f3d-9a55-1e2a3c0d9b11", json.getString("salidaId"))
    }

    @Test
    fun sinSesionNoConsultaNiInventaDatos() = runBlocking {
        cuerpo = salidasJson.toString()

        val error = try {
            fuente(token = null).obtenerSalidas()
            null
        } catch (e: SalidasException) {
            e
        }

        assertEquals(ReporteError.SIN_SESION, error?.error)
        assertEquals(0, pedidos)
    }

    @Test
    fun tokenRechazado_seInformaComoError() = runBlocking {
        codigo = 401
        cuerpo = """{"error":"Token de sesión no válido o vencido"}"""

        val error = try {
            fuente().obtenerSalidas()
            null
        } catch (e: SalidasException) {
            e
        }

        assertEquals(ReporteError.SIN_AUTORIZACION, error?.error)
    }

    @Test
    fun servidorConErrorNoDevuelveListaVacia() = runBlocking {
        codigo = 500
        cuerpo = """{"error":"Error interno del servidor"}"""

        val error = try {
            fuente().obtenerSalidas()
            null
        } catch (e: SalidasException) {
            e
        }

        assertEquals(ReporteError.SERVIDOR, error?.error)
    }

    @Test
    fun servidorCaido_seInformaErrorDeConexion() = runBlocking {
        val puerto = server.address.port
        server.stop(0)
        val fuenteCaida = ServidorSalidaDataSource(
            cliente = CronogramaHttp("http://127.0.0.1:$puerto", timeoutMs = 2000),
            tokenProvider = object : TokenProvider {
                override suspend fun idToken() = "token-de-prueba"
            },
            zona = ZoneOffset.UTC,
            hoy = { hoy }
        )

        val error = try {
            fuenteCaida.obtenerSalidas()
            null
        } catch (e: SalidasException) {
            e
        }

        assertEquals(ReporteError.SIN_CONEXION, error?.error)
    }

    @Test
    fun unRepositorioConCronogramaRealEnviaElIdDelServidor() = runBlocking {
        cuerpo = salidasJson.toString()
        val repositorio = SalidaRepository(
            dataSource = fuente(),
            reporteSender = object : ReporteSender {
                var idEnviado: String? = null
                override suspend fun enviar(
                    reporte: com.example.geditorio.data.model.ReporteSalida,
                    claveIdempotencia: String
                ): com.example.geditorio.data.reporte.EnvioReporte {
                    idEnviado = reporte.salidaId
                    return com.example.geditorio.data.reporte.EnvioReporte.guardado(reporte.salidaId, "ahora")
                }
            },
            tokenProvider = object : TokenProvider {
                override suspend fun idToken() = "token-de-prueba"
            }
        )

        val proxima = repositorio.proximaSalida()!!
        assertEquals("2026-11-01", proxima.fecha)
        val resultado = repositorio.enviarReporte(
            com.example.geditorio.data.model.ReporteSalida(proxima.id, true, "", "T 1")
        )
        assertTrue(resultado.guardado)
        assertEquals("bc1bc6b1-039c-4cd0-9218-31034cb5b585", resultado.salidaId)
    }
}
