package com.zenith.trade.journal.handler;

import com.zenith.trade.journal.dal.model.Trade;
import com.zenith.trade.journal.dal.repository.BrokerAccountRepository;
import com.zenith.trade.journal.dal.repository.TradeRepository;
import com.zenith.trade.journal.dal.repository.UserRepository;
import com.zenith.trade.journal.service.RealizedPLService;
import com.zenith.trade.journal.service.SnapTradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TradeHandlerTest {

    private TradeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TradeHandler(
                mock(SnapTradeService.class),
                mock(TradeRepository.class),
                mock(UserRepository.class),
                mock(BrokerAccountRepository.class),
                mock(RealizedPLService.class)
        );
    }

    // ==================== calculateTotalCost tests ====================

    @Test
    void calculateTotalCost_buyOrder_addsFees() {
        Trade trade = Trade.builder()
                .action("BUY")
                .price(BigDecimal.valueOf(100))
                .quantity(BigDecimal.valueOf(10))
                .commission(BigDecimal.valueOf(5))
                .fees(BigDecimal.valueOf(2))
                .build();

        BigDecimal result = handler.calculateTotalCost(trade);

        // Base: 100*10=1000, fees: 5+2=7, total: 1000+7=1007
        assertEquals(0, BigDecimal.valueOf(1007).compareTo(result));
    }

    @Test
    void calculateTotalCost_sellOrder_subtractsFees() {
        Trade trade = Trade.builder()
                .action("SELL")
                .price(BigDecimal.valueOf(100))
                .quantity(BigDecimal.valueOf(10))
                .commission(BigDecimal.valueOf(5))
                .fees(BigDecimal.valueOf(2))
                .build();

        BigDecimal result = handler.calculateTotalCost(trade);

        // Base: 100*10=1000, fees: 5+2=7, total: 1000-7=993
        assertEquals(0, BigDecimal.valueOf(993).compareTo(result));
    }

    @Test
    void calculateTotalCost_buyToOpen_addsFees() {
        Trade trade = Trade.builder()
                .action("BUY_TO_OPEN")
                .price(BigDecimal.valueOf(50))
                .quantity(BigDecimal.valueOf(5))
                .commission(BigDecimal.ONE)
                .fees(BigDecimal.ONE)
                .build();

        BigDecimal result = handler.calculateTotalCost(trade);

        // Base: 50*5=250, fees: 1+1=2, total: 250+2=252
        assertEquals(0, BigDecimal.valueOf(252).compareTo(result));
    }

    // ==================== determineAction tests ====================

    @Test
    void determineAction_standardActions() {
        assertEquals("BUY", handler.determineAction("BUY"));
        assertEquals("SELL", handler.determineAction("SELL"));
        assertEquals("BUY", handler.determineAction("BTO"));
        assertEquals("SELL", handler.determineAction("STC"));
        assertEquals("SELL_TO_OPEN", handler.determineAction("STO"));
        assertEquals("BUY_TO_CLOSE", handler.determineAction("BTC"));
    }

    @Test
    void determineAction_fullNames() {
        assertEquals("BUY", handler.determineAction("BUY_TO_OPEN"));
        assertEquals("SELL", handler.determineAction("SELL_TO_CLOSE"));
        assertEquals("SELL_TO_OPEN", handler.determineAction("SELL_TO_OPEN"));
        assertEquals("BUY_TO_CLOSE", handler.determineAction("BUY_TO_CLOSE"));
    }

    @Test
    void determineAction_caseInsensitive() {
        assertEquals("BUY", handler.determineAction("buy"));
        assertEquals("SELL", handler.determineAction("sell"));
        assertEquals("BUY", handler.determineAction("Bto"));
    }

    @Test
    void determineAction_nullReturnsUnknown() {
        assertEquals("UNKNOWN", handler.determineAction(null));
    }

    @Test
    void determineAction_unknownReturnsAsIs() {
        assertEquals("DIVIDEND", handler.determineAction("DIVIDEND"));
    }

    // ==================== generateTradeHash tests ====================

    @Test
    void generateTradeHash_producesConsistentHash() {
        UUID accountId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        LocalDate date = LocalDate.of(2024, 6, 15);
        OffsetDateTime timestamp = OffsetDateTime.parse("2024-06-15T10:30:00Z");

        String hash1 = handler.generateTradeHash(accountId, "AAPL", date, "BUY",
                BigDecimal.valueOf(100), BigDecimal.valueOf(150.50), timestamp);
        String hash2 = handler.generateTradeHash(accountId, "AAPL", date, "BUY",
                BigDecimal.valueOf(100), BigDecimal.valueOf(150.50), timestamp);

        assertNotNull(hash1);
        assertEquals(hash1, hash2);
    }

    @Test
    void generateTradeHash_differentInputs_differentHashes() {
        UUID accountId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        LocalDate date = LocalDate.of(2024, 6, 15);
        OffsetDateTime timestamp = OffsetDateTime.parse("2024-06-15T10:30:00Z");

        String hash1 = handler.generateTradeHash(accountId, "AAPL", date, "BUY",
                BigDecimal.valueOf(100), BigDecimal.valueOf(150), timestamp);
        String hash2 = handler.generateTradeHash(accountId, "GOOG", date, "BUY",
                BigDecimal.valueOf(100), BigDecimal.valueOf(150), timestamp);

        assertNotEquals(hash1, hash2);
    }

    @Test
    void generateTradeHash_isSHA256Length() {
        UUID accountId = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        OffsetDateTime timestamp = OffsetDateTime.now();

        String hash = handler.generateTradeHash(accountId, "TSLA", date, "SELL",
                BigDecimal.TEN, BigDecimal.valueOf(200), timestamp);

        // SHA-256 hex string is 64 characters
        assertEquals(64, hash.length());
    }
}
