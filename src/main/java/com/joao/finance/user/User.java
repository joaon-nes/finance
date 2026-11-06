// Pacote da classe
package com.joao.finance.user;

//jakarta.persistence traz anotações JPA (@Entity, @Id, @Column...)
import jakarta.persistence.*;
// Instant = instante absoluto no tempo, casa com TIMESTAMPTZ do banco
import java.time.Instant;
import java.util.UUID;

//Informa ao Hibernate "esta classe representa uma tabela"
@Entity
//Liga a classe a tabela users ('user' reservada no postgresql)
@Table (name="users")
public class User {
    //Espelha o CHECK (role IN ('USER', 'ADMIN')) do V1__init.sql
    //Usar User.Role.ADMIN
    public enum Role {USER, ADMIN}
    
    //@Id chave primária
    //@GenerationType.UUID = hibernate gera o UUID ao salvar. id é null antes do save()
    @Id
    @GeneratedValue (strategy = GenerationType.UUID)
    private UUID id;

    // Com ddl-auto=validate o Hibernate NÃO cria nem altera tabelas: quem garante
    // NOT NULL, tamanho e UNIQUE de verdade é o SQL do Flyway. Estes atributos
    // documentam a intenção e permitem validações do JPA, mas não substituem o banco
    @Column (nullable = false, length = 120)
    private String name;

    @Column (nullable = false, length = 160, unique = true)
    private String email;

    // O banco usa snake_case (password_hash) e o Java usa camelCase (passwordHash):
    // o "name" faz a ponte. Aqui vai o HASH da senha (BCrypt), nunca a senha em si.
    @Column (name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // STRING grava o nome do enum ('USER') no banco. Nunca use ORDINAL (0, 1):
    // reordenar o enum corromperia os dados já gravados.
    @Enumerated (EnumType.STRING)
    @Column (nullable = false, length = 20)
    private Role role = Role.USER; // valor inicial definido no campo

    // O Hibernate envia todas as colunas no INSERT. Um null explícito ignora o DEFAULT
    // do banco e quebra o NOT NULL, por isso o valor já nasce preenchido aqui.
    // updatable = false: depois de gravado, este campo nunca entra em um UPDATE.
    @Column (name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // O JPA exige um construtor sem argumentos para instanciar a entidade ao ler do banco
    // protected para o resto do código não criar um User vazio sem querer
    protected User () {}

    // Construtor que você realmente usa. Não recebe id, role nem createdAt,
    // porque o sistema define esses valores.
    public User (String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    //Getters
    public UUID getId() {return id;}
    public String getName () {return name;}
    public String getEmail () {return email;}
    public String getPasswordHash () {return passwordHash;}
    public Role getRole () {return role;}
    public Instant getCreatedAt () {return createdAt;}
}
