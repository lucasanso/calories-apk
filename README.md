# Calories

Aplicativo Android pessoal de planejamento alimentar, controle nutricional e acompanhamento de dieta. Funciona offline, sem conta e sem servidor. Os dados ficam no aparelho.

> **Status: Fase 1 concluída (análise e arquitetura).** Ainda não há código de aplicativo. Os documentos abaixo definem como o app e a biblioteca nutricional serão construídos.

## O que o app fará

- Diário alimentar por refeição, com cálculo automático de calorias, proteínas, carboidratos, gorduras, fibras, gordura saturada e sódio.
- Biblioteca de alimentos pré-instalada a partir de fontes brasileiras (TACO; TBCA como complemento), sempre com fonte e estado de preparo identificados.
- Alimentos personalizados, favoritos e modelos de refeição.
- Metas configuráveis, com calculadora opcional de estimativa energética (identificada como aproximação).
- Histórico diário, semanal e mensal com gráficos, registro de peso e comparação com as metas vigentes em cada data.
- Planejamento em calendário com planos reutilizáveis (Plano A — Treino, Plano B — Descanso…).
- Lista de compras consolidada a partir do planejamento.
- Exportação CSV e JSON, backup e restauração locais.

## Documentação (Fase 1)

| Documento | Conteúdo |
|---|---|
| [docs/01-arquitetura.md](docs/01-arquitetura.md) | Clean Architecture + MVVM, módulos, camadas, Hilt, decisões técnicas, estrutura de diretórios, bibliotecas e versões, privacidade, desempenho |
| [docs/02-banco-de-dados.md](docs/02-banco-de-dados.md) | Modelagem Room: diagrama ER, tabelas, índices, separação biblioteca / personalizados / histórico / planejamento, snapshots, migrations, backup |
| [docs/03-biblioteca-nutricional.md](docs/03-biblioteca-nutricional.md) | Fontes (TACO, TBCA, USDA), condições de uso, pipeline reproduzível de importação e validação, seed idempotente, cobertura da lista inicial |
| [docs/04-calculo-nutricional.md](docs/04-calculo-nutricional.md) | Fórmulas, status de nutrientes (Tr, NA, ausente), agregação, unidades, arredondamento, metas, Mifflin-St Jeor, lista de compras, vetores de teste |
| [docs/05-navegacao-e-interface.md](docs/05-navegacao-e-interface.md) | Grafo de navegação, telas das 5 abas, fluxos, diretrizes visuais, componentes |
| [docs/06-plano-de-entrega.md](docs/06-plano-de-entrega.md) | Fases, estratégia de testes, entregáveis, riscos e decisões pendentes |
| [nutrition-data/README.md](nutrition-data/README.md) | Organização dos dados de origem e como fornecer os arquivos oficiais |

## Tecnologia

Kotlin · Jetpack Compose · Material 3 · Room · Coroutines e Flow · DataStore · Navigation Compose · WorkManager · Hilt. Clean Architecture com MVVM e Repository Pattern. Sem backend, Firebase, analytics ou permissão de internet.

## Roteiro

- [x] **Fase 1:** análise e arquitetura
- [ ] **Fase 2:** banco nutricional (importador TACO/TBCA/USDA, validação, asset versionado)
- [ ] **Fase 3:** MVP (dashboard, biblioteca, busca, personalizados, refeições, cálculo, macros, metas)
- [ ] **Fase 4:** histórico, gráficos, planejamento, peso, lista de compras, exportação e backup
- [ ] **Fase 5:** testes completos, APK assinado, documentação de instalação

## Fontes nutricionais

- NEPA/UNICAMP. *Tabela Brasileira de Composição de Alimentos — TACO*. 4. ed. rev. e ampl. Campinas, 2011.
- FoRC/USP. *Tabela Brasileira de Composição de Alimentos — TBCA*. Versão 7.x. Uso condicionado à confirmação dos termos (ver docs/03).
- USDA ARS. *FoodData Central* (CC0), só para lacunas.

As estimativas do app não substituem orientação de nutricionista ou médico.
