package com.dosnueve.app.avisos;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.dosnueve.app.MainActivity;
import com.dosnueve.app.R;
import com.dosnueve.app.data.modelo.Materia;
import com.dosnueve.app.util.Formato;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.TimeUnit;

/**
 * Las notificaciones de 2.9 (RF-31 y RF-32): el recordatorio de los domingos y el aviso
 * de materia en rojo. Se encienden en Perfil.
 */
public final class Avisos {

    private static final String CANAL_RECORDATORIO = "recordatorio";
    private static final String CANAL_RIESGO = "riesgo";
    private static final String TRABAJO_RECORDATORIO = "recordatorio-semanal";
    private static final int ID_RECORDATORIO = 1;

    /** El recordatorio sale los domingos a las 7 p. m. (lo escogió Juanma). */
    static final DayOfWeek DIA = DayOfWeek.SUNDAY;
    static final LocalTime HORA = LocalTime.of(19, 0);

    private Avisos() {
    }

    /** Se llama una vez, al arrancar: los canales son los que el usuario ve en Ajustes. */
    public static void crearCanales(Context contexto) {
        NotificationManagerCompat avisos = NotificationManagerCompat.from(contexto);
        avisos.createNotificationChannel(new NotificationChannelCompat.Builder(
                CANAL_RECORDATORIO, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(contexto.getString(R.string.aviso_canal_recordatorio))
                .build());
        avisos.createNotificationChannel(new NotificationChannelCompat.Builder(
                CANAL_RIESGO, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(contexto.getString(R.string.aviso_canal_riesgo))
                .build());
    }

    /** Desde Android 13 hace falta el permiso; antes, que no estén apagadas en Ajustes. */
    public static boolean puedeNotificar(Context contexto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(contexto).areNotificationsEnabled();
    }

    // ===== Recordatorio semanal (RF-31) =====

    /**
     * Programa el próximo domingo a las 7 p. m. Es un trabajo de una sola vez que, al
     * salir, programa el siguiente: así no se va corriendo de hora semana tras semana.
     *
     * @param siYaHay KEEP para no duplicarlo; APPEND_OR_REPLACE desde el propio trabajo.
     */
    public static void encenderRecordatorio(Context contexto, ExistingWorkPolicy siYaHay) {
        long espera = Duration.between(LocalDateTime.now(), proximoAviso(LocalDateTime.now())).toMillis();
        OneTimeWorkRequest trabajo = new OneTimeWorkRequest.Builder(RecordatorioWorker.class)
                .setInitialDelay(espera, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(contexto)
                .enqueueUniqueWork(TRABAJO_RECORDATORIO, siYaHay, trabajo);
    }

    public static void apagarRecordatorio(Context contexto) {
        WorkManager.getInstance(contexto).cancelUniqueWork(TRABAJO_RECORDATORIO);
        NotificationManagerCompat.from(contexto).cancel(ID_RECORDATORIO);
    }

    /** El próximo domingo a las 7 p. m. que todavía no haya pasado (con un minuto de margen). */
    static LocalDateTime proximoAviso(LocalDateTime ahora) {
        LocalDateTime candidato = ahora.with(TemporalAdjusters.nextOrSame(DIA)).with(HORA);
        return candidato.isAfter(ahora.plusMinutes(1)) ? candidato : candidato.plusWeeks(1);
    }

    static void mostrarRecordatorio(Context contexto) {
        Intent abrir = new Intent(contexto, MainActivity.class)
                .putExtra(MainActivity.EXTRA_ABRIR, MainActivity.ABRIR_REGISTRAR);
        mostrar(contexto, ID_RECORDATORIO, new NotificationCompat.Builder(contexto, CANAL_RECORDATORIO)
                .setContentTitle(contexto.getString(R.string.aviso_recordatorio_titulo)), R.drawable.ic_lapiz,
                R.color.acento, abrir);
    }

    // ===== Materia en rojo (RF-32) =====

    static void mostrarRiesgo(Context contexto, Materia materia, double llevas) {
        Intent abrir = new Intent(contexto, MainActivity.class)
                .putExtra(MainActivity.EXTRA_ABRIR, MainActivity.ABRIR_MATERIA)
                .putExtra(MainActivity.EXTRA_MATERIA, Long.parseLong(materia.id));
        mostrar(contexto, 1000 + Math.floorMod(materia.id.hashCode(), 100000),
                new NotificationCompat.Builder(contexto, CANAL_RIESGO)
                        .setContentTitle(contexto.getString(R.string.aviso_riesgo_titulo, materia.nombre))
                        .setContentText(contexto.getString(R.string.aviso_riesgo_detalle,
                                Formato.nota(llevas), Formato.nota(materia.notaParaPasar))),
                R.drawable.ic_alerta, R.color.riesgo_vivo, abrir);
    }

    private static void mostrar(Context contexto, int id, NotificationCompat.Builder aviso,
                                @DrawableRes int icono, int color, Intent abrir) {
        if (!puedeNotificar(contexto)) {
            return;
        }
        // La app arranca de cero en la pantalla pedida (ver MainActivity.abrirDesdeAviso)
        abrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent alTocar = PendingIntent.getActivity(contexto, id, abrir,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        aviso.setSmallIcon(icono)
                .setColor(ContextCompat.getColor(contexto, color))
                .setContentIntent(alTocar)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(contexto).notify(id, aviso.build());
        } catch (SecurityException sinPermiso) {
            // Quitaron el permiso justo ahora: no hay a quién avisar
        }
    }
}
