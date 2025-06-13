import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe principal que gerencia o carregamento, processamento paralelo e gravação do vídeo.
 */
public class VideoProcessing2 {

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    private final byte[][][] pixels;
    private final int numThreads;

    public VideoProcessing2(String videoPath) {
        this.pixels = carregarVideo(videoPath);
        // Define o número de threads com base nos processadores disponíveis
        this.numThreads = Runtime.getRuntime().availableProcessors();
    }

    public byte[][][] getPixels() {
        return pixels;
    }

    public int getFramesCount() {
        return pixels.length;
    }

    public int getWidth() {
        return pixels[0][0].length;
    }

    public int getHeight() {
        return pixels[0].length;
    }


    public void removerSalPimenta(int radius) {
        int height = getHeight();
        int width = getWidth();
        int rowsPerThread = height / numThreads;

        for (int f = 0; f < getFramesCount(); f++) {
            byte[][] currentFrame = pixels[f];
            byte[][] processedFrame = new byte[height][width];
            byte[][] paddedFrame = addPadding(currentFrame, radius);
            List<ImageProcessor2> threads = new ArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                int startY = i * rowsPerThread;
                int endY = (i == numThreads - 1) ? height : startY + rowsPerThread;
                ImageProcessor2 thread = new ImageProcessor2(paddedFrame, processedFrame, startY, endY, radius);
                threads.add(thread);
                thread.start();
            }

            waitForThreads(threads);
            pixels[f] = processedFrame;
        }
    }

    /**
     * VERSÃO CORRIGIDA:
     * Mantém o loop de frames sequencial para respeitar a dependência de dados,
     * mas paraleliza o processamento DENTRO de cada frame.
     */
    public void removerBorroesTempo() {
        int height = getHeight();
        int width = getWidth();
        int rowsPerThread = height / numThreads;

        // O loop sobre os frames (f) é mantido de forma SEQUENCIAL
        for (int f = 1; f < getFramesCount() - 2; f++) {
            // Lê diretamente do array 'pixels', que é atualizado a cada iteração
            byte[][] previousFrame = pixels[f - 1];
            byte[][] currentFrame = pixels[f];
            byte[][] nextFrame = pixels[f + 2];
            byte[][] processedFrame = new byte[height][width]; // Frame de resultado para esta iteração

            List<ImageProcessor2> threads = new ArrayList<>();
            // A paralelização ocorre AQUI, para processar o 'processedFrame' mais rápido
            for (int i = 0; i < numThreads; i++) {
                int startY = i * rowsPerThread;
                int endY = (i == numThreads - 1) ? height : startY + rowsPerThread;
                ImageProcessor2 thread = new ImageProcessor2(previousFrame, currentFrame, nextFrame, processedFrame, startY, endY);
                threads.add(thread);
                thread.start();
            }

            // Espera todas as threads terminarem de processar o frame atual
            waitForThreads(threads);

            // Atualiza o frame principal. Agora, na próxima iteração (f+1),
            // a leitura de 'pixels[f]' pegará este resultado.
            pixels[f] = processedFrame;
        }
    }

    private byte[][] addPadding(byte[][] original, int radius) {
        int height = original.length;
        int width = original[0].length;
        byte[][] padded = new byte[height + 2 * radius][width + 2 * radius];

        // Copia a imagem original para o centro da matriz com margem
        for (int y = 0; y < height; y++) {
            System.arraycopy(original[y], 0, padded[y + radius], radius, width);
        }
        // Simplesmente deixamos a borda como 0 (preto), o que é suficiente para este caso.
        // Estratégias mais complexas como espelhamento poderiam ser implementadas se necessário.
        return padded;
    }

    private void waitForThreads(List<ImageProcessor2> threads) {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void gravarVideo(String caminho, double fps) {
        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        int fourcc = VideoWriter.fourcc('a', 'v', 'c', '1');
        VideoWriter escritor = new VideoWriter(caminho, fourcc, fps, new Size(largura, altura), true);

        if (!escritor.isOpened()) {
            System.err.println("Erro ao gravar vídeo no caminho corrigido");
            return;
        }

        Mat matrizRgb = new Mat(altura, largura, CvType.CV_8UC3);
        byte[] linha = new byte[largura * 3];

        for (int f = 0; f < qFrames; f++) {
            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {
                    byte g = (byte) pixels[f][y][x];
                    int i = x * 3;
                    linha[i] = linha[i + 1] = linha[i + 2] = g;
                }
                matrizRgb.put(y, 0, linha);
            }
            escritor.write(matrizRgb);
        }
        escritor.release();
    }

    private byte[][][] carregarVideo(String caminho) {
        VideoCapture captura = new VideoCapture(caminho);
        int largura = (int) captura.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int altura = (int) captura.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        List<byte[][]> frames = new ArrayList<>();
        Mat matrizRGB = new Mat();
        Mat escalaCinza = new Mat(altura, largura, CvType.CV_8UC1);
        byte[] linha = new byte[largura];

        while (captura.read(matrizRGB)) {
            Imgproc.cvtColor(matrizRGB, escalaCinza, Imgproc.COLOR_BGR2GRAY);
            byte[][] pixels = new byte[altura][largura];
            for (int y = 0; y < altura; y++) {
                escalaCinza.get(y, 0, linha);
                for (int x = 0; x < largura; x++) {
                    pixels[y][x] = (byte) (linha[x] & 0xFF);
                }
            }
            frames.add(pixels);
        }
        captura.release();

        byte[][][] cuboPixels = new byte[frames.size()][][];
        return frames.toArray(cuboPixels);
    }

    public void removerBorroesComMovimento() {
        int height = getHeight();
        int width = getWidth();
        int rowsPerThread = height / numThreads;

        // O loop sobre os frames (f) continua sequencial
        for (int f = 1; f < getFramesCount() - 2; f++) {
            System.out.println("Processando frame " + f + " com compensação de movimento...");

            byte[][] previousBytes = pixels[f - 1];
            byte[][] currentBytes = pixels[f];
            byte[][] nextBytes = pixels[f + 2];

            // Converte os frames de byte[] para Mat para usar no OpenCV
            Mat prevMat = new Mat(height, width, CvType.CV_8UC1);
            Mat currMat = new Mat(height, width, CvType.CV_8UC1);
            Mat nextMat = new Mat(height, width, CvType.CV_8UC1);
            prevMat.put(0, 0, previousBytes);
            currMat.put(0, 0, currentBytes);
            nextMat.put(0, 0, nextBytes);

            // --- CÁLCULO DO FLUXO ÓPTICO ---
            // Cria matrizes para armazenar os campos de vetores de fluxo
            Mat flowBackward = new Mat(); // Fluxo de 'curr' para 'prev'
            Mat flowForward = new Mat();  // Fluxo de 'curr' para 'next'

            // Calcula o fluxo para trás
            Video.calcOpticalFlowFarneback(currMat, prevMat, flowBackward, 0.5, 3, 15, 3, 5, 1.2, 0);

            // Calcula o fluxo para frente
            Video.calcOpticalFlowFarneback(currMat, nextMat, flowForward, 0.5, 3, 15, 3, 5, 1.2, 0);

            // --- FIM DO CÁLCULO ---

            byte[][] processedFrame = new byte[height][width];
            List<ImageProcessor2> threads = new ArrayList<>();

            // Passa os frames E os campos de fluxo para as threads
            for (int i = 0; i < numThreads; i++) {
                int startY = i * rowsPerThread;
                int endY = (i == numThreads - 1) ? height : startY + rowsPerThread;
                ImageProcessor2 thread = new ImageProcessor2(previousBytes, currentBytes, nextBytes, processedFrame, startY, endY, flowBackward, flowForward);
                threads.add(thread);
                thread.start();
            }

            waitForThreads(threads);
            pixels[f] = processedFrame;

            // Libera a memória das matrizes do OpenCV
            prevMat.release();
            currMat.release();
            nextMat.release();
            flowBackward.release();
            flowForward.release();
        }
    }

    public static void main(String[] args) {
        String caminhoVideo = "video-3s.mp4";
        String caminhoGravar = "video-3s-parallel.mp4";
        double fps = 24.0;

        System.out.println("Carregando o vídeo... " + caminhoVideo);
        VideoProcessing2 videoProcessor = new VideoProcessing2(caminhoVideo);

        System.out.printf("Processamento paralelo iniciado. Frames: %d  Resolução: %d x %d Threads: %d \n",
                videoProcessor.getFramesCount(), videoProcessor.getWidth(), videoProcessor.getHeight(), videoProcessor.numThreads);

        // Pro 2: Remover borrões (executado uma vez)
        System.out.println("Processamento: removendo borrão com compensação de movimento...");
        long startTimeBlur = System.currentTimeMillis();
        videoProcessor.removerBorroesComMovimento(); // Chamando o novo método
        long endTimeBlur = System.currentTimeMillis();
        System.out.printf("Remoção de borrão concluída em %d ms\n", (endTimeBlur - startTimeBlur));

        // Pro 1: Remover sal e pimenta (executado várias vezes)
        long totalTimeSaltPepper = 0;
        for (int i = 0; i < 10; i++) {
            System.out.println("Processamento: removendo sal e pimenta " + (i + 1));
            long startTime = System.currentTimeMillis();
            videoProcessor.removerSalPimenta(1); // Raio 1
            long endTime = System.currentTimeMillis();
            totalTimeSaltPepper += (endTime - startTime);
            System.out.printf("Iteração %d concluída em %d ms\n", (i + 1), (endTime - startTime));
        }
        System.out.printf("Remoção de sal e pimenta concluída. Tempo total: %d ms\n", totalTimeSaltPepper);

        System.out.println("Salvando... " + caminhoGravar);
        videoProcessor.gravarVideo(caminhoGravar, fps);
        System.out.println("Término do processamento.");
    }
}