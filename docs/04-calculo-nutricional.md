# 4. Estratégia de cálculo nutricional

Todo cálculo fica em `:domain/nutrition`, em funções puras (sem I/O e sem relógio) testadas na JVM. A interface só formata.

## 4.1 Fórmula base

```
valor consumido = (quantidade consumida na unidade de referência ÷ quantidade de referência) × valor de referência
```

Implementação (ordem das operações escolhida para reduzir erro de ponto flutuante):

```kotlin
fun scale(referenceValue: Double, amountInRefUnit: Double, referenceAmount: Double): Double =
    referenceValue * amountInRefUnit / referenceAmount
```

A fórmula vale para **todos** os nutrientes (energia, proteína, carboidrato, gordura, gordura saturada, fibra e sódio) e para qualquer quantidade de referência. Alimentos da biblioteca usam 100 g. Personalizados usam o que o rótulo indicar (30 g, 200 ml…), sem conversão prévia.

Exemplos que viram testes automatizados:

| Caso | Entrada | Resultado esperado (sem arredondar) |
|---|---|---|
| Exemplo do requisito | 165 kcal/100 g; consome 150 g | 247,5 kcal |
| Referência diferente de 100 | Rótulo: 120 kcal por 30 g; consome 45 g | 180,0 kcal |
| Quantidade fracionária | 3,2 g de proteína/100 g; consome 12,5 g | 0,4 g |
| Quantidade igual à referência | 100 g de um item com 9,0 g de fibra | 9,0 g |
| Mesma porção, outro nutriente | 51 mg de sódio/100 g; 150 g | 76,5 mg |

## 4.2 Status de cada nutriente

Cada nutriente de um alimento está em um de quatro estados. O domínio modela isso explicitamente:

```kotlin
sealed interface NutrientValue {
    data class Measured(val amount: Double) : NutrientValue  // valor numérico da fonte
    data object Trace : NutrientValue                         // "Tr": presente abaixo do limite de quantificação
    data object NotApplicable : NutrientValue                 // "NA": não se aplica ao alimento
    data object NotAvailable : NutrientValue                  // não analisado / sem dado
}
```

| Estado | Persistência | Escala | Contribuição na soma | Exibição |
|---|---|---|---|---|
| `Measured(x)` | `x` | `x × q ÷ ref` | soma normalmente | número |
| `Trace` | `0` + bit em `traceMask` | continua `Trace` | 0 (a fonte afirma que a quantidade é desprezível) | "Tr" |
| `NotApplicable` | `0` + bit em `notApplicableMask` | continua `NotApplicable` | 0 (o nutriente não existe no alimento, ex.: fibra em carne) | "NA" |
| `NotAvailable` | `NULL` | continua `NotAvailable` | **não soma**: o total fica **parcial** | "—" |

Tratar "Tr" e "NA" como 0 na soma segue o que a fonte afirma, não é inventar dado. Já `NotAvailable` nunca vira zero.

## 4.3 Agregação: refeição, dia e período

```kotlin
data class NutrientTotal(val sum: Double, val missingCount: Int, val itemCount: Int) {
    val isComplete get() = missingCount == 0
    val hasAnyData get() = missingCount < itemCount
}
```

- **Refeição** = soma dos itens. **Dia** = soma das refeições, calculada direto dos itens e não de totais já arredondados.
- Total parcial: fibra de `[5,0 g; sem dado; 2,0 g]` resulta em `sum = 7,0`, `missingCount = 1`, exibido como **"7 g · parcial"**. Um toque mostra quais alimentos estão sem o dado.
- Se nenhum item tem o dado (`missingCount == itemCount`), a interface mostra "—", e não "0 g".
- **Médias de período** (semana e mês) consideram só os dias com algum registro (`entryCount > 0`). A interface informa quantos dias entraram na média ("média de 5 dias registrados").
- No banco, a view `daily_nutrition` faz as mesmas somas com contagem de ausentes. Um teste compara a agregação do SQL com a do `NutrientAggregator` sobre os mesmos dados.

### Base do carboidrato

O carboidrato exibido é sempre o **total**, que inclui fibra (ver 03, §3.3). Isso evita misturar "carboidrato total" de uma fonte com "carboidrato disponível" de outra no mesmo total diário. Carboidrato líquido (total − fibra) não é calculado na v1.

## 4.4 Unidades e conversões

```kotlin
enum class MeasureUnit { GRAM, MILLILITER }
sealed interface QuantityInput {
    data class Mass(val grams: Double) : QuantityInput
    data class Volume(val milliliters: Double) : QuantityInput
    data class Portion(val portionId: Long, val count: Double) : QuantityInput
}
```

O `UnitConverter` resolve a entrada para a unidade de referência do alimento (`amountInRefUnit`):

| Entrada \ Referência | `GRAM` | `MILLILITER` |
|---|---|---|
| g | direto | `g ÷ densidade` (exige `densityGPerMl`) |
| ml | `ml × densidade` (exige `densityGPerMl`) | direto |
| porção | `count × amountInReferenceUnit` | `count × amountInReferenceUnit` |

- Sem densidade documentada, a opção de unidade alternativa **não aparece**. Não existe densidade padrão "1 g/ml".
- Validação: quantidade finita, maior que 0 e até 10.000 g ou ml por item. Acima de 2.000 aparece uma confirmação ("tem certeza?"), para pegar erros de digitação como 1500 em vez de 150.
- O `diary_entry` guarda o que foi digitado (`inputAmount` e `inputUnit`) **e** o valor normalizado (`amountInRefUnit`). A interface sempre mostra a quantidade como o usuário a informou.

## 4.5 Arredondamento (só na apresentação)

| Grandeza | Resumos (anel, cartões, histórico) | Detalhe do item / prévia da quantidade |
|---|---|---|
| Energia | inteiro: `248 kcal` | 1 casa: `247,5 kcal` |
| Proteína, carboidrato, gordura, fibra, saturada | 1 casa: `31,0 g` | 1 casa |
| Sódio | inteiro: `77 mg` | inteiro |
| Percentual da meta | inteiro: `82%` | — |
| Peso corporal | 1 casa: `78,4 kg` | — |

- O arredondamento é `HALF_UP` sobre `BigDecimal.valueOf(double)`, que parte da representação decimal mais curta e evita que 247,5 vire 247,4999… e arredonde para 247.
- Separador decimal pt-BR (vírgula) e separador de milhar (ponto).
- **Somas e percentuais usam valores não arredondados.** Por isso a soma das refeições exibidas pode diferir em ±1 do total do dia exibido. É esperado, e o total do dia é o correto.

## 4.6 Metas e progresso

```kotlin
data class GoalProgress(val consumed: Double, val goal: Double?) {
    val remaining get() = goal?.let { it - consumed }                   // negativo = acima da meta
    val fraction  get() = goal?.takeIf { it > 0 }?.let { consumed / it } // não é limitado a 1
}
```

- **Restante negativo** é exibido como "142 kcal acima da meta", sem sinal negativo.
- A barra de progresso visual vai até 100%. Acima disso, muda de estilo (borda ou cor de aviso) e mostra o percentual real, por exemplo 112%.
- **Meta do dia D:** a `nutrition_goal` de maior `effectiveFrom ≤ D`. Se não houver, a meta é `null` e a interface mostra só o consumido, com um convite para definir a meta.
- Metas de macronutrientes podem ser definidas em **gramas** ou derivadas de **percentual da energia**. A conversão usa 4 kcal/g para proteína e carboidrato e 9 kcal/g para gordura, identificada na interface como convenção de planejamento. O valor salvo é sempre em gramas.

## 4.7 Calculadora de estimativa energética (opcional)

> Identificada na interface como **estimativa aproximada, não prescrição nutricional**. Todos os resultados são editáveis antes de salvar. A tela recomenda acompanhamento profissional em caso de condição de saúde, gestação, uso de medicação ou objetivo agressivo.

**Taxa metabólica basal: equação de Mifflin-St Jeor** (Mifflin MD, St Jeor ST et al. *Am J Clin Nutr*. 1990;51(2):241–247):

```
masculino: TMB = 10 × peso(kg) + 6,25 × altura(cm) − 5 × idade(anos) + 5
feminino:  TMB = 10 × peso(kg) + 6,25 × altura(cm) − 5 × idade(anos) − 161
```

O sexo biológico é pedido **só** porque a equação exige. A tela explica isso.

**Gasto total estimado** = TMB × fator de atividade:

| Nível | Fator |
|---|---|
| Sedentário | 1,2 |
| Levemente ativo | 1,375 |
| Moderadamente ativo | 1,55 |
| Muito ativo | 1,725 |
| Extremamente ativo | 1,9 |

São multiplicadores convencionais, amplamente usados. A interface os apresenta como tal.

**Objetivo:**
- Manutenção = gasto total.
- Perda = gasto total − ajuste.
- Ganho = gasto total + ajuste.

O **ajuste** é escolhido pelo usuário (10%, 15% ou 20%, ou um valor em kcal) e fica visível na tela, nunca escondido. Se a meta ficar abaixo da TMB estimada, aparece um aviso informativo.

**Sugestão de macros** (editável):
- Proteína em g/kg de peso, definida pelo usuário.
- Gordura em % da energia, definida pelo usuário.
- Carboidrato = energia restante ÷ 4.
- Fibra: sugestão de 14 g por 1.000 kcal, a referência de ingestão adequada das DRIs (Institute of Medicine, 2005), citada na tela.

Vetores de teste:

| Perfil | TMB esperada | × 1,55 |
|---|---|---|
| Masculino, 30 anos, 80 kg, 180 cm | 10·80 + 6,25·180 − 5·30 + 5 = **1.780 kcal** | 2.759 kcal |
| Feminino, 25 anos, 60 kg, 165 cm | 10·60 + 6,25·165 − 5·25 − 161 = **1.345,25 kcal** | 2.085,1375 kcal |

Os dados do perfil (idade ou data de nascimento, sexo, altura, nível de atividade, objetivo) ficam só no DataStore local. O peso usado é o último `weight_record`, que pode ser alterado na tela.

## 4.8 Consolidação da lista de compras

`ShoppingListConsolidator` recebe os itens das refeições planejadas selecionadas (por intervalo de datas e, se o usuário quiser, só alguns slots) e agrupa por `foodId` e unidade de referência:

```
Plano: 150 g frango (almoço, dia 1) + 150 g frango (jantar, dia 1) + 100 g frango (almoço, dia 2)
Lista: Frango, peito, sem pele, grelhado — 400 g
```

- O agrupamento é por **alimento exato** (inclui o preparo). "Arroz cozido" e "arroz cru" não se somam, porque são registros e massas diferentes.
- **Peso pronto × peso cru:** se o plano usa um alimento preparado (ex.: frango grelhado), a lista mostra a quantidade **do alimento preparado**, com a nota "peso do alimento pronto". Converter para peso cru exigiria fatores de cocção. Eles não estão nas fontes da v1 e não serão inventados. O usuário pode editar a quantidade.
- Itens manuais ("detergente", "1 dz ovos") coexistem com os gerados. Marcar como comprado só altera `isChecked`.
- Gerar novamente cria uma lista nova. Listas anteriores não são sobrescritas.

## 4.9 Matriz de testes do motor nutricional

| Componente | Casos |
|---|---|
| `NutritionCalculator` | Vetores de 4.1; quantidade 0 ou negativa rejeitada; NaN e infinito rejeitados; referência ≠ 100; propagação de `Trace`, `NotApplicable` e `NotAvailable`. |
| `UnitConverter` | g→g, ml→ml, g↔ml com densidade, ml sem densidade (erro tipado), porções. |
| `NutrientAggregator` | Soma de refeição e de dia; total parcial; todos ausentes; total do dia igual à soma das refeições sem arredondar; equivalência com a view SQL. |
| `GoalProgressCalculator` | Abaixo, igual e acima da meta; meta nula; meta zero; meta vigente por data (troca no meio do mês). |
| `EnergyEstimator` | Vetores de 4.7; validação de faixas (idade 15–100, peso 20–400 kg, altura 100–250 cm); ajustes de objetivo. |
| `ShoppingListConsolidator` | Exemplo de 400 g; preparos diferentes não se somam; unidades diferentes não se somam; seleção por slot. |
| Formatação (`:app`) | `247.5 → "247,5"` e `"248"`; `0.05 → "0,1"` (HALF_UP); "Tr", "NA", "—"; percentuais acima de 100%. |
| Snapshot | Editar ou excluir um alimento depois do registro não altera os valores do registro; editar a quantidade recalcula a partir do snapshot. |
