# Regras de Negócio e Lógica de Domínio — FuelFinder

Este documento detalha as regras de negócio, validações de domínio, cálculos matemáticos, políticas de acesso e especificações de endpoints para a plataforma **FuelFinder**.

---

## 1. Perfis de Usuário e Controle de Acesso (RBAC)

O acesso às funcionalidades da plataforma é segregado através de papéis (Roles):

| Perfil | Código | Permissões e Escopo de Acesso |
| :--- | :--- | :--- |
| **Motorista** | `ROLE_DRIVER` | - Consultar postos de combustível e preços.<br>- Comparar valores e obter rotas.<br>- Cadastrar, editar e excluir seus próprios veículos.<br>- Registrar e editar suas próprias avaliações.<br>- Acessar histórico de abastecimentos e cálculo de consumo pessoal. |
| **Administrador** | `ROLE_ADMIN` | - Gestão total de usuários (ativar, inativar, alterar perfil).<br>- Cadastrar, atualizar e inativar postos de combustível.<br>- Cadastrar e atualizar preços de combustíveis de qualquer posto.<br>- Moderar avaliações (aprovar, rejeitar ou remover comentários). |
| **Operador de Posto** *(Evolução)* | `ROLE_STATION_OPERATOR` | - Atualizar preços de combustíveis exclusivamente do posto vinculado à sua credencial. |

### Regras de Autenticação e Conta
1. **Unicidade de E-mail**: O e-mail é o identificador único cadastral. Não são permitidas contas duplicadas com o mesmo endereço.
2. **Requisitos de Senha**: Mínimo de 8 caracteres, contendo pelo menos uma letra maiúscula, uma letra minúscula, um número e um caractere especial.
3. **Status da Conta**:
   - `ACTIVE`: Acesso irrestrito às funcionalidades do perfil.
   - `INACTIVE`: Conta desativada voluntariamente pelo usuário (pode ser reativada via suporte/login).
   - `BLOCKED`: Bloqueada administrativamente por violação de termos de uso; impede geração de novo token.
4. **Encerramento de Sessão (Logout)**: O token JWT ou refresh token é invalidado; requisições subsequentes com o token revogado retornam `401 Unauthorized`.

---

## 2. Gestão de Veículos

Os motoristas autenticados podem manter o cadastro de múltiplos veículos em seu perfil.

### 2.1 Dados Obrigatórios e Validações
- **Apelido / Identificação**: Nome amigável para o veículo (ex.: "Meu Onix", "Carro de Trabalho"). Não nulo, 2 a 50 caracteres.
- **Marca e Modelo**: Identificação da fabricante e modelo do veículo (ex.: "Chevrolet Onix", "Volkswagen Gol").
- **Ano de Fabricação**: Ano válido, maior que 1950 e menor ou igual ao ano subsequente ao corrente.
- **Tipo de Combustível Suportado**: Enum `[GASOLINE, ETHANOL, FLEX, DIESEL, CNG]`.
- **Capacidade do Tanque**: Valor numérico positivo em litros (ex.: `54.0`).
- **Consumo Médio em km/L**:
  - Para veículos `FLEX`: devem ser informados separadamente `consumoMedioGasolina` e `consumoMedioEtanol`.
  - Para veículos monoinjeção: informado o consumo respectivo ao combustível correspondente.
  - Valores válidos: entre `1.0` e `40.0` km/L.

### 2.2 Isolamento de Dados
- Um motorista **apenas** pode listar, consultar, atualizar ou excluir veículos que pertençam à sua própria conta (`user_id = token.userId`).
- Tentativas de acessar dados de veículos de terceiros resultam em `403 Forbidden`.

---

## 3. Gestão de Postos de Combustível e Localização

### 3.1 Cadastro e Manutenção (Exclusivo Administrador)
- **Dados Cadastrais**: Razão Social, Nome Fantasia, CNPJ (opcional para exibição, mas validado se informado), endereço completo e telefone de contato.
- **Coordenadas Geográficas**: Latitude (entre `-90.0` e `+90.0`) e Longitude (entre `-180.0` e `+180.0`).
- **Status do Posto**:
  - `ACTIVE`: Disponível para visualização em mapas e listagens públicas.
  - `INACTIVE`: Posto fechado ou desativado; excluído das consultas de motoristas.
  - `UNDER_MAINTENANCE`: Informação visível aos motoristas com aviso de indisponibilidade temporária.

### 3.2 Busca e Geolocalização
1. **Busca por Coordenadas**:
   - O sistema recebe `latitude`, `longitude` e opcionalmente `radiusKm` (padrão: 5 km; mínimo: 1 km; máximo: 50 km).
   - Utiliza a função espacial PostGIS `ST_DWithin` para filtrar estabelecimentos e `ST_DistanceSphere` para calcular a distância geodésica em metros/quilômetros.
2. **Fallback Manual**:
   - Caso o usuário rejeite a permissão de geolocalização no navegador, o sistema permite pesquisa por CEP, bairro ou cidade, buscando postos cuja geocodificação ou endereço cadastrado coincidam com os critérios informados.

---

## 4. Preços de Combustíveis e Comparação

### 4.1 Registro de Preços
- **Tipos de Combustível Suportados**:
  - `GASOLINE_REGULAR` (Gasolina Comum)
  - `GASOLINE_PREMIUM` (Gasolina Aditivada)
  - `ETHANOL` (Etanol Hidratado)
  - `DIESEL_S10` (Diesel S10)
  - `DIESEL_S500` (Diesel S500)
  - `CNG` (Gás Natural Veicular — medido em R$/m³)
- **Formato de Preço**: Valor monetário positivo com até 3 casas decimais (padrão adotado pelos postos de combustível no Brasil, ex.: `R$ 5,899`).
- **Histórico e Auditoria**: Cada alteração de preço gera registro com a data/hora e o identificador do usuário que realizou a atualização.
- **Preços Desatualizados**: Preços sem atualização há mais de 7 dias recebem uma flag `outdated: true`, sendo exibidos ao usuário com aviso de potencial desatualização.

---

## 5. Fórmulas de Domínio e Lógica de Cálculo

A plataforma implementa cálculos centralizados no backend para assegurar consistência e evitar divergências de regras entre clientes.

### 5.1 Estimativa de Custo para Encher o Tanque
Determina o valor financeiro aproximado para completar o reservatório do veículo:

$$\text{Custo Total} = \text{Capacidade do Tanque (L)} \times \text{Preço por Litro (R\$/L)}$$

### 5.2 Autonomia Estimada do Veículo
Projeta a distância que o automóvel pode percorrer com o tanque cheio:

$$\text{Autonomia (km)} = \text{Capacidade do Tanque (L)} \times \text{Consumo Médio (km/L)}$$

### 5.3 Custo por Quilômetro Rodado
Mede a eficiência financeira do combustível no veículo do motorista:

$$\text{Custo por KM} = \frac{\text{Preço por Litro (R\$/L)}}{\text{Consumo Médio (km/L)}}$$

### 5.4 Paridade e Recomendação Etanol vs Gasolina (Veículos Flex)

1. **Regra de Paridade Genérica (sem dados do veículo)**:
   $$\text{Índice de Paridade} = \left(\frac{\text{Preço do Etanol}}{\text{Preço da Gasolina}}\right) \times 100\%$$
   - Se $\text{Índice} \le 70\%$: Etanol é financeiramente mais vantajoso.
   - Se $\text{Índice} > 70\%$: Gasolina é financeiramente mais vantajosa.

2. **Recomendação Personalizada (quando o motorista possui veículo cadastrado)**:
   Calcula-se o custo real por quilômetro para cada combustível:
   $$\text{Custo por KM}_{\text{Etanol}} = \frac{\text{Preço Etanol}}{\text{Consumo Etanol (km/L)}}$$
   $$\text{Custo por KM}_{\text{Gasolina}} = \frac{\text{Preço Gasolina}}{\text{Consumo Gasolina (km/L)}}$$
   - Se $\text{Custo por KM}_{\text{Etanol}} < \text{Custo por KM}_{\text{Gasolina}}$: **Recomenda-se Etanol**.
   - Se $\text{Custo por KM}_{\text{Gasolina}} \le \text{Custo por KM}_{\text{Etanol}}$: **Recomenda-se Gasolina**.

### 5.5 Custo Efetivo de Deslocamento até o Posto
Evita que o motorista percorra longas distâncias para abastecer mais barato sem obter economia real:

$$\text{Custo Efetivo Total} = \text{Custo do Abastecimento} + \left(2 \times \text{Distância até o Posto (km)} \times \text{Custo por KM}\right)$$

*O fator multiplicador $2$ contabiliza o trajeto de ida e volta ao posto.*

### 5.6 Cálculo do Consumo Médio Real por Abastecimentos Consecutivos (Histórico)
Fórmula aplicada a cada abastecimento registrado com tanque completo:

$$\text{Consumo Médio (km/L)} = \frac{\text{Quilometragem Atual (km)} - \text{Quilometragem Anterior (km)}}{\text{Litros Abastecidos (L)}}$$

*Validações obrigatórias*:
- $\text{Quilometragem Atual} > \text{Quilometragem Anterior}$.
- $\text{Litros Abastecidos} > 0$.

---

## 6. Avaliações de Postos e Moderação

1. **Escala de Avaliação**: Nota inteira entre `1` (péssimo) e `5` (excelente).
2. **Comentário Textual**: Opcional, limitado a 500 caracteres, proibido conteúdo com termos ofensivos ou discurso de ódio.
3. **Limite de Avaliação**: Cada usuário autenticado pode manter no máximo **1 avaliação ativa por posto**. Ao submeter uma nova avaliação para o mesmo posto, a anterior é atualizada.
4. **Cálculo da Média do Posto**:
   $$\text{Média do Posto} = \frac{\sum_{i=1}^{N} \text{Nota}_i}{N}$$
   Onde $N$ é o número de avaliações com status `APPROVED`.
5. **Moderação de Conteúdo**:
   - Status possíveis: `PENDENTE`, `APPROVED`, `REJECTED`.
   - Administradores têm permissão para aprovar, rejeitar ou excluir avaliações com conteúdo inconsistente ou fraudulento.

---

## 7. Catálogo de Endpoints da API REST

### 7.1 Autenticação e Sessão
| Método | Endpoint | Perfil Permitido | Descrição | Status de Sucesso |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/auth/register` | Público | Cadastro de novo usuário. | `201 Created` |
| `POST` | `/auth/login` | Público | Autenticação com e-mail/senha, retorna JWT. | `200 OK` |
| `POST` | `/auth/logout` | Autenticado | Invalida a sessão ativa. | `204 No Content` |
| `POST` | `/auth/refresh` | Autenticado | Renova o token de acesso expirado. | `200 OK` |
| `GET` | `/auth/me` | Autenticado | Retorna os dados do usuário autenticado. | `200 OK` |
| `PATCH`| `/users/me` | Autenticado | Atualiza nome e dados do próprio usuário. | `200 OK` |

### 7.2 Gestão de Usuários (Administrativo)
| Método | Endpoint | Perfil Permitido | Descrição | Status de Sucesso |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/users` | `ROLE_ADMIN` | Listagem paginada de usuários. | `200 OK` |
| `GET` | `/users/{id}` | `ROLE_ADMIN` | Detalhes de um usuário específico. | `200 OK` |
| `PATCH`| `/users/{id}/role` | `ROLE_ADMIN` | Altera o perfil de acesso (`DRIVER`, `ADMIN`). | `200 OK` |
| `PATCH`| `/users/{id}/status` | `ROLE_ADMIN` | Modifica status (`ACTIVE`, `INACTIVE`, `BLOCKED`). | `200 OK` |

### 7.3 Veículos
| Método | Endpoint | Perfil Permitido | Descrição | Status de Sucesso |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/vehicles` | `ROLE_DRIVER` | Cadastra veículo para o usuário logado. | `201 Created` |
| `GET` | `/vehicles` | `ROLE_DRIVER` | Lista veículos do usuário logado. | `200 OK` |
| `GET` | `/vehicles/{id}` | `ROLE_DRIVER` | Consulta detalhes de um veículo do usuário. | `200 OK` |
| `PATCH`| `/vehicles/{id}` | `ROLE_DRIVER` | Atualiza dados cadastrais de um veículo. | `200 OK` |
| `DELETE`| `/vehicles/{id}` | `ROLE_DRIVER` | Remove veículo cadastrado. | `204 No Content` |

### 7.4 Postos de Combustível
| Método | Endpoint | Perfil Permitido | Descrição | Status de Sucesso |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/stations` | Público / Autenticado | Busca postos por geolocalização ou filtros textuais. | `200 OK` |
| `GET` | `/stations/{id}` | Público / Autenticado | Detalhes do posto, preços e nota média. | `200 OK` |
| `POST` | `/stations` | `ROLE_ADMIN` | Cadastra novo posto de combustível. | `201 Created` |
| `PATCH`| `/stations/{id}` | `ROLE_ADMIN` | Atualiza informações cadastrais do posto. | `200 OK` |
| `DELETE`| `/stations/{id}` | `ROLE_ADMIN` | Inativa ou remove o posto. | `204 No Content` |

### 7.5 Preços e Comparação
| Método | Endpoint | Perfil Permitido | Descrição | Status de Sucesso |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/stations/{id}/fuel-prices` | Público / Autenticado | Lista os preços vigentes de um posto. | `200 OK` |
| `POST` | `/stations/{id}/fuel-prices` | `ROLE_ADMIN` | Registra novo preço para um combustível. | `201 Created` |
| `PATCH`| `/stations/{id}/fuel-prices/{priceId}` | `ROLE_ADMIN` | Atualiza preço existente de um combustível. | `200 OK` |
| `GET` | `/fuel-prices/compare` | Público / Autenticado | Retorna comparativo ranqueado por preço e distância. | `200 OK` |

### 7.6 Avaliações
| Método | Endpoint | Perfil Permitido | Descrição | Status de Sucesso |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/stations/{id}/reviews` | Público / Autenticado | Lista avaliações aprovadas do posto. | `200 OK` |
| `POST` | `/stations/{id}/reviews` | `ROLE_DRIVER` | Registra avaliação com nota (1-5) e comentário. | `201 Created` |
| `PATCH`| `/reviews/{id}` | `ROLE_DRIVER` | Atualiza a própria avaliação. | `200 OK` |
| `DELETE`| `/reviews/{id}` | `ROLE_DRIVER` / `ROLE_ADMIN` | Remove avaliação (usuário autor ou admin). | `204 No Content` |

### 7.7 Recomendações Inteligentes (Spring AI)
| Método | Endpoint | Perfil Permitido | Descrição | Status de Sucesso |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/recommendations/fuel` | `ROLE_DRIVER` | Gera recomendação personalizada considerando veículo, preços, distância e parecer de IA. | `200 OK` |
