import java.util.ArrayList;
import java.util.List;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

public class VideoProcessing {

    // Carrega a biblioteca nativa (via nu.pattern.OpenCV) assim que a classe é carregada na VM.
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public static byte[][][] carregarVideo(String caminho) {

        VideoCapture captura = new VideoCapture(caminho);
        if (!captura.isOpened()) {
            System.out.println("Vídeo está sendo processado por outra aplicação");
        }

        // tamanho do frame
        int largura = (int) captura.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int altura = (int) captura.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        // não conhecço a quantidade dos frames (melhorar com outra lib) :(
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

    public static void gravarVideo(byte[][][] pixels,
                                   String caminho,
                                   double fps) {

        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        int fourcc = VideoWriter.fourcc('a', 'v', 'c', '1'); // identificação codec .mp4
        VideoWriter escritor = new VideoWriter(
                caminho, fourcc, fps, new Size(largura, altura), true);

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

    public static void main(String[] args) {

        String caminhoVideo = "video-3s.mp4";
        String caminhoGravar = "video-3s-corrigido.mp4";
        double fps = 24.0; // avaliar metadados conforme o video

        System.out.println("Carregando o vídeo... " + caminhoVideo);
        byte[][][] pixels = carregarVideo(caminhoVideo);

        System.out.printf("Frames: %d   Resolução: %d x %d \n",
                pixels.length, pixels[0][0].length, pixels[0].length);

        /*System.out.println("processamento remove ruído 1");
        //removerSalPimenta(pixels); //voce deve implementar esta funcao

        System.out.println("processamento remove ruído 2");
        //removerBorroesTempo(pixels); //voce deve implementar esta funcao*/

        int numThreads = Runtime.getRuntime().availableProcessors();
        System.out.println(numThreads);
        int totalFrames = pixels.length;
        int framesPorThread = (int) Math.ceil((double) totalFrames / numThreads);

        List<ImageProcessor> threads = new ArrayList<>();

        for (int t = 0; t < numThreads; t++) {
            int start = t * framesPorThread;
            int end = Math.min(start + framesPorThread, totalFrames);
            if (start >= end) break; // Evita threads vazias
            ImageProcessor processor = new ImageProcessor(pixels, start, end);
            threads.add(processor);
            processor.start();
        }

        // Aguarda todas as threads terminarem
        for (ImageProcessor processor : threads) {
            try {
                processor.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Processamento paralelo concluído.");

        System.out.println("Salvando...  " + caminhoGravar);
        gravarVideo(pixels, caminhoGravar, fps);
        System.out.println("Término do processamento");
    }
}
