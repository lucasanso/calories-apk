# Código aberto: sem ofuscação (nomes preservados evitam falhas por reflexão em Room, Hilt e
# kotlinx.serialization). O R8 continua removendo código não usado e otimizando.
-dontobfuscate

# Rotas com enum: a navegação tipada encontra a classe do enum pelo nome (Class.forName) e lê
# as constantes por reflexão. Manter a classe intacta (sem "enum unboxing" do R8).
-keep enum com.calories.navigation.PickTarget { *; }
