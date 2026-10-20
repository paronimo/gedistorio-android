package com.example.geditorio.data.reporte

import com.example.geditorio.data.ReporteError
import com.example.geditorio.data.model.ReporteSalida
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Pruebas del cliente HTTP del envio de reportes, con un servidor HTTP real
 * levantado en localhost. Comprueban que:
 *  - un 201/200 del backend se informa como guardado;
 *  - un 401, 404 o 500 NUNCA se informan como guardados;
 *  - un servidor caido se informa como error de conexion;
 *  - se envia el token, la clave de idempotencia y el texto del territorio.
 */
class ReporteHttpTest {

    private lateinit var server: HttpServer
    private var codigoRespuesta = 201
    private var cuerpoRespuesta = ""
    private var ultimoMetodo: String? = null
    private var ultimoToken: String? = null
    private var ultimaClave: String? = null
    private var ultimoCuerpo: String? = null

    private val reporte = ReporteSalida(
        salidaId = "salida-1",
        realizada = true,
        observaciones = "Buena participación",
        territorioTexto = "T 4 : 7250, 7251 (Faltan: 7270)"
    )

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/reports") { exchange: HttpExchange ->
            ultimoMetodo = exchange.requestMethod
            ultimoToken = exchange.requestHeaders.getFirst("Authorization")
            ultimaClave = exchange.requestHeaders.getFirst("Idempotency-Key")
            ultimoCuerpo = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val bytes = cuerpoRespuesta.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(codigoRespuesta, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun cliente() = ReporteHttp("http://127.0.0.1:${server.address.port}")

    private fun enviar() = runBlocking { cliente().enviar(reporte, "token-de-prueba", "clave-1") }

    @Test
    fun reporteNuevo_elBackendConfirma_201() {
        cuerpoRespuesta = """{"report":{"salidaId":"salida-1","receivedAt":"2026-10-19T10:00:00Z"},"duplicate":false}"""

        val resultado = enviar()

        assertTrue(resultado.guardado)
        assertFalse(resultado.duplicado)
        assertEquals("salida-1", resultado.salidaId)
        assertEquals("2026-10-19T10:00:00Z", resultado.recibidoEn)
        assertEquals("POST", ultimoMetodo)
        assertEquals("Bearer token-de-prueba", ultimoToken)
        assertEquals("clave-1", ultimaClave)
    }

    @Test
    fun reintento_elBackendDevuelve_200_duplicado() {
        codigoRespuesta = 200
        cuerpoRespuesta = """{"report":{"salidaId":"salida-1","receivedAt":"2026-10-19T10:00:00Z"},"duplicate":true}"""

        val resultado = enviar()

        assertTrue(resultado.guardado)
        assertTrue(resultado.duplicado)
    }

    @Test
    fun cuerpo_conservaTerritorioResultadoYTextoDeManzanas() {
        cuerpoRespuesta = """{"report":{"salidaId":"salida-1","receivedAt":"x"},"duplicate":false}"""

        enviar()

        val json = JSONObject(ultimoCuerpo!!)
        assertEquals("salida-1", json.getString("salidaId"))
        assertTrue(json.getBoolean("realizada"))
        assertEquals("Buena participación", json.getString("observaciones"))
        assertEquals("T 4 : 7250, 7251 (Faltan: 7270)", json.getString("territorioTexto"))
    }

    @Test
    fun tokenRechazado_401_noEsExito() {
        codigoRespuesta = 401
        cuerpoRespuesta = """{"error":"Token de sesión no válido o vencido"}"""

        val resultado = enviar()

        assertFalse(resultado.guardado)
        assertEquals(ReporteError.SIN_AUTORIZACION, resultado.error)
    }

    @Test
    fun salidaInexistente_404_noEsExito() {
        codigoRespuesta = 404
        cuerpoRespuesta = """{"error":"No se encontró la salida indicada"}"""

        val resultado = enviar()

        assertFalse(resultado.guardado)
        assertEquals(ReporteError.SALIDA_NO_ENCONTRADA, resultado.error)
    }

    @Test
    fun falloDelServidor_500_noEsExito() {
        codigoRespuesta = 500
        cuerpoRespuesta = """{"error":"Error interno del servidor"}"""

        val resultado = enviar()

        assertFalse(resultado.guardado)
        assertEquals(ReporteError.SERVIDOR, resultado.error)
    }

    @Test
    fun respuesta2xxSinReporte_noEsExito() {
        codigoRespuesta = 201
        cuerpoRespuesta = """{"ok":true}"""

        val resultado = enviar()

        assertFalse(resultado.guardado)
        assertEquals(ReporteError.SERVIDOR, resultado.error)
    }

    @Test
    fun servidorCaido_esErrorDeConexion() {
        val puerto = server.address.port
        server.stop(0)

        val resultado = runBlocking {
            ReporteHttp("http://127.0.0.1:$puerto", timeoutMs = 2000)
                .enviar(reporte, "token-de-prueba", "clave-1")
        }

        assertFalse(resultado.guardado)
        assertEquals(ReporteError.SIN_CONEXION, resultado.error)
    }

    @Test
    fun sinUrlConfigurada_noIntentaEnviar() {
        val resultado = runBlocking { ReporteHttp("").enviar(reporte, "token", "clave-1") }

        assertFalse(resultado.guardado)
        assertEquals(ReporteError.SIN_CONFIGURAR, resultado.error)
    }

    @Test
    fun sinToken_noIntentaEnviar() {
        val resultado = runBlocking { cliente().enviar(reporte, "", "clave-1") }

        assertFalse(resultado.guardado)
        assertEquals(ReporteError.SIN_SESION, resultado.error)
        assertEquals(null, ultimoMetodo)
    }
}
