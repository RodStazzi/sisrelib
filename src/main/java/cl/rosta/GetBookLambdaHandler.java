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

public class GetBookLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    
    public GetBookLambdaHandler() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
        	// Obtener todos los libros
            List<Map<String, Object>> books = getAllBooks();
            
            // Devolver respuesta de éxito
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(200);
            response.setBody(objectMapper.writeValueAsString(books));
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
    
    private List<Map<String, Object>> getAllBooks() {
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName("books")
                    .build();
            
            ScanResponse response = dynamoDbClient.scan(scanRequest);
            
            List<Map<String, Object>> books = new ArrayList<>();
            for (Map<String, AttributeValue> item : response.items()) {
                books.add(convertFromAttributeValueMap(item));
            }
            
            return books;
            
        } catch (Exception e) {
            throw new RuntimeException("Error getting all books: " + e.getMessage(), e);
        }
    }
    
    private Map<String, Object> convertFromAttributeValueMap(Map<String, AttributeValue> attributeMap) {
        Map<String, Object> result = new HashMap<>();
        
        for (Map.Entry<String, AttributeValue> entry : attributeMap.entrySet()) {
            String key = entry.getKey();
            AttributeValue value = entry.getValue();
            
            if (value.s() != null) {
            	// Intentar analizar como JSON primero; si falla, tratar como string
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
            	// Intentar analizar como entero primero y, si no, como numero double
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
}