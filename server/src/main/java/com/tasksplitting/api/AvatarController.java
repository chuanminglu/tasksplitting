package com.tasksplitting.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * T00301 最小上传闭环 (AC-1)。
 * <p>
 * {@code POST /api/avatars} 接收一个 {@link MultipartFile}，以 {@code UUID.原扩展名}
 * 落盘到本地存储目录，并把当前用户（由 {@link AuthInterceptor} 解析后放入
 * request attribute {@code userId}）的 {@code User.avatarUrl} 更新为
 * {@code /api/avatars/{文件名}}。
 * <p>
 * {@code GET /api/avatars/{filename}} 从本地存储读出字节流并按扩展名推断
 * Content-Type 返回（公开路径，文件名是 UUID 不可猜测）。
 * <p>
 * 注意：文件<b>格式/大小校验属于 T00303</b>，本任务刻意不实现（仅拒绝空文件）。
 */
@RestController
@RequestMapping("/api/avatars")
@CrossOrigin(origins = "http://localhost:5173")
public class AvatarController {

    /** 本地存储根目录（相对进程工作目录；server 从 server/ 目录启动时为 server/uploads/avatars/）。 */
    private final Path avatarDir;
    private final UserRepository userRepository;

    public AvatarController(UserRepository userRepository,
                            @Value("${avatar.upload-dir:uploads/avatars}") String uploadDir) {
        this.avatarDir = Paths.get(uploadDir);
        this.userRepository = userRepository;
    }

    /** 允许上传的图片 Content-Type（T00303 AC-3：仅 jpg/png）。 */
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES =
            java.util.Set.of("image/jpeg", "image/png");

    /** 上传大小上限：2MB（T00303 AC-3）。 */
    private static final long MAX_SIZE_BYTES = 2L * 1024 * 1024;

    @PostMapping
    public ResponseEntity<?> upload(@RequestAttribute("userId") int userId,
                                    @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "file is required"));
        }

        // T00303 (AC-3) 格式校验：仅依据声明的 contentType 判断，不做魔数字节嗅探。
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "UNSUPPORTED_FORMAT", "message", "仅支持jpg/png格式")));
        }

        // T00303 (AC-3) 大小校验：超过 2MB 拒绝。
        if (file.getSize() > MAX_SIZE_BYTES) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "FILE_TOO_LARGE", "message", "文件大小不能超过2MB")));
        }

        String extension = extractExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString() + (extension.isEmpty() ? "" : "." + extension);

        try {
            Files.createDirectories(avatarDir);
            Path target = avatarDir.resolve(fileName);
            try (var in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to store uploaded avatar", e);
        }

        String url = "/api/avatars/" + fileName;
        userRepository.updateAvatarUrl(userId, url);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<?> download(@PathVariable("filename") String filename) {
        // 防御式路径校验：只允许 [A-Za-z0-9._-]，禁止目录穿越
        if (filename == null || !filename.matches("[A-Za-z0-9._-]+") || filename.contains("..")) {
            return ResponseEntity.badRequest().body(Map.of("message", "invalid filename"));
        }
        Path file = avatarDir.resolve(filename);
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "avatar not found"));
        }
        try {
            byte[] body = Files.readAllBytes(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentTypeFor(filename)))
                    .body(body);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read avatar", e);
        }
    }

    private static String extractExtension(String originalFilename) {
        if (originalFilename == null) return "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) return "";
        String ext = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        // 只放行安全字符，避免把奇怪的东西写进文件名
        return ext.matches("[a-z0-9]+") ? ext : "";
    }

    private static String contentTypeFor(String filename) {
        int dot = filename.lastIndexOf('.');
        String ext = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
