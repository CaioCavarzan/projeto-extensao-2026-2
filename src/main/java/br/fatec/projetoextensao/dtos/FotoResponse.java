package br.fatec.projetoextensao.dtos;

/**
 * INCLUÍDO (20/10/2026). Foto do item: o banco guarda só o caminho relativo do arquivo (RN-FOT-01).
 */
public record FotoResponse(int codigo, int itemCodigo, String caminho) {
}
