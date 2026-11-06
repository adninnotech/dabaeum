package com.adn.dabaeum.nft.domain;

import java.util.regex.Pattern;

/**
 * 발행 요청. 체인에 영구히 남는 값이므로 개인정보를 담지 않는다.
 *
 * @param contractAddress 컨트랙트 주소
 * @param receiver        토큰을 받을 지갑. 다배움은 발급 지갑 자신이다
 * @param uri             메타데이터 주소 또는 다배움 식별 문자열
 * @param hash            메타데이터 원문 SHA-256 (소문자 16진수 64자)
 * @param creator         제작자 지갑. 발급 지갑 자신이다
 */
public record NftMintRequest(String contractAddress, String receiver, String uri, String hash, String creator) {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");
    private static final int MAX_URI_LENGTH = 512;

    public NftMintRequest {
        contractAddress = required(contractAddress, "contractAddress");
        receiver = required(receiver, "receiver");
        creator = required(creator, "creator");
        uri = required(uri, "uri");
        if (!NftMetadata.acceptableUri(uri) || uri.length() > MAX_URI_LENGTH) {
            throw new IllegalArgumentException("uri must be https or urn:dabaeum:nft, up to " + MAX_URI_LENGTH + " chars");
        }
        hash = required(hash, "hash");
        if (!SHA256_HEX.matcher(hash).matches()) {
            throw new IllegalArgumentException("hash must be lowercase SHA-256 hex");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
