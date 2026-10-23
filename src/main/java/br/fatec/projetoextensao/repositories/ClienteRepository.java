package br.fatec.projetoextensao.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.fatec.projetoextensao.entities.ClienteEntity;
import br.fatec.projetoextensao.entities.FonteAquisicaoEntity;

@Repository
public interface ClienteRepository extends JpaRepository<ClienteEntity, Integer> {

    // INCLUÍDO (20/10/2026): RN-FON-02: fonte usada por algum cliente não é excluída.
    boolean existsByFonte(FonteAquisicaoEntity fonte);

    // INCLUÍDO (20/10/2026): RN-CLI-03: clientes com nome parecido (aviso de possível duplicidade). "PessoaNome" = campo nome da pessoa ligada.
    List<ClienteEntity> findByPessoaNomeContainingIgnoreCaseOrderByPessoaNome(String trecho);

    // INCLUÍDO (20/10/2026): Listagem em ordem alfabética.
    List<ClienteEntity> findAllByOrderByPessoaNome();

}
