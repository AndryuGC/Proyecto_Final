package ui;

import archivos.FileCompressor;
import app.MainCrypto;
import batch.BatchProcessor;
import batch.BatchProcessor.BatchConfig;
import batch.BatchProcessor.Mode;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public class MainApp extends Application {

    private TextArea console;
    private TextField tfInclude, tfExclude;
    private CheckBox cbRecursive, cbOverwrite, cbDryRun;

    @Override
    public void start(Stage stage) {
        Button btnCompress = makeButton("Comprimir ...", "/ui/icons/compress.png");
        Button btnDecompress = makeButton("Descomprimir ...", "/ui/icons/decompress.png");
        Button btnCompEnc = makeButton("Comprimir + Encriptar ...", "/ui/icons/encrypt.png");
        Button btnDecDec = makeButton("Desencriptar + Descomprimir ...", "/ui/icons/decrypt.png");
        Button btnFolder = makeButton("Procesar carpeta ...", "/ui/icons/batch.png");

        HBox topBar = new HBox(8, btnCompress, btnDecompress, btnCompEnc, btnDecDec, btnFolder);
        topBar.setAlignment(Pos.CENTER_LEFT);

        console = new TextArea("Listo. Elige una acción.");
        console.setEditable(false);
        console.setPrefRowCount(14);

        tfInclude = new TextField(); tfInclude.setPromptText(".txt,.md");
        tfExclude = new TextField(); tfExclude.setPromptText(".log");
        cbRecursive = new CheckBox("Recursivo"); cbRecursive.setSelected(true);
        cbOverwrite = new CheckBox("Sobrescribir"); cbOverwrite.setSelected(true);
        cbDryRun = new CheckBox("Dry-run");

        GridPane bottom = new GridPane();
        bottom.setHgap(10); bottom.setVgap(8);
        bottom.add(new Label("Include:"), 0, 0);
        bottom.add(tfInclude, 1, 0);
        bottom.add(new Label("Exclude:"), 0, 1);
        bottom.add(tfExclude, 1, 1);
        bottom.add(new HBox(16, cbRecursive, cbOverwrite, cbDryRun), 1, 2);

        VBox root = new VBox(12, topBar, console, bottom);
        root.setPadding(new Insets(12));

        Stage st = stage;
        btnCompress.setOnAction(e -> doCompress(st));
        btnDecompress.setOnAction(e -> doDecompress(st));
        btnCompEnc.setOnAction(e -> doCompEncrypt(st));
        btnDecDec.setOnAction(e -> doDecryptDecompress(st));
        btnFolder.setOnAction(e -> doProcessFolder(st));

        stage.setTitle("Compresor Seguro (LZSS + XOR) — Fase 6");
        stage.setScene(new Scene(root, 860, 520));
        stage.show();
    }

    private Button makeButton(String text, String iconPath) {
        Button b = new Button(text);
        try {
            Image img = new Image(iconPath);
            ImageView iv = new ImageView(img);
            iv.setFitHeight(22); iv.setPreserveRatio(true);
            b.setGraphic(iv);
        } catch (Exception ignored) {}
        b.setMaxHeight(Double.MAX_VALUE);
        return b;
    }
    private void log(String s){ console.appendText("\n" + s); }
    private void showError(Exception ex){
        Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
        a.setHeaderText("Error"); a.showAndWait();
    }
    private void showWarn(String msg){
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null); a.showAndWait();
    }
    private Optional<String> askPassword(String title){
        TextInputDialog d = new TextInputDialog();
        d.setTitle(title); d.setHeaderText(null); d.setContentText("Contraseña:");
        return d.showAndWait();
    }

    // ===== Acciones de archivo único =====
    private void doCompress(Stage st){
        FileChooser fc = new FileChooser();
        fc.setTitle("Elegir archivo a comprimir");
        File f = fc.showOpenDialog(st);
        if (f == null) return;
        try {
            Path in = f.toPath();
            Path out = FileCompressor.changeExt(in, ".cmp");
            FileCompressor.compressFile(in, out);
            log("OK: " + in + " -> " + out);
        } catch (Exception ex){ log("ERROR: " + ex.getMessage()); showError(ex); }
    }

    private void doDecompress(Stage st){
        FileChooser fc = new FileChooser();
        fc.setTitle("Elegir .cmp a descomprimir");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CMP","*.cmp"));
        File f = fc.showOpenDialog(st);
        if (f == null) return;
        try {
            Path in = f.toPath();
            Path out = FileCompressor.changeExt(in, ".txt");
            FileCompressor.decompressFile(in, out);
            log("OK: " + in + " -> " + out);
        } catch (Exception ex){ log("ERROR: " + ex.getMessage()); showError(ex); }
    }

    private void doCompEncrypt(Stage st){
        FileChooser fc = new FileChooser();
        fc.setTitle("Elegir archivo a comprimir + encriptar");
        File f = fc.showOpenDialog(st);
        if (f == null) return;
        var pw = askPassword("Comprimir + Encriptar");
        if (pw.isEmpty() || pw.get().isBlank()) { showWarn("Contraseña vacía"); return; }
        try {
            Path in = f.toPath();
            Path out = FileCompressor.changeExt(in, ".ec");
            MainCrypto.comprimirYEncriptarArchivo(in.toString(), out.toString(), pw.get());
            log("OK: " + in + " -> " + out);
        } catch (Exception ex){ log("ERROR: " + ex.getMessage()); showError(ex); }
    }

    private void doDecryptDecompress(Stage st){
        FileChooser fc = new FileChooser();
        fc.setTitle("Elegir .ec a desencriptar + descomprimir");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("EC","*.ec"));
        File f = fc.showOpenDialog(st);
        if (f == null) return;
        var pw = askPassword("Desencriptar + Descomprimir");
        if (pw.isEmpty() || pw.get().isBlank()) { showWarn("Contraseña vacía"); return; }
        try {
            Path in = f.toPath();
            Path out = FileCompressor.changeExt(in, ".txt");
            MainCrypto.desencriptarYDescomprimirArchivo(in.toString(), out.toString(), pw.get());
            log("OK: " + in + " -> " + out);
        } catch (Exception ex){ log("ERROR: " + ex.getMessage()); showError(ex); }
    }

    // ===== Carpeta (primero pregunta MODO como antes) =====
    private void doProcessFolder(Stage st){
        Mode mode = askMode();
        if (mode == null) return;

        String pw = "";
        if (mode == Mode.COMPRESS_ENCRYPT || mode == Mode.DECRYPT_DECOMPRESS) {
            var pwOpt = askPassword("Contraseña");
            if (pwOpt.isEmpty() || pwOpt.get().isBlank()) { showWarn("Contraseña vacía"); return; }
            pw = pwOpt.get();
        }

        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Carpeta de entrada");
        File in = dc.showDialog(st);
        if (in == null) return;

        DirectoryChooser dc2 = new DirectoryChooser();
        dc2.setTitle("Carpeta de salida");
        File out = dc2.showDialog(st);
        if (out == null) return;

        try {
            BatchConfig cfg = new BatchConfig(in.getAbsolutePath(), out.getAbsolutePath(), mode)
                    .recursive(cbRecursive.isSelected())
                    .overwrite(cbOverwrite.isSelected())
                    .dryRun(cbDryRun.isSelected())
                    .password(pw);

            for (String ext : splitList(tfInclude.getText())) if (!ext.isBlank()) cfg.include(cleanExt(ext));
            for (String ext : splitList(tfExclude.getText())) if (!ext.isBlank()) cfg.exclude(cleanExt(ext));

            BatchProcessor.runBatch(cfg);
            log("Procesamiento completado: " + in + " -> " + out + " (" + mode + ")");
        } catch (Exception ex){ log("ERROR: " + ex.getMessage()); showError(ex); }
    }

    private Mode askMode(){
        ChoiceDialog<String> dlg = new ChoiceDialog<>(
                "Comprimir",
                "Comprimir",
                "Comprimir + Encriptar",
                "Descomprimir",
                "Desencriptar + Descomprimir"
        );
        dlg.setTitle("Procesar Carpeta");
        dlg.setHeaderText("¿Qué quieres hacer?");
        dlg.setContentText("Modo:");
        Optional<String> sel = dlg.showAndWait();
        if (sel.isEmpty()) return null;
        return switch (sel.get()) {
            case "Comprimir" -> Mode.COMPRESS;
            case "Comprimir + Encriptar" -> Mode.COMPRESS_ENCRYPT;
            case "Descomprimir" -> Mode.DECOMPRESS;
            case "Desencriptar + Descomprimir" -> Mode.DECRYPT_DECOMPRESS;
            default -> null;
        };
    }

    private static String[] splitList(String s){
        if (s == null || s.isBlank()) return new String[0];
        return Arrays.stream(s.split("[,;\\s]+")).toArray(String[]::new);
    }
    private static String cleanExt(String s){
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.startsWith(".") ? t.substring(1) : t;
    }

    public static void main(String[] args) { launch(args); }
}
