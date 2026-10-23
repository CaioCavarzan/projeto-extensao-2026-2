package br.fatec.projetoextensao.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import br.fatec.projetoextensao.config.ParametrosSistema;

/**
 * INCLUÍDO (20/10/2026): regras de acesso às rotas da API.
 *
 * RN-SIS-01: toda rota da API exige usuário autenticado e ativo, exceto as rotas públicas abaixo (ativação de conta,
 * pergunta de segurança e redefinição de senha). O login é feito por HTTP Basic: o front envia o cabeçalho
 * "Authorization: Basic base64(login:senha)" em cada requisição (sem sessão no servidor). Para "entrar", o front
 * chama GET /api/auth/me, que devolve UsuarioService.dadosDoUsuarioLogado().
 *
 * RN-SIS-02: @EnableMethodSecurity liga o @PreAuthorize dos Services (perfis ADMIN e OPERADOR).
 * RN-USU-14: senhas e respostas em BCrypt.
 *
 * ALTERADO (21/10/2026): com os Controllers, a API passou a ficar em /api/**, e o front (HTML, CSS e JavaScript da
 * pasta src/main/resources/static) é servido pela própria aplicação. Os arquivos do front são públicos, porque a
 * tela de login também é um deles; nenhum dado sai sem login, pois os dados só chegam pela API. A resposta leva
 * também cabeçalhos de proteção do navegador (Content-Security-Policy etc.), explicados em CABECALHO_CSP.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // ALTERADO (21/10/2026): a lista ROTAS_PUBLICAS (sem o prefixo /api e liberada em qualquer método HTTP) deu
    // lugar às três constantes abaixo; cada rota pública é liberada só no método que a tela usa.
    /** RN-SIS-01 e RN-USU-02: cadastre-se (ativar a conta de um funcionário já cadastrado). Só POST. */
    public static final String ROTA_ATIVAR_CONTA = "/api/auth/ativar-conta";
    /** RN-SIS-01 e RN-USU-05: lembrete de senha, passo 1 (mostrar a pergunta de segurança do login). Só GET. */
    public static final String ROTA_PERGUNTA_SEGURANCA = "/api/auth/pergunta-seguranca";
    /** RN-SIS-01 e RN-USU-05: lembrete de senha, passo 2 (resposta certa grava a nova senha). Só POST. */
    public static final String ROTA_REDEFINIR_SENHA = "/api/auth/redefinir-senha";

    /**
     * INCLUÍDO (21/10/2026): arquivos do front (pasta static: telas .html, css, js, img e vendor) e a página 404.
     * Toda requisição GET fora de /api é tratada como arquivo do front e é pública: a tela de login precisa abrir sem
     * login, e as demais telas, ao abrir, conferem o login e voltam para o login.html se não houver. Os dados de cada
     * tela vêm da API (/api/**), que continua exigindo login — por isso todo Controller fica em /api (ver
     * controllers/package-info.java).
     */
    public static final RequestMatcher ARQUIVOS_DO_FRONT = requisicao -> HttpMethod.GET.matches(requisicao.getMethod())
            && !requisicao.getRequestURI().substring(requisicao.getContextPath().length()).startsWith("/api/");

    /**
     * INCLUÍDO (21/10/2026): Content-Security-Policy. O navegador só executa scripts e estilos vindos de arquivos do
     * próprio sistema (nada embutido no HTML), e a tela só conversa com a própria API e com o ViaCEP (RN-END-03,
     * busca do endereço pelo CEP). Isso reduz o estrago de um texto malicioso gravado num cadastro (XSS): mesmo que
     * apareça na tela, ele não roda como script. "blob:" e "data:" em img-src servem às fotos dos itens, que o front
     * baixa com o login e mostra a partir da memória. frame-ancestors 'none': o sistema não abre dentro de outro site.
     */
    public static final String CABECALHO_CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data: blob:; font-src 'self'; connect-src 'self' https://viacep.com.br; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http, RespostasSeguranca respostas) throws Exception {
        http
            // API sem sessão e sem cookie: o login vai em cada requisição, então não há CSRF a proteger.
            .csrf(csrf -> csrf.disable())
            // ALTERADO (21/10/2026): a versão de 20/10 recebia o CorsConfigurationSource como parâmetro deste método.
            // O Spring MVC também registra um bean desse tipo (mvcHandlerMappingIntrospector), então havia dois
            // candidatos e a aplicação não subia (NoUniqueBeanDefinitionException). withDefaults() usa o bean pelo
            // nome "corsConfigurationSource", que é o método declarado no fim desta classe.
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // ALTERADO (21/10/2026): rotas públicas da API com o prefixo /api e presas ao método HTTP; arquivos do front
            // liberados (ver ARQUIVOS_DO_FRONT). Todo o resto, inclusive qualquer rota nova da API, exige login.
            .authorizeHttpRequests(rotas -> rotas
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, ROTA_ATIVAR_CONTA, ROTA_REDEFINIR_SENHA).permitAll()
                .requestMatchers(HttpMethod.GET, ROTA_PERGUNTA_SEGURANCA).permitAll()
                .requestMatchers(ARQUIVOS_DO_FRONT).permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            // INCLUÍDO (21/10/2026): cabeçalhos de proteção do navegador. O Spring Security já envia
            // X-Content-Type-Options e Cache-Control; aqui entram a CSP, a proibição de abrir em moldura (frame) e o
            // Referrer-Policy, que evita mandar o endereço das telas para sites de fora (ex.: o ViaCEP).
            .headers(cabecalhos -> cabecalhos
                .contentSecurityPolicy(csp -> csp.policyDirectives(CABECALHO_CSP))
                .frameOptions(moldura -> moldura.deny())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .httpBasic(basic -> basic.authenticationEntryPoint(respostas))
            .exceptionHandling(erros -> erros
                .authenticationEntryPoint(respostas)
                .accessDeniedHandler(respostas));
        return http.build();
    }

    /** RN-USU-14 e RN-USU-17: hash BCrypt da senha e da resposta de segurança. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Endereços do front que podem chamar a API pelo navegador (fenix.cors.origens no application.properties). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(ParametrosSistema parametros) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(parametros.getOrigensCors());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", config);
        return fonte;
    }
}
