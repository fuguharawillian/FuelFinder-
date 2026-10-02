# Fase 2 — Autenticação e Gestão de Usuários

## Objetivo

Implementar autenticação com access JWT de curta duração e refresh token rotativo, armazenado no servidor como hash, além de registro de motoristas, login, logout, revogação de sessões, RBAC e gestão de status de contas. O backend não cria sessões HTTP; mantém registros próprios de sessão para controle e revogação de tokens.

**Branch:** `feature/autenticacao-jwt`
**Dependência:** Fase 1 (Setup e Infraestrutura)

## Acesso às Telas e Retorno após Login

- Login e cadastro são as áreas funcionais públicas. Mapa e demais áreas internas exigem uma sessão autenticada.
- Quando uma pessoa sem sessão tenta abrir diretamente uma rota interna, encaminhá-la à tela de login e preservar somente a rota interna solicitada como destino de retorno.
- Após autenticação, continuar para o destino solicitado se a sessão e o papel do usuário autorizarem o acesso; caso contrário, exibir mensagem e encaminhar para uma área autorizada. Nunca aceitar URLs externas como destino de retorno.
- O controle de acesso do backend é obrigatório; ocultar links ou redirecionar no navegador não substitui a proteção das rotas e dados.
- **Estado atual:** o servidor serve as páginas estáticas internas sem autenticação; a UI faz redirecionamento/guarda por sessão e papel no cliente. Isso não equivale a proteger as páginas no servidor. O comportamento-alvo continua sendo proteger páginas e dados server-side.

---

## Endpoints

| Método | Rota | Objetivo | Acesso | Status HTTP |
|--------|------|----------|--------|-------------|
| `POST` | `/auth/register` | Cadastra novo motorista | Público | `201 Created` |
| `POST` | `/auth/login` | Autentica e emite access JWT e refresh token | Público | `200 OK` |
| `POST` | `/auth/logout` | Revoga a sessão autenticada atual | Autenticado | `204 No Content` |
| `POST` | `/auth/sessions/revoke-all` | Revoga todas as sessões do usuário autenticado | Autenticado | `204 No Content` |
| `POST` | `/auth/refresh` | Rotaciona refresh token e emite novos tokens | Público; exige refresh token válido | `200 OK` |
| `GET`  | `/auth/me` | Retorna dados do usuário autenticado | Autenticado | `200 OK` |
| `PATCH`| `/users/me` | Atualiza dados cadastrais próprios | Autenticado | `200 OK` |

---

## Regras de Negócio

### Registro (`POST /auth/register`)
1. O cadastro cria o usuário com papel `ROLE_DRIVER` e status `ACTIVE`
2. O e-mail deve ser **único** no banco de dados → `409 Conflict` em caso de duplicata
3. Senha armazenada como hash BCrypt com custo mínimo 12
4. **Requisitos mínimos de senha:** 8+ caracteres, ao menos uma letra maiúscula, uma letra minúscula, um número e um caractere especial
5. Campos obrigatórios: `fullName`, `email`, `password`

### Login (`POST /auth/login`)
1. Verifica e-mail e senha (BCrypt match)
2. Conta com status `BLOCKED` → `403 Forbidden` (impede login)
3. Conta com status `INACTIVE` → `403 Forbidden`
4. Cria uma sessão de autenticação no servidor
5. Gera access JWT curto com claims `sub: userId`, `role`, `sid: sessionId`, `iat` e `exp`
6. Gera refresh token opaco de uso único e persiste somente seu hash
7. Retorna `accessToken`, `tokenType: "Bearer"`, `expiresIn` e dados do usuário no JSON; o refresh token é definido somente no cookie `HttpOnly`

### Access JWT e Refresh Token
1. Algoritmo: HMAC-SHA256
2. Access token: duração curta configurável por `JWT_ACCESS_TOKEN_EXPIRATION`
3. Refresh token: duração configurável por `JWT_REFRESH_TOKEN_EXPIRATION`; guardar somente o hash no servidor e transportá-lo somente em cookie `HttpOnly`, `SameSite` e `Secure` em produção
4. Claims obrigatórias do access token: `sub` (UUID do usuário), `role`, `sid` (UUID da sessão), `iat`, `exp`
5. Cada refresh válido recebido pelo cookie é consumido e substituído por um novo cookie; reutilizar token consumido revoga a sessão associada
6. O endpoint de refresh não exige access JWT válido, somente cookie de refresh válido

### Logout (`POST /auth/logout`)
1. Revoga a sessão autenticada atual no servidor; o access JWT associado deixa de ser aceito nas próximas requisições
2. Expira o cookie de refresh com os mesmos atributos e escopo usados para criá-lo; o cliente descarta o access token em memória
3. Retorna `204 No Content`

### Revogação global e bloqueio
1. `POST /auth/sessions/revoke-all` revoga todas as sessões ativas pertencentes ao usuário autenticado
2. Bloquear conta revoga todas as suas sessões; conta bloqueada não pode fazer login ou renovar tokens
3. Cada requisição protegida valida que `sid` corresponde a uma sessão ativa e que a conta continua `ACTIVE`, negando imediatamente access tokens emitidos antes da revogação/bloqueio
4. Endpoints que usam cookies validam a origem permitida e aplicam `SameSite`; adicionar token anti-CSRF se essas medidas não forem suficientes no contexto de implantação

### Filtro de Segurança JWT
1. `OncePerRequestFilter` que intercepta todas as requisições
2. Extrai token do header `Authorization: Bearer <token>`
3. Valida assinatura, expiração, integridade e claim `sid`
4. Consulta o estado da sessão e o status do usuário; rejeita sessão revogada ou conta diferente de `ACTIVE`
5. Injeta `Authentication` no `SecurityContext`
6. Rotas públicas passam sem access token; `/auth/refresh` valida o refresh token no serviço de autenticação

---

## Tarefas de Implementação

### 2.1 Entidade `User`

```java
// com.fuelfinder.modules.user.entity.User

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role = Role.ROLE_DRIVER;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Construtores, getters e setters
}
```

### 2.2 Enums

```java
public enum Role {
    ROLE_DRIVER,
    ROLE_ADMIN
}

public enum AccountStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED
}
```

### Persistência de sessões e refresh tokens

Adicionar a migração `V3__auth_sessions_and_refresh_tokens.sql` e as entidades/repositórios `AuthSession` e `RefreshToken`. `auth_sessions` identifica o usuário e mantém `created_at`, `expires_at` e `revoked_at`. `refresh_tokens` referencia a sessão e armazena apenas `token_hash` (único), `expires_at`, `used_at`, `revoked_at` e `created_at`. Manter os hashes dos tokens consumidos até o fim da sessão para detectar reutilização; revogar a sessão ao detectar replay.

### 2.3 UserRepository

```java
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByIdAndStatus(UUID id, AccountStatus status);
}
```

### 2.4 DTOs (Records Imutáveis)

```java
// RegisterRequestDTO
public record RegisterRequestDTO(
    @NotBlank(message = "O nome completo é obrigatório")
    String fullName,

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    String email,

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
             message = "A senha deve conter uma letra maiúscula, uma minúscula, um número e um caractere especial")
    String password
) {}

// LoginRequestDTO
public record LoginRequestDTO(
    @NotBlank String email,
    @NotBlank String password
) {}

// AuthResponseDTO
public record AuthResponseDTO(
    String accessToken,
    String tokenType,
    long expiresIn,
    UserProfileDTO user
) {}

// Internal result used by the controller to set the HttpOnly cookie.
// Never serialize the refresh token into the JSON response.
public record AuthResult(AuthResponseDTO response, String refreshToken) {}

// UserProfileDTO
public record UserProfileDTO(
    UUID id,
    String fullName,
    String email,
    String role
) {}

// UpdateUserRequestDTO
public record UpdateUserRequestDTO(
    @Size(min = 2, max = 255) String fullName,
    @Email String email
) {}
```

### 2.5 JwtService

```java
// com.fuelfinder.modules.auth.service.JwtService

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpirationMs;

    public String generateToken(User user, UUID sessionId) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("sid", sessionId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUserId(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public UUID extractSessionId(String token) {
        return UUID.fromString(extractClaims(token).get("sid", String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public long getExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }
}
```

### 2.6 JwtAuthenticationFilter

O principal autenticado disponibiliza o usuário e a sessão validados pelo filtro:

```java
public record AuthPrincipal(UUID userId, UUID sessionId) {}
```

```java
// com.fuelfinder.config.JwtAuthenticationFilter

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;

    // Construtor com injeção

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (jwtService.isTokenValid(token)) {
            String userId = jwtService.extractUserId(token);
            String role = jwtService.extractRole(token);
            UUID sessionId = jwtService.extractSessionId(token);

            boolean activeSession = authSessionRepository
                .existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                    sessionId, UUID.fromString(userId), Instant.now());
            boolean activeUser = userRepository
                .existsByIdAndStatus(UUID.fromString(userId), AccountStatus.ACTIVE);

            if (!activeSession || !activeUser) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    new AuthPrincipal(UUID.fromString(userId), sessionId),
                    null,
                    List.of(new SimpleGrantedAuthority(role))
                );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
```

### 2.7 SecurityConfig

`OriginValidationFilter` deve validar a origem contra uma allowlist nos endpoints `/auth/refresh` e `/auth/logout`, que recebem cookies automaticamente. Configurar `SameSite` para o contexto de implantação e usar token anti-CSRF adicional se a validação de origem e `SameSite` não forem suficientes. Manter CSRF desativado globalmente só enquanto essa proteção específica estiver ativa e testada.

```java
// com.fuelfinder.config.SecurityConfig

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final OriginValidationFilter originValidationFilter;

    // Construtor com injeção

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/register", "/auth/login", "/auth/refresh").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/stations/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/fuel-prices/compare").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(originValidationFilter, JwtAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

### 2.8 AuthService

```java
// com.fuelfinder.modules.auth.service.AuthService

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenService refreshTokenService;

    // Construtor com injeção

    @Transactional
    public AuthResult register(RegisterRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                "Já existe um usuário cadastrado com o e-mail: " + request.email());
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.ROLE_DRIVER);
        user.setStatus(AccountStatus.ACTIVE);

        User saved = userRepository.save(user);
        UUID sessionId = UUID.randomUUID();
        authSessionRepository.save(AuthSession.create(sessionId, saved.getId()));
        String accessToken = jwtService.generateToken(saved, sessionId);
        String refreshToken = refreshTokenService.issue(sessionId);

        return new AuthResult(
            new AuthResponseDTO(
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds(),
                toProfileDTO(saved)
            ),
            refreshToken
        );
    }

    public AuthResult login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new BusinessException("Credenciais inválidas."));

        if (user.getStatus() == AccountStatus.BLOCKED) {
            throw new BusinessException("Conta bloqueada. Entre em contato com o suporte.");
        }

        if (user.getStatus() == AccountStatus.INACTIVE) {
            throw new BusinessException("Conta inativa.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("Credenciais inválidas.");
        }

        UUID sessionId = UUID.randomUUID();
        authSessionRepository.save(AuthSession.create(sessionId, user.getId()));
        String accessToken = jwtService.generateToken(user, sessionId);
        String refreshToken = refreshTokenService.issue(sessionId);

        return new AuthResult(
            new AuthResponseDTO(
                accessToken,
                "Bearer",
                jwtService.getExpirationSeconds(),
                toProfileDTO(user)
            ),
            refreshToken
        );
    }

    @Transactional
    public AuthResult refresh(String refreshToken) {
        // Consome o refresh token de forma atômica, rejeita replay e emite um novo par.
        return refreshTokenService.rotate(refreshToken);
    }

    @Transactional
    public void logout(UUID sessionId) {
        authSessionRepository.revoke(sessionId);
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        authSessionRepository.revokeAllByUserId(userId);
    }

    public UserProfileDTO getProfile(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        return toProfileDTO(user);
    }

    private UserProfileDTO toProfileDTO(User user) {
        return new UserProfileDTO(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getRole().name()
        );
    }
}
```

### 2.9 AuthController

```java
// com.fuelfinder.modules.auth.controller.AuthController

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    // Construtor

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(
            @Valid @RequestBody RegisterRequestDTO request) {
        return withRefreshCookie(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request) {
        return withRefreshCookie(authService.login(request), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logout(principal.sessionId());
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie())
            .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(
            @CookieValue("refreshToken") String refreshToken) {
        return withRefreshCookie(authService.refresh(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<Void> revokeAllSessions(
            @AuthenticationPrincipal AuthPrincipal principal) {
        authService.revokeAllSessions(principal.userId());
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie())
            .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(authService.getProfile(principal.userId()));
    }

    private ResponseEntity<AuthResponseDTO> withRefreshCookie(AuthResult result, HttpStatus status) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", result.refreshToken())
            .httpOnly(true)
            .secure(isProduction())
            .sameSite(configuredSameSitePolicy)
            .path(configuredAuthCookiePath)
            .maxAge(configuredRefreshTokenLifetime)
            .build();
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(result.response());
    }
}
```

---

## Fluxo de Autenticação

```mermaid
sequenceDiagram
    autonumber
    actor U as Motorista
    participant F as Frontend
    participant API as Backend
    participant DB as PostgreSQL

    U->>F: Preenche formulário de registro
    F->>API: POST /auth/register {fullName, email, password}
    API->>DB: Verifica se e-mail existe
    alt E-mail já cadastrado
        API-->>F: 409 Conflict
    else Novo usuário
        API->>DB: INSERT user (BCrypt hash, ROLE_DRIVER, ACTIVE)
        API->>DB: Cria sessão e persiste hash do refresh token
        API-->>F: 201 Created {accessToken, user} + Set-Cookie HttpOnly refresh token
    end
    F->>F: Mantém access token em memória; navegador guarda o cookie HttpOnly

    U->>F: Faz login
    F->>API: POST /auth/login {email, password}
    API->>DB: Busca usuário por e-mail
    alt Conta BLOCKED ou INACTIVE
        API-->>F: 403 Forbidden
    else Credenciais válidas
        API->>DB: Cria sessão e persiste hash do refresh token
        API-->>F: 200 OK {accessToken, user} + Set-Cookie HttpOnly refresh token
    end

    U->>F: Acessa rota protegida
    F->>API: GET /vehicles (Authorization: Bearer token)
    API->>API: JwtFilter valida assinatura, expiração e claims
    API->>DB: Verifica sessão ativa e conta ACTIVE
    API->>DB: Consulta dados
    API-->>F: 200 OK

    F->>API: POST /auth/refresh (cookie enviado pelo navegador)
    API->>DB: Consome refresh token e persiste hash do substituto
    API-->>F: 200 OK {accessToken novo} + Set-Cookie refresh token novo

    F->>API: POST /auth/logout (access JWT)
    API->>DB: Revoga sessão atual
    API-->>F: 204 No Content
```

---

## Testes

### Testes Unitários
- `JwtServiceTest`: geração, validação, expiração, claims
- `RefreshTokenServiceTest`: expiração, rotação de uso único, detecção de reutilização e revogação da sessão
- `AuthServiceTest`: registro com sucesso, e-mail duplicado, login correto, login/refresh com conta bloqueada, senha incorreta, logout e revogação global

### Testes de Integração
- `AuthControllerIntegrationTest` com `@SpringBootTest` + `MockMvc`:
  - Registro retorna 201
  - Registro com e-mail duplicado retorna 409
  - Login com credenciais válidas retorna 200 com access e refresh tokens
  - Refresh token aparece somente no header `Set-Cookie` com atributos de segurança e nunca no JSON
  - Refresh sem origem permitida é rejeitado; validar necessidade de token anti-CSRF adicional
  - Access token expirado e refresh token expirado são rejeitados
  - Refresh válido rotaciona o token e reutilização do token anterior revoga a sessão
  - Logout revoga a sessão e impede uso do access JWT e do refresh token daquela sessão
  - Revogação global invalida as sessões e tokens de todas as sessões do usuário
  - Login com conta BLOCKED retorna 403
  - Conta bloqueada não renova tokens e não acessa rotas protegidas com access token previamente emitido
  - Rota protegida sem token retorna 401
  - Rota `ROLE_ADMIN` acessada por `ROLE_DRIVER` retorna 403

---

## Critérios de Aceitação

- [ ] `POST /auth/register` cria usuário com `ROLE_DRIVER` e `ACTIVE`
- [ ] `POST /auth/register` com e-mail duplicado retorna `409 Conflict`
- [ ] Senha tem ao menos 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial
- [ ] Senha é armazenada como hash BCrypt com custo mínimo 12 (nunca em texto plano)
- [ ] `POST /auth/login` retorna access JWT curto com claims `sub`, `role`, `sid`, `iat`, `exp` e define refresh token no cookie `Set-Cookie`
- [ ] Refresh token é transmitido somente por cookie `HttpOnly`, nunca no JSON, em localStorage/sessionStorage, logs ou URLs
- [ ] Access token fica somente em memória; após reload é renovado usando o cookie
- [ ] Cookie usa `SameSite`, `Secure` em produção e é removido no logout; endpoints de cookie validam origem e proteção CSRF
- [ ] Somente o hash do refresh token é persistido; cada renovação invalida o token apresentado
- [ ] Reutilização de refresh token consumido revoga a sessão associada
- [ ] Logout revoga a sessão atual; revogação global revoga todas as sessões do usuário
- [ ] Sessão revogada ou conta não `ACTIVE` invalida imediatamente requests protegidos, inclusive com access JWT ainda não expirado
- [ ] Durações de access e refresh tokens são configuráveis; ambos os tipos de expiração são testados
- [ ] Conta `BLOCKED` não consegue fazer login
- [ ] Rotas protegidas rejeitam requisição sem token (`401`)
- [ ] Rotas `ROLE_ADMIN` rejeitam motorista (`403`)
- [ ] `GET /auth/me` retorna dados do usuário autenticado
- [ ] Testes unitários e de integração passam

---

## Commit Sugerido

```
feat: implement JWT authentication with registration, login and RBAC
```
