package com.example.geditorio.data

import com.example.geditorio.BuildConfig
import com.example.geditorio.data.model.EstadoSalida
import com.example.geditorio.data.model.ReporteSalida
import com.example.geditorio.data.model.Salida
import com.example.geditorio.data.reporte.EnvioReporte
import com.example.geditorio.data.reporte.ReporteHttp
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

/**
 * Entrega el ID token de la sesion. Lo implementa [AuthRepository] con
 * Firebase; las pruebas usan un token fijo.
 */
interface TokenProvider {
    suspend fun idToken(): String?
}

/**
 * Punto unico de acceso a los datos de salidas y al envio de reportes.
 *
 * Las salidas son el cronograma real del gestor web: cada una conserva el id
 * del backend, que es el que valida el reporte. Si el servidor no responde,
 * las consultas lanzan [SalidasException] y la pantalla muestra el error; no
 * se sustituyen por datos de ejemplo.
 *
 * Los reportes se envian al mismo backend: este repositorio obtiene el token
 * de Firebase, evita dobles envios y solo informa exito cuando el servidor
 * confirma el guardado.
 */
class SalidaRepository(
    private val dataSource: SalidaDataSource = ServidorSalidaDataSource(
        cliente = CronogramaHttp(BuildConfig.BACKEND_URL),
        tokenProvider = AuthRepository()
    ),
    // El emisor recibe el mismo TokenProvider: sin el, ReporteHttp no puede
    // obtener la sesion de Firebase y devolveria SIN_SESION en cada envio.
    private val tokenProvider: TokenProvider = AuthRepository(),
    private val reporteSender: ReporteSender = ReporteHttp(
        baseUrl = BuildConfig.BACKEND_URL,
        tokenProvider = AuthRepository()
    )
) {
    // Un solo envio a la vez: pulsar Enviar dos veces no crea dos reportes.
    private val mutexEnvio = Mutex()

    /**
     * Proxima salida pendiente o confirmada, la mas cercana en el tiempo.
     * Las ya realizadas o canceladas no se consideran "proxima salida".
     */
    suspend fun proximaSalida(): Salida? = misSalidas()
        .firstOrNull { it.estado == EstadoSalida.PENDIENTE || it.estado == EstadoSalida.CONFIRMADA }
        ?: misSalidas().firstOrNull()

    /** Salidas del cronograma, ordenadas por fecha y hora. */
    suspend fun misSalidas(): List<Salida> = dataSource.obtenerSalidas().sortedWith(
        compareBy({ it.claveFecha }, { it.claveHora })
    )

    suspend fun salidaPorId(id: String): Salida? =
        dataSource.obtenerSalidas().firstOrNull { it.id == id }

    /**
     * Envia un reporte de salida al backend web.
     *
     * [claveIdempotencia] se genera una vez por intento del formulario y se
     * conserva en los reintentos que la pantalla haga del mismo reporte. Si
     * ya hay un envio en curso, este se rechaza sin mandar nada al servidor.
     *
     * Devuelve un [EnvioReporte] con guardado = true solo cuando el backend
     * confirmo que guardo el reporte.
     */
    suspend fun enviarReporte(
        reporte: ReporteSalida,
        claveIdempotencia: String = UUID.randomUUID().toString()
    ): EnvioReporte {
        if (!mutexEnvio.tryLock()) {
            return EnvioReporte.fallido(ReporteError.ENVIO_EN_CURSO)
        }

        return try {
            // Corta antes de contactar al servidor si no hay sesion de Firebase.
            val token = try {
                tokenProvider.idToken()
            } catch (e: Exception) {
                null
            }

            if (token.isNullOrBlank()) {
                EnvioReporte.fallido(ReporteError.SIN_SESION)
            } else {
                reporteSender.enviar(reporte, claveIdempotencia)
            }
        } finally {
            mutexEnvio.unlock()
        }
    }
}
