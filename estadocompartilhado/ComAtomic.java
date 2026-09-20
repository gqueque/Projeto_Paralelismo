package estadocompartilhado;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.DoubleAdder;

public class ComAtomic {

    // Mesma função de custo pesado do enunciado - não mexer
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


    private static void processarBloco(double[][] matriz, int linhaInicio, int linhaFim, DoubleAdder somaTotal) {
        for (int i = linhaInicio; i < linhaFim; i++) {
            for (int j = 0; j < matriz[i].length; j++) {
                somaTotal.add(calcular(matriz[i][j]));
            }
        }
    }

    public static double processarComAtomic(double[][] matriz, int numTarefas) throws InterruptedException {
        int totalLinhas = matriz.length;
        int linhasPorTarefa = (int) Math.ceil((double) totalLinhas / numTarefas);


        DoubleAdder somaTotal = new DoubleAdder();

        try (var scope = StructuredTaskScope.open()) {

            for (int t = 0; t < numTarefas; t++) {
                int linhaInicio = t * linhasPorTarefa;
                int linhaFim = Math.min(linhaInicio + linhasPorTarefa, totalLinhas);

                if (linhaInicio >= linhaFim) {
                    continue;
                }


                scope.fork(() -> {
                    processarBloco(matriz, linhaInicio, linhaFim, somaTotal);
                    return null;
                });
            }

            scope.join();

        }


        return somaTotal.sum();
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
        double resultado = processarComAtomic(matriz, numTarefas);
        long fim = System.nanoTime();

        System.out.println("Resultado: " + resultado);
        System.out.println("Tempo: " + (fim - inicio) / 1_000_000 + " ms");
    }
}