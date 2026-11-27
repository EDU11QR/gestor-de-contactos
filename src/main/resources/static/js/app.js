const LIMITE_ELEMENTOS = 20;

document.addEventListener('DOMContentLoaded', function () {
    const formulario = document.getElementById('formulario-edicion');
    if (!formulario) {
        return;
    }

    const configuraciones = [
        {
            contenedor: 'telefonos',
            plantilla: 'plantilla-telefono',
            agregar: 'agregar-telefono',
            fila: '.telefono-fila',
            entrada: '.campo-telefono',
            quitar: 'quitar-telefono',
            prefijo: 'telefonos',
            idPrefijo: 'telefono',
            campo: 'numero'
        },
        {
            contenedor: 'correos',
            plantilla: 'plantilla-correo',
            agregar: 'agregar-correo',
            fila: '.correo-fila',
            entrada: '.campo-correo',
            quitar: 'quitar-correo',
            prefijo: 'correosElectronicos',
            idPrefijo: 'correo',
            campo: 'direccion'
        }
    ];

    function reindexar(configuracion) {
        const filas = document.querySelectorAll(configuracion.fila);
        filas.forEach(function (fila, indice) {
            const entrada = fila.querySelector(configuracion.entrada);
            if (!entrada) {
                return;
            }
            entrada.name = configuracion.prefijo + '[' + indice + '].' + configuracion.campo;
            entrada.id = configuracion.idPrefijo + indice;
            const etiqueta = fila.querySelector('label');
            if (etiqueta) {
                etiqueta.setAttribute('for', entrada.id);
            }
            const boton = fila.querySelector('.' + configuracion.quitar);
            if (boton) {
                boton.disabled = filas.length === 1;
            }
        });
    }

    function reindexarTodo() {
        configuraciones.forEach(reindexar);
    }

    configuraciones.forEach(function (configuracion) {
        const contenedor = document.getElementById(configuracion.contenedor);
        const plantilla = document.getElementById(configuracion.plantilla);
        const botonAgregar = document.getElementById(configuracion.agregar);
        if (!contenedor || !plantilla || !botonAgregar) {
            return;
        }

        botonAgregar.addEventListener('click', function () {
            if (document.querySelectorAll(configuracion.fila).length >= LIMITE_ELEMENTOS) {
                return;
            }
            contenedor.appendChild(plantilla.content.cloneNode(true));
            reindexarTodo();
        });

        contenedor.addEventListener('click', function (evento) {
            if (evento.target.classList.contains(configuracion.quitar)) {
                const fila = evento.target.closest(configuracion.fila);
                if (fila) {
                    fila.remove();
                    reindexarTodo();
                }
            }
        });
    });

    formulario.addEventListener('submit', reindexarTodo);
    reindexarTodo();
});
