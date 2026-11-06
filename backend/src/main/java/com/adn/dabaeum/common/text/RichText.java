package com.adn.dabaeum.common.text;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;

/** 사용자가 보낸 서식 있는 본문을 저장 전에 정화한다. 프론트 정화는 API 직접 호출을 못 막는다. */
public final class RichText {

    // relaxed() 가 a[href]·img[src]·blockquote[cite]·q[cite] 의 프로토콜을
    // 이미 http/https/mailto/ftp 등으로 제한한다. addProtocols 를 또 걸어도 더 좁혀지지 않는다.
    private static final Safelist SAFELIST = Safelist.relaxed()
        .addAttributes("a", "target", "rel");

    private static final Document.OutputSettings OUTPUT =
        new Document.OutputSettings().prettyPrint(false);

    private RichText() {
    }

    public static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        String cleaned = Jsoup.clean(html, "", SAFELIST, OUTPUT);
        return enforceNoopener(cleaned);
    }

    /**
     * target 이 있는 링크는 새 탭이 window.opener 로 원래 탭을 바꿔치기하는
     * 역탭내빙(reverse tabnabbing)에 노출된다. rel 을 noopener noreferrer 로 강제한다.
     */
    private static String enforceNoopener(String cleaned) {
        Document doc = Jsoup.parseBodyFragment(cleaned);
        doc.outputSettings(OUTPUT);
        Elements links = doc.body().select("a[target]");
        for (Element link : links) {
            link.attr("rel", "noopener noreferrer");
        }
        return doc.body().html();
    }
}
