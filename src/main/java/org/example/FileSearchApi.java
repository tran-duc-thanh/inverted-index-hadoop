package org.example;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bson.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** REST API dùng inverted index trong MongoDB để tìm tên file theo từ khóa. */
public class FileSearchApi {

    private static final String DEFAULT_MONGO_URI = "mongodb://localhost:27017";

    public static void main(String[] args) throws IOException {
        int port = args.length >= 1 ? Integer.parseInt(args[0]) : 8080;
        String mongoUri = args.length >= 2 ? args[1] : System.getenv("MONGO_URI");
        if (mongoUri == null || mongoUri.trim().isEmpty()) {
            mongoUri = DEFAULT_MONGO_URI;
        }

        MongoClient mongoClient = MongoClients.create(mongoUri);
        MongoDatabase database = mongoClient.getDatabase("inverted_index_db");
        MongoCollection<Document> collection = database.getCollection("index_results");

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/search", new SearchHandler(collection));
        server.createContext("/", new SearchPageHandler());
        ExecutorService executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            executor.shutdownNow();
            mongoClient.close();
        }));

        System.out.println("Giao diện tìm kiếm: http://localhost:" + port + "/");
        System.out.println("Search API: http://localhost:" + port + "/search?word=hadoop");
    }

    private static final class SearchPageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"/".equals(exchange.getRequestURI().getPath())) {
                sendJson(exchange, 404, "{\"error\":\"Không tìm thấy trang\"}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendJson(exchange, 405, "{\"error\":\"Chỉ hỗ trợ GET\"}");
                return;
            }

            try (InputStream page = FileSearchApi.class.getResourceAsStream("/static/index.html")) {
                if (page == null) {
                    sendJson(exchange, 500, "{\"error\":\"Không tìm thấy giao diện tìm kiếm\"}");
                    return;
                }
                ByteArrayOutputStream content = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = page.read(buffer)) != -1) {
                    content.write(buffer, 0, bytesRead);
                }
                sendHtml(exchange, 200, content.toByteArray());
            }
        }
    }

    private static final class SearchHandler implements HttpHandler {
        private final MongoCollection<Document> collection;

        private SearchHandler(MongoCollection<Document> collection) {
            this.collection = collection;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"/search".equals(exchange.getRequestURI().getPath())) {
                sendJson(exchange, 404, "{\"error\":\"Không tìm thấy API\"}");
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendJson(exchange, 405, "{\"error\":\"Chỉ hỗ trợ GET\"}");
                return;
            }

            final String word;
            try {
                word = getSearchWord(exchange.getRequestURI().getRawQuery());
            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                return;
            }

            List<String> files;
            try {
                Document indexedWord = collection.find(Filters.eq("word", word)).first();
                files = indexedWord == null
                        ? new ArrayList<>()
                        : indexedWord.getList("files", String.class, new ArrayList<>());
            } catch (Exception e) {
                System.err.println("Không thể tìm từ trong MongoDB: " + e.getMessage());
                sendJson(exchange, 500, "{\"error\":\"Lỗi khi truy vấn chỉ mục\"}");
                return;
            }

            StringBuilder response = new StringBuilder();
            response.append("{\"word\":\"").append(escapeJson(word)).append("\",\"count\":")
                    .append(files.size()).append(",\"files\":[");
            for (int i = 0; i < files.size(); i++) {
                if (i > 0) {
                    response.append(',');
                }
                response.append('\"').append(escapeJson(files.get(i))).append('\"');
            }
            response.append("]}");
            sendJson(exchange, 200, response.toString());
        }
    }

    private static String getSearchWord(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            throw new IllegalArgumentException("Thiếu tham số word");
        }

        String word = null;
        try {
            for (String pair : rawQuery.split("&")) {
                int separator = pair.indexOf('=');
                String rawName = separator >= 0 ? pair.substring(0, separator) : pair;
                String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8.name());
                if ("word".equals(name)) {
                    if (word != null) {
                        throw new IllegalArgumentException("Chỉ truyền một tham số word");
                    }
                    String rawValue = separator >= 0 ? pair.substring(separator + 1) : "";
                    word = URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name());
                }
            }
        } catch (IOException e) {
            // UTF-8 luôn có sẵn trong Java; giữ nhánh này để thỏa mãn chữ ký URLDecoder.
            throw new IllegalArgumentException("Tham số word không hợp lệ");
        }

        if (word == null) {
            throw new IllegalArgumentException("Thiếu tham số word");
        }

        String normalized = word.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ").trim();
        if (normalized.isEmpty() || normalized.split("\\s+").length != 1) {
            throw new IllegalArgumentException("word phải chứa đúng một từ");
        }
        return normalized;
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try {
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static void sendHtml(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try {
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
            }
        }
        return escaped.toString();
    }
}
