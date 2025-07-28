/*
 * Copyright 2024-2024 the original author or authors.
 */
package io.modelcontextprotocol.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.spec.JsonSchemaValidator.ValidationResponse;

/**
 * Tests for {@link DefaultJsonSchemaValidator}.
 *
 * @author Christian Tzolov
 */
class DefaultJsonSchemaValidatorTests {

	private DefaultJsonSchemaValidator validator;

	private ObjectMapper objectMapper;

	@Mock
	private ObjectMapper mockObjectMapper;

	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
		validator = new DefaultJsonSchemaValidator();
		objectMapper = new ObjectMapper();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (mocks != null) {
			mocks.close();
		}
	}

	/**
	 * Utility method to convert JSON string to Map<String, Object>
	 */
	private Map<String, Object> toMap(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<>() {
            });
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to parse JSON: " + json, e);
		}
	}

	@Test
	void testDefaultConstructor() {
		DefaultJsonSchemaValidator defaultValidator = new DefaultJsonSchemaValidator();

		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"test": {"type": "string"}
					}
				}
				""";
		String contentJson = """
				{
					"test": "value"
				}
				""";

		ValidationResponse response = defaultValidator.validate(toMap(schemaJson), toMap(contentJson));
		assertTrue(response.isValid());
	}

	@Test
	void testConstructorWithObjectMapper() {
		ObjectMapper customMapper = new ObjectMapper();
		DefaultJsonSchemaValidator customValidator = new DefaultJsonSchemaValidator(customMapper);

		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"test": {"type": "string"}
					}
				}
				""";
		String contentJson = """
				{
					"test": "value"
				}
				""";

		ValidationResponse response = customValidator.validate(toMap(schemaJson), toMap(contentJson));
		assertTrue(response.isValid());
	}

	@Test
	void testValidateWithValidStringSchema() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"name": {"type": "string"},
						"age": {"type": "integer"}
					},
					"required": ["name", "age"]
				}
				""";

		String contentJson = """
				{
					"name": "John Doe",
					"age": 30
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertTrue(response.isValid());
		assertNull(response.getErrorMessage());
		assertNotNull(response.getJsonStructuredOutput());
	}

	@Test
	void testValidateWithValidNumberSchema() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"price": {"type": "number", "minimum": 0},
						"quantity": {"type": "integer", "minimum": 1}
					},
					"required": ["price", "quantity"]
				}
				""";

		String contentJson = """
				{
					"price": 19.99,
					"quantity": 5
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertTrue(response.isValid());
		assertNull(response.getErrorMessage());
	}

	@Test
	void testValidateWithValidArraySchema() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"items": {
							"type": "array",
							"items": {"type": "string"}
						}
					},
					"required": ["items"]
				}
				""";

		String contentJson = """
				{
					"items": ["apple", "banana", "cherry"]
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertTrue(response.isValid());
		assertNull(response.getErrorMessage());
	}

	@Test
	void testValidateWithInvalidTypeSchema() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"name": {"type": "string"},
						"age": {"type": "integer"}
					},
					"required": ["name", "age"]
				}
				""";

		String contentJson = """
				{
					"name": "John Doe",
					"age": "thirty"
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertFalse(response.isValid());
		assertNotNull(response.getErrorMessage());
		assertTrue(response.getErrorMessage().contains("Validation failed"));
		assertTrue(response.getErrorMessage().contains("structuredContent does not match tool outputSchema"));
	}

	@Test
	void testValidateWithMissingRequiredField() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"name": {"type": "string"},
						"age": {"type": "integer"}
					},
					"required": ["name", "age"]
				}
				""";

		String contentJson = """
				{
					"name": "John Doe"
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertFalse(response.isValid());
		assertNotNull(response.getErrorMessage());
		assertTrue(response.getErrorMessage().contains("Validation failed"));
	}

	@Test
	void testValidateWithAdditionalPropertiesNotAllowed() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"name": {"type": "string"}
					},
					"required": ["name"]
				}
				""";

		String contentJson = """
				{
					"name": "John Doe",
					"extraField": "should not be allowed"
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertFalse(response.isValid());
		assertNotNull(response.getErrorMessage());
		assertTrue(response.getErrorMessage().contains("Validation failed"));
	}

	@Test
	void testValidateWithAdditionalPropertiesExplicitlyAllowed() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"name": {"type": "string"}
					},
					"required": ["name"],
					"additionalProperties": true
				}
				""";

		String contentJson = """
				{
					"name": "John Doe",
					"extraField": "should be allowed"
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertTrue(response.isValid());
		assertNull(response.getErrorMessage());
	}

	@Test
	void testValidateWithAdditionalPropertiesExplicitlyDisallowed() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"name": {"type": "string"}
					},
					"required": ["name"],
					"additionalProperties": false
				}
				""";

		String contentJson = """
				{
					"name": "John Doe",
					"extraField": "should not be allowed"
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertFalse(response.isValid());
		assertNotNull(response.getErrorMessage());
		assertTrue(response.getErrorMessage().contains("Validation failed"));
	}

	@Test
	void testValidateWithEmptySchema() {
		String schemaJson = """
				{
					"additionalProperties": true
				}
				""";

		String contentJson = """
				{
					"anything": "goes"
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertTrue(response.isValid());
		assertNull(response.getErrorMessage());
	}

	@Test
	void testValidateWithEmptyContent() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {}
				}
				""";

		String contentJson = """
				{}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertTrue(response.isValid());
		assertNull(response.getErrorMessage());
	}

	@Test
	void testValidateWithNestedObjectSchema() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"person": {
							"type": "object",
							"properties": {
								"name": {"type": "string"},
								"address": {
									"type": "object",
									"properties": {
										"street": {"type": "string"},
										"city": {"type": "string"}
									},
									"required": ["street", "city"]
								}
							},
							"required": ["name", "address"]
						}
					},
					"required": ["person"]
				}
				""";

		String contentJson = """
				{
					"person": {
						"name": "John Doe",
						"address": {
							"street": "123 Main St",
							"city": "Anytown"
						}
					}
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertTrue(response.isValid());
		assertNull(response.getErrorMessage());
	}

	@Test
	void testValidateWithInvalidNestedObjectSchema() {
		String schemaJson = """
				{
					"type": "object",
					"properties": {
						"person": {
							"type": "object",
							"properties": {
								"name": {"type": "string"},
								"address": {
									"type": "object",
									"properties": {
										"street": {"type": "string"},
										"city": {"type": "string"}
									},
									"required": ["street", "city"]
								}
							},
							"required": ["name", "address"]
						}
					},
					"required": ["person"]
				}
				""";

		String contentJson = """
				{
					"person": {
						"name": "John Doe",
						"address": {
							"street": "123 Main St"
						}
					}
				}
				""";

		Map<String, Object> schema = toMap(schemaJson);
		Map<String, Object> structuredContent = toMap(contentJson);

		ValidationResponse response = validator.validate(schema, structuredContent);

		assertFalse(response.isValid());
		assertNotNull(response.getErrorMessage());
		assertTrue(response.getErrorMessage().contains("Validation failed"));
	}

	@Test
	void testValidateWithJsonProcessingException() {
		DefaultJsonSchemaValidator validatorWithMockMapper = new DefaultJsonSchemaValidator(mockObjectMapper);

		Map<String, Object> schema = Map.of("type", "object");
		Map<String, Object> structuredContent = Map.of("key", "value");

		// This will trigger our null check and throw JsonProcessingException
		when(mockObjectMapper.valueToTree(any())).thenReturn(null);

		ValidationResponse response = validatorWithMockMapper.validate(schema, structuredContent);

		assertFalse(response.isValid());
		assertNotNull(response.getErrorMessage());
		assertTrue(response.getErrorMessage().contains("Error parsing tool JSON Schema"));
		assertTrue(response.getErrorMessage().contains("Failed to convert schema to JsonNode"));
	}

	@ParameterizedTest
	@MethodSource("provideValidSchemaAndContentPairs")
	void testValidateWithVariousValidInputs(Map<String, Object> schema, Map<String, Object> content) {
		ValidationResponse response = validator.validate(schema, content);

		assertTrue(response.isValid(), "Expected validation to pass for schema: " + schema + " and content: " + content);
		assertNull(response.getErrorMessage());
	}

	@ParameterizedTest
	@MethodSource("provideInvalidSchemaAndContentPairs")
	void testValidateWithVariousInvalidInputs(Map<String, Object> schema, Map<String, Object> content) {
		ValidationResponse response = validator.validate(schema, content);

		assertFalse(response.isValid(), "Expected validation to fail for schema: " + schema + " and content: " + content);
		assertNotNull(response.getErrorMessage());
		assertTrue(response.getErrorMessage().contains("Validation failed"));
	}

	private static Map<String, Object> staticToMap(String json) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readValue(json, new TypeReference<>() {
            });
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to parse JSON: " + json, e);
		}
	}

	private static Stream<Arguments> provideValidSchemaAndContentPairs() {
		return Stream.of(
				// Boolean schema
				Arguments.of(staticToMap("""
						{
							"type": "object",
							"properties": {
								"flag": {"type": "boolean"}
							}
						}
						"""), staticToMap("""
						{
							"flag": true
						}
						""")),
				// String with additional properties allowed
				Arguments.of(staticToMap("""
						{
							"type": "object",
							"properties": {
								"name": {"type": "string"}
							},
							"additionalProperties": true
						}
						"""), staticToMap("""
						{
							"name": "test",
							"extra": "allowed"
						}
						""")),
				// Array with specific items
				Arguments.of(staticToMap("""
						{
							"type": "object",
							"properties": {
								"numbers": {
									"type": "array",
									"items": {"type": "number"}
								}
							}
						}
						"""), staticToMap("""
						{
							"numbers": [1.0, 2.5, 3.14]
						}
						""")),
				// Enum validation
				Arguments.of(staticToMap("""
						{
							"type": "object",
							"properties": {
								"status": {
									"type": "string",
									"enum": ["active", "inactive", "pending"]
								}
							}
						}
						"""), staticToMap("""
						{
							"status": "active"
						}
						""")));
	}

	private static Stream<Arguments> provideInvalidSchemaAndContentPairs() {
		return Stream.of(
				// Wrong boolean type
				Arguments.of(staticToMap("""
						{
							"type": "object",
							"properties": {
								"flag": {"type": "boolean"}
							}
						}
						"""), staticToMap("""
						{
							"flag": "true"
						}
						""")),
				// Array with wrong item types
				Arguments.of(staticToMap("""
						{
							"type": "object",
							"properties": {
								"numbers": {
									"type": "array",
									"items": {"type": "number"}
								}
							}
						}
						"""), staticToMap("""
						{
							"numbers": ["one", "two", "three"]
						}
						""")),
				// Invalid enum value
				Arguments.of(staticToMap("""
						{
							"type": "object",
							"properties": {
								"status": {
									"type": "string",
									"enum": ["active", "inactive", "pending"]
								}
							}
						}
						"""), staticToMap("""
						{
							"status": "unknown"
						}
						""")),
				// Minimum constraint violation
				Arguments.of(staticToMap("""
						{
							"type": "object",
							"properties": {
								"age": {"type": "integer", "minimum": 0}
							}
						}
						"""), staticToMap("""
						{
							"age": -5
						}
						""")));
	}

	@Test
	void testValidationResponseToValid() {
		String jsonOutput = "{\"test\":\"value\"}";
		ValidationResponse response = ValidationResponse.asValid(jsonOutput);
		assertTrue(response.isValid());
		assertNull(response.getErrorMessage());
		assertEquals(jsonOutput, response.getJsonStructuredOutput());
	}

	@Test
	void testValidationResponseToInvalid() {
		String errorMessage = "Test error message";
		ValidationResponse response = ValidationResponse.asInvalid(errorMessage);
		assertFalse(response.isValid());
		assertEquals(errorMessage, response.getErrorMessage());
		assertNull(response.getJsonStructuredOutput());
	}

	@Test
	void testValidationResponseRecord() {
		ValidationResponse response1 = new ValidationResponse(true, null, "{\"valid\":true}");
		ValidationResponse response2 = new ValidationResponse(false, "Error", null);

		assertTrue(response1.isValid());
		assertNull(response1.getErrorMessage());
		assertEquals("{\"valid\":true}", response1.getJsonStructuredOutput());

		assertFalse(response2.isValid());
		assertEquals("Error", response2.getErrorMessage());
		assertNull(response2.getJsonStructuredOutput());

		// Test equality
		ValidationResponse response3 = new ValidationResponse(true, null, "{\"valid\":true}");
		assertEquals(response1, response3);
		assertNotEquals(response1, response2);
	}

}
