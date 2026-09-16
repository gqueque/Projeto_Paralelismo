package sequencial;

public class Sequencial {

    // Função de custo pesado fornecida pelo professor - não mexer
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

    private static double processar(double[][] matriz) {
        double resultado = 0.0;
        for (int i = 0; i < matriz.length; i++) {
            for (int j = 0; j < matriz[i].length; j++) {
                resultado += calcular(matriz[i][j]);
            }
        }
        return resultado;
    }

    public static void main(String[] args) {
        int tamanho = 500;
        double[][] matriz = new double[tamanho][tamanho];
        for (int i = 0; i < tamanho; i++) {
            for (int j = 0; j < tamanho; j++) {
                matriz[i][j] = (i + j) * 0.5;
            }
        }

        long inicio = System.nanoTime();
        double resultado = processar(matriz);
        long fim = System.nanoTime();

        System.out.println("Resultado: " + resultado);
        System.out.println("Tempo: " + (fim - inicio) / 1_000_000 + " ms");
    }
}
