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
        val north = Way(Way.MAJOR, listOf(lat to lng, lat + 0.0009 to lng))
        val east = Way(Way.MINOR, listOf(lat to lng, lat to lng + 0.0013))
        val enc = MapEncoder.encode(listOf(east, north), lat, lng)
        val dec = MapEncoder.decode(enc)
        assertEquals(2, dec.size)
        assertEquals(Way.MAJOR, dec[0].kind, "le principali vengono prima")
        assertTrue(abs(dec[0].pts[1].second - 100.2) <= 1.0)
        assertTrue(abs(dec[1].pts[1].first - 102.5) <= 1.5)
        assertTrue(enc.all { it == ';' || it == '0' || it == '1' || it in '?'..'~' }, "solo caratteri sicuri: $enc")
    }

    @Test fun `rispetta il budget di caratteri e scarta prima le strade minori lontane`() {
        val ways = (0 until 200).map { i ->
            Way(if (i == 0) Way.MAJOR else Way.MINOR, (0..20).map { k -> lat + 0.0001 * k to lng + 0.0001 * i })
        }
        val enc = MapEncoder.encode(ways, lat, lng, maxChars = 400)
        assertTrue(enc.length <= 400)
        assertEquals(Way.MAJOR, MapEncoder.decode(enc).first().kind)
    }

    @Test fun `i punti fuori riquadro vengono tagliati`() {
        val far = Way(Way.MAJOR, listOf(lat to lng, lat + 0.02 to lng))   // ~2,2 km a nord
        val dec = MapEncoder.decode(MapEncoder.encode(listOf(far), lat, lng))
        assertTrue(dec.isEmpty() || dec.all { w -> w.pts.all { abs(it.second) <= MapEncoder.LIMIT_M + 2 } })
    }

    @Test fun `distance`() {
        assertTrue(abs(MapEncoder.distance(lat, lng, lat + 0.001, lng) - 111.3) < 1.0)
    }

    @Test fun `il percorso e' sempre incluso e va per primo, anche col budget esaurito`() {
        val roads = (0 until 100).map { i -> Way(Way.MAJOR, listOf(lat + 0.0001 * (i % 7) to lng + 0.0001 * i, lat + 0.003 to lng + 0.0001 * i)) }
        val route = Way(Way.ROUTE, listOf(lat to lng, lat + 0.003 to lng, lat + 0.003 to lng + 0.003))
        val dec = MapEncoder.decode(MapEncoder.encode(roads + route, lat, lng, maxChars = 60))
        assertEquals(Way.ROUTE, dec.first().kind)
        assertEquals(3, dec.first().pts.size)
    }
}

class MapsLinkParserTest {
    @Test fun `segnaposto, parametri e vista`() {
        val place = "https://www.google.com/maps/place/Duomo/@45.4641,9.1919,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x1!8m2!3d45.4640976!4d9.1919266"
        assertEquals(45.4640976 to 9.1919266, MapsLinkParser.parse(place))
        assertEquals(45.5 to 9.25, MapsLinkParser.parse("https://www.google.com/maps?q=45.5%2C9.25"))
        assertEquals(45.4641 to 9.1919, MapsLinkParser.parse("https://www.google.com/maps/@45.4641,9.1919,17z"))
        assertEquals(45.1 to 9.2, MapsLinkParser.parse("geo:45.1,9.2?q=bar"))
        assertNull(MapsLinkParser.parse("https://maps.app.goo.gl/abc123"))
    }

    @Test fun `estrae l'url dal testo condiviso`() {
        assertEquals("https://maps.app.goo.gl/abc123", MapsLinkParser.extractUrl("Bar Roma\nVia X 1\nhttps://maps.app.goo.gl/abc123"))
    }
}

class RouteTrackerTest {
    private val lat = 45.0
    private val lng = 9.0
    private val route = (0..20).map { lat + 0.0005 * it to lng }      // ~1,1 km verso nord

    @Test fun `avanza lungo la linea e misura lo scostamento`() {
        val t = RouteTracker(route)
        val off = t.update(lat + 0.0012, lng + 0.0001)                 // ~7,8 m a est
        assertTrue(abs(off - 7.9) < 1.0, "off=$off")
        assertEquals(2, t.idx)
        val rem = t.remaining(lat + 0.0012, lng + 0.0001)
        assertEquals(lat + 0.0012 to lng + 0.0001, rem.first())
        assertEquals(route.last(), rem.last())
        assertEquals(route.size - 2, rem.size)
    }

    @Test fun `lontano dalla linea la distanza e' grande e non torna indietro`() {
        val t = RouteTracker(route)
        t.update(lat + 0.005, lng)
        val before = t.idx
        assertTrue(t.update(lat + 0.0001, lng + 0.01) > 500)
        assertTrue(t.idx >= before)
    }
}
