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
        nu.pattern.OpenCV.loadLocally();
    }

    private byte[][][] quadrosEmBytes;
    private final int numeroDeThreads;

    public VideoProcessing(String caminhoDoVideo) {
        this.quadrosEmBytes = carregarVideo(caminhoDoVideo);
        this.numeroDeThreads = Runtime.getRuntime().availableProcessors();
    }

    public int getTotalDeQuadros() {
        return quadrosEmBytes.length;
    }

    public int getLargura() {
        return quadrosEmBytes[0][0].length;
    }

    public int getAltura() {
        return quadrosEmBytes[0].length;
    }

    public void removerSalPimenta(int raio) {
        int altura = getAltura();
        int largura = getLargura();
        int linhasPorThread = altura / numeroDeThreads;

        byte[][][] quadrosProcessados = new byte[getTotalDeQuadros()][altura][largura];

        for (int i = 0; i < getTotalDeQuadros(); i++) {
            byte[][] quadroAtual = quadrosEmBytes[i];
            byte[][] quadroDestino = quadrosProcessados[i];
            byte[][] quadroComBorda = adicionarBorda(quadroAtual, raio);
            List<ImageProcessor> threads = new ArrayList<>();

            for (int t = 0; t < numeroDeThreads; t++) {
                int linhaInicial = t * linhasPorThread;
                int linhaFinal = (t == numeroDeThreads - 1) ? altura : linhaInicial + linhasPorThread;
                ImageProcessor thread = new ImageProcessor(quadroComBorda, quadroDestino, linhaInicial, linhaFinal, raio);
                threads.add(thread);
                thread.start();
            }

            aguardarThreads(threads);
        }
        this.quadrosEmBytes = quadrosProcessados;
    }

    public void removerBorroesTemporais() {
        int altura = getAltura();
        int largura = getLargura();
        int linhasPorThread = altura / numeroDeThreads;

        // Cria uma cópia para o resultado, garantindo que os quadros não processados (bordas) sejam mantidos.
        byte[][][] quadrosProcessados = new byte[getTotalDeQuadros()][][];
        for(int i = 0; i < getTotalDeQuadros(); i++){
            quadrosProcessados[i] = quadrosEmBytes[i].clone();
        }

        // O loop ignora o primeiro e os dois últimos quadros, pois eles não possuem vizinhos completos.
        for (int i = 1; i < getTotalDeQuadros() - 2; i++) {
            byte[][] quadroAnterior = quadrosEmBytes[i - 1];
            byte[][] quadroAtual = quadrosEmBytes[i];
            byte[][] quadroProximo = quadrosEmBytes[i + 2];
            byte[][] quadroDestino = quadrosProcessados[i];

            List<ImageProcessor> threads = new ArrayList<>();
            for (int t = 0; t < numeroDeThreads; t++) {
                int linhaInicial = t * linhasPorThread;
                int linhaFinal = (t == numeroDeThreads - 1) ? altura : linhaInicial + linhasPorThread;
                ImageProcessor thread = new ImageProcessor(quadroAnterior, quadroAtual, quadroProximo, quadroDestino, linhaInicial, linhaFinal);
                threads.add(thread);
                thread.start();
            }

            aguardarThreads(threads);
        }
        this.quadrosEmBytes = quadrosProcessados;
    }

    private byte[][] adicionarBorda(byte[][] quadroOriginal, int raio) {
        int altura = quadroOriginal.length;
        int largura = quadroOriginal[0].length;
        byte[][] quadroComBorda = new byte[altura + 2 * raio][largura + 2 * raio];

        for (int y = 0; y < altura; y++) {
            System.arraycopy(quadroOriginal[y], 0, quadroComBorda[y + raio], raio, largura);
        }
        return quadroComBorda;
    }

    private void aguardarThreads(List<ImageProcessor> threads) {
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void gravarVideo(String caminhoSaida, double fps) {
        int altura = getAltura();
        int largura = getLargura();

        int fourcc = VideoWriter.fourcc('a', 'v', 'c', '1');
        VideoWriter escritor = new VideoWriter(caminhoSaida, fourcc, fps, new Size(largura, altura), true);

        if (!escritor.isOpened()) {
            System.err.println("Erro ao abrir o escritor de vídeo para o caminho: " + caminhoSaida);
            return;
        }

        Mat quadroMat = new Mat(altura, largura, CvType.CV_8UC3);
        byte[] bufferDeLinha = new byte[largura * 3];

        for (byte[][] quadro : quadrosEmBytes) {
            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {
                    byte valorCinza = quadro[y][x];
                    int indice = x * 3;
                    bufferDeLinha[indice] = valorCinza;
                    bufferDeLinha[indice + 1] = valorCinza;
                    bufferDeLinha[indice + 2] = valorCinza;
                }
                quadroMat.put(y, 0, bufferDeLinha);
            }
            escritor.write(quadroMat);
        }
        escritor.release();
        quadroMat.release();
    }

    /**
     * CORRIGIDO: Carrega um vídeo, convertendo para escala de cinza de forma segura, linha por linha.
     */
    private byte[][][] carregarVideo(String caminhoEntrada) {
        VideoCapture captura = new VideoCapture(caminhoEntrada);
        if (!captura.isOpened()) {
            System.err.println("Erro ao abrir o vídeo: " + caminhoEntrada);
            return new byte[0][0][0];
        }

        int largura = (int) captura.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int altura = (int) captura.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        List<byte[][]> listaDeQuadros = new ArrayList<>();
        Mat quadroColorido = new Mat();
        Mat quadroCinza = new Mat();

        byte[] bufferDeLinha = new byte[largura];

        while (captura.read(quadroColorido)) {
            Imgproc.cvtColor(quadroColorido, quadroCinza, Imgproc.COLOR_BGR2GRAY);
            byte[][] pixelsDoQuadro = new byte[altura][largura];
            // **A CORREÇÃO ESTÁ AQUI:** Lendo a matriz linha por linha para garantir que os dados sejam copiados.
            for (int y = 0; y < altura; y++) {
                quadroCinza.get(y, 0, bufferDeLinha);
                System.arraycopy(bufferDeLinha, 0, pixelsDoQuadro[y], 0, largura);
            }
            listaDeQuadros.add(pixelsDoQuadro);
        }
        captura.release();
        quadroColorido.release();
        quadroCinza.release();

        return listaDeQuadros.toArray(new byte[0][][]);
    }

    public static void main(String[] args) {
        String caminhoVideo = "video-3s.mp4";
        String caminhoGravar = "video-3s-corrigido.mp4";
        double fps = 24.0;

        System.out.println("Carregando o vídeo: " + caminhoVideo);
        VideoProcessing processador = new VideoProcessing(caminhoVideo);

        if (processador.getTotalDeQuadros() == 0) {
            System.err.println("Nenhum quadro foi carregado. Abortando.");
            return;
        }

        System.out.printf("Processamento paralelo iniciado. Quadros: %d | Resolução: %d x %d | Threads: %d \n",
            processador.getTotalDeQuadros(), processador.getLargura(), processador.getAltura(), processador.numeroDeThreads);

        long tempoInicialTotal = System.currentTimeMillis();

        System.out.println("Processando: removendo borrão temporal...");
        processador.removerBorroesTemporais();

        System.out.println("Processando: removendo ruído de sal e pimenta...");
        for (int i = 0; i < 3; i++) {
            System.out.printf(" - Passada %d de 3\n", i + 1);
            processador.removerSalPimenta(1);
        }

        long tempoFinalTotal = System.currentTimeMillis();
        System.out.printf("Correção do vídeo concluída em %d ms\n", (tempoFinalTotal - tempoInicialTotal));

        System.out.println("Salvando vídeo corrigido em: " + caminhoGravar);
        processador.gravarVideo(caminhoGravar, fps);
        System.out.println("Processamento finalizado com sucesso!");
    }
}
