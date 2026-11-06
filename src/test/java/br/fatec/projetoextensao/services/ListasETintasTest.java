package br.fatec.projetoextensao.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import br.fatec.projetoextensao.dtos.AjusteEstoqueRequest;
import br.fatec.projetoextensao.dtos.ClientePFRequest;
import br.fatec.projetoextensao.dtos.FornecedorResponse;
import br.fatec.projetoextensao.dtos.ItemListaResponse;
import br.fatec.projetoextensao.dtos.NomeRequest;
import br.fatec.projetoextensao.dtos.TintaRequest;
import br.fatec.projetoextensao.dtos.TintaResponse;
import br.fatec.projetoextensao.enums.StatusItemEnum;
import br.fatec.projetoextensao.enums.StatusOsEnum;
import br.fatec.projetoextensao.exceptions.RegraNegocioException;

/** INCLUÍDO (20/10/2026): listas de apoio (status, fontes, cores, etapas) e tintas (RN-SOS, RN-SIT, RN-FON, RN-COR, RN-ROT, RN-TIN). */
class ListasETintasTest extends TesteBase {

    /** RN-SOS-01, RN-SIT-01, RN-FON-01 e RN-ROT-01: as listas fixas existem no banco ao iniciar a aplicação. */
    @Test
    void rnSos01_rnSit01_listasFixasExistem() {
        for (StatusOsEnum s : StatusOsEnum.values()) {
            assertThat(contem(statusService.listarStatusOs(), s.getNome())).as(s.getNome()).isTrue();
        }
        for (StatusItemEnum s : StatusItemEnum.values()) {
            assertThat(contem(statusService.listarStatusItens(), s.getNome())).as(s.getNome()).isTrue();
        }
        assertThat(contem(fonteAquisicaoService.listar(), FonteAquisicaoService.NAO_INFORMADA)).isTrue();
        assertThat(contem(rotaProcessoService.listar(), RotaProcessoService.PINTURA)).isTrue();
    }

    /** RN-FON-01: a fonte 'Não informada' não é excluída nem renomeada. */
    @Test
    void rnFon01_naoInformadaProtegida() {
        int codigo = fonteAquisicaoRepository.findByDescricaoIgnoreCase(FonteAquisicaoService.NAO_INFORMADA).orElseThrow().getCodigo();
        assertThatThrownBy(() -> fonteAquisicaoService.excluir(codigo))
                .hasMessage("Esta fonte é usada pelo sistema e não pode ser alterada.");
        assertThatThrownBy(() -> fonteAquisicaoService.renomear(codigo, new NomeRequest("Outra")))
                .hasMessage("Esta fonte é usada pelo sistema e não pode ser alterada.");
    }

    /** RN-FON-02 e RN-FON-04: fonte em uso não é excluída; descrição repetida é recusada. */
    @Test
    void rnFon02_rnFon04_fonteEmUsoERepetida() {
        ItemListaResponse google = fonteAquisicaoService.cadastrar(new NomeRequest("Google Teste"));
        assertThatThrownBy(() -> fonteAquisicaoService.cadastrar(new NomeRequest("google teste")))
                .hasMessage("Esta fonte de aquisição já existe.");
        clienteService.cadastrarPF(new ClientePFRequest(pessoa("Veio do Google"), novoCpf(), null, google.codigo(), null));
        assertThatThrownBy(() -> fonteAquisicaoService.excluir(google.codigo()))
                .hasMessage("Fonte em uso por clientes não pode ser excluída.");
    }

    /** RN-COR-03: nome repetido (sem diferenciar maiúsculas). RN-COR-01: cor usada em tinta não é excluída. */
    @Test
    void rnCor01_rnCor03_cores() {
        ItemListaResponse cor = corService.cadastrar(new NomeRequest("Verde Teste"));
        assertThatThrownBy(() -> corService.cadastrar(new NomeRequest("VERDE TESTE")))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Esta cor já está cadastrada.");
        novaTinta(cor.codigo(), null);
        assertThatThrownBy(() -> corService.excluir(cor.codigo())).hasMessage("Cor em uso não pode ser excluída.");
    }

    /** RN-ROT-01 e RN-ROT-04: etapa em uso não é excluída; nome repetido; a Pintura é protegida. */
    @Test
    void rnRot01_rnRot04_etapas() {
        assertThatThrownBy(() -> rotaProcessoService.cadastrar(new NomeRequest("pintura")))
                .hasMessage("Esta etapa já existe.");
        int pintura = rota(RotaProcessoService.PINTURA);
        assertThatThrownBy(() -> rotaProcessoService.excluir(pintura))
                .hasMessage("Esta etapa é usada pelo sistema e não pode ser alterada.");
        int jateamento = rota(RotaProcessoService.JATEAMENTO);
        abrirOs(novoClientePF("Cliente Etapa").codigo(), itemJateamento("Grade", null));
        assertThatThrownBy(() -> rotaProcessoService.excluir(jateamento)).hasMessage("Etapa em uso não pode ser excluída.");
    }

    /** RN-TIN-01: tinta sem cor. RN-TIN-17: valor negativo. */
    @Test
    void rnTin01_rnTin17_validacoes() {
        FornecedorResponse f = novoFornecedor("Fornecedor Validação", novoCnpj());
        assertThatThrownBy(() -> tintaService.cadastrar(new TintaRequest(f.codigo(), null, "Sem cor", null, null, null,
                new BigDecimal("-1"))))
                .satisfies(e -> assertThat(((RegraNegocioException) e).getMensagens()).containsExactly(
                        "Informe o fornecedor e a cor da tinta.", "Valor unitário inválido."));
    }

    /** RN-TIN-12: a mesma referência não se repete no mesmo fornecedor. */
    @Test
    void rnTin12_referenciaRepetida() {
        FornecedorResponse f = novoFornecedor("Fornecedor Referência", novoCnpj());
        int cor = novaCor("Cinza");
        tintaService.cadastrar(new TintaRequest(f.codigo(), cor, "Cinza 1", null, null, "REF-9", new BigDecimal("10")));
        assertThatThrownBy(() -> tintaService.cadastrar(new TintaRequest(f.codigo(), cor, "Cinza 2", null, null, "ref-9",
                new BigDecimal("10"))))
                .hasMessage("Este fornecedor já tem tinta com essa referência.");
    }

    /** RN-TIN-13 e RN-TIN-14: estoque nasce 0, muda só por ajuste e nunca fica negativo. RN-TIN-15: kg. */
    @Test
    void rnTin13_rnTin14_rnTin15_ajusteDeEstoque() {
        TintaResponse t = novaTinta(novaCor("Branco"), null);
        assertThat(t.estoqueAtual()).isEqualByComparingTo("0");
        assertThat(t.unidadeEstoque()).isEqualTo("kg");
        t = tintaService.ajustarEstoque(t.codigo(), new AjusteEstoqueRequest(new BigDecimal("10")));
        assertThat(t.estoqueAtual()).isEqualByComparingTo("10.00");
        int codigo = t.codigo();
        assertThatThrownBy(() -> tintaService.ajustarEstoque(codigo, new AjusteEstoqueRequest(new BigDecimal("-15"))))
                .hasMessage("Estoque insuficiente: saldo atual 10.00 kg.");
        assertThat(tintaService.ajustarEstoque(codigo, new AjusteEstoqueRequest(new BigDecimal("-4"))).estoqueAtual())
                .isEqualByComparingTo("6.00");
    }

    /** RN-TIN-18: tinta com valor 0 aparece como preço a confirmar. */
    @Test
    void rnTin18_precoAConfirmar() {
        FornecedorResponse f = novoFornecedor("Fornecedor Preço", novoCnpj());
        TintaResponse t = tintaService.cadastrar(new TintaRequest(f.codigo(), novaCor("Preta"), "Sem preço", null,
                null, null, BigDecimal.ZERO));
        assertThat(t.precoAConfirmar()).isTrue();
    }

    /** RN-TIN-02: tinta usada em item não é excluída. */
    @Test
    void rnTin02_tintaEmUso() {
        int cor = novaCor("Amarela");
        TintaResponse t = novaTinta(cor, null);
        abrirOs(novoClientePF("Cliente Tinta").codigo(), itemPintura("Portão", cor, t.codigo(), null));
        assertThatThrownBy(() -> tintaService.excluir(t.codigo())).hasMessage("Tinta em uso não pode ser excluída.");
    }
}
