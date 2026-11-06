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

    // INCLUÍDO (20/10/2026): RN-HOS-03: status atual da OS = registro mais recente do histórico.
    Optional<HistoricoStatusOsEntity> findTopByOsOrderByDataHoraDesc(OrdemServicoEntity os);

    // INCLUÍDO (20/10/2026): Histórico completo da OS, do mais antigo para o mais novo.
    List<HistoricoStatusOsEntity> findByOsOrderByDataHora(OrdemServicoEntity os);

}
