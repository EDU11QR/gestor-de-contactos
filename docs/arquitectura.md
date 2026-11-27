# Arquitectura del Gestor de Contactos

## 1. Objetivo y alcance

Este documento registra las decisiones de arquitectura ya establecidas para el Gestor de Contactos. Su fuente es `AGENTS.md`, el historial de análisis de arquitectura acordado antes de la implementación y las decisiones técnicas aprobadas durante las Etapas 1, 2, 3, la Fase 4 y la Etapa 5.

Todo elemento que no pueda verificarse en esas fuentes se marca como pendiente de aclaración y no debe asumirse durante la implementación.

El sistema es una aplicación web monolítica para administrar contactos de clientes y proveedores, con interfaz Thymeleaf y Bootstrap 5, persistencia en MySQL y autenticación con Spring Security.

## 2. Decisiones generales

- La aplicación es un monolito; no se implementan microservicios.
- La separación de responsabilidades es: `Controller`, `Service`, `Repository`, `Entity`, `DTO`, `Config`, `Security` y `Exception`.
- Los controladores no contienen lógica de negocio y las vistas no ejecutan consultas de persistencia.
- El stack es Java 21, Spring Boot, Spring MVC, Thymeleaf, Bootstrap 5, Spring Security, Spring Data JPA, Hibernate, MySQL, Maven, JUnit 5 y Mockito.
- La persistencia de sesiones usa Spring Session JDBC, decisión ya acordada para el diseño aprobado.
- No se introducen React ni otro frontend independiente.
- No se usa Apache Tika para la validación de imágenes.
- No se usa Flyway ni Liquibase; el DDL se aplica manualmente y en orden.
- H2 no sustituye a MySQL en las pruebas de persistencia.

## 3. Modelo de datos

### 3.1 Entidades de dominio

| Tabla | Descripción | Atributos principales |
|---|---|---|
| `usuarios` | Cuentas de acceso al sistema. | `id_usuario`, `email`, `password_hash`, `nombre`, `activo`. |
| `contactos` | Datos principales del contacto. | `id_contacto`, `dni`, `tipo_contacto`, `nombres`, `apellidos`, `foto_archivo`, `foto_content_type`, `foto_tamano_bytes`. |
| `telefonos` | Teléfonos asociados a un contacto. | `id_telefono`, `id_contacto`, `numero`. |
| `correos_electronicos` | Correos asociados a un contacto. | `id_correo`, `id_contacto`, `direccion`. |

### 3.2 Tablas de sesión y confirmación

- `SPRING_SESSION`.
- `SPRING_SESSION_ATTRIBUTES`.
- `confirmaciones_eliminacion`.

`SPRING_SESSION` y `SPRING_SESSION_ATTRIBUTES` son gestionadas por Spring Session JDBC. No se modelan como entidades JPA del dominio y su DDL se aplica de forma manual.

`confirmaciones_eliminacion` almacena `session_id`, `token_hash`, `id_contacto`, `creado_en`, `expira_en` y `consumido_en`. Su DDL se aplica mediante `V003__confirmaciones_eliminacion.sql` y no se modela como entidad JPA. La primary key es `session_id`, por lo que una sesión solo mantiene una confirmación y la generación posterior reemplaza la anterior. Tiene índices por hash, contacto y expiración, y una restricción que exige `expira_en > creado_en`.

### 3.3 Relaciones

- Un `contacto` tiene uno o varios `telefonos`.
- Un `contacto` tiene uno o varios `correos_electronicos`.
- Las tablas hijas se eliminan en cascada al eliminar el contacto.
- El `usuario` no es propietario de los contactos; no existe la noción de contactos por usuario.
- La clasificación de un contacto es `CLIENTE`, `PROVEEDOR` o `AMBOS`.
- Un contacto `AMBOS` aparece en los listados de clientes y de proveedores, y solo en el filtro `AMBOS` cuando se solicita exclusivamente esa clasificación.

### 3.4 Identidad y unicidad

- El DNI es peruano: exactamente 8 dígitos, sin checksum.
- RUC y pasaporte quedan fuera del alcance acordado.
- El DNI es único en `contactos`.
- El email de usuario es único y se normaliza con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios, al revocar y al autenticar.
- `usuarios.activo` es `BOOLEAN NOT NULL DEFAULT TRUE` con la restricción `chk_usuarios_activo`; un usuario inactivo no puede autenticarse.
- Se evitan registros duplicados mediante restricciones de unicidad en servidor y base de datos.

## 4. Validaciones y reglas de negocio

- Todo contacto debe tener nombres, apellidos, DNI, al menos un teléfono, al menos un correo electrónico y fotografía.
- El DNI debe tener 8 dígitos y ser único.
- Los formatos de teléfono y correo se validan en el servidor. El patrón de teléfono es `^(?:\+51|51)?9\d{8}$` y se normaliza a `+51XXXXXXXXX`; el correo usa `@Email`.
- Cada contacto admite como máximo 20 teléfonos y 20 correos electrónicos en alta y edición. El límite se valida en los DTO y se refleja en `app.js`.
- Se valida la presencia de datos obligatorios antes de persistir.
- Se valida y sanitiza la entrada en el servidor.
- Las longitudes aplicadas son: nombres y apellidos hasta 255 caracteres, dirección de correo hasta 255 caracteres, número de teléfono hasta 50 caracteres y DNI exacto de 8 caracteres.

### 4.1 Etapa 2 implementada

- `GET /contactos` está protegido y muestra el listado sin exponer entidades JPA.
- `ContactoBusquedaForm` transporta `termino`, `tipo`, `page` y `size`; el controlador solo enlaza el formulario con el servicio.
- `ContactoService` normaliza y valida la entrada: recorta el término, rechaza más de 100 caracteres, escapa `%`, `_` y `\`, y traduce la clasificación a `TipoContacto`.
- La búsqueda es parcial e insensible a mayúsculas sobre nombres, apellidos y DNI.
- El filtro por clasificación agrupa `CLIENTE` con `AMBOS` y `PROVEEDOR` con `AMBOS`; el filtro `AMBOS` excluye los otros tipos.
- La paginación usa `Page` con tamaño por defecto 10 y valores permitidos 10, 20 y 50; `page < 0` se normaliza a 0 y `size` no permitido a 10.
- El orden es fijo: `apellidos ASC`, `nombres ASC`, `id ASC`. No existe parámetro de orden libre.
- `ContactoRepository.buscarContactos` usa una sola consulta JPQL con agrupación explícita de búsqueda y clasificación.
- `ContactoListadoDto` contiene solo `id`, nombres, apellidos, DNI y clasificación; no carga teléfonos, correos ni rutas de fotografías.
- Las vistas `contactos/listado` y `contactos/no-encontrado` muestran los resultados y el 404 controlado sin detalles internos.
- No se añadió DDL, ni orden por parámetro libre, ni preservación de filtros desde el detalle.

### 4.2 Etapa 3 implementada

- `GET /contactos/{id}/editar` y `POST /contactos/{id}/editar` están protegidos; el POST exige token CSRF y un id inexistente responde 404 controlado.
- `ContactoEdicionForm` reutiliza las restricciones de `ContactoForm`, pero el campo `foto` es opcional para conservar la fotografía existente.
- `ContactoService.obtenerFormularioEdicion` precarga los datos del contacto y todas sus listas de teléfonos y correos.
- `contactos/editar` enlaza cada fila con `telefonos[i].numero` y `correosElectronicos[i].direccion`, permite agregar y quitar filas, deshabilita quitar la última fila y reindexa los nombres antes del envío para no perder valores no modificados.
- `ContactoService.actualizar` carga la entidad con `LockModeType.PESSIMISTIC_WRITE`, valida que el DNI no pertenezca a otro contacto y normaliza teléfonos y correos con las mismas reglas del alta.
- Las colecciones de teléfonos y correos se limpian con `flush()` y se reinsertan para conservar las restricciones de unicidad por contacto y número o dirección; la transacción se revierte completa ante una violación real.
- La edición conserva la fotografía anterior cuando no se adjunta archivo; con archivo nuevo aplica las mismas validaciones del alta y programa el borrado anterior solo tras el commit y la revalidación de referencias.

### 4.3 Fase 4 implementada

- `GET /contactos/{id}/eliminar` y `POST /contactos/{id}/eliminar` están protegidos; el POST exige token CSRF y un id inexistente responde con redirección controlada y mensaje genérico.
- `GET /contactos/{id}/eliminar` obtiene el detalle, genera un token aleatorio de 32 bytes con 43 caracteres en Base64URL y usa el identificador de la sesión JDBC, sin guardar el token en la sesión HTTP.
- Se persiste únicamente el hash SHA-256 del token, junto con `session_id`, `id_contacto`, `creado_en`, `expira_en` y `consumido_en`. El TTL es de 10 minutos y la operación de generación reemplaza cualquier confirmación anterior de la misma sesión.
- `POST /contactos/{id}/eliminar` valida el formato del token, calcula el hash y consume la confirmación mediante un `UPDATE` condicional y atómico que exige la misma sesión, el mismo contacto, `consumido_en IS NULL` y `expira_en > CURRENT_TIMESTAMP(3)`.
- `ConfirmacionEliminacionService.consumir` usa `REQUIRES_NEW` para que el consumo quede confirmado de forma independiente aunque la transacción externa se revierta o falle la eliminación posterior.
- `EliminacionContactoService` usa excepciones distintas para fallo al generar, fallo al consumir y fallo al eliminar; cada una conserva la causa y la capa HTTP solo muestra un mensaje genérico.
- `ContactoService.eliminar` carga el contacto con `LockModeType.PESSIMISTIC_WRITE`, borra el contacto y programa el borrado de la fotografía en `afterCommit` solo si `FotoReferenciaService` confirma en `REQUIRES_NEW` que el archivo ya no está referenciado.

### 4.4 Etapa 5 implementada

- `IntegridadExceptionTranslator` recorre las causas anidadas de `DataIntegrityViolationException` y `ConstraintViolationException`, controla ciclos y produce `DniDuplicadoException`, `ConflictoIntegridadException` o `ValidacionDominioException` sin copiar SQL, nombres de restricción ni mensajes del proveedor; `DniDuplicadoException` se presenta como error del campo `dni` en alta y edición.
- `GlobalExceptionHandler` devuelve HTTP 409 para conflictos de integridad y HTTP 400 para validaciones de dominio; ambos mensajes son genéricos y la causa completa queda solo en logs.
- `ContactoService.ejecutarConTraduccionIntegridad` centraliza la traducción para crear y actualizar, sin duplicar `try/catch`.
- `UsuarioUserDetailsService` valida que el email normalizado no supere 100 caracteres y lanza `UsernameNotFoundException("Credenciales inválidas")`; cuando `activo=false`, lanza `DisabledException("Credenciales inválidas")`. La capa HTTP no revela el motivo específico.
- `UsuarioRevocacionService.revocar` normaliza y valida el correo, bloquea la fila con `findByEmailForUpdate`, no hace nada si el usuario no existe, aplica `activo=false` una sola vez y elimina las sesiones del usuario en la misma transacción.
- `AutenticacionRevocacionFilter` intercepta `POST /login`, detecta la ruta por `getServletPath()` con fallback a `getRequestURI()`, abre una transacción antes de Spring Session y bloquea la fila del usuario por el correo normalizado. Mantiene el bloqueo hasta que se persiste la sesión; si la cadena termina marcada para rollback, cierra la transacción con rollback limpio y aplica el manejo de sesión descrito en E5-10.
- `SesionRevocacionJdbcRepository` construye un `DELETE FROM <tabla> WHERE PRINCIPAL_NAME = ?` con el nombre de tabla validado por expresión regular y el principal enlazado como parámetro.
- `UsuarioRevocacionRunner` se ejecuta con `@Order(2)`, después del aprovisionamiento (`@Order(1)`), solo cuando `APP_REVOCAR_EMAIL` no está vacío. El aprovisionamiento no reactiva usuarios existentes. No se añade endpoint HTTP ni interfaz administrativa de usuarios.

## 5. Fotografías
- La fotografía es obligatoria en el alta; en la edición, dejar el campo vacío conserva la existente.

- Formatos aceptados: JPG, JPEG y PNG.
- Tamaño máximo: 2 MB por archivo.
- El límite HTTP se configura con `spring.servlet.multipart.max-file-size=2MB` y `spring.servlet.multipart.max-request-size=3MB`; el límite efectivo de la fotografía es de 2 MB y coincide con `FotoService`.
- Dimensiones máximas: 2000 × 2000 píxeles.
- Se valida la firma real del archivo y se decodifica con `ImageIO` / `ImageReader`.
- APNG se rechaza explícitamente.
- Los archivos se almacenan con nombres UUID en un directorio privado, fuera de las rutas públicas.
- El archivo temporal se sincroniza con `FileChannel.force(true)` antes del movimiento atómico al directorio privado.
- No se almacenan las dimensiones de la imagen en MySQL.
- El nombre de archivo es único en `contactos`.
- El contenido multimedia no se sirve desde una ruta pública de recursos estáticos.

### 5.1 Etapa 1 implementada

- El alta se realiza en `POST /contactos` con `multipart/form-data` y token CSRF.
- El formulario está en `GET /contactos/nuevo` y usa `ContactoForm` con listas anidadas de teléfono y correo.
- La confirmación usa PRG: `POST /contactos` redirige a `GET /contactos/{id}`.
- `ContactoService.crear` verifica DNI duplicado, normaliza teléfono y correo, delega la fotografía y persiste la entidad.
- `ContactoService.obtenerDetalle` devuelve `ContactoDetalleDto`; no expone entidades JPA ni información de almacenamiento a la vista.
- La validación de fotografías comprueba firma y MIME reales, dimensiones antes de decodificar y chunks PNG para rechazar APNG.
- El directorio privado se configura con `app.fotos.dir=${APP_PHOTOS_DIR}` y no se publica como recurso estático.
- `GlobalExceptionHandler` traduce `DataIntegrityViolationException` a HTTP 409 sin exponer SQL ni detalles internos.
- Un exceso del límite multipart se devuelve al formulario de alta con un error de campo estático; no se exponen mensajes del contenedor ni de la excepción.
- `GlobalExceptionHandler` distingue la ruta de alta de la de edición y, para `POST /contactos/{id}/editar`, reconstruye `ContactoEdicionForm` y devuelve `contactos/editar` con el id y los datos precargados.

### 5.2 Etapa 3 implementada

- El formulario `contactos/editar` permite no adjuntar fotografía e indica que en ese caso se conserva la actual; no expone la ruta privada del archivo.
- Con fotografía nueva se aplican las mismas validaciones reales de MIME, firma, dimensiones y tamaño que en el alta.
- `FotoService.guardar` mantiene la compensación registrada antes de escribir el archivo nuevo.
- `FotoService.eliminarTrasConfirmacion` valida el nombre de archivo, exige transacción activa y programa el borrado en `TransactionSynchronization.afterCommit`.
- `FotoReferenciaService.estaReferenciada` consulta con `REQUIRES_NEW` y `readOnly` dentro del callback; el archivo anterior solo se elimina si la comprobación confirma que ya no está referenciado.
- La fotografía anterior no se elimina si falla la validación del archivo nuevo, si falla la persistencia, si la transacción se revierte o si aparece una referencia antes del callback.
- En alta, reemplazo y eliminación, si falla la revalidación de referencias o el borrado en `afterCommit`, el archivo puede quedar huérfano. No se implementa reconciliación automática ni política de retención; queda documentado como deuda técnica pendiente.

## 6. Transacciones

- `ContactoService` es el punto transaccional de la operación de contactos.
- `ConfirmacionEliminacionService.consumir` usa `REQUIRES_NEW` para confirmar el token de forma independiente; `ConfirmacionEliminacionService.generar` y el acceso JDBC de `ConfirmacionEliminacionRepository` no dependen de una transacción externa.
- `FotoService` participa con propagación `MANDATORY`.
- Antes de operar, se verifica `isActualTransactionActive()` e `isSynchronizationActive()`.
- La compensación de escritura se registra antes de escribir el archivo en disco.
- En la edición se bloquea la fila del contacto con `SELECT … FOR UPDATE`; la foto anterior se elimina en `afterCommit` solo si la transacción termina correctamente y `FotoReferenciaService` confirma en una transacción nueva que el archivo no está referenciado.
- En la eliminación se bloquea la fila con `SELECT … FOR UPDATE`, se borra la entidad dentro de la misma transacción y se programa el borrado de la fotografía en `afterCommit` con la misma revalidación de referencias.
- `UsuarioRevocacionService.revocar` usa una sola transacción: bloquea `usuarios` con `PESSIMISTIC_WRITE`, escribe `activo=false` y ejecuta el `DELETE` de sesiones por `PRINCIPAL_NAME`. Si falla el borrado de sesiones, se revierte el cambio de estado; no se usa `REQUIRES_NEW` porque dejaría la revocación a medias.
- `AutenticacionRevocacionFilter` usa la misma fila bloqueada durante todo `POST /login`, incluida la persistencia de la sesión por Spring Session, y cierra con `commit` o con `rollback` limpio. Si hay rollback, aplica el manejo de sesión descrito en E5-10. La coordinación es de base de datos, no un lock JVM local.
- El despliegue previsto es monoinstancia.

## 7. Seguridad y sesiones

- Spring Security protege las rutas privadas.
- `GET /` es pública y contiene solo información de presentación; no contiene información privada. El formulario de logout se muestra únicamente a usuarios autenticados.
- Las contraseñas se almacenan con BCrypt.
- CSRF permanece activo.
- El logout se realiza con `POST` y CSRF, y afecta solo a la sesión actual.
- La authority es la implícita `ROLE_USER`; no se persiste ni se confunde con roles de negocio.
- El principal personalizado es `Serializable` e implementa `CredentialsContainer`, con `serialVersionUID` estable.
- El hash BCrypt no permanece en el principal después de la autenticación.
- Se verifica por prueba unitaria que el `SecurityContext` serializado no contiene el hash BCrypt ni la contraseña en texto plano.
- La verificación real en `SPRING_SESSION_ATTRIBUTES` con Spring Session JDBC y MySQL está cubierta por una prueba de integración ejecutada contra `gestor_contactos_test` en un entorno MySQL 8.4.9 aislado.
- El valor de la cookie `GESTOR_SESSION` se decodifica con Base64 estándar en el filtro y en las pruebas que consultan `SPRING_SESSION`; Base64URL se reserva para el token de confirmación de eliminación.
- La revocación de usuarios invalida sus sesiones por `PRINCIPAL_NAME` en la misma transacción en que se marca `activo=false`.
- Un usuario inactivo no puede iniciar sesión; la respuesta es genérica para no revelar el estado de la cuenta. Un correo ausente, vacío o de más de 100 caracteres recibe la misma respuesta genérica.
- Un `POST /login` y una revocación concurrentes se coordinan por el bloqueo pesimista de la fila del usuario; la revocación no deja sesiones utilizables creadas por un login iniciado antes de ella.
- El email de usuario se normaliza con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios, al revocar y al autenticar.
- Las sesiones usan estos valores acordados:
  - `spring.session.store-type=jdbc`.
  - `spring.session.jdbc.table-name=SPRING_SESSION`.
  - `spring.session.jdbc.initialize-schema=never`.
  - `spring.session.timeout=7d`.
  - `server.servlet.session.cookie.name=GESTOR_SESSION`.
  - `server.servlet.session.cookie.max-age=7d`.
  - `server.servlet.session.cookie.http-only=true`.
  - `server.servlet.session.cookie.secure=true` por defecto; puede overridearse en pruebas HTTP locales.
  - `server.servlet.session.cookie.same-site=lax`.
  - `server.forward-headers-strategy=framework` por defecto para reconocer proxy TLS.
- La caducidad de la cookie y la inactividad del servidor no son funcionalmente equivalentes.

## 8. Usuarios

- El aprovisionamiento de usuarios es idempotente y se ejecuta fuera de HTTP.
- El aprovisionamiento usa un secreto de entorno y almacena contraseñas con BCrypt. La contraseña debe tener al menos 12 caracteres, no contener espacios y no ser igual al correo ni al nombre.
- El aprovisionamiento solo crea usuarios; no modifica contraseñas ni nombres existentes y no incluye rotación automática en esta fase.
- La revocación se ejecuta fuera de HTTP mediante `UsuarioRevocacionRunner` y `APP_REVOCAR_EMAIL`, después del aprovisionamiento.
- La revocación es idempotente: un usuario inexistente no se crea, uno ya inactivo no cambia, y las sesiones pendientes se eliminan en cada ejecución.
- La revocación invalida las sesiones del usuario por `PRINCIPAL_NAME`, bloquea la fila con `PESSIMISTIC_WRITE` y no expone un endpoint HTTP.
- `APP_REVOCAR_EMAIL` se usa solo durante el arranque de revocación y debe retirarse del entorno después de una ejecución exitosa.
- El email se normaliza con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios, al revocar y al autenticar, y se limita a 100 caracteres.
- No se publica un registro público de usuarios ni credenciales reales en el repositorio.
- No se publica un usuario de prueba con credenciales reales en el repositorio.

## 9. DDL y configuración

- La convención de DDL es `db/ddl/V###__*.sql`.
- Los archivos previstos son `db/ddl/V001__dominio_inicial.sql`, `db/ddl/V002__sesiones_jdbc.sql`, `db/ddl/V003__confirmaciones_eliminacion.sql` y `db/ddl/V004__usuarios_activo.sql`.
- El DDL se aplica manualmente y en orden; no se usa Flyway ni Liquibase.
- `V004__usuarios_activo.sql` añade `activo BOOLEAN NOT NULL DEFAULT TRUE` y `chk_usuarios_activo` a `usuarios`.
- `spring.jpa.hibernate.ddl-auto=validate`.
- `app.fotos.dir=${APP_PHOTOS_DIR}`; no se define un valor por defecto público.
- `app.provisioning.*` y `app.revocar.email` se resuelven desde `APP_PROVISIONING_*` y `APP_REVOCAR_EMAIL`; ambos runners omiten su acción cuando la variable está vacía.
- `spring.servlet.multipart.max-file-size=2MB` y `spring.servlet.multipart.max-request-size=3MB`; el límite por archivo es de 2 MB.
- Las pruebas de contactos fijan `app.fotos.dir` en la carpeta temporal del sistema y exigen `gestor_contactos_test`.
- `dni` se mapea con `CHAR(8)` y tipo JDBC explícito o `columnDefinition`.
- `CHAR(8)` con mapeo JDBC explícito o `columnDefinition` se validó con `ddl-auto=validate` en MySQL 8.4.9; no hizo falta la alternativa `VARCHAR(8)` con `CHECK`.
- La versión objetivo de despliegue es MySQL 8.4.9. La suite Java completa se ejecutó contra `gestor_contactos_test` en un servidor MySQL 8.4.9 aislado.

## 10. Pruebas

### Implementadas y ejecutadas

- Pruebas unitarias con JUnit 5 y Mockito: autenticación, normalización de correo, aprovisionamiento idempotente, borrado de credenciales y serialización del `SecurityContext`.
- Pruebas HTTP con MockMvc y `spring-security-test`: CSRF positivo y negativo, login válido e inválido, normalización del correo, logout con invalidación de sesión y bloqueo de reutilización de la sesión anterior.
- Prueba de integración real con Spring Session JDBC y MySQL 8.4.9 aislado contra `gestor_contactos_test`: consulta `SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES`, deserializa `SPRING_SECURITY_CONTEXT`, verifica la ausencia del hash y de la contraseña en texto plano, comprueba que las credenciales están borradas del principal y de `Authentication`, reutiliza la sesión en una segunda petición y verifica que la fila de sesión desaparece tras logout.
- La prueba JDBC exige que el nombre exacto de la base de datos sea `gestor_contactos_test` y falla antes de conectarse si la URL falta o apunta a otra base.
- Pruebas unitarias de la Etapa 1: validación de `ContactoForm`, normalización, persistencia y traducción de DNI duplicado por restricción única en `ContactoService`, y validación real, firma, MIME, dimensiones preventivas, APNG estructural, PNG con secuencia `acTL` en metadatos, límites, transacción, rollback, fallos de escritura y compensación en `FotoService`.
- Prueba HTTP/integración de la Etapa 1 contra `gestor_contactos_test`: autenticación, CSRF positivo y negativo, alta válida con PRG y detalle, archivo en directorio privado, DNI duplicado, fotografía inválida y restricción de unicidad en base de datos.
- Pruebas HTTP reales contra el servidor embebido para el límite multipart: aceptación de una fotografía válida cercana a 2 MB, rechazo controlado por encima de 2 MB y rechazo de una solicitud sin token CSRF.
- Pruebas de la Etapa 2: unitarias de normalización y validación en `ContactoService`, de integración de `ContactoRepository.buscarContactos` contra `gestor_contactos_test` y HTTP con MockMvc para autenticación, búsqueda, filtros, paginación, resultados vacíos, detalle existente y 404 controlado.
- Pruebas de normalización de `page` y `size` no numéricos en `ContactoService` y HTTP con MockMvc.
- Pruebas de integración de `%`, `_` y `\` literales en `ContactoRepository.buscarContactos` contra `gestor_contactos_test`.
- Pruebas unitarias de la Etapa 3 en `ContactoServiceEdicionTests`: conservación del DNI propio, rechazo de DNI ajeno, conservación y reemplazo de fotografía y validaciones de teléfono y correo.
- Pruebas HTTP con MockMvc de la Etapa 3: precarga del formulario, actualización válida con redirección, 404 en GET y POST inexistentes, CSRF rechazado y conservación de la fotografía ante un archivo inválido.
- Pruebas HTTP con MockMvc para múltiples filas: renderizado de todos los teléfonos y correos, controles de agregar y quitar, edición de una fila conservando las demás, eliminación de filas manteniendo al menos una y rechazo del formulario sin teléfono ni correo.
- Prueba HTTP real contra el servidor embebido: una fotografía superior a 2 MB en `POST /contactos/{id}/editar` devuelve `contactos/editar` con el id, el error controlado y la fotografía existente sin cambios.
- Pruebas unitarias de `FotoService` con `Mockito`: borrado de la fotografía anterior solo cuando la transacción se confirma y no se borra si la revalidación confirma una referencia.
- Pruebas de integración real en `ContactoEdicionIntegridadTests` contra `gestor_contactos_test`: la fotografía anterior se conserva si aparece una referencia antes del callback, y una violación única real en teléfonos revierte nombres, apellidos, teléfonos, correos y fotografía.
- Pruebas unitarias de la Fase 4 en `EliminacionContactoServiceTests` y `ConfirmacionEliminacionServiceTests`: orden de consumo antes del borrado, propagación de confirmación inválida, traducción separada de fallos de generación y consumo con conservación de la causa, y traducción del fallo posterior al borrado.
- Pruebas HTTP con MockMvc de la Fase 4: vista de confirmación con token oculto de 43 caracteres, CSRF rechazado, token ausente o inválido, token expirado o ya consumido, mensaje flash genérico y no eliminación del contacto ante fallos de persistencia o borrado.
- Pruebas de integración real de repositorio y servicio de confirmación contra `gestor_contactos_test`, incluido el DDL de `V003__confirmaciones_eliminacion.sql`.
- Pruebas reales en `ConfirmacionEliminacionMysqlTests` contra `gestor_contactos_test`: consumo concurrente con una sola transacción ganadora, confirmación de `REQUIRES_NEW` pese al rollback externo, y rechazo de token expirado, incorrecto y ya consumido.
- Pruebas unitarias de M4 en `IntegridadExceptionTranslatorTests` y `GlobalExceptionHandlerTests`: causas anidadas, ciclos, Hibernate y Jakarta calificados, HTTP 409/400 con mensajes genéricos y causa registrada solo en logs.
- Pruebas unitarias de revocación en `UsuarioRevocacionServiceTests`, `SesionRevocacionJdbcRepositoryTests` y `UsuarioRevocacionRunnerTests`: normalización, longitud, usuario inexistente, inactivo/revocación idempotente, SQL con parámetro enlazado, nombre de tabla válido y runner condicional.
- Pruebas HTTP y de usuario inactivo: `AutenticacionHttpTests` aísla el repositorio JDBC con `@MockitoBean`, `AutenticacionTests` verifica `DisabledException` directamente en `UsuarioUserDetailsService` y `GestorDeContactosApplicationTests` aísla el mismo repositorio para cargar el contexto sin DataSource.
- Pruebas reales en `RevocacionSesionesMysqlTests` y `RollbackRevocacionMysqlTests` contra `gestor_contactos_test`: dos sesiones del usuario revocado se eliminan, la sesión ajena permanece válida, un nuevo login falla con `/login?error`, un correo de más de 100 caracteres no crea sesión y un fallo JDBC real al borrar sesiones revierte `activo`.
- Prueba unitaria en `AutenticacionTests` de correo superior a 100 caracteres con `UsernameNotFoundException` genérico y sin acceso a repositorio.
- Prueba real en `ConcurrenciaRevocacionLoginMysqlTests`: un login pausado después de cargar el usuario y una revocación iniciada antes de reanudarlo terminan sin sesión utilizable y con el usuario inactivo.
- Pruebas reales en `RollbackFiltroLoginMysqlTests`: una excepción lanzada después de autenticar y persistir la sesión revierte una escritura JDBC aguas abajo, deja `activo` y el hash intactos, no deja fila de sesión ni atributos del usuario, y permite un login posterior válido. Una segunda prueba cubre el caso de sesión previa descrito en E5-10, incluida la cookie rotada que no debe publicarse.
- Pruebas de renderizado en `BootstrapRecursosTests`: las vistas de login, edición y eliminación tienen un solo `main`, resuelven CSS y JS de WebJars, enlazan `label/for` con los `id` reales de teléfonos y correos, conservan los nombres `telefonos[i].numero` y `correosElectronicos[i].direccion`, y envían CSRF en los formularios POST.
- Pruebas unitarias en `UsuarioProvisioningServiceTests`: aprovisionar un correo existente e inactivo no lo reactiva ni llama a `save`; una contraseña corta o derivada del correo se rechaza sin consultar ni guardar el usuario.
- Pruebas unitarias en `FormulariosContactoValidacionTests`: límite de 20 teléfonos y 20 correos en alta y edición.
- Pruebas unitarias en `CapturadorCookieSesionTests`: publicación de cabeceras al confirmar, supresión de la cookie de sesión al revertir y redirección diferida hasta que la sesión queda confirmada.
- Ejecución final: `BUILD SUCCESS`, suite Java completa de 217 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas, contra MySQL 8.4.9 aislado.
- Pruebas JavaScript: 8 aprobadas y 0 fallidas. Se ejecutan de forma independiente con `node --test src/test/javascript/app.test.mjs`, usando el módulo `node:test` sin dependencias externas; `node --check src/main/resources/static/js/app.js` verifica la sintaxis del script. No hay `package.json`, no se han añadido dependencias y ninguno de estos comandos está integrado en `mvn test` ni en CI.
- No se ha realizado validación interactiva en navegador del diseño responsive. Las capturas se generaron renderizando con Quick Look el HTML real servido por la aplicación con datos ficticios; no se afirma verificación en Chrome, Firefox, Playwright ni otra herramienta de navegador.
- H2 y Testcontainers no se usan. El daemon de Docker no responde en este entorno y no hay dependencia de Testcontainers.

### Pruebas pendientes

- Ninguna para el alcance implementado en las Etapas 1, 2, 3, la Fase 4 y la Etapa 5.

## 11. Trazabilidad de hallazgos

| ID | Problema original | Solución acordada | Estado |
|---|---|---|---|
| A1 | Riesgo de aprovisionar y revocar usuarios sin idempotencia, fuera de un flujo HTTP seguro, con posible exposición de credenciales y sesiones vigentes tras la revocación. | Aprovisionamiento idempotente fuera de HTTP, secreto de entorno y contraseñas BCrypt; revocación idempotente por `APP_REVOCAR_EMAIL`, `activo=false` y borrado transaccional de sesiones por `PRINCIPAL_NAME`. | IMPLEMENTADO Y VERIFICADO |
| A2 | Riesgo de cuentas duplicadas o autenticación inconsistente por emails con distinta capitalización o espacios. | Normalización del email con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios y al autenticar. | RESUELTO DOCUMENTALMENTE |
| A3 | Riesgo de persistir authorities o de confundir autoridad técnica con roles de negocio no previstos. | Authority implícita `ROLE_USER`, sin persistirla ni confundirla con roles de negocio. | RESUELTO DOCUMENTALMENTE |
| A4 / M3 | Riesgo histórico de operación parcial de contacto y fotografía (A4) y de condición de carrera al reemplazar o eliminar la fotografía (M3). | A4 motivó centralizar la transacción en `ContactoService`, usar `FotoService` con `MANDATORY`, verificar la transacción activa, registrar la compensación antes de escribir y programar el borrado en `afterCommit`; M3 motivó bloquear la fila y revalidar referencias con `REQUIRES_NEW`. Estado final: alta, reemplazo y eliminación aplican las reglas correspondientes, y la reconciliación automática de archivos huérfanos sigue pendiente. | IMPLEMENTADO Y VERIFICADO PARA ALTA, REEMPLAZO Y ELIMINACIÓN; DEUDA TÉCNICA PENDIENTE |
| A5 | Riesgo de romper Spring Session o exponer el hash BCrypt al serializar el principal en la sesión. | Principal personalizado `Serializable` y `CredentialsContainer`, `serialVersionUID` estable, sin hash BCrypt tras autenticación y prueba ejecutada de no serialización en `SPRING_SESSION_ATTRIBUTES`. | RESUELTO Y VERIFICADO |
| M1 | Ausencia de convención para DDL versionado y riesgo de derive automático del esquema. | Convención `db/ddl/V###__*.sql`, archivos `V001__dominio_inicial.sql`, `V002__sesiones_jdbc.sql`, `V003__confirmaciones_eliminacion.sql` y `V004__usuarios_activo.sql`, aplicación manual ordenada, sin Flyway/Liquibase y `ddl-auto=validate`. | RESUELTO Y APLICADO |
| M2 | El registro de UUID en vuelo está en memoria y no se comparte entre instancias. | Documentar el despliegue monoinstancia como restricción arquitectónica, sin añadir dependencias. | RESUELTO DOCUMENTALMENTE |
| M4 | Ausencia de una estrategia centralizada para traducir violaciones de validación y restricciones de integridad, incluidas causas anidadas. | `IntegridadExceptionTranslator` recorre causas anidadas y cíclicas de `DataIntegrityViolationException` y `ConstraintViolationException`; `ContactoService` lo usa en crear y actualizar, y `GlobalExceptionHandler` responde 409/400 con mensajes genéricos. | IMPLEMENTADO Y VERIFICADO |
| M5 | Posible discrepancia en el nombre real de la cookie y configuración Secure detrás de proxy TLS. | Fijar `GESTOR_SESSION` como nombre de cookie; `secure=true` y `server.forward-headers-strategy=framework` son defaults; `CapturadorCookieSesion` publica cabeceras después de la decisión transaccional. | CORREGIDO Y VERIFICADO |
| M6 | Incompatibilidad potencial del mapeo `CHAR(8)` con Hibernate `validate` en la versión objetivo MySQL 8.4.9. | `CHAR(8)` con mapeo JDBC explícito o `columnDefinition`; la validación completa con `ddl-auto=validate` se ejecutó en MySQL 8.4.9 y no hizo falta la alternativa `VARCHAR(8)` con `CHECK`. | CORREGIDO Y VERIFICADO |
| M7 | Riesgo de pérdida del archivo de fotografía antes del movimiento atómico. | `FileChannel.force(true)` sobre el archivo temporal antes del movimiento atómico, cubierto por pruebas unitarias. | IMPLEMENTADO Y VERIFICADO |
| M8 | Posible exposición de mensajes técnicos del contenedor al superar el límite multipart. | El exceso vuelve al formulario correspondiente, alta o edición, con un error de campo estático, sin mensajes del contenedor; cubierto por pruebas HTTP reales. | RESUELTO Y VERIFICADO |

### 11.1 Auditoría de la Etapa 3 y correcciones

| ID | Hallazgo comprobado | Corrección implementada | Estado |
|---|---|---|---|
| E3-1 | La edición mostraba y enviaba solo el primer teléfono y el primer correo, por lo que se perdían las filas adicionales. | `editar.html` renderiza todas las filas con índices explícitos, agrega y quita filas, impide quedar sin ninguna y reindexa antes del envío; cubierto por pruebas MockMvc de renderizado, edición conservando las demás filas, eliminación de filas y rechazo sin filas. | RESUELTO Y VERIFICADO |
| E3-2 | Un error multipart en edición devolvía `contactos/nuevo`, perdiendo el contexto de edición. | `GlobalExceptionHandler` detecta la ruta `POST /contactos/{id}/editar`, reconstruye `ContactoEdicionForm` desde `ContactoService` y devuelve `contactos/editar` con id, datos y error; cubierto por prueba HTTP real con servidor embebido. | RESUELTO Y VERIFICADO |
| E3-3 | La comprobación de referencias y el borrado del archivo anterior podían quedar separados por una transacción. | `FotoReferenciaService.estaReferenciada` usa `REQUIRES_NEW` dentro del callback y el archivo solo se elimina si no está referenciado; cubierto por prueba unitaria y prueba de integración real que fuerza una referencia antes del callback. | RESUELTO Y VERIFICADO |
| E3-4 | No existía una prueba que demostrara el rollback real de teléfonos y correos tras una violación de integridad. | `ContactoEdicionIntegridadTests` provoca una violación única real en MySQL contra `gestor_contactos_test`, sin `@Transactional` externo ni sustitución por Mockito, y comprueba nombres, apellidos, teléfonos, correos y fotografía intactos. | RESUELTO Y VERIFICADO |
| E3-5 | La documentación afirmaba una cobertura superior a la demostrada. | `README.md` y este documento se actualizaron con el alcance real de las pruebas; el contador de esa etapa quedó registrado como histórico y no como estado actual. | CORREGIDO Y VERIFICADO |

### 11.2 Auditoría final de la Fase 4 y correcciones

| ID | Hallazgo comprobado | Corrección implementada | Estado |
|---|---|---|---|
| E4-1 | `README.md` y este documento afirmaban que la eliminación estaba pendiente y mantenían un contador de pruebas desactualizado. | Se documentan los endpoints, el token aleatorio, el hash SHA-256, la asociación con sesión y contacto, el TTL de 10 minutos, el consumo atómico, `REQUIRES_NEW`, el DDL de V003, la eliminación transaccional, el borrado de fotografía tras el commit y la limitación de archivos huérfanos; el contador de esa etapa quedó registrado como histórico. | CORREGIDO Y VERIFICADO |
| E4-2 | `ContactoEliminacionHttpTests` usaba `@MockBean` y `@SpyBean`, deprecados en la versión actual de Spring Boot. | Se sustituyen por `@MockitoBean` y `@MockitoSpyBean`, conservando el comportamiento de las pruebas. | CORREGIDO Y VERIFICADO |
| E4-3 | `FalloPersistenciaConfirmacionException` se usaba tanto al generar como al consumir, con un mensaje que solo describía el registro. | Se añade `FalloConsumoConfirmacionException` para el consumo; se conserva la causa original, se mantiene el mensaje HTTP genérico y se actualizan controlador y pruebas unitarias. | CORREGIDO Y VERIFICADO |
| E4-4 | No estaba documentada la posibilidad de que quede una fotografía huérfana si falla el borrado diferido. | Se documenta como limitación conocida y deuda técnica pendiente, sin implementar reconciliación automática ni cambiar la política de la Fase 4. | CORREGIDO Y VERIFICADO |

### 11.3 Auditoría de la Etapa 5 y correcciones

| ID | Hallazgo comprobado | Corrección implementada | Estado |
|---|---|---|---|
| E5-1 | `IntegridadExceptionTranslatorTests` no compilaba por ambigüedad entre `jakarta.validation.ConstraintViolationException` y `org.hibernate.exception.ConstraintViolationException`. | Se califica el tipo de Hibernate en el traductor y el de Jakarta en la prueba; la suite M4 quedó verde. | CORREGIDO Y VERIFICADO |
| E5-2 | `AutenticacionTests` esperaba `DisabledException` directo, pero `DaoAuthenticationProvider` lo envuelve en `InternalAuthenticationServiceException`. | La prueba verifica `DisabledException` en `UsuarioUserDetailsService.loadUserByUsername`, que es donde se decide el bloqueo. | CORREGIDO Y VERIFICADO |
| E5-3 | `AutenticacionHttpTests` y `GestorDeContactosApplicationTests` excluían DataSource pero el contexto creaba `SesionRevocacionJdbcRepository`, que exige `JdbcTemplate`. | Se aísla el repositorio con `@MockitoBean` en ambos tests sin activar autoconfiguración JDBC. | CORREGIDO Y VERIFICADO |
| E5-4 | `RevocacionSesionesMysqlTests` comprobaba `"Cerrar sesión"` en `/contactos`, donde no forma parte de la vista. | La comprobación de texto se traslada a `/`; `/contactos` verifica únicamente que la sesión ajena sigue autenticada. | CORREGIDO Y VERIFICADO |
| E5-5 | Los documentos afirmaban que M4 y la revocación estaban pendientes y mantenían un contador desactualizado. | Se documenta la implementación real, `V004` y el runner de revocación; el contador de esa etapa quedó registrado como histórico, no como estado actual. | CORREGIDO Y VERIFICADO |
| E5-6 | Existía una carrera entre `POST /login` y la revocación: la sesión podía persistirse después de que la revocación borrara sesiones. | `AutenticacionRevocacionFilter` bloquea la fila del usuario en la transacción que envuelve el chain de login y la persistencia de Spring Session; `ConcurrenciaRevocacionLoginMysqlTests` verifica que no queda sesión utilizable. | CORREGIDO Y VERIFICADO |
| E5-7 | La longitud del correo no se validaba en la autenticación HTTP, aunque `SPRING_SESSION.PRINCIPAL_NAME` está limitado a 100 caracteres. | `UsuarioUserDetailsService` y el filtro de login tratan cualquier correo de más de 100 caracteres como credencial inválida genérica; cubierto por prueba unitaria y prueba MySQL. | CORREGIDO Y VERIFICADO |
| E5-8 | La prueba de rollback simulaba una excepción Java en el repositorio, no un fallo real de JDBC. | `RollbackRevocacionMysqlTests` usa una tabla de sesión inexistente controlada y verifica `DataAccessException` real, ausencia de tabla y conservación de `activo=true`. | CORREGIDO Y VERIFICADO |
| E5-9 | No existía prueba de que el aprovisionamiento respetara un usuario revocado. | `noReactivaUsuarioRevocadoAlAprovisionar` verifica que un usuario inactivo existente permanece inactivo y no se guarda. | CORREGIDO Y VERIFICADO |
| E5-10 | La prueba de rollback del filtro demostró que Spring Session JDBC persistía la sesión fuera de la transacción JPA y que, con una sesión previa, la rotación del identificador podía dejarla inutilizable tras el rollback. | `AutenticacionRevocacionFilter` captura el `Set-Cookie` de la respuesta con `CapturadorCookieSesion`, suprime la cookie rotada en rollback y publica cabeceras, redirecciones y flush solo después de confirmar o revertir; sin sesión previa elimina la sesión creada mediante `SessionRepository.deleteById`, y con sesión previa `SesionRollbackJdbcRepository`, ahora transaccional, restaura `SESSION_ID`, atributos y metadatos para conservar la cookie anterior. | CORREGIDO Y VERIFICADO |
| E5-11 | Las pruebas de filtro de listado usaban `%` como término con `terminoVacio=true` y dependían de que la BD no tuviera contactos residuales. | `ContactoRepositoryListadoTests` usa una marca única en apellidos y `terminoVacio=false` para aislar sus filas; la suite completa pasó sin borrar datos de `gestor_contactos_test`. | CORREGIDO Y VERIFICADO |

### 11.4 Correcciones posteriores a la auditoría final

| ID | Hallazgo comprobado | Corrección implementada | Estado |
|---|---|---|---|
| A-1 | Las capturas antiguas contenían datos personales y no podían reutilizarse como capturas saneadas. | No se copiaron ni se publicaron; la carpeta raíz `imagenes/` quedó ignorada por Git. Las cinco capturas finales se generaron con datos ficticios en `docs/imagenes/`. | CORREGIDO Y VERIFICADO |
| M-1 | Las rutas `docs/imagenes/login.jpeg`, `docs/imagenes/Inicio.jpeg`, `docs/imagenes/VerContactos.jpeg`, `docs/imagenes/RegistrarContacto.jpeg` y `docs/imagenes/DetalleContacto.jpeg` no estaban documentadas. | `README.md` y este documento enumeran las rutas finales; se generaron a 1600 × 1600 desde HTML real servido por la aplicación con datos ficticios y se convirtieron a JPEG. | CORREGIDO Y DOCUMENTADO |
| M-2 | No estaba demostrado que un rollback con sesión previa preservara la sesión cuando Spring Session rota el identificador. | La cobertura quedó integrada en `RollbackFiltroLoginMysqlTests`; la solución actualizada se describe en E5-10. | CORREGIDO Y VERIFICADO |
| M-3 | `SesionJdbcConMysqlTests` usaba Base64URL para una cookie que Spring Session codifica con Base64 estándar. | La prueba usa `Base64.getDecoder()`, alineado con `AutenticacionRevocacionFilter`; Base64URL sigue usándose solo en el token de eliminación. | CORREGIDO Y VERIFICADO |
| B-1 | `.DS_Store` y `db/.DS_Store` aparecían como archivos no ignorados. | `.DS_Store` se añadió a `.gitignore` sin eliminar archivos del sistema operativo. | CORREGIDO |
| S-1 | Los defaults no garantizaban cookie `Secure` ni interpretación de proxy TLS, y una respuesta confirmada podía conservar la cookie rotada tras un rollback. | `secure=true` y `server.forward-headers-strategy=framework` son defaults; `CapturadorCookieSesion` difiere redirección, error y flush y publica cabeceras solo después de commit/rollback, suprimiendo la cookie de sesión en rollback. | CORREGIDO Y VERIFICADO |
| ME-3 | El bloqueo pesimista del usuario en login podía esperar sin límite. | `findByEmailForUpdate` usa `jakarta.persistence.lock.timeout=2000`; no se añadió rate limiting ni dependencias adicionales. | MITIGADO; RATE LIMITING PENDIENTE |
| ME-4 | La contraseña de aprovisionamiento no tenía política explícita de longitud ni composición. | `UsuarioProvisioningService` exige al menos 12 caracteres, sin espacios y distinta del correo y del nombre; cubierto por pruebas unitarias. | CORREGIDO Y VERIFICADO |
| ME-5 | No había límite máximo de teléfonos y correos por contacto. | `@Size(max = 20)` en los DTO de alta y edición y límite de 20 filas en `app.js`; cubierto por pruebas Java y JavaScript. | CORREGIDO Y VERIFICADO |
| ME-6 | El mensaje de DNI duplicado incluía el valor y los logs podían exponer causas técnicas. | `DniDuplicadoException` usa un mensaje genérico y `GlobalExceptionHandler` registra solo el tipo de excepción traducida, sin causa ni SQL. | CORREGIDO Y VERIFICADO |

El estado `RESUELTO DOCUMENTALMENTE` indica que la decisión de diseño está documentada; no implica que exista código Java o pruebas ejecutadas.

## 12. Decisiones pendientes de aclaración o implementación

- Reconciliación automatizada de fotografías huérfanas y política de retención, como deuda técnica pendiente.
- Caducidad operativa adicional a `SPRING_SESSION_TIMEOUT=7d` y a la configuración de la cookie.
- Rate limiting de intentos de login: solo se añadió timeout al bloqueo pesimista; no se introdujeron dependencias ni una política de limitación.
- Validación interactiva en navegador del diseño responsive.
- Usuario de prueba, sin credenciales reales.
- Disponibilidad de Docker para Testcontainers no requerida en la etapa actual.

## 13. Capturas

Las capturas finales están en:

- `docs/imagenes/login.jpeg`
- `docs/imagenes/Inicio.jpeg`
- `docs/imagenes/VerContactos.jpeg`
- `docs/imagenes/RegistrarContacto.jpeg`
- `docs/imagenes/DetalleContacto.jpeg`

Se generaron a 1600 × 1600 desde el HTML real servido por la aplicación en ejecución contra `gestor_contactos_test`, con datos ficticios, y se convirtieron a JPEG. No se generaron imágenes simuladas ni se copiaron las capturas antiguas no saneadas; la carpeta raíz `imagenes/` está ignorada por Git. No se afirma verificación interactiva en navegador.
