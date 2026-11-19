package com.dosnueve.app.avisos;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.dosnueve.app.data.Cuenta;

/**
 * Sale los domingos a las 7 p. m. (RF-31): muestra "¿Ya registraste tus notas?" y deja
 * programado el del domingo siguiente. Si se apaga en Perfil o se cierra sesión, se cancela.
 */
public class RecordatorioWorker extends Worker {

    public RecordatorioWorker(@NonNull Context contexto, @NonNull WorkerParameters parametros) {
        super(contexto, parametros);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context contexto = getApplicationContext();
        if (Cuenta.haySesion()) {
            Avisos.mostrarRecordatorio(contexto);
        }
        // Se encadena detrás de este: corre cuando este termine, el próximo domingo
        Avisos.encenderRecordatorio(contexto, ExistingWorkPolicy.APPEND_OR_REPLACE);
        return Result.success();
    }
}
