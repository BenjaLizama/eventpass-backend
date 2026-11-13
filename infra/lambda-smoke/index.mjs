export async function handler(event) {
    for (const record of event.Records) {
        const { orderId } = JSON.parse(record.body);
        const receiveCount = Number(record.attributes.ApproximateReceiveCount);
        if (receiveCount === 1) {
            throw new Error("Fallo de prueba en la primera entrega");
        }

        const objectUrl = process.env.FLOCI_SMOKE_ENDPOINT + "/eventpass-lambda-smoke/" + orderId + ".json";
        const response = await fetch(objectUrl, {
            method: "PUT",
            body: JSON.stringify({ orderId, messageId: record.messageId, receiveCount }),
        });
        if (!response.ok) {
            throw new Error("No se pudo registrar la entrega. HTTP " + response.status);
        }
    }
}
