# 6. Plano de entrega, testes e riscos

## 6.1 Fases

| Fase | Entregas | Critério de pronto |
|---|---|---|
| **1. Análise e arquitetura** ✅ | Documentos `01`–`06` e estrutura de `nutrition-data/` | Arquitetura, modelagem, diretórios, bibliotecas, estratégia de importação, de cálculo e de navegação revisadas pelo usuário |
| **2. Banco nutricional** ✅ | Arquivos oficiais com SHA-256 (`sources.lock.json`); módulos `:domain`, `:library-format` e `:tools:food-importer` com 41 testes; curadoria em `nutrition-data/curation/`; `food_library.json` (611 alimentos); relatório de importação; `docs/03` atualizado | 0 erros; planilha × PDF da TACO: 5.796 valores, 0 divergências; 213 valores de ouro conferidos; asset determinístico (`verifyFoodLibrary`); todos os 60 itens da lista inicial mapeados |
| **3. MVP** ✅ | Módulos `:data` (Room v1, seed idempotente, repositórios, DataStore) e `:app` (Compose, Hilt, navegação tipada); Hoje, busca e seleção, biblioteca, detalhe, alimentos personalizados, refeições (slots, editar, mover, repetir, favoritas), metas com calculadora, histórico básico; ícone; `release/Calories-0.3.0.apk` assinado; biblioteca v2 (leite fluido) | 88 testes passando (domínio 40, formato 5, importador 23, dados 10 com Robolectric, app 7 + 3 capturas): inclui fluxo de ponta a ponta no app real, importação real de 614 alimentos, atualização de versão preservando dados do usuário e manifesto sem internet. Capturas em `docs/screenshots/`. O APK não foi executado num aparelho físico neste ambiente. |
| **4. Recursos avançados** ✅ | Room v2 (migration 1→2); histórico semana/mês com gráficos de kcal × meta e de macros; peso com gráfico e média móvel; planejamento (calendário, dia planejado, planos A/B/C, aplicar em intervalo e dias da semana, copiar dia, registrar no diário); lista de compras somada; Dados e backup (CSV padrão e Excel pt-BR, backup JSON, restauração validada e atômica, exportar/importar planos); `release/Calories-0.4.0.apk` assinado com a mesma chave. Lembretes opcionais **não** entraram (exigiriam permissão de notificação; ficam para quando o usuário pedir). | 106 testes passando + 5 de capturas (domínio 51, formato 5, importador 23, dados 16, app 11): migration com dados reais; backup de ida e volta idêntico; arquivos inválidos recusados sem tocar no banco; CSV exato; fluxos de interface de planejamento (plano → calendário → diário → lista de 1.050 g) e de backup/restauração pelo seletor de arquivos. Android Lint sem erros. O APK não foi executado num aparelho físico neste ambiente. |
| **5. Testes e entrega** | Bateria completa (6.2); testes de migration; APK release assinado; README com instalação; documentação final | Todos os testes passando; APK instalado e verificado no Galaxy S26 |

Cada fase termina com commit e push, e nenhuma fase começa sem a anterior estar verde.

## 6.2 Estratégia de testes

| Nível | Ferramenta | Cobertura |
|---|---|---|
| Domínio (JVM puro) | JUnit 4, kotlinx-coroutines-test | Matriz de 04, §4.9; regras dos use cases (biblioteca somente leitura, snapshot, metas por data, copiar dia ou refeição, aplicar plano em REPLACE e APPEND) |
| Importador | JUnit 4 | Leitores com arquivos de exemplo; mapeamento de símbolos; classificação de preparo; cada regra E1–E9 e W1–W4; golden values; determinismo (duas execuções com bytes iguais) |
| Room (JVM via Robolectric) | Room in-memory, Turbine | DAOs; `CustomFoodDao` não altera linhas `LIBRARY`; view `daily_nutrition` igual ao `NutrientAggregator`; políticas `onDelete`; FTS (acentos, prefixo, sinônimos) |
| Seed | Room in-memory + assets de teste v1 e v2 | Primeira importação; idempotência; atualização (valor alterado, item removido vira `isDeprecated`); favoritos e personalizados intocados; snapshots antigos inalterados; atomicidade (falha simulada no meio) |
| Migrations | `MigrationTestHelper` | Cada par de versões N→N+1 com dados reais |
| Backup e exportação | JVM | Exportar → limpar → restaurar → comparar igualdade; arquivo corrompido, versão futura ou referência quebrada são rejeitados sem tocar no banco; CSV comparado com arquivo de referência (UTF-8 BOM, aspas, vírgula decimal) |
| UI | Compose UI Test (Robolectric) | Adicionar alimento → dashboard atualiza; editar e remover com desfazer; alimento da biblioteca sem "Editar"; meta acima de 100% |
| Manifesto | Teste de build | Manifesto final sem `android.permission.INTERNET` |
| Manual no aparelho | Galaxy S26 | Instalação, tema claro e escuro, fonte grande, TalkBack básico, backup em pasta, restauração em reinstalação |

## 6.3 Entregáveis finais

- Código-fonte organizado (estrutura de 01, §1.9) e projeto que abre no Android Studio.
- `release/Calories-<versão>.apk`, assinado. A chave fica fora do repositório.
- `README.md` com funcionalidades, instalação no Galaxy (inclusive o Auto Blocker da Samsung), backup e transferência entre aparelhos.
- Documentação de arquitetura (`docs/01`–`05`), fontes nutricionais (`docs/03` + `nutrition-data/`) e o relatório de importação.

## 6.4 Riscos e mitigações

| Risco | Impacto | Mitigação |
|---|---|---|
| ~~Termos de uso da TBCA impedem a incorporação~~ (confirmado) | Lacunas (tilápia, cottage, macarrão cozido…) sem fonte brasileira | Resolvido na Fase 2: lacunas cobertas pelo USDA (CC0), identificadas como referência internacional; produtos de marca pelo rótulo. |
| ~~Extração do PDF da TACO com erro de coluna~~ | Valores errados silenciosos | Resolvido: a planilha oficial é a fonte primária e o PDF é usado só na checagem cruzada por coordenada (0 divergências), mais os valores de ouro extraídos por outra ferramenta |
| ~~Rede do ambiente bloqueia as fontes oficiais~~ | Fase 2 não começa | Resolvido: domínios liberados; os arquivos ficam versionados no repositório |
| AGP 9 incompatível com KSP ou Hilt na versão escolhida | Atraso no esqueleto | Esqueleto primeiro; fallback para a linha AGP 8.13 |
| Usuário espera registrar leite em ml | Atrito | Densidade só com fonte; explicação na interface; o personalizado pode usar referência em ml |
| Confusão entre peso cru e peso pronto | Totais errados | Preparo sempre visível, agrupamento por nome base com escolha explícita, nota na lista de compras |
| Troca de chave de assinatura entre versões | Reinstalação apaga os dados | Uma chave estável documentada; exportar backup antes de reinstalar |

## 6.5 Decisões que dependem do usuário

1. **TBCA:** o termo oficial proíbe reprodução, mesmo parcial, e este repositório é público. Por isso a TBCA não entra na biblioteca. Para usá-la há duas saídas: cadastrar os itens desejados como alimentos personalizados no app (uso pessoal da informação, com citação), ou tornar o repositório privado e pedir autorização à equipe da TBCA.
2. **Cadastro pelo rótulo** de produtos de marca (whey, iogurte proteico, granola, goma de tapioca): disponível desde a Fase 3 em Alimentos › Novo alimento, informando a base do rótulo (ex.: por 30 g).
3. **Chave de assinatura:** guardar o arquivo de chave entregue junto com o APK; versões futuras precisam dela para atualizar sem desinstalar.
