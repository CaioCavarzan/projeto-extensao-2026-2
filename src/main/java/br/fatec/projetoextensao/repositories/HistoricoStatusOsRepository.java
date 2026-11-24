package br.fatec.projetoextensao.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.HistoricoStatusOsEntity;
import br.fatec.projetoextensao.entities.HistoricoStatusOsId;
import br.fatec.projetoextensao.entities.OrdemServicoEntity;

@Repository
public interface HistoricoStatusOsRepository extends JpaRepository<HistoricoStatusOsEntity, HistoricoStatusOsId> {

    Optional<HistoricoStatusOsEntity> findTopByOsOrderByDataHoraDesc(OrdemServicoEntity os);

    List<HistoricoStatusOsEntity> findByOsOrderByDataHora(OrdemServicoEntity os);

}
