package naoestruturado;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ParalelismoNaoEstruturado {

    // func do enunciado
    private static double calcular(double valor) {
        double resultado = valor;
        for (int i = 0; i < 1000; i++) {
            resultado +=
                    Math.sin(valor + i)
                            * Math.cos(valor - i)
                            * Math.sqrt(Math.abs(valor) + 1);
        }
        return resultado;git init
    }


    private static double processarBloco(double[][] matriz, int linhaInicio, int linhaFim) {
        double resultado = 0.0;
        for (int i = linhaInicio; i < linhaFim; i++) {
            for (int j = 0; j < matriz[i].length; j++) {
                resultado += calcular(matriz[i][j]);
            }
        }
        return resultado;
    }

    // numTarefas = em quantos pedaços a matriz vai ser dividida (5, 10, 100...)
    public static double processarParalelo(double[][] matriz, int numTarefas) throws InterruptedException, ExecutionException {
        int totalLinhas = matriz.length;
        int linhasPorTarefa = (int) Math.ceil((double) totalLinhas / numTarefas);

        ExecutorService executor = Executors.newFixedThreadPool(numTarefas);
        List<Future<Double>> futures = new ArrayList<>();

        try {
            for (int t = 0; t < numTarefas; t++) {
                int linhaInicio = t * linhasPorTarefa;
                int linhaFim = Math.min(linhaInicio + linhasPorTarefa, totalLinhas);

                if (linhaInicio >= linhaFim) {
                    continue;
                }

                Callable<Double> tarefa = () -> processarBloco(matriz, linhaInicio, linhaFim);
                futures.add(executor.submit(tarefa));
            }

            double resultadoFinal = 0.0;
            for (Future<Double> future : futures) {
                resultadoFinal += future.get(); // .get() bloqueia até a tarefa terminar
            }
            return resultadoFinal;

        } finally {
            executor.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        int tamanho = 500;
        double[][] matriz = new double[tamanho][tamanho];
        for (int i = 0; i < tamanho; i++) {
            for (int j = 0; j < tamanho; j++) {
                matriz[i][j] = (i + j) * 0.5;
            }
        }

        int numTarefas = 10;

        long inicio = System.nanoTime();
        double resultado = processarParalelo(matriz, numTarefas);
        long fim = System.nanoTime();

        System.out.println("Resultado: " + resultado);
        System.out.println("Tempo: " + (fim - inicio) / 1_000_000 + " ms");
    }
}