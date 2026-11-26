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
- MySQL 8.4.9.
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

`APP_PHOTOS_DIR` es obligatorio para iniciar la aplicación y debe apuntar a un directorio privado del servidor. No existe valor por defecto público y su contenido no se sirve desde rutas estáticas.

Los límites HTTP de fotografías están fijados en `application.yml`: `spring.servlet.multipart.max-file-size=2MB` y `spring.servlet.multipart.max-request-size=3MB`. El límite efectivo de la fotografía es de 2 MB por archivo y coincide con la validación de `FotoService`.

## Creación de base de datos

Crear un esquema MySQL y aplicar manualmente los scripts DDL previstos, en este orden:

1. `db/ddl/V001__dominio_inicial.sql`
2. `db/ddl/V002__sesiones_jdbc.sql`

Los scripts ya están incluidos en el repositorio y deben aplicarse manualmente porque no se usa una herramienta de migraciones. La validación del esquema usa `spring.jpa.hibernate.ddl-auto=validate`.

## Ejecución

Copiar `.env.example` a `.env`, completar los valores locales, exportar las variables y ejecutar:

```bash
set -a; . ./.env; set +a
mvn spring-boot:run
```

La aplicación queda disponible en `http://localhost:8080/`. La base de datos y las tablas de sesión deben existir previamente porque `ddl-auto=validate`.

## Usuario de prueba

No hay credenciales reales en el repositorio. Para crear un usuario local, definir `APP_PROVISIONING_EMAIL`, `APP_PROVISIONING_PASSWORD` y `APP_PROVISIONING_NOMBRE` en `.env` antes de iniciar la aplicación. El aprovisionamiento normaliza el correo y guarda la contraseña con BCrypt. Si el usuario ya existe, no modifica su contraseña ni su nombre; no hay rotación automática de credenciales en esta fase.

Si las variables están vacías, la aplicación inicia sin crear usuarios. El formulario de acceso está en `http://localhost:8080/login`. El cierre de sesión se realiza mediante `POST /logout` con token CSRF.

## Reglas de negocio

- Los contactos pueden ser clientes, proveedores o ambos.
- Cada contacto tiene nombres, apellidos, DNI, uno o varios teléfonos, uno o varios correos y fotografía obligatoria.
- El DNI es peruano de 8 dígitos, único y sin checksum; la unicidad se comprueba antes de guardar y una carrera contra el `INSERT` se muestra como error de campo DNI.
- El teléfono se valida con `^(?:\+51|51)?9\d{8}$` y se normaliza a `+51XXXXXXXXX`; el correo se valida con `@Email` y se normaliza a minúsculas.
- Las fotografías aceptan JPG, JPEG y PNG, con límite de 2 MB por archivo y 2000 × 2000 píxeles, validación preventiva de dimensiones, firma real, rechazo estructural de APNG y almacenamiento privado. La solicitud completa admite hasta 3 MB; un archivo mayor a 2 MB se devuelve al formulario con un error controlado.
- El alta usa PRG: `POST /contactos` redirige a `GET /contactos/{id}`; el detalle no expone entidades JPA ni la ruta del archivo.
- Las rutas privadas requieren autenticación, BCrypt, CSRF y sesiones persistentes con Spring Session JDBC.
- `GET /` es pública y no contiene información privada; el formulario de logout se muestra solo a usuarios autenticados.
- La revocación de usuarios, cuando se implemente, deberá invalidar sus sesiones.
- El listado protegido `GET /contactos` busca de forma parcial e insensible a mayúsculas en nombres, apellidos y DNI; recorta espacios, escapa comodines y limita el término a 100 caracteres.
- Los filtros por clasificación respetan que un contacto `AMBOS` aparece en los listados de `CLIENTE` y `PROVEEDOR`, mientras que el filtro `AMBOS` muestra solo esos contactos.
- El listado pagina con tamaño por defecto 10 y valores permitidos 10, 20 y 50; una página negativa se normaliza a 0 y un tamaño inválido a 10. El orden es fijo por apellidos, nombres e id.
- El listado usa `ContactoListadoDto` y no carga teléfonos, correos ni fotografías.
- Un detalle inexistente responde HTTP 404 con una vista controlada y sin detalles internos.

## Decisiones arquitectónicas

La arquitectura completa, el modelo de datos, la trazabilidad de hallazgos y las decisiones pendientes están documentados en [`docs/arquitectura.md`](docs/arquitectura.md).

La Etapa 1 añade `ContactoController`, `ContactoService`, `FotoService`, `ContactoRepository`, DTOs de entrada/salida, `FotoProperties`, un handler mínimo de integridad y las vistas `contactos/nuevo` y `contactos/detalle`. `ContactoService` es el único punto transaccional del flujo; `FotoService` usa propagación `MANDATORY`, verifica la transacción activa y registra la compensación antes de escribir. No se añadieron dependencias.

La Etapa 2 añade el listado protegido. `ContactoBusquedaForm` transporta filtros y paginación; `ContactoService` normaliza y valida la entrada, aplica paginación y orden fijos y convierte a `ContactoListadoDto`; `ContactoRepository` ejecuta una única consulta JPQL con agrupación explícita de búsqueda y clasificación; `ContactoController` solo enlaza el formulario con el servicio. La vista `contactos/listado` muestra únicamente las columnas acordadas y conserva filtros en la paginación. Se añade una vista `contactos/no-encontrado` para 404 controlado. No se modificaron autenticación, CSRF, entidades, DDL ni dependencias.

## Funcionalidades pendientes

- Edición y eliminación de contactos.
- Reemplazo y eliminación de fotografías; la reconciliación automatizada de archivos huérfanos está pendiente y no existe una política de retención definida.
- Caducidad operativa y revocación de usuarios con invalidación de sesiones.
- M4 completo: traducción genérica de causas anidadas de `DataIntegrityViolationException` y de `ConstraintViolationException`. La unicidad de DNI ya se traduce a error de campo.

El detalle de decisiones y trazabilidad está en `docs/arquitectura.md`.

## Pruebas

### Resumen

La cobertura incluye autenticación, CSRF, sesiones JDBC, alta y detalle de contactos, validación de fotografías, listado, búsqueda, filtros, paginación, caracteres comodín literales y 404 controlado. La relación detallada de pruebas y decisiones está en [`docs/arquitectura.md`](docs/arquitectura.md).

H2 y Testcontainers no se usan. Para ejecutar las pruebas, exporta `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` apuntando exactamente a `gestor_contactos_test`, define `APP_PHOTOS_DIR` en una ruta temporal privada, deja vacías las variables de aprovisionamiento y ejecuta:

```bash
unset APP_PROVISIONING_EMAIL APP_PROVISIONING_PASSWORD APP_PROVISIONING_NOMBRE
export APP_PHOTOS_DIR="${TMPDIR:-/tmp}/gestor-contactos-test-fotos"
mvn test
```

## Uso de agentes de IA

- Las reglas generales están en `AGENTS.md`.
- El rol del arquitecto está en `.opencode/agents/architect.md`.
- El rol del revisor está en `.opencode/agents/reviewer.md`.
- El desarrollador humano revisa, comprende y aprueba todos los cambios.
