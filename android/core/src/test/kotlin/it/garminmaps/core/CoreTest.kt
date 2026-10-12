package it.garminmaps.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavParserTest {
    @Test fun `distanza nel titolo, istruzione nel testo`() {
        val s = NavParser.parse("350 m", "Svolta a destra su Via Roma", "12 min · 4,1 km · 14:35")!!
        assertEquals("350 m", s.distance)
        assertEquals("Svolta a destra su Via Roma", s.instruction)
        assertEquals(NavParser.RIGHT, s.maneuver)
        assertEquals("12 min · 4,1 km · 14:35", s.eta)
    }

    @Test fun `distanza nel testo con Tra`() {
        val s = NavParser.parse("Via Dante", "Tra 1,2 km svolta a sinistra", null)!!
        assertEquals("1,2 km", s.distance)
        assertEquals("Via Dante · svolta a sinistra", s.instruction)
        assertEquals(NavParser.LEFT, s.maneuver)
    }

    @Test fun `senza distanza e' null, a meno che la categoria sia navigation`() {
        assertNull(NavParser.parse("Condivisione posizione", "Attiva", null))
        assertNotNull(NavParser.parse("Arrivo alle 14:35", "Sei arrivato", null, isNavigation = true))
    }

    @Test fun `classificazione manovre it ed en`() {
        val c = NavParser::classify
        assertEquals(NavParser.SLIGHT_RIGHT, c("Mantieni la destra"))
        assertEquals(NavParser.SHARP_LEFT, c("Svolta secca a sinistra"))
        assertEquals(NavParser.UTURN, c("Fai un'inversione a U"))
        assertEquals(NavParser.ROUNDABOUT, c("Alla rotatoria prendi la seconda uscita a destra"))
        assertEquals(NavParser.ARRIVE, c("Sei arrivato a destinazione"))
        assertEquals(NavParser.STRAIGHT, c("Prosegui dritto"))
        assertEquals(NavParser.RIGHT, c("Turn right onto Main St"))
        assertEquals(NavParser.SLIGHT_LEFT, c("Slight left"))
        assertEquals(NavParser.UNKNOWN, c("Pedaggio"))
    }
}

class MapEncoderTest {
    private val lat = 45.4642
    private val lng = 9.19

    @Test fun `simplify toglie i punti allineati e tiene gli angoli`() {
        val pts = listOf(0.0 to 0.0, 50.0 to 0.5, 100.0 to 0.0, 100.0 to 100.0)
        assertEquals(listOf(0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0), MapEncoder.simplify(pts, 6.0))
    }

    @Test fun `encode e decode: errore massimo 1 m, classi conservate`() {
        // ~100 m a nord e ~100 m a est del riferimento
        val north = Way(true, listOf(lat to lng, lat + 0.0009 to lng))
        val east = Way(false, listOf(lat to lng, lat to lng + 0.0013))
        val enc = MapEncoder.encode(listOf(east, north), lat, lng)
        val dec = MapEncoder.decode(enc)
        assertEquals(2, dec.size)
        assertTrue(dec[0].major, "le principali vengono prima")
        assertTrue(abs(dec[0].pts[1].second - 100.2) <= 1.0)
        assertTrue(abs(dec[1].pts[1].first - 102.5) <= 1.5)
        assertTrue(enc.all { it == ';' || it == '0' || it == '1' || it in '?'..'~' }, "solo caratteri sicuri: $enc")
    }

    @Test fun `rispetta il budget di caratteri e scarta prima le strade minori lontane`() {
        val ways = (0 until 200).map { i ->
            Way(i == 0, (0..20).map { k -> lat + 0.0001 * k to lng + 0.0001 * i })
        }
        val enc = MapEncoder.encode(ways, lat, lng, maxChars = 400)
        assertTrue(enc.length <= 400)
        assertTrue(MapEncoder.decode(enc).first().major)
    }

    @Test fun `i punti fuori riquadro vengono tagliati`() {
        val far = Way(true, listOf(lat to lng, lat + 0.02 to lng))   // ~2,2 km a nord
        val dec = MapEncoder.decode(MapEncoder.encode(listOf(far), lat, lng))
        assertTrue(dec.isEmpty() || dec.all { w -> w.pts.all { abs(it.second) <= MapEncoder.LIMIT_M + 2 } })
    }

    @Test fun `distance`() {
        assertTrue(abs(MapEncoder.distance(lat, lng, lat + 0.001, lng) - 111.3) < 1.0)
    }
}
