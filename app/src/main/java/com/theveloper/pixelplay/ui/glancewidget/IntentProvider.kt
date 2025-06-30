package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import android.content.Intent
import com.theveloper.pixelplay.MainActivity

object IntentProvider {
    fun mainActivityIntent(context: Context): Intent {
        val intent = Intent(context, MainActivity::class.java)
        // ACTION_MAIN y CATEGORY_LAUNCHER son típicos para iniciar la actividad principal.
        // Si la app ya está en ejecución, estos flags ayudan a traerla al frente.
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        // FLAG_ACTIVITY_NEW_TASK es necesario si se inicia desde un context que no es de Activity (como un AppWidgetProvider).
        // FLAG_ACTIVITY_REORDER_TO_FRONT traerá la tarea existente al frente si ya está ejecutándose,
        // en lugar de lanzar una nueva instancia encima si el launchMode lo permite.
        // Si MainActivity tiene launchMode="singleTop", onNewIntent será llamado si ya está en la cima.
        // Si tiene launchMode="singleTask" o "singleInstance", se comportará según esos modos.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        return intent
    }
}
