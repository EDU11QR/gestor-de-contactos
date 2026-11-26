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
- Un contacto `AMBOS` aparece en los listados de clientes y de proveedores.

### 3.4 Identidad y unicidad

- El DNI es peruano: exactamente 8 dígitos, sin checksum.
- RUC y pasaporte quedan fuera del alcance acordado.
- El DNI es único en `contactos`.
- El email de usuario es único y se normaliza con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios y al autenticar.
- Se evitan registros duplicados mediante restricciones de unicidad en servidor y base de datos.

## 4. Validaciones y reglas de negocio

- Todo contacto debe tener nombres, apellidos, DNI, al menos un teléfono, al menos un correo electrónico y fotografía.
- El DNI debe tener 8 dígitos y ser único.
- Los formatos de teléfono y correo se validan en el servidor. El patrón exacto no está documentado y queda pendiente de aclaración.
- Se valida la presencia de datos obligatorios antes de persistir.
- Se valida y sanitiza la entrada en el servidor.
- La longitud exacta de columnas distintas de `dni` no está documentada y queda pendiente de aclaración.

## 5. Fotografías

- La fotografía es obligatoria.
- Formatos aceptados: JPG, JPEG y PNG.
- Tamaño máximo: 2 MB.
- Dimensiones máximas: 2000 × 2000 píxeles.
- Se valida la firma real del archivo y se decodifica con `ImageIO` / `ImageReader`.
- APNG se rechaza explícitamente.
- Los archivos se almacenan con nombres UUID en un directorio privado, fuera de las rutas públicas.
- El archivo temporal se sincroniza con `FileChannel.force(true)` antes del movimiento atómico al directorio privado.
- No se almacenan las dimensiones de la imagen en MySQL.
- El nombre de archivo es único en `contactos`.
- El contenido multimedia no se sirve desde una ruta pública de recursos estáticos.

## 6. Transacciones

- `ContactoService` es el único punto transaccional de la operación de contactos.
- `FotoService` participa con propagación `MANDATORY`.
- Antes de operar, se verifica `isActualTransactionActive()` e `isSynchronizationActive()`.
- La compensación de escritura se registra antes de escribir el archivo en disco.
- La eliminación de la foto anterior se hace dentro de la misma operación transaccional, con `SELECT … FOR UPDATE` y reconsulta antes de borrar.
- Se aplica un período de gracia de reconciliación de 24 horas para archivos huérfanos.
- El despliegue previsto es monoinstancia.

## 7. Seguridad y sesiones

- Spring Security protege las rutas privadas.
- Las contraseñas se almacenan con BCrypt.
- CSRF permanece activo.
- El logout se realiza con `POST` y CSRF, y afecta solo a la sesión actual.
- La authority es la implícita `ROLE_USER`; no se persiste ni se confunde con roles de negocio.
- El principal personalizado es `Serializable` e implementa `CredentialsContainer`, con `serialVersionUID` estable.
- El hash BCrypt no permanece en el principal después de la autenticación.
- Se verifica que el principal no serialize datos sensibles en `SPRING_SESSION_ATTRIBUTES`.
- La revocación de usuarios invalida sus sesiones por `PRINCIPAL_NAME`.
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
- La revocación invalida las sesiones del usuario por `PRINCIPAL_NAME`.
- El email se normaliza con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios y al autenticar.
- No se publica un registro público de usuarios ni credenciales reales en el repositorio.
- El usuario de prueba queda pendiente de definición.

## 9. DDL y configuración

- La convención de DDL es `db/ddl/V###__*.sql`.
- Los archivos previstos son `db/ddl/V001__dominio_inicial.sql` y `db/ddl/V002__sesiones_jdbc.sql`.
- El DDL se aplica manualmente y en orden; no se usa Flyway ni Liquibase.
- `spring.jpa.hibernate.ddl-auto=validate`.
- `dni` se mapea con `CHAR(8)` y tipo JDBC explícito o `columnDefinition`.
- Si la validación con MySQL 8.4.9 falla, se usa la alternativa acordada `VARCHAR(8)` con `CHECK`.
- La versión del servidor MySQL queda pendiente de verificación en el entorno de ejecución.

## 10. Pruebas

- Las pruebas se prevén con JUnit 5 y Mockito; este documento no afirma que estén implementadas o ejecutadas.
- Se prevén casos exitosos y fallidos de las reglas de negocio.
- Se prevé la verificación de restricciones de persistencia, incluida la unicidad de DNI y emails.
- Se prevé Testcontainers si Docker está disponible; si no, una instancia MySQL dedicada.
- Docker no estaba disponible al momento de la auditoría de entorno, por lo que su disponibilidad queda pendiente de verificación.
- H2 no se usa como sustituto de MySQL.
- Se prevén pruebas de fotografía para formato, tamaño, firma real, rechazo de APNG, transacción, durabilidad y limpieza.
- Se prevén pruebas de sesión para caducidad, cookie, CSRF, revocación y no serialización del hash en `SPRING_SESSION_ATTRIBUTES`.
- M4 contempla el manejo de `ConstraintViolationException` y `DataIntegrityViolationException` en la cadena de causas, con pruebas de integración planificadas.

## 11. Trazabilidad de hallazgos

| ID | Problema original | Solución acordada | Estado |
|---|---|---|---|
| A1 | Riesgo de aprovisionar y revocar usuarios sin idempotencia, fuera de un flujo HTTP seguro, con posible exposición de credenciales y sesiones vigentes tras la revocación. | Aprovisionamiento idempotente fuera de HTTP, secreto de entorno, contraseñas BCrypt y revocación con invalidación de sesiones por `PRINCIPAL_NAME`. | RESUELTO DOCUMENTALMENTE |
| A2 | Riesgo de cuentas duplicadas o autenticación inconsistente por emails con distinta capitalización o espacios. | Normalización del email con `trim` y `toLowerCase(Locale.ROOT)` al crear usuarios y al autenticar. | RESUELTO DOCUMENTALMENTE |
| A3 | Riesgo de persistir authorities o de confundir autoridad técnica con roles de negocio no previstos. | Authority implícita `ROLE_USER`, sin persistirla ni confundirla con roles de negocio. | RESUELTO DOCUMENTALMENTE |
| A4 | Riesgo de operación parcial de contacto y fotografía si la escritura del archivo quedaba fuera de la transacción del servicio. | `ContactoService` centraliza la transacción; `FotoService` usa `MANDATORY`; se verifican transacción activa y sincronización; la compensación se registra antes de escribir en disco. | RESUELTO DOCUMENTALMENTE |
| A5 | Riesgo de romper Spring Session o exponer el hash BCrypt al serializar el principal en la sesión. | Principal personalizado `Serializable` y `CredentialsContainer`, `serialVersionUID` estable, sin hash BCrypt tras autenticación y prueba de no serialización en `SPRING_SESSION_ATTRIBUTES`. | RESUELTO DOCUMENTALMENTE |
| M1 | Ausencia de convención para DDL versionado y riesgo de derive automático del esquema. | Convención `db/ddl/V###__*.sql`, archivos `V001__dominio_inicial.sql` y `V002__sesiones_jdbc.sql`, aplicación manual ordenada, sin Flyway/Liquibase y `ddl-auto=validate`. | RESUELTO DOCUMENTALMENTE |
| M2 | El registro de UUID en vuelo está en memoria y no se comparte entre instancias. | Documentar el despliegue monoinstancia como restricción arquitectónica, sin añadir dependencias. | RESUELTO DOCUMENTALMENTE |
| M3 | Condición de carrera al reemplazar o eliminar la fotografía de un contacto. | Bloqueo `SELECT … FOR UPDATE`, reconsulta antes de borrar, gracia de 24 horas y despliegue monoinstancia. | RESUELTO DOCUMENTALMENTE |
| M4 | Ausencia de una estrategia centralizada para traducir violaciones de validación y restricciones de integridad, incluidas causas anidadas, sin pruebas de integración previstas. | Manejo de `ConstraintViolationException` y `DataIntegrityViolationException` en la cadena de causas, con pruebas de integración. | PENDIENTE DE IMPLEMENTACIÓN |
| M5 | Posible discrepancia en el nombre real de la cookie y configuración Secure detrás de proxy TLS. | Fijar `GESTOR_SESSION` como nombre de cookie y configurar `server.forward-headers-strategy=framework` cuando exista proxy inverso TLS. | RESUELTO DOCUMENTALMENTE |
| M6 | Incompatibilidad potencial del mapeo `CHAR(8)` con Hibernate `validate` en MySQL 8.4.9. | `CHAR(8)` con mapeo JDBC explícito o `columnDefinition`, verificación en MySQL 8.4.9 y alternativa `VARCHAR(8)` con `CHECK`. | RESUELTO DOCUMENTALMENTE |
| M7 | Riesgo de pérdida del archivo de fotografía antes del movimiento atómico. | `FileChannel.force(true)` sobre el archivo temporal antes del movimiento atómico. | RESUELTO DOCUMENTALMENTE |

El estado `RESUELTO DOCUMENTALMENTE` indica que la decisión de diseño está documentada; no implica que exista código Java o pruebas ejecutadas.

## 12. Decisiones pendientes de aclaración o implementación

- Implementación de M4.
- Patrón exacto de validación de teléfonos y correos electrónicos.
- Longitudes exactas de columnas no documentadas.
- Ruta exacta del directorio privado de fotografías.
- Usuario de prueba, sin credenciales reales.
- Versión efectiva del servidor MySQL y disponibilidad de Docker para Testcontainers.
