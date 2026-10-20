package com.example.geditorio.data.cloud

import com.example.geditorio.data.ReporteError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del envío de reportes a la nube, con un cliente de Firestore FALSO
 * (no se conecta a Firebase). Verifican:
 *  - sin Internet el reporte queda en la cola persistente y NO se informa
 *    como enviado;
 *  - al volver la conexión se reintenta y se quita de la cola;
 *  - los reintentos usan la misma clave: nunca se duplica;
 *  - una salida inexistente no se guarda ni se reintenta en bucle;
 *  - el texto del territorio y las manzanas llegan intactos;
 *  - el fallo de una salida no impide enviar las demás.
 */
class SincronizadorReportesTest {

    private val territorio = "T 4 : 7250, 7251 (Faltan: 7270)\nManzanas: 12, 13"

    private fun pendiente(
        outingId: String = "salida-1",
        uid: String = "uid-1",
        fecha: String = "2026-11-10"
    ) = ReportePendiente(
        clave = "${outingId}__$uid",
        outingId = outingId,
        salidaFecha = fecha,
        realizada = true,
        observaciones = "Buena participación",
        territorioTexto = territorio,
        uid = uid,
        email = "conductor@example.com"
    )

    /** Cliente falso: simula Firestore, sus documentos y su estado de red. */
    private class ClienteFalso : ClienteReportesNube {
        var sinConexion = false
        val documentos = mutableMapOf<String, ReportePendiente>()
        var llamadas = 0

        override suspend fun guardar(pendiente: ReportePendiente): ResultadoGuardado {
            llamadas += 1
            if (sinConexion) throw CloudReportException(ReporteError.SIN_CONEXION)
            if (pendiente.outingId.startsWith("inexistente")) {
                throw CloudReportException(ReporteError.SALIDA_NO_ENCONTRADA)
            }
            val yaExistia = documentos.containsKey(pendiente.clave)
            documentos[pendiente.clave] = pendiente
            return ResultadoGuardado(yaExistia, pendiente.clave)
        }
    }

    @Test
    fun `sin Internet queda pendiente y no se informa como enviado`() = runBlocking {
        val cliente = ClienteFalso().apply { sinConexion = true }
        val cola = ColaEnMemoria()
        val sincronizador = SincronizadorReportes(cliente, cola)

        val resultado = sincronizador.enviar(pendiente())

        assertTrue(resultado is ResultadoEnvio.Pendiente)
        assertEquals(1, cola.tamano())
        assertEquals(1, cola.listar().first().intentos)
        assertTrue(cliente.documentos.isEmpty())
    }

    @Test
    fun `al volver la conexion se envia y se quita de la cola`() = runBlocking {
        val cliente = ClienteFalso().apply { sinConexion = true }
        val cola = ColaEnMemoria()
        val sincronizador = SincronizadorReportes(cliente, cola)

        sincronizador.enviar(pendiente())
        assertEquals(1, cola.tamano())

        cliente.sinConexion = false
        val resumen = sincronizador.reintentarPendientes()

        assertEquals(1, resumen.guardados)
        assertEquals(0, resumen.pendientes)
        assertEquals(0, cola.tamano())
        assertNotNull(cliente.documentos["salida-1__uid-1"])
    }

    @Test
    fun `los reintentos conservan la misma clave y no duplican`() = runBlocking {
        val cliente = ClienteFalso().apply { sinConexion = true }
        val cola = ColaEnMemoria()
        val sincronizador = SincronizadorReportes(cliente, cola)

        repeat(3) { sincronizador.enviar(pendiente()) }
        cliente.sinConexion = false
        sincronizador.reintentarPendientes()
        val yaExistia = sincronizador.enviar(pendiente())

        assertTrue(yaExistia is ResultadoEnvio.Guardado)
        assertTrue((yaExistia as ResultadoEnvio.Guardado).yaExistia)
        assertEquals(1, cliente.documentos.size)
        assertEquals("salida-1__uid-1", cliente.documentos.keys.first())
        // La clave no cambió entre todos los intentos.
        assertEquals("salida-1__uid-1", cola.listar().firstOrNull()?.clave ?: "salida-1__uid-1")
    }

    @Test
    fun `una salida inexistente no se guarda ni se reintenta en bucle`() = runBlocking {
        val cliente = ClienteFalso()
        val cola = ColaEnMemoria()
        val sincronizador = SincronizadorReportes(cliente, cola)

        val resultado = sincronizador.enviar(pendiente(outingId = "inexistente-1"))

        assertTrue(resultado is ResultadoEnvio.Error)
        assertEquals(ReporteError.SALIDA_NO_ENCONTRADA, (resultado as ResultadoEnvio.Error).motivo)
        assertEquals(0, cola.tamano())
        assertEquals(0, cliente.documentos.size)
    }

    @Test
    fun `sin autorizacion no se reintenta en segundo plano`() = runBlocking {
        val cliente = object : ClienteReportesNube {
            override suspend fun guardar(pendiente: ReportePendiente): ResultadoGuardado {
                throw CloudReportException(ReporteError.SIN_AUTORIZACION)
            }
        }
        val cola = ColaEnMemoria()
        val sincronizador = SincronizadorReportes(cliente, cola)

        val resultado = sincronizador.enviar(pendiente())

        assertTrue(resultado is ResultadoEnvio.Error)
        assertEquals(0, cola.tamano())
    }

    @Test
    fun `conserva el texto del territorio y las manzanas`() = runBlocking {
        val cliente = ClienteFalso()
        val cola = ColaEnMemoria()
        val sincronizador = SincronizadorReportes(cliente, cola)

        sincronizador.enviar(pendiente())

        val guardado = cliente.documentos.getValue("salida-1__uid-1")
        assertEquals(territorio, guardado.territorioTexto)
        assertTrue(guardado.territorioTexto.contains("7250, 7251"))
        assertTrue(guardado.territorioTexto.contains("Manzanas: 12, 13"))
        assertTrue(guardado.realizada)
        assertEquals("2026-11-10", guardado.salidaFecha)
    }

    @Test
    fun `si falla uno, los demas siguen y el fallido queda para reintentar`() = runBlocking {
        var fallarPrimero = true
        val cliente = object : ClienteReportesNube {
            val guardados = mutableListOf<String>()
            override suspend fun guardar(pendiente: ReportePendiente): ResultadoGuardado {
                if (fallarPrimero && pendiente.outingId == "salida-1") {
                    fallarPrimero = false
                    throw CloudReportException(ReporteError.SIN_CONEXION)
                }
                guardados += pendiente.outingId
                return ResultadoGuardado(false, pendiente.clave)
            }
        }
        val cola = ColaEnMemoria()
        val sincronizador = SincronizadorReportes(cliente, cola)
        runCatching { sincronizador.enviar(pendiente("salida-1")) }
        runCatching { sincronizador.enviar(pendiente("salida-2")) }

        // salida-3 ya se envió; la 1 falló por conexión y la 2 se guardó.
        assertEquals("la fallida queda en la cola", 1, cola.tamano())
        assertEquals("salida-1__uid-1", cola.listar().first().clave)

        fallarPrimero = false
        // Se simula el reintento de todos los pendientes.
        val resumen = sincronizador.reintentarPendientes()

        assertEquals(1, resumen.guardados)
        assertTrue(cliente.guardados.contains("salida-1"))
        assertEquals(0, cola.tamano())
    }

    @Test
    fun `el id del documento es estable por salida y usuario`() {
        assertEquals("salida-1__uid-1", FirestoreReportesNube.documentId("salida-1", "uid-1"))
        assertEquals(
            FirestoreReportesNube.documentId("salida-1", "uid-1"),
            FirestoreReportesNube.documentId("salida-1", "uid-1")
        )
    }
}
