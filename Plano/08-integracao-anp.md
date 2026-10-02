# Fase 8 — Integração com os Dados da ANP

## Objetivo e estado

**Objetivo:** manter o pipeline administrativo auditável, validado, idempotente
e transacional para receber CSV/TSV direto ou ZIP que contenha CSV. Os preços
são históricos e informativos; a interface deve exibir a data de coleta da ANP
e não prometer preços em tempo real.

**Dependência:** Fase 5 (Preços de Combustíveis).

## Fonte e formato

- Fonte: Portal de Dados Abertos da ANP — Série Histórica de Preços de
  Combustíveis e GLP. A URL HTTPS oficial é fornecida na solicitação de importação.
- O downloader aceita HTTPS somente nos hosts oficiais configurados
  (`gov.br`, `www.gov.br`, `anp.gov.br`, `www.anp.gov.br` e `dados.gov.br`) e
  nas portas padrão 443 (ou não especificadas); não segue redirecionamentos. O
  tamanho é limitado a 50 MB e há timeouts de conexão e leitura.
- A entrega atual baixa CSV/TSV diretamente, reconhece ponto e vírgula,
  tabulação ou vírgula, BOM UTF-8/UTF-16 e UTF-8/Windows-1252. Layouts
  incompatíveis registram os cabeçalhos lidos e o delimitador detectado.
  Também aceita um ZIP com CSV/TSV e nomes de entradas UTF-8 ou CP437, sem
  extrair caminhos do arquivo. O ZIP é
  limitado a 1.000 entradas, 100 MB descompactados por entrada e 200 MB no
  total; quando há mais de um CSV/TSV com layout ANP válido, a importação falha
  com os nomes das entradas para que a fonte seja desambiguada.
- O parser identifica o delimitador pelo cabeçalho e consistência das colunas,
  interpreta decimal com vírgula quando separado por `;` e reporta linhas
  inválidas sem descartar os demais registros válidos.
- A importação processa somente linhas `SP` do CSV. Normaliza o CNPJ e o
  combustível, prepara os postos distintos e importa cada preço com data e
  origem `ANP_IMPORT`. A chave histórica é
  `(station_id, fuel_type_id, collection_date)`; reimportações idênticas são
  ignoradas e valores alterados seguem a regra de atualização existente. Se o
  arquivo repetir a mesma chave com valores divergentes, prevalece a última
  linha válida e a divergência fica registrada no relatório.
- Somente após o commit do CSV, consulta
  `https://revendedoresapi.anp.gov.br/v1/combustivel?uf=SP`. O Swagger
  (`/swagger/v1/swagger.json`) documenta o parâmetro `numeropagina`, inteiro
  iniciado em 1. A resposta usa o envelope `status`, `title`, `succeeded` e
  `data`; a leitura termina quando `data` é um array vazio. A API não informa
  total de páginas. Na verificação da fonte, as páginas 1 e 2 retornaram 5.000
  e 3.427 registros e a página 3 encerrou com `data: []`; o código não fixa
  esses totais e avança até a resposta vazia.
- Cada item é validado novamente para `uf == SP`; CNPJ pontuado é normalizado
  para 14 dígitos e usado somente para atualizar latitude/longitude do posto.
  Coordenadas devem ser numéricas e estar nos intervalos geográficos válidos;
  valores ausentes ou inválidos não apagam coordenadas salvas. A importação
  reporta CNPJs sem correspondência, registros conflitantes e coordenadas
  ausentes. O array `produtos` da API não é usado para formar preços.
- As chamadas de página são sequenciais, com intervalo curto entre páginas.
  Falhas HTTP/limite interrompem a etapa da API e deixam os preços do CSV
  preservados; repetir a mesma importação é idempotente para postos e preços.

## Endpoints administrativos

Todos os endpoints exigem `ROLE_ADMIN`. A implementação recebe `sourceUrl`
e `referencePeriod`; a evolução planejada substitui o campo único por
`sourceUrl`, `referenceYear` e `referenceSemester`. O usuário autenticado é
registrado como autor da importação.

O ano deve ser informado como ano de quatro algarismos e o semestre como valor
inteiro `1` ou `2`. Persistir os valores separadamente (`reference_year`,
`reference_semester`) e apresentá-los, por exemplo, como “1º semestre de 2026”.
A faixa histórica permitida e a regra para registros cuja data de coleta não
caiba no semestre informado precisam ser decididas antes da implementação.

| Método | Rota | Resposta |
|---|---|---|
| `POST` | `/admin/anp/import` | `202 Accepted` com o log da importação |
| `GET` | `/admin/anp/imports` | `200 OK` com logs ordenados do mais recente |
| `GET` | `/admin/anp/imports/{id}` | `200 OK` com o log solicitado; `404` se não existir |

O `POST` inicia o trabalho em segundo plano e retorna `RUNNING`. A interface
consulta `GET /admin/anp/imports/{id}` para exibir as etapas CSV e API. A
porcentagem é determinada apenas durante o processamento das linhas do CSV,
cujo total é conhecido; na consulta paginada sem total conhecido, a interface
usa um indicador indeterminado e mostra a página concluída.

Contrato de entrada planejado para `POST /admin/anp/import`:

```json
{
  "sourceUrl": "https://dados.gov.br/arquivo-oficial.zip",
  "referenceYear": 2026,
  "referenceSemester": 1
}
```

`referenceYear` é um inteiro correspondente a um ano com quatro algarismos
(validação de faixa permitida ainda pendente); `referenceSemester` aceita
somente `1` ou `2`. Não aceitar novamente um único campo como `2026-S1`.

## Mapeamento

| Campo ANP | Destino |
|---|---|
| `CNPJ da Revenda` | `Station.cnpj`, normalizado para 14 dígitos |
| `Revenda`, `Bandeira` | Nome corporativo/comercial e marca do posto |
| `Nome da Rua`, `Numero Rua`, `Bairro`, `Cep` | Endereço do posto |
| `Municipio`, `Estado - Sigla` | Cidade e UF |
| `Produto` | Código canônico do catálogo |
| `Data da Coleta`, `Valor de Venda` | Data e valor em `FuelPrice` |
| `Unidade de Medida` | Validada contra o combustível: `R$/litro` ou `R$/m³` |
| `latitude`, `longitude` da API de revendedores | `Station.latitude`, `Station.longitude`, após validação |

| Produto ANP | `FuelType.code` |
|---|---|
| Gasolina comum | `GASOLINE_REGULAR` |
| Gasolina aditivada ou premium | `GASOLINE_PREMIUM` |
| Etanol | `ETHANOL` |
| Diesel S10 | `DIESEL_S10` |
| Diesel S500 | `DIESEL_S500` |
| GNV | `CNG` |

## Regras de processamento e auditoria

- A identidade do posto é seu CNPJ normalizado. A chave de preço é
  `(station_id, fuel_type_id, collection_date)`. Uma nova execução não cria
  duplicatas; se o preço existente mudou, ele é atualizado.
- A importação procura postos pelo CNPJ normalizado; não usa correspondência
  aproximada por endereço/nome. Sem CNPJ válido, razão social, município, UF,
  produto ou preço válidos, não cria um preço vinculado e registra a linha.
  Dados cadastrais divergentes entre linhas do CSV ou diferentes do cadastro
  existente são atualizados conforme a fonte CSV e descritos no log.
- Produto desconhecido, preço/data/unidade inválidos, CNPJ inválido e outros
  problemas por registro são armazenados como erros e resultam em `PARTIAL`
  quando houver linhas válidas processadas.
- `AnpImportLog` registra arquivo, período, URL sem query string, início/fim,
  contagens por etapa, progresso, status, erros e UUID do administrador. Os
  status são `RUNNING`, `SUCCESS`, `PARTIAL` e `FAILED`. O resumo distingue
  linhas lidas, linhas SP associadas, outros estados, inválidas, postos
  criados/atualizados, preços associados/alterados, coordenadas atualizadas,
  CNPJs sem correspondência e páginas consultadas.
- Download ou layout inválido gera um log `FAILED`. Falha interna do CSV
  reverte essa etapa; erro durante a API resulta em `PARTIAL` e preserva o
  commit do CSV para uma repetição idempotente.
- A migração V5 torna latitude e longitude anuláveis em `stations`, permitindo
  armazenar postos ainda não geocodificados. Consultas geográficas não incluem
  postos sem coordenadas.

## Geocodificação manual com Geoapify

Leia a chave exclusivamente da variável de ambiente `GEOAPIFY_API_KEY`. Para
testar chamadas reais, configure-a no ambiente do processo que inicia a
aplicação; nunca a grave no código, em arquivos versionados ou no frontend. Sem
a variável, o cadastro manual de postos continua operando sem geocodificação.
Essa integração não é usada na importação ANP, que consulta as coordenadas da
API de revendedores da própria ANP.

## Implementação entregue

- Entidades, repositório e DTOs de auditoria em `modules/anp`.
- Downloader restrito a hosts HTTPS oficiais, parser CSV/TSV e processador
  transacional por linha.
- Serviço de importação, atualização idempotente de preços e endpoints
  administrativos protegidos por `ROLE_ADMIN`.
- Atualização de coordenadas pela API paginada de revendedores da ANP; Geoapify
  permanece disponível para o fluxo de cadastro manual.
- Execução assíncrona com etapa e progresso consultáveis, resumo persistido e
  possibilidade de repetir a mesma URL para retomar uma carga parcial sem
  duplicar preços.
- Testes unitários do parser, downloader, processador, paginação da API e
  atualização de coordenadas; teste de integração dos endpoints, RBAC,
  importação e idempotência.

## Trabalho futuro planejado (não implementado)

1. Alterar o contrato para `referenceYear` e `referenceSemester`; definir a
   faixa histórica permitida e como validar se as datas do CSV pertencem ao
   semestre declarado. Migrar os dados de `reference_period` sem editar
   migrações já aplicadas.

Essa mudança de contrato é independente da importação SP e da atualização das
coordenadas entregues nesta etapa.

## Critérios de aceitação

- [x] Importações idempotentes por posto, combustível e data de coleta.
- [x] Falhas internas revertem os dados parciais e preservam dados existentes.
- [x] Auditoria inclui período, URL segura, usuário, contagens, status e erros.
- [x] CNPJ normalizado e validado; produtos e unidades mapeados ao catálogo.
- [x] Atualizar coordenadas pela API paginada da ANP, validando UF, CNPJ e coordenadas.
- [x] Preservar preços importados quando a etapa de coordenadas falhar.
- [x] Coordenadas ausentes preservadas/registradas no resumo e nos detalhes.
- [x] Endpoints de importação e histórico restritos a `ROLE_ADMIN`.
- [x] Acompanhar as etapas do CSV e da API sem inventar percentual para paginação desconhecida.
- [x] Exibir resumo de linhas, postos, preços, coordenadas, divergências e páginas.
- [x] Testes unitários e de integração direcionados passam.
- [ ] Executar `mvn -q clean verify` completo, incluindo o gate de cobertura de linhas.
- [ ] Receber, validar, armazenar e apresentar ano e semestre em campos separados.

## Testes executados

```powershell
mvn -q "-Dtest=AnpCsvParserTest,AnpCsvDownloaderTest,AnpImportProcessorTest,AnpImportServiceTest,AnpImportControllerIntegrationTest,AnpRetailerApiClientTest,AnpRetailerImportServiceTest,AnpCoordinateUpdaterTest" -DforkCount=0 test
```
