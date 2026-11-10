# Calories

Aplicativo Android pessoal de planejamento alimentar, controle nutricional e acompanhamento de dieta. Funciona offline, sem conta e sem servidor. Os dados ficam no aparelho.

> **Status: Fase 2 concluída (biblioteca nutricional).**
>
> - Há 611 alimentos (TACO 4ª ed. completa + 14 lacunas do USDA) em [`app/src/main/assets/library/food_library.json`](app/src/main/assets/library/food_library.json).
> - O asset é gerado por um importador reproduzível e validado contra os arquivos oficiais.
> - Ainda não há o app Android: ele começa na Fase 3.

## O que o app fará

- Diário alimentar por refeição, com cálculo automático de calorias, proteínas, carboidratos, gorduras, fibras, gordura saturada e sódio.
- Biblioteca de alimentos pré-instalada a partir da TACO (NEPA/UNICAMP), com lacunas cobertas pelo USDA, sempre com fonte e estado de preparo identificados.
- Alimentos personalizados, favoritos e modelos de refeição.
- Metas configuráveis, com calculadora opcional de estimativa energética (identificada como aproximação).
- Histórico diário, semanal e mensal com gráficos, registro de peso e comparação com as metas vigentes em cada data.
- Planejamento em calendário com planos reutilizáveis (Plano A — Treino, Plano B — Descanso…).
- Lista de compras consolidada a partir do planejamento.
- Exportação CSV e JSON, backup e restauração locais.

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/01-arquitetura.md](docs/01-arquitetura.md) | Clean Architecture + MVVM, módulos, camadas, Hilt, decisões técnicas, estrutura de diretórios, bibliotecas e versões, privacidade, desempenho |
| [docs/02-banco-de-dados.md](docs/02-banco-de-dados.md) | Modelagem Room: diagrama ER, tabelas, índices, separação biblioteca / personalizados / histórico / planejamento, snapshots, migrations, backup |
| [docs/03-biblioteca-nutricional.md](docs/03-biblioteca-nutricional.md) | Fontes (TACO, USDA; por que não a TBCA), condições de uso verificadas, pipeline de importação, checagem cruzada, validação, curadoria, cobertura da lista inicial |
| [nutrition-data/reports/import-report.md](nutrition-data/reports/import-report.md) | Relatório da última importação (gerado) |
| [docs/04-calculo-nutricional.md](docs/04-calculo-nutricional.md) | Fórmulas, status de nutrientes (Tr, NA, ausente), agregação, unidades, arredondamento, metas, Mifflin-St Jeor, lista de compras, vetores de teste |
| [docs/05-navegacao-e-interface.md](docs/05-navegacao-e-interface.md) | Grafo de navegação, telas das 5 abas, fluxos, diretrizes visuais, componentes |
| [docs/06-plano-de-entrega.md](docs/06-plano-de-entrega.md) | Fases, estratégia de testes, entregáveis, riscos e decisões pendentes |
| [nutrition-data/README.md](nutrition-data/README.md) | Organização dos dados de origem e como fornecer os arquivos oficiais |

## Biblioteca nutricional: comandos

Requer JDK 21. O wrapper baixa o Gradle 9.9.0.

Regerar o asset e o relatório a partir dos arquivos oficiais em `nutrition-data/raw/`:

```bash
./gradlew :tools:food-importer:generateFoodLibrary
```

Conferir que o asset commitado é exatamente o que o importador gera:

```bash
./gradlew :tools:food-importer:verifyFoodLibrary
```

Rodar os testes (domínio, formato do asset, importador e integração com os dados reais):

```bash
./gradlew test
```

## Tecnologia

Kotlin · Jetpack Compose · Material 3 · Room · Coroutines e Flow · DataStore · Navigation Compose · WorkManager · Hilt. Clean Architecture com MVVM e Repository Pattern. Sem backend, Firebase, analytics ou permissão de internet.

## Roteiro

- [x] **Fase 1:** análise e arquitetura
- [x] **Fase 2:** banco nutricional (importador TACO/USDA, checagem cruzada, validação, asset versionado)
- [ ] **Fase 3:** MVP (dashboard, biblioteca, busca, personalizados, refeições, cálculo, macros, metas)
- [ ] **Fase 4:** histórico, gráficos, planejamento, peso, lista de compras, exportação e backup
- [ ] **Fase 5:** testes completos, APK assinado, documentação de instalação

## Fontes nutricionais

- NEPA/UNICAMP. *Tabela Brasileira de Composição de Alimentos — TACO*. 4. ed. rev. e ampl. Campinas, 2011.
- U.S. Department of Agriculture, Agricultural Research Service. *FoodData Central: SR Legacy (2018-04)*. Domínio público (CC0); usado só para lacunas da TACO.

A TBCA (FoRC/USP) não foi incorporada: o termo de uso proíbe a reprodução do material (ver docs/03, §3.2).

As estimativas do app não substituem orientação de nutricionista ou médico.
