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

public class GetIdBookLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    
    public GetIdBookLambdaHandler() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
        	// Obtener el ID del libro a partir de los parámetros de la ruta
            Map<String, String> pathParameters = event.getPathParameters();
            String bookId = null;
            
            if (pathParameters != null) {
                bookId = pathParameters.get("id");
            }
            
            if (bookId == null || bookId.isEmpty()) {
                return createBadRequestResponse("Book ID is required");
            }
            
            // Obtener un libro específico por su ID
            Map<String, Object> book = getBookById(bookId);
            
            if (book == null) {
                return createNotFoundResponse();
            }
            
            // Devolver respuesta de exito
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(200);
            response.setBody(objectMapper.writeValueAsString(book));
            return response;
                    
        } catch (Exception error) {
            context.getLogger().log("Error: " + error.getMessage());
            
            // Crear un mapa de respuestas de error
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
    
    private Map<String, Object> getBookById(String bookId) {
        try {
            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName("books")
                    .key(Map.of("id", AttributeValue.builder().s(bookId).build()))
                    .build();
            
            GetItemResponse response = dynamoDbClient.getItem(getItemRequest);
            
            if (response.item().isEmpty()) {
                return null; // Libro no encontrado
            }
            
            return convertFromAttributeValueMap(response.item());
            
        } catch (Exception e) {
            throw new RuntimeException("Error getting book by ID: " + e.getMessage(), e);
        }
    }
    
    private Map<String, Object> convertFromAttributeValueMap(Map<String, AttributeValue> attributeMap) {
        Map<String, Object> result = new HashMap<>();
        
        for (Map.Entry<String, AttributeValue> entry : attributeMap.entrySet()) {
            String key = entry.getKey();
            AttributeValue value = entry.getValue();
            
            if (value.s() != null) {
            	// Intentar analizarlo como JSON primero; si falla, tratarlo como string
                String stringValue = value.s();
                try {
                	// Comprobar si tiene formato JSON
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
            	// Intentar analizarlo como entero primero y, si no, como double
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
