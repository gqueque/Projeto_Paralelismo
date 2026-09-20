# Projeto de Paralelismo - Programação Paralela, Concorrente e Distribuída

Projeto da disciplina de Programação Paralela, Concorrente e Distribuída (Sistema de Informação, 5º período), sob a tutoria do professor Rafael Nunes.

O objetivo é usar como base um processamento pesado e custoso, nesse caso uma matriz onde cada elemento passa por um cálculo trigonométrico caro, e comparar diferentes formas de paralelizar esse processamento, evoluindo de uma versão sequencial até versões com paralelismo estruturado e estado compartilhado.

## Status do projeto

- [x] V1 - Sequencial (fornecida pelo professor)
- [x] V2 - Paralelismo não estruturado
- [x] V3 - Paralelismo estruturado
- [x] V4 - Estado compartilhado (DoubleAdder e ConcurrentLinkedQueue)
- [x] Experimentos de desempenho automatizados (`experimentos/ExperimentoRunner.java`)

## Estrutura do repositório

```
paralelismo-projeto/
├── sequencial/
│   └── Sequencial.java
├── naoestruturado/
│   └── ParalelismoNaoEstruturado.java
├── estruturado/
│   └── ParalelismoEstruturado.java
├── estadocompartilhado/
│   ├── ComAtomic.java
│   └── ComConcurrentQueue.java
├── experimentos/
│   └── ExperimentoRunner.java
├── rodar-tudo.ps1
└── readme.md
```

Cada pasta é um pacote Java diferente, um para cada versão do projeto. O `experimentos/` não é uma versão em si - é uma ferramenta que chama as outras 4 automaticamente para gerar os dados de desempenho.

## O problema

O cálculo pesado é o mesmo em todas as versões - a função `calcular()` faz 1000 iterações de seno/cosseno/raiz por elemento da matriz:

```java
private static double calcular(double valor) {
    double resultado = valor;
    for (int i = 0; i < 1000; i++) {
        resultado += Math.sin(valor + i) * Math.cos(valor - i) * Math.sqrt(Math.abs(valor) + 1);
    }
    return resultado;
}
```

O que muda entre as versões é como essa função é chamada para todos os elementos da matriz: uma por vez, ou em paralelo, e como o resultado final é montado.

## V1 - Sequencial

Versão de referência (baseline), fornecida pelo professor. Percorre a matriz inteira com um único `for` duplo, chamando `calcular()` elemento por elemento, tudo em uma única thread.

```
Matriz → loop sequencial → calcular() por elemento → soma acumulada → resultado
```

Essa versão não é paralela de propósito: é o tempo dela que serve de comparação (speedup) para todas as outras versões.

## V2 - Paralelismo não estruturado

A matriz é dividida em `N` blocos de linhas. Cada bloco vira uma tarefa que é enviada para um `ExecutorService`, que gerencia um pool de threads. Cada tarefa processa seu pedaço de forma independente e devolve um resultado parcial através de um `Future<Double>`. No final, os parciais de todas as tarefas são somados.

```
Matriz → divide em N blocos → ExecutorService cria N tarefas → threads processam em paralelo
       → future.get() coleta cada parcial → soma final → shutdown()
```

Chama-se de "não estruturado" porque não existe uma relação explícita no código dizendo que essas tarefas pertencem a um mesmo bloco de execução - é responsabilidade do programador lembrar de coletar todos os `Future`s e de encerrar o executor (`shutdown()`) manualmente. Se uma tarefa falhar, as outras continuam rodando à toa, sem cancelamento automático.

O resultado numérico é idêntico ao da V1 (só muda o tempo de execução), o que é usado para validar que a paralelização não introduziu erro de cálculo.

## V3 - Paralelismo estruturado

Resolve o mesmo problema que a V2, mas usando `StructuredTaskScope` (API de concorrência estruturada, ainda em preview no Java 25) em vez de `ExecutorService`/`Future`.

```
Matriz → divide em N blocos → scope.fork() cria subtarefas → threads processam em paralelo
       → scope.join() espera TODAS terminarem → subtask.get() coleta cada parcial → resultado
```

A diferença central em relação à V2 não é só de sintaxe: o `StructuredTaskScope` obriga que todas as subtarefas criadas dentro do escopo (`try (var scope = ...)`) sejam esperadas (`join()`) antes que o programa saia do bloco. Não existe como "esquecer" de esperar uma tarefa, como acontecia na V2. Além disso, se uma subtarefa falhar, as demais são canceladas automaticamente - não sobra trabalho rodando à toa.

O resultado numérico também bate com o das versões anteriores (com pequenas variações na última casa decimal, explicadas na seção de observações abaixo).

## V4 - Estado compartilhado

A partir da mesma estrutura da V3, as tarefas deixam de devolver um resultado parcial para o método principal e passam a escrever diretamente num **estado compartilhado** entre todas elas. Foram implementadas duas variantes, resolvendo o mesmo problema de formas diferentes:

**V4a - `DoubleAdder` (`estadocompartilhado/ComAtomic.java`)**

Uma única variável compartilhada (`DoubleAdder`) recebe a soma de todas as tarefas via `.add()`. Ela é "thread-safe" por construção - protege internamente contra duas threads escrevendo ao mesmo tempo, evitando que uma atualização sobrescreva a outra (condição de corrida).

```
Matriz → divide em N blocos → scope.fork() cria subtarefas → cada tarefa soma direto no DoubleAdder
       → scope.join() → resultado = somaTotal.sum()
```

**V4b - `ConcurrentLinkedQueue` (`estadocompartilhado/ComConcurrentQueue.java`)**

Em vez de somar direto numa variável compartilhada, cada tarefa calcula seu próprio resultado parcial e o deposita como um item novo numa fila compartilhada (`ConcurrentLinkedQueue<Double>`). Como cada tarefa só adiciona o seu próprio item (nunca sobrescreve o de outra), não existe risco de condição de corrida na escrita. No final, a fila inteira é somada.

```
Matriz → divide em N blocos → scope.fork() cria subtarefas → cada tarefa dá resultados.add(parcial)
       → scope.join() → resultado = soma de todos os itens da fila
```

As duas variantes resolvem o mesmo risco (escrita concorrente insegura) por caminhos diferentes: uma soma de forma protegida direto num só lugar, a outra evita a escrita compartilhada acumulando itens separados e somando depois.

## Ausência de deadlock, livelock e starvation

Nenhuma das implementações usa locks manuais (`synchronized`, `Lock`, `wait`/`notify`), o que já elimina a principal fonte de deadlock (duas threads esperando uma pela outra por recursos travados). A justificativa por versão:

- **V2 e V3**: cada tarefa processa um bloco de linhas totalmente independente das demais - não há comunicação nem dependência entre tarefas durante a execução, então não há como uma tarefa bloquear esperando outra.
- **V4a**: o `DoubleAdder` já implementa internamente uma estratégia de atualização sem bloqueio (lock-free), então não há espera bloqueante entre as threads que escrevem nele.
- **V4b**: `ConcurrentLinkedQueue` também é uma estrutura lock-free - cada `.add()` é uma operação atômica que não trava a fila para as demais threads.
- **Starvation**: como o `numTarefas` sempre divide o trabalho em blocos de tamanho parecido (`Math.ceil`), nenhuma tarefa fica sistematicamente maior que as outras a ponto de nunca conseguir CPU - o `ExecutorService`/`StructuredTaskScope` distribui as tarefas entre as threads disponíveis de forma justa.

Testado rodando cada versão múltiplas vezes seguidas (inclusive dentro do `ExperimentoRunner`, que soma 10 repetições por configuração) sem travamentos observados, nas 4 execuções completas dos experimentos (500x500 até 2000x2000).

## Como compilar e rodar

Projeto sem Maven/Gradle. Os comandos abaixo devem ser rodados a partir da pasta raiz do repositório (a que contém as pastas `sequencial/`, `naoestruturado/`, etc.). As versões V3, V4 e o `ExperimentoRunner` usam `StructuredTaskScope`, que ainda é uma funcionalidade em *preview* do Java - por isso precisam da flag `--enable-preview` tanto para compilar quanto para rodar, além de `--release 25` (ajuste esse número para a versão do JDK instalada, se for diferente).

### V1 - Sequencial

```
javac sequencial/Sequencial.java -d bin
java -cp bin sequencial.Sequencial
```

### V2 - Paralelismo não estruturado

```
javac naoestruturado/ParalelismoNaoEstruturado.java -d bin
java -cp bin naoestruturado.ParalelismoNaoEstruturado
```

### V3 - Paralelismo estruturado

```
javac --release 25 --enable-preview estruturado/ParalelismoEstruturado.java -d bin
java --enable-preview -cp bin estruturado.ParalelismoEstruturado
```

### V4a - Estado compartilhado (DoubleAdder)

```
javac --release 25 --enable-preview estadocompartilhado/ComAtomic.java -d bin
java --enable-preview -cp bin estadocompartilhado.ComAtomic
```

### V4b - Estado compartilhado (ConcurrentLinkedQueue)

```
javac --release 25 --enable-preview estadocompartilhado/ComConcurrentQueue.java -d bin
java --enable-preview -cp bin estadocompartilhado.ComConcurrentQueue
```

Todas imprimem o resultado do cálculo e o tempo de execução em milissegundos:

```
Resultado: <valor>
Tempo: <valor> ms
```

O "Resultado" deve ser o mesmo (ou praticamente idêntico, ver observação abaixo) em todas as versões - só o "Tempo" deve variar.

### Rodando tudo de uma vez

O script `rodar-tudo.ps1`, na raiz do projeto, compila e roda as 5 versões em sequência, já rotuladas:

```
.\rodar-tudo.ps1
```

### Rodando os experimentos de desempenho automaticamente

O `ExperimentoRunner` compila junto com as outras versões (ele depende delas) e roda automaticamente todas as combinações de tamanho de matriz (500x500 a 2000x2000) e quantidade de tarefas (5, 10, 100), 10 repetições cada, já calculando tempo médio e speedup:

```
javac --release 25 --enable-preview sequencial/Sequencial.java naoestruturado/ParalelismoNaoEstruturado.java estruturado/ParalelismoEstruturado.java estadocompartilhado/ComAtomic.java estadocompartilhado/ComConcurrentQueue.java experimentos/ExperimentoRunner.java -d bin
java --enable-preview -cp bin experimentos.ExperimentoRunner > resultados.txt
```

> Se estiver usando IntelliJ, configure o Language Level do projeto para a versão de preview correspondente (File → Project Structure → Project) e adicione `--enable-preview` nas VM options de cada Run Configuration, para poder rodar direto pelo botão de play.

## Requisitos

- JDK 25 (ou a versão instalada na máquina - ajustar o número em `--release` nos comandos acima de acordo)

## Observação sobre pequenas diferenças no resultado

Os valores de "Resultado" entre as versões podem diferir levemente na última casa decimal (por exemplo, `...528295E7` vs `...528634E7`). Isso não é um erro: como cada versão soma os resultados parciais das threads em uma ordem diferente (dependendo de qual thread termina primeiro), e a soma de números `double` não é associativa (`(a+b)+c` pode arredondar de forma ligeiramente diferente de `a+(b+c)`), pequenas variações de arredondamento são esperadas e não indicam falha na paralelização.

## Diagrama de arquitetura
<img width="6192" height="3650" alt="diagrama" src="https://github.com/user-attachments/assets/c98c6b5c-1bca-44ea-b1f5-48cd1ec36b77" />


## Resultados dos experimentos

Cada implementação foi executada 10 vezes para cada combinação de tamanho de matriz e quantidade de tarefas, através do `experimentos/ExperimentoRunner.java`. Os valores abaixo são o tempo médio das 10 execuções.

### E1 — Matriz 500×500

| Implementação                    | 5 tarefas (ms) | 10 tarefas (ms) | 100 tarefas (ms) |
|-----------------------------------|-----------------|-------------------|---------------------|
| Sequencial                        | 4028,57         | -                 | -                    |
| Paralelismo não estruturado       | 1180,40         | 726,34            | 736,83               |
| Paralelismo estruturado           | 1231,39         | 718,60            | 711,88               |
| Estruturado + DoubleAdder         | 1191,15         | 688,32            | 715,08               |
| Estruturado + coleção concorrente | 1228,04         | 691,54            | 711,22               |

### E2 — Matriz 1000×1000

| Implementação                    | 5 tarefas (ms) | 10 tarefas (ms) | 100 tarefas (ms) |
|-----------------------------------|-----------------|-------------------|---------------------|
| Sequencial                        | 16209,25        | -                 | -                    |
| Paralelismo não estruturado       | 4459,38         | 2647,35           | 2564,75              |
| Paralelismo estruturado           | 4487,45         | 2624,31           | 2579,55              |
| Estruturado + DoubleAdder         | 4365,25         | 2639,94           | 2593,50              |
| Estruturado + coleção concorrente | 3948,09         | 2630,73           | 2609,84              |

### E3 — Matriz 1500×1500

| Implementação                    | 5 tarefas (ms) | 10 tarefas (ms) | 100 tarefas (ms) |
|-----------------------------------|-----------------|-------------------|---------------------|
| Sequencial                        | 36701,74        | -                 | -                    |
| Paralelismo não estruturado       | 8930,71         | 6439,66           | 5572,52              |
| Paralelismo estruturado           | 8735,41         | 6108,91           | 5796,62              |
| Estruturado + DoubleAdder         | 9546,64         | 5958,21           | 5916,09              |
| Estruturado + coleção concorrente | 9565,83         | 5826,28           | 5871,79              |

### E4 — Matriz 2000×2000

| Implementação                    | 5 tarefas (ms) | 10 tarefas (ms) | 100 tarefas (ms) |
|-----------------------------------|-----------------|-------------------|---------------------|
| Sequencial                        | 62928,68        | -                 | -                    |
| Paralelismo não estruturado       | 16986,56        | 11295,10          | 9948,63              |
| Paralelismo estruturado           | 17118,30        | 10426,03          | 10392,88             |
| Estruturado + DoubleAdder         | 17667,10        | 10399,65          | 10367,90             |
| Estruturado + coleção concorrente | 16979,59        | 10358,52          | 10485,73             |

## Análise dos resultados

Alguns padrões consistentes aparecem nas 4 matrizes testadas:

1. **O maior salto de speedup acontece de 5 para 10 tarefas.** Em todas as matrizes, dobrar de 5 para 10 tarefas quase dobra o speedup (ex.: na matriz 2000x2000, o não estruturado vai de 3,70x para 5,57x). Isso indica que a máquina usada nos testes tem em torno de 8-10 núcleos lógicos disponíveis - com poucas tarefas (5), sobra capacidade de CPU ociosa.
2. **De 10 para 100 tarefas, o ganho passa a ser pequeno ou praticamente nulo** (e em alguns casos até piora ligeiramente, como a V4b na matriz 2000x2000: 10358,52 ms com 10 tarefas vs 10485,73 ms com 100). Isso acontece porque, uma vez que o número de tarefas já ultrapassa o número de núcleos físicos, criar ainda mais tarefas só adiciona overhead de gerenciamento (troca de contexto entre threads) sem trazer paralelismo real adicional.
3. **As 4 implementações paralelas (V2, V3, V4a, V4b) têm desempenho muito próximo entre si** para a mesma quantidade de tarefas - as diferenças percentuais entre elas costumam ficar abaixo de 5%. Isso é esperado: a forma de gerenciar as tarefas (Executor vs StructuredTaskScope) e a forma de agregar o resultado (Future vs estado compartilhado) têm impacto pequeno perto do custo dominante, que é o próprio cálculo trigonométrico pesado em `calcular()`.
4. **O speedup nunca chega perto do número de tarefas usado.** Mesmo com 100 tarefas, o speedup fica na faixa de 6x-6,3x, nunca 100x - isso é o comportamento esperado, já que o speedup real é limitado pelo número de núcleos físicos da CPU, não pelo número de tarefas criadas (Lei de Amdahl na prática).

## 10. Comparação das implementações

Melhor configuração de cada implementação, com base nos experimentos na matriz de maior porte (2000×2000, que melhor evidencia o comportamento assintótico):

| Implementação                     | Tempo médio (ms) | Speedup | Quantidade de Tarefas | Resultado correto |
|-------------------------------------|--------------------|-----------|--------------------------|----------------------|
| Sequencial                          | 62928,68           | 1,00      | -                        | ✅                   |
| Paralelismo não estruturado         | 9948,63            | 6,33      | 100                      | ✅                   |
| Paralelismo estruturado             | 10392,88           | 6,05      | 100                      | ✅                   |
| Estruturado + AtomicInteger (DoubleAdder) | 10367,90     | 6,07      | 100                      | ✅                   |
| Estruturado + coleção concorrente   | 10358,52            | 6,08      | 10                       | ✅                   |

> Speedup = Tempo sequencial / Tempo paralelo
> "Resultado correto" = ✅ o valor bateu com a versão sequencial (com diferença apenas de arredondamento de ponto flutuante, ver observação acima).
> Tabelas completas com os 3 tamanhos de tarefa testados para cada uma das 4 matrizes estão na seção "Resultados dos experimentos".
