package com.zenith.trade.journal.service;

import com.zenith.trade.journal.dal.model.Trade;
import java.math.BigDecimal;
import java.util.*;

public class RealizedPLService {

    private static class TradeBatch {
        BigDecimal quantity; // Signed: Positive for Long, Negative for Short
        BigDecimal price;

        TradeBatch(BigDecimal quantity, BigDecimal price) {
            this.quantity = quantity;
            this.price = price;
        }
    }

    /**
     * Calculate realized P&L for a list of trades using FIFO method.
     */
    public List<Trade> calculateRealizedPL(List<Trade> trades) {
        // Sort trades by date ascending, then BUY before SELL within same date
        // so that long positions are opened before sells attempt to close them
        trades.sort(Comparator.comparing(Trade::getTradeDate)
                .thenComparing(t -> t.getAction().toUpperCase().contains("BUY") ? 0 : 1)
                .thenComparing(Trade::getCreatedAt));

        // Map of symbol -> Queue of open positions
        Map<String, Deque<TradeBatch>> openPositions = new HashMap<>();

        for (Trade trade : trades) {
            // Only process if quantity and price are present
            if (trade.getQuantity() == null || trade.getPrice() == null) {
                continue;
            }

            String symbol = trade.getSymbol();
            String action = trade.getAction().toUpperCase();
            BigDecimal quantity = trade.getQuantity().abs();
            BigDecimal price = trade.getPrice();

            boolean isBuy = action.contains("BUY");
            boolean isShort = isShortPosition(symbol, openPositions);

            if (isBuy) {
                if (isShort) {
                    // Covering a short position (Closing via Buy)
                    processClosing(trade, openPositions.get(symbol), -1);
                } else {
                    // Opening a long position
                    addToOpenPositions(symbol, quantity, price, openPositions);
                }
            } else { // SELL
                boolean isLong = isLongPosition(symbol, openPositions);
                if (isLong) {
                    // Selling a long position (Closing via Sell)
                    processClosing(trade, openPositions.get(symbol), 1);
                } else {
                    // Opening a short position (Selling to Open)
                    addToOpenPositions(symbol, quantity.negate(), price, openPositions);
                }
            }
        }

        return trades;
    }

    private boolean isShortPosition(String symbol, Map<String, Deque<TradeBatch>> openPositions) {
        Deque<TradeBatch> batches = openPositions.get(symbol);
        return batches != null && !batches.isEmpty() && batches.peek().quantity.signum() < 0;
    }

    private boolean isLongPosition(String symbol, Map<String, Deque<TradeBatch>> openPositions) {
        Deque<TradeBatch> batches = openPositions.get(symbol);
        return batches != null && !batches.isEmpty() && batches.peek().quantity.signum() > 0;
    }

    private void addToOpenPositions(String symbol, BigDecimal quantity, BigDecimal price,
            Map<String, Deque<TradeBatch>> openPositions) {
        openPositions.computeIfAbsent(symbol, k -> new ArrayDeque<>()).add(new TradeBatch(quantity, price));
    }

    private void processClosing(Trade trade, Deque<TradeBatch> batches, int direction) {
        BigDecimal remainingQtyToClose = trade.getQuantity().abs();
        BigDecimal realizedPl = BigDecimal.ZERO;
        BigDecimal closingPrice = trade.getPrice();

        while (remainingQtyToClose.compareTo(BigDecimal.ZERO) > 0 && !batches.isEmpty()) {
            TradeBatch batch = batches.peek();
            BigDecimal batchQty = batch.quantity.abs();

            BigDecimal matchedQty = remainingQtyToClose.min(batchQty);

            // Calculate P&L for this portion
            BigDecimal pnl;
            if (direction == 1) { // Closing Long: (Sell Price - Buy Price) * Qty
                pnl = closingPrice.subtract(batch.price).multiply(matchedQty);
            } else { // Closing Short: (Entry Price - Buy Price) * Qty
                pnl = batch.price.subtract(closingPrice).multiply(matchedQty);
            }

            realizedPl = realizedPl.add(pnl);

            // Update batch
            if (batchQty.compareTo(matchedQty) > 0) {
                // Partial close of batch
                BigDecimal newBatchQty = batchQty.subtract(matchedQty);
                // Restore sign
                batch.quantity = (batch.quantity.signum() > 0) ? newBatchQty : newBatchQty.negate();
                remainingQtyToClose = BigDecimal.ZERO; // Done
            } else {
                // Fully closed batch
                batches.poll();
                remainingQtyToClose = remainingQtyToClose.subtract(matchedQty);
            }
        }

        trade.setRealizedPl(realizedPl);
    }
}
