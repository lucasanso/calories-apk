# nutrition-data

Dados de origem da biblioteca nutricional do Calories. O processo completo está em [docs/03-biblioteca-nutricional.md](../docs/03-biblioteca-nutricional.md).

> Status: **estrutura definida na Fase 1, ainda sem dados.** Os arquivos são adicionados na Fase 2.

## Estrutura

```
nutrition-data/
├── sources.lock.json        # manifesto: cada arquivo bruto com URL, data de acesso, versão, licença e SHA-256
├── raw/                     # arquivos oficiais exatamente como baixados (nunca editar)
│   ├── taco-4ed/            # PDF (e planilha, se o NEPA publicar) da TACO 4ª ed.
│   │   └── extracted/       # CSV extraído do PDF, se necessário, com o comando usado
│   ├── tbca-7.x/
│   │   └── manual/          # transcrições por alimento (lista de lacunas), com URL e data
│   └── usda-fdc/            # CSVs oficiais SR Legacy / Foundation Foods
├── curation/                # decisões humanas, revisáveis em PR
│   ├── include-list.csv
│   ├── category-map.csv
│   ├── preparation-overrides.csv
│   ├── display-names.csv
│   ├── aliases.csv
│   ├── accepted-warnings.csv
│   ├── golden-values.csv
│   └── library-version.txt
└── reports/
    └── import-report.md     # gerado pelo importador
```

## Como fornecer os arquivos oficiais

Se este ambiente não tiver acesso aos sites das fontes, baixe os arquivos e coloque-os nas pastas acima, **sem renomear nem modificar o conteúdo**:

| Fonte | Onde obter | Pasta |
|---|---|---|
| TACO 4ª edição (NEPA/UNICAMP) | Site do NEPA/UNICAMP, seção TACO | `raw/taco-4ed/` |
| TBCA (FoRC/USP) | tbca.net.br. Leia o termo de uso antes. | `raw/tbca-7.x/` |
| USDA FoodData Central | fdc.nal.usda.gov, página de download (CSV de SR Legacy e Foundation Foods) | `raw/usda-fdc/` |

O importador calcula o SHA-256 de cada arquivo e o registra no `sources.lock.json` na primeira execução. Nas execuções seguintes, compara o hash e para se algum arquivo mudar.

## Regras

- Nunca editar arquivos de `raw/`. Correções e decisões ficam em `curation/`, com justificativa.
- Nunca digitar valores nutricionais fora de `golden-values.csv`. Esse arquivo serve só para conferência e registra a página da fonte de cada valor.
- O asset `app/src/main/assets/library/food_library.json` é **gerado**. Não editar à mão.
