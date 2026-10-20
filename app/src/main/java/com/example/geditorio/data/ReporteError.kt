package com.example.geditorio.data

/**
 * Motivos por los que un reporte no pudo guardarse en el backend.
 * La pantalla los traduce a un mensaje (strings.xml), asi el error de
 * conexion nunca se confunde con un envio exitoso.
 */
enum class ReporteError {
    /** Ya hay un reporte enviandose: se ignora el segundo envio. */
    ENVIO_EN_CURSO,

    /** No hay URL de servidor configurada. */
    SIN_CONFIGURAR,

    /** No hay sesion de Firebase: el backend rechazaria el envio. */
    SIN_SESION,

    /** El backend rechazo el token (vencido, revocado o de otro proyecto). */
    SIN_AUTORIZACION,

    /** La salida del reporte no existe en el cronograma del gestor. */
    SALIDA_NO_ENCONTRADA,

    /** Sin red, servidor apagado, URL inalcanzable o tiempo de espera agotado. */
    SIN_CONEXION,

    /** El backend respondio 4xx/5xx o no confirmo el guardado. */
    SERVIDOR,

    /** Cualquier otro fallo inesperado del cliente. */
    GENERICO
}
