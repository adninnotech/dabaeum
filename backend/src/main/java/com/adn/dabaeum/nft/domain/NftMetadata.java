package com.adn.dabaeum.nft.domain;

import java.util.regex.Pattern;

/** 체인에 적는 메타데이터 주소와, 그 주소가 내줄 원문과 해시. */
public record NftMetadata(String uri, String json, String hash) {

    private static final Pattern DABAEUM_URN =
        Pattern.compile("^urn:dabaeum:nft:(badges|certificates):[0-9a-f-]{36}$");
    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public NftMetadata {
        if (uri == null || !acceptableUri(uri)) {
            throw new IllegalArgumentException("uri must be https or urn:dabaeum:nft");
        }
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("json is required");
        }
        if (hash == null || !SHA256_HEX.matcher(hash).matches()) {
            throw new IllegalArgumentException("hash must be SHA-256 hex");
        }
    }

    /** 웹 주소는 https 만, 식별 문자열은 다배움 NFT 형식만 받는다. */
    public static boolean acceptableUri(String uri) {
        return uri.startsWith("https://") || DABAEUM_URN.matcher(uri).matches();
    }

}
