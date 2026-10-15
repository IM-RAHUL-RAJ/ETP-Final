package com.capstone.etp.service;

import com.capstone.etp.dto.OrderResponse;
import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.entity.Account;
import com.capstone.etp.entity.Instrument;
import com.capstone.etp.entity.Order;
import com.capstone.etp.entity.PortfolioHolding;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.exception.IllegalOrderStateException;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.exception.OptimisticLockException;
import com.capstone.etp.kafka.producer.OrderEventProducer;
import com.capstone.etp.mapper.OrderMapper;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.util.CurrencyConverter;
import com.capstone.etp.validator.OrderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderValidator orderValidator;
    private final AccountAuthorizer accountAuthorizer;
    private final OrderEventProducer orderEventProducer;

    // create a new order
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        accountAuthorizer.ensureOrderManagementAllowed();
        // Perform database operations in a transaction
        PlaceOrderInfo info = persistOrder(request);

        // Publish event AFTER transaction commits
        orderEventProducer.publishOrderPlaced(
                info.order,
                info.instrument.getSymbol(),
                info.convertedLimitPriceInInstrumentCurrency);

        return OrderResponse.builder()
                .orderId(info.order.getId())
                .status(info.order.getStatus())
                .message("Order created successfully")
                .symbol(info.instrument.getSymbol())
                .side(info.order.getSide())
                .quantity(info.order.getQuantity())
                .price(info.order.getLimitPrice())
                .build();
    }

    @Transactional
    private PlaceOrderInfo persistOrder(PlaceOrderRequest request) {
        accountAuthorizer.ensureAccountAccess(request.getAccountId());

        Account account = orderMapper.findAccountById(request.getAccountId());
        if (account == null) {
            throw new AccountNotFoundException(
                    request.getAccountId());
        }

        Instrument instrument = orderMapper.findInstrumentBySymbol(request.getSymbol());
        if (instrument == null) {
            throw new InstrumentNotFoundException(
                    null,
                    InstrumentNotFoundException.Reason.UNKNOWN_SYMBOL);
        }

        PortfolioHolding holding = orderMapper.findPortfolioHolding(
                request.getAccountId(),
                instrument.getId());

        List<Order> existingOrders = orderMapper.findExistingOrdersByAccount(
                request.getAccountId());

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .accountId(request.getAccountId())
                .instrumentId(instrument.getId())
                .idempotencyKey(request.getIdempotencyKey())
                .side(request.getSide())
                .quantity(request.getQuantity())
                .limitPrice(request.getPrice())
                .status(OrderStatus.NEW)
                .createdAt(Instant.now())
                .build();

        orderValidator.validate(order, account, instrument, holding, existingOrders);
        BigDecimal convertedLimitPriceInInstrumentCurrency = CurrencyConverter.convert(
                account.getCurrency(),
                instrument.getCurrency(),
                order.getLimitPrice());

        orderMapper.insertOrder(order);
        int touchedAccounts = orderMapper.incrementAccountVersionIfVersionMatches(
                account.getId(),
                account.getVersion());
        if (touchedAccounts == 0) {
            throw new OptimisticLockException(
                    "Account version mismatch during order placement; concurrent modification detected");
        }

        return new PlaceOrderInfo(order, instrument, convertedLimitPriceInInstrumentCurrency);
    }

   @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
      accountAuthorizer.ensureOrderManagementAllowed();
      Order order = orderMapper.findOrderById(orderId);
      accountAuthorizer.ensureAccountAccess(order.getAccountId());
       Account account = orderMapper.findAccountById(order.getAccountId());
       if (account == null) {
           throw new AccountNotFoundException(order.getAccountId());
       }
       try{
                order.cancel();
       } catch (IllegalOrderStateException e) {
           throw new IllegalOrderStateException("Failed to cancel order: " + e.getMessage());
       }
       

       int affectedRows = orderMapper.updateOrderStatusIfCurrentStatusMatches(
                orderId,
               OrderStatus.NEW,
               order.getStatus(),
               order.getTerminalAt());
       if (affectedRows == 0) {
           throw new OptimisticLockException(
                   "Order status mismatch during cancellation; concurrent modification detected");
       }

       int touchedAccounts = orderMapper.incrementAccountVersionIfVersionMatches(
               account.getId(),
               account.getVersion());
       if (touchedAccounts == 0) {
           throw new OptimisticLockException(
                   "Account version mismatch during order cancellation; concurrent modification detected");
       }

       return OrderResponse.builder()
               .orderId(orderId)
               .status(order.getStatus())
               .message("Order cancelled successfully")
               .build();
    }

    private static class PlaceOrderInfo {
        final Order order;
        final Instrument instrument;
        final BigDecimal convertedLimitPriceInInstrumentCurrency;

        PlaceOrderInfo(
                Order order,
                Instrument instrument,
                BigDecimal convertedLimitPriceInInstrumentCurrency) {
            this.order = order;
            this.instrument = instrument;
            this.convertedLimitPriceInInstrumentCurrency = convertedLimitPriceInInstrumentCurrency;
        }
    }
}
