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
- Docker, opcional, para pruebas con Testcontainers.

## Variables de entorno

Copiar `.env.example` a `.env` y completar los valores locales. `.env` está ignorado por Git y no debe versionarse. No se almacenan credenciales reales en el repositorio.

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

## Creación de base de datos

Crear un esquema MySQL y aplicar manualmente los scripts DDL previstos, en este orden:

1. `db/ddl/V001__dominio_inicial.sql`
2. `db/ddl/V002__sesiones_jdbc.sql`

Los archivos SQL todavía no se han creado. La validación del esquema usa `spring.jpa.hibernate.ddl-auto=validate`.

## Ejecución

El proyecto todavía no incluye `pom.xml` ni código Java, por lo que no es ejecutable en este momento. La ejecución quedará definida cuando se cree la configuración Maven aprobada.

## Usuario de prueba

Pendiente de definición. No se publican credenciales reales en este repositorio.

## Reglas de negocio

- Los contactos pueden ser clientes, proveedores o ambos.
- Cada contacto tiene nombres, apellidos, DNI, uno o varios teléfonos, uno o varios correos y fotografía obligatoria.
- El DNI es peruano de 8 dígitos, único y sin checksum.
- Las fotografías aceptan JPG, JPEG y PNG, con límite de 2 MB y 2000 × 2000 píxeles, validación de firma real y almacenamiento privado.
- Las rutas privadas requieren autenticación, BCrypt, CSRF y sesiones persistentes con Spring Session JDBC.
- La revocación de usuarios invalida sus sesiones.

## Decisiones arquitectónicas

La arquitectura completa, el modelo de datos, la trazabilidad de hallazgos y las decisiones pendientes están documentados en [`docs/arquitectura.md`](docs/arquitectura.md).

## Pruebas

Las pruebas planificadas usan JUnit 5 y Mockito, cubren reglas de negocio, persistencia, seguridad, sesiones y fotografías. Testcontainers se usará si Docker está disponible; en caso contrario se usará una instancia MySQL dedicada. H2 no sustituye a MySQL.

Los comandos de ejecución de pruebas quedarán disponibles cuando se cree `pom.xml`.

## Uso de agentes de IA

- Las reglas generales están en `AGENTS.md`.
- El rol del arquitecto está en `.opencode/agents/architect.md`.
- El rol del revisor está en `.opencode/agents/reviewer.md`.
- El desarrollador humano revisa, comprende y aprueba todos los cambios.
