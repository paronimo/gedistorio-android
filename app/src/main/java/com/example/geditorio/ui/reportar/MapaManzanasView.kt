package com.example.geditorio.ui.reportar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.geditorio.domain.Manzana
import com.example.geditorio.domain.Punto

/**
 * Mapa de manzanas dibujado a mano con Canvas.
 *
 * Dibuja los poligonos del GeoJSON tal como vienen (sin deformar), permite
 * acercar/alejar/desplazar y avisa cuando se toca una manzana.
 *
 * El "mundo" de los datos esta en grados (x = longitud, y = latitud). Para
 * dibujar se usa una proyeccion simple: se ajusta el ancho y el alto por
 * separado y se toma el menor factor, asi las proporciones no se deforman.
 */
class MapaManzanasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Se avisa con la manzana tocada, o null si se toco el fondo. */
    var onManzanaTocada: ((Manzana?) -> Unit)? = null

    private var manzanas: List<Manzana> = emptyList()
    private var seleccionadas: Set<Manzana> = emptySet()

    // Encuadre del mundo en el lienzo, calculado en onSizeChanged.
    private var minX = 0f
    private var maxX = 0f
    private var minY = 0f
    private var maxY = 0f
    private var escalaBase = 1f
    private var centroX = 0f
    private var centroY = 0f

    // Transformacion del usuario sobre el encuadre base.
    private var escala = 1f
    private var desplazamientoX = 0f
    private var desplazamientoY = 0f

    private val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EFEFEF")
    }
    private val rellenoSeleccionado = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#A5D6A7")
    }
    private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#616161")
    }
    private val bordeSeleccionado = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#2E7D32")
    }
    private val etiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textAlign = Paint.Align.CENTER
        textSize = 26f
        isFakeBoldText = true
    }

    private val gestos = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean {
            desplazamientoX -= distanceX
            desplazamientoY -= distanceY
            invalidate()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onManzanaTocada?.invoke(manzanaEn(e.x, e.y))
            return true
        }
    })

    private val gestoEscala = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            escala = (escala * detector.scaleFactor).coerceIn(ESCALA_MINIMA, ESCALA_MAXIMA)
            invalidate()
            return true
        }
    })

    /** Carga las manzanas a dibujar. */
    fun setManzanas(lista: List<Manzana>) {
        manzanas = lista
        recalcularEncuadre()
        invalidate()
    }

    /** Manzanas que se pintan en verde. */
    fun setSeleccionadas(lista: Set<Manzana>) {
        seleccionadas = lista
        invalidate()
    }

    /** Vuelve al encuadre inicial. */
    fun reiniciarVista() {
        escala = 1f
        desplazamientoX = 0f
        desplazamientoY = 0f
        invalidate()
    }

    /** Acerca el mapa un paso. */
    fun acercar() {
        escala = (escala * 1.3f).coerceIn(ESCALA_MINIMA, ESCALA_MAXIMA)
        invalidate()
    }

    /** Aleja el mapa un paso. */
    fun alejar() {
        escala = (escala / 1.3f).coerceIn(ESCALA_MINIMA, ESCALA_MAXIMA)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcularEncuadre()
    }

    /** Calcula el encuadre base a partir de la extension de todos los poligonos. */
    private fun recalcularEncuadre() {
        val puntos = manzanas.flatMap { it.contorno }
        if (puntos.isEmpty() || width == 0 || height == 0) {
            escalaBase = 1f
            return
        }

        minX = puntos.minOf { it.x }
        maxX = puntos.maxOf { it.x }
        minY = puntos.minOf { it.y }
        maxY = puntos.maxOf { it.y }

        val anchoDatos = (maxX - minX).takeIf { it > 0f } ?: 1f
        val altoDatos = (maxY - minY).takeIf { it > 0f } ?: 1f

        val margen = MARGEN_DP * resources.displayMetrics.density
        val anchoUtil = (width - margen * 2).coerceAtLeast(1f)
        val altoUtil = (height - margen * 2).coerceAtLeast(1f)

        // Menor factor: se usa el mismo en ambos ejes para no deformar.
        escalaBase = minOf(anchoUtil / anchoDatos, altoUtil / altoDatos)
        centroX = width / 2f
        centroY = height / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (manzanas.isEmpty() || escalaBase <= 0f) return

        val path = Path()
        for (manzana in manzanas) {
            if (manzana.contorno.size < 3) continue
            path.rewind()
            manzana.contorno.forEachIndexed { indice, punto ->
                val p = aPantalla(punto)
                if (indice == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()

            val marcada = manzana in seleccionadas
            canvas.drawPath(path, if (marcada) rellenoSeleccionado else relleno)
            canvas.drawPath(path, if (marcada) bordeSeleccionado else borde)

            val centro = aPantalla(manzana.centro)
            canvas.drawText(manzana.idManzana, centro.x, centro.y + etiqueta.textSize / 3f, etiqueta)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        gestoEscala.onTouchEvent(event)
        gestos.onTouchEvent(event)
        return true
    }

    /** Convierte una coordenada del mundo (grados) a pixeles del lienzo. */
    private fun aPantalla(punto: Punto): PointF {
        val factor = escalaBase * escala
        val x = centroX + ((punto.x - (minX + maxX) / 2f) * factor) + desplazamientoX
        // La latitud crece hacia arriba, la pantalla hacia abajo: se invierte.
        val y = centroY - ((punto.y - (minY + maxY) / 2f) * factor) + desplazamientoY
        return PointF(x, y)
    }

    /** Manzana cuyo contorno contiene el punto tocado. La ultima dibujada gana. */
    private fun manzanaEn(x: Float, y: Float): Manzana? =
        manzanas.lastOrNull { contiene(it, x, y) }

    private fun contiene(manzana: Manzana, x: Float, y: Float): Boolean {
        val contorno = manzana.contorno
        if (contorno.size < 3) return false

        // Caja envolvente primero: descarte rapido antes del calculo fino.
        val rect = RectF(
            Float.MAX_VALUE, Float.MAX_VALUE,
            -Float.MAX_VALUE, -Float.MAX_VALUE,
        )
        for (punto in contorno) {
            val p = aPantalla(punto)
            rect.left = minOf(rect.left, p.x)
            rect.top = minOf(rect.top, p.y)
            rect.right = maxOf(rect.right, p.x)
            rect.bottom = maxOf(rect.bottom, p.y)
        }
        if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) return false

        // Ray casting: cuenta los cruces de un rayo horizontal.
        var dentro = false
        var j = contorno.lastIndex
        for (i in contorno.indices) {
            val pi = aPantalla(contorno[i])
            val pj = aPantalla(contorno[j])
            val cruza = ((pi.y > y) != (pj.y > y)) &&
                (x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x)
            if (cruza) dentro = !dentro
            j = i
        }
        return dentro
    }

    companion object {
        private const val MARGEN_DP = 12f
        private const val ESCALA_MINIMA = 0.5f
        private const val ESCALA_MAXIMA = 12f
    }
}
