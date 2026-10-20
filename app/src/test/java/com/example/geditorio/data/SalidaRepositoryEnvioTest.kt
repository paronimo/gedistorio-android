package com.example.geditorio.data

import com.example.geditorio.data.model.ReporteSalida
import com.example.geditorio.data.reporte.EnvioReporte
import com.example.geditorio.data.reporte.ReporteHttp
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del control de envios del repositorio: mientras un reporte se esta
 * enviando, un segundo toque no manda nada (ni siquiera llega al emisor).
 */
class SalidaRepositoryEnvioTest {

    private val reporte = ReporteSalida("salida-1", true, "", "")

    private class EmisorLento(private val demoraMs: Long) : ReporteSender {
        var llamadas = 0
            private set

        override suspend fun enviar(reporte: ReporteSalida, claveIdempotencia: String): EnvioReporte {
            llamadas += 1
            delay(demoraMs)
            return EnvioReporte.guardado(reporte.salidaId, "ahora")
        }
    }

    private class AuthConToken(private val token: String?) : TokenProvider {
        override suspend fun idToken(): String? = token
    }

    @Test
    fun pulsarDosVecesSeguido_soloSeEnviaUnaVez() = runBlocking {
        val emisor = EmisorLento(200)
        val repositorio = SalidaRepository(
            reporteSender = emisor,
            tokenProvider = AuthConToken("token")
        )

        val primero = async { repositorio.enviarReporte(reporte, "clave-1") }
        delay(50)
        val segundo = repositorio.enviarReporte(reporte, "clave-1")

        assertFalse(segundo.guardado)
        assertEquals(ReporteError.ENVIO_EN_CURSO, segundo.error)

        val resultado = primero.await()
        assertTrue(resultado.guardado)
        assertEquals(1, emisor.llamadas)
    }

    @Test
    fun sinSesion_noSeEnviaNada() = runBlocking {
        val emisor = EmisorLento(0)
        val repositorio = SalidaRepository(
            reporteSender = emisor,
            tokenProvider = AuthConToken(null)
        )

        val resultado = repositorio.enviarReporte(reporte, "clave-1")

        assertFalse(resultado.guardado)
        assertEquals(ReporteError.SIN_SESION, resultado.error)
        assertEquals(0, emisor.llamadas)
    }

    /**
     * Regresion: si ReporteHttp se construye SIN TokenProvider, devuelve
     * SIN_SESION en cada envio aunque la sesion de Firebase sea valida. Ese era
     * el sintoma ("La sesion no es valida" aun cerrando sesion y
     * volviendo a entrar). Aqui se comprueba que, con un TokenProvider, el
     * emisor intenta la conexion en lugar de fallar por sesion.
     */
    @Test
    fun conTokenProvider_elEmisorNoFallaPorSesion() = runBlocking {
        // URL inalcanzable a proposito: lo que importa es que NO devuelva
        // SIN_SESION (eso probaria que no tiene TokenProvider).
        val emisor = ReporteHttp(
            baseUrl = "http://127.0.0.1:1",
            timeoutMs = 2000,
            tokenProvider = AuthConToken("token-valido")
        )

        val resultado = emisor.enviar(reporte, "clave-1")

        assertTrue(
            "Debe intentar enviar con el token, no fallar por sesion",
            resultado.error != ReporteError.SIN_SESION
        )
    }

    @Test
    fun sinTokenProvider_elEmisorFallaPorSesion() = runBlocking {
        // Documenta el defecto que se corrigio: sin tokenProvider, falla siempre.
        val emisor = ReporteHttp(baseUrl = "http://127.0.0.1:1", timeoutMs = 2000)

        val resultado = emisor.enviar(reporte, "clave-1")

        assertFalse(resultado.guardado)
        assertEquals(ReporteError.SIN_SESION, resultado.error)
    }

    @Test
    fun elCuerpoQueSeEnviariaEsElQueEsperaElBackend() {
        val receptor = ReporteSalida(
            salidaId = "s-9",
            realizada = false,
            observaciones = "Llovió",
            territorioTexto = "T 7"
        )

        // org.json no garantiza el orden de las claves: se comparan los campos.
        val json = org.json.JSONObject(ReporteHttp.cuerpoJson(receptor))

        assertEquals("s-9", json.getString("salidaId"))
        assertFalse(json.getBoolean("realizada"))
        assertEquals("Llovió", json.getString("observaciones"))
        assertEquals("T 7", json.getString("territorioTexto"))
        assertEquals(4, json.length())
    }
}
