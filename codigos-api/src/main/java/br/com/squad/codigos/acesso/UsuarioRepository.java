package br.com.squad.codigos.acesso;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByNomeIgnoreCase(String nome);

    Optional<Usuario> findByCredencialId(String credencialId);
}
