package ui;

import archivos.FileCompressor;
import batch.BatchProcessor;
import batch.BatchProcessor.BatchConfig;
import batch.BatchProcessor.Mode;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Optional;

public class MainApp extends Application {

    private CheckBox chkRecursive;
    private CheckBox chkOverwrite;
    private CheckBox chkDryRun;
    private TextField txtInclude;
    private TextField txtExclude;
    private TextArea console;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Compresor Seguro (LZSS + XOR) — Fase 6");

        // ===== Botones (no cambia la lógica) =====
        Button btnCmp    = styledButton("Comprimir archivo…",   "primary");
        btnCmp.setOnAction(e -> onCompressFile(stage));

        Button btnDecmp  = styledButton("Descomprimir archivo…","secondary");
        btnDecmp.setOnAction(e -> onDecompressFile(stage));

        Button btnCmpEnc = styledButton("Comprimir + Encriptar…","accent");
        btnCmpEnc.setOnAction(e -> onCompressEncryptFile(stage));

        Button btnDecEnc = styledButton("Desencriptar + Descomprimir…","warning");
        btnDecEnc.setOnAction(e -> onDecryptDecompressFile(stage));

        Button btnBatch  = styledButton("Procesar carpeta…","primary-outline");
        btnBatch.setOnAction(e -> onBatch(stage));

        // Íconos PNG (ponlos en /ui/icons/*.png)
        attachIcon(btnCmp,    "compress.png");
        attachIcon(btnDecmp,  "decompress.png");
        attachIcon(btnCmpEnc, "encrypt.png");
        attachIcon(btnDecEnc, "decrypt.png");
        attachIcon(btnBatch,  "batch.png");

        HBox actions = new HBox(10, btnCmp, btnDecmp, btnCmpEnc, btnDecEnc, btnBatch);
        actions.getStyleClass().add("actions-bar");
        actions.setPadding(new Insets(14));

        // ===== Panel inferior de opciones =====
        chkRecursive = new CheckBox("Recursivo");    chkRecursive.setSelected(true);
        chkOverwrite = new CheckBox("Sobrescribir"); chkOverwrite.setSelected(false);
        chkDryRun    = new CheckBox("Dry-run");      chkDryRun.setSelected(false);
        chkRecursive.getStyleClass().add("flat-check");
        chkOverwrite.getStyleClass().add("flat-check");
        chkDryRun.getStyleClass().add("flat-check");

        txtInclude = new TextField(".txt,.md");
        txtExclude = new TextField(".log");
        txtInclude.getStyleClass().add("flat-input");
        txtExclude.getStyleClass().add("flat-input");

        GridPane opts = new GridPane();
        opts.getStyleClass().add("options-card");
        opts.setHgap(10); opts.setVgap(8); opts.setPadding(new Insets(14));
        int r = 0;
        opts.add(label("Include:"), 0, r); opts.add(txtInclude, 1, r++);
        opts.add(label("Exclude:"), 0, r); opts.add(txtExclude, 1, r++);
        opts.add(chkRecursive, 0, r); opts.add(chkOverwrite, 1, r); opts.add(chkDryRun, 2, r);

        // ===== Consola (más compacta) =====
        console = new TextArea();
        console.setEditable(false);
        console.setWrapText(true);
        console.setPrefRowCount(10);
        console.setPrefHeight(220);
        console.getStyleClass().add("console");

        // spacer para que la consola no se expanda de más
        Region spacer = new Region();
        VBox middle = new VBox(console, spacer);
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // ===== Root =====
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(actions);
        root.setCenter(middle);
        root.setBottom(opts);

        Scene scene = new Scene(root, 1000, 600);
        URL css = getClass().getResource("/ui/style.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.show();

        log("Listo. Elige una acción.\n");
    }

    // ========== Acciones archivo único ==========

    private void onCompressFile(Stage owner) {
        File in = chooseFile(owner, "Selecciona archivo de texto", "*.txt", "*.md");
        if (in == null) return;
        File out = chooseSave(owner, "Guardar .cmp", stripExt(in.getName()) + ".cmp");
        if (out == null) return;
        try {
            FileCompressor.comprimirArchivo(in.getAbsolutePath(), out.getAbsolutePath());
            logOk("Comprimir: " + in + " -> " + out);
            logSizes(Path.of(in.getAbsolutePath()), Path.of(out.getAbsolutePath()), "CMP");
        } catch (Exception ex) {
            logFriendlyError("Error al comprimir", ex);
        }
    }

    private void onDecompressFile(Stage owner) {
        File in = chooseFile(owner, "Selecciona archivo .cmp", "*.cmp");
        if (in == null) return;
        File out = chooseSave(owner, "Guardar .txt", stripExt(in.getName()) + ".txt");
        if (out == null) return;
        try {
            FileCompressor.descomprimirArchivo(in.getAbsolutePath(), out.getAbsolutePath());
            logOk("Descomprimir: " + in + " -> " + out);
            logSizes(null, Path.of(out.getAbsolutePath()), "TXT (recuperado)");
            maybeCompareWithOriginal(owner, Path.of(out.getAbsolutePath()));
        } catch (Exception ex) {
            logFriendlyError("Error al descomprimir", ex);
        }
    }

    private void onCompressEncryptFile(Stage owner) {
        File in = chooseFile(owner, "Selecciona archivo de texto", "*.txt", "*.md");
        if (in == null) return;
        String password = askPassword("Password para encriptar");
        if (password == null || password.isBlank()) return;

        File out = chooseSave(owner, "Guardar .ec", stripExt(in.getName()) + ".ec");
        if (out == null) return;
        try {
            FileCompressor.comprimirYEncriptarArchivo(in.getAbsolutePath(), out.getAbsolutePath(), password);
            logOk("Comprimir+Encriptar: " + in + " -> " + out);
            logSizes(Path.of(in.getAbsolutePath()), Path.of(out.getAbsolutePath()), "EC");
        } catch (Exception ex) {
            logFriendlyError("Error al comprimir+encriptar", ex);
        }
    }

    private void onDecryptDecompressFile(Stage owner) {
        File in = chooseFile(owner, "Selecciona archivo .ec", "*.ec");
        if (in == null) return;

        String password = askPassword("Password para descifrar");
        if (password == null || password.isBlank()) return;

        // Validar password ANTES de pedir la ruta de salida
        boolean ok;
        try {
            ok = FileCompressor.validarPassword(in.getAbsolutePath(), password);
        } catch (Exception ignored) { ok = false; }

        if (!ok) {
            logErr("Contraseña incorrecta.");
            showError("Contraseña incorrecta", "La contraseña no coincide o el archivo fue cifrado con otra clave.");
            return;
        }

        File out = chooseSave(owner, "Guardar .txt", stripExt(in.getName()) + ".txt");
        if (out == null) return;

        try {
            FileCompressor.desencriptarYDescomprimirArchivo(in.getAbsolutePath(), out.getAbsolutePath(), password);
            logOk("Desencriptar+Descomprimir: " + in + " -> " + out);
            logSizes(null, Path.of(out.getAbsolutePath()), "TXT (recuperado)");
            maybeCompareWithOriginal(owner, Path.of(out.getAbsolutePath()));
        } catch (Exception ex) {
            if (looksLikeWrongPassword(ex)) {
                logErr("Contraseña incorrecta.");
                showError("Contraseña incorrecta", "La contraseña no coincide o el archivo fue cifrado con otra clave.");
            } else {
                logFriendlyError("Error al desencriptar+descomprimir", ex);
            }
        }
    }

    // ========== Batch por carpeta ==========

    private void onBatch(Stage owner) {
        ChoiceDialog<String> dlg = new ChoiceDialog<>("COMPRESION (.txt -> .cmp)",
                "COMPRESION (.txt -> .cmp)",
                "COMPRESION+ENCRIPTACION (.txt -> .ec)",
                "DESCOMPRESION (.cmp -> .txt)",
                "DESCIFRAR+DESCOMPRIMIR (.ec -> .txt)");
        dlg.setTitle("Procesar carpeta");
        dlg.setHeaderText("Elige el modo de procesamiento");

        Optional<String> choice = dlg.showAndWait();
        if (choice.isEmpty()) return;

        Mode mode = switch (choice.get()) {
            case "COMPRESION (.txt -> .cmp)" -> Mode.COMPRESS;
            case "COMPRESION+ENCRIPTACION (.txt -> .ec)" -> Mode.COMPRESS_ENCRYPT;
            case "DESCOMPRESION (.cmp -> .txt)" -> Mode.DECOMPRESS;
            case "DESCIFRAR+DESCOMPRIMIR (.ec -> .txt)" -> Mode.DECRYPT_DECOMPRESS;
            default -> Mode.COMPRESS;
        };

        File inDir = chooseDir(owner, "Carpeta de entrada");
        if (inDir == null) return;
        File outDir = chooseDir(owner, "Carpeta de salida");
        if (outDir == null) return;

        String password = null;
        if (mode == Mode.COMPRESS_ENCRYPT || mode == Mode.DECRYPT_DECOMPRESS) {
            password = askPassword("Password");
            if (password == null || password.isBlank()) return;
        }

        try {
            BatchConfig cfg = new BatchConfig(inDir.getAbsolutePath(), outDir.getAbsolutePath(), mode)
                    .recursive(chkRecursive.isSelected())
                    .overwrite(chkOverwrite.isSelected())
                    .dryRun(chkDryRun.isSelected());
            if (password != null) cfg.password(password);

            if (mode == Mode.COMPRESS || mode == Mode.COMPRESS_ENCRYPT) {
                String includeCsv = txtInclude.getText().trim();
                String excludeCsv = txtExclude.getText().trim();
                if (!includeCsv.isBlank()) cfg.include(splitCsv(includeCsv));
                if (!excludeCsv.isBlank()) cfg.exclude(splitCsv(excludeCsv));
            }

            log("\n=== BATCH " + mode.label + " ===");
            BatchProcessor.runBatch(cfg);
            log("=== FIN BATCH ===\n");
        } catch (Exception ex) {
            if (mode == Mode.DECRYPT_DECOMPRESS && looksLikeWrongPassword(ex)) {
                logErr("Contraseña incorrecta en algún archivo del lote.");
                showError("Contraseña incorrecta", "La contraseña no coincide para uno o más archivos.");
            } else {
                logFriendlyError("Error en batch", ex);
            }
        }
    }

    // ========== Utilidades UI/estilo ==========

    private Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("flat-label");
        return l;
    }

    private Button styledButton(String text, String styleClass) {
        Button b = new Button(text);
        b.getStyleClass().addAll("flat-btn", styleClass);
        return b;
    }

    private void attachIcon(Button b, String fileName) {
        try {
            URL url = getClass().getResource("/ui/icons/" + fileName);
            if (url == null) return; // si el ícono no existe, no falla
            Image img = new Image(url.toExternalForm(), 28, 28, true, true);
            ImageView iv = new ImageView(img);
            b.setGraphic(iv);
            b.setContentDisplay(ContentDisplay.LEFT);
            b.setGraphicTextGap(12);
        } catch (Exception ignored) { }
    }

    private File chooseFile(Stage owner, String title, String... filters) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        if (filters != null && filters.length > 0)
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(String.join(", ", filters), filters));
        return fc.showOpenDialog(owner);
    }

    private File chooseSave(Stage owner, String title, String suggested) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialFileName(suggested);
        return fc.showSaveDialog(owner);
    }

    private File chooseDir(Stage owner, String title) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle(title);
        return dc.showDialog(owner);
    }

    private String askPassword(String title) {
        TextInputDialog td = new TextInputDialog();
        td.setTitle(title);
        td.setHeaderText(null);
        td.setContentText("Password:");
        Optional<String> res = td.showAndWait();
        return res.orElse(null);
    }

    private String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return (i >= 0) ? name.substring(0, i) : name;
    }

    private String[] splitCsv(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    private void log(String s)   { console.appendText(s + "\n"); }
    private void logOk(String s) { console.appendText("[OK] " + s + "\n"); }
    private void logErr(String s){ console.appendText("[ERR] " + s + "\n"); }

    private void showError(String header, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText(header);
        a.setContentText((content == null || content.isBlank()) ? "(sin detalle)" : content);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        a.showAndWait();
    }

    // ========== Extras: tamaños, comparación, errores amigables ==========

    private void logSizes(Path original, Path result, String etiqueta) {
        try {
            if (original != null) {
                long inB  = Files.size(original);
                log("  Tamaño original : " + inB + " B (" + human(inB) + ")");
            }
            long outB = Files.size(result);
            log("  Tamaño " + etiqueta + " : " + outB + " B (" + human(outB) + ")\n");
        } catch (IOException ignored) { }
    }

    private void maybeCompareWithOriginal(Stage owner, Path recovered) {
        ButtonType si = new ButtonType("Comparar…", ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("Omitir", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "¿Comparar el recuperado con el original?", si, no);
        a.setHeaderText(null);
        a.setTitle("Comparar");
        a.showAndWait().ifPresent(bt -> {
            if (bt == si) {
                File orig = chooseFile(owner, "Selecciona el archivo ORIGINAL", "*.*");
                if (orig != null) compareFiles(Path.of(orig.getAbsolutePath()), recovered);
            }
        });
    }

    private void compareFiles(Path a, Path b) {
        try {
            long sa = Files.size(a), sb = Files.size(b);
            log("  Original : " + sa + " B (" + human(sa) + ")");
            log("  Recuperado: " + sb + " B (" + human(sb) + ")");
            if (sa != sb) {
                logErr("  Resultado: DIFERENTES (tamaños distintos)");
                return;
            }
            String ha = sha256(a), hb = sha256(b);
            if (ha.equals(hb)) {
                logOk("  Resultado: IGUALES (byte a byte)  SHA-256=" + ha + "\n");
            } else {
                logErr("  Resultado: DIFERENTES (hash distinto)  orig=" + ha + " rec=" + hb + "\n");
            }
        } catch (Exception ex) {
            logErr("Error al comparar: " + ex.getMessage());
        }
    }

    private String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(p)) {
            byte[] buf = new byte[1 << 20]; // 1 MB
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        byte[] dig = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte x : dig) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private String human(long bytes) {
        double b = bytes;
        String[] u = {"B","KB","MB","GB","TB"};
        int i = 0;
        while (b >= 1024 && i < u.length-1) { b /= 1024; i++; }
        return String.format("%.2f %s", b, u[i]);
    }

    private boolean looksLikeWrongPassword(Throwable ex) {
        String m = (ex.getMessage() == null) ? "" : ex.getMessage().toLowerCase();
        return m.contains("malformed")
                || m.contains("input length")
                || m.contains("bytes to tokens")
                || m.contains("formato")
                || m.contains("invalid")
                || m.contains("out of bounds");
    }


    private void loglyfriendlyerror(String header, Exception ex) {
        logFriendlyError(header, ex);
    }

    private void logFriendlyError(String titulo, Exception ex) {
        String msg = (ex.getMessage() == null || ex.getMessage().isBlank())
                ? ex.toString()
                : ex.getMessage();
        logErr(titulo + ": " + msg);
        showError(titulo, msg);
    }

    public static void main(String[] args) { launch(args); }
}
