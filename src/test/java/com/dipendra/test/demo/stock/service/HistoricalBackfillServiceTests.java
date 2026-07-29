package com.dipendra.test.demo.stock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.dipendra.test.demo.stock.config.DhanProperties;
import com.dipendra.test.demo.stock.domain.Nifty50Constituent;
import com.dipendra.test.demo.stock.repository.Nifty50Repository;

class HistoricalBackfillServiceTests {
    @Test
    void splitsLongHistoryIntoDhanCompliantWindows() {
        DhanHistoricalClient client = mock(DhanHistoricalClient.class);
        StockCandleStore store = mock(StockCandleStore.class);
        Nifty50Constituent stock = mock(Nifty50Constituent.class);
        when(stock.getId()).thenReturn(1L);
        when(client.fetchIntraday(eq(stock), any(), any())).thenReturn(List.of());
        HistoricalBackfillService service = new HistoricalBackfillService(new DhanProperties(),
                mock(Nifty50Repository.class), client, store);
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 9, 15);
        LocalDateTime to = from.plusDays(200);

        int count = service.backfillStock(stock, from, to);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LocalDateTime> starts = ArgumentCaptor.forClass(LocalDateTime.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LocalDateTime> ends = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(client, org.mockito.Mockito.times(3)).fetchIntraday(eq(stock), starts.capture(), ends.capture());
        assertThat(count).isZero();
        assertThat(starts.getAllValues()).containsExactly(from, from.plusDays(89), from.plusDays(178));
        assertThat(ends.getAllValues()).containsExactly(from.plusDays(89), from.plusDays(178), to);
    }
}
