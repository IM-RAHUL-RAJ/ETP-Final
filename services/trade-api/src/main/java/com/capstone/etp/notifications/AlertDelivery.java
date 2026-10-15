package com.capstone.etp.notifications;


public interface AlertDelivery {

    DeliveryReceipt deliver(AlertDeliveryRequest request) throws AlertDeliveryException;
}
