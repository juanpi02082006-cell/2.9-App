package com.dosnueve.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValidacionesTest {

    @Test
    public void usuarioSugeridoSaleDelCorreo() {
        assertEquals("juanpi_02", Validaciones.usuarioSugerido("Juan.Pi_02@gmail.com"));
        assertEquals("ab0", Validaciones.usuarioSugerido("a.b@gmail.com"));
    }

    @Test
    public void elPrimerIntentoEsElUsuarioTalCual() {
        assertEquals("juanma", Validaciones.usuarioConNumero("juanma", 1));
    }

    @Test
    public void losSiguientesLlevanNumero() {
        assertEquals("juanma2", Validaciones.usuarioConNumero("juanma", 2));
        assertEquals("juanma12", Validaciones.usuarioConNumero("juanma", 12));
    }

    @Test
    public void conNumeroNoPasaDe20() {
        String largo = "abcdefghijklmnopqrst"; // 20
        String conNumero = Validaciones.usuarioConNumero(largo, 12);
        assertEquals("abcdefghijklmnopqr12", conNumero);
        assertTrue(Validaciones.usuarioValido(conNumero));
    }
}
