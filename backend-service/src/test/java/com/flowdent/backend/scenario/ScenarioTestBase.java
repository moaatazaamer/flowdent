package com.flowdent.backend.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Boots the whole backend against an in-memory database and drives it through the REST API,
 * exactly like the React admin does. Seeded accounts: admin/admin, orderadmin/orderadmin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ScenarioTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected static final String TODAY = LocalDate.now().toString();

    protected String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("Login failed for " + email + ": " + r.getResponse().getContentAsString());
        }
        return JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    protected String adminToken() throws Exception {
        return login("admin", "admin");
    }

    protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return token == null ? b : b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /** Sends a JSON request and returns the response body, failing if the status differs. */
    protected String call(String method, String url, Object body, String token, int expectedStatus) throws Exception {
        MockHttpServletRequestBuilder b = switch (method) {
            case "GET" -> MockMvcRequestBuilders.get(url);
            case "POST" -> MockMvcRequestBuilders.post(url);
            case "PUT" -> MockMvcRequestBuilders.put(url);
            case "DELETE" -> MockMvcRequestBuilders.delete(url);
            default -> throw new IllegalArgumentException(method);
        };
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult r = mvc.perform(auth(b, token)).andReturn();
        String content = r.getResponse().getContentAsString();
        if (r.getResponse().getStatus() != expectedStatus) {
            throw new AssertionError(method + " " + url + " expected " + expectedStatus + " but was "
                    + r.getResponse().getStatus() + ": " + content);
        }
        return content;
    }

    protected String getJson(String url, String token) throws Exception {
        return call("GET", url, null, token, 200);
    }

    protected String postJson(String url, Object body, String token) throws Exception {
        return call("POST", url, body, token, 201);
    }

    protected static BigDecimal num(String jsonText, String path) {
        Object v = JsonPath.read(jsonText, path);
        return new BigDecimal(String.valueOf(v));
    }

    protected static long id(String jsonText) {
        return ((Number) JsonPath.read(jsonText, "$.id")).longValue();
    }

    protected static String str(String jsonText, String path) {
        return String.valueOf((Object) JsonPath.read(jsonText, path));
    }

    /** Finds one element of a JSON array by id and returns it as JSON text. */
    protected String findById(String arrayJson, long id) throws Exception {
        List<Map<String, Object>> list = JsonPath.read(arrayJson, "$[?(@.id == " + id + ")]");
        if (list.isEmpty()) throw new AssertionError("id " + id + " not found in " + arrayJson);
        return json.writeValueAsString(list.get(0));
    }

    protected static Map<String, Object> map(Object... kv) {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
