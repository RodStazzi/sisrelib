export const handler = async (event, context) => {
    console.log('========== FULL EVENT ==========');
    console.log(JSON.stringify(event, null, 2));
    console.log('================================');
    
    try {
        // Extraer toda la información relevante
        const token = event.identitySource?.[0] || 
                     event.headers?.authorization || 
                     event.headers?.Authorization;
        
        const httpMethod = event.requestContext?.http?.method;
        const path = event.requestContext?.http?.path;
        const routeKey = event.routeKey;
        
        console.log('📍 Token:', token);
        console.log('📍 HTTP Method:', httpMethod);
        console.log('📍 Path:', path);
        console.log('📍 Route Key:', routeKey);
        
        // Lógica de autorización
        let isAuthorized = false;
        let reason = '';
        
        if (httpMethod === 'OPTIONS') {
            console.log('OPTIONS request - allowing for CORS');
            return {
                isAuthorized: true,
                context: {
                    role: 'cors-preflight'
                }
            };
        }

        // Verificar si es GET (de cualquier forma posible)
        if (httpMethod === 'GET' || 
            routeKey?.startsWith('GET ') || 
            event.requestContext?.http?.method === 'GET') {
            // GET es público - siempre permitir
            isAuthorized = true;
            reason = 'GET is public';
            console.log('✅ GET request detected - allowing public access');
        } else if (token === 'abc123') {
            // POST/PUT/DELETE solo con token admin
            isAuthorized = true;
            reason = 'Valid admin token';
            console.log('✅ Admin token valid - allowing modification');
        } else {
            // No es GET y no tiene token admin
            isAuthorized = false;
            reason = `Non-GET (${httpMethod}) without valid token`;
            console.log(`❌ ${httpMethod} request without admin token - denying`);
        }
        
        // Respuesta para API Gateway v2
        const response = {
            isAuthorized: isAuthorized,
            context: {
                role: token === 'abc123' ? 'admin' : 'public',
                method: httpMethod,
                path: path,
                routeKey: routeKey,
                reason: reason,
                timestamp: new Date().toISOString()
            }
        };
        
        console.log('📤 Auth Response:', JSON.stringify(response, null, 2));
        
        return response;
        
    } catch (error) {
        console.error('❌ Error in authorizer:', error);
        console.error('Stack:', error.stack);
        
        // En caso de error, permitir GET pero denegar el resto
        const method = event.requestContext?.http?.method || event.routeKey?.split(' ')[0];
        const allowOnError = method === 'GET';
        
        return {
            isAuthorized: allowOnError,
            context: {
                error: error.message,
                method: method,
                allowedOnError: allowOnError
            }
        };
    }
};

// Versión que SIEMPRE permite GET, sin importar nada más
export const handler = async (event, context) => {
    console.log('Event:', JSON.stringify(event, null, 2));
    
    // Detectar si es GET de cualquier forma posible
    const isGet = event.requestContext?.http?.method === 'GET' ||
                  event.routeKey?.startsWith('GET ') ||
                  event.httpMethod === 'GET';
    
    // Token para otros métodos
    const token = event.identitySource?.[0];
    
    if (isGet) {
        console.log('✅ GET detected - ALWAYS ALLOW');
        return {
            isAuthorized: true,
            context: {
                reason: 'GET is always public'
            }
        };
    }
    
    // Para otros métodos, verificar token
    const isAuthorized = (token === 'abc123');
    console.log(isAuthorized ? '✅ Admin token valid' : '❌ Invalid token');
    
    return {
        isAuthorized: isAuthorized,
        context: {
            method: event.requestContext?.http?.method,
            tokenProvided: token ? 'yes' : 'no'
        }
    };
};