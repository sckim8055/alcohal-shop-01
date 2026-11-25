package me.nexthub.sool.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import me.nexthub.sool.auth.Auth;
import me.nexthub.sool.auth.Member;
import me.nexthub.sool.common.ApiException;
import me.nexthub.sool.common.AppClock;
import me.nexthub.sool.common.Tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사진·동영상 올리기. 파일 종류는 이름이나 브라우저가 알려 준 값이 아니라 파일 앞부분의 내용으로 판단한다.
 * 저장 이름은 서버가 새로 짓는다. 지금은 서버 디스크(data/uploads)에 두고, 운영에서는 저장소 서비스로 옮긴다.
 */
@RestController
public class FileController {

    public static final String URL_PREFIX = "/api/files/";
    public static final Pattern STORED_URL = Pattern.compile("^/api/files/[a-f0-9]{32}\\.(jpg|png|gif|webp|mp4|webm)$");
    private static final long MAX_IMAGE = 5L * 1024 * 1024;
    private static final long MAX_VIDEO = 100L * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** compressing: 동영상을 뒤에서 줄이는 중이면 true (몇 분 뒤 용량이 줄어든다) */
    public record Uploaded(String url, String kind, String contentType, long size, boolean compressing) {
    }

    private record Type(String ext, String contentType, String kind) {
    }

    private final JdbcClient db;
    private final Auth auth;
    private final Tenant tenant;
    private final AppClock clock;
    private final Path dir;
    private final VideoCompressor compressor;

    public FileController(JdbcClient db, Auth auth, Tenant tenant, AppClock clock, VideoCompressor compressor,
                          @Value("${sool.upload-dir:./data/uploads}") String dir) throws IOException {
        this.db = db;
        this.auth = auth;
        this.tenant = tenant;
        this.clock = clock;
        this.compressor = compressor;
        this.dir = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(this.dir);
    }

    @PostMapping("/api/files")
    public Uploaded upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws IOException {
        Member user = auth.requireUser(request);
        if (file.isEmpty()) {
            throw ApiException.bad("파일이 비어 있습니다.");
        }
        byte[] head = new byte[16];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        }
        Type type = detect(head, read);
        if (type == null) {
            throw ApiException.bad("사진은 JPG·PNG·GIF·WEBP, 동영상은 MP4·WEBM 파일만 올릴 수 있습니다.");
        }
        long max = "image".equals(type.kind()) ? MAX_IMAGE : MAX_VIDEO;
        if (file.getSize() > max) {
            throw ApiException.bad("image".equals(type.kind())
                    ? "사진은 5MB 이하만 올릴 수 있습니다." : "동영상은 100MB 이하만 올릴 수 있습니다.");
        }
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);
        String name = HexFormat.of().formatHex(random) + "." + type.ext();
        file.transferTo(dir.resolve(name));
        db.sql("""
                INSERT INTO upload_file (tenant_id, member_id, stored_name, kind, content_type, size_bytes, created_at)
                VALUES (:t, :m, :n, :k, :c, :s, :now)
                """)
                .param("t", tenant.id()).param("m", user.id()).param("n", name).param("k", type.kind())
                .param("c", type.contentType()).param("s", file.getSize()).param("now", clock.now()).update();
        boolean compressing = "video".equals(type.kind()) && compressor.submit(dir.resolve(name), name);
        return new Uploaded(URL_PREFIX + name, type.kind(), type.contentType(), file.getSize(), compressing);
    }

    /** 이 회원이 올린 파일이면 종류(image, video)를, 아니면 null 을 돌려준다. */
    public String kindOfOwned(String url, long memberId) {
        if (url == null || !STORED_URL.matcher(url).matches()) {
            return null;
        }
        return db.sql("SELECT kind FROM upload_file WHERE stored_name = :n AND member_id = :m AND tenant_id = :t")
                .param("n", url.substring(URL_PREFIX.length())).param("m", memberId).param("t", tenant.id())
                .query(String.class).optional().orElse(null);
    }

    public Path directory() {
        return dir;
    }

    private static Type detect(byte[] b, int n) {
        if (n >= 3 && u(b[0]) == 0xFF && u(b[1]) == 0xD8 && u(b[2]) == 0xFF) {
            return new Type("jpg", "image/jpeg", "image");
        }
        if (n >= 8 && u(b[0]) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return new Type("png", "image/png", "image");
        }
        if (n >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return new Type("gif", "image/gif", "image");
        }
        if (n >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new Type("webp", "image/webp", "image");
        }
        if (n >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') {
            return new Type("mp4", "video/mp4", "video");
        }
        if (n >= 4 && u(b[0]) == 0x1A && u(b[1]) == 0x45 && u(b[2]) == 0xDF && u(b[3]) == 0xA3) {
            return new Type("webm", "video/webm", "video");
        }
        return null;
    }

    private static int u(byte b) {
        return b & 0xFF;
    }
}
