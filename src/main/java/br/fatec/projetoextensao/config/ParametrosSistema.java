package br.fatec.projetoextensao.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Valores que as regras de negócio usam e que podem mudar sem mexer no código.
 * Os valores ficam no application.properties (chaves "fenix.*"); o número depois dos dois-pontos é o padrão,
 * usado quando a chave não existe no arquivo.
 */

@Component
public class ParametrosSistema {

    // Escala de prioridade do cliente e da OS.
    // A do cliente é calculada pelo faturamento (PrioridadeClienteService); a da OS pode ser mudada à mão nessa escala.
    @Value("${fenix.prioridade.minima:0}")
    private int prioridadeMinima;

    @Value("${fenix.prioridade.maxima:100}")
    private int prioridadeMaxima;

    // Lista fechada de unidades do item, gravadas em maiúsculas.
    @Value("${fenix.itens.unidades:PEÇA,CONJUNTO,KG,METRO,JOGO}")
    private List<String> unidadesItem;

    // Senhas erradas seguidas antes de bloquear as tentativas do login e por quanto tempo.
    @Value("${fenix.login.max-tentativas:5}")
    private int maxTentativasLogin;

    @Value("${fenix.login.bloqueio-minutos:15}")
    private int minutosBloqueioLogin;

    // Pasta do servidor onde ficam os arquivos das fotos (o banco guarda só o caminho relativo).
    @Value("${fenix.fotos.diretorio:fotos}")
    private String diretorioFotos;

    // Máximo de fotos por item (decisão de 20/10: 10).
    @Value("${fenix.fotos.max-por-item:10}")
    private int maxFotosPorItem;

    // Tamanho máximo de cada foto, em MB.
    @Value("${fenix.fotos.tamanho-maximo-mb:10}")
    private int tamanhoMaximoFotoMb;

    // Lado maior da foto gravada, em pixels, e qualidade do JPG.
    @Value("${fenix.fotos.lado-maximo-px:1920}")
    private int ladoMaximoFotoPx;

    @Value("${fenix.fotos.qualidade-jpg:0.8}")
    private float qualidadeJpg;

    // Origens (endereços do front) que podem chamar a API pelo navegador (CORS).
    @Value("${fenix.cors.origens:http://localhost:5173}")
    private List<String> origensCors;

    public int getPrioridadeMinima() { return prioridadeMinima; }
    public int getPrioridadeMaxima() { return prioridadeMaxima; }
    public List<String> getUnidadesItem() { return unidadesItem; }
    public int getMaxTentativasLogin() { return maxTentativasLogin; }
    public int getMinutosBloqueioLogin() { return minutosBloqueioLogin; }
    public String getDiretorioFotos() { return diretorioFotos; }
    public int getMaxFotosPorItem() { return maxFotosPorItem; }
    public int getTamanhoMaximoFotoMb() { return tamanhoMaximoFotoMb; }
    public int getLadoMaximoFotoPx() { return ladoMaximoFotoPx; }
    public float getQualidadeJpg() { return qualidadeJpg; }
    public List<String> getOrigensCors() { return origensCors; }
}
