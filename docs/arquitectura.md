# Arquitectura del Gestor de Contactos

## 1. Objetivo y alcance

Este documento registra las decisiones de arquitectura ya establecidas para el Gestor de Contactos. Su fuente es `AGENTS.md` y el historial de análisis de arquitectura acordado antes de la implementación. No incorpora decisiones nuevas.

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
| `usuarios` | Cuentas de acceso al sistema. | `id_usuario`, `email`, `password_hash`, `nombre`. |
| `contactos` | Datos principales del contacto. | `id_contacto`, `dni`, `tipo_contacto`, `nombres`, `apellidos`, `foto_archivo`, `foto_content_type`, `foto_tamano_bytes`. |
| `telefonos` | Teléfonos asociados a un contacto. | `id_telefono`, `id_contacto`, `numero`. |
| `correos_electronicos` | Correos asociados a un contacto. | `id_correo`, `id_contacto`, `direccion`. |

### 3.2 Tablas de sesión

- `SPRING_SESSION`.
- `SPRING_SESSION_ATTRIBUTES`.

Son gestionadas por Spring Session JDBC. No se modelan como entidades JPA del dominio y su DDL se aplica de forma manual.

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
- El email de usuario es único y se normaliza con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios y al autenticar.
- Se evitan registros duplicados mediante restricciones de unicidad en servidor y base de datos.

## 4. Validaciones y reglas de negocio

- Todo contacto debe tener nombres, apellidos, DNI, al menos un teléfono, al menos un correo electrónico y fotografía.
- El DNI debe tener 8 dígitos y ser único.
- Los formatos de teléfono y correo se validan en el servidor. El patrón de teléfono es `^(?:\+51|51)?9\d{8}$` y se normaliza a `+51XXXXXXXXX`; el correo usa `@Email`.
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

## 5. Fotografías

- La fotografía es obligatoria.
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

## 6. Transacciones

- `ContactoService` es el único punto transaccional de la operación de contactos.
- `FotoService` participa con propagación `MANDATORY`.
- Antes de operar, se verifica `isActualTransactionActive()` e `isSynchronizationActive()`.
- La compensación de escritura se registra antes de escribir el archivo en disco.
- La eliminación de la foto anterior se hace dentro de la misma operación transaccional, con `SELECT … FOR UPDATE` y reconsulta antes de borrar.
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
- La verificación real en `SPRING_SESSION_ATTRIBUTES` con Spring Session JDBC y MySQL 8.4.9 está cubierta por una prueba de integración ejecutada contra `gestor_contactos_test`.
- Cuando se implemente la revocación de usuarios, deberá invalidar sus sesiones por `PRINCIPAL_NAME`.
- El email de usuario se normaliza con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios y al autenticar.
- Las sesiones usan estos valores acordados:
  - `spring.session.store-type=jdbc`.
  - `spring.session.jdbc.table-name=SPRING_SESSION`.
  - `spring.session.jdbc.initialize-schema=never`.
  - `spring.session.timeout=7d`.
  - `server.servlet.session.cookie.name=GESTOR_SESSION`.
  - `server.servlet.session.cookie.max-age=7d`.
  - `server.servlet.session.cookie.http-only=true`.
  - `server.servlet.session.cookie.secure=true` en producción.
  - `server.servlet.session.cookie.same-site=lax`.
  - `server.forward-headers-strategy=framework` cuando exista proxy TLS.
- La caducidad de la cookie y la inactividad del servidor no son funcionalmente equivalentes.

## 8. Usuarios

- El aprovisionamiento de usuarios es idempotente y se ejecuta fuera de HTTP.
- El aprovisionamiento usa un secreto de entorno y almacena contraseñas con BCrypt.
- El aprovisionamiento solo crea usuarios; no modifica contraseñas ni nombres existentes y no incluye rotación automática en esta fase.
- Cuando se implemente la revocación, deberá invalidar las sesiones del usuario por `PRINCIPAL_NAME`.
- El email se normaliza con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios y al autenticar.
- No se publica un registro público de usuarios ni credenciales reales en el repositorio.
- No se publica un usuario de prueba con credenciales reales en el repositorio.

## 9. DDL y configuración

- La convención de DDL es `db/ddl/V###__*.sql`.
- Los archivos previstos son `db/ddl/V001__dominio_inicial.sql` y `db/ddl/V002__sesiones_jdbc.sql`.
- El DDL se aplica manualmente y en orden; no se usa Flyway ni Liquibase.
- `spring.jpa.hibernate.ddl-auto=validate`.
- `app.fotos.dir=${APP_PHOTOS_DIR}`; no se define un valor por defecto público.
- `spring.servlet.multipart.max-file-size=2MB` y `spring.servlet.multipart.max-request-size=3MB`; el límite por archivo es de 2 MB.
- Las pruebas de contactos fijan `app.fotos.dir` en la carpeta temporal del sistema y exigen `gestor_contactos_test`.
- `dni` se mapea con `CHAR(8)` y tipo JDBC explícito o `columnDefinition`.
- Si la validación con MySQL 8.4.9 falla, se usa la alternativa acordada `VARCHAR(8)` con `CHECK`.
- La versión del servidor MySQL usada en las pruebas es 8.4.9, verificada en la ejecución.

## 10. Pruebas

### Implementadas y ejecutadas

- Pruebas unitarias con JUnit 5 y Mockito: autenticación, normalización de correo, aprovisionamiento idempotente, borrado de credenciales y serialización del `SecurityContext`.
- Pruebas HTTP con MockMvc y `spring-security-test`: CSRF positivo y negativo, login válido e inválido, normalización del correo, logout con invalidación de sesión y bloqueo de reutilización de la sesión anterior.
- Prueba de integración real con Spring Session JDBC y MySQL 8.4.9 contra `gestor_contactos_test`: consulta `SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES`, deserializa `SPRING_SECURITY_CONTEXT`, verifica la ausencia del hash y de la contraseña en texto plano, comprueba que las credenciales están borradas del principal y de `Authentication`, reutiliza la sesión en una segunda petición y verifica que la fila de sesión desaparece tras logout.
- La prueba JDBC exige que el nombre exacto de la base de datos sea `gestor_contactos_test` y falla antes de conectarse si la URL falta o apunta a otra base.
- Pruebas unitarias de la Etapa 1: validación de `ContactoForm`, normalización, persistencia y traducción de DNI duplicado por restricción única en `ContactoService`, y validación real, firma, MIME, dimensiones preventivas, APNG estructural, PNG con secuencia `acTL` en metadatos, límites, transacción, rollback, fallos de escritura y compensación en `FotoService`.
- Prueba HTTP/integración de la Etapa 1 contra `gestor_contactos_test`: autenticación, CSRF positivo y negativo, alta válida con PRG y detalle, archivo en directorio privado, DNI duplicado, fotografía inválida y restricción de unicidad en base de datos.
- Pruebas HTTP reales contra el servidor embebido para el límite multipart: aceptación de una fotografía válida cercana a 2 MB, rechazo controlado por encima de 2 MB y rechazo de una solicitud sin token CSRF.
- Pruebas de la Etapa 2: unitarias de normalización y validación en `ContactoService`, de integración de `ContactoRepository.buscarContactos` contra `gestor_contactos_test` y HTTP con MockMvc para autenticación, búsqueda, filtros, paginación, resultados vacíos, detalle existente y 404 controlado.
- Pruebas de normalización de `page` y `size` no numéricos en `ContactoService` y HTTP con MockMvc.
- Pruebas de integración de `%`, `_` y `\` literales en `ContactoRepository.buscarContactos` contra `gestor_contactos_test`.
- Ejecución final: `BUILD SUCCESS`, 81 pruebas ejecutadas, 0 fallos y 0 errores.
- H2 y Testcontainers no se usan. El daemon de Docker no responde en este entorno y no hay dependencia de Testcontainers.

### Pruebas pendientes

- Ninguna para el alcance implementado en las Etapas 1 y 2.

## 11. Trazabilidad de hallazgos

| ID | Problema original | Solución acordada | Estado |
|---|---|---|---|
| A1 | Riesgo de aprovisionar y revocar usuarios sin idempotencia, fuera de un flujo HTTP seguro, con posible exposición de credenciales y sesiones vigentes tras la revocación. | Aprovisionamiento idempotente fuera de HTTP, secreto de entorno y contraseñas BCrypt implementados; revocación con invalidación de sesiones por `PRINCIPAL_NAME` pendiente de implementación. | PARCIALMENTE IMPLEMENTADO; REVOCACIÓN PENDIENTE |
| A2 | Riesgo de cuentas duplicadas o autenticación inconsistente por emails con distinta capitalización o espacios. | Normalización del email con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios y al autenticar. | RESUELTO DOCUMENTALMENTE |
| A3 | Riesgo de persistir authorities o de confundir autoridad técnica con roles de negocio no previstos. | Authority implícita `ROLE_USER`, sin persistirla ni confundirla con roles de negocio. | RESUELTO DOCUMENTALMENTE |
| A4 | Riesgo de operación parcial de contacto y fotografía si la escritura del archivo quedaba fuera de la transacción del servicio. | `ContactoService` centraliza la transacción; `FotoService` usa `MANDATORY`, verificación de transacción activa y sincronización; la compensación se registra antes de escribir en disco. | IMPLEMENTADO Y VERIFICADO PARA EL ALTA; REEMPLAZO Y ELIMINACIÓN PENDIENTES |
| A5 | Riesgo de romper Spring Session o exponer el hash BCrypt al serializar el principal en la sesión. | Principal personalizado `Serializable` y `CredentialsContainer`, `serialVersionUID` estable, sin hash BCrypt tras autenticación y prueba ejecutada de no serialización en `SPRING_SESSION_ATTRIBUTES`. | RESUELTO Y VERIFICADO |
| M1 | Ausencia de convención para DDL versionado y riesgo de derive automático del esquema. | Convención `db/ddl/V###__*.sql`, archivos `V001__dominio_inicial.sql` y `V002__sesiones_jdbc.sql`, aplicación manual ordenada, sin Flyway/Liquibase y `ddl-auto=validate`. | RESUELTO DOCUMENTALMENTE |
| M2 | El registro de UUID en vuelo está en memoria y no se comparte entre instancias. | Documentar el despliegue monoinstancia como restricción arquitectónica, sin añadir dependencias. | RESUELTO DOCUMENTALMENTE |
| M3 | Condición de carrera al reemplazar o eliminar la fotografía de un contacto. | Bloqueo `SELECT … FOR UPDATE`, reconsulta antes de borrar, despliegue monoinstancia y gracia de 24 horas como medida de contención, sin política de retención definida ni reconciliación automatizada. | RESUELTO DOCUMENTALMENTE |
| M4 | Ausencia de una estrategia centralizada para traducir violaciones de validación y restricciones de integridad, incluidas causas anidadas. | `GlobalExceptionHandler` traduce `DataIntegrityViolationException` directo y `ContactoService` traduce la unicidad de DNI a error de campo; la búsqueda genérica en cadena de causas y `ConstraintViolationException` quedan pendientes. | PARCIALMENTE IMPLEMENTADO |
| M5 | Posible discrepancia en el nombre real de la cookie y configuración Secure detrás de proxy TLS. | Fijar `GESTOR_SESSION` como nombre de cookie y configurar `server.forward-headers-strategy=framework` cuando exista proxy inverso TLS. | RESUELTO DOCUMENTALMENTE |
| M6 | Incompatibilidad potencial del mapeo `CHAR(8)` con Hibernate `validate` en MySQL 8.4.9. | `CHAR(8)` con mapeo JDBC explícito o `columnDefinition`, verificación en MySQL 8.4.9 y alternativa `VARCHAR(8)` con `CHECK`. | RESUELTO DOCUMENTALMENTE |
| M7 | Riesgo de pérdida del archivo de fotografía antes del movimiento atómico. | `FileChannel.force(true)` sobre el archivo temporal antes del movimiento atómico, cubierto por pruebas unitarias. | IMPLEMENTADO Y VERIFICADO |
| M8 | Posible exposición de mensajes técnicos del contenedor al superar el límite multipart. | El exceso vuelve al formulario de alta con un error de campo estático, sin mensajes del contenedor; cubierto por pruebas HTTP reales. | RESUELTO Y VERIFICADO |

El estado `RESUELTO DOCUMENTALMENTE` indica que la decisión de diseño está documentada; no implica que exista código Java o pruebas ejecutadas.

## 12. Decisiones pendientes de aclaración o implementación

- Implementación completa de M4: traducción genérica de causas anidadas de `DataIntegrityViolationException` y de `ConstraintViolationException`.
- Edición y eliminación de contactos.
- Reemplazo y eliminación de fotografías con las reglas M3, sin reconciliación automatizada de archivos huérfanos ni política de retención definida.
- Caducidad operativa y revocación de usuarios con invalidación de sesiones.
- Usuario de prueba, sin credenciales reales.
- Disponibilidad de Docker para Testcontainers no requerida en la etapa actual.
