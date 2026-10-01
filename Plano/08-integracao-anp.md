# Fase 8 — Integração com os Dados da ANP

## Objetivo e estado

**Objetivo-alvo da evolução:** ampliar o pipeline administrativo auditável,
validado, idempotente e transacional para receber CSV/TSV direto ou ZIP que
contenha CSV. A implementação entregue atualmente baixa CSV/TSV direto; o
suporte a ZIP está planejado abaixo e ainda não foi implementado. Os preços são
históricos e informativos; a interface deve exibir a data de coleta da ANP e não
prometer preços em tempo real.

**Dependência:** Fase 5 (Preços de Combustíveis).

## Fonte e formato

- Fonte: Portal de Dados Abertos da ANP — Série Histórica de Preços de
  Combustíveis e GLP. A URL HTTPS oficial é fornecida na solicitação de importação.
- O downloader aceita HTTPS somente nos hosts oficiais configurados
  (`gov.br`, `www.gov.br`, `anp.gov.br`, `www.anp.gov.br` e `dados.gov.br`) e
  nas portas padrão 443 (ou não especificadas); não segue redirecionamentos. O
  tamanho é limitado a 50 MB e há timeouts de conexão e leitura.
- A entrega atual baixa CSV/TSV diretamente, reconhece ponto e vírgula,
  tabulação ou vírgula, BOM UTF-8 e UTF-8/Windows-1252. ZIP, os casos
  específicos de números com vírgula decimal e a robustez de detecção do
  delimitador descrita abaixo são trabalho planejado, ainda não implementado.
- O comportamento-alvo deve aceitar um CSV/TSV direto ou um ZIP da ANP
  contendo CSV. Para ZIP: validar o arquivo, inspecionar as entradas sem
  confiar em caminhos internos, aplicar limites de tamanho/expansão e processar
  o CSV elegível. Falhas de integridade, ausência de CSV ou layout incompatível
  devem produzir erro útil e auditável. Limites de descompactação e critério
  para selecionar entre múltiplos CSVs válidos permanecem pendentes.
- A detecção do delimitador deve usar cabeçalhos conhecidos e consistência do
  número de colunas; `;` é delimitador quando esse for o formato, sem confundir
  a vírgula decimal no valor com separador de campo. Interpretar formatos
  numéricos conforme amostras oficiais (inclusive decimal com vírgula quando
  presente), sem conversões ambíguas. Um CSV válido separado por `;` não pode
  falhar com a mensagem genérica “Não foi possível identificar o separador do
  CSV ANP”; o diagnóstico deve indicar causa útil quando o layout realmente
  não puder ser reconhecido. Casos concretos devem ser cobertos por amostras de
  teste da ANP.

## Endpoints administrativos

Todos os endpoints exigem `ROLE_ADMIN`. A implementação atual recebe `sourceUrl`
e `referencePeriod`; a forma-alvo do pedido substitui o campo único por
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
- O pipeline futuro deve procurar o posto pelo CNPJ normalizado antes de criar
  qualquer registro. Se o CNPJ corresponder a um posto existente, vincular o
  preço a esse posto. Se não existir, criar somente quando CNPJ válido, razão
  social, município e UF — campos obrigatórios do cadastro — estiverem
  disponíveis e coerentes; coordenadas podem permanecer nulas conforme a regra
  atual e a exceção de coordenadas ausentes para importação da ANP descrita na
  Fase 4. Isso não altera os requisitos do cadastro manual de posto.
- Não usar correspondência aproximada por endereço/nome para criar postos e
  não criar duplicatas silenciosamente. Sem CNPJ válido ou sem campos
  obrigatórios suficientes, não criar posto nem preço vinculado; classificar a
  linha como falha e explicar o campo ausente/inválido. Divergência cadastral
  para CNPJ já existente deve ser reportada sem sobrescrever silenciosamente.
  A política de reconciliação/atualização de metadados do posto existente
  permanece pendente.
- Produto desconhecido, preço/data/unidade inválidos, CNPJ inválido e outros
  problemas por registro são armazenados como erros e resultam em `PARTIAL`
  quando houver linhas válidas processadas.
- Se `GEOAPIFY_API_KEY` estiver configurada, postos sem coordenadas podem ser
  geocodificados; os resultados são reutilizados durante a importação. Sem a
  chave, o posto e o preço ainda são salvos, latitude/longitude permanecem
  nulas e o resultado é `PARTIAL`, sem bloquear a inicialização ou outras
  funcionalidades.
- `AnpImportLog` registra arquivo, período, URL sem query string, início/fim,
  contagens, status, erros e UUID do administrador. Os status são `SUCCESS`,
  `PARTIAL` e `FAILED`.
- O resultado-alvo distingue registros lidos, importados, ignorados e falhos.
  Cada linha de dados deve receber uma classificação única e explicação quando
  ignorada/falha; a definição de se “importado” conta linha ou preço e os
  critérios exatos de “ignorado” devem ser fechados no contrato antes de
  implementar os novos contadores. Manter erro de posto associado à linha e
  não persistir o preço daquela linha se o posto não puder ser resolvido ou
  cadastrado com segurança.
- Download ou layout inválido gera um log `FAILED`. Falha interna do
  processamento reverte a transação de dados, registra a falha fora dela e
  propaga o erro; assim, os dados válidos anteriores permanecem disponíveis.
- A migração V5 torna latitude e longitude anuláveis em `stations`, permitindo
  armazenar postos ainda não geocodificados. Consultas geográficas não incluem
  postos sem coordenadas.

## Configuração Geoapify

Leia a chave exclusivamente da variável de ambiente `GEOAPIFY_API_KEY`. Para
testar chamadas reais, configure-a no ambiente do processo que inicia a
aplicação; nunca a grave no código, em arquivos versionados ou no frontend. Sem
a variável, a aplicação continua operando sem geocodificação. Os limites e
condições do serviço devem ser verificados na documentação vigente da Geoapify.

## Implementação entregue

- Entidades, repositório e DTOs de auditoria em `modules/anp`.
- Downloader restrito a hosts HTTPS oficiais, parser CSV/TSV e processador
  transacional por linha.
- Serviço de importação, atualização idempotente de preços e endpoints
  administrativos protegidos por `ROLE_ADMIN`.
- Geocodificação opcional Geoapify e migração V5 para coordenadas nulas.
- Testes unitários do parser, downloader, processador e serviço; teste de
  integração dos endpoints, RBAC, importação, geocodificação opcional e
  idempotência.

## Trabalho futuro planejado (não implementado)

1. Evoluir entrada para aceitar ZIP oficial e CSV/TSV direto, com validação
   segura do arquivo compactado e seleção não ambígua do CSV.
2. Ajustar e testar parser para delimitador identificado pelo cabeçalho/
   consistência, com `;` preservado como delimitador e suporte aos formatos
   numéricos reais da ANP, inclusive decimal com vírgula.
3. Criar ou localizar postos de forma controlada pelo CNPJ normalizado antes
   de importar o preço, verificando dados obrigatórios e reportando
   inconsistências sem duplicação ou sobrescrita silenciosa.
4. Alterar contrato de entrada, log e interface para `referenceYear` e
   `referenceSemester`; planejar migração progressiva dos dados atuais de
   `reference_period`, sem editar migrações já aplicadas.
5. Acrescentar resumo consistente de lidos/importados/ignorados/falhos,
   explicações por linha e testes para formatos, duplicidades, postos novos e
   erros de cadastro.

As tarefas acima são incrementais sobre a importação entregue. Não significam
que o backend, banco ou formulário já tenham sido atualizados.

## Critérios de aceitação

- [x] Importações idempotentes por posto, combustível e data de coleta.
- [x] Falhas internas revertem os dados parciais e preservam dados existentes.
- [x] Auditoria inclui período, URL segura, usuário, contagens, status e erros.
- [x] CNPJ normalizado e validado; produtos e unidades mapeados ao catálogo.
- [x] Geocodificação opcional, sem chave obrigatória para iniciar a aplicação.
- [x] Coordenadas ausentes persistidas como nulas e reportadas como importação parcial.
- [x] Endpoints de importação e histórico restritos a `ROLE_ADMIN`.
- [x] Testes unitários e de integração direcionados passam.
- [x] `mvn -q clean verify` passa com o gate de 100% de cobertura de linhas.
- [ ] Aceitar e validar arquivo ZIP contendo CSV, além dos formatos diretos já suportados.
- [ ] Validar delimitador e números com amostras oficiais ANP, incluindo `;` e vírgula decimal.
- [ ] Criar posto novo apenas após validação de CNPJ e dados obrigatórios; não gravar preço órfão.
- [ ] Exibir contadores de registros lidos, importados, ignorados e falhos com mensagens úteis.
- [ ] Receber, validar, armazenar e apresentar ano e semestre em campos separados.

## Testes executados

```powershell
mvn -q "-Dtest=AnpCsvParserTest,AnpCsvDownloaderTest,AnpImportProcessorTest,AnpImportServiceTest,AnpImportControllerIntegrationTest" test
```
