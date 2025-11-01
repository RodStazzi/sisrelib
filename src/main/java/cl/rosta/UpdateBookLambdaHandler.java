package cl.rosta;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class UpdateBookLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    
    public UpdateBookLambdaHandler() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            // Obtener el ID del libro de los parámetros de la ruta
            Map<String, String> pathParameters = event.getPathParameters();
            String bookId = null;
            
            if (pathParameters != null) {
                bookId = pathParameters.get("id");
            }
            
            if (bookId == null || bookId.isEmpty()) {
                return createBadRequestResponse("Book ID is required");
            }
            
            // Analizar los datos del libro recibidos del cuerpo de la solicitud
            Map<String, Object> bookUpdates = objectMapper.readValue(event.getBody(), Map.class);
            
            //  Eliminar el ID de las actualizaciones si está presente (no se puede actualizar la clave)
            bookUpdates.remove("id");
            
            if (bookUpdates.isEmpty()) {
                return createBadRequestResponse("No fields to update");
            }
            
            // Verificar primero si el libro existe
            if (!bookExists(bookId)) {
                return createNotFoundResponse();
            }
            
            // Actualizar el libro
            Map<String, Object> updatedBook = updateBook(bookId, bookUpdates);
            
            // Devolver respuesta de éxito
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(200);
            response.setBody(objectMapper.writeValueAsString(updatedBook));
            return response;
                    
        } catch (Exception error) {
            context.getLogger().log("Error: " + error.getMessage());
            
            // Crear mapa de respuestas de error
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("message", error.getMessage());
            
            // Devolver respuesta de error
            try {
                APIGatewayProxyResponseEvent errorResponse = new APIGatewayProxyResponseEvent();
                errorResponse.setStatusCode(500);
                errorResponse.setBody(objectMapper.writeValueAsString(errorMap));
                return errorResponse;
            } catch (Exception e) {
                APIGatewayProxyResponseEvent fallbackResponse = new APIGatewayProxyResponseEvent();
                fallbackResponse.setStatusCode(500);
                fallbackResponse.setBody("{\"message\": \"Internal server error\"}");
                return fallbackResponse;
            }
        }
    }
    
    private boolean bookExists(String bookId) {
        try {
            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName("books")
                    .key(Map.of("id", AttributeValue.builder().s(bookId).build()))
                    .build();
            
            GetItemResponse response = dynamoDbClient.getItem(getItemRequest);
            return !response.item().isEmpty();
            
        } catch (Exception e) {
            throw new RuntimeException("Error checking if book exists: " + e.getMessage(), e);
        }
    }
    
    private Map<String, Object> updateBook(String bookId, Map<String, Object> updates) {
        try {
        	// Construir la expresión de actualizacion
            StringBuilder updateExpression = new StringBuilder("SET ");
            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            Map<String, String> expressionAttributeNames = new HashMap<>();
            
            List<String> updateClauses = new ArrayList<>();
            int index = 0;
            
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                String attributeName = "#attr" + index;
                String attributeValue = ":val" + index;
                
                expressionAttributeNames.put(attributeName, key);
                expressionAttributeValues.put(attributeValue, convertToAttributeValue(value));
                
                updateClauses.add(attributeName + " = " + attributeValue);
                index++;
            }
            
            updateExpression.append(String.join(", ", updateClauses));
            
            // Ejecutar la actualizacion
            UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                    .tableName("books")
                    .key(Map.of("id", AttributeValue.builder().s(bookId).build()))
                    .updateExpression(updateExpression.toString())
                    .expressionAttributeNames(expressionAttributeNames)
                    .expressionAttributeValues(expressionAttributeValues)
                    .returnValues(ReturnValue.ALL_NEW)
                    .build();
            
            UpdateItemResponse response = dynamoDbClient.updateItem(updateItemRequest);
            
            return convertFromAttributeValueMap(response.attributes());
            
        } catch (Exception e) {
            throw new RuntimeException("Error updating book: " + e.getMessage(), e);
        }
    }
    
    private AttributeValue convertToAttributeValue(Object value) {
        if (value instanceof String) {
            return AttributeValue.builder().s((String) value).build();
        } else if (value instanceof Number) {
            return AttributeValue.builder().n(value.toString()).build();
        } else if (value instanceof Boolean) {
            return AttributeValue.builder().bool((Boolean) value).build();
        } else if (value != null) {
        	// Para objetos complejos, convertir a string
            try {
                return AttributeValue.builder().s(objectMapper.writeValueAsString(value)).build();
            } catch (Exception e) {
                return AttributeValue.builder().s(value.toString()).build();
            }
        } else {
            return AttributeValue.builder().nul(true).build();
        }
    }
    
    private Map<String, Object> convertFromAttributeValueMap(Map<String, AttributeValue> attributeMap) {
        Map<String, Object> result = new HashMap<>();
        
        for (Map.Entry<String, AttributeValue> entry : attributeMap.entrySet()) {
            String key = entry.getKey();
            AttributeValue value = entry.getValue();
            
            if (value.s() != null) {
            	// Intentar analizar primero como JSON; si falla, tratar como string
                String stringValue = value.s();
                try {
                	// Verificar si tiene formato JSON
                    if ((stringValue.startsWith("{") && stringValue.endsWith("}")) ||
                        (stringValue.startsWith("[") && stringValue.endsWith("]"))) {
                        result.put(key, objectMapper.readValue(stringValue, Object.class));
                    } else {
                        result.put(key, stringValue);
                    }
                } catch (Exception e) {
                    result.put(key, stringValue);
                }
            } else if (value.n() != null) {
            	// Intentar analizar primero como entero y, si es necesario, como número double
                try {
                    result.put(key, Integer.parseInt(value.n()));
                } catch (NumberFormatException e) {
                    try {
                        result.put(key, Double.parseDouble(value.n()));
                    } catch (NumberFormatException ex) {
                        result.put(key, value.n());
                    }
                }
            } else if (value.bool() != null) {
                result.put(key, value.bool());
            } else {
                result.put(key, value.toString());
            }
        }
        
        return result;
    }
    
    private APIGatewayProxyResponseEvent createNotFoundResponse() {
        try {
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("message", "Book not found");
            
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(404);
            response.setBody(objectMapper.writeValueAsString(errorMap));
            return response;
        } catch (Exception e) {
            APIGatewayProxyResponseEvent fallbackResponse = new APIGatewayProxyResponseEvent();
            fallbackResponse.setStatusCode(404);
            fallbackResponse.setBody("{\"message\": \"Book not found\"}");
            return fallbackResponse;
        }
    }
    
    private APIGatewayProxyResponseEvent createBadRequestResponse(String message) {
        try {
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("message", message);
            
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(400);
            response.setBody(objectMapper.writeValueAsString(errorMap));
            return response;
        } catch (Exception e) {
            APIGatewayProxyResponseEvent fallbackResponse = new APIGatewayProxyResponseEvent();
            fallbackResponse.setStatusCode(400);
            fallbackResponse.setBody("{\"message\": \"Bad request\"}");
            return fallbackResponse;
        }
    }
}