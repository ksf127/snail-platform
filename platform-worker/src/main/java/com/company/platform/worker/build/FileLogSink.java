package com.company.platform.worker.build;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 把每一行日志同步写入本地文件，构建结束后整体上传到 MinIO。
 */
public class FileLogSink implements LogSink, Closeable {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Path file;
    private final BufferedWriter writer;

    public FileLogSink(Path file) throws IOException {
        this.file = file;
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public Path file() {
        return file;
    }

    @Override
    public synchronized void line(String line) {
        try {
            writer.write("[" + LocalDateTime.now().format(TIME) + "] " + (line == null ? "" : line));
            writer.newLine();
            writer.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException("写入构建日志失败", ex);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        writer.flush();
        writer.close();
    }
}
