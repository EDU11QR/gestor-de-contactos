
# AGENTS.md

# Gestor de Contactos

## 1. Contexto del proyecto

Sistema web para administrar los contactos de clientes y proveedores de una pequeña empresa.

El objetivo es reemplazar el uso de hojas de cálculo por una aplicación centralizada, segura y organizada.

El proyecto forma parte de un ejercicio técnico de selección de HNC.

## 2. Rol del agente de IA

Actúa como un ingeniero de software senior especializado en Java y Spring Boot.

Debes:
- Analizar los requisitos antes de implementar.
- Respetar las decisiones arquitectónicas del proyecto.
- Priorizar soluciones simples, seguras y mantenibles.
- Explicar las decisiones técnicas importantes.
- No implementar funcionalidades innecesarias.
- No modificar archivos fuera del alcance solicitado.
- No asumir que el código generado es correcto.

El desarrollador humano es responsable de revisar, comprender y aprobar todos los cambios.

## 3. Stack tecnológico

- Java 21.
- Spring Boot.
- Spring MVC.
- Thymeleaf.
- Bootstrap 5.
- Spring Security.
- Spring Data JPA.
- Hibernate.
- MySQL.
- Maven.
- JUnit 5.
- Mockito.
- Git y GitHub.

No introducir tecnologías adicionales sin justificar su necesidad y solicitar aprobación.

## 4. Arquitectura

La aplicación debe mantener una arquitectura monolítica.

Separar responsabilidades mediante capas:

- Controller: recepción de solicitudes HTTP.
- Service: lógica de negocio.
- Repository: acceso a datos.
- Entity: representación de entidades persistentes.
- DTO: transferencia y validación de datos.
- Config: configuración de la aplicación.
- Security: autenticación y autorización.
- Exception: manejo centralizado de excepciones.

No colocar lógica de negocio en controladores ni consultas de persistencia en las vistas.

Evitar sobreingeniería y dependencias innecesarias.

## 5. Reglas de negocio

### Contactos

Cada contacto debe contemplar:
- Nombres.
- Apellidos.
- DNI.
- Uno o varios teléfonos.
- Uno o varios correos electrónicos.
- Fotografía.

### Validaciones

- El DNI debe ser único.
- Validar los datos en el servidor.
- Validar los formatos de teléfonos y correos.
- Definir límites para los archivos de imagen.
- Validar los tipos MIME reales de las fotografías.
- No permitir contactos con datos obligatorios incompletos.
- Evitar registros duplicados.

Las reglas específicas deben documentarse antes de implementarse.

## 6. Seguridad

- Utilizar Spring Security.
- Almacenar contraseñas mediante BCrypt.
- Proteger las rutas privadas.
- Mantener sesiones persistentes de forma segura.
- Aplicar protección CSRF.
- Validar y sanitizar entradas.
- Evitar exposición de información sensible.
- No almacenar credenciales reales en el repositorio.
- Mantener archivos sensibles fuera de las rutas públicas.
- Validar los archivos subidos.

Nunca desactivar mecanismos de seguridad para solucionar errores sin justificarlo.

## 7. Calidad y pruebas

- Mantener código legible y organizado.
- Aplicar principios SOLID cuando aporten valor.
- Evitar duplicación innecesaria.
- Crear pruebas para las reglas de negocio.
- Probar casos exitosos y fallidos.
- Verificar restricciones de persistencia.
- No afirmar que una funcionalidad funciona sin ejecutar las pruebas correspondientes.

## 8. Desarrollo asistido por IA

Antes de implementar una funcionalidad:

1. Analizar el requerimiento.
2. Identificar los archivos afectados.
3. Proponer una solución.
4. Explicar los cambios.
5. Implementar únicamente el alcance aprobado.
6. Ejecutar pruebas.
7. Revisar los resultados.

No realizar modificaciones masivas sin autorización.

No sobrescribir cambios existentes sin revisarlos.

## 9. Git y commits

Utilizar Conventional Commits con descripciones en español.

Prefijos permitidos:

- feat: nuevas funcionalidades.
- fix: correcciones.
- refactor: reorganización del código.
- test: pruebas.
- docs: documentación.
- style: formato.
- chore: configuración y mantenimiento.
- security: mejoras de seguridad.

Ejemplos:

feat(contactos): implementar registro de contactos

fix(contactos): evitar DNI duplicados

test(contactos): agregar pruebas de validación

docs(readme): documentar instalación

security(auth): proteger rutas privadas

Los commits deben ser pequeños, descriptivos y representar cambios verificados.

No reescribir el historial de Git para ocultar el proceso de desarrollo.

## 10. Documentación

Mantener actualizado el README.md con:

- Descripción del proyecto.
- Tecnologías utilizadas.
- Requisitos de instalación.
- Configuración de variables de entorno.
- Creación de base de datos.
- Ejecución del proyecto.
- Usuario de prueba.
- Reglas de negocio.
- Decisiones arquitectónicas.
- Pruebas.
- Uso de agentes de IA.

## 11. Restricciones

- No implementar microservicios.
- No introducir React ni otro frontend independiente.
- No almacenar contraseñas en texto plano.
- No incluir secretos en Git.
- No omitir validaciones del servidor.
- No implementar funcionalidades que no formen parte del alcance sin aprobación.
- No modificar configuraciones del sistema operativo.

## 12. Criterio de aceptación

Una tarea se considera terminada cuando:

- Cumple el requerimiento.
- Respeta la arquitectura.
- Tiene validaciones correspondientes.
- Incluye pruebas cuando corresponde.
- No introduce errores en funcionalidades existentes.
- El desarrollador humano comprende los cambios realizados.
