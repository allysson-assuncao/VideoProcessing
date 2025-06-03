import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SequentialVideoProcessing {

    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public static byte[][][] carregarVideo(String caminho) {

        VideoCapture captura = new VideoCapture(caminho);

        int largura = (int) captura.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int altura = (int) captura.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        List<byte[][]> frames = new ArrayList<>();

        Mat matrizRGB = new Mat();

        //criando uma matriz temporária em escala de cinza
        Mat escalaCinza = new Mat(altura, largura, CvType.CV_8UC1); //1 única escala
        byte linha[] = new byte[largura];

        while (captura.read(matrizRGB)) {//leitura até o último frames

            //convertemos o frame atual para escala de cinza
            Imgproc.cvtColor(matrizRGB, escalaCinza, Imgproc.COLOR_BGR2GRAY);

            //criamos uma matriz para armazenar o valor de cada pixel (int estouro de memória)
            byte pixels[][] = new byte[altura][largura];
            for (int y = 0; y < altura; y++) {
                escalaCinza.get(y, 0, linha);
                for (int x = 0; x < largura; x++) {
                    pixels[y][x] = (byte)(linha[x] & 0xFF); //shift de correção - unsig
                }
            }
            frames.add(pixels);
        }
        captura.release();

        /* converte o array de frames em matriz 3D */
        byte cuboPixels[][][] = new byte[frames.size()][][];
        for (int i = 0; i < frames.size(); i++) {
            cuboPixels[i] = frames.get(i);
        }

        return cuboPixels;
    }

    public static void gravarVideo(byte pixels[][][],
            String caminho,
            double fps) {

        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        int fourcc = VideoWriter.fourcc('a', 'v', 'c', '1');   // identificação codec .mp4
        VideoWriter escritor = new VideoWriter(
                caminho, fourcc, fps, new Size(largura, altura), true);

        if (!escritor.isOpened()) {
            System.err.println("Erro ao gravar vídeo no caminho corrigido");
        }

        Mat matrizRgb = new Mat(altura, largura, CvType.CV_8UC3); //voltamos a operar no RGB (limitação da lib)

        byte linha[] = new byte[largura * 3];                // BGR intercalado

        for (int f = 0; f < qFrames; f++) {
            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {
                    byte g = (byte) pixels[f][y][x];
                    int i = x * 3;
                    linha[i] = linha[i + 1] = linha[i + 2] = g;     // cinza → B,G,R
                }
                matrizRgb.put(y, 0, linha);
            }
            escritor.write(matrizRgb);
        }
        escritor.release(); //limpando o buffer
    }

    public static void removerSalPimenta(byte[][][] pixels, int raio) {
        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        for (int f = 0; f < qFrames; f++) {
            byte[][] novoFrame = new byte[altura][largura];

            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {

                    ArrayList<Integer> vizinhos = new ArrayList<>();

                    for (int dy = -raio; dy <= raio; dy++) {
                        for (int dx = -raio; dx <= raio; dx++) {
                            int ny = y + dy;
                            int nx = x + dx;
                            if (ny >= 0 && ny < altura && nx >= 0 && nx < largura) {
                                vizinhos.add(pixels[f][ny][nx] & 0xFF); // conversão para inteiro positivo
                            }
                        }
                    }

                    // ordena a lista e pega o valor central
                    Collections.sort(vizinhos);
                    int mediana = vizinhos.get(vizinhos.size() / 2);
                    novoFrame[y][x] = (byte) mediana;
                }
            }

            pixels[f] = novoFrame;
        }
    }

    // Suaviza borrões no tempo, usando média entre frames vizinhos
    public static void removerBorroesTempo(byte[][][] pixels) {
        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        for (int f = 1; f < qFrames - 2; f++) {
            byte[][] anterior = pixels[f - 1];
            byte[][] atual = pixels[f];
            byte[][] proximo = pixels[f + 2];
            byte[][] novoFrame = new byte[altura][largura];

            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {
                    int v1 = anterior[y][x] & 0xFF;
                    int v2 = atual[y][x] & 0xFF;
                    int v3 = proximo[y][x] & 0xFF;

                    int media = (v1 + v3) / 2;
                    if(Math.abs(v1 - v3) < 50 && Math.abs(media - v2) > 40){
                        novoFrame[y][x] = (byte) media;
                    } else {
                        novoFrame[y][x] = atual[y][x];
                    }
                }
            }

            pixels[f] = novoFrame;
        }
    }

    public static void main(String[] args) {

        String caminhoVideo = "video-3s.mp4";
        String caminhoGravar = "video-3s2.mp4";
        double fps = 24.0;

        System.out.println("Carregando o vídeo... " + caminhoVideo);
        byte pixels[][][] = carregarVideo(caminhoVideo);


        System.out.printf("Frames: %d   Resolução: %d x %d \n",
                pixels.length, pixels[0][0].length, pixels[0].length);

        //Pro 2
        for(int i = 0; i < 1; i++) {
            System.out.println("processamento remove borrão");
            removerBorroesTempo(pixels);
        }

        //Pro 1
        for(int i = 0; i < 10; i++){
            System.out.println("processamento remove sal e pimenta " + (i + 1));
            removerSalPimenta(pixels, 1);
        }

        System.out.println("Salvando...  " + caminhoGravar);
        gravarVideo(pixels, caminhoGravar, fps);
        System.out.println("Término do processamento");
    }
}
