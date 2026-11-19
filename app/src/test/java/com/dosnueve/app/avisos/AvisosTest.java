package com.dosnueve.app.avisos;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDateTime;

/** El recordatorio sale los domingos a las 7 p. m. (2026-11-22 es domingo). */
public class AvisosTest {

    private static final LocalDateTime ESTE_DOMINGO = LocalDateTime.of(2026, 11, 22, 19, 0);

    @Test
    public void entreSemanaVaAlDomingo() {
        assertEquals(ESTE_DOMINGO, Avisos.proximoAviso(LocalDateTime.of(2026, 11, 18, 10, 0)));
    }

    @Test
    public void elDomingoAntesDeLas7EsEseMismoDia() {
        assertEquals(ESTE_DOMINGO, Avisos.proximoAviso(LocalDateTime.of(2026, 11, 22, 18, 0)));
    }

    @Test
    public void justoDespuesDeAvisarPasaALaOtraSemana() {
        assertEquals(ESTE_DOMINGO.plusWeeks(1),
                Avisos.proximoAviso(LocalDateTime.of(2026, 11, 22, 19, 0, 10)));
    }

    @Test
    public void elDomingoEnLaNochePasaALaOtraSemana() {
        assertEquals(ESTE_DOMINGO.plusWeeks(1),
                Avisos.proximoAviso(LocalDateTime.of(2026, 11, 22, 21, 30)));
    }
}
