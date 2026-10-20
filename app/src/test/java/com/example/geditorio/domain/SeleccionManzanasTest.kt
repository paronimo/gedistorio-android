package com.example.geditorio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Pruebas unitarias para la lectura del GeoJSON de manzanas, la seleccion
 * individual y por territorio, el desmarcado, el calculo de faltantes y el
 * tratamiento de identificadores ausentes o invalidos.
 */
class SeleccionManzanasTest {

    private lateinit var geoData: ManzanasGeoData

    @Before
    fun setUp() {
        val geoJsonFile = File("src/main/assets/manzanas.geojson")
        val altFile = File("app/src/main/assets/manzanas.geojson")
        val archivo = if (geoJsonFile.exists()) geoJsonFile else altFile
        assertTrue("El archivo manzanas.geojson debe existir para las pruebas", archivo.exists())
        geoData = parseManzanasGeoJson(archivo.readText())
    }

    @Test
    fun lecturaGeoJson_distribucionPorTerritorio() {
        // El archivo debe contener exactamente 19 manzanas en 6 territorios (1 a 6)
        assertEquals(19, geoData.manzanas.size)
        assertEquals(6, geoData.territorios.size)
        assertTrue(geoData.invalidos.isEmpty())

        val t1 = geoData.manzanasDe("1").map { it.idManzana }
        val t2 = geoData.manzanasDe("2").map { it.idManzana }
        val t3 = geoData.manzanasDe("3").map { it.idManzana }
        val t4 = geoData.manzanasDe("4").map { it.idManzana }
        val t5 = geoData.manzanasDe("5").map { it.idManzana }
        val t6 = geoData.manzanasDe("6").map { it.idManzana }

        assertEquals(listOf("7243", "7244", "7247"), t1)
        assertEquals(listOf("7242", "7245", "7246"), t2)
        assertEquals(listOf("7249", "7252", "7253", "7254"), t3)
        assertEquals(listOf("7250", "7251", "7270"), t4)
        assertEquals(listOf("7255", "7256", "7258"), t5)
        assertEquals(listOf("7238", "7239", "7240"), t6)
    }

    @Test
    fun seleccionUnaManzana_calculaFaltantesCorrectamente() {
        // Ejemplo esperado: Solo manzana 7250 -> "T 4 : 7250 (Faltan: 7251, 7270)"
        val m7250 = geoData.manzanaPorId("7250")
        assertNotNull(m7250)

        val seleccion = SeleccionMapa().conManzanaIndividual(m7250!!)
        val texto = seleccionATexto(seleccion) { tId -> geoData.manzanasDe(tId) }

        assertEquals("T 4 : 7250 (Faltan: 7251, 7270)", texto)
        assertTrue(seleccion.estaMarcada(m7250))
    }

    @Test
    fun seleccionDosManzanas_calculaFaltantesCorrectamente() {
        // Ejemplo esperado: Manzanas 7250 y 7251 -> "T 4 : 7250, 7251 (Faltan: 7270)"
        val m7250 = geoData.manzanaPorId("7250")!!
        val m7251 = geoData.manzanaPorId("7251")!!

        val seleccion = SeleccionMapa()
            .conManzanaIndividual(m7250)
            .conManzanaIndividual(m7251)
        val texto = seleccionATexto(seleccion) { tId -> geoData.manzanasDe(tId) }

        assertEquals("T 4 : 7250, 7251 (Faltan: 7270)", texto)
    }

    @Test
    fun seleccionTerritorioCompleto_muestraSoloNumeroDeTerritorio() {
        // Ejemplo esperado: Territorio completo -> "T 4" (sin enumerar manzanas)
        val seleccion = SeleccionMapa().conTerritorioCompleto("4")
        val texto = seleccionATexto(seleccion) { tId -> geoData.manzanasDe(tId) }

        assertEquals("T 4", texto)

        // Todas las manzanas de T 4 se consideran marcadas visualmente
        geoData.manzanasDe("4").forEach { m ->
            assertTrue(seleccion.estaMarcada(m))
        }
    }

    @Test
    fun diferenciaTerritorioCompleto_deSeleccionIndividualDeTodasSusManzanas() {
        // Si el usuario marca individualmente las 3 manzanas de T 4, se listan todas sin faltantes
        // ("T 4 : 7250, 7251, 7270"), mientras que territorio completo es simplemente "T 4".
        val m7250 = geoData.manzanaPorId("7250")!!
        val m7251 = geoData.manzanaPorId("7251")!!
        val m7270 = geoData.manzanaPorId("7270")!!

        val seleccionIndividual = SeleccionMapa()
            .conManzanaIndividual(m7250)
            .conManzanaIndividual(m7251)
            .conManzanaIndividual(m7270)
        val textoIndividual = seleccionATexto(seleccionIndividual) { tId -> geoData.manzanasDe(tId) }

        val seleccionCompleta = SeleccionMapa().conTerritorioCompleto("4")
        val textoCompleto = seleccionATexto(seleccionCompleta) { tId -> geoData.manzanasDe(tId) }

        assertEquals("T 4 : 7250, 7251, 7270", textoIndividual)
        assertEquals("T 4", textoCompleto)
        assertFalse(seleccionIndividual.territorioCompleto("4"))
        assertTrue(seleccionCompleta.territorioCompleto("4"))
    }

    @Test
    fun seleccionManzanasDeDistintosTerritorios_separaCadaTerritorio() {
        // Manzana 7250 (T 4) y manzana 7243 (T 1)
        val m7250 = geoData.manzanaPorId("7250")!!
        val m7243 = geoData.manzanaPorId("7243")!!

        val seleccion = SeleccionMapa()
            .conManzanaIndividual(m7250)
            .conManzanaIndividual(m7243)
        val texto = seleccionATexto(seleccion) { tId -> geoData.manzanasDe(tId) }

        val lineas = texto.split("\n")
        assertEquals(2, lineas.size)
        assertEquals("T 1 : 7243 (Faltan: 7244, 7247)", lineas[0])
        assertEquals("T 4 : 7250 (Faltan: 7251, 7270)", lineas[1])
    }

    @Test
    fun seleccionTerritorioCompleto_masManzanaDeOtroTerritorio() {
        // T 4 completo + manzana 7243 (T 1)
        val m7243 = geoData.manzanaPorId("7243")!!
        val seleccion = SeleccionMapa()
            .conTerritorioCompleto("4")
            .conManzanaIndividual(m7243)
        val texto = seleccionATexto(seleccion) { tId -> geoData.manzanasDe(tId) }

        val lineas = texto.split("\n")
        assertEquals(2, lineas.size)
        assertEquals("T 4", lineas[0])
        assertEquals("T 1 : 7243 (Faltan: 7244, 7247)", lineas[1])
    }

    @Test
    fun desmarcadoDeManzanaYDeTerritorio() {
        val m7250 = geoData.manzanaPorId("7250")!!

        // Marcar y luego quitar manzana individual
        var sel = SeleccionMapa().conManzanaIndividual(m7250)
        assertTrue(sel.estaMarcada(m7250))
        sel = sel.sinManzana(m7250)
        assertFalse(sel.estaMarcada(m7250))
        assertTrue(sel.vacia)

        // Alternar manzana individual
        sel = sel.alternarManzanaIndividual(m7250)
        assertTrue(sel.estaMarcada(m7250))
        sel = sel.alternarManzanaIndividual(m7250)
        assertFalse(sel.estaMarcada(m7250))
        assertTrue(sel.vacia)

        // Marcar y luego quitar territorio completo
        sel = sel.conTerritorioCompleto("4")
        assertTrue(sel.territorioCompleto("4"))
        sel = sel.sinTerritorioCompleto("4")
        assertFalse(sel.territorioCompleto("4"))
        assertTrue(sel.vacia)
    }

    @Test
    fun cancelacion_noModificaSeleccionPrevia() {
        val m7250 = geoData.manzanaPorId("7250")!!
        val estadoInicial = SeleccionMapa().conManzanaIndividual(m7250)

        // Simular que el usuario abre el dialogo y elige "Cancelar"
        val estadoDespuesDeCancelar = estadoInicial // No se aplica ninguna modificacion
        assertEquals(estadoInicial, estadoDespuesDeCancelar)
        assertEquals("T 4 : 7250 (Faltan: 7251, 7270)", seleccionATexto(estadoDespuesDeCancelar) { t -> geoData.manzanasDe(t) })
    }

    @Test
    fun geoJsonConIdentificadoresAusentesOInvalidos() {
        val geoJsonInvalido = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "idManzana": "101", "territorioId": "1" },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[0,0], [0,1], [1,1], [1,0], [0,0]]]
                  }
                },
                {
                  "type": "Feature",
                  "properties": { "territorioId": "1" },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[0,0], [0,1], [1,1], [0,0]]]
                  }
                },
                {
                  "type": "Feature",
                  "properties": { "idManzana": "102" },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[0,0], [0,1], [1,1], [0,0]]]
                  }
                },
                {
                  "type": "Feature",
                  "properties": { "idManzana": "103", "territorioId": "1" },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[0,0], [0,1]]]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = parseManzanasGeoJson(geoJsonInvalido)
        // Solo la feature 101 debe ser valida; las demas van a invalidos sin inventar datos
        assertEquals(1, parsed.manzanas.size)
        assertEquals("101", parsed.manzanas[0].idManzana)
        assertEquals(3, parsed.invalidos.size)
    }
}
