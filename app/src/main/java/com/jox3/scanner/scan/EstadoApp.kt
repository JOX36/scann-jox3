package com.jox3.scanner.scan

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** `true` mientras la pantalla de la app está a la vista (la actualiza MainActivity en onStart/onStop). */
object EstadoApp {
    @Volatile var visible: Boolean = false

    /** `true` cuando la animación de bienvenida ya se mostró en este proceso. */
    @Volatile var inicioMostrado: Boolean = false
}

/**
 * Almacén de ViewModels de ALCANCE DE PROCESO. Los ViewModels del escaneo y de la
 * verificación viven aquí y no en la Activity: antes, al deslizar la app fuera de
 * "recientes" o cerrar la pantalla, la Activity se destruía, su ViewModel se
 * limpiaba y el escaneo se cancelaba aunque el servicio en primer plano
 * mantuviera vivo el proceso ("de un momento a otro se cerró el escaneo").
 * Ahora el escaneo sigue mientras viva el proceso, con o sin pantalla.
 */
object AlmacenViewModelsProceso : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}
