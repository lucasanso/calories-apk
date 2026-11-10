# 6. Plano de entrega, testes e riscos

## 6.1 Fases

| Fase | Entregas | Critério de pronto |
|---|---|---|
| **1. Análise e arquitetura** (esta) | Documentos `01`–`06` e estrutura de `nutrition-data/` | Arquitetura, modelagem, diretórios, bibliotecas, estratégia de importação, de cálculo e de navegação revisadas pelo usuário |
| **2. Banco nutricional** | `sources.lock.json`; arquivos brutos; `:tools:food-importer` com testes; `curation/*.csv`; `golden-values.csv`; `food_library.json`; relatório de importação; atualização de `03` com as confirmações | Importador determinístico; zero erros; valores de ouro conferidos; todos os itens ✅ de 03, §3.8 presentes; lacunas resolvidas ou documentadas |
| **3. MVP** | Esqueleto Gradle (4 módulos) compilando e gerando APK; Room v1 + seed; Hoje; Alimentos (biblioteca, busca, personalizados, favoritos); refeições (slots, adicionar, editar, remover, duplicar, modelos); metas manuais e calculadora | Fluxos de 05, §5.3 funcionando no aparelho; testes de domínio e de DAO passando; APK de debug instalável |
| **4. Recursos avançados** | Histórico com gráficos; peso; planejamento (planos, calendário, aplicar e copiar); lista de compras; exportação CSV/JSON; backup e restauração; lembretes opcionais | Fluxos completos; backup de ida e volta idêntico; restauração atômica |
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
| Termos de uso da TBCA impedem a incorporação | Lacunas (tilápia, cottage, macarrão cozido…) sem fonte brasileira | Fallback para USDA (CC0) identificado como internacional, ou cadastro pelo rótulo. A TBCA é isolada na lista de inclusão e pode ser removida com uma linha. |
| Extração do PDF da TACO com erro de coluna | Valores errados silenciosos | Usar planilha oficial se existir; verificações kcal↔kJ e soma centesimal = 100; dupla transcrição; golden values |
| Rede do ambiente bloqueia as fontes oficiais | Fase 2 não começa | Liberar domínios ou o usuário fornece os arquivos em `nutrition-data/raw/` |
| AGP 9 incompatível com KSP ou Hilt na versão escolhida | Atraso no esqueleto | Esqueleto primeiro; fallback para a linha AGP 8.13 |
| Usuário espera registrar leite em ml | Atrito | Densidade só com fonte; explicação na interface; o personalizado pode usar referência em ml |
| Confusão entre peso cru e peso pronto | Totais errados | Preparo sempre visível, agrupamento por nome base com escolha explícita, nota na lista de compras |
| Troca de chave de assinatura entre versões | Reinstalação apaga os dados | Uma chave estável documentada; exportar backup antes de reinstalar |

## 6.5 Decisões que dependem do usuário

1. **Acesso às fontes oficiais:** liberar `nepa.unicamp.br`, `tbca.net.br` e `fdc.nal.usda.gov` no ambiente, ou enviar os arquivos.
2. **TBCA:** prosseguir com o uso condicional descrito em 03, §3.2, ou usar apenas TACO + USDA + rótulos.
3. **Repositório público ou privado.** Isso não muda as licenças (TACO e USDA permitem redistribuição; a TBCA é não comercial), mas define quem vê o asset gerado.
