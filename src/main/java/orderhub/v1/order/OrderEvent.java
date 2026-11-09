package orderhub.v1.order;

import java.time.OffsetDateTime;

public record OrderEvent(long userId,long orderId,String orderNo,String status,String type,OffsetDateTime occurredAt) {}
