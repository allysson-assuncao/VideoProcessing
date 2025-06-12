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
     * NOVO MÉTODO AUXILIAR
     * Cria um único frame com margens (padding) ao redor.
     */
    private static byte[][] createPaddedFrame(byte[][] originalFrame, int padding) {
        int height = originalFrame.length;
        int width = originalFrame[0].length;
        int paddedHeight = height + 2 * padding;
        int paddedWidth = width + 2 * padding;

        byte[][] paddedFrame = new byte[paddedHeight][paddedWidth];

        // 1. Copia o conteúdo original para o centro do novo frame
        for (int i = 0; i < height; i++) {
            System.arraycopy(originalFrame[i], 0, paddedFrame[i + padding], padding, width);
        }

        // 2. Preenche as margens superior e inferior replicando as bordas
        for (int p = 0; p < padding; p++) {
            System.arraycopy(paddedFrame[padding], 0, paddedFrame[p], 0, paddedWidth); // Topo
            System.arraycopy(paddedFrame[paddedHeight - 1 - padding], 0, paddedFrame[paddedHeight - 1 - p], 0, paddedWidth); // Fundo
        }

        // 3. Preenche as margens laterais replicando as bordas
        for (int y = 0; y < paddedHeight; y++) {
            for (int p = 0; p < padding; p++) {
                paddedFrame[y][p] = paddedFrame[y][padding]; // Esquerda
                paddedFrame[y][paddedWidth - 1 - p] = paddedFrame[y][paddedWidth - 1 - padding]; // Direita
            }
        }

        return paddedFrame;
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
        byte[][][] processedPixels = deepCopy(originalPixels);

        int totalFrames = originalPixels.length;
        int height = originalPixels[0].length;
        int width = originalPixels[0][0].length;
        int numThreads = Runtime.getRuntime().availableProcessors();

        System.out.println("Utilizando " + numThreads + " threads.");
        System.out.printf("Frames: %d | Resolução: %d x %d \n", totalFrames, width, height);

        int numPassesSalPimenta = 10;
        int raioSalPimenta = 1;
        int padding = 2;

        long tempoInicioProcessamento = System.currentTimeMillis();

        // --- LOOP PRINCIPAL DE PROCESSAMENTO FRAME A FRAME ---
        for (int f = 0; f < totalFrames; f++) {
            System.out.printf("\rProcessando frame %d/%d...", f + 1, totalFrames);

            // ETAPA 1: REMOVER BORRÃO TEMPORAL
            if (f > 0 && f < totalFrames - 2) {
                byte[][] tempOutputFrame = new byte[height][width];
                // Cria os frames com margem UMA VEZ para a tarefa de borrão
                byte[][] paddedAnterior = createPaddedFrame(originalPixels[f - 1], padding);
                byte[][] paddedAtual = createPaddedFrame(originalPixels[f], padding);
                byte[][] paddedProximo = createPaddedFrame(originalPixels[f + 2], padding);

                List<Thread> threads = new ArrayList<>();
                int rowsPerThread = height / numThreads;
                int startY = 0;
                for (int i = 0; i < numThreads; i++) {
                    int sliceHeight = (i == numThreads - 1) ? (height - startY) : rowsPerThread;
                    threads.add(new ImageProcessor2(ImageProcessor2.Task.TEMPORAL_BLUR, null,
                            paddedAnterior, paddedAtual, paddedProximo, tempOutputFrame,
                            startY, sliceHeight, padding, raioSalPimenta));
                    startY += sliceHeight;
                }
                for (Thread t : threads) t.start();
                for (Thread t : threads) { try { t.join(); } catch (InterruptedException e) { e.printStackTrace(); } }
                processedPixels[f] = tempOutputFrame;
            }

            // ETAPA 2: REMOVER RUÍDO SAL E PIMENTA (ITERATIVAMENTE)
            for (int pass = 0; pass < numPassesSalPimenta; pass++) {
                byte[][] inputFrameForSP = processedPixels[f];
                byte[][] tempOutputFrame = new byte[height][width];
                // Cria o frame com margem UMA VEZ para cada passada de S&P
                byte[][] paddedInput = createPaddedFrame(inputFrameForSP, padding);

                List<Thread> threads = new ArrayList<>();
                int rowsPerThread = height / numThreads;
                int startY = 0;
                for (int i = 0; i < numThreads; i++) {
                    int sliceHeight = (i == numThreads - 1) ? (height - startY) : rowsPerThread;
                    threads.add(new ImageProcessor2(ImageProcessor2.Task.SALT_PEPPER, paddedInput,
                            null, null, null, tempOutputFrame,
                            startY, sliceHeight, padding, raioSalPimenta));
                    startY += sliceHeight;
                }
                for (Thread t : threads) t.start();
                for (Thread t : threads) { try { t.join(); } catch (InterruptedException e) { e.printStackTrace(); } }
                processedPixels[f] = tempOutputFrame;
            }
        }

        System.out.println("\nProcessamento dos filtros concluído.");
        long tempoFimProcessamento = System.currentTimeMillis();
        System.out.println("Tempo de processamento dos filtros: " + (tempoFimProcessamento - tempoInicioProcessamento) + " ms");

        System.out.println("Salvando o vídeo corrigido... " + caminhoGravar);
        gravarVideo(processedPixels, caminhoGravar, fps);

        long tempoFimTotal = System.currentTimeMillis();
        System.out.println("Tempo total de execução: " + (tempoFimTotal - tempoInicioTotal) + " ms");
        System.out.println("Processamento concluído!");
    }
}
