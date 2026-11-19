package br.fatec.projetoextensao.repositories;

/**
 * INCLUÍDO (19/11/2026): número que a próxima OS vai receber, mostrado na tela Nova OS antes de salvar.
 *
 * nr_os é IDENTITY (RN-OS-08), então quem sabe o próximo valor é o banco. Por que não "maior nr_os + 1": no SQL
 * Server, um INSERT desfeito por ROLLBACK (por exemplo, OS com item inválido) consome o número mesmo assim, e a
 * conta erraria. Como em ProcedimentosBanco, quem atende no sistema é NumeracaoOsSqlServer e, nos testes no H2, a
 * imitação NumeracaoOsH2 (só em src/test).
 *
 * É só uma previsão: se outra OS for aberta antes, o número salvo é o seguinte.
 */
public interface NumeracaoOs {

    int proximoNumero();
}
