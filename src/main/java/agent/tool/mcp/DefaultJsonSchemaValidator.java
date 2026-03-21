package agent.tool.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultJsonSchemaValidator implements JsonSchemaValidator {

    private static final Logger logger = LoggerFactory.getLogger(DefaultJsonSchemaValidator.class);

    private static final JsonMapper SHARED_MAPPER = JsonMapper.builder().build();

    private final JsonMapper jsonMapper;
    private final SchemaRegistry schemaFactory;
    private final ConcurrentHashMap<String, Schema> schemaCache;

    public DefaultJsonSchemaValidator() {
        this(SHARED_MAPPER);
    }

    public DefaultJsonSchemaValidator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.schemaFactory = SchemaRegistry.withDialect(Dialects.getDraft202012());
        this.schemaCache = new ConcurrentHashMap<>();
    }

    @Override
    public ValidationResponse validate(Map<String, Object> schema, Object structuredContent) {
        if (schema == null) {
            throw new IllegalArgumentException("Schema must not be null");
        }
        if (structuredContent == null) {
            throw new IllegalArgumentException("Structured content must not be null");
        }

        try {
            JsonNode jsonStructuredOutput = structuredContent instanceof String
                    ? this.jsonMapper.readTree((String) structuredContent)
                    : this.jsonMapper.valueToTree(structuredContent);

            List<Error> validationResult = this.getOrCreateJsonSchema(schema).validate(jsonStructuredOutput);

            return !validationResult.isEmpty()
                    ? ValidationResponse.asInvalid(
                            "Validation failed: structuredContent does not match tool outputSchema. Validation errors: "
                                    + String.valueOf(validationResult))
                    : ValidationResponse.asValid(jsonStructuredOutput.toString());
        } catch (JsonProcessingException e) {
            logger.error("Failed to validate CallToolResult: Error parsing schema", e);
            return ValidationResponse.asInvalid("Error parsing tool JSON Schema: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to validate CallToolResult: Unexpected error", e);
            return ValidationResponse.asInvalid("Unexpected validation error: " + e.getMessage());
        }
    }

    private Schema getOrCreateJsonSchema(Map<String, Object> schema) throws JsonProcessingException {
        String cacheKey = this.generateCacheKey(schema);
        Schema cachedSchema = this.schemaCache.get(cacheKey);
        if (cachedSchema != null) {
            return cachedSchema;
        }

        Schema newSchema = this.createJsonSchema(schema);
        Schema existingSchema = this.schemaCache.putIfAbsent(cacheKey, newSchema);
        return existingSchema != null ? existingSchema : newSchema;
    }

    private Schema createJsonSchema(Map<String, Object> schema) throws JsonProcessingException {
        JsonNode schemaNode = this.jsonMapper.valueToTree(schema);
        if (schemaNode == null) {
            throw new JsonProcessingException("Failed to convert schema to JsonNode") {};
        }
        return this.schemaFactory.getSchema(schemaNode);
    }

    protected String generateCacheKey(Map<String, Object> schema) {
        return schema.containsKey("$id")
                ? String.valueOf(schema.get("$id"))
                : String.valueOf(schema.hashCode());
    }

    public void clearCache() {
        this.schemaCache.clear();
    }

    public int getCacheSize() {
        return this.schemaCache.size();
    }
}