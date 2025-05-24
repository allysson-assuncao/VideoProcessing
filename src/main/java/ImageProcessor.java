import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ImageProcessor extends Thread {
    private final byte[][][] frames;
    private final int startFrame;
    private final int endFrame;

    public ImageProcessor(byte[][][] frames, int startFrame, int endFrame) {
        this.frames = frames;
        this.startFrame = startFrame;
        this.endFrame = endFrame;
    }

    @Override
    public void run() {
        for (int i = startFrame; i < endFrame; i++) {
            removerBorroes(frames[i], i);
            removerSal(frames[i]);
        }
    }

    // Metodo que corrige os borroes de um frame
    private void removerBorroes(byte[][] frame, int indice) {
        // Percorre a imagem e busca regioes com muitos pontos com cores extremas


    }

    private void removerSal(byte[][] frame) {
        int rows = frame.length;
        int cols = frame[0].length;
        byte[][] novoFrame = new byte[rows][cols];
        int LIMIAR = 30; // Limiar para considerar um pixel como outlier

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                // Coletar vizinhos 3x3
                List<Byte> vizinhos = new ArrayList<>();
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        int ni = i + di;
                        int nj = j + dj;
                        if (ni >= 0 && ni < rows && nj >= 0 && nj < cols) {
                            vizinhos.add(frame[ni][nj]);
                        }
                    }
                }
                // Calcular mediana
                Collections.sort(vizinhos);
                byte mediana = vizinhos.get(vizinhos.size() / 2);

                // Verificar se pixel é outlier
                if (Math.abs((frame[i][j] & 0xFF) - (mediana & 0xFF)) > LIMIAR) {
                    novoFrame[i][j] = mediana;
                } else {
                    novoFrame[i][j] = frame[i][j];
                }
            }
        }

        // Copiar resultado de volta para o frame original
        for (int i = 0; i < rows; i++) {
            System.arraycopy(novoFrame[i], 0, frame[i], 0, cols);
        }
    }
}

/*

    1 - normal
    2 - borrao preto
    3 - borrao preto
    4 - borrao preto
    5 - normal
    6 - normal
    7 - normal

    1 1 1 1
    1 1 1 1
    1 1 1 1
    1 1 1 1

 */