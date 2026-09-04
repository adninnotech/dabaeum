package com.adn.dabaeum.credential.application;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.springframework.stereotype.Component;

/**
 * W3C Bitstring Status List v1.0 의 encodedList 를 만든다.
 *
 * <p>비트 0 이 첫 바이트의 최상위 비트다. 배열을 GZIP 으로 압축한 뒤 multibase base64url
 * (접두 {@code u}, 패딩 없음)로 감싼다. 폐기가 없는 131,072 비트는 수십 바이트로 줄어든다.
 */
@Component
public class BitstringStatusListEncoder {

    private static final Pattern ENCODED_LIST = Pattern.compile("^u[A-Za-z0-9_-]+$");

    public String encode(int capacityBits, Collection<Integer> setIndexes) {
        Objects.requireNonNull(setIndexes, "setIndexes");
        if (capacityBits <= 0 || capacityBits % 8 != 0) {
            throw new IllegalArgumentException("capacityBits must be a positive multiple of 8");
        }
        byte[] bits = new byte[capacityBits / 8];
        for (Integer index : setIndexes) {
            Objects.requireNonNull(index, "index");
            if (index < 0 || index >= capacityBits) {
                throw new IllegalArgumentException("index is outside the status list");
            }
            bits[index / 8] |= (byte) (0x80 >>> (index % 8));
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            gzip.write(bits);
        } catch (IOException exception) {
            throw new IllegalStateException("Bitstring compression failed", exception);
        }
        return "u" + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.toByteArray());
    }

    /** 검증자 쪽 절차. 인덱스 순서대로 비트를 돌려준다. */
    public BitSet decode(String encodedList, int capacityBits) {
        Objects.requireNonNull(encodedList, "encodedList");
        if (!ENCODED_LIST.matcher(encodedList).matches()) {
            throw new IllegalArgumentException("encodedList is not multibase base64url");
        }
        byte[] compressed = Base64.getUrlDecoder().decode(encodedList.substring(1));
        byte[] bits = new byte[capacityBits / 8];
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            int read = gzip.readNBytes(bits, 0, bits.length);
            if (read != bits.length || gzip.read() != -1) {
                throw new IllegalArgumentException("encodedList length does not match the list");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("encodedList is not valid GZIP", exception);
        }
        BitSet result = new BitSet(capacityBits);
        for (int index = 0; index < capacityBits; index++) {
            if ((bits[index / 8] & (0x80 >>> (index % 8))) != 0) {
                result.set(index);
            }
        }
        return result;
    }
}
