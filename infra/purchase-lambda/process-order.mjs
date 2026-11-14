/**
 * Procesa una orden de un solo ítem. Los adaptadores deben conservar el mismo
 * resultado al repetir la emisión y al registrar el estado final de la orden.
 */
export async function processOrder(order, { reserve, issue, complete, refund }) {
  if (!order.orderId || !order.userId || order.items?.length !== 1) {
    throw new Error('Se esperaba una orden de EventPass con un ítem.');
  }

  const [item] = order.items;
  const reservation = await reserve(item);
  if (reservation.status === 'REJECTED') {
    await refund(order.orderId);
    return;
  }
  if (reservation.status !== 'RESERVED') {
    throw new Error('Estado de reserva inesperado: ' + reservation.status);
  }

  await issue(order);
  await complete(order.orderId);
}
