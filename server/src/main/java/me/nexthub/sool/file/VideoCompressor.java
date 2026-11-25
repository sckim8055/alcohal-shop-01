package me.nexthub.sool.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 올린 동영상을 ffmpeg 으로 줄인다(가로 최대 1280, H.264). 올린 사람을 기다리게 하지 않도록 뒤에서 한 번에 하나씩 처리한다.
 * 줄인 결과가 원본보다 작을 때만 바꿔 넣고, 실패하면 원본을 그대로 둔다.
 * ffmpeg 이 없으면 아무것도 하지 않는다(원본 그대로 보여 준다).
 */
@Service
public class VideoCompressor {

    private static final Logger log = LoggerFactory.getLogger(VideoCompressor.class);
    private static final int TIMEOUT_MINUTES = 20;

    private final JdbcClient db;
    private final String ffmpeg;
    private final boolean available;
    private final ExecutorService queue = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "video-compress");
        t.setDaemon(true);
        return t;
    });

    public VideoCompressor(JdbcClient db, @Value("${sool.ffmpeg-path:${SOOL_FFMPEG:ffmpeg}}") String ffmpeg) {
        this.db = db;
        this.ffmpeg = ffmpeg;
        this.available = check();
        if (available) {
            log.info("동영상 압축 사용: {}", ffmpeg);
        } else {
            log.warn("ffmpeg 을 찾지 못해 동영상을 줄이지 않고 원본 그대로 둡니다. (찾은 경로: {})", ffmpeg);
        }
    }

    public boolean available() {
        return available;
    }

    private boolean check() {
        try {
            Process p = new ProcessBuilder(ffmpeg, "-version")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** mp4 동영상이면 줄이기 대기열에 넣는다. 넣었으면 true */
    public boolean submit(Path file, String storedName) {
        if (!available || !storedName.endsWith(".mp4")) {
            setStatus(storedName, "skipped", null);
            return false;
        }
        setStatus(storedName, "pending", null);
        queue.submit(() -> compress(file, storedName));
        return true;
    }

    private void compress(Path file, String storedName) {
        Path tmp = file.resolveSibling(storedName + ".tmp.mp4");
        try {
            long before = Files.size(file);
            // 파일 이름은 서버가 지은 것만 넘기고, 셸을 거치지 않고 실행한다.
            Process p = new ProcessBuilder(List.of(ffmpeg, "-y", "-i", file.toString(),
                    "-vf", "scale=trunc(min(1280\\,iw)/2)*2:-2",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "28", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", tmp.toString()))
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new IllegalStateException("시간 초과");
            }
            if (p.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg 종료 코드 " + p.exitValue());
            }
            long after = Files.size(tmp);
            if (after > 0 && after < before) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                db.sql("UPDATE upload_file SET compress_status = 'done', original_size = :o, size_bytes = :s WHERE stored_name = :n")
                        .param("o", before).param("s", after).param("n", storedName).update();
                log.info("동영상 압축 완료: {} {}KB -> {}KB", storedName, before / 1024, after / 1024);
            } else {
                setStatus(storedName, "skipped", before);   // 이미 충분히 작다
            }
        } catch (Exception e) {
            log.warn("동영상 압축 실패(원본 유지): {} {}", storedName, e.toString());
            setStatus(storedName, "failed", null);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
                // 임시 파일은 다음 정리 때 지운다
            }
        }
    }

    private void setStatus(String storedName, String status, Long originalSize) {
        db.sql("UPDATE upload_file SET compress_status = :st, original_size = COALESCE(:o, original_size) WHERE stored_name = :n")
                .param("st", status).param("o", originalSize).param("n", storedName).update();
    }

    @PreDestroy
    void stop() {
        queue.shutdownNow();
    }
}
