import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.global.opencv_imgproc; // Para cvtColor

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VideoProcessor2 {

    public static VideoData carregarVideo(String caminho) throws FrameGrabber.Exception {
        File videoFile = new File(caminho);
        if (!videoFile.exists()) {
            System.err.println("Arquivo de vídeo não encontrado: " + caminho);
            return null;
        }

        List<byte[][]> framesList = new ArrayList<>();
        double fps = 0;
        int largura = 0;
        int altura = 0;

        // Conversores (podem ser instanciados uma vez)
        OpenCVFrameConverter.ToMat matConverter = new OpenCVFrameConverter.ToMat();
        Java2DFrameConverter java2DConverter = new Java2DFrameConverter();

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(caminho)) {
            grabber.start();

            largura = grabber.getImageWidth();
            altura = grabber.getImageHeight();
            fps = grabber.getFrameRate();

            if (largura == 0 || altura == 0) {
                System.err.println("Não foi possível obter as dimensões do vídeo.");
                grabber.stop();
                return null;
            }

            Frame quadroGrabbed;
            while ((quadroGrabbed = grabber.grabImage()) != null) {
                if (quadroGrabbed.image == null || quadroGrabbed.imageWidth == 0 || quadroGrabbed.imageHeight == 0) {
                    continue;
                }

                Mat quadroMatColorido = matConverter.convert(quadroGrabbed); // Converte Frame para Mat
                Mat quadroCinza = new Mat();

                if (quadroMatColorido.channels() > 1) {
                    opencv_imgproc.cvtColor(quadroMatColorido, quadroCinza, opencv_imgproc.COLOR_BGR2GRAY);
                } else {
                    quadroCinza = quadroMatColorido.clone(); // Já é cinza, apenas clona
                }

                // --- CORREÇÃO AQUI ---
                // Converter Mat (quadroCinza) para BufferedImage para extrair os pixels
                Frame frameParaBufferedImage = matConverter.convert(quadroCinza); // Converte Mat de volta para Frame
                BufferedImage imagemCinza = java2DConverter.convert(frameParaBufferedImage); // Converte Frame para BufferedImage

                if (imagemCinza != null) {
                    // Verifica se a imagem convertida é do tipo esperado (TYPE_BYTE_GRAY)
                    // Se não for, pode ser necessário forçar a conversão para TYPE_BYTE_GRAY
                    if (imagemCinza.getType() != BufferedImage.TYPE_BYTE_GRAY) {
                        BufferedImage tempImg = new BufferedImage(imagemCinza.getWidth(), imagemCinza.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
                        tempImg.getGraphics().drawImage(imagemCinza, 0, 0, null);
                        imagemCinza = tempImg;
                    }

                    byte[][] pixelsFrame = new byte[altura][largura];
                    DataBufferByte dataBuffer = (DataBufferByte) imagemCinza.getRaster().getDataBuffer();
                    byte[] pixelData = dataBuffer.getData();

                    // A ordem dos pixels em pixelData é linha por linha
                    // Certifique-se que a largura usada aqui é a da imagemCinza, que deve ser a mesma de 'largura'
                    int frameLargura = imagemCinza.getWidth();
                    int frameAltura = imagemCinza.getHeight();

                    for (int y = 0; y < frameAltura; y++) {
                        for (int x = 0; x < frameLargura; x++) {
                             if (y < altura && x < largura) { // Segurança adicional de contorno
                                pixelsFrame[y][x] = pixelData[y * frameLargura + x];
                            }
                        }
                    }
                    framesList.add(pixelsFrame);
                }
                // --- FIM DA CORREÇÃO ---

                quadroMatColorido.release();
                if (quadroCinza != quadroMatColorido) {
                    quadroCinza.release();
                }
            }
            grabber.stop();
        }

        if (framesList.isEmpty()) {
            System.err.println("Nenhum quadro foi extraído.");
            return null;
        }

        byte[][][] cuboPixels = new byte[framesList.size()][altura][largura];
        for (int i = 0; i < framesList.size(); i++) {
            cuboPixels[i] = framesList.get(i);
        }

        return new VideoData(cuboPixels, fps, largura, altura);
    }

    // O método gravarVideo e os métodos de processamento (removerSalPimenta, removerBorroesTempo)
    // permanecem os mesmos da resposta anterior.
    public static void gravarVideo(byte[][][] pixels,
                                   String caminhoSaida,
                                   double fps,
                                   int largura,
                                   int altura) throws FrameRecorder.Exception {

        if (pixels == null || pixels.length == 0) {
            System.err.println("Dados de pixels vazios para gravação.");
            return;
        }

        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(caminhoSaida, largura, altura, 0)) { // 0 canais de áudio
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setFrameRate(fps);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P); // Formato amplamente compatível com H.264

            recorder.start();

            Java2DFrameConverter frameConverter = new Java2DFrameConverter();

            for (int f = 0; f < pixels.length; f++) {
                BufferedImage imagemFrame = new BufferedImage(largura, altura, BufferedImage.TYPE_BYTE_GRAY);
                byte[] buffer = ((DataBufferByte) imagemFrame.getRaster().getDataBuffer()).getData();

                // Copia os dados do nosso array de bytes para o buffer da BufferedImage
                for (int y = 0; y < altura; y++) {
                    System.arraycopy(pixels[f][y], 0, buffer, y * largura, largura);
                }
                recorder.record(frameConverter.getFrame(imagemFrame));
            }
            recorder.stop();
            recorder.release(); // Garante que todos os recursos sejam liberados
        }
        System.out.println("Vídeo salvo em: " + caminhoSaida);
    }


    public static void removerSalPimenta(byte[][][] pixels, int raio) {
        if (pixels == null || pixels.length == 0) return;
        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        for (int f = 0; f < qFrames; f++) {
            byte[][] frameOriginal = pixels[f];
            byte[][] novoFrame = new byte[altura][largura];

            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {
                    ArrayList<Integer> vizinhos = new ArrayList<>();
                    for (int dy = -raio; dy <= raio; dy++) {
                        for (int dx = -raio; dx <= raio; dx++) {
                            int ny = y + dy;
                            int nx = x + dx;
                            if (ny >= 0 && ny < altura && nx >= 0 && nx < largura) {
                                vizinhos.add(frameOriginal[ny][nx] & 0xFF);
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
        if (pixels == null || pixels.length < 3) return;
        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        byte[][][] pixelsOriginais = new byte[qFrames][altura][largura];
        for(int f=0; f<qFrames; f++) {
            for(int y=0; y<altura; y++) {
                pixelsOriginais[f][y] = pixels[f][y].clone();
            }
        }

        for (int f = 1; f < qFrames - 1; f++) {
            byte[][] anterior = pixelsOriginais[f - 1];
            byte[][] atual = pixelsOriginais[f];
            byte[][] proximo = pixelsOriginais[f + 1];
            byte[][] novoFrame = new byte[altura][largura];

            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {
                    int v1 = anterior[y][x] & 0xFF;
                    int v2 = atual[y][x] & 0xFF;
                    int v3 = proximo[y][x] & 0xFF;

                    int media = (v1 + v2 + v3) / 3;
                    novoFrame[y][x] = (byte) media;
                }
            }
            pixels[f] = novoFrame;
        }
    }

    public static void main(String[] args) {
        String caminhoVideoEntrada = "video-3s.mp4";
        String caminhoVideoSaida = "video-3s-corrigido.mp4";

        System.out.println("Iniciando processamento do vídeo: " + caminhoVideoEntrada);

        try {
            VideoData dadosVideo = carregarVideo(caminhoVideoEntrada);

            if (dadosVideo == null || dadosVideo.pixels() == null) {
                System.err.println("Falha ao carregar o vídeo.");
                return;
            }

            byte[][][] pixels = dadosVideo.pixels();
            System.out.printf("Vídeo carregado: %d frames, Resolução: %d x %d, FPS: %.2f\n",
                    pixels.length, dadosVideo.largura(), dadosVideo.altura(), dadosVideo.fps());

            System.out.println("Aplicando filtro de mediana (remover sal e pimenta)...");
            for (int i = 0; i < 1; i++) { // Ajuste o número de iterações conforme necessário
                System.out.println("Iteração de remoção de ruído 'Sal e Pimenta': " + (i + 1));
                removerSalPimenta(pixels, 1); // Raio 1 (janela 3x3)
            }

            System.out.println("Aplicando filtro de suavização temporal (remover borrões)...");
            removerBorroesTempo(pixels);

            System.out.println("Salvando vídeo processado em: " + caminhoVideoSaida);
            gravarVideo(pixels, caminhoVideoSaida, dadosVideo.fps(), dadosVideo.largura(), dadosVideo.altura());

            System.out.println("Processamento concluído!");

        } catch (FrameGrabber.Exception e) {
            System.err.println("Erro ao carregar o vídeo (FrameGrabber): " + e.getMessage());
            e.printStackTrace();
        } catch (FrameRecorder.Exception e) {
            System.err.println("Erro ao gravar o vídeo (FrameRecorder): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Ocorreu um erro geral: " + e.getMessage());
            e.printStackTrace();
        }
    }
}