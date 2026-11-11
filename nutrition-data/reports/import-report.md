# Relatório de importação da biblioteca nutricional

> Gerado por `./gradlew :tools:food-importer:generateFoodLibrary`. Não editar à mão.

| Item | Valor |
|---|---|
| Versão da biblioteca | 2 |
| Formato do asset | 1 |
| Alimentos | 614 |
| SHA-256 do asset | `615fe94fa034718d29b77a785efe9706895bd0eff4f593a9569cda242f8e51f0` |
| Erros | 0 |
| Avisos | 56 |

## Fontes e arquivos

**TACO4** — Tabela Brasileira de Composição de Alimentos – TACO (4ª edição revisada e ampliada). Licença: É permitida a reprodução total ou parcial do material, desde que seja citada a fonte (TACO 4ª ed., p. ii). Acesso: 2026-11-10.

- `raw/taco-4ed/Taco-4a-Edicao.xlsx` (workbook) — SHA-256 `a66b8ec528daeabc63bc2b015fc9bd8c6d76b941c2fc0ed93a4311d449302d14`
- `raw/taco-4ed/taco_4_edicao_ampliada_e_revisada.pdf` (pdf) — SHA-256 `2002aec5615b5b1395aaa8fa675635bbb7f712c33f278af5e332f1cac8f108c8`

**USDA_SR** — USDA FoodData Central – SR Legacy (SR Legacy, abril de 2018). Licença: Domínio público, CC0 1.0 Universal (USDA solicita citar o FoodData Central como fonte). Acesso: 2026-11-10.

- `raw/usda-fdc/FoodData_Central_sr_legacy_food_csv_2018-04.zip` (csv-zip) — SHA-256 `b80817294b8850530aaedf2e515c02593b1824f763a0ff356e5c2081643e6fd0`

## Composição da biblioteca

| Fonte | Alimentos |
|---|---|
| TACO4 | 597 |
| USDA_SR | 17 |

| Categoria | Alimentos |
|---|---|
| Proteínas | 212 |
| Carboidratos | 214 |
| Gorduras | 35 |
| Vegetais e legumes | 74 |
| Outros | 79 |

| Preparo | Alimentos | Origem da classificação |
|---|---|---|
| Cru | 254 | curated_selection: 1, description: 229, override: 24 |
| Cozido | 63 | curated_selection: 2, description: 61 |
| Grelhado | 22 | description: 22 |
| Assado | 22 | curated_selection: 2, description: 20 |
| Frito | 28 | description: 26, override: 2 |
| Refogado | 9 | description: 8, override: 1 |
| Torrado | 6 | description: 6 |
| Em conserva | 14 | description: 14 |
| Seco ou em pó | 22 | curated_selection: 2, description: 11, override: 9 |
| Pronto para consumo | 137 | curated_selection: 10, group_default: 72, override: 55 |
| Preparação culinária | 37 | group_default: 30, override: 7 |

## Status dos nutrientes

| Nutriente | Medido | Traço (Tr) | Não aplicável (NA) | Sem dado |
|---|---|---|---|---|
| Energia | 608 | 0 | 2 | 4 |
| Proteínas | 593 | 6 | 10 | 5 |
| Carboidratos | 599 | 0 | 10 | 5 |
| Gorduras totais | 577 | 30 | 2 | 5 |
| Gorduras saturadas | 433 | 36 | 2 | 143 |
| Fibras | 372 | 7 | 221 | 14 |
| Sódio | 540 | 64 | 0 | 10 |

## Checagem cruzada TACO: planilha × PDF oficial

Valores publicados derivados da planilha comparados com o texto do PDF (mesma edição, ambos do NEPA/UNICAMP).

| Linhas encontradas no PDF | Quantidade |
|---|---|
| Tabela 1 (composição) | 597 |
| Tabela 1 (minerais) | 597 |
| Tabela 2 (ácidos graxos) | 423 |

| Campo | Valores comparados |
|---|---|
| Carboidrato (g) | 597 |
| Cinzas (g) | 597 |
| Energia (kJ) | 597 |
| Energia (kcal) | 597 |
| Fibra alimentar (g) | 597 |
| Lipídeos (g) | 597 |
| Proteína (g) | 597 |
| Sódio (mg) | 597 |
| Umidade (%) | 597 |
| Ácidos graxos saturados (g) | 423 |
| **Total** | **5796** |

Divergências de valor: **0**. Divergências de descrição: **2** (todas precisam estar em `curation/taco-pdf-deviations.csv`).

| Item | Campo | Planilha | PDF | Página |
|---|---|---|---|---|
| 69 | descrição | Abóbora, pescoço, crua | Abobora, pescoço, crua | 33 |
| 540 | descrição | L | Feijoada | 63 |

## Verificações de consistência

| Verificação | Itens verificados | Maior desvio |
|---|---|---|
| E8 kcal↔kJ (TACO) | 591 | 1.7758290666667 |
| E9 soma centesimal (TACO4) | 404 | 0.10579710144927094 |
| E9 soma centesimal (USDA_SR) | 15 | 0.07 |
| E7 valores de ouro | 234 | — |

## Cobertura da lista inicial do requisito

| Pedido | Categoria | Status | Alimentos na biblioteca | Observação |
|---|---|---|---|---|
| Peito de frango | Proteínas | ✅ TACO | `TACO4:406` Frango, peito, com pele, assado<br>`TACO4:407` Frango, peito, com pele, cru<br>`TACO4:408` Frango, peito, sem pele, cozido<br>`TACO4:409` Frango, peito, sem pele, cru<br>`TACO4:410` Frango, peito, sem pele, grelhado | Com e sem pele; cru, cozido, grelhado, assado. |
| Coxa de frango | Proteínas | ✅ TACO | `TACO4:396` Frango, coxa, com pele, assada<br>`TACO4:397` Frango, coxa, com pele, crua<br>`TACO4:398` Frango, coxa, sem pele, cozida<br>`TACO4:399` Frango, coxa, sem pele, crua | Com e sem pele; crua, cozida, assada. |
| Carne bovina magra | Proteínas | 🔷 genérico → itens específicos | `TACO4:376` Carne, bovina, patinho, sem gordura, cru<br>`TACO4:377` Carne, bovina, patinho, sem gordura, grelhado<br>`TACO4:369` Carne, bovina, miolo de alcatra, sem gordura, cru<br>`TACO4:370` Carne, bovina, miolo de alcatra, sem gordura, grelhado<br>`TACO4:351` Carne, bovina, coxão mole, sem gordura, cozido<br>`TACO4:352` Carne, bovina, coxão mole, sem gordura, cru<br>`TACO4:363` Carne, bovina, lagarto, cozido<br>`TACO4:364` Carne, bovina, lagarto, cru<br>`TACO4:371` Carne, bovina, músculo, sem gordura, cozido<br>`TACO4:372` Carne, bovina, músculo, sem gordura, cru | Não existe item genérico; a busca por 'carne magra' leva aos cortes magros da TACO. |
| Patinho | Proteínas | ✅ TACO | `TACO4:376` Carne, bovina, patinho, sem gordura, cru<br>`TACO4:377` Carne, bovina, patinho, sem gordura, grelhado | Sem gordura; cru e grelhado. |
| Acém | Proteínas | ✅ TACO | `TACO4:326` Carne, bovina, acém, moído, cozido<br>`TACO4:327` Carne, bovina, acém, moído, cru<br>`TACO4:328` Carne, bovina, acém, sem gordura, cozido<br>`TACO4:329` Carne, bovina, acém, sem gordura, cru | Moído e sem gordura; cru e cozido. |
| Alcatra | Proteínas | ✅ TACO | `TACO4:369` Carne, bovina, miolo de alcatra, sem gordura, cru<br>`TACO4:370` Carne, bovina, miolo de alcatra, sem gordura, grelhado | A TACO traz 'miolo de alcatra'. |
| Carne moída | Proteínas | 🔷 genérico → itens específicos | `TACO4:326` Carne, bovina, acém, moído, cozido<br>`TACO4:327` Carne, bovina, acém, moído, cru | A composição depende do corte; a TACO traz acém moído (cru e cozido). |
| Tilápia | Proteínas | 🌎 USDA (lacuna na TACO) | `USDA_SR:175176` Tilápia, crua<br>`USDA_SR:175177` Tilápia, cozida em calor seco (assada ou grelhada) | Ausente na TACO 4ª ed. |
| Salmão | Proteínas | ✅ TACO | `TACO4:315` Salmão, filé, com pele, fresco, grelhado<br>`TACO4:316` Salmão, sem pele, fresco, cru<br>`TACO4:317` Salmão, sem pele, fresco, grelhado |  |
| Atum | Proteínas | ✅ TACO | `TACO4:277` Atum, conserva em óleo<br>`TACO4:278` Atum, fresco, cru | Fresco cru e em conserva. |
| Sardinha | Proteínas | ✅ TACO | `TACO4:318` Sardinha, assada<br>`TACO4:319` Sardinha, conserva em óleo<br>`TACO4:320` Sardinha, frita<br>`TACO4:321` Sardinha, inteira, crua |  |
| Ovo inteiro | Proteínas | ✅ TACO | `TACO4:488` Ovo, de galinha, inteiro, cozido/10minutos<br>`TACO4:489` Ovo, de galinha, inteiro, cru<br>`TACO4:490` Ovo, de galinha, inteiro, frito | Cru, cozido e frito. |
| Clara de ovo | Proteínas | ✅ TACO | `TACO4:486` Ovo, de galinha, clara, cozida/10minutos | A TACO traz só a clara cozida. |
| Leite | Proteínas | 🌎 USDA (lacuna na TACO) | `USDA_SR:172217` Leite de vaca integral, fluido (3,25% de gordura)<br>`USDA_SR:172205` Leite de vaca semidesnatado, fluido (2% de gordura)<br>`USDA_SR:173432` Leite de vaca desnatado, fluido<br>`TACO4:458` Leite, de vaca, integral<br>`TACO4:457` Leite, de vaca, desnatado, UHT<br>`TACO4:459` Leite, de vaca, integral, pó<br>`TACO4:456` Leite, de vaca, desnatado, pó | Na TACO 4ª ed., energia e macronutrientes do leite fluido estão em reavaliação (*); leite fluido completo vem do USDA. Leite em pó: TACO. |
| Iogurte natural | Proteínas | ✅ TACO | `TACO4:448` Iogurte, natural<br>`TACO4:449` Iogurte, natural, desnatado |  |
| Queijo cottage | Proteínas | 🌎 USDA (lacuna na TACO) | `USDA_SR:172179` Queijo cottage, tradicional<br>`USDA_SR:172182` Queijo cottage, light (2% de gordura) | Ausente na TACO 4ª ed. |
| Whey protein | Proteínas | 🌎 USDA (lacuna na TACO) | `USDA_SR:173177` Whey protein isolado (pó)<br>`USDA_SR:173180` Proteína em pó à base de soro de leite (whey) | Genérico; a composição varia muito por marca: prefira o rótulo. |
| Arroz branco | Carboidratos | ✅ TACO | `TACO4:3` Arroz, tipo 1, cozido<br>`TACO4:4` Arroz, tipo 1, cru<br>`TACO4:5` Arroz, tipo 2, cozido<br>`TACO4:6` Arroz, tipo 2, cru | Tipo 1 e tipo 2; cru e cozido. |
| Arroz integral | Carboidratos | ✅ TACO | `TACO4:1` Arroz, integral, cozido<br>`TACO4:2` Arroz, integral, cru |  |
| Feijão carioca | Carboidratos | ✅ TACO | `TACO4:561` Feijão, carioca, cozido<br>`TACO4:562` Feijão, carioca, cru |  |
| Feijão preto | Carboidratos | ✅ TACO | `TACO4:567` Feijão, preto, cozido<br>`TACO4:568` Feijão, preto, cru |  |
| Batata inglesa | Carboidratos | ✅ TACO | `TACO4:91` Batata, inglesa, cozida<br>`TACO4:92` Batata, inglesa, crua<br>`TACO4:93` Batata, inglesa, frita<br>`TACO4:94` Batata, inglesa, sauté |  |
| Batata-doce | Carboidratos | ✅ TACO | `TACO4:88` Batata, doce, cozida<br>`TACO4:89` Batata, doce, crua |  |
| Mandioca | Carboidratos | ✅ TACO | `TACO4:129` Mandioca, cozida<br>`TACO4:130` Mandioca, crua<br>`TACO4:132` Mandioca, frita |  |
| Macarrão | Carboidratos | 🌎 USDA (lacuna na TACO) | `TACO4:40` Macarrão, trigo, cru<br>`TACO4:41` Macarrão, trigo, cru, com ovos<br>`USDA_SR:169737` Macarrão de trigo, cozido, sem sal (massa enriquecida)<br>`USDA_SR:169751` Macarrão de trigo, cozido, com sal (massa enriquecida) | Cru na TACO; cozido só no USDA. |
| Aveia | Carboidratos | ✅ TACO | `TACO4:7` Aveia, flocos, crua | Flocos, crua. |
| Pão francês | Carboidratos | ✅ TACO | `TACO4:53` Pão, trigo, francês |  |
| Pão integral | Carboidratos | ✅ TACO | `TACO4:52` Pão, trigo, forma, integral | Pão de forma integral. |
| Tapioca | Carboidratos | 🏷️ cadastrar pelo rótulo | `TACO4:551` Tapioca, com manteiga | A TACO traz 'Tapioca, com manteiga'; para a goma hidratada pura, cadastre pelo rótulo. |
| Cuscuz | Carboidratos | ✅ TACO | `TACO4:533` Cuscuz, de milho, cozido com sal<br>`TACO4:534` Cuscuz, paulista | Cuscuz de milho cozido e cuscuz paulista. |
| Banana | Carboidratos | ✅ TACO | `TACO4:175` Banana, da terra, crua<br>`TACO4:177` Banana, figo, crua<br>`TACO4:178` Banana, maçã, crua<br>`TACO4:179` Banana, nanica, crua<br>`TACO4:180` Banana, ouro, crua<br>`TACO4:181` Banana, pacova, crua<br>`TACO4:182` Banana, prata, crua |  |
| Maçã | Carboidratos | ✅ TACO | `TACO4:221` Maçã, Argentina, com casca, crua<br>`TACO4:222` Maçã, Fuji, com casca, crua |  |
| Mamão | Carboidratos | ✅ TACO | `TACO4:225` Mamão, Formosa, cru<br>`TACO4:226` Mamão, Papaia, cru |  |
| Laranja | Carboidratos | ✅ TACO | `TACO4:208` Laranja, baía, crua<br>`TACO4:210` Laranja, da terra, crua<br>`TACO4:212` Laranja, lima, crua<br>`TACO4:214` Laranja, pêra, crua<br>`TACO4:216` Laranja, valência, crua |  |
| Azeite de oliva | Gorduras | ✅ TACO | `TACO4:260` Azeite, de oliva, extra virgem |  |
| Pasta de amendoim | Gorduras | 🌎 USDA (lacuna na TACO) | `USDA_SR:172470` Pasta de amendoim, integral, sem sal<br>`USDA_SR:174266` Pasta de amendoim, com sal | Ausente na TACO 4ª ed. |
| Amendoim | Gorduras | ✅ TACO | `TACO4:557` Amendoim, grão, cru<br>`TACO4:558` Amendoim, torrado, salgado |  |
| Castanha-do-pará | Gorduras | ✅ TACO | `TACO4:589` Castanha-do-Brasil, crua | A TACO usa 'Castanha-do-Brasil'. |
| Castanha-de-caju | Gorduras | ✅ TACO | `TACO4:588` Castanha-de-caju, torrada, salgada |  |
| Abacate | Gorduras | ✅ TACO | `TACO4:163` Abacate, cru |  |
| Manteiga | Gorduras | ✅ TACO | `TACO4:261` Manteiga, com sal<br>`TACO4:262` Manteiga, sem sal |  |
| Óleo de soja | Gorduras | ✅ TACO | `TACO4:272` Óleo, de soja |  |
| Alface | Vegetais e legumes | ✅ TACO | `TACO4:77` Alface, americana, crua<br>`TACO4:78` Alface, crespa, crua<br>`TACO4:79` Alface, lisa, crua<br>`TACO4:80` Alface, roxa, crua |  |
| Tomate | Vegetais e legumes | ✅ TACO | `TACO4:157` Tomate, com semente, cru |  |
| Cenoura | Vegetais e legumes | ✅ TACO | `TACO4:109` Cenoura, cozida<br>`TACO4:110` Cenoura, crua |  |
| Brócolis | Vegetais e legumes | ✅ TACO | `TACO4:100` Brócolis, cozido<br>`TACO4:101` Brócolis, cru |  |
| Couve | Vegetais e legumes | ✅ TACO | `TACO4:115` Couve, manteiga, crua<br>`TACO4:116` Couve, manteiga, refogada |  |
| Abobrinha | Vegetais e legumes | ✅ TACO | `TACO4:70` Abobrinha, italiana, cozida<br>`TACO4:71` Abobrinha, italiana, crua<br>`TACO4:72` Abobrinha, italiana, refogada<br>`TACO4:73` Abobrinha, paulista, crua |  |
| Pepino | Vegetais e legumes | ✅ TACO | `TACO4:142` Pepino, cru |  |
| Beterraba | Vegetais e legumes | ✅ TACO | `TACO4:97` Beterraba, cozida<br>`TACO4:98` Beterraba, crua |  |
| Espinafre | Vegetais e legumes | ✅ TACO | `TACO4:119` Espinafre, Nova Zelândia, cru<br>`TACO4:120` Espinafre, Nova Zelândia, refogado |  |
| Repolho | Vegetais e legumes | ✅ TACO | `TACO4:149` Repolho, branco, cru<br>`TACO4:150` Repolho, roxo, cru<br>`TACO4:151` Repolho, roxo, refogado |  |
| Café | Outros | ✅ TACO | `TACO4:471` Café, infusão 10%<br>`TACO4:511` Café, pó, torrado | Infusão 10% e pó torrado. |
| Açúcar | Outros | ✅ TACO | `TACO4:492` Açúcar, cristal<br>`TACO4:493` Açúcar, mascavo<br>`TACO4:494` Açúcar, refinado |  |
| Mel | Outros | ✅ TACO | `TACO4:507` Mel, de abelha |  |
| Chocolate amargo | Outros | 🌎 USDA (lacuna na TACO) | `TACO4:498` Chocolate, meio amargo<br>`USDA_SR:170273` Chocolate amargo, 70–85% de cacau<br>`USDA_SR:170272` Chocolate amargo, 60–69% de cacau | A TACO traz 'meio amargo'; amargo 60–85% vem do USDA. |
| Iogurte proteico | Proteínas | 🏷️ cadastrar pelo rótulo | `USDA_SR:170894` Iogurte grego, natural, desnatado | Produto de marca: cadastre pelo rótulo. Iogurte grego (USDA) disponível como referência, não como equivalente. |
| Granola | Carboidratos | 🏷️ cadastrar pelo rótulo | `USDA_SR:171646` Granola caseira | Receita caseira do USDA como referência; granolas comerciais variam muito: prefira o rótulo. |
| Suco de laranja | Outros | ✅ TACO | `TACO4:209` Laranja, baía, suco<br>`TACO4:211` Laranja, da terra, suco<br>`TACO4:213` Laranja, lima, suco<br>`TACO4:215` Laranja, pêra, suco<br>`TACO4:217` Laranja, valência, suco |  |
| Água de coco | Outros | ✅ TACO | `TACO4:478` Coco, água de | A TACO usa 'Coco, água de'. |

## Erros

Nenhum.

## Avisos

Avisos não bloqueiam e **não alteram valores**: os dados são mantidos como publicados.

<details><summary>W1 — 42 itens</summary>

- TACO4:71 Abobrinha, italiana, crua: Energia publicada (19.0 kcal) difere mais de 15% de 4P+4C+9G (22.5 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:72 Abobrinha, italiana, refogada: Energia publicada (24.0 kcal) difere mais de 15% de 4P+4C+9G (28.4 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:74 Acelga, crua: Energia publicada (21.0 kcal) difere mais de 15% de 4P+4C+9G (24.9 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:75 Agrião, cru: Energia publicada (17.0 kcal) difere mais de 15% de 4P+4C+9G (21.8 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:81 Alfavaca, crua: Energia publicada (29.0 kcal) difere mais de 15% de 4P+4C+9G (36.1 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:84 Almeirão, cru: Energia publicada (18.0 kcal) difere mais de 15% de 4P+4C+9G (22.2 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:100 Brócolis, cozido: Energia publicada (25.0 kcal) difere mais de 15% de 4P+4C+9G (30.5 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:101 Brócolis, cru: Energia publicada (25.0 kcal) difere mais de 15% de 4P+4C+9G (33.1 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:104 Caruru, cru: Energia publicada (34.0 kcal) difere mais de 15% de 4P+4C+9G (42.2 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:105 Catalonha, crua: Energia publicada (24.0 kcal) difere mais de 15% de 4P+4C+9G (29.5 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:108 Cebolinha, crua: Energia publicada (20.0 kcal) difere mais de 15% de 4P+4C+9G (24.8 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:113 Chuchu, cru: Energia publicada (17.0 kcal) difere mais de 15% de 4P+4C+9G (20.1 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:114 Coentro, folhas desidratadas: Energia publicada (309.0 kcal) difere mais de 15% de 4P+4C+9G (369.2 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:115 Couve, manteiga, crua: Energia publicada (27.0 kcal) difere mais de 15% de 4P+4C+9G (33.3 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:117 Couve-flor, crua: Energia publicada (23.0 kcal) difere mais de 15% de 4P+4C+9G (27.4 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:118 Couve-flor, cozida: Energia publicada (19.0 kcal) difere mais de 15% de 4P+4C+9G (23.1 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:119 Espinafre, Nova Zelândia, cru: Energia publicada (16.0 kcal) difere mais de 15% de 4P+4C+9G (20.2 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:125 Feijão, broto, cru: Energia publicada (39.0 kcal) difere mais de 15% de 4P+4C+9G (48.9 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:127 Jiló, cru: Energia publicada (27.0 kcal) difere mais de 15% de 4P+4C+9G (32.2 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:133 Manjericão, cru: Energia publicada (21.0 kcal) difere mais de 15% de 4P+4C+9G (26.0 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:135 Mostarda, folha, crua: Energia publicada (18.0 kcal) difere mais de 15% de 4P+4C+9G (23.0 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:137 Nabo, cru: Energia publicada (18.0 kcal) difere mais de 15% de 4P+4C+9G (22.1 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:138 Palmito, juçara, em conserva: Energia publicada (23.0 kcal) difere mais de 15% de 4P+4C+9G (28.0 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:139 Palmito, pupunha, em conserva: Energia publicada (29.0 kcal) difere mais de 15% de 4P+4C+9G (36.5 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:144 Pimentão, verde, cru: Energia publicada (21.0 kcal) difere mais de 15% de 4P+4C+9G (25.8 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:147 Quiabo, cru: Energia publicada (30.0 kcal) difere mais de 15% de 4P+4C+9G (35.9 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:149 Repolho, branco, cru: Energia publicada (17.0 kcal) difere mais de 15% de 4P+4C+9G (20.1 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:150 Repolho, roxo, cru: Energia publicada (31.0 kcal) difere mais de 15% de 4P+4C+9G (37.3 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:153 Salsa, crua: Energia publicada (33.0 kcal) difere mais de 15% de 4P+4C+9G (41.4 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:154 Seleta de legumes, enlatada: Energia publicada (57.0 kcal) difere mais de 15% de 4P+4C+9G (68.0 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:155 Serralha, crua: Energia publicada (30.0 kcal) difere mais de 15% de 4P+4C+9G (36.7 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:156 Taioba, crua: Energia publicada (34.0 kcal) difere mais de 15% de 4P+4C+9G (41.3 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:160 Tomate, purê: Energia publicada (28.0 kcal) difere mais de 15% de 4P+4C+9G (33.2 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:162 Vagem, crua: Energia publicada (25.0 kcal) difere mais de 15% de 4P+4C+9G (30.2 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:218 Limão, cravo, suco: Energia publicada (14.0 kcal) difere mais de 15% de 4P+4C+9G (22.0 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:219 Limão, galego, suco: Energia publicada (22.0 kcal) difere mais de 15% de 4P+4C+9G (32.5 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:220 Limão, tahiti, cru: Energia publicada (32.0 kcal) difere mais de 15% de 4P+4C+9G (48.9 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:474 Cerveja, pilsen: Energia publicada (41.0 kcal) difere mais de 15% de 4P+4C+9G (15.6 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:513 Fermento em pó, químico: Energia publicada (90.0 kcal) difere mais de 15% de 4P+4C+9G (178.5 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:514 Fermento, biológico, levedura, tablete: Energia publicada (90.0 kcal) difere mais de 15% de 4P+4C+9G (112.3 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:539 Feijão tropeiro mineiro: Energia publicada (152.0 kcal) difere mais de 15% de 4P+4C+9G (180.4 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.
- TACO4:540 Feijoada: Energia publicada (117.0 kcal) difere mais de 15% de 4P+4C+9G (139.7 kcal). Esperado com fatores de Atwater específicos, álcool ou fibra elevada; o valor publicado é mantido.

</details>

<details><summary>W2 — 7 itens</summary>

- TACO4:78 Alface, crespa, crua: Fibra (1.8 g) maior que carboidrato total (1.7 g)
- TACO4:108 Cebolinha, crua: Fibra (3.6 g) maior que carboidrato total (3.4 g)
- TACO4:128 Jurubeba, crua: Fibra (23.9 g) maior que carboidrato total (23.1 g)
- TACO4:163 Abacate, cru: Fibra (6.3 g) maior que carboidrato total (6.0 g)
- TACO4:241 Pequi, cru: Fibra (19.0 g) maior que carboidrato total (13.0 g)
- TACO4:325 Caldo de galinha, tablete: Fibra (11.8 g) maior que carboidrato total (10.6 g)
- TACO4:586 Tremoço, em conserva: Fibra (14.4 g) maior que carboidrato total (12.4 g)

</details>

<details><summary>W5 — 7 itens</summary>

- TACO4:251 Tangerina, Poncã, crua: Pequena inconsistência kcal/kJ na própria TACO (kcal×4,184 = 158.28 kJ, planilha traz 158.22 kJ); o app usa as kcal publicadas.
- TACO4:252 Tangerina, Poncã, suco: Pequena inconsistência kcal/kJ na própria TACO (kcal×4,184 = 151.08 kJ, planilha traz 150.98 kJ); o app usa as kcal publicadas.
- TACO4:256 Uva, Itália, crua: Pequena inconsistência kcal/kJ na própria TACO (kcal×4,184 = 221.22 kJ, planilha traz 221.16 kJ); o app usa as kcal publicadas.
- TACO4:292 Corvina do mar, crua: Pequena inconsistência kcal/kJ na própria TACO (kcal×4,184 = 393.30 kJ, planilha traz 391.52 kJ); o app usa as kcal publicadas.
- TACO4:492 Açúcar, cristal: Pequena inconsistência kcal/kJ na própria TACO (kcal×4,184 = 1618.56 kJ, planilha traz 1619.21 kJ); o app usa as kcal publicadas.
- TACO4:559 Ervilha, em vagem: Pequena inconsistência kcal/kJ na própria TACO (kcal×4,184 = 368.58 kJ, planilha traz 366.97 kJ); o app usa as kcal publicadas.
- TACO4:594 Linhaça, semente: Pequena inconsistência kcal/kJ na própria TACO (kcal×4,184 = 2071.48 kJ, planilha traz 2071.53 kJ); o app usa as kcal publicadas.

</details>

## Diferenças em relação ao asset anterior

Versão anterior: 1. Adicionados: 3. Removidos: 0. Alterados: 2.

- + USDA_SR:172205 Leite de vaca semidesnatado, fluido (2% de gordura)
- + USDA_SR:172217 Leite de vaca integral, fluido (3,25% de gordura)
- + USDA_SR:173432 Leite de vaca desnatado, fluido
- ~ TACO4:457 Leite, de vaca, desnatado, UHT: sinônimos
- ~ TACO4:458 Leite, de vaca, integral: sinônimos
