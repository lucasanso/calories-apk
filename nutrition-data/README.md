# nutrition-data

Dados de origem e decisões de curadoria da biblioteca nutricional do Calories. Metodologia completa em [docs/03-biblioteca-nutricional.md](../docs/03-biblioteca-nutricional.md).

## Estrutura

```
nutrition-data/
├── sources.lock.json        # cada fonte: citação, licença, data de acesso e arquivos com URL + SHA-256
├── raw/                     # arquivos oficiais exatamente como baixados (nunca editar)
│   ├── taco-4ed/
│   │   ├── Taco-4a-Edicao.xlsx                       # planilha oficial (NEPA/UNICAMP)
│   │   └── taco_4_edicao_ampliada_e_revisada.pdf     # publicação oficial (checagem cruzada)
│   └── usda-fdc/
│       └── FoodData_Central_sr_legacy_food_csv_2018-04.zip
├── curation/                # decisões humanas, revisáveis, sempre com justificativa
│   ├── categories.csv
│   ├── taco-groups.csv
│   ├── category-overrides.csv
│   ├── preparation-overrides.csv
│   ├── display-names.csv
│   ├── aliases.csv
│   ├── usda-foods.csv
│   ├── source-corrections.csv
│   ├── taco-pdf-deviations.csv
│   ├── value-exclusions.csv
│   ├── taco-alcohol.csv
│   ├── golden-values.csv
│   ├── requested-foods.csv
│   └── library-version.txt
└── reports/
    └── import-report.md     # gerado pelo importador (não editar)
```

## Fontes

| Fonte | Licença | Uso |
|---|---|---|
| TACO 4ª ed. (NEPA/UNICAMP, 2011) | "É permitida a reprodução total ou parcial do material, desde que seja citada a fonte." | Tabela completa (597 itens) |
| USDA FoodData Central — SR Legacy (2018-04) | Domínio público, CC0 1.0 | 17 itens que faltam na TACO |
| TBCA (FoRC/USP) | "Não é permitida a reprodução total ou parcial do material." | Não incorporada |

## Comandos

```bash
./gradlew :tools:food-importer:generateFoodLibrary
```

```bash
./gradlew :tools:food-importer:verifyFoodLibrary
```

- `generateFoodLibrary` gera `app/src/main/assets/library/food_library.json` e `reports/import-report.md`.
- `verifyFoodLibrary` falha se o asset commitado for diferente do que seria gerado.

## Regras

- Nunca editar arquivos de `raw/`. Para trocar um arquivo, atualize URL, data e SHA-256 em `sources.lock.json`. O importador recusa hash divergente.
- Nunca digitar valores nutricionais. A curadoria decide nome, categoria, preparo, sinônimos e exclusões documentadas. Valores vêm só dos arquivos oficiais.
- `golden-values.csv` serve só para conferência. Seus valores foram extraídos por um caminho independente do importador: `pdftotext` para a TACO e o dataset JSON para o USDA.
- Toda mudança no conteúdo exige incrementar `curation/library-version.txt`, regra E12. É isso que faz o app reimportar.
- O asset `app/src/main/assets/library/food_library.json` é **gerado**. Não editar à mão.
