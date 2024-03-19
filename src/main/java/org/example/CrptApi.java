package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class CrptApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrptApi.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private AtomicInteger counterRequest = new AtomicInteger(0);
    private int limit;
    private long time;
    private volatile LocalDateTime localDateTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        //Переводим TimeUnit в наносекунды
        this.time = timeUnit.toNanos(1);
        this.limit = requestLimit;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public void createDocument(Document document, String signature) throws InterruptedException {
        if (localDateTime == null) {
            localDateTime = LocalDateTime.now();
        }

        //Если указанное время не вышло и лимит запросов не превышен, можем обращаться к API
        if (LocalDateTime.now().isBefore(localDateTime.plusNanos(time)) && counterRequest.get() != limit) {
            counterRequest.incrementAndGet();

            //Логика создания документа
            try {
                String requestBody = objectMapper.writeValueAsString(document);

                String apiUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Content-Type", "application/json")
                        .header("Signature", signature)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    LOGGER.info("Документ создан");
                } else {
                    LOGGER.error("Ошибка при создании документа. HTTP-статус: " + response.statusCode());
                }
            } catch (Exception e) {
                LOGGER.error("Ошибка при отправке запроса: " + e.getMessage());
            }

            //Если лимит запросов превышен, спим
        } else {
            LOGGER.info("Превышен лимит запросов. Ожидайте окончания блокировки");
            while (LocalDateTime.now().isBefore(localDateTime.plusNanos(time))) {
                Thread.sleep(1000);
            }
            localDateTime = LocalDateTime.now();
            counterRequest.set(0);
        }
    }

    @Data
    static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }

    @Data
    static class Description {
        private String participantInn;
    }

    @Data
    static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }


    public static void main(String[] args) throws InterruptedException {
        Document document = new Document();
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 5);
        //Проверка работы метода
        for (int i = 0; i < 30; i++) {
            crptApi.createDocument(document, "signature");
        }
    }
}