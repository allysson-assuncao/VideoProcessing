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
 * Renomeada para VideoProcessing2.
 */
public class VideoProcessing2 {

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    private final byte[][][] pixels;
    private final int numThreads;

    public VideoProcessing2(String videoPath) {
        this.pixels = carregarVideo(videoPath);
        this.numThreads = Runtime.getRuntime().availableProcessors();
    }

    public int getFramesCount() {
        return pixels.length;
    }

    public int getWidth() {
        return pixels.length > 0 ? pixels[0][0].length : 0;
    }

    public int getHeight() {
        return pixels.length > 0 ? pixels[0].length : 0;
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

    public void removerBorroesComMovimento() {
        int height = getHeight();
        int width = getWidth();
        int rowsPerThread = height / numThreads;

        for (int f = 1; f < getFramesCount() - 2; f++) {
            System.out.println("Processando frame " + f + " com compensação de movimento...");

            byte[][] previousBytes = pixels[f - 1];
            byte[][] currentBytes = pixels[f];
            byte[][] nextBytes = pixels[f + 2];

            Mat prevMat = new Mat(height, width, CvType.CV_8UC1);
            Mat currMat = new Mat(height, width, CvType.CV_8UC1);
            Mat nextMat = new Mat(height, width, CvType.CV_8UC1);

            for (int i = 0; i < height; i++) {
                prevMat.put(i, 0, previousBytes[i]);
                currMat.put(i, 0, currentBytes[i]);
                nextMat.put(i, 0, nextBytes[i]);
            }

            Mat flowBackward = new Mat();
            Mat flowForward = new Mat();

            Video.calcOpticalFlowFarneback(currMat, prevMat, flowBackward, 0.5, 3, 15, 3, 5, 1.2, 0);
            Video.calcOpticalFlowFarneback(currMat, nextMat, flowForward, 0.5, 3, 15, 3, 5, 1.2, 0);

            byte[][] processedFrame = new byte[height][width];
            List<ImageProcessor2> threads = new ArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                int startY = i * rowsPerThread;
                int endY = (i == numThreads - 1) ? height : startY + rowsPerThread;
                ImageProcessor2 thread = new ImageProcessor2(previousBytes, currentBytes, nextBytes, processedFrame, startY, endY, flowBackward, flowForward);
                threads.add(thread);
                thread.start();
            }

            waitForThreads(threads);
            pixels[f] = processedFrame;

            prevMat.release();
            currMat.release();
            nextMat.release();
            flowBackward.release();
            flowForward.release();
        }
    }

    private byte[][] addPadding(byte[][] original, int radius) {
        int height = original.length;
        int width = original[0].length;
        byte[][] padded = new byte[height + 2 * radius][width + 2 * radius];
        for (int y = 0; y < height; y++) {
            System.arraycopy(original[y], 0, padded[y + radius], radius, width);
        }
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
        int altura = getHeight();
        int largura = getWidth();

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
            byte[][] pixelsFrame = new byte[altura][largura];
            for (int y = 0; y < altura; y++) {
                escalaCinza.get(y, 0, linha);
                System.arraycopy(linha, 0, pixelsFrame[y], 0, largura);
            }
            frames.add(pixelsFrame);
        }
        captura.release();

        byte[][][] cuboPixels = new byte[frames.size()][][];
        return frames.toArray(cuboPixels);
    }

    public static void main(String[] args) {
        String caminhoVideo = "video-3s.mp4";
        String caminhoGravar = "video-3s-corrigido.mp4";
        double fps = 24.0;

        System.out.println("Carregando o vídeo... " + caminhoVideo);
        VideoProcessing2 videoProcessor = new VideoProcessing2(caminhoVideo);

        System.out.printf("Processamento paralelo iniciado. Frames: %d  Resolução: %d x %d Threads: %d \n",
                videoProcessor.getFramesCount(), videoProcessor.getWidth(), videoProcessor.getHeight(), videoProcessor.numThreads);

        long startTime = System.currentTimeMillis();

        // Etapa 1: Remover borrões com COMPENSAÇÃO DE MOVIMENTO
        videoProcessor.removerBorroesComMovimento();

        // Etapa 2: Remover ruído "sal e pimenta"
        System.out.println("Processamento: removendo sal e pimenta...");
        for (int i = 0; i < 5; i++) { // Reduzi o número de iterações para um teste mais rápido
            System.out.println("...iteração " + (i + 1));
            videoProcessor.removerSalPimenta(1);
        }

        long endTime = System.currentTimeMillis();
        System.out.printf("Processamento de filtros concluído em %d ms\n", (endTime - startTime));

        System.out.println("Salvando... " + caminhoGravar);
        videoProcessor.gravarVideo(caminhoGravar, fps);
        System.out.println("Término do processamento. Vídeo salvo em: " + caminhoGravar);
    }
}