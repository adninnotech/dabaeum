package com.adn.dabaeum.blockchain.api;

import com.adn.dabaeum.common.api.ApiMeta;
import com.adn.dabaeum.common.api.PageMeta;
import java.util.List;

public record BlockchainTransactionPageResponse(
    List<BlockchainTransactionResponse> data,
    PageMeta page,
    ApiMeta meta
) {
}
