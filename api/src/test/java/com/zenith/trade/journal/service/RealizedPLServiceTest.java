package com.zenith.trade.journal.service;

import com.zenith.trade.journal.dal.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RealizedPLServiceTest {

    private RealizedPLService service;

    @BeforeEach
    void setUp() {
        service = new RealizedPLService();
    }

    private Trade makeTrade(String symbol, String action, double qty, double price, LocalDate date) {
        return Trade.builder()
                .id(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .symbol(symbol)
                .action(action)
                .quantity(BigDecimal.valueOf(qty))
                .price(BigDecimal.valueOf(price))
                .tradeDate(date)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void simpleBuyThenSell_correctRealizedPL() {
        List<Trade> trades = new ArrayList<>();
        trades.add(makeTrade("AAPL", "BUY", 100, 150.0, LocalDate.of(2024, 1, 1)));
        trades.add(makeTrade("AAPL", "SELL", 100, 160.0, LocalDate.of(2024, 2, 1)));

        service.calculateRealizedPL(trades);

        // BUY trade should not have realizedPl set
        assertNull(trades.get(0).getRealizedPl());
        // SELL trade: (160 - 150) * 100 = 1000
        assertNotNull(trades.get(1).getRealizedPl());
        assertEquals(0, BigDecimal.valueOf(1000.0).compareTo(trades.get(1).getRealizedPl()));
    }

    @Test
    void multipleBuysThenSingleSell_FIFOOrder() {
        List<Trade> trades = new ArrayList<>();
        trades.add(makeTrade("AAPL", "BUY", 50, 100.0, LocalDate.of(2024, 1, 1)));
        trades.add(makeTrade("AAPL", "BUY", 50, 120.0, LocalDate.of(2024, 1, 15)));
        trades.add(makeTrade("AAPL", "SELL", 100, 130.0, LocalDate.of(2024, 2, 1)));

        service.calculateRealizedPL(trades);

        // SELL: FIFO -> first 50 @ 100 => (130-100)*50=1500, then 50 @ 120 => (130-120)*50=500 => total 2000
        BigDecimal sellPl = trades.get(2).getRealizedPl();
        assertNotNull(sellPl);
        assertEquals(0, BigDecimal.valueOf(2000.0).compareTo(sellPl));
    }

    @Test
    void partialClose_onlyCalculatesPLOnClosedPortion() {
        List<Trade> trades = new ArrayList<>();
        trades.add(makeTrade("TSLA", "BUY", 100, 200.0, LocalDate.of(2024, 1, 1)));
        trades.add(makeTrade("TSLA", "SELL", 50, 250.0, LocalDate.of(2024, 2, 1)));

        service.calculateRealizedPL(trades);

        // Only 50 shares closed: (250-200)*50 = 2500
        BigDecimal sellPl = trades.get(1).getRealizedPl();
        assertNotNull(sellPl);
        assertEquals(0, BigDecimal.valueOf(2500.0).compareTo(sellPl));
    }

    @Test
    void shortPosition_sellToOpenBuyToClose() {
        List<Trade> trades = new ArrayList<>();
        // Sell to open (short)
        trades.add(makeTrade("MSFT", "SELL", 100, 300.0, LocalDate.of(2024, 1, 1)));
        // Buy to close
        trades.add(makeTrade("MSFT", "BUY", 100, 280.0, LocalDate.of(2024, 2, 1)));

        service.calculateRealizedPL(trades);

        // Short P&L: (300 - 280) * 100 = 2000
        BigDecimal buyPl = trades.get(1).getRealizedPl();
        assertNotNull(buyPl);
        assertEquals(0, BigDecimal.valueOf(2000.0).compareTo(buyPl));
    }

    @Test
    void multipleSymbols_dontInterfere() {
        List<Trade> trades = new ArrayList<>();
        trades.add(makeTrade("AAPL", "BUY", 100, 150.0, LocalDate.of(2024, 1, 1)));
        trades.add(makeTrade("GOOG", "BUY", 50, 100.0, LocalDate.of(2024, 1, 1)));
        trades.add(makeTrade("AAPL", "SELL", 100, 160.0, LocalDate.of(2024, 2, 1)));
        trades.add(makeTrade("GOOG", "SELL", 50, 90.0, LocalDate.of(2024, 2, 1)));

        service.calculateRealizedPL(trades);

        // AAPL: (160-150)*100 = 1000
        assertEquals(0, BigDecimal.valueOf(1000.0).compareTo(trades.get(2).getRealizedPl()));
        // GOOG: (90-100)*50 = -500
        assertEquals(0, BigDecimal.valueOf(-500.0).compareTo(trades.get(3).getRealizedPl()));
    }

    @Test
    void nullQuantityOrPrice_tradesAreSkipped() {
        List<Trade> trades = new ArrayList<>();
        Trade nullQty = makeTrade("AAPL", "BUY", 100, 150.0, LocalDate.of(2024, 1, 1));
        nullQty.setQuantity(null);
        trades.add(nullQty);

        Trade nullPrice = makeTrade("AAPL", "SELL", 100, 160.0, LocalDate.of(2024, 2, 1));
        nullPrice.setPrice(null);
        trades.add(nullPrice);

        // Should not throw
        assertDoesNotThrow(() -> service.calculateRealizedPL(trades));
        assertNull(trades.get(0).getRealizedPl());
        assertNull(trades.get(1).getRealizedPl());
    }

    @Test
    void emptyTradeList_noErrors() {
        List<Trade> trades = new ArrayList<>();
        assertDoesNotThrow(() -> service.calculateRealizedPL(trades));
        assertTrue(trades.isEmpty());
    }

    @Test
    void negativeQuantitySell_handledCorrectly() {
        // SnapTrade returns negative quantities for SELL trades
        List<Trade> trades = new ArrayList<>();
        trades.add(makeTrade("AAPL", "BUY", 100, 150.0, LocalDate.of(2024, 1, 1)));
        trades.add(makeTrade("AAPL", "SELL", -100, 160.0, LocalDate.of(2024, 2, 1)));

        service.calculateRealizedPL(trades);

        // Should still calculate P&L correctly: (160 - 150) * 100 = 1000
        assertNotNull(trades.get(1).getRealizedPl());
        assertEquals(0, BigDecimal.valueOf(1000.0).compareTo(trades.get(1).getRealizedPl()));
    }

    @Test
    void buyWithNoMatchingSell_realizedPlStaysNull() {
        List<Trade> trades = new ArrayList<>();
        trades.add(makeTrade("NVDA", "BUY", 100, 500.0, LocalDate.of(2024, 1, 1)));

        service.calculateRealizedPL(trades);

        assertNull(trades.get(0).getRealizedPl());
    }

    @Test
    void sameDateBuyAndSell_plAssignedToSellNotBuy() {
        // When BUY and SELL happen on the same date, BUY should be processed first
        // so the SELL closes the long (not opens a short)
        List<Trade> trades = new ArrayList<>();
        // Add SELL first to test sort ordering
        trades.add(makeTrade("PLTR", "SELL", -10, 7.30, LocalDate.of(2024, 1, 1)));
        trades.add(makeTrade("PLTR", "BUY", 10, 3.85, LocalDate.of(2024, 1, 1)));

        service.calculateRealizedPL(trades);

        // BUY should have no P&L (it's the opening trade)
        Trade buyTrade = trades.stream().filter(t -> t.getAction().equals("BUY")).findFirst().get();
        assertNull(buyTrade.getRealizedPl());

        // SELL should have the P&L: (7.30 - 3.85) * 10 = 34.50
        Trade sellTrade = trades.stream().filter(t -> t.getAction().equals("SELL")).findFirst().get();
        assertNotNull(sellTrade.getRealizedPl());
        assertEquals(0, BigDecimal.valueOf(34.50).compareTo(sellTrade.getRealizedPl()));
    }
}
