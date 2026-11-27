package com.gestordecontactos;

import com.gestordecontactos.repository.ConfirmacionEliminacionRepository;
import com.gestordecontactos.repository.ContactoRepository;
import com.gestordecontactos.repository.SesionRevocacionJdbcRepository;
import com.gestordecontactos.repository.SesionRollbackJdbcRepository;
import com.gestordecontactos.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
class GestorDeContactosApplicationTests {

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private ContactoRepository contactoRepository;

    @MockitoBean
    private ConfirmacionEliminacionRepository confirmacionEliminacionRepository;

    @MockitoBean
    private SesionRevocacionJdbcRepository sesionRevocacionJdbcRepository;

    @MockitoBean
    private SesionRollbackJdbcRepository sesionRollbackJdbcRepository;

    @Test
    void contextLoads() {
    }

    @TestConfiguration
    static class ConfiguracionTransaccional {

        @Bean
        PlatformTransactionManager platformTransactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }
    }
}
