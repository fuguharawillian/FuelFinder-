# **Projeto: “FuelFinder”**

## Plataforma de consulta e comparação de preços de combustíveis

## **Problema / Dor do usuário**

Os preços dos combustíveis variam significativamente entre postos próximos, e muitos motoristas não possuem uma forma simples, centralizada e confiável de comparar valores antes de abastecer. Como resultado, podem pagar mais caro desnecessariamente ou escolher um posto apenas pela proximidade, sem considerar preço, qualidade percebida e avaliações de outros consumidores.

Além do impacto direto no orçamento, a escolha de combustíveis de baixa qualidade pode estar associada a problemas no desempenho e na conservação do veículo, aumentando o risco de danos, manutenções não planejadas e prejuízos financeiros relevantes ao longo do tempo.

Atualmente, as informações sobre postos — como localização, preços, promoções, avaliações e qualidade percebida — costumam estar dispersas, desatualizadas ou serem difíceis de comparar rapidamente durante o deslocamento do motorista.

## **Solução Proposta:**

O FuelFinder será uma plataforma web responsiva desenvolvida para auxiliar motoristas na busca por postos de combustível, na consulta de informações e na comparação de preços de forma rápida, prática e segura.

Com base na geolocalização do usuário, mediante autorização, o sistema exibirá os postos de combustível próximos em um mapa interativo e em uma lista organizada. Dessa forma, o motorista poderá identificar opções mais convenientes antes de se deslocar para abastecer.

As informações sobre postos e preços de combustíveis serão obtidas prioritariamente a partir da Série Histórica de Preços de Combustíveis e de GLP, disponibilizada em dados abertos pela Agência Nacional do Petróleo, Gás Natural e Biocombustíveis (ANP). Essa base é formada a partir de pesquisas de preços realizadas periodicamente em estabelecimentos revendedores.

No MVP, o sistema deverá utilizar o arquivo semestral mais recente disponibilizado pela ANP — inicialmente, o arquivo referente ao 1º semestre de 2026, enquanto este permanecer como o período mais atual publicado na fonte oficial. A solução deverá realizar o download do arquivo, validar sua estrutura, tratar os dados necessários e importá-los para uma base de dados própria da plataforma.

O processo de integração deverá permitir a atualização dos registros sempre que a ANP disponibilizar um novo arquivo compatível com a periodicidade definida para o projeto. Cada importação deverá registrar informações de rastreabilidade, incluindo a fonte dos dados, o período de referência do arquivo, a data de importação e o status do processamento.

A aplicação deverá armazenar, quando disponíveis na base oficial, informações como região, estado, município, identificação da revenda, CNPJ, endereço, bairro, CEP, produto, data de coleta, valor de venda, unidade de medida e bandeira do posto. As informações geográficas necessárias para a exibição em mapa poderão ser obtidas ou complementadas por meio de processo de geocodificação de endereços, quando aplicável.

É importante destacar que os valores apresentados pelo FuelFinder terão caráter informativo e histórico, pois dependerão da data de coleta e da periodicidade de publicação dos arquivos da ANP. Portanto, a plataforma não terá como objetivo apresentar preços em tempo real praticados no momento do abastecimento, mas oferecer uma referência confiável para comparação e apoio à decisão do usuário.

Além da comparação de preços, o FuelFinder buscará apoiar uma decisão de abastecimento mais consciente. Os usuários poderão cadastrar seus veículos e receber estimativas personalizadas, considerando as características informadas sobre o automóvel, os combustíveis compatíveis e os valores disponíveis na região pesquisada.

Assim, a solução pretende reduzir o tempo gasto na busca por postos, facilitar a economia no abastecimento e oferecer maior transparência ao motorista na escolha de onde abastecer, contribuindo para decisões mais seguras e financeiramente vantajosas.

**Funcionalidades Principais:**

Para garantir a viabilidade da primeira versão do FuelFinder, as funcionalidades foram divididas entre o escopo inicial de implementação (MVP) e as evoluções futuras. Essa separação permite entregar uma solução funcional para consulta e comparação de preços, mantendo a arquitetura preparada para expansões posteriores.

No escopo do MVP, o sistema deverá incluir um processo de integração com os arquivos públicos disponibilizados pela ANP. Esse processo será responsável por identificar o arquivo mais recente definido para importação, realizar seu download, validar sua estrutura, tratar os dados relevantes e carregar as informações na base de dados da plataforma.

A atualização dos dados deverá ocorrer de acordo com a disponibilidade de novos arquivos na fonte oficial e com a periodicidade estabelecida pela administração do sistema. Caso uma importação apresente erro de estrutura, inconsistência ou falha no processamento, o sistema deverá registrar a ocorrência e preservar os últimos dados válidos disponíveis para consulta.

A partir dos dados importados, os usuários poderão consultar postos de combustível, visualizar sua localização, verificar os preços registrados para cada tipo de combustível e comparar as opções disponíveis na região pesquisada.

### **Escopo Inicial**

### **Integração com a base de dados da ANP**

O sistema deverá integrar-se aos arquivos públicos da Série Histórica de Preços de Combustíveis e de GLP, disponibilizados pela Agência Nacional do Petróleo, Gás Natural e Biocombustíveis (ANP), por meio do portal oficial de dados abertos:

**Fonte Oficial:** https://www.gov.br/anp/pt-br/centrais-de-conteudo/dados-abertos/serie-historica-de-precos-de-combustiveis

A integração terá como finalidade importar e disponibilizar, na plataforma FuelFinder, informações públicas sobre preços de combustíveis coletadas pela ANP em postos revendedores. Os dados terão caráter informativo e histórico, não representando necessariamente os preços praticados em tempo real no momento da consulta ou do abastecimento.

A integração deverá contemplar, no mínimo, os seguintes requisitos:

* identificação do arquivo mais recente definido para carga no sistema, conforme a disponibilidade da ANP;  
* realização do download do arquivo disponibilizado na fonte oficial;  
* validação do formato do arquivo e da presença dos campos necessários para a importação;  
* tratamento, limpeza e padronização dos dados recebidos;  
* armazenamento das informações relevantes em uma base de dados própria da plataforma;  
* prevenção de registros duplicados em casos de reimportação do mesmo arquivo ou período;  
* registro da data e hora da importação, do período de referência dos dados, da origem do arquivo e do resultado do processamento;  
* disponibilização dos dados importados para consulta, filtros e comparação de preços na plataforma;  
* manutenção dos últimos dados válidos importados caso ocorra falha na obtenção ou no processamento de uma nova atualização.

Os dados importados deverão preservar, sempre que disponível, informações como região, unidade federativa, município, identificação da revenda, endereço, bairro, CEP, produto, data da coleta, valor de venda, unidade de medida e bandeira do posto.

A data de coleta informada pela ANP deverá ser armazenada e exibida ao usuário junto aos preços consultados, permitindo a identificação clara do período de referência de cada valor apresentado.

#### **Autenticação e gerenciamento de sessão**

O sistema deverá permitir o cadastro e a autenticação de usuários por meio de e-mail e senha. Após a validação das credenciais, será criada uma sessão autenticada para acesso às funcionalidades restritas da plataforma.

Também deverá ser disponibilizada a funcionalidade de encerramento de sessão (*logout*), invalidando o acesso ativo do usuário no dispositivo utilizado.

#### **Controle de acesso baseado em perfi**l

O acesso às funcionalidades será controlado conforme o perfil do usuário, utilizando o modelo de Controle de Acesso Baseado em Papéis (*Role-Based Access Control — RBAC*).

Inicialmente, serão considerados os seguintes perfis:

* Motorista: poderá consultar postos, comparar preços, cadastrar veículos e registrar avaliações.  
* Administrador: será responsável pela gestão de cadastros, manutenção das informações dos postos e moderação de avaliações ou dados inseridos na plataforma.

Essa estrutura permite restringir funcionalidades administrativas e preservar a integridade das informações apresentadas aos usuários.

#### **Localização e consulta de postos de combustível**

O sistema deverá utilizar a geolocalização do dispositivo, mediante autorização do usuário, para identificar sua posição aproximada e apresentar os postos de combustível mais próximos.

Os resultados deverão ser exibidos em duas visualizações:

* Mapa interativo, com marcadores representando os postos localizados;  
* Listagem ordenada, contendo nome do posto, endereço, distância estimada e preços disponíveis.

Caso o usuário não autorize o acesso à localização, o sistema deverá permitir a busca manual por endereço, bairro, cidade ou CEP.

####  **Consulta e comparação de preços de combustíveis**

O FuelFinder deverá permitir a consulta dos preços praticados pelos postos cadastrados para diferentes tipos de combustível, como gasolina, etanol, diesel e gás natural veicular, quando aplicável.

O usuário poderá filtrar e ordenar os resultados com base em critérios como:

* tipo de combustível;  
* menor preço;  
* menor distância;  
* avaliação média do posto;  
* faixa de preço;  
* localização pesquisada.

A funcionalidade de comparação deverá apresentar, de forma objetiva, os valores encontrados nos postos próximos, permitindo que o motorista identifique a opção mais vantajosa antes de se deslocar para abastecer.

#### **4.1.5. Cadastro e gerenciamento de veículos**

Usuários autenticados poderão cadastrar um ou mais veículos em seu perfil. Cada veículo deverá conter informações relevantes para o cálculo de consumo e estimativas de custo, tais como:

* identificação ou apelido do veículo;  
* marca e modelo;  
* ano de fabricação;  
* tipo de combustível utilizado;  
* capacidade aproximada do tanque;  
* média de consumo, em quilômetros por litro.

Com base nos dados cadastrados e nos preços disponíveis, o sistema poderá apresentar estimativas, como:

* custo aproximado para abastecimento completo;  
* custo estimado por quilômetro rodado;  
* autonomia aproximada do veículo;  
* comparação de custo-benefício entre combustíveis compatíveis.

As estimativas serão informativas e dependerão da precisão dos dados fornecidos pelo usuário, das condições de uso do veículo e dos preços cadastrados na plataforma.

#### **4.1.6. Avaliação de postos**

Usuários autenticados poderão avaliar postos de combustível com uma classificação numérica e, quando aplicável, registrar um comentário textual.

As avaliações poderão considerar critérios como atendimento, qualidade percebida do serviço, organização e experiência geral no estabelecimento. O sistema deverá calcular e exibir a média das avaliações de cada posto, auxiliando os usuários na tomada de decisão.

Para preservar a confiabilidade da plataforma, avaliações poderão ser submetidas à moderação administrativa.

#### **4.1.7. Administração de dados dos postos**

O perfil de administrador deverá possuir recursos para gerenciar as informações disponibilizadas no sistema, incluindo:

* cadastro, edição e inativação de postos;  
* atualização de endereço, coordenadas geográficas e informações de contato;  
* cadastro e atualização de preços de combustíveis;  
* gerenciamento de tipos de combustível;  
* consulta e moderação de avaliações registradas pelos usuários.

Essa funcionalidade é essencial para assegurar que os dados apresentados no sistema permaneçam organizados, atualizados e consistentes.

### **Evoluções previstas (pós-MVP)**

As funcionalidades a seguir não fazem parte da primeira versão do sistema, mas poderão ser implementadas em versões posteriores, conforme a evolução do projeto.

#### **Atualização colaborativa de preços**

Permitir que usuários informem ou confirmem os preços observados nos postos. Essas informações poderão ser submetidas a validação automática, aprovação administrativa ou mecanismos de reputação para reduzir dados incorretos.

#### **Histórico de abastecimentos**

Disponibilizar um módulo para registro de abastecimentos realizados pelo usuário, incluindo data, quilometragem, tipo de combustível, quantidade abastecida e valor pago.

Com esses dados, o sistema poderá calcular automaticamente o consumo médio real do veículo e gerar indicadores de gasto ao longo do tempo.

#### **Notificações e alertas**

Implementar notificações para informar o usuário sobre eventos relevantes, tais como:

* redução de preço em postos próximos;  
* promoções cadastradas;  
* preços abaixo de um limite definido pelo usuário;  
* necessidade de atualização de dados ou avaliações.

#### **Favoritos e rotas personalizadas**

Permitir que usuários marquem postos como favoritos e consultem preços ao longo de rotas frequentes, como o trajeto entre residência, trabalho e faculdade.

#### **Cadastro de promoções pelos postos:**

Criar um perfil específico para representantes de postos de combustível, permitindo o gerenciamento de informações do estabelecimento, atualização de preços e publicação de promoções, sujeito à validação da administração da plataforma.

#### **Indicadores e relatórios analíticos:**

Desenvolver painéis administrativos com indicadores sobre preços médios por região, combustíveis mais consultados, postos mais avaliados, quantidade de usuários ativos e evolução dos dados cadastrados.

Esses recursos poderão apoiar a gestão da plataforma e a análise do comportamento dos usuários.

**Tipos de usuários e suas permissões:**

* Administrador   
* Usuário final

**Diagrama de arquitetura:**

* **Frontend:** HTML   
* **Backend:** Sprinboot  
* **Banco de dados:** postgre

**Endpoints da API (principais rotas REST)**

### **1\. Autenticação e gerenciamento de sessão**

Responsável pelo cadastro, autenticação de usuários e encerramento seguro de sessões.

| Método | Rotas | Descrição |
| :---: | ----- | ----- |
| `POST` | `/auth/register` | Realiza o cadastro de um novo usuário. |
| `POST` | `/auth/login` | Autentica o usuário e gera o token de acesso. |
| `POST` | `/auth/logout` | Encerra a sessão ativa, invalidando o token ou refresh token. |
| `POST` | `/auth/refresh` | Renova o token de acesso quando necessário. |
| `GET` | `/auth/me` | Retorna os dados do usuário autenticado. |
| `PATH` | `/users/me` | Permite a atualização dos dados cadastrais do próprio usuário. |

### **2\. Usuários e controle de acesso por perfil**

O sistema deverá adotar controle de acesso baseado em papéis (*Role-Based Access Control — RBAC*), restringindo operações conforme o perfil do usuário.

Perfis iniciais sugeridos:

* Motorista: consulta postos, preços, avaliações e gerencia seus veículos;  
* Administrador: gerencia cadastros de postos, preços e modera avaliações;  
* Operador de posto (opcional no MVP, caso o abastecimento dos dados seja manual pela administração): atualiza preços e informações de seu posto.

| Método | Rotas | Descrição |
| :---: | ----- | ----- |
| `GET` | `/users` | Lista usuários cadastrados. Acesso administrativo. |
| `GET` | `/users/{id}` | Consulta os dados de um usuário específico. |
| `PATCH` | `/users/{id}/role` | Altera o perfil de acesso de um usuário. Acesso administrativo. |
| `PATCH` | `/users/{id}/status` | Ativa, inativa ou bloqueia uma conta. Acesso administrativo |

### **3\. Cadastro e gerenciamento de veículos**

Permite que o motorista informe os veículos utilizados, possibilitando o cálculo de custo estimado por quilômetro e recomendações relacionadas ao consumo.

| Método | Rotas | Descrição |
| :---: | ----- | ----- |
| `POST` | `/vehicles`  | Cadastra um veículo para o usuário autenticado.  |
| `GET` | `/vehicles`  | Lista os veículos do usuário autenticado.  |
| `GET` | `/vehicles/{id}`  | Retorna os detalhes de um veículo específico.  |
| `PATH` | `/vehicles/{id}`  | Atualiza dados do veículo.  |
| `DELETE` | `/vehicles/{id}`  | Remove um veículo cadastrado.  |

### 

### Os principais dados do veículo podem incluir:

* ### marca, modelo e ano;

* ### tipo de combustível aceito;

* ### capacidade do tanque;

* ### consumo médio em quilômetros por litro;

* ### identificação opcional, como apelido ou placa parcial.

### **4\. Consulta e localização de postos de combustível**

Disponibiliza a busca geolocalizada de postos, permitindo que o usuário visualize estabelecimentos próximos à sua localização atual ou a uma região informada.

| Método | Rotas | Descrição |
| :---: | ----- | ----- |
| `GET` | `/stations`  | Lista postos, com suporte a filtros geográficos e comerciais.  |
| `GET` | `/stations/{id}`  | Retorna os detalhes de um posto específico.  |
| `POST` | `/stations`  | Cadastra um novo posto. Acesso administrativo.  |
| `PATH` | `/stations/{id}`  | Atualiza informações de um posto. Acesso administrativo.  |
| `DELETE` | `/stations/{id}`  | Remove ou inativa um posto. Acesso administrativo.  |

Principais filtros esperados:

* latitude e longitude do usuário;  
* raio máximo de busca;  
* tipo de combustível;  
* faixa de preço;  
* avaliação mínima;  
* ordenação por distância, preço ou avaliação.

### **5\. Preços de combustíveis e comparação**

### Este módulo concentra as informações de preço por tipo de combustível em cada posto. A comparação deverá considerar a localização do usuário, o combustível selecionado e, quando aplicável, os dados de consumo do veículo cadastrado.

| Método | Rotas | Descrição |
| :---: | ----- | ----- |
| `GET` | `/stations/{id}/fuel-prices`  | Lista os preços de combustíveis de um posto.  |
| `POST` | `/stations/{id}/fuel-prices`  | Registra um novo preço para um posto. Acesso administrativo ou operador autorizado.  |
| `PATH` | `/stations/{id}/fuel-prices/{priceId}`  | Atualiza um preço registrado.  |
| `GET` | `/fuel-prices/compare`  | Retorna postos e preços comparativos conforme localização e filtros.  |

A resposta dessa rota poderá apresentar, para cada posto:

* preço do combustível;  
* distância estimada;  
* data e hora da última atualização;  
* custo estimado para abastecimento;  
* custo aproximado por quilômetro, quando um veículo for informado;  
* posição no ranking de menor preço ou melhor custo-benefício.

### **6\. Avaliações de postos**

Permite que usuários autenticados avaliem postos com nota e comentário, contribuindo para uma decisão de abastecimento que considere não apenas o preço, mas também a qualidade percebida do serviço.

| Método | Rotas | Descrição |
| :---: | ----- | ----- |
| `GET` | /stations/{id}/reviews  | Lista as avaliações de um posto.  |
| `POST` | `/stations/{id}/reviews`  | Registra uma avaliação para o posto.  |
| `PATH` | `/reviews/{id}`  | Atualiza uma avaliação criada pelo próprio usuário.  |
| `DELETE` | `/reviews/{id}` | Remove uma avaliação do próprio usuário ou por moderação administrativa.  |

As avaliações deverão conter, no mínimo:

* nota em escala definida, por exemplo, de 1 a 5;  
* comentário textual opcional;  
* data de criação;  
* vínculo com o usuário autor;  
* vínculo com o posto avaliado.

### **7\. Recomendações e cálculo de custo-benefício**

Embora seja possível implementar parte desse cálculo diretamente no cliente, recomenda-se disponibilizá-lo por meio da API. Dessa forma, as regras de negócio ficam centralizadas no *backend*, facilitando manutenção, evolução e consistência dos resultados.

| Método | Rotas | Descrição |
| :---: | ----- | ----- |
| `GET` | /recommendations/fuel  | Retorna a recomendação de combustível e postos mais vantajosos para um veículo e localização.  |

A recomendação poderá utilizar informações como:

* combustível compatível com o veículo;  
* consumo médio cadastrado;  
* preço por litro;  
* distância até o posto;  
* custo estimado por quilômetro;  
* data de atualização do preço.

**Tecnologias sugeridas**

A arquitetura inicial será baseada em uma aplicação monolítica modular, expondo uma API REST, permitindo a separação lógica dos domínios de negócio sem adicionar a complexidade operacional de microsserviços no MVP. 

## **Justificativa arquitetural**

A utilização de Spring Boot permite estruturar o backend em camadas bem definidas, tais como:

* Controllers: responsáveis por expor os endpoints da API REST e receber as requisições HTTP;  
* Services: responsáveis pelas regras de negócio, validações e orquestração dos casos de uso;  
* Repositories: responsáveis pelo acesso e persistência dos dados no PostgreSQL;  
* Entities: representação das entidades do domínio persistidas no banco de dados;  
* DTOs: objetos utilizados para definir contratos de entrada e saída da API, evitando expor diretamente as entidades internas;  
* Security: configuração de autenticação, autorização, perfis de acesso e validação de tokens JWT;  
* Exception Handling: tratamento centralizado de erros, garantindo respostas padronizadas para o cliente consumidor da API.

O banco PostgreSQL será utilizado como fonte principal de dados da aplicação. Além de atender aos relacionamentos entre usuários, veículos, postos, preços e avaliações, ele permite uma evolução futura para consultas geográficas — por exemplo, a identificação de postos mais próximos da localização do usuário — utilizando extensões como o PostGIS, caso esse requisito seja incorporado em uma fase posterior.

**Fluxos principais (diagrama ou descrição textual)**

### **1\. Autenticação e controle de acesso**

1. O usuário acessa a aplicação e informa seu e-mail e senha.  
2. O sistema valida as credenciais cadastradas.  
3. Quando os dados estão corretos, o sistema gera um token de autenticação para a sessão.  
4. O usuário passa a acessar as funcionalidades permitidas conforme seu perfil de acesso.  
5. Ao encerrar a sessão, o token é invalidado e o usuário precisa realizar um novo login para acessar novamente o sistema.

### **2\. Cadastro e gerenciamento de veículo**

1. O usuário autenticado acessa a área de veículos.  
2. Informa os dados do veículo, como nome/apelido, modelo, ano, tipo de combustível e capacidade do tanque.  
3. O sistema valida e registra as informações vinculadas à conta do usuário.  
4. O usuário pode visualizar, editar ou excluir os veículos cadastrados.  
5. O veículo selecionado pode ser utilizado nos cálculos de consumo e comparação de custos de abastecimento.

### **3\. Consulta e comparação de preços de combustíveis**

1. O usuário informa ou confirma sua localização atual, podendo também selecionar uma região desejada.  
2. O sistema consulta os postos cadastrados próximos à localização informada.  
3. Os postos são exibidos com seus respectivos preços, tipos de combustível, distância e avaliações.  
4. O usuário pode aplicar filtros, como tipo de combustível, faixa de preço, distância e avaliação.  
5. O sistema ordena os resultados para facilitar a comparação, por exemplo, exibindo os menores preços ou os postos mais próximos.  
6. Ao selecionar um posto, o usuário visualiza seus detalhes e os preços disponíveis.

### **4\. Localização de postos**

1. O usuário permite o acesso à sua localização ou informa manualmente um endereço, bairro ou cidade.  
2. O sistema identifica a região de busca.  
3. Os postos disponíveis são apresentados em uma lista e/ou mapa.  
4. O usuário seleciona um posto para consultar informações detalhadas, como endereço, distância, preços cadastrados e avaliações.  
5. Caso desejado, o sistema pode direcionar o usuário para um aplicativo de mapas para traçar a rota até o posto selecionado.

### **5\. Registro de abastecimento e cálculo de consumo**

1. O usuário seleciona um veículo previamente cadastrado.  
2. Informa os dados do abastecimento, como data, quilometragem atual, quantidade de litros, valor total pago e combustível utilizado.  
3. O sistema registra o abastecimento no histórico do veículo.  
4. A partir de abastecimentos consecutivos, o sistema calcula o consumo médio do veículo em quilômetros por litro.  
5. O usuário pode consultar o histórico de abastecimentos, o custo médio por quilômetro e a evolução do consumo do veículo.

O cálculo de consumo médio será realizado pela relação entre a distância percorrida e a quantidade de combustível abastecida:

Consumo médio (km/L) \= Quilometragem atual \- Quilometragem anterior

                                                \_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_\_

                                                Litros abastecidos

​

### **6\. Avaliação de postos**

1. Após consultar ou utilizar um posto, o usuário autenticado pode registrar uma avaliação.  
2. O usuário informa uma nota e, opcionalmente, um comentário sobre sua experiência.  
3. O sistema valida os dados e vincula a avaliação ao posto e ao usuário responsável.  
4. A avaliação passa a compor a nota média exibida para o posto.  
5. Outros usuários podem consultar as avaliações ao visualizar os detalhes do estabelecimento.

### **7\. Administração e moderação do sistema**

1. Um usuário com perfil administrativo acessa as funcionalidades restritas de gerenciamento.  
2. O administrador pode cadastrar, editar ou remover informações de postos e preços, quando aplicável.  
3. Também pode moderar avaliações que apresentem conteúdo inadequado ou inconsistente.  
4. As ações administrativas são protegidas pelo controle de acesso baseado em perfil.

### **Diretrizes de Qualidade, Arquitetura e Conformidade Técnica**

O desenvolvimento do FuelFinder deverá seguir boas práticas de engenharia de software, arquitetura de sistemas, segurança da informação, desempenho, acessibilidade e qualidade. As decisões técnicas deverão ser documentadas e avaliadas durante o ciclo de desenvolvimento, assegurando que a plataforma seja confiável, escalável, sustentável e adequada às necessidades dos usuários.

A definição da arquitetura, das regras de negócio, dos padrões de desenvolvimento e dos requisitos não funcionais deverá passar por revisão técnica independente. Essa revisão terá como objetivo validar a aderência das implementações aos padrões estabelecidos, identificar riscos e oportunidades de melhoria e garantir níveis adequados de disponibilidade, desempenho, segurança, manutenibilidade e confiabilidade.

Sempre que aplicáveis ao contexto do projeto, deverão ser consideradas recomendações e referências reconhecidas nacional e internacionalmente, incluindo:

* OWASP (Open Worldwide Application Security Project): adoção de práticas de desenvolvimento seguro, prevenção de vulnerabilidades comuns em aplicações web, proteção de dados de usuários, controle de acesso, validação de entradas, gerenciamento seguro de sessões e proteção contra ataques como injeção, *cross-site scripting* e acessos indevidos.  
* W3C (World Wide Web Consortium): utilização de padrões web para garantir compatibilidade entre navegadores, estruturação adequada das páginas, responsividade e acessibilidade digital, considerando especialmente as recomendações das Web Content Accessibility Guidelines (WCAG).  
* TC39 (Technical Committee 39): adoção de padrões modernos e compatíveis da linguagem JavaScript/ECMAScript, favorecendo a qualidade, a legibilidade, a manutenção e a interoperabilidade do código executado no ambiente web.  
* IETF (Internet Engineering Task Force): utilização adequada de padrões e protocolos da internet, especialmente para comunicação segura entre cliente, servidor e serviços externos, com atenção ao uso de HTTPS, APIs baseadas em HTTP, autenticação, autorização e transmissão segura de dados.  
* ISO/IEC: consideração de normas relacionadas à qualidade de software, segurança da informação e governança tecnológica. Entre as referências aplicáveis, destacam-se a ISO/IEC 25010, para avaliação de atributos de qualidade de software, e a ISO/IEC 27001, como referência para práticas de gestão da segurança da informação.  
* TPC (Transaction Processing Performance Council): uso de boas práticas relacionadas à avaliação de desempenho e eficiência de bancos de dados, especialmente em consultas, armazenamento, processamento de dados e crescimento do volume de informações importadas da ANP.

Além dessas referências, o projeto deverá adotar práticas contínuas de qualidade, tais como:

* definição e documentação de padrões de código, arquitetura e integração;  
* revisão técnica de código e de decisões arquiteturais;  
* testes unitários, de integração, funcionais e de regressão;  
* testes de desempenho para operações críticas, como importação de dados, consultas de postos e comparação de preços;  
* testes de segurança e validação de permissões de acesso;  
* monitoramento de falhas, registros de auditoria e rastreabilidade de operações relevantes;  
* tratamento adequado de erros e manutenção dos últimos dados válidos em caso de falhas de integração;  
* planejamento de backup e recuperação de dados;  
* preocupação com acessibilidade, usabilidade e compatibilidade entre dispositivos e navegadores.

Essas diretrizes deverão orientar a implementação e a evolução do FuelFinder, contribuindo para que a plataforma ofereça uma experiência segura, estável, acessível e confiável aos seus usuários.

**\==============================================**

**Das funcionalidades podemos definir que:**

1- A visualização do mapa será pelo mapa visual: Leaflet 1.9.4, com tiles do OpenStreetMap. com distância calculada em linha reta pela diferença das coordenadas. (Isso vai deixar visual onde a pessoa está e onde ficam os postos e de forma gratuita e leve). Inserimos um botão “rotas” que direciona abertura do google Maps/waze com a localização de destino setada para o posto escolhido. (Vai deixar o aplicativo mais prático para o desenvolvimento da IA e não impacta negativamente a experiência do usuário)

2- Usuário coloca informações de carro/ano e já informa o consumo médio de combustível com base na experiência dele. Dessa forma o app não depende de consultar informações de consumo médio de manuais ou qualquer outro lugar. Com base no que o usuário preenche de abastecimento e quilometragem nosso app pode calcular para futuras utilizações do usuário.

3- (Possível funcionalidade) usuário pode enviar uma foto do preço do combustível no posto se ele utilizar aquele método de na foto estar as informações de localização horario etc que o professor mostrou na última aula de SOC. O sistema avalia a foto, coleta os valores e atualiza os dados caso esteja divergente da tabela do sistema atual.

