package agent.tool.mcp;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapperSupplier;

public class JacksonMcpJsonMapperSupplier implements McpJsonMapperSupplier {

    private static final JsonMapper SHARED_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Override
    public McpJsonMapper get() {
        return new JacksonMcpJsonMapper(SHARED_MAPPER);
    }
}