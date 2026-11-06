package com.adn.dabaeum.nft.config;

import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftSourceRepository;
import com.adn.dabaeum.nft.infrastructure.mybatis.NftIssuanceMapper;
import com.adn.dabaeum.nft.infrastructure.mybatis.NftIssuanceMyBatisRepository;
import com.adn.dabaeum.nft.infrastructure.mybatis.NftSourceMapper;
import com.adn.dabaeum.nft.infrastructure.mybatis.NftSourceMyBatisRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 장부 저장소는 발행을 끈 서버에도 둔다. 이미 발행한 NFT 의 공개 메타데이터는 워커를 꺼도 계속 열려야 한다.
 */
@Configuration(proxyBeanMethods = false)
@Profile({"local", "dev"})
public class NftPersistenceConfiguration {

    @Bean
    NftIssuanceRepository nftIssuanceRepository(NftIssuanceMapper mapper) {
        return new NftIssuanceMyBatisRepository(mapper);
    }

    @Bean
    NftSourceRepository nftSourceRepository(NftSourceMapper mapper) {
        return new NftSourceMyBatisRepository(mapper);
    }
}
