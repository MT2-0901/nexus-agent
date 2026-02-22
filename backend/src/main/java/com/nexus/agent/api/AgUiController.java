package com.nexus.agent.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.agent.api.dto.agui.AgUiRunRequest;
import com.nexus.agent.service.AgUiProtocolService;
import com.nexus.agent.service.AgUiRunResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agui")
public class AgUiController {

    private final AgUiProtocolService agUiProtocolService;
    private final ObjectMapper objectMapper;

    public AgUiController(AgUiProtocolService agUiProtocolService, ObjectMapper objectMapper) {
        this.agUiProtocolService = agUiProtocolService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody run(@RequestBody AgUiRunRequest request) {
        return outputStream -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                String threadId = hasText(request.threadId()) ? request.threadId().trim() : "thread-" + UUID.randomUUID();
                String runId = hasText(request.runId()) ? request.runId().trim() : "run-" + UUID.randomUUID();
                String messageId = "msg-" + UUID.randomUUID();

                sendEvent(writer, Map.of(
                        "type", "RUN_STARTED",
                        "threadId", threadId,
                        "runId", runId,
                        "timestamp", Instant.now().toEpochMilli()
                ));
                sendEvent(writer, Map.of(
                        "type", "TEXT_MESSAGE_START",
                        "threadId", threadId,
                        "runId", runId,
                        "messageId", messageId,
                        "role", "assistant",
                        "timestamp", Instant.now().toEpochMilli()
                ));

                AgUiRunResult result;
                try {
                    result = agUiProtocolService.run(request, threadId, delta -> {
                        try {
                            sendEvent(writer, Map.of(
                                    "type", "TEXT_MESSAGE_CONTENT",
                                    "threadId", threadId,
                                    "runId", runId,
                                    "messageId", messageId,
                                    "delta", delta,
                                    "timestamp", Instant.now().toEpochMilli()
                            ));
                        } catch (IOException ex) {
                            throw new UncheckedIOException(ex);
                        }
                    });
                } catch (UncheckedIOException ex) {
                    throw ex.getCause();
                } catch (Exception ex) {
                    sendEvent(writer, Map.of(
                            "type", "RUN_ERROR",
                            "threadId", threadId,
                            "runId", runId,
                            "message", ex.getMessage() == null ? "run failed" : ex.getMessage(),
                            "timestamp", Instant.now().toEpochMilli()
                    ));
                    return;
                }

                sendEvent(writer, Map.of(
                        "type", "TEXT_MESSAGE_END",
                        "threadId", threadId,
                        "runId", runId,
                        "messageId", messageId,
                        "timestamp", Instant.now().toEpochMilli()
                ));

                Map<String, Object> resultPayload = new LinkedHashMap<>();
                resultPayload.put("sessionId", result.sessionId());
                resultPayload.put("mode", result.mode());
                resultPayload.put("response", result.response());
                resultPayload.put("activatedSkills", result.activatedSkills());
                resultPayload.put("eventCount", result.eventCount());
                resultPayload.put("timestamp", result.timestamp());

                sendEvent(writer, Map.of(
                        "type", "RUN_FINISHED",
                        "threadId", threadId,
                        "runId", runId,
                        "result", resultPayload,
                        "timestamp", Instant.now().toEpochMilli()
                ));
            }
        };
    }

    private void sendEvent(BufferedWriter writer, Map<String, Object> event) throws IOException {
        writer.write("data: ");
        writer.write(toJson(event));
        writer.write("\n\n");
        writer.flush();
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize AG-UI event", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
