package com.adn.dabaeum.blockchain.api;

import com.adn.dabaeum.blockchain.application.BlockchainMonitoringService;
import com.adn.dabaeum.blockchain.domain.BlockchainAlert;
import com.adn.dabaeum.blockchain.domain.BlockchainMetrics;
import com.adn.dabaeum.blockchain.domain.BlockchainTransactionSummary;
import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.ApiResponse;
import com.adn.dabaeum.common.api.PageMeta;
import com.adn.dabaeum.common.security.CurrentUserProvider;
import com.adn.dabaeum.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/api/v1")
public class BlockchainMonitoringController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final BlockchainMonitoringService service;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public BlockchainMonitoringController(
        BlockchainMonitoringService service,
        CurrentUserProvider currentUserProvider,
        Clock clock
    ) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/blockchain/metrics")
    public ApiResponse<BlockchainMetricsResponse> metrics(
        HttpServletRequest servletRequest
    ) {
        BlockchainMetrics metrics =
            service.metrics(currentUserProvider.requireContext());
        return new ApiResponse<>(
            new BlockchainMetricsResponse(
                metrics.blockHeight(), metrics.transactionTotal(),
                metrics.transactionConfirmed(), metrics.transactionFailed(),
                metrics.successRate(), metrics.credentialIssuedCount()),
            meta(requestId(servletRequest)));
    }

    @GetMapping("/blockchain/transactions")
    public BlockchainTransactionPageResponse transactions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest servletRequest
    ) {
        validatePaging(page, size);
        BlockchainMonitoringService.Page<BlockchainTransactionSummary> result =
            service.transactions(currentUserProvider.requireContext(), page, size);
        return new BlockchainTransactionPageResponse(
            result.data().stream()
                .map(summary -> new BlockchainTransactionResponse(
                    summary.id(), summary.txHash(), summary.type(),
                    summary.status(), summary.occurredAt(),
                    summary.requestedAt(), summary.confirmedAt(),
                    summary.blockNumber(), summary.reconciled()))
                .toList(),
            new PageMeta(result.page(), result.size(),
                result.totalElements(), result.totalPages()),
            meta(requestId(servletRequest)));
    }

    @GetMapping("/blockchain/alerts")
    public BlockchainAlertPageResponse alerts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        HttpServletRequest servletRequest
    ) {
        validatePaging(page, size);
        BlockchainMonitoringService.Page<BlockchainAlert> result =
            service.alerts(currentUserProvider.requireContext(), page, size);
        return new BlockchainAlertPageResponse(
            result.data().stream()
                .map(alert -> new BlockchainAlertResponse(
                    alert.id(), alert.severity(), alert.message(),
                    alert.occurredAt()))
                .toList(),
            new PageMeta(result.page(), result.size(),
                result.totalElements(), result.totalPages()),
            meta(requestId(servletRequest)));
    }

    private void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || page > Integer.MAX_VALUE / size) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
                "Request parameter is invalid", List.of("page", "size"));
        }
    }

    private ApiMeta meta(String requestId) {
        return new ApiMeta(requestId, OffsetDateTime.ofInstant(clock.instant(), SEOUL));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
