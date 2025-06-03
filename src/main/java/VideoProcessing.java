import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

import java.util.ArrayList;
import java.util.List;

public class VideoProcessing {

    static {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (Exception e) {
            System.err.println("Erro ao carregar OpenCV: " + e.getMessage());
            // Considerar lançar uma RuntimeException ou tratar de forma mais robusta
        }
    }

    public static byte[][][] carregarVideo(String caminho) {
        VideoCapture captura = new VideoCapture(caminho);
        if (!captura.isOpened()) {
            System.err.println("Erro ao abrir o vídeo: " + caminho);
            return new byte[0][][]; // Retorna vazio para indicar falha
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
                for (int x = 0; x < largura; x++) {
                    pixelsFrame[y][x] = linha[x]; // O & 0xFF é mais para conversão int, aqui byte já é signed.
                                                 // Se a leitura direta de OpenCV já garante o range correto, ok.
                                                 // A lógica original com (byte)(linha[x] & 0xFF) é mais segura
                                                 // para garantir que valores > 127 sejam tratados como unsigned
                                                 // ao converter para int, mas ao armazenar como byte, o valor será o mesmo.
                                                 // Vamos manter a consistência com o original, embora o efeito em byte seja o mesmo.
                    pixelsFrame[y][x] = (byte)(linha[x] & 0xFF);
                }
            }
            frames.add(pixelsFrame);
        }
        captura.release();

        if (frames.isEmpty()) {
            System.err.println("Nenhum frame carregado do vídeo: " + caminho);
            return new byte[0][][];
        }

        byte[][][] cuboPixels = new byte[frames.size()][][];
        for (int i = 0; i < frames.size(); i++) {
            cuboPixels[i] = frames.get(i);
        }
        return cuboPixels;
    }

    public static void gravarVideo(byte[][][] pixels, String caminho, double fps) {
        if (pixels == null || pixels.length == 0) {
            System.err.println("Nenhum pixel para gravar.");
            return;
        }
        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        // Tentar outros codecs se 'avc1' falhar ou não estiver disponível
        int fourcc = VideoWriter.fourcc('a', 'v', 'c', '1'); // .mp4 H.264
        // int fourcc = VideoWriter.fourcc('X', 'V', 'I', 'D'); // .avi Xvid
        // int fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G'); // .avi Motion JPEG

        VideoWriter escritor = new VideoWriter(caminho, fourcc, fps, new Size(largura, altura), false); // false para grayscale

        if (!escritor.isOpened()) {
            System.err.println("Erro ao iniciar VideoWriter para: " + caminho + ". Verifique o codec e permissões.");
            // Tentar um codec alternativo como fallback pode ser uma opção aqui
            fourcc = VideoWriter.fourcc('M', 'J', 'P', 'G'); // Fallback para MJPEG em .avi
            String fallbackPath = caminho.substring(0, caminho.lastIndexOf('.')) + "_fallback.avi";
            System.out.println("Tentando fallback com MJPEG para: " + fallbackPath);
            escritor = new VideoWriter(fallbackPath, fourcc, fps, new Size(largura, altura), false); // false para grayscale
             if (!escritor.isOpened()) {
                System.err.println("Erro ao iniciar VideoWriter com fallback para: " + fallbackPath);
                return;
            }
        }


        Mat frameCinza = new Mat(altura, largura, CvType.CV_8UC1);

        for (int f = 0; f < qFrames; f++) {
            // Colocar os bytes do frame processado diretamente na Mat
            for(int y = 0; y < altura; y++){
                frameCinza.put(y,0, pixels[f][y]);
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

    public static void main(String[] args) {
        String caminhoVideo = "video-3s.mp4"; // Coloque seu vídeo de teste aqui
        String caminhoGravar = "video-3s-corrigido-paralelo.mp4";
        double fps = 24.0; // Ajuste conforme o FPS do seu vídeo original

        System.out.println("Carregando o vídeo... " + caminhoVideo);
        long tempoInicioTotal = System.currentTimeMillis();

        byte[][][] pixels = carregarVideo(caminhoVideo);
        if (pixels.length == 0) {
            System.out.println("Falha ao carregar o vídeo. Encerrando.");
            return;
        }
        byte[][][] originalPixels = deepCopy(pixels); // Cópia para leitura segura, especialmente para o borrão

        System.out.printf("Frames: %d   Resolução: %d x %d \n",
                pixels.length, pixels[0][0].length, pixels[0].length);

        int numCores = Runtime.getRuntime().availableProcessors();
        // Para testes, você pode querer limitar o número de threads
        // int numThreads = Math.min(numCores, 4); // Exemplo: usar no máximo 4 threads
        int numThreads = numCores;
        System.out.println("Utilizando " + numThreads + " threads.");

        List<ImageProcessor> threads = new ArrayList<>();
        int totalFrames = pixels.length;
        int framesPorThread = totalFrames / numThreads;
        int framesRestantes = totalFrames % numThreads;
        int frameAtualInicio = 0;

        // Parâmetros dos filtros
        int numPassesSalPimenta = 10;
        int raioSalPimenta = 1;

        long tempoInicioProcessamento = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            int framesParaEstaThread = framesPorThread + (i < framesRestantes ? 1 : 0);
            if (framesParaEstaThread == 0) continue; // Evitar criar threads sem frames para processar

            int frameAtualFim = frameAtualInicio + framesParaEstaThread;
            // System.out.printf("Thread %d: Frames %d a %d\n", i, frameAtualInicio, frameAtualFim - 1);

            // 'originalPixels' é para leitura de referência (especialmente borrão temporal)
            // 'pixels' é onde o resultado final será escrito e lido por etapas subsequentes (sal e pimenta)
            threads.add(new ImageProcessor(frameAtualInicio, frameAtualFim, originalPixels, pixels, numPassesSalPimenta, raioSalPimenta));
            frameAtualInicio = frameAtualFim;
        }

        for (ImageProcessor t : threads) {
            t.start();
        }

        for (ImageProcessor t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt(); // Restaura o status de interrupção
            }
        }
        long tempoFimProcessamento = System.currentTimeMillis();
        System.out.println("Tempo de processamento dos filtros: " + (tempoFimProcessamento - tempoInicioProcessamento) + " ms");

        System.out.println("Salvando o vídeo corrigido... " + caminhoGravar);
        gravarVideo(pixels, caminhoGravar, fps); // 'pixels' agora contém o resultado final

        long tempoFimTotal = System.currentTimeMillis();
        System.out.println("Tempo total de execução: " + (tempoFimTotal - tempoInicioTotal) + " ms");
        System.out.println("Processamento concluído!");
    }
}