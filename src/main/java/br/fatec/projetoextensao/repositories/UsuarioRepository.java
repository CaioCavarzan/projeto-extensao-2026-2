package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.UsuarioEntity;

@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Integer> {

    Optional<UsuarioEntity> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    long countByIsAdminTrueAndAtivoTrue();

    List<UsuarioEntity> findAllByOrderByLogin();

}
