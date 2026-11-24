package br.fatec.projetoextensao.dtos;

/**
 * Foto do item: o banco guarda só o caminho relativo do arquivo.
 */

public record FotoResponse(int codigo, int itemCodigo, String caminho) {
}
