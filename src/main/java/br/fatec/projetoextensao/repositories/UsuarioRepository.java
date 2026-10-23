package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.UsuarioEntity;

@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-USU-12 e login: o login é único sem diferenciar maiúsculas.
    Optional<UsuarioEntity> findByLoginIgnoreCase(String login);

    // INCLUÍDO (20/10/2026): RN-USU-11 e RN-USU-12: login já em uso.
    boolean existsByLoginIgnoreCase(String login);

    // INCLUÍDO (20/10/2026): RN-USU-06: quantos ADMIN ativos existem (o último não pode ser rebaixado nem bloqueado).
    long countByIsAdminTrueAndAtivoTrue();

    // INCLUÍDO (20/10/2026): Listagem de usuários (só ADMIN).
    List<UsuarioEntity> findAllByOrderByLogin();

}
