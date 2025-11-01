package cl.rosta;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BookLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    
    public BookLambdaHandler() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
        	// Analizar el libro recibido del cuerpo de la solicitud
            Map<String, Object> book = objectMapper.readValue(event.getBody(), Map.class);
            
            // Agregar UUID al libro (el ID)
            book.put("id", UUID.randomUUID().toString());
            
            // Convertir al formato AttributeValue de DynamoDB
            Map<String, AttributeValue> item = convertToAttributeValueMap(book);
            
            // Insertar el elemento en DynamoDB
            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName("books")
                    .item(item)
                    .build();
            
            dynamoDbClient.putItem(putItemRequest);
            
            // Devolver respuesta de exito
            APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
            response.setStatusCode(201);
            response.setBody(objectMapper.writeValueAsString(book));
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
    
    private Map<String, AttributeValue> convertToAttributeValueMap(Map<String, Object> map) {
        Map<String, AttributeValue> result = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof String) {
                result.put(key, AttributeValue.builder().s((String) value).build());
            } else if (value instanceof Number) {
                result.put(key, AttributeValue.builder().n(value.toString()).build());
            } else if (value instanceof Boolean) {
                result.put(key, AttributeValue.builder().bool((Boolean) value).build());
            } else if (value != null) {
            	// Para objetos complejos, convertir a string
                try {
                    result.put(key, AttributeValue.builder().s(objectMapper.writeValueAsString(value)).build());
                } catch (Exception e) {
                    result.put(key, AttributeValue.builder().s(value.toString()).build());
                }
            }
        }
        
        return result;
    }
}