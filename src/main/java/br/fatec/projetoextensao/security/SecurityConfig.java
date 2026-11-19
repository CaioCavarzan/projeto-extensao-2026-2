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
 * Regras de acesso às rotas da API.
 *
 * Toda rota da API exige usuário autenticado e ativo, exceto as rotas públicas abaixo (ativação de conta,
 * pergunta de segurança e redefinição de senha). O login é feito por HTTP Basic: o front envia o cabeçalho
 * "Authorization: Basic base64(login:senha)" em cada requisição (sem sessão no servidor). Para "entrar", o front
 * chama GET /api/auth/me, que devolve UsuarioService.dadosDoUsuarioLogado().
 *
 * //@EnableMethodSecurity liga o @PreAuthorize dos Services (perfis ADMIN e OPERADOR).
 * senhas e respostas em BCrypt.
 *
 * Com os Controllers, a API passou a ficar em /api/**, e o front (HTML, CSS e JavaScript da
 * pasta src/main/resources/static) é servido pela própria aplicação. Os arquivos do front são públicos, porque a
 * tela de login também é um deles; nenhum dado sai sem login, pois os dados só chegam pela API. A resposta leva
 * também cabeçalhos de proteção do navegador (Content-Security-Policy etc.), explicados em CABECALHO_CSP.
 */

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    public static final String ROTA_ATIVAR_CONTA = "/api/auth/ativar-conta";
    public static final String ROTA_PERGUNTA_SEGURANCA = "/api/auth/pergunta-seguranca";
    public static final String ROTA_REDEFINIR_SENHA = "/api/auth/redefinir-senha";

    public static final RequestMatcher ARQUIVOS_DO_FRONT = requisicao -> HttpMethod.GET.matches(requisicao.getMethod())
            && !requisicao.getRequestURI().substring(requisicao.getContextPath().length()).startsWith("/api/");

    public static final String CABECALHO_CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data: blob:; font-src 'self'; connect-src 'self' https://viacep.com.br; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http, RespostasSeguranca respostas) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(rotas -> rotas
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, ROTA_ATIVAR_CONTA, ROTA_REDEFINIR_SENHA).permitAll()
                .requestMatchers(HttpMethod.GET, ROTA_PERGUNTA_SEGURANCA).permitAll()
                .requestMatchers(ARQUIVOS_DO_FRONT).permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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
