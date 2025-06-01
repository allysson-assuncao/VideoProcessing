import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

public class VideoProcessing {

    // Carrega a biblioteca nativa (via nu.pattern.OpenCV) assim que a classe é carregada na VM.
    static {
        try {
            nu.pattern.OpenCV.loadLocally();
            System.out.println("OpenCV carregado com sucesso!");
        } catch (Exception e) {
            System.err.println("Erro ao carregar OpenCV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static byte[][][] carregarVideo(String caminho) {

        VideoCapture captura = new VideoCapture(caminho);
        if (!captura.isOpened()) {
            System.out.println("Vídeo está sendo processado por outra aplicação");
            return null;
        }

        // tamanho do frame
        int largura = (int) captura.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int altura = (int) captura.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        // não conheço a quantidade dos frames (melhorar com outra lib) :(
        List<byte[][]> frames = new ArrayList<>();

        // matriz RGB mesmo preto e branco?? - uso na leitura do frame
        Mat matrizRGB = new Mat();

        // criando uma matriz temporária em escala de cinza
        Mat escalaCinza = new Mat(altura, largura, CvType.CV_8UC1); // 1 única escala
        byte[] linha = new byte[largura];

        while (captura.read(matrizRGB)) {//leitura até o último frames

            // convertemos o frame atual para escala de cinza
            Imgproc.cvtColor(matrizRGB, escalaCinza, Imgproc.COLOR_BGR2GRAY);

            // criamos uma matriz para armazenar o valor de cada pixel (int estouro de memória)
            byte[][] pixels = new byte[altura][largura];
            for (int y = 0; y < altura; y++) {
                escalaCinza.get(y, 0, linha);
                for (int x = 0; x < largura; x++) {
                    pixels[y][x] = (byte) (linha[x] & 0xFF); // shift de correção - unsig
                }
            }
            frames.add(pixels);
        }
        captura.release();

        // converte o array de frames em matriz 3D
        byte[][][] cuboPixels = new byte[frames.size()][][];
        for (int i = 0; i < frames.size(); i++) {
            cuboPixels[i] = frames.get(i);
        }

        return cuboPixels;
    }

    public static void gravarVideo(byte[][][] pixels, String caminho, double fps) {
        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        int fourcc = VideoWriter.fourcc('a', 'v', 'c', '1'); // identificação codec .mp4
        VideoWriter escritor = new VideoWriter(caminho, fourcc, fps, new Size(largura, altura), true);

        if (!escritor.isOpened()) {
            System.err.println("Erro ao gravar vídeo no caminho sugerido");
        }

        Mat matrizRgb = new Mat(altura, largura, CvType.CV_8UC3); // voltamos a operar no RGB (limitação da lib)

        byte[] linha = new byte[largura * 3]; // BGR intercalado

        for (int f = 0; f < qFrames; f++) {
            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {
                    byte g = pixels[f][y][x];
                    int i = x * 3;
                    linha[i] = linha[i + 1] = linha[i + 2] = g; // cinza → B,G,R
                }
                matrizRgb.put(y, 0, linha);
            }
            escritor.write(matrizRgb);
        }
        escritor.release(); // limpando o buffer
    }

    private static byte[][][] deepCopy(byte[][][] original) {
        byte[][][] copy = new byte[original.length][][];
        for (int f = 0; f < original.length; f++) {
            copy[f] = new byte[original[f].length][];
            for (int y = 0; y < original[f].length; y++) {
                copy[f][y] = Arrays.copyOf(original[f][y], original[f][y].length);
            }
        }
        return copy;
    }

    public static void main(String[] args) {

        String caminhoVideo = "video-3s.mp4";
        String caminhoGravar = "video-3s-corrigido.mp4";
        double fps = 24.0; // avaliar metadados conforme o video

        System.out.println("Carregando o vídeo... " + caminhoVideo);
        long tempoI = System.currentTimeMillis();
        byte[][][] pixels = carregarVideo(caminhoVideo);
            byte[][][] originalPixels = deepCopy(pixels);

        System.out.printf("Frames: %d Resolução: %d x %d \n", pixels.length, pixels[0][0].length, pixels[0].length);

        int numThreads = Runtime.getRuntime().availableProcessors();
        System.out.println(numThreads);
        int totalFrames = pixels.length;
        List<ImageProcessor> threads = new ArrayList<>();

        int framesPorThread = totalFrames / numThreads;
        int restante = totalFrames % numThreads;
        int start = 0;

        for (int t = 0; t < numThreads; t++) {
            int end = start + framesPorThread + (t < restante ? 1 : 0);
            restante--;
            threads.add(new ImageProcessor(start, end, originalPixels, pixels));
            start = end;
        }

        // Aguarda todas as threads terminarem
        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        System.out.println("Tempo total: " + (System.currentTimeMillis() - tempoI));
        gravarVideo(pixels, caminhoGravar, fps);
        System.out.println("Concluído!");
    }

    /*static class ImageProcessor extends Thread {
        private final int start, end;
        private final byte[][][] original, processed;

        public ImageProcessor(int start, int end, byte[][][] original, byte[][][] processed) {
            this.start = start;
            this.end = end;
            this.original = original;
            this.processed = processed;
        }

        @Override
        public void run() {
            for (int f = start; f < end; f++) {
                processFrame(f);
            }
        }

        private void processFrame(int f) {
            corrigirBorrões(f);
            corrigirRuido(f);
        }

        private void corrigirBorrões(int f) {
            Mat frame = arrayParaMat(original[f]);
            Mat circles = new Mat();
            Imgproc.HoughCircles(frame, circles, Imgproc.HOUGH_GRADIENT,
                    1, 5, 50, 25, 5, 100);

            int detectados = 0;
            for (int i = 0; i < circles.cols(); i++) {
                double[] data = circles.get(0, i);
                if (data == null) continue;
                detectados++;
                Point center = new Point(data[0], data[1]);
                int radius = (int) data[2];
                substituirRegiao(f, center, radius);
            }
            if (detectados > 0) {
                System.out.println("Frame " + f + ": " + detectados + " círculos detectados corretamente.");
            } else {
                System.out.println("Frame " + f + ": Nenhum círculo detectado.");
            }
        }

        private void substituirRegiao(int f, Point centro, int raio) {
            int x = (int) centro.x;
            int y = (int) centro.y;

            List<byte[][]> candidatos = new ArrayList<>();
            if (f > 0) candidatos.add(original[f - 1]);
            if (f < original.length - 1) candidatos.add(original[f + 1]);
            if (candidatos.isEmpty()) return;

            byte[][] melhor = candidatos.get(0);
            int altura = melhor.length;
            int largura = melhor[0].length;

            int xInicio = Math.max(0, x - raio);
            int xFim = Math.min(largura - 1, x + raio);
            int yInicio = Math.max(0, y - raio);
            int yFim = Math.min(altura - 1, y + raio);

            for (int yi = yInicio; yi <= yFim; yi++) {
                for (int xi = xInicio; xi <= xFim; xi++) {
                    if (distancia(xi, yi, x, y) <= raio) {
                        processed[f][yi][xi] = melhor[yi][xi];;
                    }
                }
            }
        }

        private double distancia(int x1, int y1, int x2, int y2) {
            return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
        }

        private void corrigirRuido(int f) {
            Mat frame = arrayParaMat(processed[f]);
            Mat filtrado = new Mat();
            Imgproc.medianBlur(frame, filtrado, 3);
            matParaArray(filtrado, processed[f]);
            System.out.println("Ruído corrigido no frame " + f);
        }

        private Mat arrayParaMat(byte[][] frame) {
            Mat mat = new Mat(frame.length, frame[0].length, CvType.CV_8UC1);
            for (int y = 0; y < frame.length; y++) {
                mat.put(y, 0, frame[y]);
            }
            return mat;
        }

        private void matParaArray(Mat mat, byte[][] array) {
            for (int y = 0; y < mat.rows(); y++) {
                mat.get(y, 0, array[y]);
            }
        }
    }*/
}
