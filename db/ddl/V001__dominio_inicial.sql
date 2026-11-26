CREATE TABLE usuarios (
    id_usuario BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    CONSTRAINT pk_usuarios PRIMARY KEY (id_usuario),
    CONSTRAINT uq_usuarios_email UNIQUE (email),
    CONSTRAINT chk_usuarios_email_no_vacio CHECK (CHAR_LENGTH(TRIM(email)) > 0),
    CONSTRAINT chk_usuarios_password_hash_no_vacio CHECK (CHAR_LENGTH(password_hash) > 0),
    CONSTRAINT chk_usuarios_nombre_no_vacio CHECK (CHAR_LENGTH(TRIM(nombre)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE contactos (
    id_contacto BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dni CHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tipo_contacto VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    nombres VARCHAR(255) NOT NULL,
    apellidos VARCHAR(255) NOT NULL,
    foto_archivo VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    foto_content_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    foto_tamano_bytes INT UNSIGNED NOT NULL,
    CONSTRAINT pk_contactos PRIMARY KEY (id_contacto),
    CONSTRAINT uq_contactos_dni UNIQUE (dni),
    CONSTRAINT uq_contactos_foto_archivo UNIQUE (foto_archivo),
    CONSTRAINT chk_contactos_dni_digitos CHECK (REGEXP_LIKE(dni, _ascii'^[0-9]{8}$')),
    CONSTRAINT chk_contactos_tipo_contacto CHECK (tipo_contacto IN ('CLIENTE', 'PROVEEDOR', 'AMBOS')),
    CONSTRAINT chk_contactos_nombres_no_vacio CHECK (CHAR_LENGTH(TRIM(nombres)) > 0),
    CONSTRAINT chk_contactos_apellidos_no_vacio CHECK (CHAR_LENGTH(TRIM(apellidos)) > 0),
    CONSTRAINT chk_contactos_foto_content_type CHECK (foto_content_type IN ('image/jpeg', 'image/png')),
    CONSTRAINT chk_contactos_foto_tamano_bytes CHECK (foto_tamano_bytes BETWEEN 1 AND 2097152),
    KEY idx_contactos_tipo_contacto (tipo_contacto)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE telefonos (
    id_telefono BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_contacto BIGINT UNSIGNED NOT NULL,
    numero VARCHAR(50) NOT NULL,
    CONSTRAINT pk_telefonos PRIMARY KEY (id_telefono),
    CONSTRAINT uq_telefonos_contacto_numero UNIQUE (id_contacto, numero),
    CONSTRAINT chk_telefonos_numero_no_vacio CHECK (CHAR_LENGTH(TRIM(numero)) > 0),
    CONSTRAINT fk_telefonos_contacto FOREIGN KEY (id_contacto) REFERENCES contactos (id_contacto) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE correos_electronicos (
    id_correo BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_contacto BIGINT UNSIGNED NOT NULL,
    direccion VARCHAR(255) NOT NULL,
    CONSTRAINT pk_correos_electronicos PRIMARY KEY (id_correo),
    CONSTRAINT uq_correos_contacto_direccion UNIQUE (id_contacto, direccion),
    CONSTRAINT chk_correos_direccion_no_vacio CHECK (CHAR_LENGTH(TRIM(direccion)) > 0),
    CONSTRAINT fk_correos_contacto FOREIGN KEY (id_contacto) REFERENCES contactos (id_contacto) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
