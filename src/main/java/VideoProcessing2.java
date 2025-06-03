import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGRA2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;


public class VideoProcessing2 {

    // O carregamento da biblioteca nativa é gerenciado automaticamente pelo Bytedeco.
    // O bloco static com nu.pattern.OpenCV.loadLocally() não é mais necessário.

    public static byte[][][] carregarVideo(String caminho) {
        List<byte[][]> framesList = new ArrayList<>();
        int largura = 0;
        int altura = 0;
        // double videoFps = 0; // FPS pode ser obtido aqui se necessário para outros usos

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(caminho)) {
            grabber.start(); // Inicia o grabber

            largura = grabber.getImageWidth();
            altura = grabber.getImageHeight();
            // videoFps = grabber.getFrameRate(); // Descomente se precisar do FPS do vídeo original

            if (largura == 0 || altura == 0) {
                System.err.println("Não foi possível obter as dimensões do vídeo: " + caminho);
                return null;
            }

            OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
            Frame capturedFrame;

            // grabImage() é preferível para processamento de imagem, pois garante um frame decodificado
            while ((capturedFrame = grabber.grabImage()) != null) {
                Mat matOriginal = converterToMat.convert(capturedFrame);
                if (matOriginal == null || matOriginal.empty()) {
                    System.err.println("Frame convertido para Mat é nulo ou vazio.");
                    continue;
                }

                Mat matCinza;
                Mat matCinzaAlocada = null; // Para rastrear se alocamos uma nova Mat

                if (matOriginal.channels() == 3) { // Comum: BGR
                    matCinzaAlocada = new Mat(altura, largura, CV_8UC1);
                    cvtColor(matOriginal, matCinzaAlocada, COLOR_BGR2GRAY);
                    matCinza = matCinzaAlocada;
                } else if (matOriginal.channels() == 4) { // Comum: BGRA
                    matCinzaAlocada = new Mat(altura, largura, CV_8UC1);
                    cvtColor(matOriginal, matCinzaAlocada, COLOR_BGRA2GRAY);
                    matCinza = matCinzaAlocada;
                } else if (matOriginal.channels() == 1) { // Já em escala de cinza
                    matCinza = matOriginal;
                } else {
                    System.err.println("Número de canais não suportado no frame: " + matOriginal.channels());
                    matOriginal.release(); // Liberar a Mat original
                    continue;
                }

                byte[][] pixels = new byte[altura][largura];
                UByteIndexer grayIndexer = matCinza.createIndexer(); // Indexador para acesso eficiente

                for (int y = 0; y < altura; y++) {
                    for (int x = 0; x < largura; x++) {
                        pixels[y][x] = (byte) grayIndexer.get(y, x); // get() retorna int (0-255)
                    }
                }
                grayIndexer.release(); // Liberar o indexer
                framesList.add(pixels);

                // Liberar Mats
                matOriginal.release(); // Sempre liberar a Mat original convertida do frame
                if (matCinzaAlocada != null) { // Se criamos uma Mat nova para escala de cinza, liberar também
                    matCinzaAlocada.release();
                }
            }
            converterToMat.close(); // Liberar o conversor
        } catch (Exception e) {
            System.err.println("Erro ao carregar o vídeo: " + e.getMessage());
            e.printStackTrace();
            return null; // Retorna null em caso de erro significativo
        }

        if (framesList.isEmpty()) {
            System.err.println("Nenhum frame foi carregado do vídeo.");
            return null;
        }

        /* converte a lista de frames em matriz 3D */
        byte[][][] cuboPixels = new byte[framesList.size()][altura][largura];
        for (int i = 0; i < framesList.size(); i++) {
            cuboPixels[i] = framesList.get(i);
        }

        return cuboPixels;
    }

    public static void gravarVideo(byte[][][] pixels,
                                   String caminho,
                                   double fps) {
        if (pixels == null || pixels.length == 0) {
            System.err.println("Array de pixels está vazio ou nulo. Não é possível gravar o vídeo.");
            return;
        }

        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        // O construtor aceita largura, altura e canais de áudio (0 para sem áudio)
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(caminho, largura, altura, 0)) {
            recorder.setFormat("mp4"); // Formato do container
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264); // Codec H.264
            recorder.setFrameRate(fps);

            // ****** Configurações de Qualidade ******

            // Abordagem 1: Usar CRF (Constant Rate Factor) - RECOMENDADO
            // CRF controla a qualidade. Valores menores = melhor qualidade, maior arquivo.
            // Faixa típica para H.264 (libx264) é 18-28. O padrão é frequentemente 23.
            // Tente um valor como 20 ou 22 para boa qualidade.
            recorder.setVideoOption("crf", "22");

            // (Opcional) Você também pode definir um "preset" para o encoder H.264 (libx264).
            // Presets afetam a velocidade de codificação e a eficiência da compressão.
            // "medium" é um bom padrão. Outros: "ultrafast", "fast", "slow", "veryslow".
            // Presets mais lentos geralmente dão melhor compressão para a mesma qualidade.
            // recorder.setVideoOption("preset", "medium");

            // Abordagem 2: Usar Bitrate (alternativa se CRF não funcionar como esperado ou não for suportado)
            // Se for usar bitrate, comente as linhas de setVideoOption("crf", ...) e ("preset", ...).
            // Exemplo de cálculo de bitrate (pode precisar de ajuste):
            // int videoBitrate = largura * altura * (int)fps / 15; // Heurística
            // if (videoBitrate < 1000000) videoBitrate = 1000000; // Mínimo de 1 Mbps, por exemplo
            // recorder.setVideoBitrate(videoBitrate);


            // Pixel format para o encoder.
            // AV_PIX_FMT_YUV420P é o formato de pixel mais comum e compatível para H.264 em MP4.
            // O FFmpegFrameRecorder tentará converter o frame de entrada (que será BGR24) para YUV420P.
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            // Se você souber EXATAMENTE qual `pixelFormat` fez a gravação funcionar (sem erro),
            // mas com qualidade ruim, você pode tentar usá-lo aqui e ainda aplicar as configurações de CRF/bitrate.
            // No entanto, YUV420P é o mais robusto para H.264.

            recorder.start(); // Inicia o recorder

            // O conversor é usado para transformar Mat (OpenCV) em Frame (JavaCV)
            OpenCVFrameConverter.ToMat frameConverter = new OpenCVFrameConverter.ToMat();

            for (int f = 0; f < qFrames; f++) {
                // 1. Criar Mat em escala de cinza a partir dos dados byte[][]
                Mat matCinza = new Mat(altura, largura, CV_8UC1);
                // Usar try-with-resources para o UByteIndexer, pois ele é AutoCloseable
                try (UByteIndexer grayIndexer = matCinza.createIndexer()) {
                    for (int y = 0; y < altura; y++) {
                        for (int x = 0; x < largura; x++) {
                            // pixels[f][y][x] é byte. Para UByteIndexer.put, precisamos de int (0-255)
                            grayIndexer.put(y, x, pixels[f][y][x] & 0xFF);
                        }
                    }
                } // grayIndexer.release() é chamado automaticamente aqui

                // 2. Converter Mat cinza para Mat BGR (replicando o canal cinza para B, G, R)
                // Isso é feito porque a maioria dos encoders H.264 em MP4 espera entrada colorida
                // (mesmo que seja para representar cinza, R=G=B).
                Mat matBGR = new Mat(altura, largura, CV_8UC3);
                cvtColor(matCinza, matBGR, COLOR_GRAY2BGR);

                // 3. Converter Mat BGR para Frame
                // O Frame resultante terá dados no formato BGR24.
                Frame outputFrame = frameConverter.convert(matBGR);

                // 4. Gravar o frame.
                // Se o pixelFormat do recorder for YUV420P (como configurado), o FFmpeg
                // fará a conversão interna de BGR24 para YUV420P.
                recorder.record(outputFrame);

                // 5. Liberar Mats para evitar vazamento de memória nativa
                matCinza.release();
                matBGR.release();
            }
            // frameConverter não precisa de close() explícito pois não gerencia recursos que exigem tal.
        } catch (Exception e) {
            System.err.println("Erro ao gravar o vídeo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void removerSalPimenta(byte[][][] pixels, int raio) {
        if (pixels == null || pixels.length == 0) return;
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
                    Collections.sort(vizinhos);
                    int mediana = vizinhos.get(vizinhos.size() / 2);
                    novoFrame[y][x] = (byte) mediana;
                }
            }
            pixels[f] = novoFrame;
        }
    }

    public static void removerBorroesTempo(byte[][][] pixels) {
        if (pixels == null || pixels.length < 3)
            return; // Precisa de pelo menos 3 frames para a lógica original (f-1, f, f+2 implicaria f+1 mas usa f+2)
        // Se for f-1, f, f+1, então pixels.length < 3.
        // O original usa f-1, f, f+2.  Isso significa que o loop deve ir até qFrames - 3.
        // E o f começa em 1.  qFrames-1 é o último índice. (qFrames-1)+2 é OutOfBounds.
        // Se f < qFrames - 2, então f_max = qFrames - 3.
        // proximo = pixels[f+2] -> pixels[qFrames-3+2] = pixels[qFrames-1], que é o último frame. OK.

        int qFrames = pixels.length;
        if (qFrames < 3) { // Ajuste: A lógica original f-1, f, f+2 precisa de pelo menos f+2 < qFrames.
            // Se f começa em 1, e f vai até qFrames-3, então (qFrames-3)+2 = qFrames-1.
            // Isso significa que precisamos de pelo menos 3 frames (índices 0, 1, 2).
            // Se qFrames = 3, f=1. anterior=pixels[0], atual=pixels[1], proximo=pixels[3] (ERRO).
            // Deve ser f < qFrames - 2.
            // Se qFrames = 3, o loop não roda (1 < 3-2 = 1 é falso).
            // Se qFrames = 4, f=1. anterior=pixels[0], atual=pixels[1], proximo=pixels[3]. OK.
            // Então, qFrames deve ser >= 4 para o loop rodar uma vez.
            // Se qFrames é 3 (índices 0, 1, 2), f = 1. f+2 = 3, o que é um erro.
            // A condição `f < qFrames - 2` está correta para evitar OutOfBounds.
            // Ex: qFrames = 3. Loop: 1 < 3-2 (1<1) não executa. Correto.
            // Ex: qFrames = 4. Loop: f=1. 1 < 4-2 (1<2). Executa para f=1.
            //    pixels[0], pixels[1], pixels[3].
            // Próxima iteração f=2. 2 < 4-2 (2<2) não executa.
            // Então o loop vai de f=1 até qFrames-3.
            System.err.println("Remoção de borrões no tempo requer pelo menos 4 frames para a lógica atual (f-1, f, f+2).");
            return;
        }

        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        for (int f = 1; f < qFrames - 2; f++) { // Loop até qFrames-3 para que f+2 seja no máximo qFrames-1
            byte[][] anterior = pixels[f - 1];
            byte[][] atual = pixels[f];
            byte[][] proximo = pixels[f + 2]; // Correto, conforme original
            byte[][] novoFrame = new byte[altura][largura];

            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {
                    int v1 = anterior[y][x] & 0xFF;
                    int v2 = atual[y][x] & 0xFF;
                    int v3 = proximo[y][x] & 0xFF;

                    int media = (v1 + v3) / 2;
                    if (Math.abs(v1 - v3) < 50 && Math.abs(media - v2) > 40) {
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
        String caminhoVideo = "video-3s.mp4";    // Coloque aqui o caminho para seu vídeo de entrada
        String caminhoGravar = "video-3s2_bytedeco.mp4"; // Nome do arquivo de saída
        double fps = 24.0; // FPS para o vídeo de saída (pode ser obtido do vídeo original se desejado)

        System.out.println("Carregando o vídeo... " + caminhoVideo);
        byte[][][] pixels = carregarVideo(caminhoVideo);

        if (pixels == null) {
            System.out.println("Falha ao carregar o vídeo. Encerrando.");
            return;
        }

        System.out.printf("Frames: %d   Resolução: %d x %d \n",
                pixels.length, pixels[0][0].length, pixels[0].length);

        /*//Pro 2
        for (int i = 0; i < 1; i++) { // No original, este loop executa 1 vez
            System.out.println("Processamento remove borrão (temporal)");
            removerBorroesTempo(pixels);
        }

        //Pro 1
        for (int i = 0; i < 10; i++) {
            System.out.println("Processamento remove sal e pimenta " + (i + 1));
            removerSalPimenta(pixels, 1); // Raio 1 para a mediana
        }*/

        System.out.println("Salvando...  " + caminhoGravar);
        gravarVideo(pixels, caminhoGravar, fps);
        System.out.println("Término do processamento");
    }
}