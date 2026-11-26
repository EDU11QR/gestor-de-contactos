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
- El DNI es peruano de 8 dígitos, único y sin checksum.
- Las fotografías aceptan JPG, JPEG y PNG, con límite de 2 MB y 2000 × 2000 píxeles, validación de firma real y almacenamiento privado.
- Las rutas privadas requieren autenticación, BCrypt, CSRF y sesiones persistentes con Spring Session JDBC.
- `GET /` es pública y no contiene información privada; el formulario de logout se muestra solo a usuarios autenticados.
- La revocación de usuarios está pendiente de implementación; cuando se implemente, deberá invalidar sus sesiones.

## Decisiones arquitectónicas

La arquitectura completa, el modelo de datos, la trazabilidad de hallazgos y las decisiones pendientes están documentados en [`docs/arquitectura.md`](docs/arquitectura.md).

## Pruebas

### Implementadas y ejecutadas

- Pruebas unitarias con JUnit 5 y Mockito: autenticación, normalización de correo, aprovisionamiento idempotente, borrado de credenciales y serialización del `SecurityContext`.
- Pruebas HTTP con MockMvc y `spring-security-test`: CSRF positivo y negativo, login válido e inválido, normalización del correo, logout con invalidación de sesión y bloqueo de reutilización de la sesión anterior.
- Prueba de integración real con Spring Session JDBC y MySQL 8.4.9 contra `gestor_contactos_test`: consulta `SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES`, deserializa `SPRING_SECURITY_CONTEXT`, verifica ausencia del hash BCrypt y de la contraseña en texto plano, comprueba credenciales borradas, reutiliza la sesión en una segunda petición y verifica que la fila de sesión desaparece tras logout.

### Pendientes

- Reglas de negocio y restricciones de persistencia de contactos.
- Validación de fotografías.
- Caducidad de sesión y revocación de usuarios.
- Manejo centralizado de violaciones de validación y restricciones de integridad.

H2 y Testcontainers no se usan. Para ejecutar las pruebas, exporta `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` apuntando exactamente a `gestor_contactos_test`, con los scripts `V001` y `V002` aplicados, y luego ejecuta:

```bash
mvn test
```

## Uso de agentes de IA

- Las reglas generales están en `AGENTS.md`.
- El rol del arquitecto está en `.opencode/agents/architect.md`.
- El rol del revisor está en `.opencode/agents/reviewer.md`.
- El desarrollador humano revisa, comprende y aprueba todos los cambios.
