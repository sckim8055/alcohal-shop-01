package me.nexthub.sool.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import me.nexthub.sool.sample.SampleData;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 테스트용 API 호출 도우미. 브라우저 한 개(세션 하나)를 흉내 낸다. */
public class Api {

    public record Res(int status, JsonNode body) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;
    private final MockHttpSession session = new MockHttpSession();
    private String csrf;

    public Api(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** 화면을 처음 연 것처럼 토큰을 받는다. */
    public Api open() {
        csrf = get("/api/me").body().get("csrfToken").asString();
        return this;
    }

    public Api login(String email) {
        open();
        Res r = post("/api/auth/login", Map.of("email", email, "password", SampleData.PASSWORD));
        assertEquals(200, r.status(), "로그인 실패: " + email);
        csrf = r.body().get("csrfToken").asString();
        return this;
    }

    public Res get(String path) {
        return run(MockMvcRequestBuilders.get(path), null, false);
    }

    public Res post(String path, Object body) {
        return run(MockMvcRequestBuilders.post(path), body, true);
    }

    public Res post(String path) {
        return run(MockMvcRequestBuilders.post(path), null, true);
    }

    public Res put(String path, Object body) {
        return run(MockMvcRequestBuilders.put(path), body, true);
    }

    /** 파일 올리기 */
    public Res upload(String fileName, byte[] content) {
        try {
            var builder = MockMvcRequestBuilders.multipart("/api/files")
                    .file(new org.springframework.mock.web.MockMultipartFile("file", fileName, "application/octet-stream", content));
            builder.session(session);
            if (csrf != null) {
                builder.header("X-CSRF-Token", csrf);
            }
            MockHttpServletResponse response = mvc.perform(builder).andReturn().getResponse();
            String text = response.getContentAsString(StandardCharsets.UTF_8);
            return new Res(response.getStatus(), text.isEmpty() ? JSON.nullNode() : JSON.readTree(text));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 토큰 없이 보내는 요청(위조 요청 흉내) */
    public Res postWithoutToken(String path, Object body) {
        return run(MockMvcRequestBuilders.post(path), body, false);
    }

    private Res run(MockHttpServletRequestBuilder builder, Object body, boolean withToken) {
        try {
            builder.session(session);
            if (withToken && csrf != null) {
                builder.header("X-CSRF-Token", csrf);
            }
            if (body != null) {
                builder.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
            }
            MockHttpServletResponse response = mvc.perform(builder).andReturn().getResponse();
            String text = response.getContentAsString(StandardCharsets.UTF_8);
            JsonNode node = text.isEmpty() ? JSON.nullNode() : JSON.readTree(text);
            // 로그인하거나 비밀번호를 바꾸면 토큰이 새로 나온다. 브라우저 쪽 코드와 같게 따라간다.
            if (response.getStatus() == 200 && node.isObject() && node.has("csrfToken")) {
                csrf = node.get("csrfToken").asString();
            }
            return new Res(response.getStatus(), node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
