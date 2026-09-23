package com.tasksplitting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * T00301 最小上传闭环 (AC-1)：
 * <ul>
 *   <li>登录用户 {@code POST /api/avatars} 上传文件 → 200，响应含头像 URL，
 *       {@code User.avatarUrl} 被更新，文件落盘 {@code uploads/avatars/}。</li>
 *   <li>无 token → 401。</li>
 *   <li>{@code GET /api/avatars/{filename}} 返回上传的字节流，Content-Type 按扩展名推断。</li>
 * </ul>
 * 注意：本任务<b>不做格式/大小校验</b>（属 T00303），测试仅验证闭环路径。
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-t00301.db"
})
@AutoConfigureMockMvc
class AvatarUploadIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper objectMapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void cleanupUploadDir() throws Exception {
        Path dir = Paths.get("uploads", "avatars");
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            }
        }
    }

    private String loginAndGetToken(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("token").asText();
    }

    @Test
    void uploadWithValidTokenReturnsUrlAndPersistsAvatar() throws Exception {
        String username = "avatar-" + System.nanoTime();
        User user = users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        byte[] bytes = "fake-avatar-bytes-0123".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
            "file", "my.png", "image/png", bytes);

        MvcResult result = mvc.perform(multipart("/api/avatars")
                .file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").isString())
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String url = body.path("url").asText();
        assertTrue(url.startsWith("/api/avatars/"), "url should be /api/avatars/<uuid>.png, got " + url);
        assertTrue(url.endsWith(".png"), "url should keep the original extension, got " + url);

        // DB 已更新
        User reloaded = users.findById(user.id())
            .orElseThrow(() -> new AssertionError("user missing after upload"));
        assertEquals(url, reloaded.avatarUrl());

        // 文件已落盘
        String filename = url.substring("/api/avatars/".length());
        Path stored = Paths.get("uploads", "avatars").resolve(filename);
        assertTrue(Files.isRegularFile(stored), "expected uploaded file at " + stored);
        assertArrayEquals(bytes, Files.readAllBytes(stored));
    }

    @Test
    void uploadWithoutTokenReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "a.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/avatars").file(file))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", org.hamcrest.Matchers.equalTo("UNAUTHORIZED")));
    }

    @Test
    void uploadEmptyFileReturns400() throws Exception {
        String username = "avatar-empty-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.png", "image/png", new byte[0]);
        mvc.perform(multipart("/api/avatars").file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void uploadUnsupportedFormatReturns400AndDoesNotPersist() throws Exception {
        String username = "avatar-badfmt-" + System.nanoTime();
        User user = users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        // 非 jpg/png 的声明 contentType → UNSUPPORTED_FORMAT，不保存、不更新 avatarUrl。
        MockMultipartFile file = new MockMultipartFile(
            "file", "anim.gif", "image/gif", "gif-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/avatars").file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FORMAT"))
            .andExpect(jsonPath("$.error.message").value("仅支持jpg/png格式"));

        // avatarUrl 未被更新
        User reloaded = users.findById(user.id())
                .orElseThrow(() -> new AssertionError("user missing"));
        org.junit.jupiter.api.Assertions.assertNull(reloaded.avatarUrl(), "avatarUrl must not change on unsupported format");
    }

    @Test
    void uploadOversizedFileReturns400AndDoesNotPersist() throws Exception {
        String username = "avatar-large-" + System.nanoTime();
        User user = users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        // 合规格式（image/png）但超过 2MB → FILE_TOO_LARGE，不保存、不更新 avatarUrl。
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
            "file", "big.png", "image/png", big);
        mvc.perform(multipart("/api/avatars").file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"))
            .andExpect(jsonPath("$.error.message").value("文件大小不能超过2MB"));

        User reloaded = users.findById(user.id())
                .orElseThrow(() -> new AssertionError("user missing"));
        org.junit.jupiter.api.Assertions.assertNull(reloaded.avatarUrl(), "avatarUrl must not change on oversized file");
    }

    @Test
    void uploadSupportsBoundarySizeExactlyTwoMegaBytes() throws Exception {
        String username = "avatar-edge-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        // 恰好 2MB 属于"不超过 2MB"，应放行并成功落盘。
        byte[] exactly = new byte[2 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
            "file", "edge.png", "image/png", exactly);
        mvc.perform(multipart("/api/avatars").file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").isString());
    }

    @Test
    void downloadReturnsUploadedBytesWithInferredContentType() throws Exception {
        String username = "avatar-dl-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        byte[] bytes = "png-bytes-4567".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", bytes);
        MvcResult up = mvc.perform(multipart("/api/avatars").file(file)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        String url = objectMapper.readTree(up.getResponse().getContentAsString()).path("url").asText();
        String filename = url.substring("/api/avatars/".length());

        MvcResult dl = mvc.perform(get("/api/avatars/" + filename))
            .andExpect(status().isOk())
            .andReturn();
        assertArrayEquals(bytes, dl.getResponse().getContentAsByteArray());
        assertEquals("image/png", dl.getResponse().getContentType());
    }

    @Test
    void downloadMissingAvatarReturns404() throws Exception {
        mvc.perform(get("/api/avatars/does-not-exist.png"))
            .andExpect(status().isNotFound());
    }

    @Test
    void downloadPathTraversalIsRejected() throws Exception {
        mvc.perform(get("/api/avatars/..%2F..%2Fetc%2Fpasswd"))
            .andExpect(result ->
                org.junit.jupiter.api.Assertions.assertTrue(
                    result.getResponse().getStatus() == 400 || result.getResponse().getStatus() == 404));
    }
}
