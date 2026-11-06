package com.adn.dabaeum.nft.application;

import com.adn.dabaeum.nft.domain.NftAmount;
import com.adn.dabaeum.nft.domain.NftLedgerPort;
import com.adn.dabaeum.nft.domain.NftMintRequest;
import com.adn.dabaeum.nft.domain.NftMintSubmission;
import com.adn.dabaeum.nft.domain.NftToken;
import com.adn.dabaeum.nft.domain.NftTransactionState;
import java.util.List;
import java.util.Optional;

/** 테스트에서 원장 호출 사이에 끼어들기 위한 위임 원장. */
class DelegatingLedger implements NftLedgerPort {

    private final NftLedgerPort delegate;

    DelegatingLedger(NftLedgerPort delegate) {
        this.delegate = delegate;
    }

    @Override
    public NftMintSubmission mint(NftMintRequest request) {
        return delegate.mint(request);
    }

    @Override
    public NftTransactionState transaction(String factHash) {
        return delegate.transaction(factHash);
    }

    @Override
    public List<Long> mintedTokenIndexes(String contractAddress, String factHash) {
        return delegate.mintedTokenIndexes(contractAddress, factHash);
    }

    @Override
    public Optional<NftToken> token(String contractAddress, long index) {
        return delegate.token(contractAddress, index);
    }

    @Override
    public long totalSupply(String contractAddress) {
        return delegate.totalSupply(contractAddress);
    }

    @Override
    public List<NftAmount> balance(String address) {
        return delegate.balance(address);
    }
}
