# Gestor de Contactos

Sistema web para administrar los contactos de clientes y proveedores de una pequeña empresa. Reemplaza el uso de hojas de cálculo por una aplicación centralizada, segura y organizada. Forma parte de un ejercicio técnico de selección de HNC.

## Tecnologías

- Java 21.
- Spring Boot y Spring MVC.
- Thymeleaf y Bootstrap 5.
- Spring Security.
- Spring Data JPA e Hibernate.
- Spring Session JDBC.
- MySQL.
- Maven.
- JUnit 5 y Mockito.
- Git y GitHub.

## Requisitos

- JDK 21.
- Maven 3.9 o superior.
- MySQL 8.4.9 como versión objetivo de despliegue; véase la nota de validación en la sección `Pruebas`.
- Docker no es necesario: no se usa Testcontainers.

## Variables de entorno

Copiar `.env.example` a `.env`, completar los valores locales y exportar las variables en la shell antes de ejecutar la aplicación. `.env` está ignorado por Git y no debe versionarse. No se almacenan credenciales reales en el repositorio.

```bash
set -a; . ./.env; set +a
```

Las variables disponibles son:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`
- `SPRING_SESSION_STORE_TYPE`
- `SPRING_SESSION_JDBC_TABLE_NAME`
- `SPRING_SESSION_JDBC_INITIALIZE_SCHEMA`
- `SPRING_SESSION_TIMEOUT`
- `SERVER_SERVLET_SESSION_COOKIE_NAME`
- `SERVER_SERVLET_SESSION_COOKIE_MAX_AGE`
- `SERVER_SERVLET_SESSION_COOKIE_HTTP_ONLY`
- `SERVER_SERVLET_SESSION_COOKIE_SECURE`
- `SERVER_SERVLET_SESSION_COOKIE_SAME_SITE`
- `SERVER_FORWARD_HEADERS_STRATEGY`
- `APP_PHOTOS_DIR`
- `APP_PROVISIONING_EMAIL`
- `APP_PROVISIONING_PASSWORD`
- `APP_PROVISIONING_NOMBRE`
- `APP_REVOCAR_EMAIL`

`APP_PHOTOS_DIR` es obligatorio para iniciar la aplicación y debe apuntar a un directorio privado del servidor. No existe valor por defecto público y su contenido no se sirve desde rutas estáticas.

Los límites HTTP de fotografías están fijados en `application.yml`: `spring.servlet.multipart.max-file-size=2MB` y `spring.servlet.multipart.max-request-size=3MB`. El límite efectivo de la fotografía es de 2 MB por archivo y coincide con la validación de `FotoService`.

## Creación de base de datos

Crear un esquema MySQL y aplicar manualmente los scripts DDL previstos, en este orden:

1. `db/ddl/V001__dominio_inicial.sql`
2. `db/ddl/V002__sesiones_jdbc.sql`
3. `db/ddl/V003__confirmaciones_eliminacion.sql`
4. `db/ddl/V004__usuarios_activo.sql`

Los scripts ya están incluidos en el repositorio y deben aplicarse manualmente porque no se usa una herramienta de migraciones. La validación del esquema usa `spring.jpa.hibernate.ddl-auto=validate`.

## Ejecución

Copiar `.env.example` a `.env`, completar los valores locales, exportar las variables y ejecutar:

```bash
set -a; . ./.env; set +a
mvn spring-boot:run
```

La aplicación queda disponible en `http://localhost:8080/`. La base de datos y las tablas de sesión deben existir previamente porque `ddl-auto=validate`.

## Usuario de prueba

No hay credenciales reales en el repositorio. Para crear un usuario local, definir `APP_PROVISIONING_EMAIL`, `APP_PROVISIONING_PASSWORD` y `APP_PROVISIONING_NOMBRE` en `.env` antes de iniciar la aplicación. El aprovisionamiento normaliza el correo y guarda la contraseña con BCrypt. La contraseña de aprovisionamiento debe tener al menos 12 caracteres, no contener espacios y no ser igual al correo ni al nombre. Si el usuario ya existe, no modifica su contraseña ni su nombre; no hay rotación automática de credenciales en esta fase.

Si las variables están vacías, la aplicación inicia sin crear usuarios. El formulario de acceso está en `http://localhost:8080/login`. El cierre de sesión se realiza mediante `POST /logout` con token CSRF.

Para revocar un usuario, definir `APP_REVOCAR_EMAIL` antes de iniciar la aplicación. El runner de revocación se ejecuta después del aprovisionamiento, normaliza el correo, marca el usuario como inactivo y elimina sus sesiones de `SPRING_SESSION` por `PRINCIPAL_NAME` en la misma transacción. Es idempotente: si el usuario no existe o ya está inactivo, no cambia el estado, pero vuelve a borrar las sesiones pendientes. No existe endpoint HTTP de revocación. Después de un inicio exitoso, retirar `APP_REVOCAR_EMAIL` del entorno para no repetir la revocación en reinicios posteriores. El correo debe tener como máximo 100 caracteres porque `SPRING_SESSION.PRINCIPAL_NAME` usa `VARCHAR(100)`. Durante `POST /login`, un filtro toma el mismo bloqueo de fila del usuario y lo mantiene hasta que Spring Session persiste la sesión; así una revocación concurrente no deja una sesión utilizable. Si la cadena termina con rollback y no existía sesión previa, el filtro elimina la sesión creada en ese intento. Si existía una sesión previa, el filtro restaura su identificador, atributos y metadatos porque Spring Session rota el identificador durante el login; `CapturadorCookieSesion` suprime la cookie rotada cuando hay rollback y publica las cabeceras solo después de confirmar o revertir la transacción. `RollbackFiltroLoginMysqlTests` y `CapturadorCookieSesionTests` comprueban que la cookie previa sigue siendo válida y que el identificador rotado no queda persistido.

## Reglas de negocio

- Los contactos pueden ser clientes, proveedores o ambos.
- Cada contacto tiene nombres, apellidos, DNI, uno o varios teléfonos, uno o varios correos y fotografía. La fotografía es obligatoria en el alta; en la edición puede dejarse vacía para conservar la existente.
- El DNI es peruano de 8 dígitos, único y sin checksum; la unicidad se comprueba antes de guardar y una carrera contra el `INSERT` se muestra como error de campo DNI.
- El teléfono se valida con `^(?:\+51|51)?9\d{8}$` y se normaliza a `+51XXXXXXXXX`; el correo se valida con `@Email` y se normaliza a minúsculas.
- Cada contacto admite como máximo 20 teléfonos y 20 correos electrónicos, tanto en alta como en edición; el límite se valida en servidor y se refleja en la interfaz.
- Las fotografías aceptan JPG, JPEG y PNG, con límite de 2 MB por archivo y 2000 × 2000 píxeles, validación preventiva de dimensiones, firma real, rechazo estructural de APNG y almacenamiento privado. La solicitud completa admite hasta 3 MB; un archivo mayor a 2 MB se devuelve al formulario con un error controlado.
- El alta usa PRG: `POST /contactos` redirige a `GET /contactos/{id}`; el detalle no expone entidades JPA ni la ruta del archivo.
- Las rutas privadas requieren autenticación, BCrypt, CSRF y sesiones persistentes con Spring Session JDBC.
- La cookie de sesión es `Secure` por defecto y `server.forward-headers-strategy=framework` reconoce proxy TLS; las pruebas HTTP locales pueden overridear `Secure=false` sin cambiar el default de producción.
- `GET /` es pública y no contiene información privada; el formulario de logout se muestra solo a usuarios autenticados.
- La revocación de usuarios marca `usuarios.activo=0`, impide nuevos inicios de sesión con un mensaje genérico e invalida todas sus sesiones por `PRINCIPAL_NAME` en la misma transacción. Un `POST /login` y una revocación concurrentes se coordinan mediante el bloqueo de la fila del usuario; la respuesta de autenticación es genérica para correo ausente, demasiado largo o usuario inactivo.
- El listado protegido `GET /contactos` busca de forma parcial e insensible a mayúsculas en nombres, apellidos y DNI; recorta espacios, escapa comodines y limita el término a 100 caracteres.
- Los filtros por clasificación respetan que un contacto `AMBOS` aparece en los listados de `CLIENTE` y `PROVEEDOR`, mientras que el filtro `AMBOS` muestra solo esos contactos.
- El listado pagina con tamaño por defecto 10 y valores permitidos 10, 20 y 50; una página negativa se normaliza a 0 y un tamaño inválido a 10. El orden es fijo por apellidos, nombres e id.
- El listado usa `ContactoListadoDto` y no carga teléfonos, correos ni fotografías.
- Un detalle inexistente responde HTTP 404 con una vista controlada y sin detalles internos.
- La edición usa `GET /contactos/{id}/editar` y `POST /contactos/{id}/editar`; ambas rutas son privadas y el POST exige token CSRF.
- `GET /contactos/{id}/editar` precarga todos los teléfonos, correos y datos del contacto; la vista permite agregar y quitar filas sin perder las no modificadas, mantiene al menos una de cada tipo y reindexa los nombres antes de enviar; un id inexistente responde 404 controlado.
- El DNI se puede conservar o cambiar, pero no puede repetirse entre contactos; la validación excluye el propio contacto y traduce la violación de unicidad a error de campo.
- En la edición se validan todos los campos con las mismas reglas del alta; teléfonos y correos se normalizan y se reemplazan en bloque.
- Si no se adjunta nueva fotografía se conserva la anterior. Si se adjunta, se valida con las mismas reglas del alta, se escribe con compensación y la fotografía anterior se elimina solo después de confirmar la transacción y revalidar en una transacción nueva que no esté referenciada.
- Un error multipart, incluido el exceso de 2 MB, distingue alta de edición: en edición vuelve a `contactos/editar` con el id y los datos precargados.
- La edición bloquea la fila del contacto mediante `SELECT … FOR UPDATE` para reducir el riesgo de actualizaciones concurrentes.
- La eliminación usa `GET /contactos/{id}/eliminar` para mostrar la confirmación y `POST /contactos/{id}/eliminar` para ejecutarla; ambas rutas son privadas y el POST exige token CSRF.
- `GET /contactos/{id}/eliminar` genera un token aleatorio de 32 bytes codificado en Base64URL de 43 caracteres. Se almacena únicamente su hash SHA-256 en `confirmaciones_eliminacion`, asociado al identificador de la sesión y al contacto, con caducidad de 10 minutos. Si ya existía una confirmación para la sesión, se reemplaza.
- `POST /contactos/{id}/eliminar` valida el formato del token, calcula su hash y lo consume con un `UPDATE` condicional y atómico que exige sesión, contacto, token no consumido y fecha de expiración futura. El consumo usa `REQUIRES_NEW`, por lo que queda confirmado aunque después falle la eliminación del contacto.
- La eliminación del contacto es transaccional: bloquea la fila con `SELECT … FOR UPDATE`, elimina el contacto y programa el borrado de su fotografía solo después del commit, revalidando en una transacción nueva que el archivo no esté referenciado.

## Decisiones arquitectónicas

La arquitectura completa, el modelo de datos, la trazabilidad de hallazgos y las decisiones pendientes están documentados en [`docs/arquitectura.md`](docs/arquitectura.md).

La Etapa 1 añade `ContactoController`, `ContactoService`, `FotoService`, `ContactoRepository`, DTOs de entrada/salida, `FotoProperties`, un handler mínimo de integridad y las vistas `contactos/nuevo` y `contactos/detalle`. `ContactoService` es el único punto transaccional del flujo; `FotoService` usa propagación `MANDATORY`, verifica la transacción activa y registra la compensación antes de escribir. No se añadieron dependencias.

La Etapa 2 añade el listado protegido. `ContactoBusquedaForm` transporta filtros y paginación; `ContactoService` normaliza y valida la entrada, aplica paginación y orden fijos y convierte a `ContactoListadoDto`; `ContactoRepository` ejecuta una única consulta JPQL con agrupación explícita de búsqueda y clasificación; `ContactoController` solo enlaza el formulario con el servicio. La vista `contactos/listado` muestra únicamente las columnas acordadas y conserva filtros en la paginación. Se añade una vista `contactos/no-encontrado` para 404 controlado. No se modificaron autenticación, CSRF, entidades, DDL ni dependencias.

La Etapa 3 añade la edición protegida. `ContactoEdicionForm` reutiliza las validaciones del alta pero permite dejar la fotografía vacía para conservar la anterior; `ContactoService` carga la entidad con bloqueo pesimista, valida la unicidad del DNI excluyendo el propio id, normaliza teléfonos y correos, reemplaza las colecciones hijas y coordina la fotografía. `ContactoRepository` aporta las consultas de unicidad y bloqueo; `FotoService` añade el borrado de la fotografía anterior en `afterCommit`, manteniendo la compensación de escritura existente. `FotoReferenciaService` revalida las referencias con `REQUIRES_NEW` antes de borrar. `GlobalExceptionHandler` distingue el exceso multipart en alta y edición. `ContactoController` solo enlaza el formulario con el servicio y la vista `contactos/editar` precarga y reindexa todas las filas. No se añadieron entidades, DDL, migraciones ni dependencias.

La Fase 4 añade la eliminación protegida. `ConfirmacionEliminacionService` genera el token, almacena solo su hash SHA-256 y lo consume con `REQUIRES_NEW` mediante `ConfirmacionEliminacionRepository`, que usa JDBC y un `UPDATE` condicional para garantizar un solo consumo. `V003__confirmaciones_eliminacion.sql` crea la tabla de confirmaciones con primary key por sesión, índices y restricción de expiración. `EliminacionContactoService` orquesta consumo y borrado, distinguiendo el fallo al generar, el fallo al consumir y el fallo al eliminar. `ContactoService.eliminar` conserva la transacción del dominio y `FotoService` programa el borrado de la fotografía tras el commit. El controlador solo enlaza las solicitudes, usa el identificador de la sesión JDBC y devuelve mensajes genéricos. No se añadieron entidades JPA ni dependencias.

La Etapa 5 cierra M4 y añade revocación de usuarios. `IntegridadExceptionTranslator` busca causas anidadas, incluidas causas cíclicas, y traduce violaciones de integridad a `DniDuplicadoException`, `ConflictoIntegridadException` o `ValidacionDominioException`; `DniDuplicadoException` permite presentar el error específicamente en el campo `dni` durante el alta y la edición, mientras `GlobalExceptionHandler` responde 409 o 400 con mensajes genéricos y registra la causa solo en logs. `ContactoService` usa un helper que centraliza la traducción dentro de las operaciones transaccionales de alta y edición. `V004__usuarios_activo.sql` añade `usuarios.activo`; `UsuarioUserDetailsService` rechaza cuentas inactivas y correos de más de 100 caracteres con mensaje genérico. `UsuarioRevocacionService` bloquea la fila del usuario con `PESSIMISTIC_WRITE`, aplica `activo=false` y elimina sus sesiones mediante `SesionRevocacionJdbcRepository` en la misma transacción. `AutenticacionRevocacionFilter` abre una transacción antes de Spring Session, toma el mismo bloqueo de fila y lo conserva hasta que se persiste la sesión; envuelve la respuesta con `CapturadorCookieSesion` para publicar cabeceras solo después del commit y suprimir la cookie rotada si hay rollback, sin confirmar la response antes de decidir. Si la autenticación termina marcada para rollback, cierra la transacción con rollback limpio, elimina la sesión creada cuando no había sesión previa y restaura la sesión previa mediante `SesionRollbackJdbcRepository`, ahora transaccional. `UsuarioRevocacionRunner` ejecuta la revocación después del aprovisionamiento cuando se define `APP_REVOCAR_EMAIL`. El aprovisionamiento no reactiva usuarios existentes. No se añadió endpoint HTTP, interfaz administrativa ni dependencias.

## Funcionalidades pendientes

- Caducidad operativa adicional a la configuración actual de `SPRING_SESSION_TIMEOUT=7d` y de la cookie de sesión.

## Limitaciones y deuda técnica pendiente

- Reconciliación automática de fotografías huérfanas: si falla la revalidación de referencias o el borrado del archivo en `afterCommit`, puede quedar una fotografía huérfana. No hay reconciliación automatizada ni política de retención definida; su implementación queda pendiente.
- Validación interactiva en navegador del diseño responsive: todavía no se ha realizado. Las capturas se generaron renderizando con Quick Look el HTML real servido por la aplicación con datos ficticios; no se afirma verificación en Chrome, Firefox, Playwright ni otra herramienta de navegador.

## Capturas

Las capturas finales están en:

- `docs/imagenes/login.jpeg`
- `docs/imagenes/Inicio.jpeg`
- `docs/imagenes/VerContactos.jpeg`
- `docs/imagenes/RegistrarContacto.jpeg`
- `docs/imagenes/DetalleContacto.jpeg`

Se generaron a 1600 × 1600 desde el HTML real servido por la aplicación en ejecución contra `gestor_contactos_test`, con datos ficticios, y se convirtieron a JPEG; no son imágenes simuladas. No se copiaron las capturas antiguas de `imagenes/`, que sigue ignorada por Git. No se afirma verificación interactiva en navegador.

El detalle de decisiones y trazabilidad está en `docs/arquitectura.md`.

## Pruebas

### Resumen

La cobertura incluye autenticación, CSRF, sesiones JDBC, alta, detalle, edición y eliminación de contactos, edición y eliminación de múltiples filas de teléfonos y correos, conservación y reemplazo de fotografías, revalidación de referencias antes del borrado, rollback real por violación de integridad, errores multipart en alta y edición, validación de fotografías, listado, búsqueda, filtros, paginación, caracteres comodín literales, 404 controlado, confirmación de borrado, consumo de token, traducción centralizada de integridad y revocación de usuarios. Las pruebas de `ConfirmacionEliminacionMysqlTests` ejecutan siete casos reales contra `gestor_contactos_test`: concurrencia, `REQUIRES_NEW`, token expirado, token incorrecto, token ya consumido, reemplazo por sesión e independencia entre sesiones. Las pruebas de `RevocacionSesionesMysqlTests` y `RollbackRevocacionMysqlTests` comprueban contra MySQL que se borran solo las sesiones del usuario revocado, que las ajenas siguen válidas, que un nuevo login falla, que un correo de más de 100 caracteres no crea sesión y que un fallo JDBC al borrar sesiones revierte `activo`. `ConcurrenciaRevocacionLoginMysqlTests` comprueba que un login iniciado antes de una revocación no deja una sesión utilizable. `RollbackFiltroLoginMysqlTests` cubre el rollback del filtro, incluido el caso de sesión previa descrito en `Usuario de prueba`. `BootstrapRecursosTests` comprueba el HTML renderizado de login, edición y eliminación: un solo `main`, WebJars resueltos, `label/for`, `id/name` reales de teléfonos y correos, y token CSRF en los formularios POST. La suite también cubre el límite de 20 teléfonos y 20 correos en DTO y JavaScript, la política de contraseña de aprovisionamiento, el mensaje genérico de DNI duplicado y la supresión de la cookie rotada en rollback. La relación detallada de pruebas y decisiones está en [`docs/arquitectura.md`](docs/arquitectura.md).

H2 y Testcontainers no se usan. Para ejecutar las pruebas, exporta `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` apuntando exactamente a `gestor_contactos_test`, define `APP_PHOTOS_DIR` en una ruta temporal privada, deja vacías las variables de aprovisionamiento y revocación, y ejecuta:

```bash
unset APP_PROVISIONING_EMAIL APP_PROVISIONING_PASSWORD APP_PROVISIONING_NOMBRE APP_REVOCAR_EMAIL
export APP_PHOTOS_DIR="${TMPDIR:-/tmp}/gestor-contactos-test-fotos"
mvn test
```

Resultado verificado: `BUILD SUCCESS`, suite Java completa de 217 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas, ejecutada contra MySQL 8.4.9 aislado.

### Pruebas JavaScript

Las pruebas de la lógica de formularios usan Node.js nativo y se ejecutan de forma independiente: no hay `package.json`, no se han añadido dependencias y no se integran en `mvn test` ni en CI. Para verificarlas:

```bash
node --check src/main/resources/static/js/app.js
node --test src/test/javascript/app.test.mjs
```

`node --check` valida la sintaxis del script de formularios. `node --test` ejecuta la suite JavaScript con el módulo `node:test`, sin dependencias externas; el resultado verificado es 8 aprobadas y 0 fallidas.

## Uso de agentes de IA

- Las reglas generales están en `AGENTS.md`.
- El rol del arquitecto está en `.opencode/agents/architect.md`.
- El rol del revisor está en `.opencode/agents/reviewer.md`.
- El desarrollador humano revisa, comprende y aprueba todos los cambios.
