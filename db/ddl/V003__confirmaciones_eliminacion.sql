CREATE TABLE confirmaciones_eliminacion (
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    id_contacto BIGINT UNSIGNED NOT NULL,
    creado_en DATETIME(3) NOT NULL,
    expira_en DATETIME(3) NOT NULL,
    consumido_en DATETIME(3) NULL,

    CONSTRAINT pk_confirmaciones_eliminacion PRIMARY KEY (session_id),
    KEY ix_confirmaciones_token_hash (token_hash),
    KEY ix_confirmaciones_contacto (id_contacto),
    KEY ix_confirmaciones_expira_en (expira_en),
    CONSTRAINT chk_confirmaciones_expiracion CHECK (expira_en > creado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
