package com.joao.finance.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

// Repository é uma INTERFACE para não ser escrita a implementação
// Spring Data cria a classe sozinho ao inicializar
// JpaRepository<User, UUID> = "gerencie a entidade User, cuja chave primária é UUID"
// Já vêm prontos save, findById, findAll, deleteById, count, existsById...
public interface UserRepository extends JpaRepository<User, UUID>{
    // Query derivada pelo NOME do método: findBy + nome do campo da entidade (email)
    // O Spring gera: SELECT ... FROM users WHERE email = ?
    // Optional evita retornar null: o chamador é forçado a tratar o "não encontrado"
    Optional<User> findByEmail(String email);
    // existsBy... gera um SELECT que só confere se existe e devolve boolean
    // != findByEmail quando você só precisa saber se o e-mail já está em uso
    boolean existsByEmail(String Email);
}
