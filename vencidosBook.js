import { DynamoDBClient, ScanCommand } from '@aws-sdk/client-dynamodb';
import { SNSClient, PublishCommand } from '@aws-sdk/client-sns';
const dynamoClient = new DynamoDBClient({ region: 'us-east-1' });
const snsClient = new SNSClient({ region: 'us-east-1' });
// ARN de tu topic SNS
const SNS_TOPIC_ARN =
'arn:aws:sns:us-east-1:727774447800:prestado-book-tema-mail';
export const handler = async (event, context) => {
console.log(' Verificando libros prestados...');
try {
// 1. Obtener todos los libros de la tabla
const params = {
TableName: 'books',
FilterExpression: 'attribute_exists(prestado_a) AND attribute_exists(retorno_fecha)'
};
const response = await dynamoClient.send(new ScanCommand(params));
if (!response.Items || response.Items.length === 0) {
console.log('No hay libros prestados');
return {
statusCode: 200,
body: JSON.stringify({ message: 'No hay libros prestados' })
};
}
// 2. Verificar cuáles están por vencer
const hoy = new Date();
hoy.setHours(0, 0, 0, 0);
const notificaciones = [];

for (const item of response.Items) {
const libro = {
id: item.id?.S,
title: item.title?.S,
prestado_a: item.prestado_a?.S,
retorno_fecha: item.retorno_fecha?.S
};
if (!libro.retorno_fecha) continue;
// Calcular días restantes
const fechaRetorno = new Date(libro.retorno_fecha + 'T00:00:00');
const diasRestantes = Math.floor((fechaRetorno - hoy) / (1000 * 60 *
60 * 24));
// Notificar si:
// - Está vencido (días < 0)
// - Vence hoy (días = 0)
// - Vence mañana (días = 1)
// - Vence en 3 días o menos
if (diasRestantes <= 3) {
notificaciones.push({
...libro,
diasRestantes
});
}
}
// 3. Si hay libros por vencer, enviar notificación
if (notificaciones.length > 0) {
await enviarNotificacionSNS(notificaciones);
return {
statusCode: 200,
body: JSON.stringify({
message: 'Notificaciones enviadas',
librosNotificados: notificaciones.length,
detalles: notificaciones
})
};
} else {
console.log('No hay libros próximos a vencer');
return {
statusCode: 200,
body: JSON.stringify({
message: 'No hay libros próximos a vencer'
})
};
}
} catch (error) {
console.error(' Error:', error);
return {
statusCode: 500,
body: JSON.stringify({
message: 'Error al procesar notificaciones',
error: error.message
})
};
}
};
// Función para enviar notificación a SNS
async function enviarNotificacionSNS(libros) {
// Construir el mensaje
let mensaje = ' ALERTA DE LIBROS PRESTADOS\n';
mensaje += '================================\n\n';
// Separar por urgencia
const vencidos = libros.filter(l => l.diasRestantes < 0);
const venceHoy = libros.filter(l => l.diasRestantes === 0);
const proximosVencer = libros.filter(l => l.diasRestantes > 0);
if (vencidos.length > 0) {
mensaje += ' LIBROS VENCIDOS:\n';
for (const libro of vencidos) {
mensaje += `• "${libro.title}" - Prestado a: ${libro.prestado_a}\n`;
mensaje += ` Venció hace ${Math.abs(libro.diasRestantes)} días
(${libro.retorno_fecha})\n\n`;
}
}
if (venceHoy.length > 0) {
mensaje += ' VENCEN HOY:\n';
for (const libro of venceHoy) {
mensaje += `• "${libro.title}" - Prestado a: ${libro.prestado_a}\n`;
mensaje += ` Debe devolverse HOY (${libro.retorno_fecha})\n\n`;
}
}
if (proximosVencer.length > 0) {
mensaje += ' PRÓXIMOS A VENCER:\n';
for (const libro of proximosVencer) {
mensaje += `• "${libro.title}" - Prestado a: ${libro.prestado_a}\n`;
mensaje += ` Vence en ${libro.diasRestantes}
día${libro.diasRestantes > 1 ? 's' : ''} (${libro.retorno_fecha})\n\n`;
}
}
mensaje += '================================\n';
mensaje += `Total de libros en alerta: ${libros.length}\n`;
mensaje += `Hora de verificación: ${new Date().toLocaleString('es-CL', {
timeZone: 'America/Santiago' })}`;
// Enviar a SNS
const params = {
TopicArn: SNS_TOPIC_ARN,
Subject: ` Alerta: ${libros.length} libro${libros.length > 1 ? 's' :
''} por vencer`,
Message: mensaje
};
console.log('Enviando notificación a SNS...');
const command = new PublishCommand(params);
const result = await snsClient.send(command);
console.log(' Notificación enviada:', result.MessageId);
return result;
}