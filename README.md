# 📚 BookTracker Pro - Sistema de Gestión de Préstamos de Libros

Sistema web de gestión de préstamos de libros desarrollado con arquitectura **serverless en AWS**. Permite a bibliotecarios o administradores registrar, editar y hacer seguimiento de libros prestados, con notificaciones automáticas por correo electrónico para recordatorios de devolución.

---

## 🎥 Demostración en Video

[![Ver Desarrollo en YouTube](https://img.shields.io/badge/YouTube-Ver_Demo-red?style=for-the-badge&logo=youtube)](https://youtu.be/Tx30C0RQuv0)

Mira el video completo de la aplicación en funcionamiento: **[Ver en YouTube](https://youtu.be/Tx30C0RQuv0)**

---

## 🏗️ Arquitectura del Sistema

![Diagrama de Arquitectura](https://img.shields.io/badge/Ver-Diagrama_Completo-blue?style=for-the-badge&logo=diagramsdotnet)

**[Ver diagrama interactivo en Draw.io](https://app.diagrams.net/#G1OdwNEiN-S2Z1fD7fbn4mEewpKCLVsWJ8#%7B%22pageId%22%3A%22AXeewe7i8EiQvWFFpIyp%22%7D)**

### Componentes Principales

```
┌─────────────┐
│   Usuario   │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────┐
│  Frontend (S3 + CloudFront) │
│  JavaScript Vanilla + HTML  │
└──────────┬──────────────────┘
           │
           ▼
    ┌──────────────┐
    │ API Gateway  │
    │ (API Key)    │
    └──────┬───────┘
           │
    ┌──────▼───────────────────────────────┐
    │         Lambda Functions (Java)       │
    ├──────────────────────────────────────┤
    │ • BookLambda (POST)                  │
    │ • GetBookLambda (GET)                │
    │ • GetIdBookLambda (GET /{id})        │
    │ • UpdateBookLambda (PUT /{id})       │
    │ • DeleteIdBookLambda (DELETE /{id})  │
    └──────────┬───────────────────────────┘
               │
               ▼
        ┌─────────────┐
        │  DynamoDB   │
        │   (books)   │
        └─────────────┘

┌──────────────┐
│ EventBridge  │ (Cron: 8:00 AM y 6:00 PM)
└──────┬───────┘
       │
       ▼
┌─────────────────────┐
│ vencidosBookLambda  │
│    (Node.js)        │
└──────┬──────────────┘
       │
       ▼
┌─────────────┐
│  Amazon SNS │
│   (Email)   │
└─────────────┘
```

---

## 🚀 Características Principales

### ✨ Gestión Completa de Libros (CRUD)
- ➕ **Crear**: Registrar nuevos préstamos con información del libro y prestatario
- 📖 **Leer**: Ver todos los libros prestados o consultar por ID específico
- ✏️ **Actualizar**: Modificar información de préstamos existentes
- 🗑️ **Eliminar**: Remover registros de préstamos completados

### 🔔 Sistema de Notificaciones Automatizado
- 🕐 **Verificación automática** dos veces al día (8:00 AM y 6:00 PM)
- 📧 **Alertas por email** para libros:
  - 🚨 Vencidos
  - ⚠️ Vencen hoy
  - 📅 Vencen en los próximos 3 días
- 📊 **Dashboard visual** con estadísticas en tiempo real

### 🎨 Interfaz Moderna
- 💎 Diseño **glassmorphism** con efectos visuales
- ✨ Animaciones fluidas y partículas flotantes
- 📱 **Responsive design** para móviles y tablets
- 🌈 Gradientes modernos y alertas visuales

---

## 🛠️ Tecnologías Utilizadas

### Frontend
- **HTML5 + CSS3**: Interfaz de usuario
- **JavaScript Vanilla**: Lógica del cliente
- **Amazon S3**: Hosting de archivos estáticos
- **CloudFront** (opcional): CDN para distribución global

### Backend
- **AWS API Gateway**: REST API con autorización por API Key
- **AWS Lambda (Java 17)**: Funciones serverless para operaciones CRUD
- **AWS Lambda (Node.js)**: Sistema de notificaciones automatizado
- **Amazon DynamoDB**: Base de datos NoSQL
- **Amazon EventBridge**: Scheduler para tareas programadas (Cron)
- **Amazon SNS**: Servicio de notificaciones por email

### Herramientas de Desarrollo
- **Java 11+**: Lenguaje para Lambdas CRUD
- **Maven**: Gestión de dependencias Java
- **AWS SDK v2**: Interacción con servicios AWS
- **Jackson**: Serialización/deserialización JSON

---

## 🔧 Configuración e Instalación

### Prerrequisitos
- Cuenta de AWS activa
- AWS CLI configurado
- Java 11+ y Maven instalados
- Node.js 18+ (para Lambda de notificaciones)

### Paso 1: Crear tabla DynamoDB

```bash
aws dynamodb create-table \
  --table-name books \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

### Paso 2: Compilar y desplegar Lambdas Java

```bash
cd book-lambda-handler
mvn clean package

# Subir el JAR a cada función Lambda
aws lambda update-function-code \
  --function-name BookLambda \
  --zip-file fileb://target/books-lambda-crud-0.0.1-SNAPSHOT.jar
```

Repetir para cada Lambda: `GetBookLambda`, `GetIdBookLambda`, `UpdateBookLambda`, `DeleteIdBookLambda`

### Paso 3: Configurar Lambda de notificaciones (Node.js)

1. Crear Topic SNS:
```bash
aws sns create-topic --name prestado-book-tema-mail

# Suscribir tu email
aws sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:ACCOUNT_ID:prestado-book-tema-mail \
  --protocol email \
  --notification-endpoint tu-email@ejemplo.com
```

2. Desplegar Lambda `vencidosBookLambda` con el código Node.js proporcionado

### Paso 4: Configurar EventBridge

Crear regla Cron para ejecutar dos veces al día:

```bash
aws events put-rule \
  --name verificar-libros-vencidos \
  --schedule-expression "cron(0 8,18 * * ? *)"
```

### Paso 5: Configurar API Gateway

1. Crear API REST
2. Configurar recursos y métodos:
   - `POST /book` → BookLambda
   - `GET /book` → GetBookLambda
   - `GET /book/{id}` → GetIdBookLambda
   - `PUT /book/{id}` → UpdateBookLambda
   - `DELETE /book/{id}` → DeleteIdBookLambda
3. Configurar API Key y plan de uso
4. Habilitar CORS
5. Desplegar API

### Paso 6: Hospedar Frontend en S3

```bash
aws s3 mb s3://booktracker-frontend
aws s3 website s3://booktracker-frontend --index-document index.html
aws s3 cp index.html s3://booktracker-frontend/ --acl public-read
```

Actualizar la URL del API Gateway en el archivo `index.html`:
```javascript
const API_BASE = 'https://tu-api-id.execute-api.us-east-1.amazonaws.com/prod';
```

---

## 📡 Endpoints de la API

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/book` | Crear nuevo préstamo | API Key |
| `GET` | `/book` | Listar todos los libros | Público |
| `GET` | `/book/{id}` | Obtener libro específico | Público |
| `PUT` | `/book/{id}` | Actualizar préstamo | API Key |
| `DELETE` | `/book/{id}` | Eliminar préstamo | API Key |

### Ejemplo de Request (POST /book)

```json
{
  "title": "El Principito",
  "author": "Antoine de Saint-Exupéry",
  "prestado_a": "Juan Pérez",
  "email": "juan.perez@ejemplo.com",
  "telefono": "+56912345678",
  "prestado_fecha": "2025-10-01",
  "retorno_fecha": "2025-11-01"
}
```

### Headers Requeridos

```
Authorization: abc123
Content-Type: application/json
```

---

## 🔔 Sistema de Notificaciones

### Funcionamiento

El Lambda `vencidosBookLambda` se ejecuta automáticamente mediante EventBridge a las **8:00 AM** y **6:00 PM** (hora de Chile) todos los días.

### Proceso de Notificación

1. 🔍 **Escaneo**: Revisa todos los libros en DynamoDB
2. 📅 **Evaluación**: Calcula días restantes hasta la fecha de retorno
3. 🚨 **Clasificación**:
   - **VENCIDOS**: Más de 0 días de retraso
   - **VENCEN HOY**: 0 días restantes
   - **PRÓXIMOS A VENCER**: 1-3 días restantes
4. 📧 **Envío**: Publica mensaje en SNS Topic
5. ✅ **Entrega**: SNS envía email a suscriptores

### Ejemplo de Email de Notificación

```
🚨 ALERTA DE LIBROS PRESTADOS
================================

🔴 LIBROS VENCIDOS:
• "Clean Code" - Prestado a: María González
  Venció hace 5 días (2025-10-26)

⚠️ VENCEN HOY:
• "Design Patterns" - Prestado a: Pedro Sánchez
  Debe devolverse HOY (2025-10-31)

📅 PRÓXIMOS A VENCER:
• "The Pragmatic Programmer" - Prestado a: Ana López
  Vence en 2 días (2025-11-02)

================================
Total de libros en alerta: 3
Hora de verificación: 31/10/2025 08:00:00
```

---

## 📊 Modelo de Datos (DynamoDB)

### Tabla: `books`

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | String (PK) | UUID único del préstamo |
| `title` | String | Título del libro |
| `author` | String | Autor del libro |
| `prestado_a` | String | Nombre del prestatario |
| `email` | String | Email del prestatario |
| `telefono` | String | Teléfono del prestatario |
| `prestado_fecha` | String (Date) | Fecha de préstamo (YYYY-MM-DD) |
| `retorno_fecha` | String (Date) | Fecha de devolución (YYYY-MM-DD) |

---

## 🎯 Casos de Uso

### Para Bibliotecarios
- 📝 Registrar préstamos en segundos
- 👁️ Monitorear todos los préstamos activos
- ⏰ Recibir alertas automáticas de vencimientos
- 📈 Ver estadísticas en tiempo real

### Para Bibliotecas Pequeñas/Medianas
- 💰 **Sin costos de infraestructura**: Paga solo por uso
- 🚀 **Escalabilidad automática**: Maneja picos de demanda
- 🔒 **Seguridad integrada**: API Key + IAM Roles
- 📱 **Acceso desde cualquier dispositivo**

---

## 🔐 Seguridad

- ✅ **API Key**: Protección de endpoints de escritura (POST, PUT, DELETE)
- ✅ **Endpoints GET públicos**: Acceso de lectura sin autenticación
- ✅ **IAM Roles**: Permisos mínimos necesarios para cada Lambda
- ✅ **HTTPS**: Comunicación cifrada mediante API Gateway
- ✅ **CORS**: Configuración restrictiva de orígenes permitidos

---

## 💰 Costos Estimados

Para una biblioteca pequeña (100 préstamos/mes):

| Servicio | Costo Mensual Estimado |
|----------|------------------------|
| DynamoDB | < $1 (Free Tier) |
| Lambda | < $1 (Free Tier) |
| API Gateway | < $1 |
| SNS | < $1 |
| S3 | < $1 |
| **TOTAL** | **< $5/mes** |

*Costos en región us-east-1. Pueden variar según uso real.*

---

## 🚀 Posibles Integraciones

- [ ] Integración con WhatsApp Business API
- [ ] Dashboard administrativo avanzado
- [ ] Reportes mensuales en PDF
- [ ] Sistema de multas por retraso
- [ ] Historial de préstamos por usuario
- [ ] Soporte multiidioma
- [ ] Búsqueda avanzada de libros
- [ ] Integración con código de barras


---

## 📝 Licencia

Este proyecto está bajo licencia MIT. Ver archivo `LICENSE` para más detalles.

---

## 📧 Contacto

**Desarrollador**: [Rodolfo Stazzi S]  
**LinkedIn**: [https://www.linkedin.com/in/rodolfostazzi/]

---

<div align="center">

**¿Te gustó este proyecto? ¡Dale una ⭐ en GitHub!**

[![GitHub stars](https://img.shields.io/github/stars/tu-usuario/booktracker-pro?style=social)](https://github.com/RodStazzi/sisrelib)

</div>
