package br.fatec.projetoextensao.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * INCLUÍDO (20/10/2026): valores que as regras de negócio usam e que podem mudar sem mexer no código.
 * Os valores ficam no application.properties (chaves "fenix.*"); o número depois dos dois-pontos é o padrão,
 * usado quando a chave não existe no arquivo.
 *
 * Atenção: os valores marcados como PROVISÓRIO no application.properties vêm de regras que a planilha
 * FENIX_REGRAS_DE_NEGOCIO marca como Confirmada, mas cujo trecho entre colchetes [ ] não foi preenchido.
 * Quando a Fênix ou o grupo definir, basta trocar o valor no application.properties.
 */
@Component
public class ParametrosSistema {

    // RN-CLI-09 e RN-OS-16: escala de prioridade do cliente e da OS (definida pela Fênix em 22/10/2026: 0 a 100).
    // A do cliente é calculada pelo faturamento (PrioridadeClienteService); a da OS pode ser mudada à mão nessa escala.
    @Value("${fenix.prioridade.minima:0}")
    private int prioridadeMinima;

    @Value("${fenix.prioridade.maxima:100}")
    private int prioridadeMaxima;

    // RN-ITE-17: lista fechada de unidades do item, gravadas em maiúsculas (definida pela Fênix em 22/10/2026).
    @Value("${fenix.itens.unidades:PEÇA,CONJUNTO,KG,METRO,JOGO}")
    private List<String> unidadesItem;

    // RN-USU-08: senhas erradas seguidas antes de bloquear as tentativas do login (PROVISÓRIO: 5) e por quanto tempo.
    @Value("${fenix.login.max-tentativas:5}")
    private int maxTentativasLogin;

    @Value("${fenix.login.bloqueio-minutos:15}")
    private int minutosBloqueioLogin;

    // RN-FOT-01: pasta do servidor onde ficam os arquivos das fotos (o banco guarda só o caminho relativo).
    @Value("${fenix.fotos.diretorio:fotos}")
    private String diretorioFotos;

    // RN-FOT-02: máximo de fotos por item (decisão de 20/10: 10).
    @Value("${fenix.fotos.max-por-item:10}")
    private int maxFotosPorItem;

    // RN-FOT-06: tamanho máximo de cada foto, em MB (decisão de 20/10: 10 MB).
    @Value("${fenix.fotos.tamanho-maximo-mb:10}")
    private int tamanhoMaximoFotoMb;

    // RN-FOT-09: lado maior da foto gravada, em pixels, e qualidade do JPG (sugestão: 1920 px e 80%).
    @Value("${fenix.fotos.lado-maximo-px:1920}")
    private int ladoMaximoFotoPx;

    @Value("${fenix.fotos.qualidade-jpg:0.8}")
    private float qualidadeJpg;

    // Origens (endereços do front) que podem chamar a API pelo navegador (CORS). Ex.: o endereço do app do Lovable.
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
