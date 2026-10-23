package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.RotaProcessoEntity;

@Repository
public interface RotaProcessoRepository extends JpaRepository<RotaProcessoEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-ROT-01 e RN-ITE-05: localizar a etapa pelo nome (ex.: Pintura).
    Optional<RotaProcessoEntity> findByNomeIgnoreCase(String nome);

    // INCLUÍDO (20/10/2026): RN-ROT-04: nome único (UK uq_rotasprocessos_nome).
    boolean existsByNomeIgnoreCase(String nome);

    // INCLUÍDO (20/10/2026): RN-ROT-04: na renomeação, o nome pode ser o da própria etapa.
    boolean existsByNomeIgnoreCaseAndCodigoNot(String nome, int codigo);

    // INCLUÍDO (20/10/2026): Listagem na ordem de cadastro.
    List<RotaProcessoEntity> findAllByOrderByCodigo();

}
