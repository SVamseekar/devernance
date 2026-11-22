package os.assurance.eu.api.evidence;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import jakarta.annotation.PreDestroy;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * all-MiniLM-L6-v2 sentence embeddings (384 dimensions) through ONNX Runtime. The model files are pinned to one
 * Hugging Face revision and checked against their SHA-256 before loading, so a changed or tampered upstream file
 * cannot slip in. They are kept in {@code modelDir}: the production image bakes them in, so the API starts
 * without reaching Hugging Face; elsewhere they are downloaded once and reused.
 */
public class DjlSentenceEmbeddingProvider implements EvidenceEmbeddingProvider {

    static final String REVISION = "1110a243fdf4706b3f48f1d95db1a4f5529b4d41";
    private static final String BASE =
        "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/" + REVISION;
    static final ModelFile MODEL = new ModelFile(
        "model.onnx", BASE + "/onnx/model.onnx",
        "6fd5d72fe4589f189f8ebc006442dbb529bb7ce38f8082112682524616046452");
    static final ModelFile TOKENIZER = new ModelFile(
        "tokenizer.json", BASE + "/tokenizer.json",
        "be50c3628f2bf5bb5e3a7f17b1f74611b2561a3a27eeab05e5aa30f411572037");
    /** The model's position table holds 512; inputs are cut at the 256 tokens sentence-transformers uses. */
    static final int MAX_TOKENS = 256;

    private final ZooModel<NDList, NDList> model;
    private final HuggingFaceTokenizer tokenizer;

    public DjlSentenceEmbeddingProvider(Path modelDir) throws Exception {
        Files.createDirectories(modelDir);
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
        Path modelFile = ensure(client, modelDir, MODEL);
        // Load tokenizer from an absolute file path. Passing a bare HuggingFace repo id
        // breaks inside some container runtimes (RelativeUrlWithoutBase).
        Path tokenizerFile = ensure(client, modelDir, TOKENIZER);

        Criteria<NDList, NDList> criteria = Criteria.builder()
            .setTypes(NDList.class, NDList.class)
            .optModelPath(modelFile.getParent())
            .optModelName("model")
            .optEngine("OnnxRuntime")
            .optTranslator(new PassThroughTranslator())
            .build();
        this.model = criteria.loadModel();
        // Without truncation a long question or an unbroken run of text exceeds the model's 512 positions and fails.
        this.tokenizer = HuggingFaceTokenizer.builder()
            .optTokenizerPath(tokenizerFile)
            .optTruncation(true)
            .optMaxLength(MAX_TOKENS)
            .build();
    }

    record ModelFile(String name, String url, String sha256) {}

    /** Returns the verified file, downloading it first when it is missing or does not match its checksum. */
    static Path ensure(HttpClient client, Path dir, ModelFile file) throws Exception {
        Path dest = dir.resolve(file.name());
        if (Files.exists(dest) && file.sha256().equals(sha256(dest))) {
            return dest;
        }
        Path part = dir.resolve(file.name() + ".part");
        HttpResponse<Path> resp = client.send(
            HttpRequest.newBuilder().uri(URI.create(file.url()))
                .timeout(Duration.ofMinutes(5))
                .GET().build(),
            HttpResponse.BodyHandlers.ofFile(part));
        if (resp.statusCode() != 200) {
            Files.deleteIfExists(part);
            throw new IllegalStateException(
                "Failed to download " + file.name() + " from " + file.url() + ": HTTP " + resp.statusCode());
        }
        String actual = sha256(part);
        if (!file.sha256().equals(actual)) {
            Files.deleteIfExists(part);
            throw new IllegalStateException(
                file.name() + " does not match its pinned SHA-256 (got " + actual + "); refusing to load it");
        }
        return Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
    }

    static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Override
    public String name() {
        return "djl-sentence";
    }

    /** Chunks per forward pass: large enough to amortise the call, small enough to bound padding and memory. */
    static final int BATCH_SIZE = 32;

    @Override
    public String embed(String text) {
        return embedAll(java.util.List.of(text)).get(0);
    }

    /**
     * Runs the texts through the model {@value #BATCH_SIZE} at a time. Each batch is padded to its longest text;
     * the attention mask keeps padding out of the mean pooling, so a text embeds the same alone or in a batch.
     */
    @Override
    public java.util.List<String> embedAll(java.util.List<String> texts) {
        java.util.List<String> out = new java.util.ArrayList<>(texts.size());
        try (Predictor<NDList, NDList> predictor = model.newPredictor()) {
            for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
                out.addAll(embedBatch(predictor, texts.subList(from, Math.min(texts.size(), from + BATCH_SIZE))));
            }
        } catch (Exception e) {
            throw new RuntimeException("Embedding failed", e);
        }
        return out;
    }

    private java.util.List<String> embedBatch(Predictor<NDList, NDList> predictor, java.util.List<String> texts)
            throws Exception {
        Encoding[] encodings = new Encoding[texts.size()];
        int seqLen = 1;
        for (int i = 0; i < texts.size(); i++) {
            encodings[i] = tokenizer.encode(texts.get(i), true, true);
            seqLen = Math.max(seqLen, encodings[i].getIds().length);
        }
        int batch = texts.size();
        long[] ids = new long[batch * seqLen];
        long[] mask = new long[batch * seqLen];
        for (int b = 0; b < batch; b++) {
            long[] tokenIds = encodings[b].getIds();
            long[] tokenMask = encodings[b].getAttentionMask();
            System.arraycopy(tokenIds, 0, ids, b * seqLen, tokenIds.length);
            System.arraycopy(tokenMask, 0, mask, b * seqLen, tokenMask.length);
        }
        try (NDManager manager = NDManager.newBaseManager("OnnxRuntime")) {
            NDArray inputIds = manager.create(ids, new Shape(batch, seqLen));
            NDArray attentionMask = manager.create(mask, new Shape(batch, seqLen));
            NDArray tokenTypeIds = manager.zeros(new Shape(batch, seqLen), DataType.INT64);
            NDList output = predictor.predict(new NDList(inputIds, attentionMask, tokenTypeIds));
            // token_embeddings [batch, seqLen, hidden], copied out because ONNX NDArrays support no tensor math.
            float[] tokens = output.get(0).toFloatArray();
            int hidden = tokens.length / (batch * seqLen);
            java.util.List<String> out = new java.util.ArrayList<>(batch);
            for (int b = 0; b < batch; b++) {
                out.add(meanPooledNormalized(tokens, mask, b, seqLen, hidden));
            }
            return out;
        }
    }

    private static String meanPooledNormalized(float[] tokens, long[] mask, int b, int seqLen, int hidden) {
        float[] pooled = new float[hidden];
        float maskSum = 0f;
        for (int t = 0; t < seqLen; t++) {
            if (mask[b * seqLen + t] == 0) continue;
            maskSum += 1f;
            int offset = (b * seqLen + t) * hidden;
            for (int h = 0; h < hidden; h++) {
                pooled[h] += tokens[offset + h];
            }
        }
        if (maskSum < 1e-9f) maskSum = 1e-9f;
        float norm = 0f;
        for (int h = 0; h < hidden; h++) {
            pooled[h] /= maskSum;
            norm += pooled[h] * pooled[h];
        }
        norm = (float) Math.sqrt(norm);
        if (norm < 1e-9f) norm = 1e-9f;
        StringBuilder sb = new StringBuilder();
        for (int h = 0; h < hidden; h++) {
            if (h > 0) sb.append(',');
            sb.append(pooled[h] / norm);
        }
        return sb.toString();
    }

    @Override
    public double similarity(String left, String right) {
        float[] l = parse(left);
        float[] r = parse(right);
        double dot = 0;
        for (int i = 0; i < Math.min(l.length, r.length); i++) dot += l[i] * r[i];
        return dot;
    }

    private float[] parse(String embedding) {
        String[] parts = embedding.split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Float.parseFloat(parts[i]);
        return v;
    }

    @PreDestroy
    public void close() {
        model.close();
    }

    private static final class PassThroughTranslator implements Translator<NDList, NDList> {
        @Override
        public NDList processInput(TranslatorContext ctx, NDList input) {
            return input;
        }
        @Override
        public NDList processOutput(TranslatorContext ctx, NDList output) {
            return output;
        }
        @Override
        public Batchifier getBatchifier() {
            return null;
        }
    }
}
