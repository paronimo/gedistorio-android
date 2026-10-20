package com.example.geditorio.push

import com.example.geditorio.data.ReporteError
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Pruebas del alta/baja del token FCM contra un servidor HTTP real en
 * localhost. Comprueban que:
 *  - el token se envía en el cuerpo y la sesión en Authorization (Bearer);
 *  - nunca se envía el nombre del conductor ni datos de cuenta;
 *  - un 401/403 se informa como falta de autorización;
 *  - sin red se informa como error de conexión y no como registro exitoso;
 *  - sin sesión o sin configuración no se hace la petición.
 */
class RegistroDispositivoHttpTest {

    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private var codigo = 201
    private var ultimoMetodo: String? = null
    private var ultimaRuta: String? = null
    private var ultimaAutorizacion: String? = null
    private var ultimoCuerpo: String? = null

    private val token = "fcm-token-de-prueba-0123456789abcdef"

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/notifications/devices") { exchange: HttpExchange ->
            ultimoMetodo = exchange.requestMethod
            ultimaRuta = exchange.requestURI.path
            ultimaAutorizacion = exchange.requestHeaders.getFirst("Authorization")
            ultimoCuerpo = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val respuesta = "{\"registered\":true}".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(codigo, respuesta.size.toLong())
            exchange.responseBody.use { it.write(respuesta) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `registra el token con la sesion en Authorization`() {
        val cliente = RegistroDispositivoHttp(baseUrl)
        val registrado = runBlocking { cliente.registrar(token, "id-token-del-conductor") }

        assertTrue(registrado)
        assertEquals("POST", ultimoMetodo)
        assertEquals("/api/notifications/devices", ultimaRuta)
        assertEquals("Bearer id-token-del-conductor", ultimaAutorizacion)
        val cuerpo = JSONObject(ultimoCuerpo!!)
        assertEquals(token, cuerpo.getString("token"))
        assertEquals("android", cuerpo.getString("platform"))
        // La app no dice quién es: ni nombre, ni correo, ni uid.
        assertFalse(cuerpo.has("email"))
        assertFalse(cuerpo.has("userId"))
        assertFalse(cuerpo.has("driver"))
    }

    @Test
    fun `la baja usa DELETE con el mismo token`() {
        codigo = 200
        val cliente = RegistroDispositivoHttp(baseUrl)
        runBlocking { cliente.darDeBaja(token, "id-token-del-conductor") }

        assertEquals("DELETE", ultimoMetodo)
        assertEquals(token, JSONObject(ultimoCuerpo!!).getString("token"))
        assertEquals("Bearer id-token-del-conductor", ultimaAutorizacion)
    }

    @Test
    fun `un 401 del backend se informa como falta de autorizacion`() {
        codigo = 401
        val cliente = RegistroDispositivoHttp(baseUrl)
        try {
            runBlocking { cliente.registrar(token, "token-vencido") }
            fail("debio lanzar RegistroDispositivoException")
        } catch (e: RegistroDispositivoException) {
            assertEquals(ReporteError.SIN_AUTORIZACION, e.error)
        }
    }

    @Test
    fun `un servidor caido se informa como error de conexion, no como registro`() {
        val cliente = RegistroDispositivoHttp("http://127.0.0.1:1", timeoutMs = 2_000)
        try {
            runBlocking { cliente.registrar(token, "id-token") }
            fail("debio lanzar RegistroDispositivoException")
        } catch (e: RegistroDispositivoException) {
            assertEquals(ReporteError.SIN_CONEXION, e.error)
        }
    }

    @Test
    fun `sin sesion no se hace ninguna peticion`() {
        val cliente = RegistroDispositivoHttp(baseUrl)
        try {
            runBlocking { cliente.registrar(token, "") }
            fail("debio lanzar RegistroDispositivoException")
        } catch (e: RegistroDispositivoException) {
            assertEquals(ReporteError.SIN_SESION, e.error)
        }
        assertEquals(null, ultimoMetodo)
    }

    @Test
    fun `sin URL configurada no se hace ninguna peticion`() {
        val cliente = RegistroDispositivoHttp("")
        try {
            runBlocking { cliente.registrar(token, "id-token") }
            fail("debio lanzar RegistroDispositivoException")
        } catch (e: RegistroDispositivoException) {
            assertEquals(ReporteError.SIN_CONFIGURAR, e.error)
        }
        assertEquals(null, ultimoMetodo)
    }
}
