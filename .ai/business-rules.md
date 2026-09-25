# Regras de Negócio — FuelFinder

Este documento formaliza as **regras de negócio do FuelFinder**. As regras aqui descritas devem orientar diretamente a implementação das validações de entidades, serviços de domínio e comportamentos esperados do sistema.

---

## 1. Domínio de Usuários e Autenticação

### 1.1 Cadastro de Usuários
* O sistema deve permitir o autocadastro de novos usuários (motoristas).
* Campos mínimos esperados: Nome, E-mail (único no sistema) e Senha.
* As senhas devem ser obrigatoriamente armazenadas com hash criptográfico seguro antes de serem persistidas no banco de dados.

### 1.2 Autenticação e Sessão
* A autenticação é realizada através do fornecimento de credenciais válidas (e-mail e senha).
* A autenticação é estritamente **stateless**, gerando um token JWT após validação com sucesso.
* O encerramento de sessão no cliente consiste no descarte local do token JWT.

### 1.3 Perfis e Permissões (RBAC)
O controle de acesso é baseado em papéis (Role-Based Access Control). Cada usuário possui ao menos um perfil associado no sistema:

```text
[ Usuário ] ── possui ──> [ Perfil / Role ] ── determina ──> [ Permissões ]
```

---

## 2. Matriz de Perfis (RBAC)

### 2.1 Perfil: Motorista (`ROLE_MOTORISTA`)
Perfil atribuído por padrão aos usuários que se cadastram na plataforma.
* **Pode:**
  * Consultar postos de combustível e visualizar suas informações no mapa ou em lista.
  * Comparar preços de combustíveis disponíveis nos postos cadastrados.
  * Cadastrar, listar, editar e remover seus próprios veículos.
  * Informar o consumo médio de seus veículos e registrar abastecimentos para cálculo de consumo.
  * Registrar avaliações (nota e comentário opcional) sobre postos de combustível.
  * Receber recomendações personalizadas de abastecimento com base no veículo selecionado.
* **Não pode:**
  * Criar ou alterar dados cadastrais de postos.
  * Cadastrar ou alterar preços oficiais de combustíveis.
  * Cadastrar novos tipos de combustível na plataforma.
  * Excluir ou moderar avaliações de outros usuários.

### 2.2 Perfil: Administrador (`ROLE_ADMIN`)
Perfil operacional de gestão da plataforma FuelFinder.
* **Pode:**
  * Gerenciar postos de combustível (criar, editar dados, ativar ou desativar postos).
  * Gerenciar preços de combustíveis (cadastrar, retificar ou atualizar preços vigentes).
  * Gerenciar tipos de combustível disponíveis no sistema (ex.: Gasolina Comum, Etanol, Diesel S10, etc.).
  * Moderar avaliações registradas por motoristas (ocultar ou remover comentários impróprios).
  * Administrar cadastros e parâmetros globais da plataforma.

### 2.3 Perfil: Operador de Posto (`ROLE_OPERADOR`)
```text
POSSIBILIDADE FUTURA / NÃO OBRIGATÓRIO NO MVP
- O perfil de operador/gerente de posto (com permissão restrita para atualizar preços exclusivamente do seu posto) é reconhecido como evolução futura da plataforma.
- Não deve ser tratado como requisito obrigatório do MVP inicial.
```

---

## 3. Domínio de Veículos

### 3.1 Dados do Veículo
O cadastro de veículo é vinculado ao usuário motorista autenticado e prevê os seguintes dados:
* **Identificação / Apelido:** Nome amigável dado pelo motorista (ex.: "Carro da Família", "Carro de Trabalho").
* **Marca:** Fabricante do veículo (ex.: Volkswagen, Chevrolet, Fiat).
* **Modelo:** Modelo específico (ex.: Gol 1.0, Onix, Strada).
* **Ano:** Ano de fabricação / modelo do veículo.
* **Combustível:** Tipo(s) de combustível aceito(s) pelo motor (ex.: Flex - Gasolina/Etanol, Apenas Gasolina, Apenas Diesel).
* **Capacidade do Tanque:** Volume máximo do reservatório em litros (deve ser um valor numérico estritamente positivo).
* **Consumo Médio Informado pelo Usuário:** Valor numérico em quilômetros por litro (km/L) informado diretamente pelo condutor.

### 3.2 Fonte dos Dados de Consumo
* **Regra Fundamental:** O sistema deve utilizar inicialmente o **consumo informado pelo próprio usuário**.
* **Restrição de Implementação:** O sistema **não deve assumir** que haverá integração ou consulta automática a bases de dados externas ou tabelas de fabricantes (ex.: Inmetro) no MVP.

---

## 4. Domínio de Consumo

Além do consumo médio fixo informado pelo condutor no cadastro do veículo, o sistema prevê o cálculo do consumo médio real obtido a partir de registros de abastecimento.

### 4.1 Fórmula de Cálculo
```text
Consumo médio (km/L) = (quilometragem atual - quilometragem anterior) / litros abastecidos
```

### 4.2 Condições Obrigatórias para Validade do Cálculo
Para que o cálculo de consumo seja matematicamente e logicamente válido, todas as seguintes condições devem ser atendidas simultaneamente:
1. **Regra do Tanque Cheio:** O abastecimento anterior e o abastecimento atual devem ter sido realizados com o método de "tanque cheio" (completar o tanque até o desarme automático da bomba). Abastecimentos parciais invalidam o cálculo de consumo exato pela fórmula direta.
2. **Quilometragem Estritamente Crescente:** A quilometragem atual do hodômetro no momento do abastecimento deve ser estritamente maior que a quilometragem registrada no abastecimento anterior (`quilometragem_atual > quilometragem_anterior`).
3. **Volume de Combustível Positivo:** A quantidade de litros abastecidos deve ser um número real estritamente maior que zero (`litros_abastecidos > 0`).
4. **Mesmo Veículo:** Os registros de abastecimento anterior e atual devem obrigatoriamente pertencer ao mesmo veículo cadastrado.

---

## 5. Domínio de Preços

### 5.1 Dados do Registro de Preço
* **Tipo de Combustível:** Combustível ao qual o preço se aplica (ex.: Gasolina Comum, Gasolina Aditivada, Etanol, Diesel).
* **Posto Associado:** Identificador do posto de combustível onde o preço é praticado.
* **Preço:** Valor monetário por litro (R$/L), com precisão mínima de duas a três casas decimais.
* **Data e Hora de Atualização:** Timestamp indicando o momento exato em que o preço foi registrado ou retificado.

### 5.2 Filtros, Comparação e Ordenação
A consulta de preços na API e na interface deve suportar:
* **Filtros:**
  * Por tipo de combustível específico.
  * Por raio geográfico em relação à posição do usuário.
  * Por posto específico.
* **Ordenação:**
  * Menor preço nominal por litro.
  * Menor distância em relação ao usuário.
  * Melhor avaliação do posto.
  * Preços mais recentemente atualizados.

---

## 6. Domínio de Comparação de Preços e Custo-Benefício

A funcionalidade de comparação tem como objetivo permitir ao motorista identificar a opção mais vantajosa para abastecimento.

### 6.1 Fatores Considerados na Comparação
A lógica de comparação e cálculo de custo-benefício poderá considerar:
1. **Preço do Combustível:** Valor por litro comercializado no posto.
2. **Distância até o Posto:** Distância calculada em linha reta a partir da localização do usuário.
3. **Avaliação do Posto:** Nota média obtida pelo posto perante os motoristas.
4. **Compatibilidade de Combustível:** Combustíveis suportados pelo veículo ativo do motorista.
5. **Consumo Médio do Veículo:** Rendimento (km/L) informado pelo usuário para cada combustível compatível.
6. **Custo Estimado de Deslocamento e Abastecimento:** Estimativa financeira considerando o combustível gasto no trajeto somado ao valor do abastecimento.
7. **Custo Aproximado por Quilômetro:** Relação `(Preço por Litro) / (Consumo km/L)` expressa em R$/km.
8. **Atualização do Preço:** Recorrência temporal da última atualização do preço (priorizando postos com valores recentes e confiáveis).

> **Aviso de Implementação:** Não transformar a comparação em regras empíricas ou fórmulas rígidas não especificadas no documento de arquitetura. O sistema deve manter flexibilidade analítica baseada nos fatores acima listados.

---

## 7. Domínio de Avaliações (Reviews)

### 7.1 Dados da Avaliação
* **Usuário:** Identificador do motorista que emitiu a avaliação.
* **Posto:** Identificador do posto de combustível avaliado.
* **Nota:** Pontuação numérica (ex.: escala de 1 a 5 estrelas).
* **Comentário:** Texto opinativo opcional fornecido pelo usuário.
* **Data e Hora:** Registro temporal de criação da avaliação.

### 7.2 Regras de Negócio de Avaliações
* **Cálculo da Média:** O posto deve manter ou calcular dinamicamente a sua nota média agregada baseada em todas as avaliações ativas registradas.
* **Unicidade de Avaliação:** Recomenda-se que cada usuário possa manter apenas uma avaliação ativa por posto (podendo atualizá-la).
* **Moderação Administrativa:** Usuários com perfil `ROLE_ADMIN` podem inspecionar, ocultar ou remover avaliações cujo conteúdo viole termos de convivência ou contenha dados ofensivos.

---

## 8. Domínio de Localização e Proximidade

### 8.1 Obtenção de Localização
* O sistema deve solicitar a localização atual do dispositivo do usuário através da API do navegador, sempre mediante **autorização explícita**.
* Caso a permissão seja negada ou o dispositivo não forneça coordenadas, a aplicação deve disponibilizar **busca manual** (campo de texto para busca por bairro, cidade, endereço ou ponto de referência).

### 8.2 Parâmetros de Localização
* As coordenadas geográficas devem ser expressas em **Latitude** e **Longitude**.
* A consulta deve aceitar um **raio de busca** (ex.: em quilômetros) em torno do ponto central informado.

### 8.3 Cálculo de Distância
* A distância entre a localização de referência (usuário) e o posto deve ser calculada em **linha reta** baseando-se exclusivamente nas coordenadas geodésicas (fórmula de Haversine ou esférica).
* O FuelFinder não computa rotas viárias nem estimativas de trânsito em sua camada interna.

---

## 9. Domínio de Recomendações de Combustível

O sistema de recomendação sintetiza os dados do veículo do motorista e dos postos ao redor para apresentar a sugestão ideal de abastecimento.

### 9.1 Critérios da Recomendação
O algoritmo de recomendação pode ponderar:
* Compatibilidade do combustível com o motor do veículo.
* Consumo médio informado para o tipo de combustível avaliado.
* Preço por litro praticado no posto.
* Distância em linha reta até o posto.
* Custo aproximado por quilômetro rodado (`Preço / Consumo`).
* Data/hora da última atualização do preço registrado.

---

## 10. Leitura de Preços por Fotografia

```text
FUNCIONALIDADE FUTURA / EM AVALIAÇÃO
- A leitura automatizada de preços de combustíveis em totens ou bombas através de fotografias capturadas pelo motorista é classificada estritamente como funcionalidade futura em avaliação técnica.
- Esta funcionalidade NÃO faz parte dos requisitos obrigatórios do MVP.
- O sistema no MVP operará com inserção de preços via painel administrativo ou cadastro direto.
```

---

## 11. Decisões de Negócio em Aberto

As seguintes regras de negócio necessitam de definição em etapas futuras:

```text
DECISÃO EM ABERTO
- Política de expiração ou descarte de preços antigos (ex.: considerar preços desatualizados após quantos dias sem nova confirmação?).
- Política de colaboração de motoristas na atualização de preços (usuários comuns poderão sugerir preços ou somente administradores?).
- Frequência e limite de avaliações por usuário (quantas avaliações um mesmo usuário pode registrar em determinado período?).
- Escala exata da nota de avaliação (ex.: valores inteiros de 1 a 5 ou suporte a frações decimais).
```
