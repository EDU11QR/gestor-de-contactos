import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const codigoApp = readFileSync(
        new URL('../../main/resources/static/js/app.js', import.meta.url),
        'utf8');

class ListaClases {
    constructor(clases = '') {
        this.valores = new Set(clases.split(/\s+/).filter(Boolean));
    }

    contains(nombre) {
        return this.valores.has(nombre);
    }

    add(nombre) {
        this.valores.add(nombre);
    }

    remove(nombre) {
        this.valores.delete(nombre);
    }
}

class Fragmento {
    constructor() {
        this.hijos = [];
    }

    appendChild(nodo) {
        this.hijos.push(nodo);
        return nodo;
    }

    cloneNode(profundo) {
        const copia = new Fragmento();
        for (const hijo of this.hijos) {
            copia.appendChild(hijo.cloneNode(profundo));
        }
        return copia;
    }
}

class Elemento {
    constructor(etiqueta, clases = '', id = '') {
        this.etiqueta = etiqueta.toLowerCase();
        this.id = id;
        this.name = null;
        this.value = '';
        this.disabled = false;
        this.claseLista = new ListaClases(clases);
        this.atributos = new Map();
        this.hijos = [];
        this.padre = null;
        this.oyentes = new Map();
        this.content = null;
    }

    get classList() {
        return this.claseLista;
    }

    setAttribute(nombre, valor) {
        this.atributos.set(nombre, valor);
        if (nombre === 'for') {
            this.atributos.set('for', valor);
        }
    }

    getAttribute(nombre) {
        return this.atributos.get(nombre) ?? null;
    }

    appendChild(nodo) {
        const hijos = nodo instanceof Fragmento ? nodo.hijos : [nodo];
        for (const hijo of hijos) {
            hijo.padre = this;
            this.hijos.push(hijo);
        }
        return nodo;
    }

    cloneNode(profundo) {
        const copia = new Elemento(this.etiqueta, '', this.id);
        copia.name = this.name;
        copia.value = this.value;
        copia.disabled = this.disabled;
        copia.claseLista = new ListaClases([...this.claseLista.valores].join(' '));
        for (const [clave, valor] of this.atributos) {
            copia.atributos.set(clave, valor);
        }
        if (profundo) {
            for (const hijo of this.hijos) {
                copia.appendChild(hijo.cloneNode(true));
            }
        }
        return copia;
    }

    remove() {
        if (!this.padre) {
            return;
        }
        const indice = this.padre.hijos.indexOf(this);
        if (indice >= 0) {
            this.padre.hijos.splice(indice, 1);
        }
        this.padre = null;
    }

    coincide(selector) {
        if (selector.startsWith('.')) {
            return this.claseLista.contains(selector.slice(1));
        }
        return this.etiqueta === selector.toLowerCase();
    }

    querySelector(selector) {
        for (const hijo of this.hijos) {
            if (hijo.coincide(selector)) {
                return hijo;
            }
            const encontrado = hijo.querySelector(selector);
            if (encontrado) {
                return encontrado;
            }
        }
        return null;
    }

    closest(selector) {
        let actual = this;
        while (actual) {
            if (actual.coincide(selector)) {
                return actual;
            }
            actual = actual.padre;
        }
        return null;
    }

    addEventListener(tipo, listener) {
        if (!this.oyentes.has(tipo)) {
            this.oyentes.set(tipo, []);
        }
        this.oyentes.get(tipo).push(listener);
    }

    dispatch(tipo, evento) {
        let actual = this;
        while (actual) {
            for (const listener of actual.oyentes.get(tipo) ?? []) {
                listener(evento);
            }
            actual = actual.padre;
        }
    }

    click() {
        if (this.disabled) {
            return;
        }
        this.dispatch('click', { target: this });
    }
}

class Documento {
    constructor() {
        this.elementos = new Map();
        this.raices = [];
        this.oyentes = new Map();
    }

    addEventListener(tipo, listener) {
        if (!this.oyentes.has(tipo)) {
            this.oyentes.set(tipo, []);
        }
        this.oyentes.get(tipo).push(listener);
    }

    getElementById(id) {
        return this.elementos.get(id) ?? null;
    }

    registrar(elemento, esRaiz = false) {
        this.elementos.set(elemento.id, elemento);
        if (esRaiz) {
            this.raices.push(elemento);
        }
        return elemento;
    }

    querySelectorAll(selector) {
        const resultados = [];
        const visitar = (nodo) => {
            for (const hijo of nodo.hijos) {
                if (hijo.coincide(selector)) {
                    resultados.push(hijo);
                }
                visitar(hijo);
            }
        };
        for (const raiz of this.raices) {
            visitar(raiz);
        }
        return resultados;
    }

    listo() {
        for (const listener of this.oyentes.get('DOMContentLoaded') ?? []) {
            listener();
        }
    }
}

function crearFila(tipo, indice, numero) {
    const esTelefono = tipo === 'telefono';
    const claseFila = esTelefono ? 'telefono-fila' : 'correo-fila';
    const claseEntrada = esTelefono ? 'campo-telefono' : 'campo-correo';
    const claseQuitar = esTelefono ? 'quitar-telefono' : 'quitar-correo';
    const prefijo = esTelefono ? 'telefonos' : 'correosElectronicos';
    const idPrefijo = esTelefono ? 'telefono' : 'correo';
    const campo = esTelefono ? 'numero' : 'direccion';

    const fila = new Elemento('div', `row g-2 mb-2 ${claseFila}`);
    const etiqueta = new Elemento('label', 'form-label');
    etiqueta.setAttribute('for', `${idPrefijo}${indice}`);
    const entrada = new Elemento('input', `form-control ${claseEntrada}`);
    entrada.name = `${prefijo}[${indice}].${campo}`;
    entrada.id = `${idPrefijo}${indice}`;
    entrada.value = numero;
    const boton = new Elemento('button', `btn btn-outline-danger ${claseQuitar}`);
    fila.appendChild(etiqueta);
    fila.appendChild(entrada);
    fila.appendChild(boton);
    return fila;
}

function crearPlantilla(id, tipo) {
    const plantilla = new Elemento('template', '', id);
    plantilla.content = new Fragmento();
    plantilla.content.appendChild(crearFila(tipo, 0, ''));
    return plantilla;
}

function crearEscenario() {
    const documento = new Documento();
    const formulario = documento.registrar(new Elemento('form', '', 'formulario-edicion'), true);
    const telefonos = documento.registrar(new Elemento('div', '', 'telefonos'));
    const correos = documento.registrar(new Elemento('div', '', 'correos'));
    documento.registrar(new Elemento('button', '', 'agregar-telefono'));
    documento.registrar(new Elemento('button', '', 'agregar-correo'));
    documento.registrar(crearPlantilla('plantilla-telefono', 'telefono'));
    documento.registrar(crearPlantilla('plantilla-correo', 'correo'));
    formulario.appendChild(telefonos);
    formulario.appendChild(correos);

    telefonos.appendChild(crearFila('telefono', 0, '+51987654321'));
    correos.appendChild(crearFila('correo', 0, 'ana@ejemplo.com'));

    vm.runInNewContext(codigoApp, { document: documento });
    documento.listo();
    return documento;
}

function estadoFila(fila, prefijo, idPrefijo, campo) {
    const entrada = fila.querySelector('input');
    const etiqueta = fila.querySelector('label');
    return {
        nombre: entrada.name,
        id: entrada.id,
        etiquetaPara: etiqueta.getAttribute('for'),
        deshabilitado: fila.querySelector('button').disabled,
        prefijo,
        idPrefijo,
        campo
    };
}

test('las filas iniciales conservan los IDs renderizados por Thymeleaf', () => {
    const documento = crearEscenario();

    assert.deepEqual(estadoFila(documento.querySelectorAll('.telefono-fila')[0],
            'telefonos', 'telefono', 'numero'), {
        nombre: 'telefonos[0].numero',
        id: 'telefono0',
        etiquetaPara: 'telefono0',
        deshabilitado: true,
        prefijo: 'telefonos',
        idPrefijo: 'telefono',
        campo: 'numero'
    });
    assert.deepEqual(estadoFila(documento.querySelectorAll('.correo-fila')[0],
            'correosElectronicos', 'correo', 'direccion'), {
        nombre: 'correosElectronicos[0].direccion',
        id: 'correo0',
        etiquetaPara: 'correo0',
        deshabilitado: true,
        prefijo: 'correosElectronicos',
        idPrefijo: 'correo',
        campo: 'direccion'
    });
});

test('agregar teléfono reindexa nombre, id y etiqueta', () => {
    const documento = crearEscenario();
    documento.getElementById('agregar-telefono').click();

    const filas = documento.querySelectorAll('.telefono-fila');
    assert.equal(filas.length, 2);
    assert.deepEqual(estadoFila(filas[0], 'telefonos', 'telefono', 'numero'), {
        nombre: 'telefonos[0].numero',
        id: 'telefono0',
        etiquetaPara: 'telefono0',
        deshabilitado: false,
        prefijo: 'telefonos',
        idPrefijo: 'telefono',
        campo: 'numero'
    });
    assert.deepEqual(estadoFila(filas[1], 'telefonos', 'telefono', 'numero'), {
        nombre: 'telefonos[1].numero',
        id: 'telefono1',
        etiquetaPara: 'telefono1',
        deshabilitado: false,
        prefijo: 'telefonos',
        idPrefijo: 'telefono',
        campo: 'numero'
    });
});

test('eliminar teléfono reindexa las filas restantes', () => {
    const documento = crearEscenario();
    documento.getElementById('agregar-telefono').click();

    const botones = documento.querySelectorAll('.quitar-telefono');
    botones[0].click();

    const filas = documento.querySelectorAll('.telefono-fila');
    assert.equal(filas.length, 1);
    assert.deepEqual(estadoFila(filas[0], 'telefonos', 'telefono', 'numero'), {
        nombre: 'telefonos[0].numero',
        id: 'telefono0',
        etiquetaPara: 'telefono0',
        deshabilitado: true,
        prefijo: 'telefonos',
        idPrefijo: 'telefono',
        campo: 'numero'
    });
});

test('no se puede eliminar el último teléfono', () => {
    const documento = crearEscenario();
    documento.querySelectorAll('.quitar-telefono')[0].click();
    assert.equal(documento.querySelectorAll('.telefono-fila').length, 1);
});

test('agregar correo reindexa nombre, id y etiqueta', () => {
    const documento = crearEscenario();
    documento.getElementById('agregar-correo').click();

    const filas = documento.querySelectorAll('.correo-fila');
    assert.equal(filas.length, 2);
    assert.deepEqual(estadoFila(filas[0], 'correosElectronicos', 'correo', 'direccion'), {
        nombre: 'correosElectronicos[0].direccion',
        id: 'correo0',
        etiquetaPara: 'correo0',
        deshabilitado: false,
        prefijo: 'correosElectronicos',
        idPrefijo: 'correo',
        campo: 'direccion'
    });
    assert.deepEqual(estadoFila(filas[1], 'correosElectronicos', 'correo', 'direccion'), {
        nombre: 'correosElectronicos[1].direccion',
        id: 'correo1',
        etiquetaPara: 'correo1',
        deshabilitado: false,
        prefijo: 'correosElectronicos',
        idPrefijo: 'correo',
        campo: 'direccion'
    });
});

test('eliminar correo reindexa las filas restantes', () => {
    const documento = crearEscenario();
    documento.getElementById('agregar-correo').click();

    const botones = documento.querySelectorAll('.quitar-correo');
    botones[0].click();

    const filas = documento.querySelectorAll('.correo-fila');
    assert.equal(filas.length, 1);
    assert.deepEqual(estadoFila(filas[0], 'correosElectronicos', 'correo', 'direccion'), {
        nombre: 'correosElectronicos[0].direccion',
        id: 'correo0',
        etiquetaPara: 'correo0',
        deshabilitado: true,
        prefijo: 'correosElectronicos',
        idPrefijo: 'correo',
        campo: 'direccion'
    });
});

test('no se puede eliminar el último correo', () => {
    const documento = crearEscenario();
    documento.querySelectorAll('.quitar-correo')[0].click();
    assert.equal(documento.querySelectorAll('.correo-fila').length, 1);
});

test('no se pueden agregar más de 20 teléfonos ni correos', () => {
    const documento = crearEscenario();
    const botonTelefono = documento.getElementById('agregar-telefono');
    const botonCorreo = documento.getElementById('agregar-correo');

    for (let indice = 0; indice < 25; indice += 1) {
        botonTelefono.click();
        botonCorreo.click();
    }

    assert.equal(documento.querySelectorAll('.telefono-fila').length, 20);
    assert.equal(documento.querySelectorAll('.correo-fila').length, 20);
});
