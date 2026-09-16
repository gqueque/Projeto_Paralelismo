# Projeto de Paralelismo - Programação Paralela, Concorrente e Distribuída

Projeto da disciplina de Programação Paralela, Concorrente e Distribuída (Sistema de Informação, 5º período), sob a tutoria do professor Rafael Nunes.

O objetivo é usar como base um processamento pesado e custoso, nesse caso uma matriz onde cada elemento passa por um cálculo trigonométrico caro e comparar diferentes formas de paralelizar esse processamento, evoluindo de uma versão sequencial até versões com paralelismo estruturado e estado compartilhado.

## Status do projeto

- [x] V1 - Sequencial (fornecida pelo professor)
- [x] V2 - Paralelismo não estruturado
- [ ] V3 - Paralelismo estruturado 
- [ ] V4 - Estado compartilhado 

Este README cobre as versões já implementadas (V1 e V2). Conforme as próximas etapas forem entrando, o README será atualizado.

## Estrutura do repositório

```
paralelismo-projeto/
├── sequencial/
│   └── Sequencial.java
├── naoestruturado/
│   └── ParalelismoNaoEstruturado.java
├── estruturado/            (em desenvolvimento)
└── estadocompartilhado/    (em desenvolvimento)
```

Cada pasta é um pacote Java diferente, um para cada versão do projeto.

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

O que muda entre as versões é como essa função é chamada para todos os elementos da matriz: uma por vez, ou em paralelo.

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

Chama-se de "não estruturado" porque não existe uma relação explícita no código dizendo que essas tarefas pertencem a um mesmo bloco de execução - é responsabilidade do programador lembrar de coletar todos os `Future`s e de encerrar o executor (`shutdown()`) manualmente.

O resultado numérico é idêntico ao da V1 (só muda o tempo de execução), o que é usado para validar que a paralelização não introduziu erro de cálculo.

## Como compilar e rodar

Projeto sem Maven/Gradle. Os comandos abaixo devem ser rodados a partir da pasta raiz do repositório (a que contém as pastas `sequencial/`, `naoestruturado/`, etc.).

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

Ambos imprimem o resultado do cálculo e o tempo de execução em milissegundos:

```
Resultado: <valor>
Tempo: <valor> ms
```

Para comparar as duas, basta rodar as duas em sequência e comparar o "Tempo" impresso - o "Resultado" deve ser o mesmo nas duas.


## Requisitos

- JDK 21 ou superior (necessário para as próximas versões, que usam `StructuredTaskScope`)