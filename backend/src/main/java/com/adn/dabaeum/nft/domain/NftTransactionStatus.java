package com.adn.dabaeum.nft.domain;

public enum NftTransactionStatus {
    /** 아직 체인에 반영되지 않았다. 나중에 다시 확인한다. */
    PENDING,
    /** 체인에 반영됐다. */
    CONFIRMED,
    /** 체인이 처리를 거절했다. */
    REJECTED
}
