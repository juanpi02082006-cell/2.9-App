package com.dosnueve.app.ui.onboarding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UniversidadTest {

    private final Universidad nacional = new Universidad(1101, "Universidad Nacional de Colombia",
            "Bogotá, D.C.", new double[]{30, 30, 40}, 3.0);
    private final Universidad cordoba = new Universidad(1113, "Universidad de Córdoba",
            "Montería", null, 3.0);

    @Test
    public void buscaSinTildesNiMayusculas() {
        assertTrue(cordoba.coincide("CORDOBA"));
        assertTrue(nacional.coincide("bogota"));
    }

    @Test
    public void cadaPalabraTieneQueEstar() {
        assertTrue(nacional.coincide("nacional  colombia"));
        assertFalse(nacional.coincide("nacional medellin"));
    }

    @Test
    public void sinBusquedaSalenTodas() {
        assertTrue(cordoba.coincide(""));
        assertTrue(cordoba.coincide("   "));
    }

    @Test
    public void sinFormaConocidaUsaLaPorDefectoYMuestraLaCiudad() {
        assertEquals(3, cordoba.cortes.length);
        assertEquals("Montería", cordoba.detalle);
    }
}
