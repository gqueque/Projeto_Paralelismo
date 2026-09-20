package experimentos;

import naoestruturado.ParalelismoNaoEstruturado;
import estruturado.ParalelismoEstruturado;
import estadocompartilhado.ComAtomic;
import estadocompartilhado.ComConcurrentQueue;

public class ExperimentoRunner {

    private static final int[] TAMANHOS = {500, 1000, 1500, 2000};
    private static final int[] TAREFAS = {5, 10, 100};
    private static final int REPETICOES = 10;

    // Cópia da função pesada do enunciado - usada só para o baseline sequencial
    private static double calcular(double valor) {
        double resultado = valor;
        for (int i = 0; i < 1000; i++) {
            resultado +=
                Math.sin(valor + i)
                * Math.cos(valor - i)
                * Math.sqrt(Math.abs(valor) + 1);
        }
        return resultado;
    }

    private static double processarSequencial(double[][] matriz) {
        double resultado = 0.0;
        for (int i = 0; i < matriz.length; i++) {
            for (int j = 0; j < matriz[i].length; j++) {
                resultado += calcular(matriz[i][j]);
            }
        }
        return resultado;
    }

    private static double[][] gerarMatriz(int tamanho) {
        double[][] matriz = new double[tamanho][tamanho];
        for (int i = 0; i < tamanho; i++) {
            for (int j = 0; j < tamanho; j++) {
                matriz[i][j] = (i + j) * 0.5;
            }
        }
        return matriz;
    }

    @FunctionalInterface
    interface Tarefa {
        double executar() throws Exception;
    }

    private static void rodar(String nome, int numTarefas, double tempoSequencial, Tarefa tarefa) throws Exception {
        double somaTempo = 0;
        double ultimoResultado = 0;
        for (int r = 0; r < REPETICOES; r++) {
            long ini = System.nanoTime();
            ultimoResultado = tarefa.executar();
            long fim = System.nanoTime();
            somaTempo += (fim - ini) / 1_000_000.0;
        }
        double tempoMedio = somaTempo / REPETICOES;
        double speedup = tempoSequencial / tempoMedio;
        System.out.printf(
            "%-30s | tarefas=%-4d | tempo medio=%10.2f ms | speedup=%6.2fx | resultado=%s%n",
            nome, numTarefas, tempoMedio, speedup, ultimoResultado
        );
    }

    public static void main(String[] args) throws Exception {
        for (int tamanho : TAMANHOS) {
            System.out.println();
            System.out.println("=========================================");
            System.out.println("Matriz " + tamanho + "x" + tamanho);
            System.out.println("=========================================");

            double[][] matriz = gerarMatriz(tamanho);

            // Sequencial nao depende de numTarefas - roda uma unica vez (10 repeticoes)
            double somaTempoSeq = 0;
            double resultadoSeq = 0;
            for (int r = 0; r < REPETICOES; r++) {
                long ini = System.nanoTime();
                resultadoSeq = processarSequencial(matriz);
                long fim = System.nanoTime();
                somaTempoSeq += (fim - ini) / 1_000_000.0;
            }
            double tempoMedioSeq = somaTempoSeq / REPETICOES;
            System.out.printf(
                "%-30s | tarefas=%-4s | tempo medio=%10.2f ms | speedup=%6.2fx | resultado=%s%n",
                "Sequencial", "-", tempoMedioSeq, 1.0, resultadoSeq
            );

            for (int numTarefas : TAREFAS) {
                System.out.println("--- numTarefas = " + numTarefas + " ---");

                rodar("Nao estruturado", numTarefas, tempoMedioSeq, () ->
                    ParalelismoNaoEstruturado.processarParalelo(matriz, numTarefas));

                rodar("Estruturado", numTarefas, tempoMedioSeq, () ->
                    ParalelismoEstruturado.processarEstruturado(matriz, numTarefas));

                rodar("Estruturado + DoubleAdder", numTarefas, tempoMedioSeq, () ->
                    ComAtomic.processarComAtomic(matriz, numTarefas));

                rodar("Estruturado + Fila", numTarefas, tempoMedioSeq, () ->
                    ComConcurrentQueue.processarComFila(matriz, numTarefas));
            }
        }

        System.out.println();
        System.out.println("Experimentos finalizados.");
    }
}
