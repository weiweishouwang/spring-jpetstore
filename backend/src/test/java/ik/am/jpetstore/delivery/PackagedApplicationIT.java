package ik.am.jpetstore.delivery;

import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T395: 前端资源与JAR装配验收。
 *
 * <p>用真实打包产物验收：同一个 JAR 同时提供 Vue 入口与 API，未知 API 返回错误而不是 HTML，
 * 没有旧构建产物残留。这里启动的是真实进程，不是 mock 上下文（plan §9.3）。</p>
 *
 * <p>该验收需要发布形态的 JAR（前端已装配进静态资源），因此由 release profile 触发
 * （见 backend/pom.xml 的 failsafe 配置）。</p>
 */
class PackagedApplicationIT {

    private Process process;

    private int port;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void startPackagedApplication() throws Exception {
        Path jar = packagedJar();
        port = freePort();
        ProcessBuilder builder = new ProcessBuilder("java", "-jar", jar.toAbsolutePath().toString());
        builder.environment().put("SPRING_DATASOURCE_URL", MySqlTestDatabase.url());
        builder.environment().put("SPRING_DATASOURCE_USERNAME", MySqlTestDatabase.username());
        builder.environment().put("SPRING_DATASOURCE_PASSWORD", MySqlTestDatabase.password());
        builder.environment().put("BACKEND_PORT", Integer.toString(port));
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(Path.of("target", "packaged-application.log").toFile()));
        process = builder.start();
        awaitHealth();
    }

    @AfterEach
    void stopPackagedApplication() throws Exception {
        if (process != null) {
            process.destroy();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
    }

    @Test
    void theFrontendEntryIsServed() throws Exception {
        HttpResponse<String> response = get("/cart");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("id=\"app\"");
    }

    @Test
    void theApiIsServedFromTheSameApplication() throws Exception {
        HttpResponse<String> response = get("/api/categories");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/json");
    }

    @Test
    void anUnknownApiPathIsNotTheFrontendEntry() throws Exception {
        HttpResponse<String> response = get("/api/does-not-exist");

        assertThat(response.statusCode())
                .as("未知 API 是错误响应，不是前端入口")
                .isBetween(400, 499);
        assertThat(response.body()).doesNotContain("id=\"app\"");
    }

    @Test
    void noStaleBuildOutputIsPackaged() throws Exception {
        Path jar = packagedJar();
        try (var entries = Files.list(jar.getParent())) {
            List<Path> jars = entries.filter(path -> path.getFileName().toString().endsWith(".jar")).toList();
            assertThat(jars).as("打包目录里只有当前产物").hasSize(1);
        }
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "text/html,application/json")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void awaitHealth() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while (System.nanoTime() < deadline) {
            if (process != null && !process.isAlive()) {
                throw new IllegalStateException("打包应用启动失败，退出码 " + process.exitValue());
            }
            try {
                HttpResponse<String> response = get("/actuator/health");
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // 还没起来，继续等待。
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("打包应用在限定时间内没有就绪");
    }

    private Path packagedJar() throws IOException {
        try (var entries = Files.list(Path.of("target"))) {
            List<Path> jars = entries
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .filter(path -> !path.getFileName().toString().endsWith(".jar.original"))
                    .toList();
            assertThat(jars).as("必须先完成打包").hasSize(1);
            return jars.get(0);
        }
    }

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
