package com.joao.finance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

    // testcontainers sobe um postgree dentro de um container temporário durante testes, descartado no final, cada execução começa um novo banco limpo
    // @TestConfiguration é a configuração que só existe em testes
    // proxyBeanMethods false é otimização padrão, a classe só declara beans sem chamar o método @Beans dentro de outro

@TestConfiguration (proxyBeanMethods = false)
public class TestcontainersConfiguration {
    // @Bean registra retorno de método como objeto gerenciado pelo spring
    // @ServiceConnection se comunida com spring boot que container é um banco, ele descobre url, usuário e senha pra usar nos valores do app.properties
    // testes não mexem no banco (porta 5433)
    // flyway roda no banco, aplicando V1__init.sql do zero

    @Bean 
    @ServiceConnection 
    PostgreSQLContainer postgreSQLContainer () {
        // versão igual ao docker-compose.yml para teste bater com ambiente real
        return new PostgreSQLContainer("postgres:16");
    }
}
