ALTER TABLE usuarios
    ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT chk_usuarios_activo CHECK (activo IN (0, 1));
