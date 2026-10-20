package com.example.geditorio.push

import com.example.geditorio.data.ReporteError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Fallo al registrar el celular para recibir notificaciones. */
class RegistroDispositivoException(
    val error: ReporteError,
    cause: Throwable? = null
) : Exception(cause?.message, cause)

/**
 * Alta y baja del token FCM del celular en el backend.
 *
 * El token de dispositivo es la identidad del celular ante FCM. El backend lo
 * liga a la cuenta que inició sesión (lo deduce del token de Firebase que
 * enviamos en Authorization): el celular no puede elegir a nombre de quién se
 * registra. Nunca se envía la contraseña ni claves de Firebase del servidor.
 *
 *   POST   /api/notifications/devices   { token, platform: "android" }
 *   DELETE /api/notifications/devices   { token }
 */
class RegistroDispositivoHttp(
    private val baseUrl: String,
    private val timeoutMs: Int = 15_000,
    private val conexion: (URL) -> HttpURLConnection = { url -> url.openConnection() as HttpURLConnection }
) {
    suspend fun registrar(token: String, idToken: String): Boolean = enviar(
        metodo = "POST",
        token = token,
        idToken = idToken
    ).optBoolean("registered", true)

    suspend fun darDeBaja(token: String, idToken: String) {
        enviar(metodo = "DELETE", token = token, idToken = idToken)
    }

    private suspend fun enviar(metodo: String, token: String, idToken: String): JSONObject =
        withContext(Dispatchers.IO) {
            if (baseUrl.isBlank()) throw RegistroDispositivoException(ReporteError.SIN_CONFIGURAR)
            if (idToken.isBlank()) throw RegistroDispositivoException(ReporteError.SIN_SESION)
            if (token.isBlank()) throw RegistroDispositivoException(ReporteError.GENERICO)
            val url = URL(baseUrl.trimEnd('/') + "/api/notifications/devices")
            var abierta: HttpURLConnection? = null
            try {
                val request = conexion(url).apply {
                    requestMethod = metodo
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    doOutput = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $idToken")
                }
                abierta = request
                val cuerpo = JSONObject().put("token", token).put("platform", "android").toString()
                request.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
                val codigo = request.responseCode
                if (codigo !in 200..299) {
                    val mensaje = request.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val error = when {
                        codigo == HttpURLConnection.HTTP_UNAUTHORIZED || codigo == HttpURLConnection.HTTP_FORBIDDEN ->
                            ReporteError.SIN_AUTORIZACION
                        codigo in 400..499 -> ReporteError.SERVIDOR
                        else -> ReporteError.SERVIDOR
                    }
                    throw RegistroDispositivoException(error, IOException(mensaje.take(200)))
                }
                val texto = request.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                JSONObject(texto)
            } catch (e: RegistroDispositivoException) {
                throw e
            } catch (e: IOException) {
                throw RegistroDispositivoException(ReporteError.SIN_CONEXION, e)
            } catch (e: JSONException) {
                throw RegistroDispositivoException(ReporteError.SERVIDOR, e)
            } finally {
                abierta?.disconnect()
            }
        }
}
