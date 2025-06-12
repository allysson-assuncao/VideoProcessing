import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.util.ArrayList;
import java.util.List;

public class VideoProcessing2 {

    static {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (Exception e) {
            System.err.println("Erro ao carregar OpenCV: " + e.getMessage());
        }
    }

    public static byte[][][] carregarVideo(String caminho) {
        VideoCapture captura = new VideoCapture(caminho);
        if (!captura.isOpened()) {
            System.err.println("Erro ao abrir o vídeo: " + caminho);
            return new byte[0][][];
        }

        int largura = (int) captura.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int altura = (int) captura.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        List<byte[][]> frames = new ArrayList<>();
        Mat matrizRGB = new Mat();
        Mat escalaCinza = new Mat(altura, largura, CvType.CV_8UC1);
        byte[] linha = new byte[largura];

        while (captura.read(matrizRGB)) {
            Imgproc.cvtColor(matrizRGB, escalaCinza, Imgproc.COLOR_BGR2GRAY);
            byte[][] pixelsFrame = new byte[altura][largura];
            for (int y = 0; y < altura; y++) {
                escalaCinza.get(y, 0, linha);
                System.arraycopy(linha, 0, pixelsFrame[y], 0, largura);
            }
            frames.add(pixelsFrame);
        }
        captura.release();

        if (frames.isEmpty()) {
            System.err.println("Nenhum frame carregado do vídeo: " + caminho);
            return new byte[0][][];
        }

        return frames.toArray(new byte[0][][]);
    }

    public static void gravarVideo(byte[][][] pixels, String caminho, double fps) {
        if (pixels == null || pixels.length == 0) {
            System.err.println("Nenhum pixel para gravar.");
            return;
        }
        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        int fourcc = VideoWriter.fourcc('a', 'v', 'c', '1');
        VideoWriter escritor = new VideoWriter(caminho, fourcc, fps, new Size(largura, altura), false);

        if (!escritor.isOpened()) {
            System.err.println("Erro ao iniciar VideoWriter para: " + caminho);
            return;
        }

        Mat frameCinza = new Mat(altura, largura, CvType.CV_8UC1);
        for (int f = 0; f < qFrames; f++) {
            for (int y = 0; y < altura; y++) {
                frameCinza.put(y, 0, pixels[f][y]);
            }
            escritor.write(frameCinza);
        }
        escritor.release();
        frameCinza.release();
    }

    public static byte[][][] deepCopy(byte[][][] original) {
        if (original == null) return null;
        byte[][][] result = new byte[original.length][][];
        for (int i = 0; i < original.length; i++) {
            if (original[i] == null) continue;
            result[i] = new byte[original[i].length][];
            for (int j = 0; j < original[i].length; j++) {
                if (original[i][j] == null) continue;
                result[i][j] = new byte[original[i][j].length];
                System.arraycopy(original[i][j], 0, result[i][j], 0, original[i][j].length);
            }
        }
        return result;
    }

    /**
     * Cria uma submatriz (tira) com margens (padding) preenchidas.
     */
    private static byte[][] createPaddedTile(byte[][] fullFrame, int startY, int height, int padding) {
        int frameHeight = fullFrame.length;
        int frameWidth = fullFrame[0].length;
        int endY = startY + height;

        int paddedTileHeight = height + 2 * padding;
        int paddedTileWidth = frameWidth + 2 * padding;

        byte[][] paddedTile = new byte[paddedTileHeight][paddedTileWidth];

        // Copia a parte principal da tira
        for (int i = 0; i < height; i++) {
            System.arraycopy(fullFrame[startY + i], 0, paddedTile[padding + i], padding, frameWidth);
        }

        // Preenche as margens superior e inferior
        byte[] topPaddingRow = (startY > 0) ? fullFrame[startY - 1] : fullFrame[startY];
        byte[] bottomPaddingRow = (endY < frameHeight) ? fullFrame[endY] : fullFrame[endY - 1];
        System.arraycopy(topPaddingRow, 0, paddedTile[0], padding, frameWidth);
        System.arraycopy(bottomPaddingRow, 0, paddedTile[paddedTileHeight - 1], padding, frameWidth);

        // Preenche as margens esquerda e direita (e cantos)
        for (int y = 0; y < paddedTileHeight; y++) {
            paddedTile[y][0] = paddedTile[y][1]; // Replica o primeiro pixel de dados
            paddedTile[y][paddedTileWidth - 1] = paddedTile[y][paddedTileWidth - 2]; // Replica o último
        }

        return paddedTile;
    }

    public static void main(String[] args) {
        String caminhoVideo = "video-3s.mp4"; // Coloque seu vídeo de teste aqui
        String caminhoGravar = "video-3s-processed.mp4";
        double fps = 24.0;

        System.out.println("Carregando o vídeo... " + caminhoVideo);
        long tempoInicioTotal = System.currentTimeMillis();

        byte[][][] originalPixels = carregarVideo(caminhoVideo);
        if (originalPixels.length == 0) {
            System.out.println("Falha ao carregar o vídeo. Encerrando.");
            return;
        }
        // 'processedPixels' é a matriz que será modificada a cada etapa
        byte[][][] processedPixels = deepCopy(originalPixels);

        int totalFrames = originalPixels.length;
        int height = originalPixels[0].length;
        int width = originalPixels[0][0].length;

        int numThreads = Runtime.getRuntime().availableProcessors();
        System.out.println("Utilizando " + numThreads + " threads.");
        System.out.printf("Frames: %d | Resolução: %d x %d \n", totalFrames, width, height);


        // Parâmetros dos filtros
        int numPassesSalPimenta = 10;
        int raioSalPimenta = 1;
        int padding = raioSalPimenta; // O padding necessário é igual ao raio do filtro

        long tempoInicioProcessamento = System.currentTimeMillis();

        // --- LOOP PRINCIPAL DE PROCESSAMENTO FRAME A FRAME ---
        for (int f = 0; f < totalFrames; f++) {
            System.out.printf("Processando frame %d/%d...\n", f + 1, totalFrames);

            // ETAPA 1: REMOVER BORRÃO TEMPORAL
            // Aplica-se apenas a frames que têm vizinhos f-1 e f+2
            if (f > 0 && f < totalFrames - 2) {
                byte[][] tempOutputFrame = new byte[height][width];
                List<Thread> threads = new ArrayList<>();
                int rowsPerThread = height / numThreads;
                int startY = 0;

                for (int i = 0; i < numThreads; i++) {
                    int rowsForThisThread = (i == numThreads - 1) ? (height - startY) : rowsPerThread;
                    byte[][] tile = createPaddedTile(originalPixels[f], startY, rowsForThisThread, padding);
                    threads.add(new ImageProcessor2(tile, startY, rowsForThisThread, f, originalPixels, tempOutputFrame, padding, raioSalPimenta, ImageProcessor2.Task.TEMPORAL_BLUR));
                    startY += rowsForThisThread;
                }

                for (Thread t : threads) t.start();
                for (Thread t : threads) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                processedPixels[f] = tempOutputFrame; // Atualiza o frame com o resultado do borrão
            }

            // ETAPA 2: REMOVER RUÍDO SAL E PIMENTA (ITERATIVAMENTE)
            for (int pass = 0; pass < numPassesSalPimenta; pass++) {
                byte[][] inputFrameForSP = processedPixels[f]; // Usa o resultado da etapa anterior
                byte[][] tempOutputFrame = new byte[height][width];
                List<Thread> threads = new ArrayList<>();
                int rowsPerThread = height / numThreads;
                int startY = 0;

                for (int i = 0; i < numThreads; i++) {
                    int rowsForThisThread = (i == numThreads - 1) ? (height - startY) : rowsPerThread;
                    byte[][] tile = createPaddedTile(inputFrameForSP, startY, rowsForThisThread, padding);
                    threads.add(new ImageProcessor2(tile, startY, rowsForThisThread, f, null, tempOutputFrame, padding, raioSalPimenta, ImageProcessor2.Task.SALT_PEPPER));
                    startY += rowsForThisThread;
                }

                for (Thread t : threads) t.start();
                for (Thread t : threads) {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                processedPixels[f] = tempOutputFrame; // Atualiza o frame com o resultado do filtro S&P
            }
        }

        long tempoFimProcessamento = System.currentTimeMillis();
        System.out.println("Tempo de processamento dos filtros: " + (tempoFimProcessamento - tempoInicioProcessamento) + " ms");

        System.out.println("Salvando o vídeo corrigido... " + caminhoGravar);
        gravarVideo(processedPixels, caminhoGravar, fps);

        long tempoFimTotal = System.currentTimeMillis();
        System.out.println("Tempo total de execução: " + (tempoFimTotal - tempoInicioTotal) + " ms");
        System.out.println("Processamento concluído!");
    }
}