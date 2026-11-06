package com.adn.dabaeum.nft.application;

import com.adn.dabaeum.common.api.ApiErrorCode;
import com.adn.dabaeum.common.api.ApiException;
import com.adn.dabaeum.nft.domain.NftIssuance;
import com.adn.dabaeum.nft.domain.NftIssuanceRepository;
import com.adn.dabaeum.nft.domain.NftIssuanceStatus;
import com.adn.dabaeum.nft.domain.NftTargetType;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * NFT 가 가리키는 공개 메타데이터. 장부에 저장한 원문을 그대로 돌려주므로 체인에 적힌 해시와 같다.
 * 체인에 보냈을 수 있는 건만 내주고, 폐기된 건은 410 이다.
 */
@Service
@Profile({"local", "dev"})
public class NftPublicMetadataService {

    /** mint 를 보냈거나 보냈을 수 있는 상태. 보내기 전·체인 거절 건은 체인에 없으므로 내주지 않는다. */
    private static final Set<NftIssuanceStatus> ON_CHAIN_OR_MAYBE = EnumSet.of(
        NftIssuanceStatus.SUBMITTING, NftIssuanceStatus.SUBMITTED, NftIssuanceStatus.MINTED,
        NftIssuanceStatus.UNKNOWN);

    private final NftIssuanceRepository issuances;

    public NftPublicMetadataService(NftIssuanceRepository issuances) {
        this.issuances = Objects.requireNonNull(issuances, "issuances");
    }

    public String metadata(NftTargetType type, UUID issuanceId) {
        NftIssuance issuance = issuances.findById(issuanceId)
            .filter(found -> found.targetType() == type)
            .filter(found -> ON_CHAIN_OR_MAYBE.contains(found.status()))
            .filter(found -> found.metadataJson() != null)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ApiErrorCode.NFT_METADATA_NOT_FOUND,
                "NFT metadata not found"));
        if (issuance.revoked()) {
            throw new ApiException(HttpStatus.GONE, ApiErrorCode.NFT_METADATA_REVOKED,
                "The credential or badge for this NFT has been revoked");
        }
        return issuance.metadataJson();
    }
}
