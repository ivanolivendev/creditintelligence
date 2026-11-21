import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HealthCheck {
    public static void main(String[] args) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/actuator/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            System.exit(1);
        }
    }
}
